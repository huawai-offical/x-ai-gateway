import { type FormEvent, useState } from 'react'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { getPortalRedeemStatus, getPortalSession, listPortalBalanceLedger, redeemPortalCode } from './api'
import { formatNumber } from './portal-format'
import { Metric, PortalFrame } from './portal-shell'
import type { PortalBalanceLedger } from './types'

export function PortalRedeemPage() {
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const [statusQuery, ledgerQuery] = useQueries({
    queries: [
      {
        queryKey: ['portal', 'redeem-status'],
        queryFn: getPortalRedeemStatus,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
      {
        queryKey: ['portal', 'balance-ledger'],
        queryFn: listPortalBalanceLedger,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
    ],
  })
  const redeemMutation = useMutation({
    mutationFn: redeemPortalCode,
    onSuccess: () => {
      setCode('')
      queryClient.invalidateQueries({ queryKey: ['portal', 'redeem-status'] })
      queryClient.invalidateQueries({ queryKey: ['portal', 'balance-ledger'] })
    },
  })

  const handleRedeem = (event: FormEvent) => {
    event.preventDefault()
    const value = code.trim()
    if (value) {
      redeemMutation.mutate(value)
    }
  }

  if (sessionQuery.isPending) {
    return <PortalFrame><PageSkeleton count={2} /></PortalFrame>
  }
  if (sessionQuery.error) {
    return <PortalFrame><InlineError error={sessionQuery.error} title="门户会话加载失败" /></PortalFrame>
  }
  if (!sessionQuery.data?.authenticated) {
    return <Navigate to="/portal/login" replace />
  }

  const ledger = (ledgerQuery.data ?? []) as PortalBalanceLedger[]

  return (
    <PortalFrame>
      <div className="grid gap-4 md:grid-cols-3">
        <Metric title="Token 余额" value={formatNumber(statusQuery.data?.currentTokenCredits)} />
        <Metric title="流水笔数" value={ledger.length} />
        <Metric title="兑换状态" value={statusQuery.data?.available ? '可用' : '关闭'} />
      </div>

      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <p className="text-sm font-medium text-primary">兑换</p>
          <CardTitle className="text-3xl">兑换与余额</CardTitle>
          <p className="text-sm text-muted-foreground">{statusQuery.data?.message ?? '兑换入口加载中。'}</p>
        </CardHeader>
        <CardContent className="space-y-4">
          <form className="grid gap-3 md:grid-cols-[1fr_120px]" onSubmit={handleRedeem}>
            <Input value={code} onChange={(event) => setCode(event.target.value)} placeholder="粘贴兑换码" />
            <Button type="submit" disabled={redeemMutation.isPending}>兑换</Button>
          </form>
          {redeemMutation.error ? <InlineError error={redeemMutation.error} title="兑换失败" /> : null}
          {redeemMutation.data ? (
            <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">
              {redeemMutation.data.message} 本次增加 {formatNumber(redeemMutation.data.deltaTokenCredits)} Token，余额 {formatNumber(redeemMutation.data.balanceAfterTokenCredits)}。
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <CardTitle>余额流水</CardTitle>
        </CardHeader>
        <CardContent>
          {ledgerQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : ledgerQuery.error ? (
            <InlineError error={ledgerQuery.error} title="余额流水加载失败" />
          ) : ledger.length ? (
            <PaginatedRows items={ledger}>
              {({ pageItems }) => (
                <div className="overflow-hidden rounded-2xl border border-border/60">
                  <table className="w-full table-fixed text-sm">
                <thead className="bg-muted/40">
                  <tr>
                    <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">变动</th>
                    <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">余额</th>
                    <th className="w-[28%] px-4 py-3 text-left font-medium text-muted-foreground">来源</th>
                    <th className="w-[36%] px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item) => (
                    <tr key={item.id} className="border-t border-border/50">
                      <td className={item.deltaTokenCredits >= 0 ? 'px-4 py-3 font-medium text-emerald-700 dark:text-emerald-300' : 'px-4 py-3 font-medium text-destructive'}>
                        {item.deltaTokenCredits >= 0 ? '+' : ''}{formatNumber(item.deltaTokenCredits)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.balanceAfterTokenCredits)}</td>
                      <td className="truncate px-4 py-3 text-muted-foreground">{item.referenceId ?? item.reason}</td>
                      <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
              )}
            </PaginatedRows>
          ) : (
            <EmptyState title="暂无余额流水" />
          )}
        </CardContent>
      </Card>
    </PortalFrame>
  )
}
