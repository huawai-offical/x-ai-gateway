import { startTransition, type FormEvent, useDeferredValue, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  ArrowUpRightIcon,
  BrainCircuitIcon,
  LoaderCircleIcon,
  PlayIcon,
  RefreshCcwIcon,
  SparklesIcon,
  WaypointsIcon,
  WorkflowIcon,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { MetricCard } from '@/components/app/metric-card'
import { PageSection } from '@/components/app/page-section'
import { StatusBadge } from '@/components/app/status-badge'
import { apiClient } from '@/lib/api'
import { useTypedMutation, useTypedQuery } from '@/lib/typed-react-query'
import { type ObservabilityTraceResponse } from '../traces/types'
import {
  AttemptCard,
  DraftStatus,
  ExecutionResultCard,
  Field,
  JsonBlock,
  KeyValueGrid,
  ListBlock,
  PlanNarrativeCard,
  StageLoading,
  TraceTimeline,
  WorkbenchDisclosure,
  WorkbenchPanel,
  WorkbenchStage,
} from './workbench-components'
import { DEBUG_PRESETS, type DebugPreset } from './workbench-presets'
import {
  featureLabel,
  type AdminChatExecuteResponse,
  type AdminResourceExecuteResponse,
  type ExecutionPreview,
  type GatewayUsageView,
  type RouteSelectionPreview,
} from './types'
import { isChatLikePath, isDebugExecutablePath, isMultipartResourcePath } from './utils'

type WorkbenchPreviewBundle = {
  routingPreview: RouteSelectionPreview
  executionPreview: ExecutionPreview
}

export function WorkbenchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [distributedKeyPrefix, setDistributedKeyPrefix] = useState(searchParams.get('distributedKeyPrefix') ?? 'sk-gw-test')
  const [protocol, setProtocol] = useState(searchParams.get('protocol') ?? 'openai')
  const [method, setMethod] = useState(searchParams.get('method') ?? 'POST')
  const [requestPath, setRequestPath] = useState(searchParams.get('requestPath') ?? '/v1/chat/completions')
  const [requestedModel, setRequestedModel] = useState(searchParams.get('requestedModel') ?? 'gpt-4o')
  const [body, setBody] = useState(searchParams.get('body') ?? '{"model":"gpt-4o","messages":[{"role":"user","content":"hello"}]}')
  const [formFields, setFormFields] = useState(searchParams.get('formFields') ?? '{"model":"gpt-4o-mini-transcribe"}')
  const [fileRefs, setFileRefs] = useState(searchParams.get('fileRefs') ?? '[{"fieldName":"file","fileKey":"file-123"}]')
  const [inputError, setInputError] = useState<unknown>(null)

  const deferredBody = useDeferredValue(body)
  const deferredFormFields = useDeferredValue(formFields)
  const deferredFileRefs = useDeferredValue(fileRefs)

  const multipartMode = isMultipartResourcePath(requestPath)
  const canExecute = isDebugExecutablePath(requestPath)

  const previewMutation = useTypedMutation<WorkbenchPreviewBundle, void>({
    mutationFn: async () => {
      const previewRequest = buildPreviewRequest({
        distributedKeyPrefix,
        protocol,
        requestPath,
        requestedModel,
        body,
        multipartMode,
        formFields,
        fileRefs,
      })

      const [routingPreview, executionPreview] = await Promise.all([
        apiClient.post<RouteSelectionPreview>('/admin/routing/preview', {
          body: previewRequest,
        }),
        apiClient.post<ExecutionPreview>('/admin/execution/preview', {
          body: previewRequest,
        }),
      ])

      return { routingPreview, executionPreview }
    },
    onSuccess: () => {
      persistSearchParams({ requestId: undefined })
    },
  })

  const executeMutation = useTypedMutation<AdminChatExecuteResponse, void>({
    mutationFn: async () => {
      const parsedBody = parseJsonBody(body)
      return apiClient.post<AdminChatExecuteResponse>('/admin/chat/execute', {
        body: {
          distributedKeyPrefix,
          protocol,
          requestPath,
          requestedModel,
          body: parsedBody,
        },
      })
    },
    onSuccess: (data) => {
      persistSearchParams({ requestId: data.requestId })
    },
  })

  const resourceExecuteMutation = useTypedMutation<AdminResourceExecuteResponse, void>({
    mutationFn: async () => {
      const parsedBody = multipartMode ? safeParseJsonBody(body) : parseJsonBody(body)
      return apiClient.post<AdminResourceExecuteResponse>('/admin/resource/execute', {
        body: {
          distributedKeyPrefix,
          protocol,
          method,
          requestPath,
          requestedModel,
          body: parsedBody,
          formFields: multipartMode ? parseJsonObject(formFields) : undefined,
          fileRefs: multipartMode ? parseJsonArray(fileRefs) : undefined,
        },
      })
    },
    onSuccess: (data) => {
      persistSearchParams({ requestId: data.requestId ?? undefined })
    },
  })

  const previewBundle = previewMutation.data
  const routingPreview = previewBundle?.routingPreview ?? null
  const executionPreview = previewBundle?.executionPreview ?? null
  const executeResult = executeMutation.data
  const resourceExecuteResult = resourceExecuteMutation.data

  const activePlan = resourceExecuteResult?.plan ?? executeResult?.plan ?? routingPreview?.plan ?? null
  const activeSelection = resourceExecuteResult?.routeSelection ?? executeResult?.routeSelection ?? routingPreview?.selection ?? null
  const activeRequestId = resourceExecuteResult?.requestId ?? executeResult?.requestId ?? searchParams.get('requestId') ?? null
  const traceQuery = useTypedQuery<ObservabilityTraceResponse>({
    queryKey: ['workbench-trace', activeRequestId],
    queryFn: () =>
      apiClient.get<ObservabilityTraceResponse>(
        `/admin/observability/traces/${encodeURIComponent(activeRequestId ?? '')}`,
      ),
    enabled: Boolean(activeRequestId),
  })

  const activePreset = useMemo(
    () => DEBUG_PRESETS.find((preset) => preset.method === method && preset.requestPath === requestPath) ?? null,
    [method, requestPath],
  )

  const bodyDraft = useMemo(() => inspectJsonDraft(deferredBody), [deferredBody])
  const formFieldsDraft = useMemo(
    () => (multipartMode ? inspectJsonDraft(deferredFormFields, 'object') : null),
    [deferredFormFields, multipartMode],
  )
  const fileRefsDraft = useMemo(
    () => (multipartMode ? inspectJsonDraft(deferredFileRefs, 'array') : null),
    [deferredFileRefs, multipartMode],
  )

  const currentError = inputError
    ?? previewMutation.error
    ?? executeMutation.error
    ?? resourceExecuteMutation.error

  const requestSummary = [
    { label: '预设', value: activePreset?.label ?? '自定义', hint: activePreset?.hint ?? '手动定义输入参数' },
    {
      label: '模式',
      value: multipartMode ? '多段资源' : isChatLikePath(requestPath) ? '对话请求' : '资源请求',
      hint: method,
    },
    { label: '请求路径', value: requestPath, hint: protocol },
    { label: '当前结果', value: activeRequestId ?? '仅预览', hint: activePlan?.supportStatus ?? '尚未生成计划' },
  ]

  const candidateEvaluations = routingPreview?.candidateEvaluations ?? activeSelection?.candidateEvaluations ?? []
  const attempts = activeSelection?.attempts ?? []
  const traceTimeline = buildTraceTimeline(traceQuery.data)
  const usageSummary = buildUsageSummary(executeResult?.usage)

  const handlePreview = async (event: FormEvent) => {
    event.preventDefault()
    try {
      setInputError(null)
      await previewMutation.mutateAsync()
    } catch (error) {
      setInputError(error)
    }
  }

  const handleExecute = async () => {
    try {
      setInputError(null)
      if (isChatLikePath(requestPath)) {
        await executeMutation.mutateAsync()
        return
      }
      await resourceExecuteMutation.mutateAsync()
    } catch (error) {
      setInputError(error)
    }
  }

  const handleClear = () => {
    previewMutation.reset()
    executeMutation.reset()
    resourceExecuteMutation.reset()
    setInputError(null)
    persistSearchParams({ requestId: undefined })
  }

  const applyPreset = (preset: DebugPreset) => {
    startTransition(() => {
      setProtocol(preset.protocol)
      setMethod(preset.method)
      setRequestPath(preset.requestPath)
      setRequestedModel(preset.requestedModel)
      setBody(preset.body)
      setFormFields(preset.formFields ?? '{"model":"gpt-4o-mini-transcribe"}')
      setFileRefs(preset.fileRefs ?? '[{"fieldName":"file","fileKey":"file-123"}]')
      setInputError(null)
    })
    previewMutation.reset()
    executeMutation.reset()
    resourceExecuteMutation.reset()
    persistSearchParams({
      protocol: preset.protocol,
      method: preset.method,
      requestPath: preset.requestPath,
      requestedModel: preset.requestedModel,
      body: preset.body,
      formFields: preset.formFields ?? '{"model":"gpt-4o-mini-transcribe"}',
      fileRefs: preset.fileRefs ?? '[{"fieldName":"file","fileKey":"file-123"}]',
      requestId: undefined,
    })
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="relative overflow-hidden rounded-3xl border border-white/10 bg-gradient-to-br from-primary/10 via-background/50 to-muted/30 p-6 backdrop-blur-md shadow-lg">
        <div className="absolute -left-16 -top-16 h-36 w-36 rounded-full bg-primary/20 blur-3xl" />
        <div className="flex flex-col xl:flex-row items-start xl:items-center justify-between gap-6 relative z-10">
          <div className="space-y-1.5 max-w-2xl">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary dark:text-primary-foreground">
              <span className="h-1.5 w-1.5 rounded-full bg-primary animate-pulse" />
              调试台
            </span>
            <h2 className="text-xl font-bold tracking-tight text-foreground">网关白盒调试台</h2>
          </div>
          <div className="flex flex-wrap items-center gap-3 text-xs font-medium">
            <div className="flex items-center gap-2 bg-background/50 backdrop-blur border border-border/40 rounded-xl p-2.5 shadow-sm">
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-primary/10 text-[10px] font-bold text-primary">1</span>
              <span>配置请求草稿</span>
            </div>
            <span className="text-muted-foreground hidden sm:inline">➔</span>
            <div className="flex items-center gap-2 bg-background/50 backdrop-blur border border-border/40 rounded-xl p-2.5 shadow-sm">
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-500/10 text-[10px] font-bold text-emerald-600">2</span>
              <span>预览上游翻译</span>
            </div>
            <span className="text-muted-foreground hidden sm:inline">➔</span>
            <div className="flex items-center gap-2 bg-background/50 backdrop-blur border border-border/40 rounded-xl p-2.5 shadow-sm">
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-amber-500/10 text-[10px] font-bold text-amber-600">3</span>
              <span>执行请求</span>
            </div>
          </div>
        </div>
      </div>

      <PageSection
        kicker="调试台"
        title="白盒翻译调试"
        actions={
          <div className="flex flex-wrap gap-2">
            <StatusBadge tone={canExecute ? 'info' : 'warning'}>
              {canExecute ? '可执行' : '仅预览'}
            </StatusBadge>
            <StatusBadge>{multipartMode ? '多段资源' : method}</StatusBadge>
            <StatusBadge tone={bodyDraft.valid ? 'success' : 'danger'}>
              请求体 · {bodyDraft.summary}
            </StatusBadge>
            {activePlan?.supportStatus ? (
              <StatusBadge tone={toneForSupportStatus(activePlan.supportStatus)}>
                {activePlan.supportStatus}
              </StatusBadge>
            ) : null}
          </div>
        }
      >
        <div className="grid gap-5 2xl:grid-cols-[minmax(0,1.2fr)_minmax(19rem,0.8fr)]">
          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
            {requestSummary.map((item) => (
              <MetricCard key={item.label} label={item.label} value={item.value} hint={item.hint} />
            ))}
          </div>

          <Card className="border-border/60 bg-card/92 shadow-sm">
            <CardHeader className="gap-2 border-b border-border/60">
              <CardTitle className="text-base">下一步入口</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 p-5">
              {activeRequestId ? (
                <Button asChild variant="outline" size="sm">
                  <Link to={`/traces?requestId=${encodeURIComponent(activeRequestId)}`}>
                    打开追踪工作台
                    <ArrowUpRightIcon data-icon="inline-end" />
                  </Link>
                </Button>
              ) : null}
              {!activeRequestId ? (
                <div className="rounded-2xl border border-dashed border-border/60 bg-background/60 px-4 py-3 text-sm text-muted-foreground">
                  执行后可从这里继续排查。
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>

        {currentError ? <InlineError error={currentError} title="调试工作台预览或执行失败" /> : null}

        <div className="grid gap-7 2xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.95fr)]">
          <WorkbenchStage
            title="客户端请求"
            kicker="步骤 1"
            icon={<BrainCircuitIcon className="size-4" />}
          >
            <div className="grid gap-6 xl:grid-cols-[minmax(17rem,0.78fr)_minmax(0,1.22fr)]">
              <div className="grid gap-5">
                <WorkbenchPanel title="快捷预设">
                  <div className="grid gap-3">
                    {DEBUG_PRESETS.map((preset) => (
                      <button
                        key={preset.id}
                        type="button"
                        onClick={() => applyPreset(preset)}
                        className="flex flex-col items-start gap-2 rounded-3xl border border-border/60 bg-background/90 px-4 py-4 text-left transition-colors hover:border-primary/30 hover:bg-accent/40"
                      >
                        <div className="flex w-full items-center justify-between gap-3">
                          <span className="font-medium text-foreground">{preset.label}</span>
                          {activePreset?.id === preset.id ? <StatusBadge tone="info">已启用</StatusBadge> : null}
                        </div>
                        <span className="text-sm leading-6 text-muted-foreground">{preset.hint}</span>
                      </button>
                    ))}
                  </div>
                </WorkbenchPanel>

                <WorkbenchPanel title="草稿校验">
                  <div className="grid gap-3">
                    <DraftStatus label="请求体" summary={bodyDraft.summary} valid={bodyDraft.valid} />
                    {formFieldsDraft ? (
                      <DraftStatus label="表单字段" summary={formFieldsDraft.summary} valid={formFieldsDraft.valid} />
                    ) : null}
                    {fileRefsDraft ? (
                      <DraftStatus label="文件引用" summary={fileRefsDraft.summary} valid={fileRefsDraft.valid} />
                    ) : null}
                  </div>
                </WorkbenchPanel>
              </div>

              <form className="flex flex-col gap-6" onSubmit={handlePreview}>
                <WorkbenchPanel title="请求上下文">
                  <div className="grid gap-4 xl:grid-cols-2">
                    <Field label="分布式 Key 前缀">
                      <Input value={distributedKeyPrefix} onChange={(event) => setDistributedKeyPrefix(event.target.value)} />
                    </Field>
                    <Field label="协议">
                      <Input value={protocol} onChange={(event) => setProtocol(event.target.value)} />
                    </Field>
                    <Field label="方法">
                      <Input value={method} onChange={(event) => setMethod(event.target.value.toUpperCase())} />
                    </Field>
                    <Field label="请求模型">
                      <Input value={requestedModel} onChange={(event) => setRequestedModel(event.target.value)} />
                    </Field>
                  </div>
                </WorkbenchPanel>

                <WorkbenchPanel title="请求内容">
                  <div className="grid gap-5">
                    <Field label="请求路径">
                      <Input value={requestPath} onChange={(event) => setRequestPath(event.target.value)} />
                    </Field>

                    <Field label="请求体">
                      <Textarea value={body} onChange={(event) => setBody(event.target.value)} rows={10} />
                    </Field>

                    {multipartMode ? (
                      <div className="grid gap-4 xl:grid-cols-2">
                        <Field label="表单字段">
                          <Textarea value={formFields} onChange={(event) => setFormFields(event.target.value)} rows={7} />
                        </Field>
                        <Field label="文件引用">
                          <Textarea value={fileRefs} onChange={(event) => setFileRefs(event.target.value)} rows={7} />
                        </Field>
                      </div>
                    ) : null}
                  </div>
                </WorkbenchPanel>

                <WorkbenchPanel title="执行动作">
                  <div className="flex flex-wrap items-center gap-3">
                    <Button type="submit" disabled={previewMutation.isPending}>
                      {previewMutation.isPending ? (
                        <LoaderCircleIcon className="animate-spin" data-icon="inline-start" />
                      ) : (
                        <WorkflowIcon data-icon="inline-start" />
                      )}
                      生成白盒预览
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={handleExecute}
                      disabled={!canExecute || executeMutation.isPending || resourceExecuteMutation.isPending}
                    >
                      {executeMutation.isPending || resourceExecuteMutation.isPending ? (
                        <LoaderCircleIcon className="animate-spin" data-icon="inline-start" />
                      ) : (
                        <PlayIcon data-icon="inline-start" />
                      )}
                      {isChatLikePath(requestPath) ? '执行对话调试' : '执行资源调试'}
                    </Button>
                    <Button type="button" variant="secondary" onClick={handleClear}>
                      <RefreshCcwIcon data-icon="inline-start" />
                      清空舞台
                    </Button>
                  </div>
                </WorkbenchPanel>
              </form>
            </div>
          </WorkbenchStage>

          <div className="grid gap-6">
            <WorkbenchStage
              title="规范化计划"
            kicker="步骤 2"
              icon={<WorkflowIcon className="size-4" />}
            >
              {routingPreview && activePlan ? (
                <div className="flex flex-col gap-5">
                  <PlanNarrativeCard
                    title="请求语义与能力"
                    items={[
                      `所需能力：${routingPreview.requestedSemantics.requiredFeatures.map(formatRequiredFeature).join('、') || '无'}`,
                      `路由选择模式：${activePlan.routeSelectionMode ?? '-'}`,
                      `执行后端：${activePlan.executionBackend ?? '-'}`,
                      `支持状态：${activePlan.supportStatus ?? '-'}`,
                      ...(activePlan.requiredFeatures.length
                        ? activePlan.requiredFeatures.map((feature) => `${formatRequiredFeature(feature)} · ${resolveFeatureLevel(activePlan.featureLevels, feature)}`)
                        : ['能力判定：当前请求未声明额外所需能力。']),
                    ]}
                  />

                  <PlanNarrativeCard
                    title="策略原因"
                    items={[
                      `路由策略原因：${activePlan.routePolicyReason ?? '-'}`,
                      `渲染策略原因：${activePlan.renderPolicyReason ?? '-'}`,
                      `回退策略原因：${activePlan.fallbackPolicyReason ?? '-'}`,
                      `后端选择原因：${activePlan.backendReason ?? '-'}`,
                    ]}
                  />

                  <PlanNarrativeCard
                    title="降级与阻断"
                    items={[
                      ...activePlan.degradations,
                      ...activePlan.blockerReasons,
                      ...activePlan.blockers,
                    ].length
                      ? [
                          ...activePlan.degradations,
                          ...activePlan.blockerReasons,
                          ...activePlan.blockers,
                        ]
                      : ['当前计划没有阻断或降级。']}
                  />
                </div>
              ) : previewMutation.isPending ? (
                <StageLoading text="正在生成规范化计划…" />
              ) : (
                <EmptyState
                  title="先生成白盒预览"
                  icon={<WorkflowIcon className="size-5" />}
                />
              )}
            </WorkbenchStage>

            <WorkbenchStage
              title="上游载荷与结果"
            kicker="步骤 3"
              icon={<SparklesIcon className="size-4" />}
            >
              {executionPreview ? (
                <div className="flex flex-col gap-5">
                  <ExecutionResultCard
                    requestPath={requestPath}
                    executeResult={executeResult}
                    resourceExecuteResult={resourceExecuteResult}
                    usageSummary={usageSummary}
                  />

                  <div className="grid gap-5 xl:grid-cols-2">
                    <PlanNarrativeCard
                      title="绑定摘要"
                      items={[
                        `绑定优先级：${executionPreview.providerBindingSummary.bindingPriority ?? '-'}`,
                        `绑定权重：${executionPreview.providerBindingSummary.bindingWeight ?? '-'}`,
                        `能力等级：${executionPreview.providerBindingSummary.capabilityLevel ?? '-'}`,
                        `解析后模型：${executionPreview.translatedUpstreamPayload.resolvedModel ?? '-'}`,
                      ]}
                    />

                    <PlanNarrativeCard
                      title="规范化结果预告"
                      items={executionPreview.normalizedResponsePreview.notes.length
                        ? executionPreview.normalizedResponsePreview.notes
                        : ['执行后可在此查看真实规范化结果。']}
                    />
                  </div>

                  <Card className="border-border/60 bg-background/90 shadow-none">
                    <CardHeader className="gap-2 border-b border-border/60">
                      <CardTitle className="text-base">上游执行载荷预览</CardTitle>
                    </CardHeader>
                    <CardContent className="flex flex-col gap-5 p-5">
                      {executionPreview.translatedUpstreamPayload.messages.length ? (
                        executionPreview.translatedUpstreamPayload.messages.map((message, index) => (
                          <div key={`${message.role}-${index}`} className="rounded-2xl border border-border/60 bg-card px-4 py-3">
                            <div className="flex items-center justify-between gap-3">
                              <div className="font-medium text-foreground">{message.role ?? '未知角色'}</div>
                              <StatusBadge>{message.parts.length} 个片段</StatusBadge>
                            </div>
                            {message.text ? (
                              <div className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">
                                {message.text}
                              </div>
                            ) : null}
                            <div className="mt-3 flex flex-wrap gap-2">
                              {message.parts.map((part, partIndex) => (
                                <StatusBadge key={`${part.type}-${partIndex}`} tone={toneForPayloadPart(part.type)}>
                                  {part.type}
                                </StatusBadge>
                              ))}
                            </div>
                          </div>
                        ))
                      ) : (
                        <div className="text-sm text-muted-foreground">当前载荷没有可展示的消息。</div>
                      )}

                      <JsonBlock title="提供方选项" value={executionPreview.translatedUpstreamPayload.providerOptions} />
                    </CardContent>
                  </Card>
                </div>
              ) : previewMutation.isPending ? (
                <StageLoading text="正在生成上游载荷预览…" />
              ) : (
                <EmptyState
                  title="右侧区域等待预览或执行"
                  icon={<SparklesIcon className="size-5" />}
                />
              )}
            </WorkbenchStage>
          </div>
        </div>

        <div className="grid gap-5 xl:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)]">
          <div className="grid gap-5">
            <div className="px-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">执行观察</div>
            <WorkbenchDisclosure title="追踪时间线" defaultOpen={Boolean(activeRequestId)}>
              {!activeRequestId ? (
                <EmptyState
                  title="执行后自动挂上追踪记录"
                  icon={<WaypointsIcon className="size-5" />}
                />
              ) : traceQuery.isPending ? (
                <StageLoading text="正在加载追踪记录…" compact />
              ) : traceQuery.error ? (
                <InlineError error={traceQuery.error} title="追踪记录查询失败" />
              ) : traceTimeline.length ? (
                <TraceTimeline items={traceTimeline} />
              ) : (
                <EmptyState title="当前没有可展示的追踪时间线" />
              )}
            </WorkbenchDisclosure>

            <WorkbenchDisclosure title="执行尝试">
              {attempts.length ? (
                <div className="grid gap-4 md:grid-cols-2">
                  {attempts.map((attempt) => (
                    <AttemptCard key={`${attempt.attempt}-${attempt.credentialId ?? 'unknown'}`} attempt={attempt} />
                  ))}
                </div>
              ) : (
                <EmptyState title="当前还没有执行尝试" />
              )}
            </WorkbenchDisclosure>
          </div>

          <div className="grid gap-5">
            <div className="px-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">深度明细</div>
            <WorkbenchDisclosure title="候选评估">
              {candidateEvaluations.length ? (
                <div className="grid gap-4 xl:grid-cols-2">
                  {candidateEvaluations.map((evaluation, index) => (
                    <Card key={`${evaluation.candidate?.bindingId ?? 'candidate'}-${index}`} className="border-border/60 bg-card/92 shadow-sm">
                      <CardHeader className="gap-2 border-b border-border/60">
                        <CardTitle className="text-base">
                          {evaluation.candidate?.candidate?.providerType ?? '未知提供方'} / {evaluation.candidate?.candidate?.modelKey ?? '-'}
                        </CardTitle>
                        <div className="text-sm text-muted-foreground">
                          {evaluation.selectionSource ?? '未知来源'} · 总分 {evaluation.totalScore ?? '-'}
                        </div>
                      </CardHeader>
                      <CardContent className="flex flex-col gap-3 p-5">
                        <div className="flex flex-wrap gap-2">
                          <StatusBadge tone={evaluation.eligible ? 'success' : 'danger'}>
                            {evaluation.eligible ? '可用' : '已排除'}
                          </StatusBadge>
                          {evaluation.healthState ? <StatusBadge>{evaluation.healthState}</StatusBadge> : null}
                          {evaluation.affinityMatched ? <StatusBadge tone="info">命中亲和</StatusBadge> : null}
                        </div>
                        <KeyValueGrid
                          items={[
                            ['冷却截止', evaluation.cooldownUntil ?? '-'],
                            ['能力等级', evaluation.candidate?.capabilityLevel ?? '-'],
                          ]}
                        />
                        <ListBlock title="评分拆解" items={evaluation.scoreBreakdown} emptyText="无评分拆解" />
                        <ListBlock title="排除原因" items={evaluation.exclusionReasons} emptyText="无排除原因" />
                      </CardContent>
                    </Card>
                  ))}
                </div>
              ) : (
                <EmptyState title="先生成预览，再查看候选评估" />
              )}
            </WorkbenchDisclosure>

            <WorkbenchDisclosure title="原始数据">
              <div className="grid gap-4">
                {routingPreview ? <JsonBlock title="路由预览" value={routingPreview} /> : null}
                {executionPreview ? <JsonBlock title="执行预览" value={executionPreview} /> : null}
                {executeResult ? <JsonBlock title="对话执行结果" value={executeResult} /> : null}
                {resourceExecuteResult ? <JsonBlock title="资源执行结果" value={resourceExecuteResult} /> : null}
                {traceQuery.data ? <JsonBlock title="追踪结果" value={traceQuery.data} /> : null}
                {!routingPreview && !executionPreview && !executeResult && !resourceExecuteResult && !traceQuery.data ? (
                  <EmptyState title="还没有原始数据" />
                ) : null}
              </div>
            </WorkbenchDisclosure>
          </div>
        </div>
      </PageSection>
    </div>
  )

  function persistSearchParams(
    overrides?: Partial<Record<
      'distributedKeyPrefix' | 'protocol' | 'method' | 'requestPath' | 'requestedModel' | 'body' | 'formFields' | 'fileRefs' | 'requestId',
      string | undefined
    >>,
  ) {
    const next = new URLSearchParams(searchParams)
    const values = {
      distributedKeyPrefix,
      protocol,
      method,
      requestPath,
      requestedModel,
      body,
      formFields,
      fileRefs,
      requestId: searchParams.get('requestId') ?? undefined,
      ...overrides,
    }

    Object.entries(values).forEach(([key, value]) => {
      if (value == null || value === '') {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    })

    setSearchParams(next, { replace: true })
  }
}

function buildPreviewRequest({
  distributedKeyPrefix,
  protocol,
  requestPath,
  requestedModel,
  body,
  multipartMode,
  formFields,
  fileRefs,
}: {
  distributedKeyPrefix: string
  protocol: string
  requestPath: string
  requestedModel: string
  body: string
  multipartMode: boolean
  formFields: string
  fileRefs: string
}) {
  return {
    distributedKeyPrefix,
    protocol,
    requestPath,
    requestedModel,
    requestBody: multipartMode
      ? buildMultipartExplainBody(requestedModel, formFields, fileRefs)
      : parseJsonBody(body),
  }
}

function buildTraceTimeline(trace?: ObservabilityTraceResponse) {
  if (!trace) return []

  const items: Array<{ title: string; meta: Array<[string, string]> }> = []

  if (trace.requestLog) {
    items.push({
      title: '请求日志',
      meta: [
        ['请求 ID', trace.requestLog.requestId],
        ['支持状态', String(trace.requestLog.supportStatus ?? '-')],
        ['降级级别', String(trace.requestLog.degradationLevel ?? '-')],
        ['响应类型', String(trace.requestLog.responseKind ?? '-')],
      ],
    })
  }

  if (trace.routeDecision) {
    items.push({
      title: '路由决策',
      meta: [
        ['选路来源', String(trace.routeDecision.selectionSource ?? '-')],
        ['支持状态', String(trace.routeDecision.supportStatus ?? '-')],
        ['降级级别', String(trace.routeDecision.degradationLevel ?? '-')],
        ['对象模式', String(trace.routeDecision.objectMode ?? '-')],
      ],
    })
  }

  trace.cacheHits.forEach((item, index) => {
    items.push({
      title: `缓存命中 #${index + 1}`,
      meta: [
        ['缓存类型', String(item.cacheKind ?? '-')],
        ['支持状态', String(item.supportStatus ?? '-')],
        ['降级级别', String(item.degradationLevel ?? '-')],
        ['对象模式', String(item.objectMode ?? '-')],
      ],
    })
  })

  trace.upstreamCacheReferences.forEach((item, index) => {
    items.push({
      title: `上游缓存引用 #${index + 1}`,
      meta: [
        ['外部缓存引用', String(item.externalCacheRef ?? '-')],
        ['状态', String(item.status ?? '-')],
      ],
    })
  })

  if (trace.asyncResourceSummary) {
    items.push({
      title: '异步资源',
      meta: [
        ['资源键', String(trace.asyncResourceSummary.resourceKey ?? '-')],
        ['资源类型', String(trace.asyncResourceSummary.resourceType ?? '-')],
        ['状态', String(trace.asyncResourceSummary.status ?? '-')],
        ['上游对象 ID', String(trace.asyncResourceSummary.upstreamObjectId ?? '-')],
      ],
    })
  }

  if (trace.asyncResourceDetail) {
    items.push({
      title: '异步资源详情',
      meta: [
        ['状态变更', String(trace.asyncResourceDetail.transitions?.length ?? 0)],
        ['产物数量', String(trace.asyncResourceDetail.artifacts?.length ?? 0)],
        ['含请求载荷', trace.asyncResourceDetail.requestPayloadJson ? '是' : '否'],
        ['含响应载荷', trace.asyncResourceDetail.responsePayloadJson ? '是' : '否'],
      ],
    })
  }

  return items
}

function parseJsonBody(value: string) {
  try {
    return JSON.parse(value)
  } catch (error) {
    throw new Error(`JSON 解析失败：${error instanceof Error ? error.message : '请求体 JSON 非法。'}`)
  }
}

function safeParseJsonBody(value: string) {
  if (!value.trim()) return {}
  return parseJsonBody(value)
}

function parseJsonObject(value: string) {
  const parsed = safeParseJsonBody(value)
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error('formFields 必须是 JSON object。')
  }
  return parsed
}

function parseJsonArray(value: string) {
  const parsed = safeParseJsonBody(value)
  if (!Array.isArray(parsed)) {
    throw new Error('fileRefs 必须是 JSON array。')
  }
  return parsed
}

function buildMultipartExplainBody(requestedModel: string, formFields: string, fileRefs: string) {
  const payload = parseJsonObject(formFields) as Record<string, unknown>
  if (!payload.model && requestedModel) {
    payload.model = requestedModel
  }
  const refs = parseJsonArray(fileRefs)
  if (refs.length) {
    payload.fileRefs = refs
  }
  return payload
}

function inspectJsonDraft(value: string, expectedType: 'json' | 'object' | 'array' = 'json') {
  const trimmed = value.trim()
  if (!trimmed) {
    return {
      valid: false,
      summary: '空白',
    }
  }

  try {
    const parsed = JSON.parse(value)
    if (expectedType === 'object') {
      return {
        valid: typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed),
        summary:
          typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
            ? `${Object.keys(parsed).length} 个字段`
            : '需为对象',
      }
    }

    if (expectedType === 'array') {
      return {
        valid: Array.isArray(parsed),
        summary: Array.isArray(parsed) ? `${parsed.length} 个引用` : '需为数组',
      }
    }

    return {
      valid: true,
      summary: Array.isArray(parsed) ? `JSON 数组 · ${parsed.length} 项` : 'JSON 有效',
    }
  } catch {
    return {
      valid: false,
      summary: 'JSON 非法',
    }
  }
}

function resolveFeatureLevel(levels: Record<string, string>, feature: string) {
  return levels[feature] ?? levels[feature.toLowerCase()] ?? levels[feature.toUpperCase()] ?? '-'
}

function formatRequiredFeature(feature: string) {
  return featureLabel(feature.toLowerCase())
}

function buildUsageSummary(usage?: GatewayUsageView | null) {
  if (!usage) return []

  const promptTokens = usage.promptTokens ?? 0
  const rawPromptTokens = usage.rawPromptTokens ?? 0
  const completionTokens = usage.completionTokens ?? 0
  const reasoningTokens = usage.reasoningTokens ?? 0
  const cacheHitTokens = usage.cacheHitTokens ?? 0
  const cacheWriteTokens = usage.cacheWriteTokens ?? 0
  const totalTokens = usage.totalTokens ?? 0

  return [
    { label: '输入 Token', value: promptTokens, hint: `原始 ${rawPromptTokens}` },
    { label: '输出 Token', value: completionTokens, hint: `推理 ${reasoningTokens}` },
    { label: '缓存 Token', value: cacheHitTokens + cacheWriteTokens, hint: usage.completeness },
    { label: '总 Token', value: totalTokens, hint: usage.source },
  ]
}

function toneForSupportStatus(status: string) {
  const normalized = status.toLowerCase()
  if (normalized.includes('block')) return 'danger' as const
  if (normalized.includes('degrad') || normalized.includes('orchestration')) return 'warning' as const
  if (normalized.includes('native')) return 'success' as const
  return 'info' as const
}

function toneForPayloadPart(type?: string | null) {
  const normalized = type?.toLowerCase()
  if (normalized === 'tool_result') return 'warning' as const
  if (normalized === 'image' || normalized === 'file') return 'info' as const
  return 'neutral' as const
}
