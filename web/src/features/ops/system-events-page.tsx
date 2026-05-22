import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type ProbeRun = {
  id: number
  probeName: string
  targetUrl: string
  status: string
  severity: string
  source: string
  latencyMs: number
  statusCode?: number | null
  errorMessage?: string | null
  detailJson?: string | null
  completedAt?: string | null
}

type SystemEvent = {
  id: number
  eventType: string
  severity: string
  source: string
  entityType?: string | null
  entityRef?: string | null
  title: string
  detailJson?: string | null
  occurredAt?: string | null
}

export function SystemEventsPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [detail, setDetail] = useState<SystemEvent | null>(null)
  const [runDetail, setRunDetail] = useState<ProbeRun | null>(null)
  const [filters, setFilters] = useState({
    severity: searchParams.get('severity') ?? '',
    source: searchParams.get('source') ?? '',
    eventType: searchParams.get('eventType') ?? '',
    entityType: searchParams.get('entityType') ?? '',
    entityRef: searchParams.get('entityRef') ?? '',
  })
  const [form, setForm] = useState({ probeName: 'manual-health', targetUrl: 'https://gateway.local/health', source: 'console', forceFailure: false })

  const eventsQuery = useQuery({
    queryKey: ['ops-system-events', filters],
    queryFn: () => apiRequest<SystemEvent[]>('/admin/ops/system-events', { params: filters }),
  })
  const runsQuery = useQuery({
    queryKey: ['ops-probe-runs'],
    queryFn: () => apiRequest<ProbeRun[]>('/admin/ops/probe-runs'),
  })
  const runMutation = useMutation({
    mutationFn: () => apiRequest<ProbeRun>('/admin/ops/probe-runs', { method: 'POST', body: form }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ops-system-events'] })
      queryClient.invalidateQueries({ queryKey: ['ops-probe-runs'] })
    },
  })

  const handleRun = (event: FormEvent) => {
    event.preventDefault()
    runMutation.mutate()
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="即时拨测" title="即时拨测运行">
        <form className="grid gap-3 md:grid-cols-[1fr_1fr_140px_120px]" onSubmit={handleRun}>
          <Input value={form.probeName} onChange={(event) => setForm((current) => ({ ...current, probeName: event.target.value }))} placeholder="拨测名称" />
          <Input value={form.targetUrl} onChange={(event) => setForm((current) => ({ ...current, targetUrl: event.target.value }))} placeholder="目标 URL" />
          <select className="flex h-10 rounded-md border border-input bg-background px-3 text-sm" value={form.source} onChange={(event) => setForm((current) => ({ ...current, source: event.target.value }))}>
            <option value="console">console</option>
            <option value="scheduler">scheduler</option>
            <option value="runbook">runbook</option>
          </select>
          <Button type="submit" disabled={runMutation.isPending}>运行</Button>
          <label className="flex items-center gap-2 text-sm text-muted-foreground md:col-span-4">
            <input type="checkbox" checked={form.forceFailure} onChange={(event) => setForm((current) => ({ ...current, forceFailure: event.target.checked }))} />
            模拟失败路径
          </label>
        </form>
        {runMutation.error ? <InlineError error={runMutation.error} title="拨测运行失败" /> : null}
      </PageSection>

      <PageSection kicker="系统事件" title="系统事件时间线">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-[160px_180px_220px_180px_1fr]">
          <select className="flex h-10 rounded-md border border-input bg-background px-3 text-sm" value={filters.severity} onChange={(event) => setFilters((current) => ({ ...current, severity: event.target.value }))}>
            <option value="">全部严重度</option>
            <option value="INFO">INFO</option>
            <option value="WARNING">WARNING</option>
            <option value="ERROR">ERROR</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
          <Input value={filters.source} onChange={(event) => setFilters((current) => ({ ...current, source: event.target.value }))} placeholder="来源筛选" />
          <Input value={filters.eventType} onChange={(event) => setFilters((current) => ({ ...current, eventType: event.target.value }))} placeholder="事件类型" />
          <Input value={filters.entityType} onChange={(event) => setFilters((current) => ({ ...current, entityType: event.target.value }))} placeholder="对象类型" />
          <Input value={filters.entityRef} onChange={(event) => setFilters((current) => ({ ...current, entityRef: event.target.value }))} placeholder="对象引用" />
        </div>
        {eventsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : eventsQuery.error ? (
          <InlineError error={eventsQuery.error} title="系统事件加载失败" />
        ) : eventsQuery.data?.length ? (
          <PaginatedRows items={(eventsQuery.data ?? []) as SystemEvent[]}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[28%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">事件</th>
                      <th className="w-[13%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">严重度</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">来源</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">对象</th>
                      <th className="w-[15%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((item) => (
                      <tr key={item.id} className="border-b border-border/40">
                        <td className="truncate px-4 py-3 font-medium text-foreground">{item.title}</td>
                        <td className="px-4 py-3"><StatusBadge tone={severityTone(item.severity)}>{item.severity}</StatusBadge></td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.source}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.entityType ?? '-'} / {item.entityRef ?? '-'}</td>
                        <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.occurredAt)}</td>
                        <td className="px-4 py-3"><Button type="button" size="sm" variant="outline" onClick={() => setDetail(item)}>详情</Button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="当前没有系统事件" />
        )}
      </PageSection>

      <PageSection kicker="拨测历史" title="拨测运行历史">
        {runsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : runsQuery.error ? (
          <InlineError error={runsQuery.error} title="拨测运行加载失败" />
        ) : runsQuery.data?.length ? (
          <PaginatedRows items={(runsQuery.data ?? []) as ProbeRun[]}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">名称</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">延迟</th>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">目标</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((run) => (
                      <tr key={run.id} className="border-b border-border/40">
                        <td className="truncate px-4 py-3 font-medium text-foreground">{run.probeName}</td>
                        <td className="px-4 py-3"><StatusBadge tone={run.status === 'SUCCEEDED' ? 'success' : 'danger'}>{run.status}</StatusBadge></td>
                        <td className="px-4 py-3 text-muted-foreground">{run.latencyMs} ms · {run.statusCode ?? '-'}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{run.targetUrl}</td>
                        <td className="px-4 py-3"><Button type="button" size="sm" variant="outline" onClick={() => setRunDetail(run)}>详情</Button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="还没有拨测运行历史" />
        )}
      </PageSection>

      <Dialog open={detail != null} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>系统事件详情</DialogTitle>
            <DialogDescription />
          </DialogHeader>
          {detail ? (
            <div className="space-y-4">
              <InfoGrid items={[
                { key: 'type', label: '类型', value: detail.eventType },
                { key: 'severity', label: '严重度', value: detail.severity },
                { key: 'source', label: '来源', value: detail.source },
                { key: 'time', label: '发生时间', value: formatInstant(detail.occurredAt) },
              ]} columnsClassName="md:grid-cols-2" />
              <CodePanel title="详情 JSON" code={detail.detailJson ?? '{}'} />
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={runDetail != null} onOpenChange={(open) => !open && setRunDetail(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>拨测运行详情</DialogTitle>
            <DialogDescription />
          </DialogHeader>
          {runDetail ? (
            <div className="space-y-4">
              <InfoGrid items={[
                { key: 'name', label: '名称', value: runDetail.probeName },
                { key: 'status', label: '状态', value: runDetail.status },
                { key: 'latency', label: '延迟', value: `${runDetail.latencyMs} ms` },
                { key: 'target', label: '目标', value: runDetail.targetUrl },
                { key: 'error', label: '错误', value: runDetail.errorMessage ?? '无' },
              ]} columnsClassName="md:grid-cols-2" />
              <CodePanel title="详情 JSON" code={runDetail.detailJson ?? '{}'} />
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function severityTone(severity: string) {
  if (severity === 'ERROR' || severity === 'CRITICAL') return 'danger'
  if (severity === 'WARNING') return 'warning'
  return 'info'
}
