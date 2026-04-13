import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedQuery } from '../../lib/typed-react-query'
import {
  type CapabilityResolution,
  featureLabel,
  formatInstant,
  matchesResolutionFilter,
  resolutionTone,
  type CapabilityMatrixRow,
} from './types'

export function CapabilityMatrixPage() {
  const [compatibilitySurface, setCompatibilitySurface] = useState('all')
  const [resolutionFilter, setResolutionFilter] = useState('all')
  const query = useTypedQuery<CapabilityMatrixRow[]>({
    queryKey: ['capability-matrix'],
    queryFn: () => apiRequest<CapabilityMatrixRow[]>('/admin/capability-matrix'),
  })

  const rows = query.data ?? []
  const filteredRows = rows.filter((item: CapabilityMatrixRow) => {
    if (compatibilitySurface !== 'all' && item.compatibilitySurface !== compatibilitySurface) return false
    if (!matchesResolutionFilter(item.features, resolutionFilter)) return false
    return true
  })

  const availableSurfaces = Array.from(new Set(rows.map((item: CapabilityMatrixRow) => item.compatibilitySurface))).sort()

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Capability matrix</p>
          <h2>站点能力矩阵</h2>
          <p className="empty-state">矩阵页以 feature resolution 为主表达，并直接深链到站点详情。</p>
        </div>
        <div className="inline-form">
          <label className="stacked-form">
            <span>兼容面</span>
            <select value={compatibilitySurface} onChange={(event) => setCompatibilitySurface(event.target.value)}>
              <option value="all">全部</option>
              {availableSurfaces.map((value: string) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <label className="stacked-form">
            <span>resolution</span>
            <select value={resolutionFilter} onChange={(event) => setResolutionFilter(event.target.value)}>
              <option value="all">全部</option>
              <option value="blocked">仅 blocked</option>
              <option value="lossy">仅 lossy</option>
            </select>
          </label>
        </div>
        <div className="card-list">
          {filteredRows.map((item: CapabilityMatrixRow) => (
            <div key={item.siteProfileId} className="detail-card">
              <div className="inline-actions">
                <Link className="action-link" to={`/provider-sites/${item.siteProfileId}`}>查看站点</Link>
              </div>
              <strong>{item.displayName}</strong>
              <span>{item.providerFamily} / {item.siteKind}</span>
              <span>health: {item.healthState}</span>
              <span>surface: {item.compatibilitySurface}</span>
              <span>fallback: {item.fallbackStrategy ?? '无'}</span>
              <span>cooldown: {item.cooldownCredentialCount} / {formatInstant(item.cooldownUntil)}</span>
              {item.blockedReason ? <span>{item.blockedReason}</span> : null}
              <div className="feature-list">
                {(Object.entries(item.features) as Array<[string, CapabilityResolution]>).map(([feature, resolution]) => (
                  <Link
                    key={feature}
                    className={`feature-badge ${resolutionTone(resolution)}`}
                    to={`/provider-sites/${item.siteProfileId}?feature=${feature}`}
                  >
                    {featureLabel(feature)}
                    <small>{resolution.declaredLevel ?? '-'}/{resolution.implementedLevel ?? '-'}/{resolution.effectiveLevel ?? '-'}</small>
                  </Link>
                ))}
              </div>
            </div>
          ))}
          {!filteredRows.length ? <p className="empty-state">暂无能力矩阵数据。</p> : null}
        </div>
      </div>
    </section>
  )
}
