import { type FormEvent, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedQuery } from '../../lib/typed-react-query'
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
    <section className="page-grid">
      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">System logs</p>
          <h2>系统日志</h2>
        </div>
        <div className="card-list">
          {systemQuery.data?.map((item: AuditLog) => (
            <div key={item.id} className="detail-card">
              <strong>{item.category} / {item.action}</strong>
              <span>{item.resourceType ?? '-'}</span>
              <span>{item.resourceRef ?? '-'}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Runtime logs</p>
          <h2>运行时日志开关</h2>
        </div>
        <div className="card-list">
          {runtimeQuery.data?.map((item: RuntimeLog) => (
            <div key={item.id} className="detail-card">
              <strong>{item.loggerName}</strong>
              <span>{item.logLevel}</span>
              <span>{item.payloadLoggingEnabled ? 'payload on' : 'payload off'}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Observability trace</p>
          <h2>联查 Trace</h2>
          <p className="empty-state">通过 requestId 串起 request log、route decision、cache hit 和 async 对象详情。</p>
        </div>
        <form className="inline-form" onSubmit={handleTraceSubmit}>
          <label className="stacked-form">
            <span>requestId</span>
            <input value={traceRequestId} onChange={(event) => setTraceRequestId(event.target.value)} placeholder="输入 requestId" />
          </label>
          <button type="submit">查询 Trace</button>
        </form>
        {(searchParams.get('providerType') || searchParams.get('requestPath')) ? (
          <div className="stack-bar">
            {searchParams.get('providerType') ? <span>providerType · {searchParams.get('providerType')}</span> : null}
            {searchParams.get('requestPath') ? <span>requestPath · {searchParams.get('requestPath')}</span> : null}
          </div>
        ) : null}
        {!submittedTraceRequestId ? (
          <p className="empty-state">输入 requestId 后可查看 trace 聚合结果。</p>
        ) : traceQuery.isPending ? (
          <p className="empty-state">正在加载 trace…</p>
        ) : traceQuery.error ? (
          <p className="empty-state">{traceQuery.error instanceof Error ? traceQuery.error.message : 'trace 查询失败。'}</p>
        ) : traceQuery.data ? (
          <div className="card-list">
            <div className="detail-grid">
              <div className="detail-card">
                <strong>requestId</strong>
                <span>{traceQuery.data.requestLog?.requestId ?? submittedTraceRequestId}</span>
              </div>
              <div className="detail-card">
                <strong>gatewayResourceKey</strong>
                <span>{traceQuery.data.requestLog?.gatewayResourceKey ?? '无'}</span>
              </div>
              <div className="detail-card">
                <strong>supportStatus</strong>
                <span>{traceQuery.data.requestLog?.supportStatus ?? '-'}</span>
              </div>
              <div className="detail-card">
                <strong>degradationLevel</strong>
                <span>{traceQuery.data.requestLog?.degradationLevel ?? '-'}</span>
              </div>
            </div>
            <div className="detail-grid">
              <div className="detail-card">
                <strong>routeDecision</strong>
                <span>{traceQuery.data.routeDecision?.selectionSource ?? '无'}</span>
              </div>
              <div className="detail-card">
                <strong>cacheHits</strong>
                <span>{traceQuery.data.cacheHits.length}</span>
              </div>
              <div className="detail-card">
                <strong>upstream refs</strong>
                <span>{traceQuery.data.upstreamCacheReferences.length}</span>
              </div>
              <div className="detail-card">
                <strong>async resource</strong>
                <span>{traceQuery.data.asyncResourceSummary?.resourceKey ?? '无'}</span>
              </div>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(traceQuery.data, null, 2)}</pre>
            </div>
          </div>
        ) : (
          <p className="empty-state">暂无 trace 结果。</p>
        )}
      </div>
    </section>
  )
}
