import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { createPortalPaymentOrder, getPortalSession, listPortalPaymentOrders } from './api'
import { formatNumber } from './portal-format'
import { Metric, PortalFrame } from './portal-shell'
import type { PortalPaymentOrder } from './types'

export function PortalOrdersPage() {
  const queryClient = useQueryClient()
  const [amountMinor, setAmountMinor] = useState('1990')
  const [tokenCredits, setTokenCredits] = useState('500')
  const [provider, setProvider] = useState('stripe')
  const [latestCheckoutUrl, setLatestCheckoutUrl] = useState<string | null>(null)
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const ordersQuery = useQuery({
    queryKey: ['portal', 'payment-orders'],
    queryFn: listPortalPaymentOrders,
    enabled: Boolean(sessionQuery.data?.authenticated),
  })
  const createMutation = useMutation({
    mutationFn: createPortalPaymentOrder,
    onSuccess: (order: PortalPaymentOrder) => {
      setLatestCheckoutUrl(order.checkoutUrl ?? null)
      queryClient.invalidateQueries({ queryKey: ['portal', 'payment-orders'] })
    },
  })

  const handleCreate = (event: FormEvent) => {
    event.preventDefault()
    createMutation.mutate({
      provider,
      amountMinor: Number(amountMinor),
      currency: 'CNY',
      tokenCredits: Number(tokenCredits),
      metadataJson: JSON.stringify({
        providerInstanceCode: `${provider}-default`,
        successUrl: `${window.location.origin}/portal/orders`,
        cancelUrl: `${window.location.origin}/portal/orders`,
      }),
    })
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

  const orders = (ordersQuery.data ?? []) as PortalPaymentOrder[]
  const paidCount = orders.filter((item) => item.status === 'PAID').length
  const refundedMinor = orders.reduce((sum, item) => sum + item.refundAmountMinor, 0)

  return (
    <PortalFrame>
      <div className="grid gap-4 md:grid-cols-3">
        <Metric title="订单数" value={orders.length} />
        <Metric title="已支付" value={paidCount} />
        <Metric title="已退款" value={`${(refundedMinor / 100).toFixed(2)} CNY`} />
      </div>

      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <p className="text-sm font-medium text-primary">订单</p>
          <CardTitle className="text-3xl">充值订单</CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <form className="grid gap-3 rounded-2xl border border-border/60 p-4 md:grid-cols-[1fr_1fr_1fr_auto]" onSubmit={handleCreate}>
            <label className="grid gap-1 text-sm">
              <span className="font-medium text-foreground">渠道</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring"
                value={provider}
                onChange={(event) => setProvider(event.target.value)}
              >
                <option value="stripe">Stripe</option>
                <option value="easypay">EasyPay</option>
                <option value="alipay">支付宝</option>
                <option value="wechat">微信支付</option>
              </select>
            </label>
            <label className="grid gap-1 text-sm">
              <span className="font-medium text-foreground">金额分</span>
              <Input inputMode="numeric" value={amountMinor} onChange={(event) => setAmountMinor(event.target.value)} />
            </label>
            <label className="grid gap-1 text-sm">
              <span className="font-medium text-foreground">Token 数量</span>
              <Input inputMode="numeric" value={tokenCredits} onChange={(event) => setTokenCredits(event.target.value)} />
            </label>
            <Button className="self-end" type="submit" disabled={createMutation.isPending}>创建订单</Button>
          </form>
          {createMutation.error ? <InlineError error={createMutation.error} title="充值订单创建失败" /> : null}
          {latestCheckoutUrl ? (
            <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-700 dark:text-emerald-300">
              <div className="font-medium">支付链接已生成</div>
              <a className="mt-2 block break-all font-mono text-xs text-emerald-700 underline dark:text-emerald-300" href={latestCheckoutUrl} target="_blank" rel="noreferrer">
                {latestCheckoutUrl}
              </a>
            </div>
          ) : null}

          {ordersQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : ordersQuery.error ? (
            <InlineError error={ordersQuery.error} title="充值订单加载失败" />
          ) : orders.length ? (
            <PaginatedRows items={orders}>
              {({ pageItems }) => (
                <div className="overflow-hidden rounded-2xl border border-border/60">
                  <table className="w-full table-fixed text-sm">
                <thead className="bg-muted/40">
                  <tr>
                    <th className="w-[20%] px-4 py-3 text-left font-medium text-muted-foreground">订单</th>
                    <th className="w-[12%] px-4 py-3 text-left font-medium text-muted-foreground">渠道</th>
                    <th className="w-[14%] px-4 py-3 text-left font-medium text-muted-foreground">金额</th>
                    <th className="w-[12%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                    <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">退款/对账</th>
                    <th className="w-[24%] px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item) => (
                    <tr key={item.id} className="border-t border-border/50 align-top">
                      <td className="break-all px-4 py-3">
                        <div className="font-mono text-xs text-foreground">{item.orderNo}</div>
                        {item.checkoutUrl ? <a className="mt-1 block text-xs text-primary underline" href={item.checkoutUrl} target="_blank" rel="noreferrer">打开支付链接</a> : null}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{item.provider}</td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {(item.amountMinor / 100).toFixed(2)} {item.currency}
                        <div className="text-xs">{formatNumber(item.tokenCredits)} Token</div>
                      </td>
                      <td className="px-4 py-3"><StatusBadge tone={orderTone(item.status)}>{item.status}</StatusBadge></td>
                      <td className="px-4 py-3 text-muted-foreground">
                        退款 {(item.refundAmountMinor / 100).toFixed(2)}
                        <div className="text-xs">{item.reconcileStatus ?? '未对账'}</div>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        创建 {formatInstant(item.createdAt) || '暂无'}
                        <div className="text-xs">支付 {formatInstant(item.paidAt) || '暂无'}</div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
              )}
            </PaginatedRows>
          ) : (
            <EmptyState title="暂无充值订单" />
          )}
        </CardContent>
      </Card>
    </PortalFrame>
  )
}

function orderTone(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'PAID') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'danger'
  if (status === 'REFUNDED' || status === 'PARTIAL_REFUNDED') return 'info'
  return 'warning'
}
