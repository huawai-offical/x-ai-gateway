import { type Dispatch, type FormEvent, type SetStateAction, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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

type ProviderSite = {
  id: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  active: boolean
  refreshedAt?: string | null
}

type SiteModelCapability = {
  id: number
  modelName: string
  modelKey: string
  supportedProtocols: string[]
  supportsCache: boolean
  supportsThinking: boolean
  supportsVisibleReasoning: boolean
  supportsReasoningReuse: boolean
  capabilityLevel: string
  sourceRefreshedAt?: string | null
}

type SiteCapabilityBundle = {
  site: ProviderSite
  capabilities: SiteModelCapability[]
}

type ModelAliasRule = {
  id: number
  protocol: string
  targetModelName: string
  targetModelKey: string
  providerType?: string | null
  baseUrlPattern?: string | null
  priority: number
  enabled: boolean
  description?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

type ModelAlias = {
  id: number
  aliasName: string
  aliasKey: string
  enabled: boolean
  description?: string | null
  rules: ModelAliasRule[]
  createdAt?: string | null
  updatedAt?: string | null
}

type ModelAliasPreviewCandidate = {
  credentialId?: number | null
  credentialName?: string | null
  providerType?: string | null
  siteProfileId?: number | null
  providerFamily?: string | null
  siteKind?: string | null
  baseUrl?: string | null
  modelName?: string | null
  modelKey?: string | null
  supportedProtocols?: string[]
  supportsCache?: boolean
  supportsThinking?: boolean
  supportsVisibleReasoning?: boolean
  supportsReasoningReuse?: boolean
  capabilityLevel?: string | null
}

type ModelAliasPreview = {
  requestedModel: string
  protocol: string
  aliasMatched: boolean
  publicModel: string
  resolvedModelKey: string
  candidateCount: number
  candidates: ModelAliasPreviewCandidate[]
}

type AliasRuleForm = {
  id: string
  protocol: string
  targetModelName: string
  providerType: string
  baseUrlPattern: string
  priority: number
  enabled: boolean
  description: string
}

type AliasFormState = {
  aliasName: string
  enabled: boolean
  description: string
  rules: AliasRuleForm[]
}

type ModelSourceItem = {
  siteId: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  supportedProtocols: string[]
  capabilityLevel: string
  tags: string[]
  refreshedAt?: string | null
}

type ModelDirectoryRow = {
  modelKey: string
  modelName: string
  modelNames: string[]
  supportedProtocols: string[]
  sources: ModelSourceItem[]
  aliasNames: string[]
  latestRefreshedAt?: string | null
}

type AliasStep = 'basic' | 'rules' | 'submit'

const ALIAS_STEPS: AliasStep[] = ['basic', 'rules', 'submit']
const ALIAS_PROTOCOL_OPTIONS = ['openai', 'responses', 'anthropic_native', 'google_native', 'ollama_native'] as const
const ALIAS_PROVIDER_OPTIONS = ['OPENAI_DIRECT', 'OPENAI_COMPATIBLE', 'ANTHROPIC_DIRECT', 'GEMINI_DIRECT', 'OLLAMA_DIRECT'] as const

export function ModelsPage() {
  const queryClient = useQueryClient()
  const [searchKeyword, setSearchKeyword] = useState('')
  const [protocolFilter, setProtocolFilter] = useState('ALL')
  const [selectedModelKey, setSelectedModelKey] = useState<string | null>(null)
  const [selectedAliasId, setSelectedAliasId] = useState<number | null>(null)
  const [aliasEditorOpen, setAliasEditorOpen] = useState(false)
  const [aliasEditorId, setAliasEditorId] = useState<number | null>(null)
  const [aliasEditorStep, setAliasEditorStep] = useState<AliasStep>('basic')
  const [aliasEditorError, setAliasEditorError] = useState<string | null>(null)
  const [aliasForm, setAliasForm] = useState<AliasFormState>(createEmptyAliasForm())
  const [previewRequestedModel, setPreviewRequestedModel] = useState('')
  const [previewProtocol, setPreviewProtocol] = useState('openai')
  const [previewResult, setPreviewResult] = useState<ModelAliasPreview | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)

  const sitesQuery = useQuery({
    queryKey: ['models-management', 'provider-sites'],
    queryFn: () => apiRequest<ProviderSite[]>('/admin/provider-sites'),
  })
  const siteIdKey = useMemo(
    () => ((sitesQuery.data ?? []) as ProviderSite[]).map((site) => site.id).sort((a, b) => a - b).join(','),
    [sitesQuery.data],
  )
  const capabilitiesQuery = useQuery({
    queryKey: ['models-management', 'capabilities', siteIdKey],
    queryFn: async (): Promise<SiteCapabilityBundle[]> => {
      const sites = (sitesQuery.data ?? []) as ProviderSite[]
      const bundles = await Promise.all(
        sites.map(async (site) => ({
          site,
          capabilities: await apiRequest<SiteModelCapability[]>(`/admin/provider-sites/${site.id}/capabilities`),
        })),
      )
      return bundles
    },
    enabled: siteIdKey.length > 0,
  })
  const aliasesQuery = useQuery({
    queryKey: ['models-management', 'model-aliases'],
    queryFn: () => apiRequest<ModelAlias[]>('/admin/model-aliases'),
  })

  const aliasSaveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ReturnType<typeof buildAliasPayload> }) => {
      if (id == null) {
        return apiRequest<ModelAlias>('/admin/model-aliases', {
          method: 'POST',
          body: JSON.stringify(payload),
        })
      }
      return apiRequest<ModelAlias>(`/admin/model-aliases/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
    },
    onSuccess: () => {
      setAliasEditorOpen(false)
      setAliasEditorId(null)
      setAliasEditorStep('basic')
      setAliasEditorError(null)
      setAliasForm(createEmptyAliasForm())
      queryClient.invalidateQueries({ queryKey: ['models-management', 'model-aliases'] })
    },
  })
  const aliasDeleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/model-aliases/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      if (selectedAliasId != null) {
        setSelectedAliasId(null)
      }
      queryClient.invalidateQueries({ queryKey: ['models-management', 'model-aliases'] })
    },
  })
  const aliasPreviewMutation = useMutation({
    mutationFn: (payload: { requestedModel: string; protocol: string }) =>
      apiRequest<ModelAliasPreview>('/admin/model-aliases/preview', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: (result: ModelAliasPreview) => {
      setPreviewError(null)
      setPreviewResult(result)
    },
  })

  const mutationError = aliasDeleteMutation.error ?? aliasSaveMutation.error
  const modelRows = useMemo(
    () => buildModelRows((capabilitiesQuery.data ?? []) as SiteCapabilityBundle[], (aliasesQuery.data ?? []) as ModelAlias[]),
    [aliasesQuery.data, capabilitiesQuery.data],
  )
  const filteredModelRows = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase()
    return modelRows.filter((row) => {
      const matchesKeyword = !keyword
        || row.modelName.toLowerCase().includes(keyword)
        || row.modelKey.toLowerCase().includes(keyword)
        || row.aliasNames.some((item) => item.toLowerCase().includes(keyword))
        || row.sources.some((source) => source.displayName.toLowerCase().includes(keyword))
      const matchesProtocol = protocolFilter === 'ALL' || row.supportedProtocols.includes(protocolFilter)
      return matchesKeyword && matchesProtocol
    })
  }, [modelRows, protocolFilter, searchKeyword])
  const protocolOptions = useMemo(() => {
    const set = new Set<string>()
    for (const row of modelRows) {
      row.supportedProtocols.forEach((item) => set.add(item))
    }
    return Array.from(set).sort((left, right) => left.localeCompare(right))
  }, [modelRows])
  const selectedModel = useMemo(
    () => modelRows.find((item) => item.modelKey === selectedModelKey) ?? null,
    [modelRows, selectedModelKey],
  )
  const aliases = useMemo(
    () => [...((aliasesQuery.data ?? []) as ModelAlias[])].sort((left, right) => left.aliasName.localeCompare(right.aliasName)),
    [aliasesQuery.data],
  )
  const selectedAlias = useMemo(
    () => aliases.find((alias) => alias.id === selectedAliasId) ?? null,
    [aliases, selectedAliasId],
  )
  const aliasTargetOptions = useMemo(() => {
    const set = new Set<string>()
    for (const row of modelRows) {
      set.add(row.modelName)
      set.add(row.modelKey)
      row.modelNames.forEach((name) => set.add(name))
    }
    return Array.from(set).filter(Boolean).sort((left, right) => left.localeCompare(right))
  }, [modelRows])
  const aliasStepIndex = ALIAS_STEPS.indexOf(aliasEditorStep)

  const handleOpenAliasCreate = () => {
    setAliasEditorOpen(true)
    setAliasEditorId(null)
    setAliasEditorStep('basic')
    setAliasEditorError(null)
    setAliasForm(createEmptyAliasForm())
  }

  const handleOpenAliasEdit = (alias: ModelAlias) => {
    setAliasEditorOpen(true)
    setAliasEditorId(alias.id)
    setAliasEditorStep('basic')
    setAliasEditorError(null)
    setAliasForm(aliasToForm(alias))
  }

  const handleDeleteAlias = (alias: ModelAlias) => {
    if (!window.confirm(`确认删除模型别名“${alias.aliasName}”吗？`)) {
      return
    }
    aliasDeleteMutation.mutate(alias.id)
  }

  const handleSaveAlias = (event: FormEvent) => {
    event.preventDefault()
    try {
      setAliasEditorError(null)
      aliasSaveMutation.mutate({
        id: aliasEditorId,
        payload: buildAliasPayload(aliasForm),
      })
    } catch (error) {
      setAliasEditorError(error instanceof Error ? error.message : '模型别名保存失败。')
    }
  }

  const handlePreviewAlias = (event: FormEvent) => {
    event.preventDefault()
    const requestedModel = previewRequestedModel.trim()
    if (!requestedModel) {
      setPreviewError('请先输入待测试的模型名称或别名。')
      setPreviewResult(null)
      return
    }
    setPreviewError(null)
    aliasPreviewMutation.mutate({ requestedModel, protocol: previewProtocol })
  }

  const isLoading = sitesQuery.isPending || aliasesQuery.isPending || (siteIdKey.length > 0 && capabilitiesQuery.isPending)
  const hasError = sitesQuery.error ?? capabilitiesQuery.error ?? aliasesQuery.error

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="大模型管理"
        title="模型目录"
        className="order-2"
        actions={(
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              queryClient.invalidateQueries({ queryKey: ['models-management', 'provider-sites'] })
              queryClient.invalidateQueries({ queryKey: ['models-management', 'capabilities'] })
            }}
          >
            刷新视图
          </Button>
        )}
      >
        {mutationError ? (
          <InlineError
            error={mutationError}
            title="模型映射操作失败"
          />
        ) : null}

        <div className="mb-4 grid gap-4 md:grid-cols-2">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">关键字筛选</span>
            <Input
              value={searchKeyword}
              onChange={(event) => setSearchKeyword(event.target.value)}
              placeholder="模型名 / 别名 / 来源站点"
            />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">协议筛选</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={protocolFilter}
              onChange={(event) => setProtocolFilter(event.target.value)}
            >
              <option value="ALL">全部协议</option>
              {protocolOptions.map((protocol) => (
                <option key={protocol} value={protocol}>
                  {protocol}
                </option>
              ))}
            </select>
          </label>
        </div>

        {isLoading ? (
          <PageSkeleton count={1} />
        ) : hasError ? (
          <InlineError error={hasError} title="模型目录加载失败" />
        ) : filteredModelRows.length ? (
          <PaginatedRows items={filteredModelRows}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[28%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型名称</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">支持协议</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">来源数</th>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">来源示例</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近刷新</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.modelKey} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-medium text-foreground">{row.modelName}</div>
                          <div className="text-xs text-muted-foreground">{row.modelKey}</div>
                          {row.aliasNames.length ? (
                            <div className="mt-1 text-xs text-muted-foreground">别名：{summarizeList(row.aliasNames, '无', 2)}</div>
                          ) : null}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{summarizeList(row.supportedProtocols, '无', 3)}</td>
                        <td className="px-4 py-3 text-muted-foreground">{row.sources.length.toLocaleString('zh-CN')}</td>
                        <td className="px-4 py-3 text-muted-foreground">{summarizeList(row.sources.map((item) => item.displayName), '无', 2)}</td>
                        <td className="px-4 py-3 text-muted-foreground">{formatInstant(row.latestRefreshedAt)}</td>
                        <td className="px-4 py-3">
                          <Button type="button" variant="outline" size="sm" onClick={() => setSelectedModelKey(row.modelKey)}>
                            查看详情
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="当前暂无可展示的大模型记录" />
        )}
      </PageSection>

      <PageSection
        kicker="模型名称映射"
        title="模型别名映射治理"
        className="order-1"
        actions={(
          <Button type="button" onClick={handleOpenAliasCreate}>新增模型别名</Button>
        )}
      >
        {aliasesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : aliasesQuery.error ? (
          <InlineError error={aliasesQuery.error} title="模型别名加载失败" />
        ) : aliases.length ? (
          <PaginatedRows items={aliases}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">别名</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">规则数</th>
                      <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">目标模型</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">更新时间</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((alias) => (
                      <tr key={alias.id} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-medium text-foreground">{alias.aliasName}</div>
                          <div className="text-xs text-muted-foreground">{alias.aliasKey}</div>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={alias.enabled ? 'success' : 'warning'}>
                            {alias.enabled ? '启用' : '停用'}
                          </StatusBadge>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{alias.rules.length.toLocaleString('zh-CN')}</td>
                        <td className="px-4 py-3 text-muted-foreground">
                          {summarizeList(
                            Array.from(new Set(alias.rules.map((rule) => rule.targetModelName).filter(Boolean))),
                            '无',
                            2,
                          )}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{formatInstant(alias.updatedAt)}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" variant="outline" size="sm" onClick={() => setSelectedAliasId(alias.id)}>
                              查看详情
                            </Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => handleOpenAliasEdit(alias)}>
                              编辑
                            </Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => handleDeleteAlias(alias)}>
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
          <EmptyState title="当前还没有模型别名映射" />
        )}

        <form className="mt-4 grid gap-4 rounded-2xl border border-border/60 bg-muted/20 p-4" onSubmit={handlePreviewAlias}>
          <h4 className="text-sm font-semibold text-foreground">请求别名预演</h4>
          <div className="grid gap-4 md:grid-cols-3">
            <label className="flex flex-col gap-2 md:col-span-2">
              <span className="text-sm font-medium text-foreground">请求模型名 / 别名</span>
              <Input
                value={previewRequestedModel}
                onChange={(event) => setPreviewRequestedModel(event.target.value)}
                placeholder="例如：chat-fast"
              />
            </label>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">协议</span>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={previewProtocol}
                onChange={(event) => setPreviewProtocol(event.target.value)}
              >
                {ALIAS_PROTOCOL_OPTIONS.map((protocol) => (
                  <option key={protocol} value={protocol}>
                    {protocol}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="flex justify-end">
            <Button type="submit" variant="outline" disabled={aliasPreviewMutation.isPending}>
              {aliasPreviewMutation.isPending ? '预演中...' : '预演映射'}
            </Button>
          </div>
          {(previewError || aliasPreviewMutation.error) ? (
            <InlineError error={aliasPreviewMutation.error ?? new Error(previewError ?? '映射预演失败')} title="映射预演失败" />
          ) : null}
          {previewResult ? (
            <div className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-sm">
              <div className="font-medium text-foreground">
                结果：{previewResult.aliasMatched ? '命中别名映射' : '未命中别名映射'}
              </div>
              <div className="mt-1 text-muted-foreground">
                公共模型={previewResult.publicModel}，解析模型键={previewResult.resolvedModelKey}，候选数={previewResult.candidateCount}
              </div>
              {previewResult.candidates.length ? (
                <div className="mt-2 space-y-1 text-xs text-muted-foreground">
                  {previewResult.candidates.slice(0, 3).map((candidate, index) => (
                    <div key={`${candidate.credentialId ?? 'no-cred'}-${candidate.modelKey ?? 'no-model'}-${index}`}>
                      #{index + 1} {candidate.modelName ?? candidate.modelKey ?? '未知模型'} · {candidate.providerType ?? '未知提供方'} · {candidate.baseUrl ?? '无基础 URL'}
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}
        </form>
      </PageSection>

      <Dialog
        open={selectedModel != null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedModelKey(null)
          }
        }}
      >
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>模型详情</DialogTitle>
            <DialogDescription>查看模型详情。</DialogDescription>
          </DialogHeader>
          {selectedModel ? (
            <div className="space-y-4">
              <InfoGrid
                items={[
                  { key: 'modelName', label: '模型名称', value: selectedModel.modelName },
                  { key: 'modelKey', label: '模型键', value: selectedModel.modelKey },
                  { key: 'protocols', label: '支持协议', value: selectedModel.supportedProtocols.join(', ') || '无' },
                  { key: 'sourceCount', label: '来源站点数', value: selectedModel.sources.length.toLocaleString('zh-CN') },
                  { key: 'aliasCount', label: '别名数量', value: selectedModel.aliasNames.length.toLocaleString('zh-CN') },
                  { key: 'latest', label: '最近刷新', value: formatInstant(selectedModel.latestRefreshedAt) },
                ]}
                columnsClassName="md:grid-cols-3"
              />
              <InfoGrid
                items={[
                  {
                    key: 'modelNames',
                    label: '原始模型名集合',
                    value: selectedModel.modelNames.length ? selectedModel.modelNames.join(', ') : '无',
                  },
                  {
                    key: 'aliases',
                    label: '命中别名',
                    value: selectedModel.aliasNames.length ? selectedModel.aliasNames.join(', ') : '无',
                  },
                ]}
                columnsClassName="md:grid-cols-1"
              />
              <PaginatedRows items={selectedModel.sources}>
                {({ pageItems }) => (
                  <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                    <table className="w-full table-fixed text-sm">
                      <thead className="bg-muted/30">
                        <tr>
                          <th className="w-[28%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">来源站点</th>
                          <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                          <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议</th>
                          <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">能力标签</th>
                          <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">刷新时间</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pageItems.map((source) => (
                          <tr key={`${selectedModel.modelKey}-${source.siteId}-${source.profileCode}`} className="border-b border-border/40 align-top">
                            <td className="px-4 py-3">
                              <div className="font-medium text-foreground">{source.displayName}</div>
                              <div className="text-xs text-muted-foreground">{source.profileCode}</div>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">
                              <div>{source.providerFamily}</div>
                              <div className="text-xs">{source.siteKind}</div>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">{summarizeList(source.supportedProtocols, '无', 3)}</td>
                            <td className="px-4 py-3 text-muted-foreground">
                              {source.tags.length ? source.tags.join(', ') : `Capability:${source.capabilityLevel}`}
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">{formatInstant(source.refreshedAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog
        open={selectedAlias != null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedAliasId(null)
          }
        }}
      >
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>模型别名详情</DialogTitle>
            <DialogDescription>查看别名规则。</DialogDescription>
          </DialogHeader>
          {selectedAlias ? (
            <div className="space-y-4">
              <InfoGrid
                items={[
                  { key: 'aliasName', label: '别名名称', value: selectedAlias.aliasName },
                  { key: 'aliasKey', label: '别名键', value: selectedAlias.aliasKey },
                  { key: 'enabled', label: '启用状态', value: selectedAlias.enabled ? '启用' : '停用' },
                  { key: 'ruleCount', label: '规则数', value: selectedAlias.rules.length.toLocaleString('zh-CN') },
                  { key: 'updatedAt', label: '更新时间', value: formatInstant(selectedAlias.updatedAt) },
                  { key: 'description', label: '描述', value: selectedAlias.description ?? '无' },
                ]}
                columnsClassName="md:grid-cols-3"
              />
              <PaginatedRows items={selectedAlias.rules}>
                {({ pageItems }) => (
                  <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                    <table className="w-full table-fixed text-sm">
                      <thead className="bg-muted/30">
                        <tr>
                          <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议</th>
                          <th className="w-[25%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">目标模型</th>
                          <th className="w-[15%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                          <th className="w-[17%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">基础 URL 匹配</th>
                          <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">优先级</th>
                          <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                          <th className="w-[7%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">说明</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pageItems.map((rule) => (
                          <tr key={rule.id} className="border-b border-border/40 align-top">
                            <td className="px-4 py-3 text-muted-foreground">{rule.protocol}</td>
                            <td className="px-4 py-3 text-muted-foreground">
                              <div>{rule.targetModelName}</div>
                              <div className="text-xs">{rule.targetModelKey}</div>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">{rule.providerType ?? '全部'}</td>
                            <td className="px-4 py-3 text-muted-foreground">{rule.baseUrlPattern ?? '无'}</td>
                            <td className="px-4 py-3 text-muted-foreground">{rule.priority}</td>
                            <td className="px-4 py-3">
                              <StatusBadge tone={rule.enabled ? 'success' : 'warning'}>{rule.enabled ? '启用' : '停用'}</StatusBadge>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">{rule.description ?? '无'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog
        open={aliasEditorOpen}
        onOpenChange={(open) => {
          setAliasEditorOpen(open)
          if (!open) {
            setAliasEditorStep('basic')
            setAliasEditorError(null)
          }
        }}
      >
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>{aliasEditorId == null ? '新增模型别名' : '编辑模型别名'}</DialogTitle>
            <DialogDescription>填写别名映射规则。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSaveAlias}>
            <Tabs value={aliasEditorStep} onValueChange={(value) => setAliasEditorStep(value as AliasStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="rules">2. 映射规则</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">模型别名</span>
                    <Input
                      value={aliasForm.aliasName}
                      onChange={(event) =>
                        setAliasForm((current) => ({
                          ...current,
                          aliasName: event.target.value,
                        }))
                      }
                      placeholder="例如：chat-fast"
                    />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={aliasForm.enabled}
                      onChange={(event) =>
                        setAliasForm((current) => ({
                          ...current,
                          enabled: event.target.checked,
                        }))
                      }
                    />
                    <span className="text-sm font-medium text-foreground">启用该模型别名</span>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">描述（可选）</span>
                    <Textarea
                      rows={4}
                      value={aliasForm.description}
                      onChange={(event) =>
                        setAliasForm((current) => ({
                          ...current,
                          description: event.target.value,
                        }))
                      }
                    />
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="rules" className="pt-3">
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-foreground">规则列表</span>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        setAliasForm((current) => ({
                          ...current,
                          rules: [...current.rules, createEmptyAliasRule()],
                        }))
                      }
                    >
                      添加规则
                    </Button>
                  </div>
                  {aliasForm.rules.map((rule) => (
                    <div key={rule.id} className="rounded-2xl border border-border/60 bg-muted/20 p-4">
                      <div className="grid gap-4 md:grid-cols-2">
                        <label className="flex flex-col gap-2">
                          <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">协议</span>
                          <select
                            className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                            value={rule.protocol}
                            onChange={(event) => updateAliasRule(setAliasForm, rule.id, { protocol: event.target.value })}
                          >
                            {ALIAS_PROTOCOL_OPTIONS.map((protocol) => (
                              <option key={protocol} value={protocol}>
                                {protocol}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="flex flex-col gap-2">
                          <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">目标模型</span>
                          <Input
                            list="model-alias-target-options"
                            value={rule.targetModelName}
                            onChange={(event) => updateAliasRule(setAliasForm, rule.id, { targetModelName: event.target.value })}
                            placeholder="选择或输入目标模型"
                          />
                        </label>
                        <label className="flex flex-col gap-2">
                          <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">Provider（可选）</span>
                          <select
                            className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                            value={rule.providerType}
                            onChange={(event) => updateAliasRule(setAliasForm, rule.id, { providerType: event.target.value })}
                          >
                            <option value="">全部 Provider</option>
                            {ALIAS_PROVIDER_OPTIONS.map((providerType) => (
                              <option key={providerType} value={providerType}>
                                {providerType}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="flex flex-col gap-2">
                          <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">Base URL 匹配（可选）</span>
                          <Input
                            value={rule.baseUrlPattern}
                            onChange={(event) => updateAliasRule(setAliasForm, rule.id, { baseUrlPattern: event.target.value })}
                            placeholder="例如 openrouter\\.ai"
                          />
                        </label>
                        <label className="flex flex-col gap-2">
                          <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">优先级</span>
                          <Input
                            type="number"
                            min={1}
                            value={rule.priority}
                            onChange={(event) =>
                              updateAliasRule(setAliasForm, rule.id, {
                                priority: parsePositiveInt(event.target.value, rule.priority),
                              })
                            }
                          />
                        </label>
                        <label className="flex flex-col gap-2">
                          <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">说明（可选）</span>
                          <Input
                            value={rule.description}
                            onChange={(event) => updateAliasRule(setAliasForm, rule.id, { description: event.target.value })}
                          />
                        </label>
                        <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-background px-4 py-3 md:col-span-2">
                          <input
                            type="checkbox"
                            className="size-4 rounded border-border"
                            checked={rule.enabled}
                            onChange={(event) => updateAliasRule(setAliasForm, rule.id, { enabled: event.target.checked })}
                          />
                          <span className="text-sm font-medium text-foreground">启用该规则</span>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            className="ml-auto"
                            onClick={() => removeAliasRule(setAliasForm, rule.id)}
                          >
                            删除规则
                          </Button>
                        </label>
                      </div>
                    </div>
                  ))}
                </div>
              </TabsContent>
              <TabsContent value="submit" className="pt-3">
                <InfoGrid
                  items={[
                    { key: 'aliasName', label: '模型别名', value: aliasForm.aliasName || '未填写' },
                    { key: 'status', label: '状态', value: aliasForm.enabled ? '启用' : '停用' },
                    { key: 'ruleCount', label: '规则数', value: aliasForm.rules.length.toLocaleString('zh-CN') },
                    {
                      key: 'targets',
                      label: '目标模型',
                      value: summarizeList(
                        Array.from(new Set(aliasForm.rules.map((rule) => rule.targetModelName).filter(Boolean))),
                        '无',
                        3,
                      ),
                    },
                  ]}
                  columnsClassName="md:grid-cols-2"
                />
              </TabsContent>
            </Tabs>
            <datalist id="model-alias-target-options">
              {aliasTargetOptions.map((model) => (
                <option key={model} value={model} />
              ))}
            </datalist>

            {(aliasEditorError || aliasSaveMutation.error) ? (
              <InlineError
                error={aliasSaveMutation.error ?? new Error(aliasEditorError ?? '模型别名保存失败')}
                title="模型别名保存失败"
              />
            ) : null}

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setAliasEditorStep(ALIAS_STEPS[Math.max(0, aliasStepIndex - 1)])}
                disabled={aliasStepIndex === 0}
              >
                上一步
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => setAliasEditorStep(ALIAS_STEPS[Math.min(ALIAS_STEPS.length - 1, aliasStepIndex + 1)])}
                disabled={aliasStepIndex === ALIAS_STEPS.length - 1}
              >
                下一步
              </Button>
              <Button type="submit" disabled={aliasSaveMutation.isPending}>
                保存映射
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function buildModelRows(bundles: SiteCapabilityBundle[], aliases: ModelAlias[]): ModelDirectoryRow[] {
  const map = new Map<string, {
    modelKey: string
    modelName: string
    modelNames: Set<string>
    protocols: Set<string>
    sources: ModelSourceItem[]
    aliasNames: Set<string>
    refreshedInstants: string[]
  }>()

  for (const bundle of bundles) {
    for (const capability of bundle.capabilities) {
      const modelKey = capability.modelKey?.trim() || capability.modelName?.trim()
      if (!modelKey) {
        continue
      }
      const normalizedKey = modelKey.toLowerCase()
      const existing = map.get(normalizedKey) ?? {
        modelKey,
        modelName: capability.modelName || modelKey,
        modelNames: new Set<string>(),
        protocols: new Set<string>(),
        sources: [],
        aliasNames: new Set<string>(),
        refreshedInstants: [],
      }
      existing.modelNames.add(capability.modelName || modelKey)
      capability.supportedProtocols.forEach((protocol) => existing.protocols.add(protocol))
      existing.sources.push({
        siteId: bundle.site.id,
        profileCode: bundle.site.profileCode,
        displayName: bundle.site.displayName,
        providerFamily: bundle.site.providerFamily,
        siteKind: bundle.site.siteKind,
        supportedProtocols: capability.supportedProtocols,
        capabilityLevel: capability.capabilityLevel,
        tags: buildCapabilityTags(capability),
        refreshedAt: capability.sourceRefreshedAt ?? bundle.site.refreshedAt,
      })
      const refreshedAt = capability.sourceRefreshedAt ?? bundle.site.refreshedAt
      if (refreshedAt) {
        existing.refreshedInstants.push(refreshedAt)
      }
      map.set(normalizedKey, existing)
    }
  }

  for (const alias of aliases) {
    for (const rule of alias.rules) {
      const targetModelKey = (rule.targetModelKey || rule.targetModelName || '').trim()
      if (!targetModelKey) {
        continue
      }
      const normalizedKey = targetModelKey.toLowerCase()
      const existing = map.get(normalizedKey) ?? {
        modelKey: targetModelKey,
        modelName: rule.targetModelName,
        modelNames: new Set<string>(),
        protocols: new Set<string>(),
        sources: [],
        aliasNames: new Set<string>(),
        refreshedInstants: [],
      }
      existing.modelNames.add(rule.targetModelName)
      existing.aliasNames.add(alias.aliasName)
      map.set(normalizedKey, existing)
    }
  }

  return Array.from(map.entries())
    .map(([, value]) => ({
      modelKey: value.modelKey,
      modelName: value.modelName,
      modelNames: Array.from(value.modelNames).sort((left, right) => left.localeCompare(right)),
      supportedProtocols: Array.from(value.protocols).sort((left, right) => left.localeCompare(right)),
      sources: value.sources.sort((left, right) => left.displayName.localeCompare(right.displayName)),
      aliasNames: Array.from(value.aliasNames).sort((left, right) => left.localeCompare(right)),
      latestRefreshedAt: pickLatestInstant(value.refreshedInstants),
    }))
    .sort((left, right) => left.modelName.localeCompare(right.modelName))
}

function buildCapabilityTags(capability: SiteModelCapability) {
  const tags: string[] = []
  if (capability.supportsCache) tags.push('Cache')
  if (capability.supportsThinking) tags.push('Thinking')
  if (capability.supportsVisibleReasoning) tags.push('VisibleReasoning')
  if (capability.supportsReasoningReuse) tags.push('ReasoningReuse')
  return tags
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

function createEmptyAliasForm(): AliasFormState {
  return {
    aliasName: '',
    enabled: true,
    description: '',
    rules: [createEmptyAliasRule()],
  }
}

function createEmptyAliasRule(): AliasRuleForm {
  return {
    id: `draft-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    protocol: 'openai',
    targetModelName: '',
    providerType: '',
    baseUrlPattern: '',
    priority: 100,
    enabled: true,
    description: '',
  }
}

function aliasToForm(alias: ModelAlias): AliasFormState {
  return {
    aliasName: alias.aliasName,
    enabled: alias.enabled,
    description: alias.description ?? '',
    rules: alias.rules.length
      ? alias.rules.map((rule) => ({
        id: `rule-${rule.id}`,
        protocol: rule.protocol || 'openai',
        targetModelName: rule.targetModelName,
        providerType: rule.providerType ?? '',
        baseUrlPattern: rule.baseUrlPattern ?? '',
        priority: rule.priority,
        enabled: rule.enabled,
        description: rule.description ?? '',
      }))
      : [createEmptyAliasRule()],
  }
}

function buildAliasPayload(form: AliasFormState) {
  const aliasName = form.aliasName.trim()
  if (!aliasName) {
    throw new Error('模型别名不能为空。')
  }

  const rules = form.rules
    .map((rule) => ({
      protocol: rule.protocol.trim(),
      targetModelName: rule.targetModelName.trim(),
      providerType: rule.providerType.trim() || null,
      baseUrlPattern: rule.baseUrlPattern.trim() || null,
      priority: Number.isFinite(rule.priority) ? Math.max(1, Math.round(rule.priority)) : 100,
      enabled: rule.enabled,
      description: rule.description.trim() || null,
    }))
    .filter((rule) => rule.protocol && rule.targetModelName)

  if (!rules.length) {
    throw new Error('至少需要一条有效映射规则。')
  }

  return {
    aliasName,
    enabled: form.enabled,
    description: form.description.trim() || null,
    rules,
  }
}

function updateAliasRule(
  setForm: Dispatch<SetStateAction<AliasFormState>>,
  ruleId: string,
  patch: Partial<AliasRuleForm>,
) {
  setForm((current) => ({
    ...current,
    rules: current.rules.map((rule) => (rule.id === ruleId ? { ...rule, ...patch } : rule)),
  }))
}

function removeAliasRule(
  setForm: Dispatch<SetStateAction<AliasFormState>>,
  ruleId: string,
) {
  setForm((current) => {
    const filtered = current.rules.filter((rule) => rule.id !== ruleId)
    if (filtered.length) {
      return {
        ...current,
        rules: filtered,
      }
    }
    return {
      ...current,
      rules: [createEmptyAliasRule()],
    }
  })
}

function parsePositiveInt(value: string, fallback: number) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.max(1, Math.round(parsed))
}

function pickLatestInstant(values: string[]) {
  let latest: string | null = null
  for (const value of values) {
    const current = Date.parse(value)
    if (Number.isNaN(current)) {
      continue
    }
    if (latest == null || current > Date.parse(latest)) {
      latest = value
    }
  }
  return latest
}
