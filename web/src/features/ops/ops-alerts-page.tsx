import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type AlertRule = {
  id: number
  ruleName: string
  metricKey: string
  comparisonOperator: string
  thresholdValue: number
  severity: string
}

type AlertEvent = {
  id: number
  title: string
  severity: string
  status: string
  message: string
}

export function OpsAlertsPage() {
  const queryClient = useQueryClient()
  const [ruleName, setRuleName] = useState('')
  const [metricKey, setMetricKey] = useState('qps')
  const [thresholdValue, setThresholdValue] = useState('1')

  const rulesQuery = useQuery({
    queryKey: ['ops-alert-rules'],
    queryFn: () => apiRequest<AlertRule[]>('/admin/ops/alerts/rules'),
  })
  const alertsQuery = useQuery({
    queryKey: ['ops-alert-events'],
    queryFn: () => apiRequest<AlertEvent[]>('/admin/ops/alerts?status=OPEN'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/ops/alerts/rules', {
        method: 'POST',
        body: JSON.stringify({
          ruleName,
          metricKey,
          comparisonOperator: '>',
          thresholdValue: Number(thresholdValue),
          severity: 'HIGH',
          enabled: true,
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-alert-rules'] }),
  })

  const ackMutation = useMutation({
    mutationFn: (id: number) => apiRequest(`/admin/ops/alerts/${id}/ack`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-alert-events'] }),
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Alert rules</p>
          <h2>告警规则</h2>
        </div>
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={ruleName} onChange={(e) => setRuleName(e.target.value)} placeholder="规则名称" />
          <input value={metricKey} onChange={(e) => setMetricKey(e.target.value)} placeholder="metric key" />
          <input value={thresholdValue} onChange={(e) => setThresholdValue(e.target.value)} placeholder="阈值" />
          <button type="submit">创建</button>
        </form>
      </div>
      <div className="panel">
        <div className="card-list">
          {rulesQuery.data?.map((rule: AlertRule) => (
            <div key={rule.id} className="detail-card">
              <strong>{rule.ruleName}</strong>
              <span>{rule.metricKey}</span>
              <span>{rule.comparisonOperator} {rule.thresholdValue}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="panel">
        <div className="card-list">
          {alertsQuery.data?.map((alert: AlertEvent) => (
            <div key={alert.id} className="detail-card">
              <strong>{alert.title}</strong>
              <span>{alert.severity}</span>
              <span>{alert.message}</span>
              <button type="button" onClick={() => ackMutation.mutate(alert.id)}>Ack</button>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
