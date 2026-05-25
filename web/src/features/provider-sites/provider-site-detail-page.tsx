import { type FormEvent, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeftIcon, PencilIcon, PlusIcon, RefreshCwIcon, Trash2Icon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
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

type JsonRecord = Record<string, unknown>
type CompatibilityProfile = 'default' | 'openai_chat_completions' | 'responses_to_chat_completions' | 'anthropic_messages' | 'gemini_generate_content'
type ReasoningRequestMode = 'none' | 'extra_body_thinking_enabled'
type AssistantReasoningField = 'none' | 'reasoning_content' | 'reasoning'
type HistoryReplayPolicy = 'none' | 'required_when_tool_calls'

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
  compatibilityProfile: CompatibilityProfile
  reasoningRequestMode: ReasoningRequestMode
  assistantReasoningField: AssistantReasoningField
  historyReplayPolicy: HistoryReplayPolicy
  preservedConversationProfile: JsonRecord
  active: boolean
}

type DetailTab = 'overview' | 'endpoints' | 'models' | 'diagnostics'
type EndpointEditorStep = 'basic' | 'runtime' | 'profile'

const COMPATIBILITY_PROFILE_OPTIONS: Array<{ value: CompatibilityProfile; label: string }> = [
  { value: 'default', label: '默认直连' },
  { value: 'openai_chat_completions', label: 'OpenAI-compatible Chat Completions' },
  { value: 'responses_to_chat_completions', label: 'Responses 转 Chat Completions' },
  { value: 'anthropic_messages', label: 'Anthropic Messages' },
  { value: 'gemini_generate_content', label: 'Gemini GenerateContent' },
]

const REASONING_REQUEST_OPTIONS: Array<{ value: ReasoningRequestMode; label: string }> = [
  { value: 'none', label: '不注入' },
  { value: 'extra_body_thinking_enabled', label: 'extra_body.thinking = enabled' },
]

const ASSISTANT_REASONING_FIELD_OPTIONS: Array<{ value: AssistantReasoningField; label: string }> = [
  { value: 'none', label: '不指定' },
  { value: 'reasoning_content', label: 'reasoning_content' },
  { value: 'reasoning', label: 'reasoning' },
]

const HISTORY_REPLAY_OPTIONS: Array<{ value: HistoryReplayPolicy; label: string }> = [
  { value: 'none', label: '不限制' },
  { value: 'required_when_tool_calls', label: '工具调用时要求 reasoning' },
]

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
  const featureMap = (site?.features ?? {}) as Record<string, CapabilityResolution>
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
              <div className="mb-2 text-sm font-medium text-foreground">站点运行时画像</div>
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
                  <div className="mb-2 text-sm font-medium text-foreground">{endpoint.displayName} 运行时画像</div>
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
                <TabsTrigger value="profile">3. 兼容画像</TabsTrigger>
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
                    <span className="text-sm font-medium text-foreground">厂商类型</span>
                    <select className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={endpointDraft.providerType} onChange={(event) => setEndpointDraft({ ...endpointDraft, providerType: event.target.value })}>
                      {['OPENAI_DIRECT', 'OPENAI_COMPATIBLE', 'ANTHROPIC_DIRECT', 'GEMINI_DIRECT', 'OLLAMA_DIRECT'].map((option) => (
                        <option key={option} value={option}>{option}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">站点类型</span>
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
                    <span className="text-sm font-medium text-foreground">鉴权策略</span>
                    <Input value={endpointDraft.authStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, authStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">路径策略</span>
                    <Input value={endpointDraft.pathStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, pathStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">模型寻址策略</span>
                    <Input value={endpointDraft.modelAddressingStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, modelAddressingStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">错误结构策略</span>
                    <Input value={endpointDraft.errorSchemaStrategy} onChange={(event) => setEndpointDraft({ ...endpointDraft, errorSchemaStrategy: event.target.value })} />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">流式传输方式</span>
                    <Input value={endpointDraft.streamTransport} onChange={(event) => setEndpointDraft({ ...endpointDraft, streamTransport: event.target.value })} />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="profile" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">兼容画像</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={endpointDraft.compatibilityProfile}
                      onChange={(event) => setEndpointDraft({ ...endpointDraft, compatibilityProfile: event.target.value as CompatibilityProfile })}
                    >
                      {COMPATIBILITY_PROFILE_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Thinking 注入</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={endpointDraft.reasoningRequestMode}
                      onChange={(event) => setEndpointDraft({ ...endpointDraft, reasoningRequestMode: event.target.value as ReasoningRequestMode })}
                    >
                      {REASONING_REQUEST_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">Assistant Reasoning 字段</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={endpointDraft.assistantReasoningField}
                      onChange={(event) => setEndpointDraft({ ...endpointDraft, assistantReasoningField: event.target.value as AssistantReasoningField })}
                    >
                      {ASSISTANT_REASONING_FIELD_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">工具历史回放</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={endpointDraft.historyReplayPolicy}
                      onChange={(event) => setEndpointDraft({ ...endpointDraft, historyReplayPolicy: event.target.value as HistoryReplayPolicy })}
                    >
                      {HISTORY_REPLAY_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                  </label>
                  <div className="rounded-2xl border border-border/60 bg-muted/20 p-4 md:col-span-2">
                    <div className="mb-2 text-sm font-medium text-foreground">运行时画像预览</div>
                    <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-xl bg-background p-3 text-xs text-muted-foreground">
                      {JSON.stringify(buildConversationProfile(endpointDraft), null, 2)}
                    </pre>
                  </div>
                </div>
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
                            <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                            <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">路径</th>
                            <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">能力等级</th>
                            <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">特性解析</th>
                            <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">阻断</th>
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
                              <td className="px-4 py-3 text-muted-foreground">
                                <FeatureResolutionSummary surface={surface} featureMap={featureMap} />
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
          </div>
        </TabsContent>
      </Tabs>
    </div>
  )
}

function FeatureResolutionSummary({
  surface,
  featureMap,
}: {
  surface: SurfaceCapability
  featureMap: Record<string, CapabilityResolution>
}) {
  const featureKeys = surface.requiredFeatures.length
    ? surface.requiredFeatures
    : Object.keys(surface.featureResolutions ?? {})
  if (!featureKeys.length) {
    return <span>无</span>
  }
  return (
    <div className="flex flex-col gap-1">
      {featureKeys.map((featureKey) => {
        const resolution = surface.featureResolutions?.[featureKey] ?? featureMap[featureKey]
        const status = resolution?.supportStatus ?? resolution?.effectiveLevel ?? 'unknown'
        return (
          <div key={featureKey} className="flex min-w-0 items-center justify-between gap-2">
            <span className="truncate" title={featureKey}>{featureKey}</span>
            <StatusBadge tone={supportTone(status)}>{status}</StatusBadge>
          </div>
        )
      })}
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
    compatibilityProfile: 'default',
    reasoningRequestMode: 'none',
    assistantReasoningField: 'none',
    historyReplayPolicy: 'none',
    preservedConversationProfile: {},
    active: true,
  }
}

function defaultEndpointDraftForSite(site: ProviderSite): ProtocolEndpointDraft {
  const profileFields = profileToDraftFields(site.conversationProfile ?? {})
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
    ...profileFields,
    active: true,
  }
}

function endpointToDraft(endpoint: ProviderProtocolEndpoint): ProtocolEndpointDraft {
  const profileFields = profileToDraftFields(endpoint.conversationProfile ?? {})
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
    ...profileFields,
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
    conversationProfile: buildConversationProfile(draft),
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

function profileToDraftFields(profile: JsonRecord) {
  const compatibilityProfile = inferCompatibilityProfile(profile)
  const reasoningRequestMode = inferReasoningRequestMode(profile)
  const assistantReasoningField = inferAssistantReasoningField(profile)
  const historyReplayPolicy = inferHistoryReplayPolicy(profile)
  return {
    compatibilityProfile,
    reasoningRequestMode,
    assistantReasoningField,
    historyReplayPolicy,
    preservedConversationProfile: stripStructuredConversationProfile(profile, {
      compatibilityProfile,
      reasoningRequestMode,
      assistantReasoningField,
      historyReplayPolicy,
    }),
  }
}

function buildConversationProfile(draft: ProtocolEndpointDraft): JsonRecord {
  const profile: JsonRecord = { ...draft.preservedConversationProfile }
  switch (draft.compatibilityProfile) {
    case 'openai_chat_completions':
      profile.upstreamSurface = 'chat_completions'
      break
    case 'responses_to_chat_completions':
      profile.ingressProtocol = 'responses'
      profile.upstreamSurface = 'chat_completions'
      profile.responsesCompatibility = { mode: 'emulate_with_chat_completions' }
      break
    case 'anthropic_messages':
      profile.targetProtocol = 'anthropic_messages'
      profile.reasoningTransport = 'thinking_blocks'
      break
    case 'gemini_generate_content':
      profile.targetProtocol = 'gemini_generate_content'
      break
    default:
      break
  }

  const reasoning = isJsonRecord(profile.reasoning) ? { ...profile.reasoning } : {}
  if (draft.reasoningRequestMode === 'extra_body_thinking_enabled') {
    reasoning.requestField = 'extra_body.thinking'
    reasoning.requestEnabledValue = { type: 'enabled' }
  }
  if (draft.assistantReasoningField !== 'none') {
    reasoning.assistantReasoningField = draft.assistantReasoningField
  }
  if (draft.historyReplayPolicy !== 'none') {
    reasoning.historyReplayPolicy = draft.historyReplayPolicy
  }
  if (Object.keys(reasoning).length) {
    profile.reasoning = reasoning
  } else {
    delete profile.reasoning
  }
  return profile
}

function inferCompatibilityProfile(profile: JsonRecord): CompatibilityProfile {
  const responsesCompatibility = isJsonRecord(profile.responsesCompatibility) ? profile.responsesCompatibility : {}
  const responsesMode = stringValue(responsesCompatibility.mode ?? profile.responsesMode)
  if (responsesMode === 'emulate_with_chat_completions' || profile.ingressProtocol === 'responses') {
    return 'responses_to_chat_completions'
  }
  if (profile.targetProtocol === 'anthropic_messages') {
    return 'anthropic_messages'
  }
  if (profile.targetProtocol === 'gemini_generate_content') {
    return 'gemini_generate_content'
  }
  if (profile.upstreamSurface === 'chat_completions') {
    return 'openai_chat_completions'
  }
  return 'default'
}

function inferReasoningRequestMode(profile: JsonRecord): ReasoningRequestMode {
  const reasoning = isJsonRecord(profile.reasoning) ? profile.reasoning : {}
  const requestEnabledValue = isJsonRecord(reasoning.requestEnabledValue) ? reasoning.requestEnabledValue : {}
  if (reasoning.requestField === 'extra_body.thinking' && requestEnabledValue.type === 'enabled') {
    return 'extra_body_thinking_enabled'
  }
  return 'none'
}

function inferAssistantReasoningField(profile: JsonRecord): AssistantReasoningField {
  const reasoning = isJsonRecord(profile.reasoning) ? profile.reasoning : {}
  if (reasoning.assistantReasoningField === 'reasoning_content' || reasoning.assistantReasoningField === 'reasoning') {
    return reasoning.assistantReasoningField
  }
  return 'none'
}

function inferHistoryReplayPolicy(profile: JsonRecord): HistoryReplayPolicy {
  const reasoning = isJsonRecord(profile.reasoning) ? profile.reasoning : {}
  return reasoning.historyReplayPolicy === 'required_when_tool_calls' ? 'required_when_tool_calls' : 'none'
}

function stripStructuredConversationProfile(
  profile: JsonRecord,
  structured: {
    compatibilityProfile: CompatibilityProfile
    reasoningRequestMode: ReasoningRequestMode
    assistantReasoningField: AssistantReasoningField
    historyReplayPolicy: HistoryReplayPolicy
  },
): JsonRecord {
  const preserved: JsonRecord = { ...profile }
  switch (structured.compatibilityProfile) {
    case 'responses_to_chat_completions':
      delete preserved.ingressProtocol
      delete preserved.upstreamSurface
      delete preserved.responsesCompatibility
      delete preserved.responsesMode
      break
    case 'openai_chat_completions':
      delete preserved.upstreamSurface
      break
    case 'anthropic_messages':
      delete preserved.targetProtocol
      delete preserved.reasoningTransport
      break
    case 'gemini_generate_content':
      delete preserved.targetProtocol
      break
    default:
      break
  }

  if (isJsonRecord(preserved.reasoning)) {
    const reasoning = { ...preserved.reasoning }
    if (structured.reasoningRequestMode !== 'none') {
      delete reasoning.requestField
      delete reasoning.requestEnabledValue
    }
    if (structured.assistantReasoningField !== 'none') {
      delete reasoning.assistantReasoningField
    }
    if (structured.historyReplayPolicy !== 'none') {
      delete reasoning.historyReplayPolicy
    }
    if (Object.keys(reasoning).length) {
      preserved.reasoning = reasoning
    } else {
      delete preserved.reasoning
    }
  }
  return preserved
}

function isJsonRecord(value: unknown): value is JsonRecord {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}
