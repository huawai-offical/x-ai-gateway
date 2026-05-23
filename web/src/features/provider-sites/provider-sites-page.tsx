import { type FormEvent, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRightIcon, DownloadIcon, PlusIcon, RefreshCwIcon, Trash2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import {
  SITE_KIND_OPTIONS,
  type ProviderProtocolEndpoint,
  type ProviderSite,
  type ProviderSiteDraft,
  type ProviderSitePreset,
} from './types'

const emptyDraft: ProviderSiteDraft = {
  profileCode: '',
  displayName: '',
  vendorCode: '',
  vendorName: '',
  siteKind: 'OPENAI_COMPATIBLE_GENERIC',
  baseUrlPattern: '',
  description: '',
  conversationProfileJson: '',
  active: true,
}

type EditorStep = 'basic' | 'connection' | 'advanced'

type ProviderCatalogRow = {
  key: string
  site: ProviderSite | null
  preset: ProviderSitePreset | null
  displayName: string
  profileCode: string
  vendorCode: string
  vendorName: string
  providerFamily: string
  siteKind: string
  supportedProtocols: string[]
  baseUrl: string | null
  endpoints: ProviderProtocolEndpoint[]
  modelFamilies: string[]
  modelCount: number
  credentialCount: number
}

export function ProviderSitesPage() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [vendorFilter, setVendorFilter] = useState('ALL')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorStep, setEditorStep] = useState<EditorStep>('basic')
  const [draft, setDraft] = useState<ProviderSiteDraft>(emptyDraft)
  const [draftError, setDraftError] = useState<string | null>(null)

  const sitesQuery = useQuery({
    queryKey: ['provider-sites', 'list'],
    queryFn: () => apiRequest<ProviderSite[]>('/admin/provider-sites'),
  })
  const presetsQuery = useQuery({
    queryKey: ['provider-sites', 'presets'],
    queryFn: () => apiRequest<ProviderSitePreset[]>('/admin/provider-sites/presets'),
  })

  const sites = useMemo(
    () => [...((sitesQuery.data ?? []) as ProviderSite[])].sort((left, right) => {
      const vendorCompare = vendorLabel(left).localeCompare(vendorLabel(right))
      return vendorCompare || left.displayName.localeCompare(right.displayName)
    }),
    [sitesQuery.data],
  )
  const presets = useMemo(
    () => [...((presetsQuery.data ?? []) as ProviderSitePreset[])].sort((left, right) =>
      vendorPresetLabel(left).localeCompare(vendorPresetLabel(right)) || left.displayName.localeCompare(right.displayName),
    ),
    [presetsQuery.data],
  )
  const catalogRows = useMemo(() => buildCatalogRows(sites, presets), [presets, sites])
  const vendorOptions = useMemo(() => {
    const set = new Set<string>()
    for (const row of catalogRows) {
      set.add(row.vendorCode)
    }
    return Array.from(set).sort((left, right) => left.localeCompare(right))
  }, [catalogRows])
  const filteredRows = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase()
    return catalogRows.filter((row) => {
      const matchesVendor = vendorFilter === 'ALL' || row.vendorCode === vendorFilter
      const text = [
        row.profileCode,
        row.displayName,
        row.vendorCode,
        row.vendorName,
        row.providerFamily,
        row.siteKind,
        row.baseUrl,
        row.site ? '已导入' : '可导入',
        summarizeEndpointLabels(row.endpoints),
      ].filter(Boolean).join(' ').toLowerCase()
      return matchesVendor && (!normalizedKeyword || text.includes(normalizedKeyword))
    })
  }, [catalogRows, keyword, vendorFilter])

  const saveMutation = useMutation({
    mutationFn: (payload: ReturnType<typeof buildPayload>) =>
      apiRequest<ProviderSite>('/admin/provider-sites', {
        method: 'POST',
        body: payload,
      }),
    onSuccess: () => {
      closeEditor()
      invalidateProviderSiteQueries(queryClient)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/provider-sites/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => invalidateProviderSiteQueries(queryClient),
  })
  const refreshMutation = useMutation({
    mutationFn: (id?: number) => {
      if (id == null) {
        return apiRequest<ProviderSite[]>('/admin/provider-sites/refresh', {
          method: 'POST',
          body: { siteProfileIds: [] },
        })
      }
      return apiRequest<ProviderSite>(`/admin/provider-sites/${id}/refresh`, {
        method: 'POST',
      })
    },
    onSuccess: () => invalidateProviderSiteQueries(queryClient),
  })
  const importMutation = useMutation({
    mutationFn: (code: string) =>
      apiRequest<ProviderSite>(`/admin/provider-sites/presets/${encodeURIComponent(code)}/import`, {
        method: 'POST',
        body: { active: true, refreshCapabilities: true },
      }),
    onSuccess: () => invalidateProviderSiteQueries(queryClient),
  })

  const firstError = sitesQuery.error ?? presetsQuery.error ?? saveMutation.error ?? deleteMutation.error ?? refreshMutation.error ?? importMutation.error

  const openCreate = () => {
    setDraft(emptyDraft)
    setEditorStep('basic')
    setDraftError(null)
    setEditorOpen(true)
  }

  const closeEditor = () => {
    setEditorOpen(false)
    setEditorStep('basic')
    setDraft(emptyDraft)
    setDraftError(null)
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    try {
      setDraftError(null)
      saveMutation.mutate(buildPayload(draft))
    } catch (error) {
      setDraftError(error instanceof Error ? error.message : 'API 入口保存失败。')
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="上游接入"
        title="厂商管理中心"
        actions={(
          <>
            <Button type="button" variant="outline" onClick={() => refreshMutation.mutate(undefined)} disabled={refreshMutation.isPending}>
              <RefreshCwIcon data-icon="inline-start" />
              刷新能力
            </Button>
            <Button type="button" onClick={openCreate}>
              <PlusIcon data-icon="inline-start" />
              新增自定义入口
            </Button>
          </>
        )}
      >
        {firstError ? <InlineError error={firstError} title="厂商管理操作失败" /> : null}
        {sitesQuery.isPending || presetsQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : (
          <>
            <InfoGrid
              items={[
                { key: 'catalog', label: '厂商目录', value: catalogRows.length.toLocaleString('zh-CN'), hint: `${sites.length} 个已导入 / ${catalogRows.length - sites.length} 个可导入` },
                { key: 'sites', label: '已导入入口', value: sites.length.toLocaleString('zh-CN') },
                { key: 'models', label: '模型记录', value: sites.reduce((sum, site) => sum + site.modelCount, 0).toLocaleString('zh-CN') },
                { key: 'credentials', label: '绑定凭证', value: sites.reduce((sum, site) => sum + site.linkedCredentialCount, 0).toLocaleString('zh-CN') },
              ]}
            />

            <div className="mt-5 grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">关键字</span>
                <Input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder="厂商 / 入口 / 协议入口 / Base URL"
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">厂商</span>
                <select
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={vendorFilter}
                  onChange={(event) => setVendorFilter(event.target.value)}
                >
                  <option value="ALL">全部厂商</option>
                  {vendorOptions.map((vendor) => (
                    <option key={vendor} value={vendor}>
                      {vendor}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </>
        )}
      </PageSection>

      <section className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <div className="text-xs font-medium uppercase text-muted-foreground">
            厂商目录
          </div>
          <h2 className="text-xl font-semibold tracking-tight text-foreground">厂商与 API 入口</h2>
        </div>
        {sitesQuery.isPending || presetsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : filteredRows.length ? (
          <PaginatedRows
            items={filteredRows}
            itemLabel="个厂商"
            paginationClassName="rounded-none border-x-0 border-b-0 bg-transparent px-0"
          >
            {({ pageItems }) => (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[1100px] table-fixed text-sm">
                  <thead>
                    <tr>
                      <th className="w-[22%] border-b border-border/60 px-3 py-3 text-left font-medium text-muted-foreground">厂商目录</th>
                      <th className="w-[16%] border-b border-border/60 px-3 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[18%] border-b border-border/60 px-3 py-3 text-left font-medium text-muted-foreground">默认站点类型</th>
                      <th className="w-[22%] border-b border-border/60 px-3 py-3 text-left font-medium text-muted-foreground">协议入口</th>
                      <th className="w-[10%] border-b border-border/60 px-3 py-3 text-left font-medium text-muted-foreground">规模</th>
                      <th className="w-[12%] border-b border-border/60 px-3 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.key} className="border-b border-border/40 align-top last:border-b-0">
                        <td className="px-3 py-4">
                          <div className="font-medium text-foreground">{row.displayName}</div>
                          <div className="text-xs text-muted-foreground">{row.vendorName} / {row.vendorCode}</div>
                          <div className="mt-1 truncate text-xs text-muted-foreground" title={row.baseUrl ?? undefined}>
                            {row.baseUrl ?? '未配置 Base URL'}
                          </div>
                        </td>
                        <td className="px-3 py-4">
                          <div className="flex flex-wrap gap-1.5">
                            {row.site ? (
                              <>
                                <StatusBadge tone={row.site.active ? 'success' : 'warning'}>{row.site.active ? '已导入' : '已停用'}</StatusBadge>
                                <StatusBadge tone={healthTone(row.site.healthState)}>{row.site.healthState}</StatusBadge>
                              </>
                            ) : (
                              <StatusBadge tone="neutral">可导入</StatusBadge>
                            )}
                          </div>
                          <div className="mt-1 text-xs text-muted-foreground">
                            {row.site ? `刷新：${formatInstant(row.site.refreshedAt)}` : `预设：${row.profileCode}`}
                          </div>
                        </td>
                        <td className="px-3 py-4 text-muted-foreground">
                          <div>{formatEnum(row.siteKind)}</div>
                          <div className="text-xs">{row.providerFamily} / {summarizeList(row.supportedProtocols, '无', 2)}</div>
                        </td>
                        <td className="px-3 py-4 text-muted-foreground">
                          <EndpointSummary endpoints={row.endpoints} />
                        </td>
                        <td className="px-3 py-4 text-muted-foreground">
                          <div>{row.modelCount.toLocaleString('zh-CN')} 模型</div>
                          <div className="text-xs">{row.credentialCount.toLocaleString('zh-CN')} 凭证</div>
                        </td>
                        <td className="px-3 py-4">
                          <div className="flex flex-wrap gap-2">
                            {row.site ? (
                              <>
                                <Link to={`/console/provider-sites/${row.site.id}`}>
                                  <Button type="button" variant="outline" size="sm">
                                    <ArrowRightIcon data-icon="inline-start" />
                                    管理
                                  </Button>
                                </Link>
                                <Button type="button" variant="outline" size="sm" onClick={() => refreshMutation.mutate(row.site?.id)}>
                                  <RefreshCwIcon data-icon="inline-start" />
                                  刷新
                                </Button>
                                <Button
                                  type="button"
                                  variant="destructive"
                                  size="sm"
                                  onClick={() => {
                                    if (row.site && window.confirm(`确认删除 API 入口“${row.site.displayName}”吗？`)) {
                                      deleteMutation.mutate(row.site.id)
                                    }
                                  }}
                                >
                                  <Trash2Icon data-icon="inline-start" />
                                  删除
                                </Button>
                              </>
                            ) : row.preset ? (
                              <ImportPresetButton
                                preset={row.preset}
                                disabled={importMutation.isPending}
                                onImport={(code) => importMutation.mutate(code)}
                              />
                            ) : null}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="没有匹配的厂商目录" className="rounded-none border-x-0 bg-transparent" />
        )}
      </section>

      <Dialog open={editorOpen} onOpenChange={(open) => (open ? setEditorOpen(true) : closeEditor())}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>新增自定义 API 入口</DialogTitle>
            <DialogDescription>预设厂商请在厂商目录中导入；这里仅用于没有预设的自定义兼容入口。</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4" onSubmit={handleSubmit}>
            <Tabs value={editorStep} onValueChange={(value) => setEditorStep(value as EditorStep)}>
              <TabsList variant="line" className="w-full justify-start overflow-x-auto">
                <TabsTrigger value="basic">1. 基本信息</TabsTrigger>
                <TabsTrigger value="connection">2. 连接方式</TabsTrigger>
                <TabsTrigger value="advanced">3. 高级配置</TabsTrigger>
              </TabsList>

              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">入口编码</span>
                    <Input value={draft.profileCode} onChange={(event) => setDraft({ ...draft, profileCode: event.target.value })} placeholder="site:custom_openai" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">入口名称</span>
                    <Input value={draft.displayName} onChange={(event) => setDraft({ ...draft, displayName: event.target.value })} placeholder="Custom OpenAI-compatible" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">厂商编码</span>
                    <Input value={draft.vendorCode} onChange={(event) => setDraft({ ...draft, vendorCode: event.target.value })} placeholder="例如 custom_provider" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">厂商名称</span>
                    <Input value={draft.vendorName} onChange={(event) => setDraft({ ...draft, vendorName: event.target.value })} placeholder="例如 Custom Provider" />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={draft.active}
                      onChange={(event) => setDraft({ ...draft, active: event.target.checked })}
                    />
                    <span className="text-sm font-medium text-foreground">创建后启用该 API 入口</span>
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="connection" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">默认站点类型</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={draft.siteKind}
                      onChange={(event) => setDraft({ ...draft, siteKind: event.target.value })}
                    >
                      {SITE_KIND_OPTIONS.map((siteKind) => (
                        <option key={siteKind} value={siteKind}>
                          {formatEnum(siteKind)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Base URL 匹配</span>
                    <Input value={draft.baseUrlPattern} onChange={(event) => setDraft({ ...draft, baseUrlPattern: event.target.value })} placeholder="https://provider.example.com/v1" />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">描述</span>
                    <Textarea rows={3} value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="advanced" className="pt-3">
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">对话兼容画像 JSON</span>
                  <Textarea
                    rows={8}
                    value={draft.conversationProfileJson}
                    onChange={(event) => setDraft({ ...draft, conversationProfileJson: event.target.value })}
                    placeholder='{"reasoningContentMode":"passthrough"}'
                  />
                </label>
              </TabsContent>
            </Tabs>

            {(draftError || saveMutation.error) ? (
              <InlineError error={saveMutation.error ?? new Error(draftError ?? 'API 入口保存失败。')} title="API 入口保存失败" />
            ) : null}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeEditor}>取消</Button>
              <Button type="submit" disabled={saveMutation.isPending}>保存入口</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function ImportPresetButton({
  preset,
  disabled,
  onImport,
}: {
  preset: ProviderSitePreset
  disabled: boolean
  onImport: (code: string) => void
}) {
  return (
    <Button type="button" variant="outline" size="sm" onClick={() => onImport(preset.code)} disabled={disabled}>
      <DownloadIcon data-icon="inline-start" />
      导入
    </Button>
  )
}

function EndpointSummary({ endpoints }: { endpoints: ProviderProtocolEndpoint[] }) {
  if (!endpoints.length) {
    return <span>暂无</span>
  }
  return (
    <div className="flex flex-col gap-1">
      {endpoints.slice(0, 2).map((endpoint) => (
        <div key={endpoint.id ?? endpoint.endpointCode} className="min-w-0">
          <div className="truncate text-foreground" title={endpoint.protocolSuite}>{endpoint.protocolSuite}</div>
          <div className="text-xs">{endpoint.providerType}</div>
        </div>
      ))}
      {endpoints.length > 2 ? (
        <div className="text-xs">+{endpoints.length - 2} 个入口</div>
      ) : null}
    </div>
  )
}

function buildCatalogRows(sites: ProviderSite[], presets: ProviderSitePreset[]): ProviderCatalogRow[] {
  const sitesById = new Map(sites.map((site) => [site.id, site]))
  const sitesByProfileCode = new Map(sites.map((site) => [site.profileCode, site]))
  const usedSiteIds = new Set<number>()
  const rows: ProviderCatalogRow[] = []

  for (const preset of presets) {
    const site = preset.existingSiteProfileId ? sitesById.get(preset.existingSiteProfileId) ?? null : sitesByProfileCode.get(preset.profileCode) ?? null
    if (site) {
      usedSiteIds.add(site.id)
    }
    rows.push({
      key: `preset:${preset.code}`,
      site,
      preset,
      displayName: site?.displayName ?? preset.displayName,
      profileCode: site?.profileCode ?? preset.profileCode,
      vendorCode: site?.vendorCode?.trim() || preset.vendorCode?.trim() || preset.providerFamily.toLowerCase(),
      vendorName: site?.vendorName?.trim() || vendorPresetLabel(preset),
      providerFamily: site?.providerFamily ?? preset.providerFamily,
      siteKind: site?.siteKind ?? preset.siteKind,
      supportedProtocols: site?.supportedProtocols ?? preset.supportedProtocols,
      baseUrl: site?.baseUrlPattern ?? preset.defaultBaseUrl ?? null,
      endpoints: site?.protocolEndpoints?.length ? site.protocolEndpoints : (preset.protocolEndpoints ?? []),
      modelFamilies: preset.modelFamilies,
      modelCount: site?.modelCount ?? preset.modelFamilies.length,
      credentialCount: site?.linkedCredentialCount ?? 0,
    })
  }

  for (const site of sites) {
    if (usedSiteIds.has(site.id)) {
      continue
    }
    rows.push({
      key: `site:${site.id}`,
      site,
      preset: null,
      displayName: site.displayName,
      profileCode: site.profileCode,
      vendorCode: vendorCode(site),
      vendorName: vendorLabel(site),
      providerFamily: site.providerFamily,
      siteKind: site.siteKind,
      supportedProtocols: site.supportedProtocols,
      baseUrl: site.baseUrlPattern ?? null,
      endpoints: site.protocolEndpoints ?? [],
      modelFamilies: [],
      modelCount: site.modelCount,
      credentialCount: site.linkedCredentialCount,
    })
  }

  return rows.sort((left, right) =>
    left.vendorName.localeCompare(right.vendorName) || left.displayName.localeCompare(right.displayName),
  )
}

function buildPayload(draft: ProviderSiteDraft) {
  const profileCode = draft.profileCode.trim()
  const displayName = draft.displayName.trim()
  if (!profileCode) {
    throw new Error('入口编码不能为空。')
  }
  if (!displayName) {
    throw new Error('入口名称不能为空。')
  }

  return {
    profileCode,
    displayName,
    vendorCode: draft.vendorCode.trim() || null,
    vendorName: draft.vendorName.trim() || null,
    siteKind: draft.siteKind,
    baseUrlPattern: draft.baseUrlPattern.trim() || null,
    description: draft.description.trim() || null,
    conversationProfile: parseConversationProfile(draft.conversationProfileJson),
    active: draft.active,
  }
}

function parseConversationProfile(value: string) {
  if (!value.trim()) {
    return null
  }
  const parsed = JSON.parse(value)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('对话兼容画像必须是 JSON 对象。')
  }
  return parsed as Record<string, unknown>
}

function invalidateProviderSiteQueries(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['provider-sites'] })
  queryClient.invalidateQueries({ queryKey: ['models-management', 'provider-sites'] })
  queryClient.invalidateQueries({ queryKey: ['models-management', 'capabilities'] })
}

function vendorCode(site: ProviderSite) {
  return site.vendorCode?.trim() || site.providerFamily.toLowerCase()
}

function vendorLabel(site: ProviderSite) {
  return site.vendorName?.trim() || site.vendorCode?.trim() || site.providerFamily
}

function vendorPresetLabel(preset: ProviderSitePreset) {
  return preset.vendorName?.trim() || preset.vendorCode?.trim() || preset.providerFamily
}

function summarizeList(items: string[], fallback: string, maxItems = 2) {
  const normalized = items.map((item) => item.trim()).filter(Boolean)
  if (!normalized.length) {
    return fallback
  }
  if (normalized.length <= maxItems) {
    return normalized.join(', ')
  }
  return `${normalized.slice(0, maxItems).join(', ')} +${normalized.length - maxItems}`
}

function summarizeEndpointLabels(endpoints: ProviderProtocolEndpoint[]) {
  return endpoints
    .flatMap((endpoint) => [endpoint.protocolSuite, endpoint.providerType, endpoint.siteKind])
    .filter(Boolean)
    .join(' ')
}

function formatEnum(value: string) {
  return value.replaceAll('_', ' ')
}

function healthTone(value?: string | null) {
  if (value === 'READY' || value === 'HEALTHY') return 'success'
  if (value === 'UNKNOWN') return 'neutral'
  if (value === 'DEGRADED' || value === 'COOLDOWN') return 'warning'
  return 'danger'
}
