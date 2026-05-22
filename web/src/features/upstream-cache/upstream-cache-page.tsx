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
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiClient } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import { useTypedQuery } from '@/lib/typed-react-query'

type ObservabilitySummary = {
  sampledFrom?: string | null
  sampledTo?: string | null
  sampledRouteDecisionCount: number
  sampledCacheHitCount: number
  sampledActiveUpstreamCacheReferenceCount: number
  sampledUsageRecordCount: number
  sampledFinalUsageRecordCount: number
  sampledPartialUsageRecordCount: number
  totalCacheHitTokens: number
  totalCacheWriteTokens: number
  totalSavedInputTokens: number
}

type CacheHitRow = {
  id: number
  requestId?: string | null
  protocol?: string | null
  requestPath?: string | null
  providerType?: string | null
  credentialId?: number | null
  modelGroup?: string | null
  cacheKind?: string | null
  cacheHitTokens?: number | null
  cacheWriteTokens?: number | null
  savedInputTokens?: number | null
  cachedContentRef?: string | null
  createdAt?: string | null
}

type UpstreamCacheReferenceRow = {
  id: number
  distributedKeyId?: number | null
  providerType?: string | null
  credentialId?: number | null
  modelGroup?: string | null
  externalCacheRef?: string | null
  status?: string | null
  effectiveStatus?: string | null
  expired?: boolean | null
  active?: boolean | null
  expireAt?: string | null
  lastUsedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  lifecycle?: CacheLifecycle | null
}

type CacheLifecycle = {
  status?: string | null
  effective_status?: string | null
  expired?: boolean | null
  active?: boolean | null
  expire_at?: string | null
  last_used_at?: string | null
}

type CacheTab = 'references' | 'hits'
type DetailPayload = CacheHitRow | UpstreamCacheReferenceRow

const PROVIDER_OPTIONS = [
  'ALL',
  'OPENAI_DIRECT',
  'OPENAI_COMPATIBLE',
  'GEMINI_DIRECT',
  'ANTHROPIC_DIRECT',
  'OLLAMA_DIRECT',
  'OPENAI_OAUTH',
  'GEMINI_OAUTH',
  'CLAUDE_ACCOUNT',
] as const

const REF_STATUS_OPTIONS = ['ALL', 'ACTIVE', 'EXPIRED', 'INVALIDATED'] as const

export function UpstreamCachePage() {
  const [activeTab, setActiveTab] = useState<CacheTab>('references')
  const [distributedKeyId, setDistributedKeyId] = useState('')
  const [providerType, setProviderType] = useState<(typeof PROVIDER_OPTIONS)[number]>('ALL')
  const [referenceStatus, setReferenceStatus] = useState<(typeof REF_STATUS_OPTIONS)[number]>('ALL')
  const [fromLocal, setFromLocal] = useState(() => toLocalDateTimeInput(new Date(Date.now() - 24 * 60 * 60 * 1000)))
  const [toLocal, setToLocal] = useState(() => toLocalDateTimeInput(new Date()))
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailTitle, setDetailTitle] = useState('')
  const [detailPayload, setDetailPayload] = useState<DetailPayload | null>(null)

  const baseParams = useMemo(() => ({
    distributedKeyId: parseOptionalNumber(distributedKeyId) ?? undefined,
    providerType: providerType === 'ALL' ? undefined : providerType,
    from: toIsoString(fromLocal),
    to: toIsoString(toLocal),
  }), [distributedKeyId, fromLocal, providerType, toLocal])

  const summaryQuery = useTypedQuery<ObservabilitySummary>({
    queryKey: ['upstream-cache', 'summary', baseParams],
    queryFn: () => apiClient.get<ObservabilitySummary>('/admin/observability/summary', { params: baseParams }),
  })

  const referencesQuery = useTypedQuery<UpstreamCacheReferenceRow[]>({
    queryKey: ['upstream-cache', 'references', baseParams, referenceStatus],
    queryFn: () =>
      apiClient.get<UpstreamCacheReferenceRow[]>('/admin/observability/upstream-cache-references', {
        params: {
          ...baseParams,
          status: referenceStatus === 'ALL' ? undefined : referenceStatus,
        },
      }),
  })

  const cacheHitsQuery = useTypedQuery<CacheHitRow[]>({
    queryKey: ['upstream-cache', 'hits', baseParams],
    queryFn: () => apiClient.get<CacheHitRow[]>('/admin/observability/cache-hits', { params: baseParams }),
  })

  const currentQuery = activeTab === 'references' ? referencesQuery : cacheHitsQuery
  const pageError = summaryQuery.error ?? currentQuery.error
  const summary = summaryQuery.data

  const cacheHitRatio = summary && summary.sampledRouteDecisionCount > 0
    ? summary.sampledCacheHitCount / summary.sampledRouteDecisionCount
    : 0

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="缓存记录"
        title="缓存记录"
      >
        {pageError ? <InlineError error={pageError} title="缓存观测加载失败" /> : null}

        <div className="grid gap-4 rounded-2xl border border-border/60 bg-card/92 p-4 md:grid-cols-2 xl:grid-cols-5">
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">访问密钥 ID</span>
            <Input value={distributedKeyId} onChange={(event) => setDistributedKeyId(event.target.value)} placeholder="可选" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">提供方类型</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={providerType}
              onChange={(event) => setProviderType(event.target.value as (typeof PROVIDER_OPTIONS)[number])}
            >
              {PROVIDER_OPTIONS.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">引用状态</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={referenceStatus}
              onChange={(event) => setReferenceStatus(event.target.value as (typeof REF_STATUS_OPTIONS)[number])}
            >
              {REF_STATUS_OPTIONS.map((option) => (
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
        </div>

        {summaryQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : summary ? (
          <InfoGrid
            className="mt-4"
            columnsClassName="md:grid-cols-2 xl:grid-cols-5"
            items={[
              {
                key: 'window',
                label: '采样窗口',
                value: `${formatInstant(summary.sampledFrom)} ~ ${formatInstant(summary.sampledTo)}`,
              },
              {
                key: 'hit-ratio',
                label: '缓存命中率',
                value: `${(cacheHitRatio * 100).toFixed(2)}%`,
                hint: `${summary.sampledCacheHitCount}/${summary.sampledRouteDecisionCount}`,
              },
              {
                key: 'active-ref',
                label: '活跃引用',
                value: summary.sampledActiveUpstreamCacheReferenceCount.toLocaleString('zh-CN'),
              },
              {
                key: 'token-gain',
                label: 'Token 收益',
                value: `节省 ${summary.totalSavedInputTokens.toLocaleString('zh-CN')}`,
                hint: `命中 ${summary.totalCacheHitTokens.toLocaleString('zh-CN')} / 写入 ${summary.totalCacheWriteTokens.toLocaleString('zh-CN')}`,
              },
              {
                key: 'usage-complete',
                label: '用量完整性',
                value: `${summary.sampledFinalUsageRecordCount}/${summary.sampledUsageRecordCount}`,
                hint: `部分 ${summary.sampledPartialUsageRecordCount.toLocaleString('zh-CN')}`,
              },
            ]}
          />
        ) : null}
      </PageSection>

      <PageSection kicker="缓存记录" title="引用与命中记录">
        <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as CacheTab)} className="gap-4">
          <TabsList variant="line" className="w-full justify-start overflow-x-auto">
            <TabsTrigger value="references">上游缓存引用</TabsTrigger>
            <TabsTrigger value="hits">缓存命中记录</TabsTrigger>
          </TabsList>

          <TabsContent value="references" className="mt-0">
            <ListContainer
              isPending={referencesQuery.isPending}
              isEmpty={!referencesQuery.data?.length}
              emptyTitle="当前筛选下暂无上游缓存引用"
            >
              <PaginatedRows items={referencesQuery.data ?? []}>
                {({ pageItems }) => (
                  <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                    <table className="w-full table-fixed text-sm">
                      <thead className="bg-muted/30">
                        <tr>
                          <th className="w-[27%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">外部引用</th>
                          <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                          <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">凭证</th>
                          <th className="w-[11%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                          <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">过期时间</th>
                          <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近使用</th>
                          <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pageItems.map((row) => (
                          <tr key={row.id} className="border-b border-border/40 align-top">
                            <td className="truncate px-4 py-3 text-foreground">{valueOrDash(row.externalCacheRef)}</td>
                            <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.providerType)}</td>
                            <td className="px-4 py-3 text-muted-foreground">{valueOrDash(row.credentialId)}</td>
                            <td className="px-4 py-3">
                              <StatusBadge tone={toneByStatus(row.effectiveStatus ?? row.status)}>{valueOrDash(row.effectiveStatus ?? row.status)}</StatusBadge>
                            </td>
                            <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.expireAt)}</td>
                            <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.lastUsedAt)}</td>
                            <td className="px-4 py-3">
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => openDetail('上游缓存引用详情', row, setDetailOpen, setDetailTitle, setDetailPayload)}
                              >
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

          <TabsContent value="hits" className="mt-0">
            <ListContainer
              isPending={cacheHitsQuery.isPending}
              isEmpty={!cacheHitsQuery.data?.length}
              emptyTitle="当前筛选下暂无缓存命中记录"
            >
              <PaginatedRows items={cacheHitsQuery.data ?? []}>
                {({ pageItems }) => (
                  <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                    <table className="w-full table-fixed text-sm">
                      <thead className="bg-muted/30">
                        <tr>
                          <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">请求 ID</th>
                          <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">缓存类型</th>
                          <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                          <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型组</th>
                          <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Token 收益</th>
                          <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                          <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pageItems.map((row) => (
                          <tr key={row.id} className="border-b border-border/40 align-top">
                            <td className="truncate px-4 py-3 text-foreground">{valueOrDash(row.requestId)}</td>
                            <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.cacheKind)}</td>
                            <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.providerType)}</td>
                            <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.modelGroup)}</td>
                            <td className="truncate px-4 py-3 text-muted-foreground">
                              命中 {valueOrDash(row.cacheHitTokens)} / 写入 {valueOrDash(row.cacheWriteTokens)} / 节省 {valueOrDash(row.savedInputTokens)}
                            </td>
                            <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.createdAt)}</td>
                            <td className="px-4 py-3">
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => openDetail('缓存命中详情', row, setDetailOpen, setDetailTitle, setDetailPayload)}
                              >
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
          {isCacheReferencePayload(detailPayload) ? (
            <InfoGrid
              columnsClassName="md:grid-cols-2 xl:grid-cols-4"
              items={cacheReferenceDetailItems(detailPayload)}
            />
          ) : null}
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
}: {
  children: ReactNode
  isPending: boolean
  isEmpty: boolean
  emptyTitle: string
}) {
  if (isPending) return <PageSkeleton count={1} />
  if (isEmpty) return <EmptyState title={emptyTitle} />
  return <>{children}</>
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

function isCacheReferencePayload(payload: DetailPayload | null): payload is UpstreamCacheReferenceRow {
  return Boolean(payload && 'externalCacheRef' in payload)
}

function cacheReferenceDetailItems(row: UpstreamCacheReferenceRow) {
  const lifecycle = row.lifecycle
  return [
    {
      key: 'cache',
      label: '缓存引用',
      value: valueOrDash(row.externalCacheRef),
      hint: `凭证 ${valueOrDash(row.credentialId)}`,
    },
    {
      key: 'status',
      label: '有效状态',
      value: valueOrDash(row.effectiveStatus ?? lifecycle?.effective_status ?? row.status),
      hint: `原始 ${valueOrDash(lifecycle?.status ?? row.status)}`,
    },
    {
      key: 'active',
      label: '活跃 / 过期',
      value: `${booleanLabel(row.active ?? lifecycle?.active)} / ${booleanLabel(row.expired ?? lifecycle?.expired)}`,
      hint: '活跃 / 过期',
    },
    {
      key: 'time',
      label: '过期 / 最近使用',
      value: `${formatInstant(lifecycle?.expire_at ?? row.expireAt)} / ${formatInstant(lifecycle?.last_used_at ?? row.lastUsedAt)}`,
      hint: valueOrDash(row.modelGroup),
    },
  ]
}

function booleanLabel(value?: boolean | null) {
  if (value == null) return '-'
  return value ? '是' : '否'
}

function parseOptionalNumber(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return null
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed)) return null
  return parsed
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

function valueOrDash(value?: string | number | null) {
  if (value == null) return '-'
  if (typeof value === 'string') {
    return value.trim() || '-'
  }
  return value
}

function toneByStatus(status?: string | null) {
  if (!status) return 'neutral' as const
  const normalized = status.toUpperCase()
  if (normalized.includes('INVALID') || normalized.includes('ERROR') || normalized.includes('FAIL')) return 'danger' as const
  if (normalized.includes('EXPIRED') || normalized.includes('WARNING')) return 'warning' as const
  if (normalized.includes('ACTIVE') || normalized.includes('SUCCESS')) return 'success' as const
  return 'info' as const
}
