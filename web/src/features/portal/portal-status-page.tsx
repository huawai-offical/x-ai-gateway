import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { formatInstant } from '@/lib/format'
import { getPortalSession, listPortalChannelStatuses } from './api'
import { Metric, PortalFrame } from './portal-shell'
import type { PortalChannelStatus } from './types'

export function PortalStatusPage() {
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const statusQuery = useQuery({
    queryKey: ['portal', 'channels-status'],
    queryFn: listPortalChannelStatuses,
    enabled: Boolean(sessionQuery.data?.authenticated),
  })

  if (sessionQuery.isPending) {
    return <PortalFrame><PageSkeleton count={2} /></PortalFrame>
  }
  if (sessionQuery.error) {
    return <PortalFrame><InlineError error={sessionQuery.error} title="门户会话加载失败" /></PortalFrame>
  }
  if (!sessionQuery.data?.authenticated) {
    return <Navigate to="/portal/login" replace />
  }

  const channels = (statusQuery.data ?? []) as PortalChannelStatus[]
  const activeCount = channels.filter((item) => item.active).length
  const blockedCount = channels.filter((item) => item.blockedReason).length

  return (
    <PortalFrame>
      <div className="grid gap-4 md:grid-cols-3">
        <Metric title="可用通道" value={activeCount} />
        <Metric title="异常通道" value={blockedCount} />
        <Metric title="总通道" value={channels.length} />
      </div>

      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <p className="text-sm font-medium text-primary">状态</p>
          <CardTitle className="text-3xl">服务状态</CardTitle>
        </CardHeader>
        <CardContent>
          {statusQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : statusQuery.error ? (
            <InlineError error={statusQuery.error} title="服务状态加载失败" />
          ) : channels.length ? (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {channels.map((item) => (
                <div key={item.siteProfileId} className="rounded-2xl border border-border/60 bg-card/80 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="truncate font-medium text-foreground">{item.displayName}</div>
                      <div className="mt-1 text-xs text-muted-foreground">{item.profileCode} / {item.siteKind}</div>
                    </div>
                    <StatusBadge tone={statusTone(item)}>{item.active ? item.healthState : 'DISABLED'}</StatusBadge>
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {item.supportedProtocols.map((protocol) => (
                      <span key={protocol} className="rounded-full bg-primary/10 px-2 py-1 text-xs font-medium text-primary">{protocol}</span>
                    ))}
                  </div>
                  {item.blockedReason ? (
                    <div className="mt-4 rounded-xl border border-amber-500/20 bg-amber-500/10 px-3 py-2 text-sm text-amber-700 dark:text-amber-300">{item.blockedReason}</div>
                  ) : null}
                  <div className="mt-4 text-xs text-muted-foreground">刷新：{formatInstant(item.refreshedAt) || '暂无'}</div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState title="暂无可展示的服务状态" />
          )}
        </CardContent>
      </Card>
    </PortalFrame>
  )
}

function statusTone(item: PortalChannelStatus): 'success' | 'warning' | 'danger' {
  if (!item.active || item.blockedReason) return 'danger'
  if (item.healthState === 'HEALTHY' || item.healthState === 'ACTIVE') return 'success'
  return 'warning'
}
