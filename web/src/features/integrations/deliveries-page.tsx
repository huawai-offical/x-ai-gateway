import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import type { NotificationChannel, OutboundDelivery } from './types'

export function DeliveriesPage() {
  const queryClient = useQueryClient()
  const [eventType, setEventType] = useState('')
  const [deliveryStatus, setDeliveryStatus] = useState('')
  const [testChannelId, setTestChannelId] = useState('')

  const channelsQuery = useQuery({
    queryKey: ['integrations', 'channels'],
    queryFn: () => apiRequest<NotificationChannel[]>('/admin/integrations/channels'),
  })
  const deliveriesQuery = useQuery({
    queryKey: ['integrations', 'deliveries', eventType, deliveryStatus],
    queryFn: () => {
      const search = new URLSearchParams()
      if (eventType) search.set('eventType', eventType)
      if (deliveryStatus) search.set('deliveryStatus', deliveryStatus)
      const query = search.toString()
      return apiRequest<OutboundDelivery[]>(`/admin/integrations/deliveries${query ? `?${query}` : ''}`)
    },
  })

  const replayMutation = useMutation({
    mutationFn: (id: number) => apiRequest<OutboundDelivery>(`/admin/integrations/deliveries/${id}/replay`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['integrations', 'deliveries'] }),
  })
  const testMutation = useMutation({
    mutationFn: () =>
      apiRequest<OutboundDelivery>('/admin/integrations/test-delivery', {
        method: 'POST',
        body: JSON.stringify({
          channelId: Number(testChannelId),
          eventType: 'ALERT_OPENED',
          severity: 'INFO',
          entityType: 'SYSTEM',
          entityRef: 'test',
          summary: 'manual test delivery',
          details: { source: 'g5-ui-test' },
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['integrations', 'deliveries'] }),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Deliveries</p>
          <h2>投递记录、重试与重放</h2>
        </div>
        <div className="inline-form">
          <input value={eventType} onChange={(event) => setEventType(event.target.value)} placeholder="event type" />
          <input value={deliveryStatus} onChange={(event) => setDeliveryStatus(event.target.value)} placeholder="delivery status" />
          <select value={testChannelId} onChange={(event) => setTestChannelId(event.target.value)} aria-label="test channel">
            <option value="">选择测试 channel</option>
            {channelsQuery.data?.map((item: NotificationChannel) => (
              <option key={item.id} value={item.id}>{item.channelName}</option>
            ))}
          </select>
          <button type="button" onClick={() => testMutation.mutate()} disabled={!testChannelId}>
            发送测试投递
          </button>
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Recent deliveries</p>
          <h3>最近投递</h3>
        </div>
        <div className="card-list">
          {deliveriesQuery.data?.map((item: OutboundDelivery) => (
            <div key={item.id} className="detail-card">
              <strong>{item.eventType}</strong>
              <span>{item.deliveryStatus} / attempt {item.attemptCount}</span>
              <span>{item.responseSummary ?? item.lastError ?? '等待投递结果'}</span>
              <span>{item.entityType ?? 'SYSTEM'} / {item.entityRef ?? '-'}</span>
              <div className="inline-actions">
                {item.requestId ? <Link className="action-link" to={`/traces?requestId=${encodeURIComponent(item.requestId)}`}>查看 Trace</Link> : null}
                {item.deliveryStatus !== 'SUCCEEDED' ? (
                  <button type="button" onClick={() => replayMutation.mutate(item.id)}>重放</button>
                ) : null}
              </div>
              <details>
                <summary>payload</summary>
                <pre>{item.payloadJson}</pre>
              </details>
            </div>
          ))}
          {!deliveriesQuery.data?.length ? <p className="empty-state">当前没有投递记录。</p> : null}
        </div>
      </div>
    </section>
  )
}
