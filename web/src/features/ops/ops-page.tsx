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

type OpsSloSummary = {
  summary: {
    requestCount: number
    failedRequestCount: number
    errorRate: number
    errorBudgetRatio: number
    errorBudgetRemainingRatio: number
    burnRate: number
    riskLevel: string
    silencedAlertCount: number
  }
  risks: Array<{
    scopeType: string
    scopeRef?: string | null
    policyName: string
    burnRate: number
    errorBudgetRemainingRatio: number
    riskLevel: string
    suspectedCauses: string[]
    suggestedActions: string[]
  }>
  recommendedActions: string[]
}

type OpsCapacitySummary = {
  distributedKeys: Array<{
    distributedKeyId: number
    keyName: string
    maskedKey: string
    pressureLevel: string
    budgetLimitMicros?: number | null
    currentBudgetMicros?: number | null
    remainingBudgetMicros?: number | null
    rpmLimit?: number | null
    currentRpm?: number | null
    remainingRpm?: number | null
    tpmLimit?: number | null
    currentTpm?: number | null
    remainingTpm?: number | null
    concurrencyLimit?: number | null
    currentConcurrency?: number | null
    remainingConcurrency?: number | null
    notes: string[]
  }>
  recommendedActions: string[]
}

export function OpsPage() {
  const [events, setEvents] = useState<string[]>([])
  const summaryQuery = useQuery({
    queryKey: ['ops-summary'],
    queryFn: () => apiRequest<OpsSummary>('/admin/ops/summary'),
    refetchInterval: 10_000,
  })
  const sloQuery = useQuery({
    queryKey: ['ops-slo'],
    queryFn: () => apiRequest<OpsSloSummary>('/admin/ops/slo'),
    refetchInterval: 10_000,
  })
  const capacityQuery = useQuery({
    queryKey: ['ops-capacity'],
    queryFn: () => apiRequest<OpsCapacitySummary>('/admin/ops/capacity'),
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
          <p className="panel-kicker">SLO</p>
          <h2>错误预算</h2>
        </div>
        <div className="detail-grid">
          <div className="detail-card">
            <strong>风险等级</strong>
            <span>{sloQuery.data?.summary.riskLevel ?? '-'}</span>
          </div>
          <div className="detail-card">
            <strong>Error budget remaining</strong>
            <span>{sloQuery.data ? `${(sloQuery.data.summary.errorBudgetRemainingRatio * 100).toFixed(1)}%` : '-'}</span>
          </div>
          <div className="detail-card">
            <strong>Burn rate</strong>
            <span>{sloQuery.data?.summary.burnRate?.toFixed(2) ?? '-'}</span>
          </div>
          <div className="detail-card">
            <strong>Silenced alerts</strong>
            <span>{sloQuery.data?.summary.silencedAlertCount ?? '-'}</span>
          </div>
        </div>
        <div className="card-list">
          {sloQuery.data?.risks.map((risk: OpsSloSummary['risks'][number]) => (
            <div key={`${risk.policyName}-${risk.scopeType}-${risk.scopeRef ?? 'global'}`} className="detail-card">
              <strong>{risk.policyName}</strong>
              <span>{risk.scopeType}{risk.scopeRef ? ` / ${risk.scopeRef}` : ''}</span>
              <span>{risk.riskLevel} · burn rate {risk.burnRate.toFixed(2)}</span>
              <span>{risk.suspectedCauses[0] ?? '当前没有额外风险说明。'}</span>
            </div>
          ))}
          {sloQuery.data?.recommendedActions.map((action: string) => (
            <div key={action} className="detail-card">
              <strong>Recommended action</strong>
              <span>{action}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Capacity</p>
          <h2>预算压力</h2>
        </div>
        <div className="card-list">
          {capacityQuery.data?.distributedKeys.map((item: OpsCapacitySummary['distributedKeys'][number]) => (
            <div key={item.distributedKeyId} className="detail-card">
              <strong>{item.keyName}</strong>
              <span>{item.pressureLevel} · {item.maskedKey}</span>
              <span>
                budget {item.currentBudgetMicros ?? 0}/{item.budgetLimitMicros ?? 0} ·
                rpm {item.currentRpm ?? 0}/{item.rpmLimit ?? 0}
              </span>
              <span>
                tpm {item.currentTpm ?? 0}/{item.tpmLimit ?? 0} ·
                concurrency {item.currentConcurrency ?? 0}/{item.concurrencyLimit ?? 0}
              </span>
              <span>{item.notes[0] ?? '当前窗口压力平稳。'}</span>
            </div>
          ))}
          {capacityQuery.data?.recommendedActions.map((action: string) => (
            <div key={action} className="detail-card">
              <strong>Recommended action</strong>
              <span>{action}</span>
            </div>
          ))}
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
