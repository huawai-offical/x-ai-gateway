import { type ReactNode, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
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

type AsyncResourceSummary = {
  resourceKey: string
  resourceType?: string | null
  status?: string | null
  normalizedStatus?: string | null
  terminal?: boolean
  deleted?: boolean
  objectMode?: string | null
  upstreamObjectId?: string | null
  eventCount?: number | null
  latestTransition?: unknown
  failureReason?: string | null
  cancelReason?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

type AsyncResourceDetail = {
  lifecycle?: unknown
  transitions?: unknown[]
  lineage?: ResourceLineage | null
  artifacts?: unknown[]
  requestPayloadJson?: unknown
  responsePayloadJson?: unknown
  metadataJson?: unknown
}

type ResourceLineage = {
  summary?: ResourceLineageSummary | null
  nodes?: unknown[]
  edges?: unknown[]
}

type ResourceLineageSummary = {
  resource_type?: string | null
  resource_id?: string | null
  status?: string | null
  node_count?: number | null
  edge_count?: number | null
}

const RESOURCE_TYPE_OPTIONS = ['ALL', 'RESPONSE', 'UPLOAD'] as const
const STATUS_OPTIONS = ['ALL', 'ACTIVE', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELED', 'DELETED'] as const

export function ResourcesPage() {
  const [distributedKeyId, setDistributedKeyId] = useState('')
  const [resourceType, setResourceType] = useState<(typeof RESOURCE_TYPE_OPTIONS)[number]>('ALL')
  const [status, setStatus] = useState<(typeof STATUS_OPTIONS)[number]>('ALL')
  const [fromLocal, setFromLocal] = useState(() => toLocalDateTimeInput(new Date(Date.now() - 24 * 60 * 60 * 1000)))
  const [toLocal, setToLocal] = useState(() => toLocalDateTimeInput(new Date()))
  const [selectedResourceKey, setSelectedResourceKey] = useState<string | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const listParams = useMemo(() => ({
    distributedKeyId: parseOptionalNumber(distributedKeyId) ?? undefined,
    resourceType: resourceType === 'ALL' ? undefined : resourceType,
    status: status === 'ALL' ? undefined : status,
    from: toIsoString(fromLocal),
    to: toIsoString(toLocal),
  }), [distributedKeyId, fromLocal, resourceType, status, toLocal])

  const listQuery = useTypedQuery<AsyncResourceSummary[]>({
    queryKey: ['resources', 'async-list', listParams],
    queryFn: () => apiClient.get<AsyncResourceSummary[]>('/admin/resources/async', { params: listParams }),
  })

  const detailQuery = useTypedQuery<AsyncResourceDetail>({
    queryKey: ['resources', 'async-detail', selectedResourceKey],
    queryFn: () => apiClient.get<AsyncResourceDetail>(`/admin/resources/async/${encodeURIComponent(selectedResourceKey ?? '')}`),
    enabled: detailOpen && selectedResourceKey != null,
  })

  const selectedSummary = useMemo(
    () => (listQuery.data ?? []).find((item) => item.resourceKey === selectedResourceKey) ?? null,
    [listQuery.data, selectedResourceKey],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="异步资源"
        title="异步资源记录"
      >
        <div className="grid gap-4 rounded-2xl border border-border/60 bg-card/92 p-4 md:grid-cols-2 xl:grid-cols-5">
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">请求键 ID</span>
            <Input value={distributedKeyId} onChange={(event) => setDistributedKeyId(event.target.value)} placeholder="可选" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">资源类型</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={resourceType}
              onChange={(event) => setResourceType(event.target.value as (typeof RESOURCE_TYPE_OPTIONS)[number])}
            >
              {RESOURCE_TYPE_OPTIONS.map((option) => (
                <option key={option} value={option}>{resourceTypeLabel(option)}</option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted-foreground">状态</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={status}
              onChange={(event) => setStatus(event.target.value as (typeof STATUS_OPTIONS)[number])}
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>{resourceStatusLabel(option)}</option>
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
      </PageSection>

      <PageSection kicker="异步资源" title="资源记录清单">
        {listQuery.error ? <InlineError error={listQuery.error} title="资源记录加载失败" /> : null}
        <ListContainer
          isPending={listQuery.isPending}
          isEmpty={!listQuery.data?.length}
          emptyTitle="当前筛选下暂无资源记录"
        >
          <PaginatedRows items={listQuery.data ?? []}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">资源 Key</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">类型</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">对象</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">上游对象 ID</th>
                      <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">事件数</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">更新时间</th>
                      <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.resourceKey} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{valueOrDash(row.resourceKey)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{resourceTypeLabel(row.resourceType)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={toneByStatus(row.normalizedStatus ?? row.status)}>
                            {resourceStatusLabel(row.normalizedStatus ?? row.status)}
                          </StatusBadge>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{resourceObjectModeLabel(row.objectMode)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{valueOrDash(row.upstreamObjectId)}</td>
                        <td className="px-4 py-3 text-muted-foreground">{valueOrDash(row.eventCount)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.updatedAt)}</td>
                        <td className="px-4 py-3">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              setSelectedResourceKey(row.resourceKey)
                              setDetailOpen(true)
                            }}
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
      </PageSection>

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>资源详情</DialogTitle>
            <DialogDescription>{selectedResourceKey ?? '-'}</DialogDescription>
          </DialogHeader>

          {detailQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : detailQuery.error ? (
            <InlineError error={detailQuery.error} title="资源详情加载失败" />
          ) : detailQuery.data ? (
            <Tabs defaultValue="lifecycle" className="gap-4">
              <TabsList variant="line" className="w-full justify-start overflow-x-auto">
                <TabsTrigger value="lifecycle">生命周期</TabsTrigger>
                <TabsTrigger value="transitions">状态迁移</TabsTrigger>
                <TabsTrigger value="payload">请求/响应</TabsTrigger>
                <TabsTrigger value="metadata">元数据</TabsTrigger>
              </TabsList>

              <TabsContent value="lifecycle" className="mt-0">
                <div className="space-y-4">
                  <InfoGrid
                    columnsClassName="md:grid-cols-2 xl:grid-cols-4"
                    items={resourceDetailItems(selectedSummary, getLineageSummary(detailQuery.data.lineage))}
                  />
                  <JsonPanel value={{
                    summary: selectedSummary,
                    lifecycle: detailQuery.data.lifecycle,
                    lineage: detailQuery.data.lineage,
                    artifacts: detailQuery.data.artifacts,
                  }}
                  />
                </div>
              </TabsContent>

              <TabsContent value="transitions" className="mt-0">
                <JsonPanel value={detailQuery.data.transitions ?? []} />
              </TabsContent>

              <TabsContent value="payload" className="mt-0">
                <JsonPanel value={{
                  requestPayloadJson: detailQuery.data.requestPayloadJson,
                  responsePayloadJson: detailQuery.data.responsePayloadJson,
                }}
                />
              </TabsContent>

              <TabsContent value="metadata" className="mt-0">
                <JsonPanel value={detailQuery.data.metadataJson ?? {}} />
              </TabsContent>
            </Tabs>
          ) : (
            <EmptyState title="未找到资源详情" />
          )}
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

function JsonPanel({ value }: { value: unknown }) {
  return (
    <pre className="max-h-[60vh] overflow-auto rounded-2xl border border-border/60 bg-muted/30 p-4 text-xs leading-6 text-foreground">
      {JSON.stringify(value ?? {}, null, 2)}
    </pre>
  )
}

function getLineageSummary(lineage?: ResourceLineage | null) {
  return lineage?.summary ?? null
}

function resourceDetailItems(summary: AsyncResourceSummary | null, lineageSummary: ResourceLineageSummary | null) {
  return [
    {
      key: 'resource',
      label: '资源',
      value: valueOrDash(lineageSummary?.resource_id ?? summary?.resourceKey),
      hint: resourceTypeLabel(lineageSummary?.resource_type ?? summary?.resourceType),
    },
    {
      key: 'status',
      label: '生命周期状态',
      value: resourceStatusLabel(summary?.normalizedStatus ?? lineageSummary?.status ?? summary?.status),
      hint: summary?.terminal ? '终态' : '非终态',
    },
    {
      key: 'lineage',
      label: '谱系节点 / 边',
      value: `${valueOrDash(lineageSummary?.node_count)} / ${valueOrDash(lineageSummary?.edge_count)}`,
      hint: '谱系摘要',
    },
    {
      key: 'events',
      label: '事件数',
      value: valueOrDash(summary?.eventCount),
      hint: `更新于 ${formatInstant(summary?.updatedAt)}`,
    },
  ]
}

function resourceTypeLabel(value?: string | null) {
  switch ((value ?? '').toUpperCase()) {
    case 'ALL':
      return '全部'
    case 'RESPONSE':
      return '响应对象'
    case 'UPLOAD':
      return '上传对象'
    case 'TUNING':
      return '调优任务'
    default:
      return valueOrDash(value)
  }
}

function resourceStatusLabel(value?: string | null) {
  switch ((value ?? '').toUpperCase()) {
    case 'ALL':
      return '全部'
    case 'ACTIVE':
      return '活跃'
    case 'PROCESSING':
      return '处理中'
    case 'COMPLETED':
    case 'SUCCEEDED':
      return '已完成'
    case 'FAILED':
      return '失败'
    case 'CANCELED':
      return '已取消'
    case 'DELETED':
      return '已删除'
    default:
      return valueOrDash(value)
  }
}

function resourceObjectModeLabel(value?: string | null) {
  switch ((value ?? '').toUpperCase()) {
    case 'UPSTREAM_OBJECT_WITH_LOCAL_LINEAGE':
      return '上游对象（含本地谱系）'
    case 'UPSTREAM_OBJECT':
      return '上游对象'
    case 'LOCAL_OBJECT':
      return '本地对象'
    default:
      return valueOrDash(value)
  }
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
  if (normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('BLOCK')) return 'danger' as const
  if (normalized.includes('WARN') || normalized.includes('COOLDOWN')) return 'warning' as const
  if (normalized.includes('COMPLETE') || normalized.includes('READY') || normalized.includes('ACTIVE') || normalized.includes('SUCCESS')) return 'success' as const
  return 'info' as const
}
