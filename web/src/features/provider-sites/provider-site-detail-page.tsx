import { type FormEvent, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeftIcon, PencilIcon, PlusIcon, RefreshCwIcon, Trash2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
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
  type CapabilityResolution,
  type ProviderProtocolEndpoint,
  type ProviderSite,
  type SiteModelCapability,
  type SurfaceCapability,
} from './types'

type ProtocolEndpointDraft = {
  endpointCode: string
  displayName: string
  protocolSuite: string
  providerType: string
  siteKind: string
  baseUrl: string
  authStrategy: string
  pathStrategy: string
  modelAddressingStrategy: string
  errorSchemaStrategy: string
  streamTransport: string
  conversationProfileJson: string
  active: boolean
}

type DetailTab = 'overview' | 'endpoints' | 'models' | 'diagnostics'
type EndpointEditorStep = 'basic' | 'runtime' | 'advanced'

export function ProviderSiteDetailPage() {
  const params = useParams()
  const siteId = Number(params.id)
  const queryClient = useQueryClient()
  const [endpointDialogOpen, setEndpointDialogOpen] = useState(false)
  const [detailTab, setDetailTab] = useState<DetailTab>('overview')
  const [editingEndpoint, setEditingEndpoint] = useState<ProviderProtocolEndpoint | null>(null)
  const [endpointEditorStep, setEndpointEditorStep] = useState<EndpointEditorStep>('basic')
  const [endpointDraft, setEndpointDraft] = useState<ProtocolEndpointDraft>(emptyEndpointDraft())
  const [endpointError, setEndpointError] = useState<string | null>(null)

  const siteQuery = useQuery({
    queryKey: ['provider-sites', 'detail', siteId],
    queryFn: () => apiRequest<ProviderSite>(`/admin/provider-sites/${siteId}`),
    enabled: Number.isFinite(siteId),
  })
  const capabilitiesQuery = useQuery({
    queryKey: ['provider-sites', 'capabilities', siteId],
    queryFn: () => apiRequest<SiteModelCapability[]>(`/admin/provider-sites/${siteId}/capabilities`),
    enabled: Number.isFinite(siteId),
  })
  const refreshMutation = useMutation({
    mutationFn: () => apiRequest<ProviderSite>(`/admin/provider-sites/${siteId}/refresh`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['provider-sites', 'detail', siteId] })
      queryClient.invalidateQueries({ queryKey: ['provider-sites', 'capabilities', siteId] })
      queryClient.invalidateQueries({ queryKey: ['provider-sites', 'list'] })
    },
  })
  const endpointMutation = useMutation({
    mutationFn: ({ endpointId, payload }: { endpointId?: number; payload: ReturnType<typeof buildEndpointPayload> }) => {
      if (endpointId == null) {
        return apiRequest<ProviderProtocolEndpoint>(`/admin/provider-sites/${siteId}/protocol-endpoints`, {
          method: 'POST',
          body: JSON.stringify(payload),
        })
      }
      return apiRequest<ProviderProtocolEndpoint>(`/admin/provider-sites/${siteId}/protocol-endpoints/${endpointId}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
    },
    onSuccess: () => {
      setEndpointDialogOpen(false)
      setEditingEndpoint(null)
      setEndpointEditorStep('basic')
      setEndpointError(null)
      invalidateProviderSiteDetail(queryClient, siteId)
    },
  })
  const deleteEndpointMutation = useMutation({
    mutationFn: (endpointId: number) =>
      apiRequest<void>(`/admin/provider-sites/${siteId}/protocol-endpoints/${endpointId}`, { method: 'DELETE' }),
    onSuccess: () => invalidateProviderSiteDetail(queryClient, siteId),
  })

  const site = siteQuery.data
  const capabilities = useMemo(
    () => [...((capabilitiesQuery.data ?? []) as SiteModelCapability[])].sort((left, right) => left.modelName.localeCompare(right.modelName)),
    [capabilitiesQuery.data],
  )
  const surfaces = useMemo<Array<[string, SurfaceCapability]>>(
    () => Object.entries((site?.surfaces ?? {}) as Record<string, SurfaceCapability>)
      .sort(([left], [right]) => left.localeCompare(right)),
    [site?.surfaces],
  )
  const features = useMemo<Array<[string, CapabilityResolution]>>(
    () => Object.entries((site?.features ?? {}) as Record<string, CapabilityResolution>)
      .sort(([left], [right]) => left.localeCompare(right)),
    [site?.features],
  )
  const protocolEndpoints = (site?.protocolEndpoints ?? []) as ProviderProtocolEndpoint[]
  const firstError = siteQuery.error ?? capabilitiesQuery.error ?? refreshMutation.error

  if (siteQuery.isPending) {
    return <PageSkeleton count={2} />
  }

  if (!site || firstError) {
    return <InlineError error={firstError ?? new Error('API 入口不存在。')} title="API 入口加载失败" />
  }

  const openCreateEndpoint = () => {
    setEditingEndpoint(null)
    setEndpointEditorStep('basic')
    setEndpointDraft(defaultEndpointDraftForSite(site))
    setEndpointError(null)
    setEndpointDialogOpen(true)
  }

  const openEditEndpoint = (endpoint: ProviderProtocolEndpoint) => {
    setEditingEndpoint(endpoint)
    setEndpointEditorStep('basic')
    setEndpointDraft(endpointToDraft(endpoint))
    setEndpointError(null)
    setEndpointDialogOpen(true)
  }

  const handleSubmitEndpoint = (event: FormEvent) => {
    event.preventDefault()
    try {
      setEndpointError(null)
      endpointMutation.mutate({
        endpointId: editingEndpoint?.id,
        payload: buildEndpointPayload(endpointDraft),
      })
    } catch (error) {
      setEndpointError(error instanceof Error ? error.message : '协议入口保存失败。')
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="厂商管理"
        title={site.displayName}
        actions={(
          <>
            <Link to="/console/provider-sites">
              <Button type="button" variant="outline">
                <ArrowLeftIcon data-icon="inline-start" />
                返回厂商管理
              </Button>
            </Link>
            <Button type="button" variant="outline" onClick={() => refreshMutation.mutate()} disabled={refreshMutation.isPending}>
              <RefreshCwIcon data-icon="inline-start" />
              刷新能力
            </Button>
          </>
        )}
      >
        {firstError ? <InlineError error={firstError} title="API 入口操作失败" /> : null}
        <InfoGrid
          items={[
            { key: 'vendor', label: '厂商', value: site.vendorName || site.vendorCode || site.providerFamily, hint: site.vendorCode ?? site.providerFamily },
            { key: 'kind', label: '协议入口', value: formatEnum(site.siteKind), hint: `${site.providerFamily} / ${site.compatibilitySurface}` },
            { key: 'status', label: '状态', value: <StatusBadge tone={site.active ? 'success' : 'warning'}>{site.active ? '启用' : '停用'}</StatusBadge>, hint: site.healthState },
            { key: 'counts', label: '模型与凭证', value: `${site.modelCount} 模型 / ${site.linkedCredentialCount} 凭证`, hint: `刷新 ${formatInstant(site.refreshedAt)}` },
          ]}
        />
      </PageSection>

      <Tabs value={detailTab} onValueChange={(value) => setDetailTab(value as DetailTab)} className="gap-4">
        <TabsList variant="line" className="w-full justify-start overflow-x-auto">
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="endpoints">协议入口</TabsTrigger>
          <TabsTrigger value="models">模型能力</TabsTrigger>
          <TabsTrigger value="diagnostics">高级诊断</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="mt-0">
          <PageSection kicker="概览" title="调用与兼容画像">
            <InfoGrid
              columnsClassName="md:grid-cols-2 xl:grid-cols-4"
              items={[
                { key: 'auth', label: '鉴权策略', value: site.authStrategy },
                { key: 'path', label: '路径策略', value: site.pathStrategy },
                { key: 'modelAddressing', label: '模型寻址', value: site.modelAddressingStrategy },
                { key: 'errorSchema', label: '错误结构', value: site.errorSchemaStrategy },
                { key: 'baseUrl', label: 'Base URL 匹配', value: site.baseUrlPattern ?? '未配置', className: 'xl:col-span-2' },
                { key: 'requirements', label: '凭证要求', value: summarizeList(site.credentialRequirements, '无', 4) },
                { key: 'protocols', label: '支持协议', value: summarizeList(site.supportedProtocols, '无', 4) },
                { key: 'endpoints', label: '协议入口', value: `${protocolEndpoints.length} 个`, hint: summarizeList(protocolEndpoints.map((endpoint) => endpoint.protocolSuite), '暂无', 3) },
              ]}
            />
            <div className="mt-4 rounded-2xl border border-border/60 bg-muted/20 p-4">
              <div className="mb-2 text-sm font-medium text-foreground">conversation profile</div>
              <pre className="max-h-80 overflow-auto whitespace-pre-wrap break-words rounded-xl bg-background p-3 text-xs text-muted-foreground">
                {JSON.stringify(site.conversationProfile ?? {}, null, 2)}
              </pre>
            </div>
          </PageSection>
        </TabsContent>

        <TabsContent value="endpoints" className="mt-0">
          <PageSection
            kicker="协议入口"
            title="厂商协议入口"
            actions={(
              <Button type="button" variant="outline" onClick={openCreateEndpoint}>
                <PlusIcon data-icon="inline-start" />
                新增入口
              </Button>
            )}
          >
        {endpointMutation.error || deleteEndpointMutation.error ? (
          <InlineError error={endpointMutation.error ?? deleteEndpointMutation.error} title="协议入口操作失败" />
        ) : null}
        {protocolEndpoints.length ? (
          <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
            <table className="w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">入口</th>
                  <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议簇</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">运行时</th>
                  <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Base URL</th>
                  <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">凭证</th>
                  <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                  <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {protocolEndpoints.map((endpoint) => (
                  <tr key={endpoint.id} className="border-b border-border/40 align-top">
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{endpoint.displayName}</div>
                      <div className="truncate text-xs text-muted-foreground" title={endpoint.endpointCode}>{endpoint.endpointCode}</div>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{endpoint.protocolSuite}</td>
                    <td className="px-4 py-3 text-muted-foreground">
                      <div>{endpoint.providerType}</div>
                      <div className="text-xs">{endpoint.siteKind}</div>
                    </td>
                    <td className="truncate px-4 py-3 text-muted-foreground" title={endpoint.baseUrl}>{endpoint.baseUrl}</td>
                    <td className="px-4 py-3 text-muted-foreground">{endpoint.linkedCredentialCount}</td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={endpoint.active ? 'success' : 'warning'}>{endpoint.active ? '启用' : '停用'}</StatusBadge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => openEditEndpoint(endpoint)}>
                          <PencilIcon data-icon="inline-start" />
                          编辑
                        </Button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => deleteEndpointMutation.mutate(endpoint.id)}
                          disabled={deleteEndpointMutation.isPending || endpoint.linkedCredentialCount > 0}
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
        ) : (
          <EmptyState title="暂无协议入口" />
        )}
        {protocolEndpoints.some((endpoint) => Object.keys(endpoint.conversationProfile ?? {}).length > 0) ? (
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            {protocolEndpoints
              .filter((endpoint) => Object.keys(endpoint.conversationProfile ?? {}).length > 0)
              .map((endpoint) => (
                <div key={endpoint.id} className="rounded-2xl border border-border/60 bg-muted/20 p-4">
                  <div className="mb-2 text-sm font-medium text-foreground">{endpoint.displayName} conversation profile</div>
                  <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-xl bg-background p-3 text-xs text-muted-foreground">
                    {JSON.stringify(endpoint.conversationProfile ?? {}, null, 2)}
                  </pre>
                </div>
              ))}
          </div>
        ) : null}
          </PageSection>
        </TabsContent>

      <Dialog
        open={endpointDialogOpen}
        onOpenChange={(open) => {
          setEndpointDialogOpen(open)
          if (!open) {
            setEndpointError(null)
            setEditingEndpoint(null)
            setEndpointEditorStep('basic')
          }
        }}
      >
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>{editingEndpoint ? '编辑协议入口' : '新增协议入口'}</DialogTitle>
            <DialogDescription className="sr-only">配置厂商协议入口。</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4" onSubmit={handleSubmitEndpoint}>
            <Tabs value={endpointEditorStep} onValueChange={(value) => setEndpointEditorStep(value as EndpointEditorStep)}>
              <TabsList variant="line" className="w-full justify-start overflow-x-auto">
                <TabsTrigger value="basic">1. 基本信息</TabsTrigger>
                <TabsTrigger value="runtime">2. 运行时策略</TabsTrigger>
                <TabsTrigger value="advanced">3. 高级 JSON</TabsTrigger>
              </TabsList>

              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">入口编码</span>
                    <Input value={endpointDraft.endpointCode} onChange={(event) => setEndpointDraft({ ...endpointDraft, endpointCode: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">显示名称</span>
                    <Input value={endpointDraft.displayName} onChange={(event) => setEndpointDraft({ ...endpointDraft, displayName: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">协议簇</span>
                    <Input value={endpointDraft.protocolSuite} onChange={(event) => setEndpointDraft({ ...endpointDraft, protocolSuite: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Base URL</span>
                    <Input value={endpointDraft.baseUrl} onChange={(event) => setEndpointDraft({ ...endpointDraft, baseUrl: event.target.value })} />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={endpointDraft.active}
                      onChange={(event) => setEndpointDraft({ ...endpointDraft, active: event.target.checked })}
                    />
                    <span className="text-sm font-medium text-foreground">启用协议入口</span>
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="runtime" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Provider Type</span>
                    <select className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={endpointDraft.providerType} onChange={(event) => setEndpointDraft({ ...endpointDraft, providerType: event.target.value })}>
                      {['OPENAI_DIRECT', 'OPENAI_COMPATIBLE', 'ANTHROPIC_DIRECT', 'GEMINI_DIRECT', 'OLLAMA_DIRECT'].map((option) => (
                        <option key={option} value={option}>{option}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Site Kind</span>
                    <select className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={endpointDraft.siteKind} onChange={(event) => setEndpointDraft({ ...endpointDraft, siteKind: event.target.value })}>
                      {[
                        'OPENAI_DIRECT',
                        'OPENAI_COMPATIBLE_GENERIC',
                        'AZURE_OPENAI',
                        'DEEPSEEK',
                        'QWEN',
                        'MOONSHOT',
                        'SILICONFLOW',
                        'VOLCENGINE',
                        'MINIMAX',
                        'DIFY',
                        'GROK',
                        'MISTRAL',
                        'COHERE',
                        'JINA',
                        'TOGETHER',
                        'FIREWORKS',
                        'OPENROUTER',
                        'PERPLEXITY',
                        'ANTHROPIC_DIRECT',
                        'GEMINI_DIRECT',
                        'OLLAMA_DIRECT',
                        'VERTEX_AI',
                      ].map((option) => (
                        <option key={option} value={option}>{option}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Auth Strategy</span>
                    <Input value={endpointDraft.authStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, authStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Path Strategy</span>
                    <Input value={endpointDraft.pathStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, pathStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Model Addressing</span>
                    <Input value={endpointDraft.modelAddressingStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, modelAddressingStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Error Schema</span>
                    <Input value={endpointDraft.errorSchemaStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, errorSchemaStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">Stream Transport</span>
                    <Input value={endpointDraft.streamTransport} onChange={(event) => setEndpointDraft({ ...endpointDraft, streamTransport: event.target.value })} />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="advanced" className="pt-3">
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">Conversation Profile JSON</span>
                  <Textarea
                    rows={7}
                    value={endpointDraft.conversationProfileJson}
                    onChange={(event) => setEndpointDraft({ ...endpointDraft, conversationProfileJson: event.target.value })}
                  />
                </label>
              </TabsContent>
            </Tabs>
            {endpointError || endpointMutation.error ? (
              <InlineError error={endpointMutation.error ?? new Error(endpointError ?? '协议入口保存失败。')} title="协议入口保存失败" />
            ) : null}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEndpointDialogOpen(false)}>
                取消
              </Button>
              <Button type="submit" disabled={endpointMutation.isPending}>
                保存入口
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

        <TabsContent value="models" className="mt-0">
          <PageSection kicker="模型能力" title="模型能力清单">
            {capabilitiesQuery.isPending ? (
              <PageSkeleton count={1} />
            ) : capabilities.length ? (
              <PaginatedRows items={capabilities} itemLabel="个模型">
                {({ pageItems }) => (
                  <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                    <table className="w-full table-fixed text-sm">
                      <thead className="bg-muted/30">
                        <tr>
                          <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型</th>
                          <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议</th>
                          <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">能力</th>
                          <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">执行后端</th>
                          <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">刷新时间</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pageItems.map((model) => (
                          <tr key={model.id} className="border-b border-border/40 align-top">
                            <td className="px-4 py-3">
                              <div className="font-medium text-foreground">{model.modelName}</div>
                              <div className="text-xs text-muted-foreground">{model.modelKey}</div>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">{summarizeList(model.supportedProtocols, '无', 3)}</td>
                            <td className="px-4 py-3 text-muted-foreground">{summarizeModelTags(model).join(' / ') || '无'}</td>
                            <td className="px-4 py-3 text-muted-foreground">
                              <div>{model.preferredBackend ?? '未指定'}</div>
                              <div className="text-xs">{model.capabilityLevel}</div>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">{formatInstant(model.sourceRefreshedAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            ) : (
              <EmptyState title="暂无模型能力记录" />
            )}
          </PageSection>
        </TabsContent>

        <TabsContent value="diagnostics" className="mt-0">
          <div className="flex flex-col gap-6">
            <PageSection kicker="Surface" title="入口能力矩阵">
              {surfaces.length ? (
                <PaginatedRows items={surfaces} itemLabel="个 surface">
                  {({ pageItems }) => (
                    <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                      <table className="w-full table-fixed text-sm">
                        <thead className="bg-muted/30">
                          <tr>
                            <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Surface</th>
                            <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                            <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">路径</th>
                            <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">能力等级</th>
                            <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">阻断原因</th>
                          </tr>
                        </thead>
                        <tbody>
                          {pageItems.map(([key, surface]) => (
                            <tr key={key} className="border-b border-border/40 align-top">
                              <td className="px-4 py-3">
                                <div className="font-medium text-foreground">{key}</div>
                                <div className="text-xs text-muted-foreground">{surface.resourceType} / {surface.operation}</div>
                              </td>
                              <td className="px-4 py-3">
                                <StatusBadge tone={supportTone(surface.supportStatus)}>{surface.supportStatus ?? 'unknown'}</StatusBadge>
                              </td>
                              <td className="px-4 py-3 text-muted-foreground">{surface.normalizedPath ?? '无'}</td>
                              <td className="px-4 py-3 text-muted-foreground">
                                <div>执行：{surface.executionCapabilityLevel ?? '-'}</div>
                                <div>渲染：{surface.renderCapabilityLevel ?? '-'}</div>
                                <div>综合：{surface.overallCapabilityLevel ?? '-'}</div>
                              </td>
                              <td className="px-4 py-3 text-muted-foreground">{summarizeList(surface.blockerReasons, '无', 2)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </PaginatedRows>
              ) : (
                <EmptyState title="暂无 surface 能力记录" />
              )}
            </PageSection>

            <PageSection kicker="Feature" title="特性解析">
              {features.length ? (
                <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {features.map(([key, resolution]) => (
                    <div key={key} className="rounded-2xl border border-border/60 bg-muted/20 p-4">
                      <div className="mb-2 flex items-center justify-between gap-2">
                        <div className="font-medium text-foreground">{key}</div>
                        <StatusBadge tone={supportTone(resolution.supportStatus)}>{resolution.supportStatus ?? resolution.effectiveLevel ?? 'unknown'}</StatusBadge>
                      </div>
                      <div className="text-xs leading-5 text-muted-foreground">
                        <div>声明：{resolution.declaredLevel ?? '-'}</div>
                        <div>实现：{resolution.implementedLevel ?? '-'}</div>
                        <div>有效：{resolution.effectiveLevel ?? '-'}</div>
                        <div>阻断：{summarizeList(resolution.blockedReasons, '无', 2)}</div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState title="暂无特性解析记录" />
              )}
            </PageSection>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  )
}

function summarizeModelTags(model: SiteModelCapability) {
  const tags: string[] = []
  if (model.supportsChat) tags.push('Chat')
  if (model.supportsTools) tags.push('Tools')
  if (model.supportsImageInput) tags.push('Image')
  if (model.supportsEmbeddings) tags.push('Embeddings')
  if (model.supportsCache) tags.push('Cache')
  if (model.supportsThinking) tags.push('Thinking')
  if (model.supportsVisibleReasoning) tags.push('VisibleReasoning')
  if (model.supportsReasoningReuse) tags.push('ReasoningReuse')
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

function formatEnum(value: string) {
  return value.replaceAll('_', ' ')
}

function supportTone(value?: string | null) {
  const normalized = (value ?? '').toLowerCase()
  if (normalized === 'native' || normalized === 'ready' || normalized === 'healthy') return 'success'
  if (normalized === 'emulated' || normalized === 'degraded') return 'warning'
  if (normalized === 'blocked' || normalized === 'unsupported' || normalized === 'failed') return 'danger'
  return 'neutral'
}

function invalidateProviderSiteDetail(queryClient: ReturnType<typeof useQueryClient>, siteId: number) {
  queryClient.invalidateQueries({ queryKey: ['provider-sites', 'detail', siteId] })
  queryClient.invalidateQueries({ queryKey: ['provider-sites', 'list'] })
  queryClient.invalidateQueries({ queryKey: ['provider-sites', 'credential-options'] })
}

function emptyEndpointDraft(): ProtocolEndpointDraft {
  return {
    endpointCode: '',
    displayName: '',
    protocolSuite: '',
    providerType: 'OPENAI_COMPATIBLE',
    siteKind: 'OPENAI_COMPATIBLE_GENERIC',
    baseUrl: '',
    authStrategy: '',
    pathStrategy: '',
    modelAddressingStrategy: '',
    errorSchemaStrategy: '',
    streamTransport: 'sse',
    conversationProfileJson: '',
    active: true,
  }
}

function defaultEndpointDraftForSite(site: ProviderSite): ProtocolEndpointDraft {
  return {
    endpointCode: `${site.profileCode}:custom`,
    displayName: `${site.displayName} 自定义入口`,
    protocolSuite: `${site.vendorCode ?? site.siteKind.toLowerCase()}.openai_compatible`,
    providerType: providerTypeForSiteKind(site.siteKind),
    siteKind: site.siteKind,
    baseUrl: site.baseUrlPattern ?? '',
    authStrategy: site.authStrategy,
    pathStrategy: site.pathStrategy,
    modelAddressingStrategy: site.modelAddressingStrategy,
    errorSchemaStrategy: site.errorSchemaStrategy,
    streamTransport: site.streamTransport ?? 'sse',
    conversationProfileJson: Object.keys(site.conversationProfile ?? {}).length
      ? JSON.stringify(site.conversationProfile, null, 2)
      : '',
    active: true,
  }
}

function endpointToDraft(endpoint: ProviderProtocolEndpoint): ProtocolEndpointDraft {
  return {
    endpointCode: endpoint.endpointCode,
    displayName: endpoint.displayName,
    protocolSuite: endpoint.protocolSuite,
    providerType: endpoint.providerType,
    siteKind: endpoint.siteKind,
    baseUrl: endpoint.baseUrl,
    authStrategy: endpoint.authStrategy,
    pathStrategy: endpoint.pathStrategy,
    modelAddressingStrategy: endpoint.modelAddressingStrategy,
    errorSchemaStrategy: endpoint.errorSchemaStrategy,
    streamTransport: endpoint.streamTransport ?? 'sse',
    conversationProfileJson: Object.keys(endpoint.conversationProfile ?? {}).length
      ? JSON.stringify(endpoint.conversationProfile, null, 2)
      : '',
    active: endpoint.active,
  }
}

function buildEndpointPayload(draft: ProtocolEndpointDraft) {
  return {
    endpointCode: requireText(draft.endpointCode, '入口编码不能为空。'),
    displayName: requireText(draft.displayName, '显示名称不能为空。'),
    protocolSuite: requireText(draft.protocolSuite, '协议簇不能为空。'),
    providerType: draft.providerType,
    siteKind: draft.siteKind,
    baseUrl: requireText(draft.baseUrl, 'Base URL 不能为空。'),
    authStrategy: draft.authStrategy.trim() || null,
    pathStrategy: draft.pathStrategy.trim() || null,
    modelAddressingStrategy: draft.modelAddressingStrategy.trim() || null,
    errorSchemaStrategy: draft.errorSchemaStrategy.trim() || null,
    streamTransport: draft.streamTransport.trim() || null,
    conversationProfile: parseConversationProfile(draft.conversationProfileJson),
    active: draft.active,
  }
}

function requireText(value: string, message: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    throw new Error(message)
  }
  return trimmed
}

function parseConversationProfile(raw: string) {
  if (!raw.trim()) {
    return {}
  }
  const value = JSON.parse(raw) as unknown
  if (value == null || Array.isArray(value) || typeof value !== 'object') {
    throw new Error('Conversation Profile 必须是 JSON 对象。')
  }
  return value as Record<string, unknown>
}

function providerTypeForSiteKind(siteKind: string) {
  switch (siteKind) {
    case 'OPENAI_DIRECT':
    case 'AZURE_OPENAI':
      return 'OPENAI_DIRECT'
    case 'ANTHROPIC_DIRECT':
      return 'ANTHROPIC_DIRECT'
    case 'GEMINI_DIRECT':
    case 'VERTEX_AI':
      return 'GEMINI_DIRECT'
    case 'OLLAMA_DIRECT':
      return 'OLLAMA_DIRECT'
    default:
      return 'OPENAI_COMPATIBLE'
  }
}
