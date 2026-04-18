import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedQuery } from '../../lib/typed-react-query'
import {
  formatInstant,
  isAcceptedExceptionReason,
  type CapabilityMatrixRow,
  type SurfaceCapability,
} from './types'

type MatrixView = 'blocked' | 'degraded' | 'accepted'

type MatrixItem = {
  siteProfileId: number
  displayName: string
  providerFamily: string
  siteKind: string
  compatibilitySurface: string
  blockedReason?: string | null
  operation: string
  surfaceKey: string
  normalizedPath?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  summary: string
}

export function CapabilityMatrixPage() {
  const [view, setView] = useState<MatrixView>('blocked')
  const [compatibilitySurface, setCompatibilitySurface] = useState('all')

  const query = useTypedQuery<CapabilityMatrixRow[]>({
    queryKey: ['capability-matrix'],
    queryFn: () => apiRequest<CapabilityMatrixRow[]>('/admin/capability-matrix'),
  })

  const rows = query.data ?? []
  const availableSurfaces = Array.from(new Set(rows.map((item) => item.compatibilitySurface))).sort()

  const items = rows
    .flatMap((row) => toMatrixItems(row, view))
    .filter((item) => compatibilitySurface === 'all' || item.compatibilitySurface === compatibilitySurface)

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Capability matrix</p>
          <h2>全局限制发现器</h2>
          <p className="empty-state">默认按 blocked、degraded、accepted exception 三类问题组织，而不是先铺系统内部字段。</p>
        </div>
        <div className="inline-form">
          <label className="stacked-form">
            <span>兼容面</span>
            <select value={compatibilitySurface} onChange={(event) => setCompatibilitySurface(event.target.value)}>
              <option value="all">全部</option>
              {availableSurfaces.map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="inline-actions">
          {(['blocked', 'degraded', 'accepted'] as MatrixView[]).map((item) => (
            <button
              key={item}
              type="button"
              className={`secondary-button${view === item ? ' active' : ''}`}
              onClick={() => setView(item)}
            >
              {viewLabel(item)}
            </button>
          ))}
        </div>
        <div className="card-list">
          {items.map((item) => (
            <div key={`${item.siteProfileId}-${item.surfaceKey}-${item.operation}`} className="detail-card">
              <strong>{item.displayName}</strong>
              <span>{item.providerFamily} / {item.siteKind}</span>
              <span>{item.operation} · {item.supportStatus ?? '-'}</span>
              <span>{item.normalizedPath ?? '无 normalizedPath'}</span>
              <span>{item.summary}</span>
              <div className="inline-actions">
                <Link className="action-link" to={`/provider-sites/${item.siteProfileId}?surface=${item.surfaceKey}`}>查看站点</Link>
                <Link className="action-link" to={`/workbench?protocol=openai&requestPath=${encodeURIComponent(item.normalizedPath ?? '/v1/chat/completions')}`}>进入 Workbench</Link>
                <Link className="action-link" to={`/traces?providerType=${encodeURIComponent(item.siteKind)}&requestPath=${encodeURIComponent(item.normalizedPath ?? '/v1/chat/completions')}`}>查看 Trace</Link>
                <Link className="action-link" to={`/incidents?entityType=SITE_PROFILE&entityRef=${encodeURIComponent(String(item.siteProfileId))}`}>查看 Incident</Link>
              </div>
              <span>compatibilitySurface: {item.compatibilitySurface}</span>
              {item.blockedReason ? <span>site blocker: {item.blockedReason}</span> : null}
            </div>
          ))}
          {!items.length ? <p className="empty-state">当前筛选下没有对应问题项。</p> : null}
        </div>
      </div>

      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Matrix notes</p>
          <h3>阅读方式</h3>
        </div>
        <div className="card-list">
          <div className="detail-card">
            <strong>Blocked</strong>
            <span>先看完全不可执行的 surface，优先处理 blocker 或 accepted exception。</span>
          </div>
          <div className="detail-card">
            <strong>Degraded</strong>
            <span>聚焦存在 orchestration、lossy 或 render 差异的 surface。</span>
          </div>
          <div className="detail-card">
            <strong>Accepted exceptions</strong>
            <span>这些是已冻结的范围边界，要给出原因、影响和建议动作。</span>
          </div>
          <div className="detail-card">
            <strong>Last refresh</strong>
            <span>{rows[0] ? formatInstant(rows[0].cooldownUntil) : '无'}</span>
          </div>
        </div>
      </div>
    </section>
  )
}

function toMatrixItems(row: CapabilityMatrixRow, view: MatrixView): MatrixItem[] {
  return Object.entries(row.surfaces)
    .filter(([, surface]) => matchesView(surface, view))
    .map(([surfaceKey, surface]) => ({
      siteProfileId: row.siteProfileId,
      displayName: row.displayName,
      providerFamily: row.providerFamily,
      siteKind: row.siteKind,
      compatibilitySurface: row.compatibilitySurface,
      blockedReason: row.blockedReason,
      operation: surface.operation,
      surfaceKey,
      normalizedPath: surface.normalizedPath,
      supportStatus: surface.supportStatus,
      degradationLevel: surface.degradationLevel,
      summary: summarizeSurface(surface, view),
    }))
}

function matchesView(surface: SurfaceCapability, view: MatrixView) {
  if (view === 'blocked') {
    return surface.supportStatus === 'BLOCKED'
  }
  if (view === 'degraded') {
    return surface.supportStatus === 'DEGRADED' || surface.supportStatus === 'ORCHESTRATION'
  }
  return surface.blockerReasons.some((reason) => isAcceptedExceptionReason(reason))
}

function summarizeSurface(surface: SurfaceCapability, view: MatrixView) {
  if (view === 'accepted') {
    return surface.blockerReasons.find((reason) => isAcceptedExceptionReason(reason)) ?? 'accepted exception'
  }
  if (view === 'blocked') {
    return surface.blockerReasons[0] ?? '当前 surface 已被阻断。'
  }
  return surface.lossReasons[0] ?? surface.blockerReasons[0] ?? '当前 surface 存在降级或 orchestration 差异。'
}

function viewLabel(view: MatrixView) {
  switch (view) {
    case 'blocked':
      return 'Blocked'
    case 'degraded':
      return 'Degraded'
    case 'accepted':
      return 'Accepted exceptions'
  }
}
