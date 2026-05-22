import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { getPortalSession, listPortalSubscriptions } from './api'
import { formatNumber } from './portal-format'
import { PortalFrame } from './portal-shell'
import type { PortalSubscription } from './types'

export function PortalSubscriptionsPage() {
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const subscriptionsQuery = useQuery({
    queryKey: ['portal', 'subscriptions'],
    queryFn: listPortalSubscriptions,
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

  const subscriptions = (subscriptionsQuery.data ?? []) as PortalSubscription[]

  return (
    <PortalFrame>
      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <p className="text-sm font-medium text-primary">订阅</p>
          <CardTitle className="text-3xl">我的订阅</CardTitle>
        </CardHeader>
        <CardContent>
          {subscriptionsQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : subscriptionsQuery.error ? (
            <InlineError error={subscriptionsQuery.error} title="订阅加载失败" />
          ) : subscriptions.length ? (
            <PaginatedRows items={subscriptions}>
              {({ pageItems }) => (
                <div className="overflow-hidden rounded-2xl border border-border/60">
                  <table className="w-full table-fixed text-sm">
                <thead className="bg-muted/40">
                  <tr>
                    <th className="w-[24%] px-4 py-3 text-left font-medium text-muted-foreground">套餐</th>
                    <th className="w-[14%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                    <th className="w-[24%] px-4 py-3 text-left font-medium text-muted-foreground">速率</th>
                    <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">日额度</th>
                    <th className="w-[20%] px-4 py-3 text-left font-medium text-muted-foreground">有效期</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item) => (
                    <tr key={item.id} className="border-t border-border/50">
                      <td className="truncate px-4 py-3 font-medium text-foreground">{item.planName}</td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={item.status === 'ACTIVE' ? 'success' : 'warning'}>{item.status}</StatusBadge>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        RPM {formatNumber(item.rpmLimit)} / TPM {formatNumber(item.tpmLimit)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.dailyTokenLimit)}</td>
                      <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.expiresAt) || '长期'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
              )}
            </PaginatedRows>
          ) : (
            <EmptyState title="暂无订阅" />
          )}
        </CardContent>
      </Card>
    </PortalFrame>
  )
}
