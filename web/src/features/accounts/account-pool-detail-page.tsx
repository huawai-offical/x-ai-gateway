import { type FormEvent, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type AccountPool = { id: number; poolName: string; providerType: string }
type Account = { id: number; accountName: string; healthy: boolean; frozen: boolean }

export function AccountPoolDetailPage() {
  const { id } = useParams()
  const [distributedKeyId, setDistributedKeyId] = useState('')
  const poolQuery = useQuery({
    queryKey: ['account-pool', id],
    queryFn: () => apiRequest<AccountPool>(`/admin/account-pools/${id}`),
  })
  const accountsQuery = useQuery({
    queryKey: ['accounts', id],
    queryFn: () => apiRequest<Account[]>(`/admin/accounts/pool/${id}`),
  })
  const bindMutation = useMutation({
    mutationFn: () =>
      apiRequest(`/admin/account-pools/${id}/bindings`, {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyId: Number(distributedKeyId),
          providerType: poolQuery.data?.providerType === 'OPENAI_OAUTH'
            ? 'OPENAI_DIRECT'
            : poolQuery.data?.providerType === 'GEMINI_OAUTH'
              ? 'GEMINI_DIRECT'
              : 'ANTHROPIC_DIRECT',
        }),
      }),
    onSuccess: () => setDistributedKeyId(''),
  })

  const handleBind = (event: FormEvent) => {
    event.preventDefault()
    if (!distributedKeyId.trim()) return
    bindMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Pool detail</p>
          <h2>{poolQuery.data?.poolName ?? '账号池'}</h2>
        </div>
        {poolQuery.data && (
          <div className="inline-actions">
            <Link className="action-link" to={`/accounts/connect/${poolQuery.data.providerType.toLowerCase()}?poolId=${poolQuery.data.id}`}>
              发起 OAuth 连接
            </Link>
          </div>
        )}
        <form className="inline-form" onSubmit={handleBind}>
          <input value={distributedKeyId} onChange={(e) => setDistributedKeyId(e.target.value)} placeholder="绑定 DistributedKey ID" />
          <button type="submit" disabled={bindMutation.isPending || !poolQuery.data}>绑定到 key</button>
        </form>
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {accountsQuery.data?.map((account: Account) => (
            <Link key={account.id} className="detail-card" to={`/accounts/${account.id}`}>
              <strong>{account.accountName}</strong>
              <span>{account.healthy ? 'healthy' : 'unhealthy'}</span>
              <span>{account.frozen ? 'frozen' : 'active'}</span>
            </Link>
          ))}
          {!accountsQuery.data?.length && <p className="empty-state">当前账号池还没有账号。</p>}
        </div>
      </div>
    </section>
  )
}
