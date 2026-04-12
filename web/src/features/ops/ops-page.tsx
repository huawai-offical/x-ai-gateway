import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type OpsSummary = {
  snapshot: {
    observedAt: string
    qps: number
    errorRate: number
    p95LatencyMs: number
    providerFailures: number
    activeAlerts: number
    affectedEntities: string[]
  }
  alerts: Array<{ id: number; title: string; severity: string; status: string }>
}

export function OpsPage() {
  const [events, setEvents] = useState<string[]>([])
  const summaryQuery = useQuery({
    queryKey: ['ops-summary'],
    queryFn: () => apiRequest<OpsSummary>('/admin/ops/summary'),
    refetchInterval: 10_000,
  })

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const socket = new WebSocket(`${protocol}://${window.location.host}/admin/ops/ws`)
    socket.onmessage = (event) => {
      setEvents((previous) => [event.data, ...previous].slice(0, 20))
    }
    return () => socket.close()
  }, [])

  const cards = useMemo(() => {
    const snapshot = summaryQuery.data?.snapshot
    if (!snapshot) return []
    return [
      { label: 'QPS', value: snapshot.qps.toFixed(2) },
      { label: '错误率', value: `${(snapshot.errorRate * 100).toFixed(1)}%` },
      { label: '活跃告警', value: String(snapshot.activeAlerts) },
      { label: 'Provider failures', value: String(snapshot.providerFailures) },
    ]
  }, [summaryQuery.data])

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Ops realtime</p>
          <h2>实时指挥台</h2>
        </div>
        <div className="detail-grid">
          {cards.map((card) => (
            <div key={card.label} className="detail-card">
              <strong>{card.label}</strong>
              <span>{card.value}</span>
            </div>
          ))}
        </div>
        <div className="inline-actions">
          <Link className="action-link" to="/ops/alerts">查看告警</Link>
          <Link className="action-link" to="/ops/probes">查看 Probe</Link>
          <Link className="action-link" to="/ops/logs">查看日志</Link>
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">WebSocket</p>
          <h2>实时事件流</h2>
        </div>
        <div className="code-block">
          <pre>{events.join('\n') || '等待事件...'}</pre>
        </div>
      </div>
    </section>
  )
}
