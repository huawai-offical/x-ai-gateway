import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type DistributedKey = {
  id: number
  keyName: string
  keyPrefix: string
  active: boolean
  allowedProtocols: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  budgetLimitMicros?: number | null
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
}

export function KeysPage() {
  const queryClient = useQueryClient()
  const [keyName, setKeyName] = useState('')

  const keysQuery = useQuery({
    queryKey: ['distributed-keys'],
    queryFn: () => apiRequest<DistributedKey[]>('/admin/distributed-keys'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/distributed-keys', {
        method: 'POST',
        body: JSON.stringify({
          keyName,
          allowedProtocols: ['openai', 'responses'],
          allowedModels: [],
          allowedProviderTypes: [],
        }),
      }),
    onSuccess: () => {
      setKeyName('')
      queryClient.invalidateQueries({ queryKey: ['distributed-keys'] })
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!keyName.trim()) return
    createMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">DistributedKey</p>
          <h2>策略对象列表</h2>
        </div>
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={keyName} onChange={(e) => setKeyName(e.target.value)} placeholder="新 key 名称" />
          <button type="submit" disabled={createMutation.isPending}>
            创建
          </button>
        </form>
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {keysQuery.data?.map((item: DistributedKey) => (
            <Link key={item.id} className="detail-card" to={`/keys/${item.id}`}>
              <strong>{item.keyName}</strong>
              <span>{item.keyPrefix}</span>
              <span>{item.active ? 'active' : 'inactive'}</span>
              <span>providers: {item.allowedProviderTypes.length || 'all'}</span>
            </Link>
          ))}
          {!keysQuery.data?.length && <p className="empty-state">还没有 DistributedKey。</p>}
        </div>
      </div>
    </section>
  )
}
