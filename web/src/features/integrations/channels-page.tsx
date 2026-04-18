import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { apiRequest } from '../../lib/api'
import type { NotificationChannel, WebhookEndpoint } from './types'

export function ChannelsPage() {
  const queryClient = useQueryClient()
  const [channelName, setChannelName] = useState('')
  const [channelType, setChannelType] = useState('WEBHOOK')
  const [webhookEndpointId, setWebhookEndpointId] = useState('')
  const [emailTo, setEmailTo] = useState('')

  const channelsQuery = useQuery({
    queryKey: ['integrations', 'channels'],
    queryFn: () => apiRequest<NotificationChannel[]>('/admin/integrations/channels'),
  })
  const webhooksQuery = useQuery({
    queryKey: ['integrations', 'webhooks'],
    queryFn: () => apiRequest<WebhookEndpoint[]>('/admin/integrations/webhooks'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<NotificationChannel>('/admin/integrations/channels', {
        method: 'POST',
        body: JSON.stringify({
          channelName,
          channelType,
          webhookEndpointId: channelType === 'EMAIL' ? null : Number(webhookEndpointId),
          emailTo: channelType === 'EMAIL' ? emailTo : null,
          templateMode: 'DEFAULT',
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'channels'] })
      setChannelName('')
      setWebhookEndpointId('')
      setEmailTo('')
    },
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Channels</p>
          <h2>通知通道</h2>
        </div>
        <div className="inline-form">
          <input value={channelName} onChange={(event) => setChannelName(event.target.value)} placeholder="channel name" />
          <select value={channelType} onChange={(event) => setChannelType(event.target.value)} aria-label="channel type">
            <option value="WEBHOOK">WEBHOOK</option>
            <option value="IM_WEBHOOK">IM_WEBHOOK</option>
            <option value="EMAIL">EMAIL</option>
          </select>
          {channelType === 'EMAIL' ? (
            <input value={emailTo} onChange={(event) => setEmailTo(event.target.value)} placeholder="ops@example.com" />
          ) : (
            <select value={webhookEndpointId} onChange={(event) => setWebhookEndpointId(event.target.value)} aria-label="webhook endpoint">
              <option value="">选择 endpoint</option>
              {webhooksQuery.data?.map((item: WebhookEndpoint) => (
                <option key={item.id} value={item.id}>{item.endpointName}</option>
              ))}
            </select>
          )}
          <button
            type="button"
            onClick={() => createMutation.mutate()}
            disabled={!channelName || (channelType === 'EMAIL' ? !emailTo : !webhookEndpointId)}
          >
            创建 channel
          </button>
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Configured channels</p>
          <h3>已配置通道</h3>
        </div>
        <div className="card-list">
          {channelsQuery.data?.map((item: NotificationChannel) => (
            <div key={item.id} className="detail-card">
              <strong>{item.channelName}</strong>
              <span>{item.channelType}</span>
              <span>{item.channelType === 'EMAIL' ? item.emailTo : `endpoint #${item.webhookEndpointId ?? '-'}`}</span>
              <span>{item.enabled ? '已启用' : '已停用'}</span>
            </div>
          ))}
          {!channelsQuery.data?.length ? <p className="empty-state">还没有通知通道。</p> : null}
        </div>
      </div>
    </section>
  )
}
