import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type Account = {
  id: number
  accountName: string
  providerType: string
  externalAccountId: string
  healthy: boolean
  frozen: boolean
  proxyId?: number | null
  tlsFingerprintProfileId?: number | null
}

export function AccountDetailPage() {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const accountQuery = useQuery({
    queryKey: ['account', id],
    queryFn: () => apiRequest<Account>(`/admin/accounts/${id}`),
  })
  const [proxyId, setProxyId] = useState('')
  const [tlsFingerprintProfileId, setTlsFingerprintProfileId] = useState('')

  const refreshMutation = useMutation({
    mutationFn: () => apiRequest(`/admin/accounts/${id}/refresh`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['account', id] }),
  })
  const freezeMutation = useMutation({
    mutationFn: (frozen: boolean) => apiRequest(`/admin/accounts/${id}/freeze?frozen=${frozen}`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['account', id] }),
  })
  const networkMutation = useMutation({
    mutationFn: () =>
      apiRequest(`/admin/accounts/${id}/network`, {
        method: 'POST',
        body: JSON.stringify({
          proxyId: proxyId.trim() ? Number(proxyId) : null,
          tlsFingerprintProfileId: tlsFingerprintProfileId.trim() ? Number(tlsFingerprintProfileId) : null,
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['account', id] }),
  })
  const exportMutation = useMutation({
    mutationFn: () => apiRequest<{ config: string }>(`/admin/accounts/${id}/export?clientFamily=GENERIC_OPENAI`),
  })

  const handleNetworkSave = (event: FormEvent) => {
    event.preventDefault()
    networkMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Account</p>
          <h2>{accountQuery.data?.accountName ?? '账号详情'}</h2>
        </div>
        {accountQuery.data && (
          <div className="detail-grid">
            <span>provider: {accountQuery.data.providerType}</span>
            <span>external: {accountQuery.data.externalAccountId}</span>
            <span>healthy: {String(accountQuery.data.healthy)}</span>
            <span>frozen: {String(accountQuery.data.frozen)}</span>
            <span>proxyId: {accountQuery.data.proxyId ?? '-'}</span>
            <span>tlsProfileId: {accountQuery.data.tlsFingerprintProfileId ?? '-'}</span>
          </div>
        )}
        <div className="inline-actions">
          <button onClick={() => refreshMutation.mutate()} type="button">刷新</button>
          <button onClick={() => freezeMutation.mutate(!(accountQuery.data?.frozen ?? false))} type="button">
            {accountQuery.data?.frozen ? '解冻' : '冻结'}
          </button>
          <button onClick={() => exportMutation.mutate()} type="button">导出配置</button>
        </div>
        {exportMutation.data && (
          <div className="code-block">
            <pre>{exportMutation.data.config}</pre>
          </div>
        )}
        <form className="inline-form" onSubmit={handleNetworkSave}>
          <input value={proxyId} onChange={(e) => setProxyId(e.target.value)} placeholder="proxy id" />
          <input value={tlsFingerprintProfileId} onChange={(e) => setTlsFingerprintProfileId(e.target.value)} placeholder="tls profile id" />
          <button type="submit">保存网络绑定</button>
        </form>
      </div>
    </section>
  )
}
