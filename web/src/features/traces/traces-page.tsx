import { type FormEvent, type ReactNode, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  ArrowUpRightIcon,
  BoxesIcon,
  SearchIcon,
  WaypointsIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { MetricCard } from '@/components/app/metric-card'
import { PageSection } from '@/components/app/page-section'
import { StatusBadge } from '@/components/app/status-badge'
import { formatInstant } from '@/lib/format'
import { apiClient } from '@/lib/api'
import { useTypedQuery } from '@/lib/typed-react-query'
import type {
  CacheHitEntry,
  TraceLookupResponse,
  UpstreamCacheReferenceEntry,
} from './types'

const SECONDARY_TABS = ['entity', 'actions', 'raw'] as const
type SecondaryTab = (typeof SECONDARY_TABS)[number]

type TraceStage = {
  id: string
  title: string
  summary: string
  detail: string
  tone: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
  timestamp?: string | null
  items: string[]
}

export function TracesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [requestId, setRequestId] = useState(searchParams.get('requestId') ?? '')
  const [gatewayResourceKey, setGatewayResourceKey] = useState(searchParams.get('gatewayResourceKey') ?? '')
  const [upstreamObjectId, setUpstreamObjectId] = useState(searchParams.get('upstreamObjectId') ?? '')

  const submittedRequestId = searchParams.get('requestId') ?? ''
  const submittedGatewayResourceKey = searchParams.get('gatewayResourceKey') ?? ''
  const submittedUpstreamObjectId = searchParams.get('upstreamObjectId') ?? ''
  const providerType = searchParams.get('providerType') ?? ''
  const requestPath = searchParams.get('requestPath') ?? ''
  const activeTab = resolveSecondaryTab(searchParams.get('tab'))

  const query = useTypedQuery<TraceLookupResponse>({
    queryKey: ['trace-lookup', submittedRequestId, submittedGatewayResourceKey, submittedUpstreamObjectId],
    queryFn: () =>
      apiClient.get<TraceLookupResponse>(
        buildTraceLookupUrl(submittedRequestId, submittedGatewayResourceKey, submittedUpstreamObjectId),
      ),
    enabled: Boolean(submittedRequestId || submittedGatewayResourceKey || submittedUpstreamObjectId),
  })

  const trace = query.data?.trace
  const stages = useMemo(
    () => buildTraceStages(query.data, providerType, requestPath),
    [providerType, query.data, requestPath],
  )

  const metrics = useMemo(() => {
    const requestLog = trace?.requestLog
    const routeDecision = trace?.routeDecision

    return [
      {
        label: '请求 ID',
        value: query.data?.requestId ?? requestLog?.requestId ?? '-',
        hint: '链路主锚点',
      },
      {
        label: '网关资源键',
        value: query.data?.gatewayResourceKey ?? requestLog?.gatewayResourceKey ?? '-',
        hint: '异步资源与对象侧锚点',
      },
      {
        label: '支持状态',
        value: requestLog?.supportStatus ?? routeDecision?.supportStatus ?? '-',
        hint: routeDecision?.selectionSource ?? '等待路由决策',
      },
      {
        label: '延迟',
        value: requestLog?.durationMs != null ? `${requestLog.durationMs} ms` : '-',
        hint: requestLog?.status ?? '等待请求日志',
      },
    ]
  }, [query.data, trace?.requestLog, trace?.routeDecision])

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const next = new URLSearchParams()
    if (requestId.trim()) next.set('requestId', requestId.trim())
    if (gatewayResourceKey.trim()) next.set('gatewayResourceKey', gatewayResourceKey.trim())
    if (upstreamObjectId.trim()) next.set('upstreamObjectId', upstreamObjectId.trim())
    if (providerType) next.set('providerType', providerType)
    if (requestPath) next.set('requestPath', requestPath)
    if (activeTab !== 'entity') next.set('tab', activeTab)
    setSearchParams(next)
  }

  const clearSearch = () => {
    setRequestId('')
    setGatewayResourceKey('')
    setUpstreamObjectId('')
    setSearchParams(new URLSearchParams())
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="链路查询"
        title="链路时间轴工作台"
      >
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="grid gap-4 xl:grid-cols-3">
            <FormField label="请求 ID">
              <Input
                value={requestId}
                onChange={(event) => setRequestId(event.target.value)}
                placeholder="输入请求 ID"
              />
            </FormField>
            <FormField label="网关资源键">
              <Input
                value={gatewayResourceKey}
                onChange={(event) => setGatewayResourceKey(event.target.value)}
                placeholder="输入网关资源键"
              />
            </FormField>
            <FormField label="上游对象 ID">
              <Input
                value={upstreamObjectId}
                onChange={(event) => setUpstreamObjectId(event.target.value)}
                placeholder="输入上游对象 ID"
              />
            </FormField>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button type="submit">
              <SearchIcon data-icon="inline-start" />
              查询链路
            </Button>
            <Button type="button" variant="outline" onClick={clearSearch}>
              清空
            </Button>
            {providerType ? <StatusBadge tone="info">{providerType}</StatusBadge> : null}
            {requestPath ? <StatusBadge>{requestPath}</StatusBadge> : null}
          </div>
        </form>
      </PageSection>

      {!submittedRequestId && !submittedGatewayResourceKey && !submittedUpstreamObjectId ? (
        <EmptyState
          title="输入至少一个联查锚点"
          icon={<WaypointsIcon className="size-5" />}
        />
      ) : query.isPending ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <MetricCard key={index} label="加载中" value="..." hint="正在联查链路" />
          ))}
        </div>
      ) : query.error ? (
        <InlineError error={query.error} title="链路追踪查询失败" />
      ) : query.data ? (
        <>
          <PageSection
            kicker="概览"
            title="链路概览"
          >
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {metrics.map((metric) => (
                <MetricCard key={metric.label} label={metric.label} value={metric.value} hint={metric.hint} />
              ))}
            </div>
          </PageSection>

          <PageSection
            kicker="时间线"
            title="阶段时间轴"
          >
            <div className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,0.85fr)]">
              <div className="flex flex-col gap-4">
                {stages.map((stage, index) => (
                  <TraceStageCard
                    key={stage.id}
                    stage={stage}
                    isLast={index === stages.length - 1}
                  />
                ))}
              </div>

              <div className="flex flex-col gap-4">
                <TraceContextCard
                  title="查询上下文"
                  items={[
                    ['提供方类型', providerType || '无'],
                    ['请求路径', requestPath || '无'],
                    ['命中记录', String(query.data.matches.length)],
                    ['缓存命中', String(trace?.cacheHits.length ?? 0)],
                  ]}
                />

                <TraceContextCard
                  title="对象与异步资源"
                  items={[
                    ['响应对象', `${trace?.requestLog?.responseObjectType ?? '-'} / ${trace?.requestLog?.responseObjectId ?? '-'}`],
                    ['网关资源键', trace?.requestLog?.gatewayResourceKey ?? '-'],
                    ['异步资源', trace?.asyncResourceSummary?.resourceKey ?? '-'],
                    ['上游引用', String(trace?.upstreamCacheReferences.length ?? 0)],
                  ]}
                />
              </div>
            </div>
          </PageSection>

          <PageSection
            kicker="次级视图"
            title="实体、动作与原始数据"
          >
            <Tabs
              value={activeTab}
              onValueChange={(nextValue) => {
                const nextTab = resolveSecondaryTab(nextValue)
                const next = new URLSearchParams(searchParams)
                if (nextTab === 'entity') {
                  next.delete('tab')
                } else {
                  next.set('tab', nextTab)
                }
                setSearchParams(next)
              }}
              className="gap-4"
            >
              <TabsList className="grid w-full grid-cols-3">
                {SECONDARY_TABS.map((tab) => (
                  <TabsTrigger key={tab} value={tab}>
                    {tabLabel(tab)}
                  </TabsTrigger>
                ))}
              </TabsList>

              <TabsContent value="entity" className="mt-0">
                <div className="grid gap-4 lg:grid-cols-2">
                  {query.data.matches.map((match) => (
                    <Card key={match.requestId} className="border-border/60 bg-card/92 shadow-sm">
                      <CardHeader className="gap-2 border-b border-border/60">
                        <CardTitle className="text-base">{match.requestId}</CardTitle>
                      </CardHeader>
                      <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                        <TraceMetaItem label="网关资源键" value={String(match.gatewayResourceKey ?? '-')} />
                        <TraceMetaItem label="提供方类型" value={String(match.providerType ?? '-')} />
                        <TraceMetaItem label="响应状态" value={String(match.responseStatus ?? '-')} />
                        <TraceMetaItem label="耗时" value={match.durationMs != null ? `${match.durationMs} ms` : '-'} />
                      </CardContent>
                    </Card>
                  ))}
                  {trace?.asyncResourceSummary ? (
                    <Card className="border-border/60 bg-card/92 shadow-sm">
                      <CardHeader className="gap-2 border-b border-border/60">
                        <CardTitle className="text-base">异步资源</CardTitle>
                      </CardHeader>
                      <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                        <TraceMetaItem label="资源键" value={trace.asyncResourceSummary.resourceKey} />
                        <TraceMetaItem label="资源类型" value={trace.asyncResourceSummary.resourceType ?? '-'} />
                        <TraceMetaItem label="状态" value={trace.asyncResourceSummary.status ?? '-'} />
                        <TraceMetaItem label="上游对象 ID" value={trace.asyncResourceSummary.upstreamObjectId ?? '-'} />
                      </CardContent>
                    </Card>
                  ) : null}
                  {!query.data.matches.length && !trace?.asyncResourceSummary ? (
                    <EmptyState
                      className="lg:col-span-2"
                      title="当前链路没有额外实体摘要"
                    />
                  ) : null}
                </div>
              </TabsContent>

              <TabsContent value="actions" className="mt-0">
                <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
                  <ActionCard
                      title="前往工作台"
                    to={`/console/workbench?requestId=${encodeURIComponent(query.data.requestId ?? submittedRequestId ?? '')}${requestPath ? `&requestPath=${encodeURIComponent(requestPath)}` : ''}`}
                    label="打开调试工作台"
                  />
                  <ActionCard
                    title="前往事件处置视图"
                    to={`/console/incidents?entityType=REQUEST&entityRef=${encodeURIComponent(query.data.requestId ?? submittedRequestId ?? '')}`}
                    label="打开事件处置视图"
                  />
                  <ActionCard
                    title="前往运维总览"
                    to="/console/ops"
                    label="打开运维总览"
                  />
                </div>
              </TabsContent>

              <TabsContent value="raw" className="mt-0">
                <Card className="border-border/60 bg-card/92 text-foreground shadow-sm">
                  <CardHeader className="gap-2 border-b border-border/60">
                    <CardTitle className="text-base text-foreground">原始载荷</CardTitle>
                  </CardHeader>
                  <CardContent className="p-0">
                    <pre className="overflow-x-auto p-5 text-xs leading-6 text-foreground">
                      {JSON.stringify(query.data, null, 2)}
                    </pre>
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>
          </PageSection>
        </>
      ) : (
        <EmptyState
          title="没有查到对应链路"
          icon={<BoxesIcon className="size-5" />}
        />
      )}
    </div>
  )
}

function FormField({
  label,
  children,
}: {
  label: string
  children: ReactNode
}) {
  return (
    <label className="flex flex-col gap-2">
      <span className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
        {label}
      </span>
      {children}
    </label>
  )
}

function TraceStageCard({
  stage,
  isLast,
}: {
  stage: TraceStage
  isLast: boolean
}) {
  return (
    <div className="relative pl-7">
      {!isLast ? <div className="absolute left-3 top-10 h-[calc(100%-1rem)] w-px bg-border/80" /> : null}
      <div className="absolute left-0 top-6 flex size-6 items-center justify-center rounded-full border border-border/70 bg-background shadow-sm">
        <span className="size-2 rounded-full bg-current text-muted-foreground" />
      </div>
      <Card className="border-border/60 bg-card/92 shadow-sm">
        <CardHeader className="gap-2 border-b border-border/60">
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-1">
              <CardTitle className="text-base">{stage.title}</CardTitle>
              <div className="text-sm text-muted-foreground">{stage.summary}</div>
            </div>
            <div className="flex flex-col items-end gap-2">
              <StatusBadge tone={stage.tone}>{stage.detail}</StatusBadge>
              {stage.timestamp ? (
                <div className="text-xs text-muted-foreground">{formatInstant(stage.timestamp)}</div>
              ) : null}
            </div>
          </div>
        </CardHeader>
        <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
          {stage.items.length ? (
            <div className="grid gap-2">
              {stage.items.map((item) => (
                <div
                  key={item}
                  className="rounded-2xl border border-border/60 bg-background px-4 py-3 leading-6"
                >
                  {item}
                </div>
              ))}
            </div>
          ) : (
            <div className="rounded-2xl border border-border/60 bg-background px-4 py-3">
              当前阶段没有更多明细。
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function TraceContextCard({
  title,
  items,
}: {
  title: string
  items: Array<[string, string]>
}) {
  return (
    <Card className="border-border/60 bg-card/92 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
        {items.map(([label, value]) => (
          <TraceMetaItem key={label} label={label} value={value} />
        ))}
      </CardContent>
    </Card>
  )
}

function ActionCard({
  title,
  to,
  label,
}: {
  title: string
  to: string
  label: string
}) {
  return (
    <Card className="border-border/60 bg-card/92 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="p-5">
        <Button asChild variant="outline" size="sm">
          <Link to={to}>
            {label}
            <ArrowUpRightIcon data-icon="inline-end" />
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}

function TraceMetaItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 bg-background/90 px-4 py-3">
      <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{label}</span>
      <span className="max-w-[16rem] break-all text-right text-sm text-foreground">{value}</span>
    </div>
  )
}

function buildTraceLookupUrl(requestId?: string, gatewayResourceKey?: string, upstreamObjectId?: string) {
  const params = new URLSearchParams()
  if (requestId) params.set('requestId', requestId)
  if (gatewayResourceKey) params.set('gatewayResourceKey', gatewayResourceKey)
  if (upstreamObjectId) params.set('upstreamObjectId', upstreamObjectId)
  return `/admin/traces/lookup?${params.toString()}`
}

function buildTraceStages(
  response: TraceLookupResponse | undefined,
  providerType: string,
  requestPath: string,
): TraceStage[] {
  const trace = response?.trace
  const requestLog = trace?.requestLog
  const routeDecision = trace?.routeDecision
  const cacheHits = trace?.cacheHits ?? []
  const upstreamCacheReferences = trace?.upstreamCacheReferences ?? []
  const asyncResource = trace?.asyncResourceSummary

  return [
    {
      id: 'request-parse',
      title: '请求解析',
      summary: `${requestLog?.protocol ?? '未知协议'} ${requestLog?.requestPath ?? (requestPath || '未命中路径')}`,
      detail: requestLog?.status ?? '等待请求日志',
      tone: requestLog?.status === 'FAILED' ? 'danger' : 'success',
      timestamp: requestLog?.startedAt ?? requestLog?.createdAt,
      items: [
        `模型：${requestLog?.requestedModel ?? requestLog?.publicModel ?? '-'}`,
        `资源 / 操作：${requestLog?.resourceType ?? '-'} / ${requestLog?.operation ?? '-'}`,
        `支持 / 降级：${requestLog?.supportStatus ?? '-'} / ${requestLog?.degradationLevel ?? '-'}`,
      ],
    },
    {
      id: 'route-selection',
      title: '路由选择',
      summary: routeDecision?.selectionSource ?? '未命中路由决策',
      detail: routeDecision?.selectedProviderType ?? (providerType || '等待选路'),
      tone: routeDecision?.supportStatus === 'BLOCKED' ? 'danger' : routeDecision ? 'success' : 'neutral',
      timestamp: routeDecision?.createdAt,
      items: [
        `选中基础 URL：${routeDecision?.selectedBaseUrl ?? '-'}`,
        `候选数量：${routeDecision?.candidateCount ?? 0}`,
        `执行后端：${routeDecision?.executionBackend ?? '-'}`,
      ],
    },
    {
      id: 'cache-lookup',
      title: '缓存检索',
      summary: `${cacheHits.length} 次缓存命中 / ${upstreamCacheReferences.length} 条上游引用`,
      detail: cacheHits.length ? '命中缓存' : '未命中缓存',
      tone: cacheHits.length ? 'success' : 'info',
      timestamp: cacheHits[0]?.createdAt ?? upstreamCacheReferences[0]?.updatedAt,
      items: buildCacheItems(cacheHits, upstreamCacheReferences),
    },
    {
      id: 'translation',
      title: 'API 翻译',
      summary: `${requestLog?.objectMode ?? routeDecision?.objectMode ?? '-'} -> ${requestLog?.responseKind ?? '-'}`,
      detail: requestLog?.supportStatus ?? routeDecision?.supportStatus ?? '等待翻译结果',
      tone:
        requestLog?.supportStatus === 'BLOCKED'
          ? 'danger'
          : requestLog?.degradationLevel && requestLog.degradationLevel !== 'NATIVE'
            ? 'warning'
            : 'info',
      timestamp: requestLog?.createdAt,
      items: [
        `选路来源：${requestLog?.selectionSource ?? routeDecision?.selectionSource ?? '-'}`,
        `执行后端：${requestLog?.executionBackend ?? routeDecision?.executionBackend ?? '-'}`,
        `响应对象：${requestLog?.responseObjectType ?? '-'} / ${requestLog?.responseObjectId ?? '-'}`,
      ],
    },
    {
      id: 'remote-call',
      title: '远端调用',
      summary: `${routeDecision?.selectedProviderType ?? requestLog?.providerType ?? (providerType || '未知提供方')}`,
      detail: requestLog?.durationMs != null ? `${requestLog.durationMs} ms` : '等待耗时',
      tone: requestLog?.status === 'FAILED' ? 'danger' : 'success',
      timestamp: requestLog?.completedAt ?? requestLog?.createdAt,
      items: [
        `凭证 ID：${routeDecision?.selectedCredentialId ?? requestLog?.credentialId ?? '-'}`,
        `解析后模型：${requestLog?.resolvedModelKey ?? routeDecision?.resolvedModelKey ?? '-'}`,
        `错误：${requestLog?.errorCode ?? '无'}`,
      ],
    },
    {
      id: 'response-processing',
      title: '流式返回处理',
      summary: requestLog?.responseStatus ?? asyncResource?.status ?? '等待返回状态',
      detail: asyncResource?.normalizedStatus ?? requestLog?.responseKind ?? '响应',
      tone:
        requestLog?.status === 'FAILED'
          ? 'danger'
          : asyncResource?.status === 'FAILED'
            ? 'danger'
            : 'success',
      timestamp: asyncResource?.updatedAt ?? requestLog?.completedAt,
      items: [
        `规范化事件数：${requestLog?.canonicalEventCount ?? '-'}`,
        `异步资源：${asyncResource?.resourceKey ?? '-'}`,
        `上游对象：${asyncResource?.upstreamObjectId ?? requestLog?.responseObjectId ?? '-'}`,
      ],
    },
  ]
}

function buildCacheItems(
  cacheHits: CacheHitEntry[],
  upstreamReferences: UpstreamCacheReferenceEntry[],
) {
  const items = cacheHits.slice(0, 2).map((item) =>
    `${item.cacheKind ?? '缓存'} · 节省 ${item.savedInputTokens ?? 0} · 引用 ${item.cachedContentRef ?? '-'}`,
  )
  if (upstreamReferences[0]) {
    items.push(`上游引用：${upstreamReferences[0].externalCacheRef ?? '-'} · 状态 ${upstreamReferences[0].status ?? '-'}`)
  }
  return items.length ? items : ['当前请求没有命中缓存，也没有关联上游缓存引用。']
}

function tabLabel(tab: SecondaryTab) {
  switch (tab) {
    case 'entity':
      return '实体'
    case 'actions':
      return '动作'
    case 'raw':
      return '原始 JSON'
  }
}

function resolveSecondaryTab(value: string | null): SecondaryTab {
  if (value && SECONDARY_TABS.includes(value as SecondaryTab)) {
    return value as SecondaryTab
  }
  return 'entity'
}
