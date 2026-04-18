import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { apiRequest } from '../../lib/api'
import type { NotificationChannel, OutboundSubscription } from './types'

export function SubscriptionsPage() {
  const queryClient = useQueryClient()
  const [subscriptionName, setSubscriptionName] = useState('')
  const [channelId, setChannelId] = useState('')
  const [eventType, setEventType] = useState('ALERT_OPENED')
  const [severity, setSeverity] = useState('HIGH')
  const [entityType, setEntityType] = useState('CREDENTIAL')

  const channelsQuery = useQuery({
    queryKey: ['integrations', 'channels'],
    queryFn: () => apiRequest<NotificationChannel[]>('/admin/integrations/channels'),
  })
  const subscriptionsQuery = useQuery({
    queryKey: ['integrations', 'subscriptions'],
    queryFn: () => apiRequest<OutboundSubscription[]>('/admin/integrations/subscriptions'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<OutboundSubscription>('/admin/integrations/subscriptions', {
        method: 'POST',
        body: JSON.stringify({
          subscriptionName,
          channelId: Number(channelId),
          eventType,
          severity,
          entityType,
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'subscriptions'] })
      setSubscriptionName('')
    },
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Subscriptions</p>
          <h2>事件订阅</h2>
        </div>
        <div className="inline-form">
          <input value={subscriptionName} onChange={(event) => setSubscriptionName(event.target.value)} placeholder="subscription name" />
          <select value={channelId} onChange={(event) => setChannelId(event.target.value)} aria-label="channel id">
            <option value="">选择 channel</option>
            {channelsQuery.data?.map((item: NotificationChannel) => (
              <option key={item.id} value={item.id}>{item.channelName}</option>
            ))}
          </select>
          <input value={eventType} onChange={(event) => setEventType(event.target.value)} placeholder="event type" />
          <input value={severity} onChange={(event) => setSeverity(event.target.value)} placeholder="severity" />
          <input value={entityType} onChange={(event) => setEntityType(event.target.value)} placeholder="entity type" />
          <button type="button" onClick={() => createMutation.mutate()} disabled={!subscriptionName || !channelId}>
            创建订阅
          </button>
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Active subscriptions</p>
          <h3>订阅列表</h3>
        </div>
        <div className="card-list">
          {subscriptionsQuery.data?.map((item: OutboundSubscription) => (
            <div key={item.id} className="detail-card">
              <strong>{item.subscriptionName}</strong>
              <span>channel #{item.channelId}</span>
              <span>{item.eventType ?? 'ALL'} / {item.severity ?? 'ALL'} / {item.entityType ?? 'ALL'}</span>
              <span>{item.enabled ? '已启用' : '已停用'}</span>
            </div>
          ))}
          {!subscriptionsQuery.data?.length ? <p className="empty-state">还没有事件订阅。</p> : null}
        </div>
      </div>
    </section>
  )
}
