import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type ProxyItem = { id: number; proxyName: string; proxyUrl: string; lastStatus?: string | null }

export function ProxiesPage() {
  const queryClient = useQueryClient()
  const [proxyName, setProxyName] = useState('')
  const [proxyUrl, setProxyUrl] = useState('')

  const proxiesQuery = useQuery({
    queryKey: ['network-proxies'],
    queryFn: () => apiRequest<ProxyItem[]>('/admin/network/proxies'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/network/proxies', {
        method: 'POST',
        body: JSON.stringify({ proxyName, proxyUrl }),
      }),
    onSuccess: () => {
      setProxyName('')
      setProxyUrl('')
      queryClient.invalidateQueries({ queryKey: ['network-proxies'] })
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!proxyName.trim() || !proxyUrl.trim()) return
    createMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Network proxy</p>
          <h2>代理池</h2>
        </div>
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={proxyName} onChange={(e) => setProxyName(e.target.value)} placeholder="代理名称" />
          <input value={proxyUrl} onChange={(e) => setProxyUrl(e.target.value)} placeholder="https://proxy.example.com:443" />
          <button type="submit">创建</button>
        </form>
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {proxiesQuery.data?.map((proxy: ProxyItem) => (
            <Link key={proxy.id} className="detail-card" to={`/network/proxies/${proxy.id}`}>
              <strong>{proxy.proxyName}</strong>
              <span>{proxy.proxyUrl}</span>
              <span>{proxy.lastStatus ?? 'unknown'}</span>
            </Link>
          ))}
        </div>
      </div>
    </section>
  )
}
