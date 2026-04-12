import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type ProviderSite = {
  id: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  authStrategy: string
  pathStrategy: string
  modelAddressingStrategy: string
  errorSchemaStrategy: string
  healthState: string
  blockedReason?: string | null
  supportedProtocols: string[]
  modelCount: number
}

type SiteCapability = {
  id: number
  modelName: string
  modelKey: string
  supportedProtocols: string[]
  supportsChat: boolean
  supportsEmbeddings: boolean
  capabilityLevel: string
}

export function ProviderSiteDetailPage() {
  const params = useParams()
  const id = Number(params.id)
  const queryClient = useQueryClient()

  const detailQuery = useQuery({
    queryKey: ['provider-site', id],
    queryFn: () => apiRequest<ProviderSite>(`/admin/provider-sites/${id}`),
    enabled: Number.isFinite(id),
  })

  const capabilitiesQuery = useQuery({
    queryKey: ['provider-site-capabilities', id],
    queryFn: () => apiRequest<SiteCapability[]>(`/admin/provider-sites/${id}/capabilities`),
    enabled: Number.isFinite(id),
  })

  const refreshMutation = useMutation({
    mutationFn: () =>
      apiRequest<ProviderSite>(`/admin/provider-sites/${id}/refresh-capabilities`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['provider-site', id] })
      queryClient.invalidateQueries({ queryKey: ['provider-site-capabilities', id] })
      queryClient.invalidateQueries({ queryKey: ['provider-sites'] })
      queryClient.invalidateQueries({ queryKey: ['capability-matrix'] })
    },
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Site detail</p>
          <h2>{detailQuery.data?.displayName ?? '站点档案'}</h2>
        </div>
        <button type="button" onClick={() => refreshMutation.mutate()}>刷新能力快照</button>
        <div className="card-list">
          <div className="detail-card">
            <strong>{detailQuery.data?.profileCode ?? '-'}</strong>
            <span>{detailQuery.data?.providerFamily ?? '-'}</span>
            <span>{detailQuery.data?.siteKind ?? '-'}</span>
            <span>{detailQuery.data?.authStrategy ?? '-'}</span>
            <span>{detailQuery.data?.pathStrategy ?? '-'}</span>
            <span>{detailQuery.data?.modelAddressingStrategy ?? '-'}</span>
            <span>{detailQuery.data?.errorSchemaStrategy ?? '-'}</span>
            <span>{detailQuery.data?.healthState ?? '-'}</span>
            {detailQuery.data?.blockedReason ? <span>{detailQuery.data.blockedReason}</span> : null}
          </div>
        </div>
      </div>
      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Model capabilities</p>
          <h3>模型能力</h3>
        </div>
        <div className="card-list">
          {capabilitiesQuery.data?.map((item: SiteCapability) => (
            <div key={item.id} className="detail-card">
              <strong>{item.modelName}</strong>
              <span>{item.modelKey}</span>
              <span>{item.capabilityLevel}</span>
              <span>chat: {String(item.supportsChat)}</span>
              <span>embeddings: {String(item.supportsEmbeddings)}</span>
              <span>{item.supportedProtocols.join(', ')}</span>
            </div>
          ))}
          {!capabilitiesQuery.data?.length ? <p className="empty-state">暂无模型能力记录。</p> : null}
        </div>
      </div>
    </section>
  )
}
