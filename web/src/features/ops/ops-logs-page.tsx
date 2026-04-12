import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type AuditLog = { id: number; category: string; action: string; resourceType?: string | null; resourceRef?: string | null; detailJson?: string | null }
type RuntimeLog = { id: number; loggerName: string; logLevel: string; payloadLoggingEnabled: boolean }

export function OpsLogsPage() {
  const systemQuery = useQuery({
    queryKey: ['ops-system-logs'],
    queryFn: () => apiRequest<AuditLog[]>('/admin/ops/logs/system'),
  })
  const runtimeQuery = useQuery({
    queryKey: ['ops-runtime-logs'],
    queryFn: () => apiRequest<RuntimeLog[]>('/admin/ops/logs/runtime'),
  })

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
    </section>
  )
}
