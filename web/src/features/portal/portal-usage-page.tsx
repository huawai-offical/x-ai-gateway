import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { getPortalSession, getPortalUsageSummary } from './api'
import { formatNumber } from './portal-format'
import { Metric, PortalFrame } from './portal-shell'
import type { PortalUsageItem } from './types'

export function PortalUsagePage() {
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const usageQuery = useQuery({
    queryKey: ['portal', 'usage-summary'],
    queryFn: getPortalUsageSummary,
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

  const usage = usageQuery.data
  const recentUsage = (usage?.recentUsage ?? []) as PortalUsageItem[]

  return (
    <PortalFrame>
      <div className="grid gap-4 md:grid-cols-4">
        <Metric title="请求数" value={formatNumber(usage?.requestCount)} />
        <Metric title="总 Token" value={formatNumber(usage?.totalTokens)} />
        <Metric title="输入 Token" value={formatNumber(usage?.promptTokens)} />
        <Metric title="缓存命中 Token" value={formatNumber(usage?.cacheHitTokens)} />
      </div>

      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div>
            <p className="text-sm font-medium text-primary">用量</p>
            <CardTitle className="text-3xl">用量明细</CardTitle>
          </div>
          <Button type="button" variant="outline" onClick={() => exportUsageCsv(recentUsage)} disabled={!recentUsage.length}>
            导出 CSV
          </Button>
        </CardHeader>
        <CardContent>
          {usageQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : usageQuery.error ? (
            <InlineError error={usageQuery.error} title="用量加载失败" />
          ) : recentUsage.length ? (
            <PaginatedRows items={recentUsage}>
              {({ pageItems }) => (
                <div className="overflow-hidden rounded-2xl border border-border/60">
                  <table className="w-full table-fixed text-sm">
                <thead className="bg-muted/40">
                  <tr>
                    <th className="w-[22%] px-4 py-3 text-left font-medium text-muted-foreground">请求</th>
                    <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">协议</th>
                    <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">模型</th>
                    <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">Token</th>
                    <th className="w-[12%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                    <th className="w-[14%] px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item: PortalUsageItem) => (
                    <tr key={item.requestId} className="border-t border-border/50 align-top">
                      <td className="break-all px-4 py-3 font-mono text-xs text-foreground">{item.requestId}</td>
                      <td className="px-4 py-3 text-muted-foreground">{item.protocol}</td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-foreground">{item.modelGroup}</div>
                        <div className="text-xs text-muted-foreground">{item.providerType}</div>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatNumber(item.totalTokens)}
                        <div className="text-xs">in {formatNumber(item.promptTokens)} / out {formatNumber(item.completionTokens)}</div>
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={item.completeness === 'COMPLETE' ? 'success' : 'warning'}>{item.completeness}</StatusBadge>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.createdAt) || '暂无'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
              )}
            </PaginatedRows>
          ) : (
            <EmptyState title="暂无用量记录" />
          )}
        </CardContent>
      </Card>
    </PortalFrame>
  )
}

function exportUsageCsv(items: PortalUsageItem[]) {
  const rows = [
    ['requestId', 'protocol', 'modelGroup', 'providerType', 'promptTokens', 'completionTokens', 'totalTokens', 'completeness', 'createdAt'],
    ...items.map((item) => [
      item.requestId,
      item.protocol,
      item.modelGroup,
      item.providerType,
      String(item.promptTokens),
      String(item.completionTokens),
      String(item.totalTokens),
      item.completeness,
      item.createdAt ?? '',
    ]),
  ]
  const csv = rows.map((row) => row.map(escapeCsv).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'portal-usage.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

function escapeCsv(value: string) {
  return `"${value.replaceAll('"', '""')}"`
}
