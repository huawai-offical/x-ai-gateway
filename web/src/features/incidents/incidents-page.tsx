import { Link, useSearchParams } from 'react-router-dom'
import { ArrowUpRightIcon, RadarIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { formatInstant } from '@/lib/format'
import { apiClient } from '@/lib/api'
import { useTypedQuery } from '@/lib/typed-react-query'
import type { OutboundDelivery } from '../integrations/types'
import {
  type IncidentEntityResponse,
  type IncidentSummaryResponse,
  type IncidentTimelineEventResponse,
  type OpsAlertEvent,
} from './types'

export function IncidentsPage() {
  const [searchParams] = useSearchParams()
  const entityType = searchParams.get('entityType')
  const entityRef = searchParams.get('entityRef')

  const query = useTypedQuery<IncidentSummaryResponse>({
    queryKey: ['incident-summary', entityType, entityRef],
    queryFn: () => apiClient.get<IncidentSummaryResponse>('/admin/incidents/summary'),
  })

  const outboundDeliveriesQuery = useTypedQuery<OutboundDelivery[]>({
    queryKey: ['incident-outbound-deliveries', entityType, entityRef],
    queryFn: () =>
      apiClient.get<OutboundDelivery[]>('/admin/integrations/deliveries', {
        params: {
          entityType,
          entityRef,
        },
      }),
  })

  const summary = query.data
  const incidents = filterIncidents(summary?.incidents ?? [], entityType, entityRef)
  const affectedEntities = filterEntities(summary?.affectedEntities ?? [], entityType, entityRef)
  const timeline = filterTimeline(summary?.timeline ?? [], entityType, entityRef)

  const sectionActions = (
    <div className="flex flex-wrap gap-2">
      {entityType ? <StatusBadge tone="warning">{entityType}</StatusBadge> : null}
      {entityRef ? <StatusBadge>{entityRef}</StatusBadge> : null}
      {entityType || entityRef ? (
        <Button asChild variant="outline" size="sm">
          <Link to="/console/incidents">清除聚焦</Link>
        </Button>
      ) : null}
      <Button asChild variant="outline" size="sm">
        <Link to="/console/ops">返回智能运维总览</Link>
      </Button>
    </div>
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="智能运维总览补充视图"
        title="事件处置视图"
        actions={sectionActions}
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.4fr)_minmax(20rem,1fr)]">
        <PageSection
          kicker="当前事件"
          title="当前风险事件"
        >
          {query.isPending ? (
            <PageSkeleton count={1} />
          ) : incidents.length ? (
            <div className="grid gap-4 lg:grid-cols-2">
              {incidents.map((incident) => (
                <EventCard
                  key={incident.id}
                  title={incident.title}
                  meta={[
                    incident.message,
                    `${incident.severity} · ${incident.status}`,
                    `${incident.entityType ?? 'SYSTEM'} / ${incident.entityRef ?? '全局'}`,
                  ]}
                  footer={
                    <div className="flex flex-wrap gap-2">
                      <ActionButton
                        to={`/console/traces?requestId=${encodeURIComponent(incident.entityRef ?? '')}`}
                        label="查看链路追踪"
                      />
                      <ActionButton
                        to={`/console/workbench?requestId=${encodeURIComponent(incident.entityRef ?? '')}`}
                        label="进入调试工作台"
                      />
                      <ActionButton
                        to={`/console/incidents?entityType=${encodeURIComponent(
                          incident.entityType ?? 'SYSTEM',
                        )}&entityRef=${encodeURIComponent(incident.entityRef ?? incident.title)}`}
                        label="聚焦实体"
                      />
                    </div>
                  }
                />
              ))}
            </div>
          ) : (
            <EmptyState title="当前筛选下没有打开中的事件" />
          )}
        </PageSection>

        <PageSection
          kicker="建议动作"
          title="建议动作"
        >
          {summary?.recommendedActions.length ? (
            <div className="flex flex-col gap-3">
              {summary.recommendedActions.map((action) => (
                <Card key={action} className="border-border/60 bg-card/92 shadow-sm">
                  <CardContent className="flex items-start gap-3 p-4 text-sm text-foreground">
                    <StatusBadge tone="info">下一步</StatusBadge>
                    <span>{normalizeUserVisibleTerms(action)}</span>
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : (
            <EmptyState title="当前没有额外建议动作" />
          )}
        </PageSection>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <PageSection
          kicker="受影响对象"
          title="受影响对象"
        >
          {affectedEntities.length ? (
            <div className="grid gap-4 lg:grid-cols-2">
              {affectedEntities.map((entity) => (
                <EventCard
                  key={`${entity.entityType}-${entity.entityRef}-${entity.title}`}
                  title={entity.title}
                  meta={[entity.summary, `${entity.entityType} / ${entity.entityRef}`, `${entity.severity} · ${entity.status}`]}
                  footer={
                    <div className="flex flex-wrap gap-2">
                      <ActionButton
                        to={`/console/incidents?entityType=${encodeURIComponent(entity.entityType)}&entityRef=${encodeURIComponent(entity.entityRef)}`}
                        label="聚焦事件"
                      />
                      <ActionButton
                        to={`/console/traces?gatewayResourceKey=${encodeURIComponent(entity.entityRef)}`}
                        label="查看链路追踪"
                      />
                    </div>
                  }
                />
              ))}
            </div>
          ) : (
            <EmptyState title="暂无受影响对象" />
          )}
        </PageSection>

        <PageSection
          kicker="外发投递"
          title="外发状态摘要"
        >
          {outboundDeliveriesQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : outboundDeliveriesQuery.error ? (
            <InlineError error={outboundDeliveriesQuery.error} title="外发投递加载失败" />
          ) : outboundDeliveriesQuery.data?.length ? (
            <div className="flex flex-col gap-4">
              {outboundDeliveriesQuery.data.slice(0, 4).map((delivery) => (
                <EventCard
                  key={delivery.id}
                  title={delivery.eventType}
                  meta={[
                    delivery.responseSummary ?? delivery.lastError ?? '等待投递结果',
                    `${delivery.deliveryStatus} / 尝试 ${delivery.attemptCount}`,
                    `${delivery.entityType ?? 'SYSTEM'} / ${delivery.entityRef ?? '-'}`,
                  ]}
                  footer={
                    <div className="flex flex-wrap gap-2">
                      {delivery.requestId ? (
                        <ActionButton
                          to={`/console/traces?requestId=${encodeURIComponent(delivery.requestId)}`}
                          label="查看链路追踪"
                        />
                      ) : null}
                      <ActionButton to="/console/integrations/deliveries" label="查看全部投递" />
                    </div>
                  }
                />
              ))}
            </div>
          ) : (
            <EmptyState title="当前没有相关外发投递记录" />
          )}
        </PageSection>
      </div>

      <div className="grid gap-6">
        <PageSection
          kicker="事件时间线"
          title="事件时间线"
        >
          {timeline.length ? (
            <div className="flex flex-col gap-4">
              {timeline.map((event) => (
                <Card key={`${event.eventType}-${event.title}-${event.occurredAt}`} className="border-border/60 bg-card/92 shadow-sm">
                  <CardHeader className="gap-2 border-b border-border/60">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="flex min-w-0 flex-col gap-2">
                        <CardTitle className="text-base">{event.title}</CardTitle>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <StatusBadge tone={toneForSeverity(event.severity)}>{event.severity}</StatusBadge>
                        <StatusBadge>{event.source}</StatusBadge>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                    <div>{event.entityType ?? '系统'} / {event.entityRef ?? '全局'}</div>
                    <div>{formatInstant(event.occurredAt)}</div>
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : (
            <EmptyState
              title="当前没有可展示的事件时间线"
              icon={<RadarIcon className="size-5" />}
            />
          )}
        </PageSection>
      </div>
    </div>
  )
}

function EventCard({
  title,
  meta,
  footer,
}: {
  title: string
  meta: string[]
  footer?: React.ReactNode
}) {
  return (
    <Card className="border-border/60 bg-card/92 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 p-5">
        <div className="flex flex-col gap-2 text-sm text-muted-foreground">
          {meta.map((item) => (
            <div key={item}>{normalizeUserVisibleTerms(item)}</div>
          ))}
        </div>
        {footer}
      </CardContent>
    </Card>
  )
}

function ActionButton({ to, label }: { to: string; label: string }) {
  return (
    <Button asChild variant="outline" size="sm">
      <Link to={to}>
        {label}
        <ArrowUpRightIcon data-icon="inline-end" />
      </Link>
    </Button>
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

function toneForSeverity(severity?: string | null) {
  const normalized = severity?.toLowerCase()
  if (normalized === 'critical' || normalized === 'error') return 'danger' as const
  if (normalized === 'warn' || normalized === 'warning') return 'warning' as const
  return 'info' as const
}

function normalizeUserVisibleTerms(value: string) {
  return value.replace(/\bTrace\b/g, '链路追踪')
}
