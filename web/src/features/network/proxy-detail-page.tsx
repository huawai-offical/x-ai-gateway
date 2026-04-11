import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type ProxyItem = { id: number; proxyName: string; proxyUrl: string; lastStatus?: string | null; lastLatencyMs?: number | null }
type ProbeResult = { id: number; status: string; latencyMs?: number | null; targetHost: string }

export function ProxyDetailPage() {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const proxiesQuery = useQuery({
    queryKey: ['network-proxies'],
    queryFn: () => apiRequest<ProxyItem[]>('/admin/network/proxies'),
  })

  const current = proxiesQuery.data?.find((item: ProxyItem) => String(item.id) === id)

  const probeMutation = useMutation({
    mutationFn: () => apiRequest(`/admin/network/proxies/${id}/probe`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['network-proxies'] })
      queryClient.invalidateQueries({ queryKey: ['network-probe-results', id] })
    },
  })

  const probeResultsQuery = useQuery({
    queryKey: ['network-probe-results', id],
    queryFn: () => apiRequest<ProbeResult[]>(`/admin/network/probes/${id}`),
    enabled: Boolean(id),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Proxy detail</p>
          <h2>{current?.proxyName ?? '代理详情'}</h2>
        </div>
        <button onClick={() => probeMutation.mutate()} disabled={probeMutation.isPending}>
          手动 Probe
        </button>
        {current && <p className="empty-state">{current.proxyUrl}</p>}
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {probeResultsQuery.data?.map((item: ProbeResult) => (
            <div key={item.id} className="detail-card">
              <strong>{item.status}</strong>
              <span>{item.latencyMs ?? '-'} ms</span>
              <span>{item.targetHost}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
