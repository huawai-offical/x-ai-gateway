import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type CapabilityRow = {
  siteProfileId: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  healthState: string
  blockedReason?: string | null
  supportsResponses: boolean
  supportsEmbeddings: boolean
  supportsAudio: boolean
  supportsImages: boolean
  supportsModeration: boolean
  supportsFiles: boolean
  supportsUploads: boolean
  supportsBatches: boolean
  supportsTuning: boolean
  supportsRealtime: boolean
}

export function CapabilityMatrixPage() {
  const query = useQuery({
    queryKey: ['capability-matrix'],
    queryFn: () => apiRequest<CapabilityRow[]>('/admin/capability-matrix'),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Capability matrix</p>
          <h2>站点能力矩阵</h2>
        </div>
        <div className="card-list">
          {query.data?.map((item: CapabilityRow) => (
            <div key={item.siteProfileId} className="detail-card">
              <strong>{item.displayName}</strong>
              <span>{item.providerFamily} / {item.siteKind}</span>
              <span>{item.healthState}</span>
              <span>responses: {String(item.supportsResponses)}</span>
              <span>embeddings: {String(item.supportsEmbeddings)}</span>
              <span>audio: {String(item.supportsAudio)}</span>
              <span>images: {String(item.supportsImages)}</span>
              <span>moderation: {String(item.supportsModeration)}</span>
              <span>files: {String(item.supportsFiles)}</span>
              <span>uploads: {String(item.supportsUploads)}</span>
              <span>batches: {String(item.supportsBatches)}</span>
              <span>tuning: {String(item.supportsTuning)}</span>
              <span>realtime: {String(item.supportsRealtime)}</span>
              {item.blockedReason ? <span>{item.blockedReason}</span> : null}
            </div>
          ))}
          {!query.data?.length ? <p className="empty-state">暂无能力矩阵数据。</p> : null}
        </div>
      </div>
    </section>
  )
}
