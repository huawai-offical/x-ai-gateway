import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { apiRequest } from '../../lib/api'
import type { WebhookEndpoint } from './types'

export function WebhooksPage() {
  const queryClient = useQueryClient()
  const [endpointName, setEndpointName] = useState('')
  const [endpointUrl, setEndpointUrl] = useState('')
  const [secret, setSecret] = useState('')

  const webhooksQuery = useQuery({
    queryKey: ['integrations', 'webhooks'],
    queryFn: () => apiRequest<WebhookEndpoint[]>('/admin/integrations/webhooks'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<WebhookEndpoint>('/admin/integrations/webhooks', {
        method: 'POST',
        body: JSON.stringify({
          endpointName,
          endpointUrl,
          secret,
          signingMode: 'HMAC_SHA256',
          timeoutMs: 5000,
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'webhooks'] })
      setEndpointName('')
      setEndpointUrl('')
      setSecret('')
    },
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Webhook endpoints</p>
          <h2>统一 webhook 出口</h2>
        </div>
        <p className="panel-copy">先配置 endpoint、签名和 timeout，后续 Channels 与 Subscriptions 都围绕这里复用。</p>
        <div className="inline-form">
          <input value={endpointName} onChange={(event) => setEndpointName(event.target.value)} placeholder="endpoint name" />
          <input value={endpointUrl} onChange={(event) => setEndpointUrl(event.target.value)} placeholder="https://example.com/hook" />
          <input value={secret} onChange={(event) => setSecret(event.target.value)} placeholder="secret (optional)" />
          <button type="button" onClick={() => createMutation.mutate()} disabled={!endpointName || !endpointUrl}>
            创建 endpoint
          </button>
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Configured endpoints</p>
          <h3>已配置 endpoint</h3>
        </div>
        <div className="card-list">
          {webhooksQuery.data?.map((item: WebhookEndpoint) => (
            <div key={item.id} className="detail-card">
              <strong>{item.endpointName}</strong>
              <span>{item.endpointUrl}</span>
              <span>{item.signingMode} / timeout {item.timeoutMs}ms</span>
              <span>{item.enabled ? '已启用' : '已停用'}</span>
              <span>secret 指纹：{item.secretFingerprint ?? '未配置'}</span>
            </div>
          ))}
          {!webhooksQuery.data?.length ? <p className="empty-state">还没有 webhook endpoint。</p> : null}
        </div>
      </div>
    </section>
  )
}
