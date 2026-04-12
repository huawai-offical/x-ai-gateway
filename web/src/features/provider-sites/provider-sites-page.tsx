import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type ProviderSite = {
  id: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  authStrategy: string
  pathStrategy: string
  healthState: string
  blockedReason?: string | null
  supportedProtocols: string[]
  modelCount: number
}

export function ProviderSitesPage() {
  const query = useQuery({
    queryKey: ['provider-sites'],
    queryFn: () => apiRequest<ProviderSite[]>('/admin/provider-sites'),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Provider sites</p>
          <h2>站点档案与能力快照</h2>
        </div>
        <div className="card-list">
          {query.data?.map((item: ProviderSite) => (
            <Link key={item.id} to={`/provider-sites/${item.id}`} className="detail-card">
              <strong>{item.displayName}</strong>
              <span>{item.providerFamily} / {item.siteKind}</span>
              <span>{item.authStrategy} / {item.pathStrategy}</span>
              <span>{item.healthState}</span>
              <span>protocols: {item.supportedProtocols.join(', ') || '-'}</span>
              <span>models: {item.modelCount}</span>
              {item.blockedReason ? <span>{item.blockedReason}</span> : null}
            </Link>
          ))}
          {!query.data?.length ? <p className="empty-state">暂无站点档案。</p> : null}
        </div>
      </div>
    </section>
  )
}
