import { type FormEvent, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { apiRequest } from '@/lib/api'
import { useTypedQuery } from '@/lib/typed-react-query'
import { type ObservabilityTraceResponse } from '../provider-sites/types'

type AuditLog = { id: number; category: string; action: string; resourceType?: string | null; resourceRef?: string | null; detailJson?: string | null }
type RuntimeLog = { id: number; loggerName: string; logLevel: string; payloadLoggingEnabled: boolean }

export function OpsLogsPage() {
  const [searchParams] = useSearchParams()
  const [traceRequestId, setTraceRequestId] = useState(searchParams.get('requestId') ?? '')
  const [submittedTraceRequestId, setSubmittedTraceRequestId] = useState(searchParams.get('requestId') ?? '')
  const systemQuery = useQuery({
    queryKey: ['ops-system-logs'],
    queryFn: () => apiRequest<AuditLog[]>('/admin/ops/logs/system'),
  })
  const runtimeQuery = useQuery({
    queryKey: ['ops-runtime-logs'],
    queryFn: () => apiRequest<RuntimeLog[]>('/admin/ops/logs/runtime'),
  })
  const traceQuery = useTypedQuery<ObservabilityTraceResponse>({
    queryKey: ['ops-observability-trace', submittedTraceRequestId],
    queryFn: () => apiRequest<ObservabilityTraceResponse>(`/admin/observability/traces/${encodeURIComponent(submittedTraceRequestId)}`),
    enabled: Boolean(submittedTraceRequestId),
  })

  const handleTraceSubmit = (event: FormEvent) => {
    event.preventDefault()
    setSubmittedTraceRequestId(traceRequestId.trim())
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="系统日志" title="系统审计日志">
        {systemQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : systemQuery.error ? (
          <InlineError error={systemQuery.error} title="系统日志加载失败" />
        ) : systemQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {systemQuery.data.map((item: AuditLog) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.category} / {item.action}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-2 p-5 text-sm text-muted-foreground">
                  <div>{item.resourceType ?? '-'}</div>
                  <div>{item.resourceRef ?? '-'}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有系统日志" />
        )}
      </PageSection>

      <PageSection kicker="运行时日志" title="运行时日志开关">
        {runtimeQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : runtimeQuery.error ? (
          <InlineError error={runtimeQuery.error} title="运行时日志配置加载失败" />
        ) : runtimeQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {runtimeQuery.data.map((item: RuntimeLog) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.loggerName}</CardTitle>
                </CardHeader>
                <CardContent className="p-5 text-sm text-muted-foreground">
                  <div>{item.logLevel}</div>
                  <div>{item.payloadLoggingEnabled ? '载荷记录已开启' : '载荷记录已关闭'}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有运行时日志配置" />
        )}
      </PageSection>

      <PageSection kicker="链路追踪" title="联查链路">
        <form className="grid gap-4 md:grid-cols-[minmax(0,1fr)_auto]" onSubmit={handleTraceSubmit}>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">请求 ID</span>
            <Input value={traceRequestId} onChange={(event) => setTraceRequestId(event.target.value)} placeholder="输入请求 ID" />
          </label>
          <div className="flex items-end">
            <Button type="submit">查询链路</Button>
          </div>
        </form>
        {(searchParams.get('providerType') || searchParams.get('requestPath')) ? (
          <div className="flex flex-wrap gap-2 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
            {searchParams.get('providerType') ? <span>提供方类型 · {searchParams.get('providerType')}</span> : null}
            {searchParams.get('requestPath') ? <span>请求路径 · {searchParams.get('requestPath')}</span> : null}
          </div>
        ) : null}
        {!submittedTraceRequestId ? (
          <EmptyState title="输入请求 ID 后可查看链路聚合结果" />
        ) : traceQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : traceQuery.error ? (
          <InlineError error={traceQuery.error} title="链路查询失败" />
        ) : traceQuery.data ? (
          <div className="flex flex-col gap-6">
            <InfoGrid
              items={[
                { key: 'request-id', label: '请求 ID', value: traceQuery.data.requestLog?.requestId ?? submittedTraceRequestId },
                { key: 'resource-key', label: '网关资源键', value: traceQuery.data.requestLog?.gatewayResourceKey ?? '无' },
                { key: 'support-status', label: '支持状态', value: traceQuery.data.requestLog?.supportStatus ?? '-' },
                { key: 'degradation', label: '降级级别', value: traceQuery.data.requestLog?.degradationLevel ?? '-' },
                { key: 'route', label: '路由决策', value: traceQuery.data.routeDecision?.selectionSource ?? '无' },
                { key: 'cache-hits', label: '缓存命中', value: traceQuery.data.cacheHits.length },
                { key: 'upstream-refs', label: '上游缓存引用', value: traceQuery.data.upstreamCacheReferences.length },
                { key: 'async-resource', label: '异步资源', value: traceQuery.data.asyncResourceSummary?.resourceKey ?? '无' },
              ]}
              columnsClassName="md:grid-cols-2 xl:grid-cols-4"
            />
            <CodePanel title="链路 JSON" code={JSON.stringify(traceQuery.data, null, 2)} />
          </div>
        ) : (
          <EmptyState title="暂无链路结果" />
        )}
      </PageSection>
    </div>
  )
}
