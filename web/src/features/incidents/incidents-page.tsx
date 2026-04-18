import { Link, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedQuery } from '../../lib/typed-react-query'
import {
  formatInstant,
  type IncidentEntityResponse,
  type IncidentSummaryResponse,
  type IncidentTimelineEventResponse,
  type OpsAlertEvent,
} from '../provider-sites/types'
import type { OutboundDelivery } from '../integrations/types'

export function IncidentsPage() {
  const [searchParams] = useSearchParams()
  const entityType = searchParams.get('entityType')
  const entityRef = searchParams.get('entityRef')

  const query = useTypedQuery<IncidentSummaryResponse>({
    queryKey: ['incident-summary', entityType, entityRef],
    queryFn: () => apiRequest<IncidentSummaryResponse>('/admin/incidents/summary'),
  })

  const summary = query.data
  const incidents = filterIncidents(summary?.incidents ?? [], entityType, entityRef)
  const affectedEntities = filterEntities(summary?.affectedEntities ?? [], entityType, entityRef)
  const timeline = filterTimeline(summary?.timeline ?? [], entityType, entityRef)
  const snapshot = summary?.opsSummary.snapshot
  const outboundDeliveriesQuery = useTypedQuery<OutboundDelivery[]>({
    queryKey: ['incident-outbound-deliveries', entityType, entityRef],
    queryFn: () => {
      const params = new URLSearchParams()
      if (entityType) params.set('entityType', entityType)
      if (entityRef) params.set('entityRef', entityRef)
      const query = params.toString()
      return apiRequest<OutboundDelivery[]>(`/admin/integrations/deliveries${query ? `?${query}` : ''}`)
    },
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Incident command center</p>
          <h2>当前事件指挥台</h2>
          <p className="empty-state">先回答发生了什么、影响谁、为什么危险以及下一步该做什么。</p>
        </div>
        {entityType || entityRef ? (
          <div className="stack-bar">
            {entityType ? <span>entityType · {entityType}</span> : null}
            {entityRef ? <span>entityRef · {entityRef}</span> : null}
            <Link className="action-link" to="/incidents">清除聚焦</Link>
          </div>
        ) : null}
        {snapshot ? (
          <div className="detail-grid">
            <div className="detail-card">
              <strong>Active incidents</strong>
              <span>{incidents.length}</span>
            </div>
            <div className="detail-card">
              <strong>Risk level</strong>
              <span>{summary?.sloSummary.summary.riskLevel ?? '-'}</span>
            </div>
            <div className="detail-card">
              <strong>Affected entities</strong>
              <span>{affectedEntities.length}</span>
            </div>
            <div className="detail-card">
              <strong>Quarantines</strong>
              <span>{summary?.quarantines.length ?? 0}</span>
            </div>
            <div className="detail-card">
              <strong>Burn rate</strong>
              <span>{summary?.sloSummary.summary.burnRate?.toFixed(2) ?? '-'}</span>
            </div>
            <div className="detail-card">
              <strong>Provider failures</strong>
              <span>{snapshot.providerFailures}</span>
            </div>
          </div>
        ) : (
          <p className="empty-state">正在加载 incident 摘要…</p>
        )}
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Current incidents</p>
          <h3>当前风险事件</h3>
        </div>
        <div className="card-list">
          {incidents.map((incident) => (
            <div key={incident.id} className="detail-card">
              <strong>{incident.title}</strong>
              <span>{incident.severity} · {incident.status}</span>
              <span>{incident.message}</span>
              <span>{incident.entityType ?? 'SYSTEM'} / {incident.entityRef ?? 'global'}</span>
              <div className="inline-actions">
                <Link
                  className="action-link"
                  to={`/traces?requestId=${encodeURIComponent(incident.entityRef ?? '')}`}
                >
                  查看 Trace
                </Link>
                <Link
                  className="action-link"
                  to={`/workbench?requestId=${encodeURIComponent(incident.entityRef ?? '')}`}
                >
                  进入 Workbench
                </Link>
                <Link
                  className="action-link"
                  to={`/incidents?entityType=${encodeURIComponent(incident.entityType ?? 'SYSTEM')}&entityRef=${encodeURIComponent(incident.entityRef ?? incident.title)}`}
                >
                  聚焦实体
                </Link>
              </div>
            </div>
          ))}
          {!incidents.length ? <p className="empty-state">当前筛选下没有打开中的 incident。</p> : null}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Affected entities</p>
          <h3>受影响对象</h3>
        </div>
        <div className="card-list">
          {affectedEntities.map((entity) => (
            <div key={`${entity.entityType}-${entity.entityRef}-${entity.title}`} className="detail-card">
              <strong>{entity.title}</strong>
              <span>{entity.entityType} / {entity.entityRef}</span>
              <span>{entity.severity} · {entity.status}</span>
              <span>{entity.summary}</span>
              <div className="inline-actions">
                <Link className="action-link" to={`/incidents?entityType=${encodeURIComponent(entity.entityType)}&entityRef=${encodeURIComponent(entity.entityRef)}`}>
                  聚焦事件
                </Link>
                <Link className="action-link" to={`/traces?gatewayResourceKey=${encodeURIComponent(entity.entityRef)}`}>
                  查看 Trace
                </Link>
              </div>
            </div>
          ))}
          {!affectedEntities.length ? <p className="empty-state">暂无受影响对象。</p> : null}
        </div>
      </div>

      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Outbound delivery</p>
          <h3>外发状态摘要</h3>
        </div>
        <div className="card-list">
          {(outboundDeliveriesQuery.data ?? []).slice(0, 4).map((delivery) => (
            <div key={delivery.id} className="detail-card">
              <strong>{delivery.eventType}</strong>
              <span>{delivery.deliveryStatus} / attempt {delivery.attemptCount}</span>
              <span>{delivery.responseSummary ?? delivery.lastError ?? '等待投递结果'}</span>
              <span>{delivery.entityType ?? 'SYSTEM'} / {delivery.entityRef ?? '-'}</span>
              <div className="inline-actions">
                {delivery.requestId ? (
                  <Link className="action-link" to={`/traces?requestId=${encodeURIComponent(delivery.requestId)}`}>
                    查看 Trace
                  </Link>
                ) : null}
                <Link className="action-link" to="/integrations/deliveries">
                  查看全部投递
                </Link>
              </div>
            </div>
          ))}
          {!outboundDeliveriesQuery.data?.length ? <p className="empty-state">当前没有相关外发投递记录。</p> : null}
        </div>
      </div>

      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Recommended actions</p>
          <h3>建议动作</h3>
        </div>
        <div className="card-list">
          {summary?.recommendedActions.map((action) => (
            <div key={action} className="detail-card">
              <strong>Next action</strong>
              <span>{action}</span>
            </div>
          ))}
          {!summary?.recommendedActions.length ? <p className="empty-state">当前没有额外建议动作。</p> : null}
        </div>
      </div>

      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Evidence</p>
          <h3>风险证据</h3>
        </div>
        {summary ? (
          <div className="detail-grid">
            <div className="detail-card">
              <strong>Error budget remaining</strong>
              <span>{(summary.sloSummary.summary.errorBudgetRemainingRatio * 100).toFixed(1)}%</span>
            </div>
            <div className="detail-card">
              <strong>Silences</strong>
              <span>{summary.silences.length}</span>
            </div>
            <div className="detail-card">
              <strong>Site health tracked</strong>
              <span>{summary.healthScores.sites.length}</span>
            </div>
            <div className="detail-card">
              <strong>Capacity pressure keys</strong>
              <span>{summary.capacitySummary.distributedKeys.length}</span>
            </div>
          </div>
        ) : (
          <p className="empty-state">暂无风险证据。</p>
        )}
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Incident timeline</p>
          <h3>事件时间线</h3>
        </div>
        <div className="card-list">
          {timeline.map((event) => (
            <div key={`${event.eventType}-${event.title}-${event.occurredAt}`} className="detail-card">
              <strong>{event.title}</strong>
              <span>{event.severity} · {event.source}</span>
              <span>{event.description}</span>
              <span>{event.entityType ?? 'SYSTEM'} / {event.entityRef ?? 'global'}</span>
              <span>{formatInstant(event.occurredAt)}</span>
            </div>
          ))}
          {!timeline.length ? <p className="empty-state">当前没有可展示的 incident timeline。</p> : null}
        </div>
      </div>
    </section>
  )
}

function filterIncidents(items: OpsAlertEvent[], entityType?: string | null, entityRef?: string | null) {
  return items.filter((item) => matchesEntity(item.entityType, item.entityRef, entityType, entityRef))
}

function filterEntities(items: IncidentEntityResponse[], entityType?: string | null, entityRef?: string | null) {
  return items.filter((item) => matchesEntity(item.entityType, item.entityRef, entityType, entityRef))
}

function filterTimeline(items: IncidentTimelineEventResponse[], entityType?: string | null, entityRef?: string | null) {
  return items.filter((item) => matchesEntity(item.entityType, item.entityRef, entityType, entityRef))
}

function matchesEntity(
  candidateType?: string | null,
  candidateRef?: string | null,
  entityType?: string | null,
  entityRef?: string | null,
) {
  if (entityType && candidateType !== entityType) return false
  if (entityRef && candidateRef !== entityRef) return false
  return true
}
