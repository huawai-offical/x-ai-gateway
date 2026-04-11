import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type AccountPool = {
  id: number
  poolName: string
  providerType: string
  active: boolean
}

export function AccountPoolsPage() {
  const queryClient = useQueryClient()
  const [poolName, setPoolName] = useState('')
  const [providerType, setProviderType] = useState('OPENAI_OAUTH')

  const poolsQuery = useQuery({
    queryKey: ['account-pools'],
    queryFn: () => apiRequest<AccountPool[]>('/admin/account-pools'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/account-pools', {
        method: 'POST',
        body: JSON.stringify({
          poolName,
          providerType,
          supportedModels: [],
          supportedProtocols: ['openai'],
          allowedClientFamilies: [],
        }),
      }),
    onSuccess: () => {
      setPoolName('')
      queryClient.invalidateQueries({ queryKey: ['account-pools'] })
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!poolName.trim()) return
    createMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Account pools</p>
          <h2>账号池管理</h2>
        </div>
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={poolName} onChange={(e) => setPoolName(e.target.value)} placeholder="账号池名称" />
          <select value={providerType} onChange={(e) => setProviderType(e.target.value)}>
            <option value="OPENAI_OAUTH">OPENAI_OAUTH</option>
            <option value="GEMINI_OAUTH">GEMINI_OAUTH</option>
            <option value="CLAUDE_ACCOUNT">CLAUDE_ACCOUNT</option>
          </select>
          <button type="submit">创建</button>
        </form>
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {poolsQuery.data?.map((pool: AccountPool) => (
            <Link key={pool.id} className="detail-card" to={`/account-pools/${pool.id}`}>
              <strong>{pool.poolName}</strong>
              <span>{pool.providerType}</span>
              <span>{pool.active ? 'active' : 'inactive'}</span>
            </Link>
          ))}
        </div>
      </div>
    </section>
  )
}
