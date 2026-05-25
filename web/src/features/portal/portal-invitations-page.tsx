import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { getPortalInvitationSummary, getPortalSession } from './api'
import { formatNumber } from './portal-format'
import { Metric, PortalFrame } from './portal-shell'
import type { PortalInvitationLeaderboardEntry, PortalInvitationUser } from './types'

export function PortalInvitationsPage() {
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const summaryQuery = useQuery({
    queryKey: ['portal', 'invitations', 'summary'],
    queryFn: getPortalInvitationSummary,
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

  const summary = summaryQuery.data
  const directInvites = (summary?.directInvites ?? []) as PortalInvitationUser[]
  const leaderboard = (summary?.leaderboard ?? []) as PortalInvitationLeaderboardEntry[]

  return (
    <PortalFrame>
      <div className="grid gap-4 md:grid-cols-4">
        <Metric title="直接邀请" value={formatNumber(summary?.directInviteCount)} />
        <Metric title="累计邀请" value={formatNumber(summary?.totalInviteCount)} />
        <Metric title="返佣 Token" value={formatNumber(summary?.referrerRewardTokenCredits)} />
        <Metric title="最近邀请" value={formatInstant(summary?.latestInviteAt) || '暂无'} />
      </div>

      {summaryQuery.isPending ? (
        <PageSkeleton count={2} />
      ) : summaryQuery.error ? (
        <InlineError error={summaryQuery.error} title="邀请统计加载失败" />
      ) : (
        <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
          <Card className="border-border bg-card/95 shadow-lg">
            <CardHeader>
              <p className="text-sm font-medium text-primary">我的邀请</p>
              <CardTitle>直接邀请用户</CardTitle>
            </CardHeader>
            <CardContent>
              {directInvites.length ? (
                <PaginatedRows items={directInvites}>
                  {({ pageItems }) => (
                    <div className="overflow-x-auto rounded-lg border border-border bg-card">
                      <table className="w-full min-w-[520px] table-fixed text-sm">
                        <thead className="bg-muted/40">
                          <tr>
                            <th className="w-[52%] px-4 py-3 text-left font-medium text-muted-foreground">用户</th>
                            <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">用户 ID</th>
                            <th className="w-[30%] px-4 py-3 text-left font-medium text-muted-foreground">邀请时间</th>
                          </tr>
                        </thead>
                        <tbody>
                          {pageItems.map((item) => (
                            <tr key={item.userId} className="border-t border-border/50 align-top">
                              <td className="px-4 py-3">
                                <div className="font-medium text-foreground">{item.displayName || item.email}</div>
                                <div className="truncate text-xs text-muted-foreground">{item.email}</div>
                              </td>
                              <td className="px-4 py-3 text-muted-foreground">#{item.userId}</td>
                              <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.invitedAt) || '未知'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </PaginatedRows>
              ) : (
                <EmptyState title="还没有直接邀请用户" />
              )}
            </CardContent>
          </Card>

          <Card className="border-border bg-card/95 shadow-lg">
            <CardHeader>
              <p className="text-sm font-medium text-primary">增长排行</p>
              <CardTitle>邀请排行榜</CardTitle>
            </CardHeader>
            <CardContent>
              {leaderboard.length ? (
                <div className="overflow-x-auto rounded-lg border border-border bg-card">
                  <table className="w-full min-w-[520px] table-fixed text-sm">
                    <thead className="bg-muted/40">
                      <tr>
                        <th className="w-[34%] px-4 py-3 text-left font-medium text-muted-foreground">用户</th>
                        <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">直接</th>
                        <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">累计</th>
                        <th className="w-[30%] px-4 py-3 text-left font-medium text-muted-foreground">返佣</th>
                      </tr>
                    </thead>
                    <tbody>
                      {leaderboard.map((item) => (
                        <tr key={item.userId} className="border-t border-border/50 align-top">
                          <td className="px-4 py-3">
                            <div className="font-medium text-foreground">{item.displayName || `#${item.userId}`}</div>
                            <div className="text-xs text-muted-foreground">{formatInstant(item.latestInviteAt) || '暂无邀请'}</div>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.directInviteCount)}</td>
                          <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.totalInviteCount)}</td>
                          <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.referrerRewardTokenCredits)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <EmptyState title="还没有排行榜数据" />
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </PortalFrame>
  )
}
