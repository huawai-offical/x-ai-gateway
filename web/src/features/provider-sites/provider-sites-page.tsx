import { type FormEvent, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DownloadIcon, PencilIcon, PlusIcon, RefreshCwIcon, Trash2Icon } from 'lucide-react'
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

export function ProviderSitesPage() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [vendorFilter, setVendorFilter] = useState('ALL')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingSite, setEditingSite] = useState<ProviderSite | null>(null)
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
  const vendorOptions = useMemo(() => {
    const set = new Set<string>()
    for (const site of sites) {
      set.add(vendorCode(site))
    }
    return Array.from(set).sort((left, right) => left.localeCompare(right))
  }, [sites])
  const filteredSites = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase()
    return sites.filter((site) => {
      const matchesVendor = vendorFilter === 'ALL' || vendorCode(site) === vendorFilter
      const text = [
        site.profileCode,
        site.displayName,
        site.vendorCode,
        site.vendorName,
        site.providerFamily,
        site.siteKind,
        site.baseUrlPattern,
      ].filter(Boolean).join(' ').toLowerCase()
      return matchesVendor && (!normalizedKeyword || text.includes(normalizedKeyword))
    })
  }, [keyword, sites, vendorFilter])
  const vendorGroups = useMemo(() => buildVendorGroups(sites), [sites])

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ReturnType<typeof buildPayload> }) => {
      if (id == null) {
        return apiRequest<ProviderSite>('/admin/provider-sites', {
          method: 'POST',
          body: payload,
        })
      }
      return apiRequest<ProviderSite>(`/admin/provider-sites/${id}`, {
        method: 'PUT',
        body: payload,
      })
    },
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
    setEditingSite(null)
    setDraft(emptyDraft)
    setDraftError(null)
    setEditorOpen(true)
  }

  const openEdit = (site: ProviderSite) => {
    setEditingSite(site)
    setDraft(siteToDraft(site))
    setDraftError(null)
    setEditorOpen(true)
  }

  const closeEditor = () => {
    setEditorOpen(false)
    setEditingSite(null)
    setDraft(emptyDraft)
    setDraftError(null)
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    try {
      setDraftError(null)
      saveMutation.mutate({
        id: editingSite?.id ?? null,
        payload: buildPayload(draft),
      })
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
              新增 API 入口
            </Button>
          </>
        )}
      >
        {firstError ? <InlineError error={firstError} title="厂商管理操作失败" /> : null}
        {sitesQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : (
          <>
            <InfoGrid
              items={[
                { key: 'vendors', label: '厂商数', value: vendorGroups.length.toLocaleString('zh-CN') },
                { key: 'sites', label: 'API 入口', value: sites.length.toLocaleString('zh-CN') },
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
                  placeholder="厂商 / API 入口 / 协议家族 / Base URL"
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

      <PageSection kicker="厂商聚合" title="厂商与 API 入口">
        {sitesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : filteredSites.length ? (
          <PaginatedRows items={filteredSites} itemLabel="个入口">
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">厂商</th>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">API 入口</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议入口</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型/凭证</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((site) => (
                      <tr key={site.id} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-medium text-foreground">{vendorLabel(site)}</div>
                          <div className="text-xs text-muted-foreground">{vendorCode(site)}</div>
                        </td>
                        <td className="px-4 py-3">
                          <Link className="font-medium text-primary hover:underline" to={`/console/provider-sites/${site.id}`}>
                            {site.displayName}
                          </Link>
                          <div className="text-xs text-muted-foreground">{site.profileCode}</div>
                          <div className="mt-1 truncate text-xs text-muted-foreground">{site.baseUrlPattern ?? '未配置 Base URL 匹配'}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>{formatEnum(site.siteKind)}</div>
                          <div className="text-xs">{site.providerFamily} / {site.compatibilitySurface}</div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1.5">
                            <StatusBadge tone={site.active ? 'success' : 'warning'}>{site.active ? '启用' : '停用'}</StatusBadge>
                            <StatusBadge tone={healthTone(site.healthState)}>{site.healthState}</StatusBadge>
                          </div>
                          <div className="mt-1 text-xs text-muted-foreground">刷新：{formatInstant(site.refreshedAt)}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>{site.modelCount.toLocaleString('zh-CN')} 模型</div>
                          <div className="text-xs">{site.linkedCredentialCount.toLocaleString('zh-CN')} 凭证</div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" variant="outline" size="sm" onClick={() => openEdit(site)}>
                              <PencilIcon data-icon="inline-start" />
                              编辑
                            </Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => refreshMutation.mutate(site.id)}>
                              <RefreshCwIcon data-icon="inline-start" />
                              刷新
                            </Button>
                            <Button
                              type="button"
                              variant="destructive"
                              size="sm"
                              onClick={() => {
                                if (window.confirm(`确认删除 API 入口“${site.displayName}”吗？`)) {
                                  deleteMutation.mutate(site.id)
                                }
                              }}
                            >
                              <Trash2Icon data-icon="inline-start" />
                              删除
                            </Button>
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
          <EmptyState title="没有匹配的 API 入口" />
        )}
      </PageSection>

      <PageSection kicker="预设导入" title="厂商预设">
        {presetsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : presets.length ? (
          <PaginatedRows items={presets} itemLabel="个预设">
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">预设</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">厂商</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议入口</th>
                      <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型族</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((preset) => (
                      <tr key={preset.code} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-medium text-foreground">{preset.displayName}</div>
                          <div className="text-xs text-muted-foreground">{preset.code}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>{vendorPresetLabel(preset)}</div>
                          <div className="text-xs">{preset.vendorCode ?? preset.providerFamily}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>{formatEnum(preset.siteKind)}</div>
                          <div className="text-xs">{summarizeList(preset.supportedProtocols, '无', 2)}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{summarizeList(preset.modelFamilies, '无', 3)}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            {preset.imported ? (
                              <Link to={`/console/provider-sites/${preset.existingSiteProfileId}`}>
                                <Button type="button" variant="outline" size="sm">查看入口</Button>
                              </Link>
                            ) : (
                              <Button type="button" variant="outline" size="sm" onClick={() => importMutation.mutate(preset.code)}>
                                <DownloadIcon data-icon="inline-start" />
                                导入
                              </Button>
                            )}
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
          <EmptyState title="暂无厂商预设" />
        )}
      </PageSection>

      <Dialog open={editorOpen} onOpenChange={(open) => (open ? setEditorOpen(true) : closeEditor())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingSite ? '编辑 API 入口' : '新增 API 入口'}</DialogTitle>
            <DialogDescription>配置厂商、协议入口和请求兼容画像。</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4" onSubmit={handleSubmit}>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">入口编码</span>
                <Input value={draft.profileCode} onChange={(event) => setDraft({ ...draft, profileCode: event.target.value })} />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">入口名称</span>
                <Input value={draft.displayName} onChange={(event) => setDraft({ ...draft, displayName: event.target.value })} />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">厂商编码</span>
                <Input value={draft.vendorCode} onChange={(event) => setDraft({ ...draft, vendorCode: event.target.value })} placeholder="例如 xiaomi_mimo" />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">厂商名称</span>
                <Input value={draft.vendorName} onChange={(event) => setDraft({ ...draft, vendorName: event.target.value })} placeholder="例如 小米 MiMo" />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">协议入口</span>
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
                <Input value={draft.baseUrlPattern} onChange={(event) => setDraft({ ...draft, baseUrlPattern: event.target.value })} />
              </label>
            </div>
            <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
              <input
                type="checkbox"
                className="size-4 rounded border-border"
                checked={draft.active}
                onChange={(event) => setDraft({ ...draft, active: event.target.checked })}
              />
              <span className="text-sm font-medium text-foreground">启用该 API 入口</span>
            </label>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">描述</span>
              <Textarea rows={3} value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} />
            </label>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">对话兼容画像 JSON</span>
              <Textarea
                rows={6}
                value={draft.conversationProfileJson}
                onChange={(event) => setDraft({ ...draft, conversationProfileJson: event.target.value })}
                placeholder='{"reasoningContentMode":"passthrough"}'
              />
            </label>
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

function buildVendorGroups(sites: ProviderSite[]) {
  const map = new Map<string, { code: string; name: string; sites: ProviderSite[] }>()
  for (const site of sites) {
    const code = vendorCode(site)
    const group = map.get(code) ?? { code, name: vendorLabel(site), sites: [] }
    group.sites.push(site)
    map.set(code, group)
  }
  return Array.from(map.values()).sort((left, right) => left.name.localeCompare(right.name))
}

function siteToDraft(site: ProviderSite): ProviderSiteDraft {
  return {
    profileCode: site.profileCode,
    displayName: site.displayName,
    vendorCode: site.vendorCode ?? '',
    vendorName: site.vendorName ?? '',
    siteKind: site.siteKind,
    baseUrlPattern: site.baseUrlPattern ?? '',
    description: site.description ?? '',
    conversationProfileJson: Object.keys(site.conversationProfile ?? {}).length
      ? JSON.stringify(site.conversationProfile, null, 2)
      : '',
    active: site.active,
  }
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

function formatEnum(value: string) {
  return value.replaceAll('_', ' ')
}

function healthTone(value?: string | null) {
  if (value === 'READY' || value === 'HEALTHY') return 'success'
  if (value === 'UNKNOWN') return 'neutral'
  if (value === 'DEGRADED' || value === 'COOLDOWN') return 'warning'
  return 'danger'
}
