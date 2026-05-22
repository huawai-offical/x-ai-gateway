import { type ReactNode, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiClient } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import { useTypedQuery } from '@/lib/typed-react-query'

type RequestLogRow = {
  id: number
  requestId: string
  distributedKeyId?: number | null
  distributedKeyPrefix?: string | null
  protocol?: string | null
  requestPath?: string | null
  resourceType?: string | null
  operation?: string | null
  requestedModel?: string | null
  publicModel?: string | null
  resolvedModelKey?: string | null
  modelGroup?: string | null
  providerType?: string | null
  credentialId?: number | null
  selectionSource?: string | null
  executionBackend?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  gatewayResourceKey?: string | null
  responseKind?: string | null
  responseObjectType?: string | null
  responseObjectId?: string | null
  responseStatus?: string | null
  canonicalEventCount?: number | null
  status?: string | null
  startedAt?: string | null
  completedAt?: string | null
  createdAt?: string | null
  durationMs?: number | null
  errorCode?: string | null
  errorMessage?: string | null
  clientInstanceId?: string | null
  clientInstanceName?: string | null
  sessionAffinityKey?: string | null
  sessionKey?: string | null
  filterAction?: string | null
  filterRuleId?: string | null
  filterSummaryJson?: string | null
  usageInputTokens?: number | null
  usageOutputTokens?: number | null
  usageTotalTokens?: number | null
}

type RouteDecisionRow = {
  id: number
  requestId: string
  distributedKeyId?: number | null
  distributedKeyPrefix?: string | null
  requestedModel?: string | null
  publicModel?: string | null
  resolvedModelKey?: string | null
  protocol?: string | null
  requestPath?: string | null
  resourceType?: string | null
  operation?: string | null
  modelGroup?: string | null
  selectionSource?: string | null
  executionBackend?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  selectedCredentialId?: number | null
  selectedProviderType?: string | null
  selectedBaseUrl?: string | null
  prefixHash?: string | null
  fingerprint?: string | null
  candidateCount: number
  candidateSummaryJson?: string | null
  createdAt?: string | null
  clientInstanceId?: string | null
  clientInstanceName?: string | null
  sessionAffinityKey?: string | null
  sessionKey?: string | null
}

type CacheHitRow = {
  id: number
  requestId: string
  distributedKeyId?: number | null
  protocol?: string | null
  requestPath?: string | null
  resourceType?: string | null
  operation?: string | null
  providerType?: string | null
  credentialId?: number | null
  modelGroup?: string | null
  prefixHash?: string | null
  fingerprint?: string | null
  cacheKind?: string | null
  executionBackend?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  cacheHitTokens?: number | null
  cacheWriteTokens?: number | null
  savedInputTokens?: number | null
  cachedContentRef?: string | null
  createdAt?: string | null
  clientInstanceId?: string | null
  sessionAffinityKey?: string | null
}

type UpstreamCacheReferenceRow = {
  id: number
  distributedKeyId?: number | null
  providerType?: string | null
  credentialId?: number | null
  modelGroup?: string | null
  prefixHash?: string | null
  externalCacheRef?: string | null
  status?: string | null
  expireAt?: string | null
  lastUsedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

type ObservabilityTab = 'request-logs' | 'route-decisions' | 'cache-hits' | 'upstream-cache-references'
type DetailPayload = RequestLogRow | RouteDecisionRow | CacheHitRow | UpstreamCacheReferenceRow

const TAB_OPTIONS: Array<{ key: ObservabilityTab; label: string }> = [
  { key: 'request-logs', label: '请求日志' },
  { key: 'route-decisions', label: '选路决策' },
  { key: 'cache-hits', label: '缓存命中' },
  { key: 'upstream-cache-references', label: '上游缓存引用' },
]

export function RequestLogsPage() {
  const [activeTab, setActiveTab] = useState<ObservabilityTab>('request-logs')
  const [requestId, setRequestId] = useState('')
  const [gatewayResourceKey, setGatewayResourceKey] = useState('')
  const [upstreamObjectId, setUpstreamObjectId] = useState('')
  const [providerType, setProviderType] = useState('ALL')
  const [distributedKeyId, setDistributedKeyId] = useState('')
  const [fromLocal, setFromLocal] = useState(() => toLocalDateTimeInput(new Date(Date.now() - 6 * 60 * 60 * 1000)))
  const [toLocal, setToLocal] = useState(() => toLocalDateTimeInput(new Date()))
  const [upstreamStatus, setUpstreamStatus] = useState('ALL')
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailTitle, setDetailTitle] = useState('')
  const [detailPayload, setDetailPayload] = useState<DetailPayload | null>(null)

  const baseParams = useMemo(() => {
    const distributedKey = parseOptionalNumber(distributedKeyId)
    return {
      requestId: requestId.trim() || undefined,
      gatewayResourceKey: gatewayResourceKey.trim() || undefined,
      upstreamObjectId: upstreamObjectId.trim() || undefined,
      providerType: providerType === 'ALL' ? undefined : providerType,
      distributedKeyId: distributedKey ?? undefined,
      from: toIsoString(fromLocal),
      to: toIsoString(toLocal),
    }
  }, [distributedKeyId, fromLocal, gatewayResourceKey, providerType, requestId, toLocal, upstreamObjectId])

  const requestLogsQuery = useTypedQuery<RequestLogRow[]>({
    queryKey: ['request-logs', 'request-logs', baseParams],
    queryFn: () => apiClient.get<RequestLogRow[]>('/admin/observability/request-logs', { params: baseParams }),
    enabled: true,
  })

  const routeDecisionsQuery = useTypedQuery<RouteDecisionRow[]>({
    queryKey: ['request-logs', 'route-decisions', baseParams],
    queryFn: () => apiClient.get<RouteDecisionRow[]>('/admin/observability/route-decisions', { params: baseParams }),
    enabled: true,
  })

  const cacheHitsQuery = useTypedQuery<CacheHitRow[]>({
    queryKey: ['request-logs', 'cache-hits', baseParams],
    queryFn: () => apiClient.get<CacheHitRow[]>('/admin/observability/cache-hits', { params: baseParams }),
    enabled: true,
  })

  const upstreamCacheReferencesQuery = useTypedQuery<UpstreamCacheReferenceRow[]>({
    queryKey: ['request-logs', 'upstream-cache-references', baseParams, upstreamStatus],
    queryFn: () =>
      apiClient.get<UpstreamCacheReferenceRow[]>('/admin/observability/upstream-cache-references', {
        params: {
          ...baseParams,
          status: upstreamStatus === 'ALL' ? undefined : upstreamStatus,
        },
      }),
    enabled: activeTab === 'upstream-cache-references',
  })

  const currentQuery = activeTab === 'request-logs'
    ? requestLogsQuery
    : activeTab === 'route-decisions'
      ? routeDecisionsQuery
      : activeTab === 'cache-hits'
        ? cacheHitsQuery
        : upstreamCacheReferencesQuery

  const providerTypeOptions = useMemo(() => {
    const values = new Set<string>()
    requestLogsQuery.data?.forEach((row) => addIfNotBlank(values, row.providerType))
    routeDecisionsQuery.data?.forEach((row) => addIfNotBlank(values, row.selectedProviderType))
    cacheHitsQuery.data?.forEach((row) => addIfNotBlank(values, row.providerType))
    upstreamCacheReferencesQuery.data?.forEach((row) => addIfNotBlank(values, row.providerType))
    return Array.from(values).sort((left, right) => left.localeCompare(right))
  }, [
    cacheHitsQuery.data,
    requestLogsQuery.data,
    routeDecisionsQuery.data,
    upstreamCacheReferencesQuery.data,
  ])

  const summaryHints = {
    requestLogs: requestLogsQuery.data?.length ?? 0,
    routeDecisions: routeDecisionsQuery.data?.length ?? 0,
    cacheHits: cacheHitsQuery.data?.length ?? 0,
    upstreamRefs: upstreamCacheReferencesQuery.data?.length ?? 0,
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="可观测性"
        title="请求日志与缓存观测"
      >
        <div className="grid gap-4 rounded-2xl border border-border/60 bg-card/92 p-4 md:grid-cols-2 xl:grid-cols-4">
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">请求 ID</span>
            <Input value={requestId} onChange={(event) => setRequestId(event.target.value)} placeholder="可选" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">网关资源键</span>
            <Input value={gatewayResourceKey} onChange={(event) => setGatewayResourceKey(event.target.value)} placeholder="可选" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">上游对象 ID</span>
            <Input value={upstreamObjectId} onChange={(event) => setUpstreamObjectId(event.target.value)} placeholder="可选" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">访问密钥 ID</span>
            <Input value={distributedKeyId} onChange={(event) => setDistributedKeyId(event.target.value)} placeholder="可选" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">提供方类型</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={providerType}
              onChange={(event) => setProviderType(event.target.value)}
            >
              <option value="ALL">全部</option>
              {providerTypeOptions.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">开始时间</span>
            <Input type="datetime-local" value={fromLocal} onChange={(event) => setFromLocal(event.target.value)} />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">结束时间</span>
            <Input type="datetime-local" value={toLocal} onChange={(event) => setToLocal(event.target.value)} />
          </label>
          {activeTab === 'upstream-cache-references' ? (
            <label className="flex flex-col gap-2">
              <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">状态</span>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={upstreamStatus}
                onChange={(event) => setUpstreamStatus(event.target.value)}
              >
                <option value="ALL">全部</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="EXPIRED">EXPIRED</option>
                <option value="INVALIDATED">INVALIDATED</option>
              </select>
            </label>
          ) : (
            <div className="flex items-end">
              <div className="flex flex-wrap gap-2">
                <StatusBadge tone="info">请求日志 {summaryHints.requestLogs}</StatusBadge>
                <StatusBadge tone="warning">选路决策 {summaryHints.routeDecisions}</StatusBadge>
                <StatusBadge tone="success">缓存命中 {summaryHints.cacheHits}</StatusBadge>
                <StatusBadge>上游引用 {summaryHints.upstreamRefs}</StatusBadge>
              </div>
            </div>
          )}
        </div>
      </PageSection>

      <PageSection kicker="日志清单" title="观测数据">
        {currentQuery.error ? <InlineError error={currentQuery.error} title="观测数据加载失败" /> : null}
        <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as ObservabilityTab)} className="gap-4">
          <TabsList variant="line" className="w-full justify-start overflow-x-auto">
            {TAB_OPTIONS.map((item) => (
              <TabsTrigger key={item.key} value={item.key}>{item.label}</TabsTrigger>
            ))}
          </TabsList>

          <TabsContent value="request-logs" className="mt-0">
            <ListContainer
              isPending={requestLogsQuery.isPending}
              isEmpty={!requestLogsQuery.data?.length}
              emptyTitle="当前筛选下暂无请求日志"
            >
              <PaginatedRows items={requestLogsQuery.data ?? []}>
                {({ pageItems }) => (
                  <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
                    <table aria-label="请求日志表" className="min-w-[1120px] w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">请求 ID</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型</th>
                      <th className="w-[13%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">客户端</th>
                      <th className="w-[13%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">会话</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">耗时</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.id} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="truncate text-foreground">{valueOrDash(row.requestId)}</div>
                          <div className="mt-1 truncate text-xs text-muted-foreground">{formatInstant(row.createdAt)}</div>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.publicModel ?? row.requestedModel)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.clientInstanceName ?? row.clientInstanceId)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.sessionAffinityKey ?? row.sessionKey)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.providerType)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={toneByStatus(row.status ?? row.supportStatus)}>{valueOrDash(row.status ?? row.supportStatus)}</StatusBadge>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{formatMs(row.durationMs)}</td>
                        <td className="px-4 py-3">
                          <Button type="button" variant="outline" size="sm" onClick={() => openDetail('请求日志详情', row, setDetailOpen, setDetailTitle, setDetailPayload)}>
                            详情
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            </ListContainer>
          </TabsContent>

          <TabsContent value="route-decisions" className="mt-0">
            <ListContainer
              isPending={routeDecisionsQuery.isPending}
              isEmpty={!routeDecisionsQuery.data?.length}
              emptyTitle="当前筛选下暂无选路决策"
            >
              <PaginatedRows items={routeDecisionsQuery.data ?? []}>
                {({ pageItems }) => (
                  <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
                    <table aria-label="选路决策表" className="min-w-[1040px] w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[21%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">请求 ID</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">解析后模型</th>
                      <th className="w-[13%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">凭证</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">支持状态</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">候选数</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.id} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{valueOrDash(row.requestId)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.resolvedModelKey ?? row.publicModel)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.selectedProviderType)}</td>
                        <td className="px-4 py-3 text-muted-foreground">{valueOrDash(row.selectedCredentialId)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={toneByStatus(row.supportStatus)}>{valueOrDash(row.supportStatus)}</StatusBadge>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{valueOrDash(row.candidateCount)}</td>
                        <td className="px-4 py-3">
                          <Button type="button" variant="outline" size="sm" onClick={() => openDetail('选路决策详情', row, setDetailOpen, setDetailTitle, setDetailPayload)}>
                            详情
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            </ListContainer>
          </TabsContent>

          <TabsContent value="cache-hits" className="mt-0">
            <ListContainer
              isPending={cacheHitsQuery.isPending}
              isEmpty={!cacheHitsQuery.data?.length}
              emptyTitle="当前筛选下暂无缓存命中记录"
            >
              <PaginatedRows items={cacheHitsQuery.data ?? []}>
                {({ pageItems }) => (
                  <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
                    <table aria-label="缓存命中表" className="min-w-[900px] w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">请求 ID</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">缓存类型</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Token 收益</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.id} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{valueOrDash(row.requestId)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.cacheKind)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.providerType)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">
                          命中 {valueOrDash(row.cacheHitTokens)} / 写入 {valueOrDash(row.cacheWriteTokens)} / 节省 {valueOrDash(row.savedInputTokens)}
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.createdAt)}</td>
                        <td className="px-4 py-3">
                          <Button type="button" variant="outline" size="sm" onClick={() => openDetail('缓存命中详情', row, setDetailOpen, setDetailTitle, setDetailPayload)}>
                            详情
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            </ListContainer>
          </TabsContent>

          <TabsContent value="upstream-cache-references" className="mt-0">
            <ListContainer
              isPending={upstreamCacheReferencesQuery.isPending}
              isEmpty={!upstreamCacheReferencesQuery.data?.length}
              emptyTitle="当前筛选下暂无上游缓存引用"
            >
              <PaginatedRows items={upstreamCacheReferencesQuery.data ?? []}>
                {({ pageItems }) => (
                  <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
                    <table aria-label="上游缓存引用表" className="min-w-[980px] w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[25%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">外部引用</th>
                      <th className="w-[15%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">凭证</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">过期时间</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近使用</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.id} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{valueOrDash(row.externalCacheRef)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.providerType)}</td>
                        <td className="px-4 py-3 text-muted-foreground">{valueOrDash(row.credentialId)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={toneByStatus(row.status)}>{valueOrDash(row.status)}</StatusBadge>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.expireAt)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.lastUsedAt)}</td>
                        <td className="px-4 py-3">
                          <Button type="button" variant="outline" size="sm" onClick={() => openDetail('上游缓存引用详情', row, setDetailOpen, setDetailTitle, setDetailPayload)}>
                            详情
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            </ListContainer>
          </TabsContent>
        </Tabs>
      </PageSection>

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-w-4xl" aria-describedby={undefined}>
          <DialogHeader>
            <DialogTitle>{detailTitle}</DialogTitle>
          </DialogHeader>
          <pre className="max-h-[70vh] overflow-auto rounded-2xl border border-border/60 bg-muted/30 p-4 text-xs leading-6 text-foreground">
            {JSON.stringify(detailPayload ?? {}, null, 2)}
          </pre>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function ListContainer({
  children,
  isPending,
  isEmpty,
  emptyTitle,
  emptyAction,
}: {
  children: ReactNode
  isPending: boolean
  isEmpty: boolean
  emptyTitle: string
  emptyAction?: ReactNode
}) {
  if (isPending) {
    return <PageSkeleton count={1} />
  }
  if (isEmpty) {
    return (
      <div className="flex flex-col gap-3">
        <EmptyState title={emptyTitle} />
        {emptyAction ? <div className="flex justify-center">{emptyAction}</div> : null}
      </div>
    )
  }
  return <>{children}</>
}

function addIfNotBlank(target: Set<string>, value?: string | null) {
  if (!value) return
  const normalized = value.trim()
  if (!normalized) return
  target.add(normalized)
}

function openDetail(
  title: string,
  payload: DetailPayload,
  setOpen: (next: boolean) => void,
  setTitle: (next: string) => void,
  setPayload: (next: DetailPayload) => void,
) {
  setTitle(title)
  setPayload(payload)
  setOpen(true)
}

function toneByStatus(status?: string | null) {
  if (!status) return 'neutral' as const
  const normalized = status.toUpperCase()
  if (normalized.includes('FAIL') || normalized.includes('BLOCK') || normalized.includes('ERROR')) return 'danger' as const
  if (normalized.includes('WARN') || normalized.includes('COOLDOWN')) return 'warning' as const
  if (normalized.includes('SUCCESS') || normalized.includes('READY') || normalized.includes('ACTIVE') || normalized.includes('OPEN')) return 'success' as const
  return 'info' as const
}

function toLocalDateTimeInput(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function toIsoString(localDateTime: string) {
  if (!localDateTime) return undefined
  const parsed = new Date(localDateTime)
  if (Number.isNaN(parsed.getTime())) return undefined
  return parsed.toISOString()
}

function parseOptionalNumber(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return null
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed)) return null
  return parsed
}

function valueOrDash(value?: string | number | null) {
  if (value == null) return '-'
  if (typeof value === 'string') {
    return value.trim() || '-'
  }
  return value
}

function formatMs(value?: number | null) {
  if (value == null || Number.isNaN(value)) return '-'
  return `${Math.max(0, value).toFixed(0)} ms`
}
