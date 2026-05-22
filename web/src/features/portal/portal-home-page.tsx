import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ArrowUpRightIcon, TerminalSquareIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import {
  createPortalPaymentOrder,
  getPortalRedeemStatus,
  getPortalSession,
  listPortalBalanceLedger,
  listPortalAnnouncements,
  listPortalKeys,
  listPortalPaymentOrders,
  listPortalSubscriptions,
  logoutPortal,
  markPortalAnnouncementRead,
  redeemPortalCode,
} from './api'
import { formatNumber } from './portal-format'
import { Metric, PortalFrame } from './portal-shell'
import type { PortalAnnouncement, PortalBalanceLedger, PortalKey, PortalPaymentOrder, PortalRedeemStatus, PortalSubscription } from './types'

export function PortalHomePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [redeemCode, setRedeemCode] = useState('')
  const [paymentProvider, setPaymentProvider] = useState('stripe')
  const [paymentAmountMinor, setPaymentAmountMinor] = useState('1990')
  const [paymentTokenCredits, setPaymentTokenCredits] = useState('500')
  const [latestCheckoutUrl, setLatestCheckoutUrl] = useState<string | null>(null)
  const sessionQuery = useQuery({
    queryKey: ['portal', 'session'],
    queryFn: getPortalSession,
  })
  const [subscriptionsQuery, keysQuery, announcementsQuery, redeemQuery, ledgerQuery, paymentOrdersQuery] = useQueries({
    queries: [
      {
        queryKey: ['portal', 'subscriptions'],
        queryFn: listPortalSubscriptions,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
      {
        queryKey: ['portal', 'keys'],
        queryFn: listPortalKeys,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
      {
        queryKey: ['portal', 'announcements'],
        queryFn: listPortalAnnouncements,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
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
      {
        queryKey: ['portal', 'payment-orders'],
        queryFn: listPortalPaymentOrders,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
    ],
  })
  const markReadMutation = useMutation({
    mutationFn: markPortalAnnouncementRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['portal', 'announcements'] }),
  })
  const redeemMutation = useMutation({
    mutationFn: redeemPortalCode,
    onSuccess: () => {
      setRedeemCode('')
      queryClient.invalidateQueries({ queryKey: ['portal', 'redeem-status'] })
      queryClient.invalidateQueries({ queryKey: ['portal', 'balance-ledger'] })
    },
  })
  const createPaymentMutation = useMutation({
    mutationFn: createPortalPaymentOrder,
    onSuccess: (order: PortalPaymentOrder) => {
      setLatestCheckoutUrl(order.checkoutUrl ?? null)
      queryClient.invalidateQueries({ queryKey: ['portal', 'payment-orders'] })
    },
  })
  const logoutMutation = useMutation({
    mutationFn: logoutPortal,
    onSettled: () => {
      queryClient.clear()
      navigate('/portal/login', { replace: true })
    },
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
  const keys = (keysQuery.data ?? []) as PortalKey[]
  const announcements = (announcementsQuery.data ?? []) as PortalAnnouncement[]
  const redeemStatus = redeemQuery.data as PortalRedeemStatus | undefined
  const ledger = (ledgerQuery.data ?? []) as PortalBalanceLedger[]
  const paymentOrders = (paymentOrdersQuery.data ?? []) as PortalPaymentOrder[]
  const codexAccess = buildCodexAccess(keys, subscriptions, redeemStatus, ledger)
  const portalApiBase = getPortalApiBase()

  const handleRedeem = (event: FormEvent) => {
    event.preventDefault()
    const code = redeemCode.trim()
    if (!code) {
      return
    }
    redeemMutation.mutate(code)
  }

  const handleCreatePayment = (event: FormEvent) => {
    event.preventDefault()
    const amountMinor = Number(paymentAmountMinor)
    const tokenCredits = Number(paymentTokenCredits)
    if (!Number.isFinite(amountMinor) || amountMinor <= 0 || !Number.isFinite(tokenCredits) || tokenCredits <= 0) {
      return
    }
    createPaymentMutation.mutate({
      provider: paymentProvider,
      amountMinor,
      currency: 'CNY',
      tokenCredits,
      metadataJson: JSON.stringify({
        providerInstanceCode: `${paymentProvider}-default`,
        successUrl: `${window.location.origin}/portal`,
        cancelUrl: `${window.location.origin}/portal`,
      }),
    })
  }

  return (
    <PortalFrame>
      <header className="flex flex-col justify-between gap-4 rounded-lg border border-border bg-card/95 p-5 shadow-lg backdrop-blur md:flex-row md:items-center md:p-6">
        <div>
          <p className="text-sm font-medium text-primary">用户门户</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-foreground">
            {sessionQuery.data.displayName || sessionQuery.data.email}
          </h1>
            <p className="mt-2 text-sm font-medium text-muted-foreground">
              会话有效至：{formatInstant(sessionQuery.data.expiresAt)}
            </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" asChild>
            <Link to="/portal/redeem">兑换额度</Link>
          </Button>
          <Button type="button" variant="outline" onClick={() => logoutMutation.mutate()}>
            退出门户
          </Button>
        </div>
      </header>

      <div className="grid gap-4 md:grid-cols-4">
        <Metric title="活跃订阅" value={subscriptions.filter((item) => item.status === 'ACTIVE').length} />
        <Metric title="访问密钥" value={keys.length} />
        <Metric title="未读公告" value={announcements.filter((item) => !item.read).length} />
        <Metric title="Token 余额" value={formatNumber(redeemStatus?.currentTokenCredits)} />
      </div>

      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <p className="flex items-center gap-2 text-sm font-medium text-primary">
              <TerminalSquareIcon data-icon="inline-start" />
              Codex 应用接口
            </p>
            <CardTitle className="mt-2">Codex 接入</CardTitle>
          </div>
          <StatusBadge tone={codexAccess.tone}>{codexAccess.status}</StatusBadge>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-3 md:grid-cols-5">
            <PortalCodexMetric label="接口地址" value={portalApiBase} />
            <PortalCodexMetric label="访问密钥" value={codexAccess.maskedKey} />
            <PortalCodexMetric label="默认模型" value={codexAccess.model} />
            <PortalCodexMetric label="可用额度" value={codexAccess.quota} />
            <PortalCodexMetric label="最近使用" value={codexAccess.lastUsed} />
          </div>
          <div className="rounded-lg border border-primary/20 bg-primary/10 p-4">
            <pre className="overflow-auto text-xs leading-6 text-foreground">
              {[
                `OPENAI_BASE_URL=${portalApiBase}`,
                `OPENAI_API_KEY=${codexAccess.maskedKey}`,
                `OPENAI_MODEL=${codexAccess.model}`,
              ].join('\n')}
            </pre>
          </div>
          {codexAccess.message ? (
            <div className="rounded-lg border border-border bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
              {codexAccess.message}
            </div>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" variant="outline" asChild>
              <Link to="/portal/keys">
                管理访问密钥
                <ArrowUpRightIcon data-icon="inline-end" />
              </Link>
            </Button>
            <Button type="button" size="sm" variant="outline" asChild>
              <Link to="/portal/subscriptions">
                查看订阅
                <ArrowUpRightIcon data-icon="inline-end" />
              </Link>
            </Button>
            <Button type="button" size="sm" variant="outline" asChild>
              <Link to="/portal/redeem">
                兑换额度
                <ArrowUpRightIcon data-icon="inline-end" />
              </Link>
            </Button>
          </div>
        </CardContent>
      </Card>

      <section className="grid gap-6 xl:grid-cols-[1.25fr_0.75fr]">
        <Card className="border-border bg-card/95 shadow-lg">
          <CardHeader>
            <CardTitle>我的订阅</CardTitle>
            <Button className="w-fit" type="button" size="sm" variant="outline" asChild>
              <Link to="/portal/subscriptions">查看订阅页</Link>
            </Button>
          </CardHeader>
          <CardContent>
            {subscriptionsQuery.isPending ? (
              <PageSkeleton count={1} />
            ) : subscriptionsQuery.error ? (
              <InlineError error={subscriptionsQuery.error} title="订阅加载失败" />
            ) : subscriptions.length ? (
              <PaginatedRows items={subscriptions}>
                {({ pageItems }) => (
                  <div className="overflow-x-auto rounded-lg border border-border bg-card">
                    <table className="min-w-[46rem] w-full text-sm">
                      <thead className="bg-muted/40">
                        <tr>
                          <th className="w-[28%] px-4 py-3 text-left font-medium text-muted-foreground">套餐</th>
                          <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                          <th className="w-[28%] px-4 py-3 text-left font-medium text-muted-foreground">额度</th>
                          <th className="w-[26%] px-4 py-3 text-left font-medium text-muted-foreground">有效期</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pageItems.map((item) => (
                          <tr key={item.id} className="border-t border-border/50 align-top">
                            <td className="px-4 py-3 font-medium text-foreground">{item.planName}</td>
                            <td className="px-4 py-3"><StatusBadge tone={item.status === 'ACTIVE' ? 'success' : 'warning'}>{item.status}</StatusBadge></td>
                            <td className="px-4 py-3 text-muted-foreground">
                              RPM {formatNumber(item.rpmLimit)} / TPM {formatNumber(item.tpmLimit)}
                            </td>
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

        <Card className="border-border bg-card/95 shadow-lg">
          <CardHeader>
            <CardTitle>公告与兑换</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {announcements.length ? (
              announcements.map((item) => (
                <div key={item.id} className="rounded-lg border border-border bg-card p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="font-medium text-foreground">{item.title}</div>
                    <StatusBadge tone={item.read ? 'info' : 'warning'}>{item.read ? '已读' : '未读'}</StatusBadge>
                  </div>
                  <p className="mt-1 text-sm text-muted-foreground">{item.summary}</p>
                  {item.body ? <p className="mt-3 whitespace-pre-wrap text-sm text-foreground">{item.body}</p> : null}
                  {!item.read ? (
                    <Button
                      className="mt-3"
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => markReadMutation.mutate(item.id)}
                    >
                      标记已读
                    </Button>
                  ) : null}
                  <Button
                    className="mt-3 ml-2"
                    type="button"
                    size="sm"
                    variant="ghost"
                    asChild
                  >
                    <Link to={`/portal/announcements/${item.id}`}>查看详情</Link>
                  </Button>
                </div>
              ))
            ) : (
              <EmptyState title="暂无公告" />
            )}
            <form className="rounded-lg border border-dashed border-primary/30 bg-primary/10 p-4 text-sm text-foreground" onSubmit={handleRedeem}>
              <div className="font-medium">兑换码</div>
              <p className="mt-1 text-muted-foreground">{redeemStatus?.message ?? '兑换入口加载中。'}</p>
              <div className="mt-3 flex gap-2">
                <input
                  className="h-10 flex-1 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring"
                  value={redeemCode}
                  onChange={(event) => setRedeemCode(event.target.value)}
                  placeholder="粘贴兑换码"
                />
                <Button type="submit" disabled={redeemMutation.isPending}>兑换</Button>
              </div>
              {redeemMutation.error ? (
                <div className="mt-3 rounded-xl border border-destructive/20 bg-destructive/10 px-3 py-2 text-destructive">
                  {redeemMutation.error instanceof Error ? redeemMutation.error.message : '兑换失败'}
                </div>
              ) : null}
              {redeemMutation.data ? (
                <div className="mt-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-3 py-2 text-emerald-700 dark:text-emerald-300">
                  {redeemMutation.data.message} 本次增加 {formatNumber(redeemMutation.data.deltaTokenCredits)} Token。
                </div>
              ) : null}
            </form>
          </CardContent>
        </Card>
      </section>

      <Card className="border-border bg-card/95 shadow-lg">
          <CardHeader>
            <CardTitle>我的访问密钥</CardTitle>
            <Button className="w-fit" type="button" size="sm" variant="outline" asChild>
              <Link to="/portal/keys">管理我的访问密钥</Link>
            </Button>
        </CardHeader>
        <CardContent>
          {keysQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : keysQuery.error ? (
            <InlineError error={keysQuery.error} title="访问密钥加载失败" />
          ) : keys.length ? (
            <PaginatedRows items={keys}>
              {({ pageItems }) => (
                <div className="overflow-x-auto rounded-lg border border-border bg-card">
                  <table className="min-w-[54rem] w-full text-sm">
                    <thead className="bg-muted/40">
                      <tr>
                        <th className="w-[24%] px-4 py-3 text-left font-medium text-muted-foreground">名称</th>
                        <th className="w-[22%] px-4 py-3 text-left font-medium text-muted-foreground">掩码</th>
                        <th className="w-[14%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                        <th className="w-[20%] px-4 py-3 text-left font-medium text-muted-foreground">限制</th>
                        <th className="w-[20%] px-4 py-3 text-left font-medium text-muted-foreground">最近使用</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pageItems.map((item) => (
                        <tr key={item.id} className="border-t border-border/50">
                          <td className="px-4 py-3 font-medium text-foreground">{item.keyName}</td>
                          <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{item.maskedKey}</td>
                          <td className="px-4 py-3"><StatusBadge tone={item.active ? 'success' : 'warning'}>{item.active ? '启用' : '停用'}</StatusBadge></td>
                          <td className="px-4 py-3 text-muted-foreground">RPM {formatNumber(item.rpmLimit)}</td>
                          <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.lastUsedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </PaginatedRows>
          ) : (
            <EmptyState title="暂无访问密钥" />
          )}
        </CardContent>
      </Card>

      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <CardTitle>充值订单</CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <form className="grid gap-3 rounded-lg border border-border bg-card p-4 md:grid-cols-[1fr_1fr_1fr_auto]" onSubmit={handleCreatePayment}>
            <label className="grid gap-1 text-sm">
              <span className="font-medium text-foreground">渠道</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring"
                value={paymentProvider}
                onChange={(event) => setPaymentProvider(event.target.value)}
              >
                <option value="stripe">Stripe</option>
                <option value="easypay">EasyPay</option>
                <option value="alipay">支付宝</option>
                <option value="wechat">微信支付</option>
              </select>
            </label>
            <label className="grid gap-1 text-sm">
              <span className="font-medium text-foreground">金额分</span>
              <input
                className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring"
                inputMode="numeric"
                value={paymentAmountMinor}
                onChange={(event) => setPaymentAmountMinor(event.target.value)}
              />
            </label>
            <label className="grid gap-1 text-sm">
              <span className="font-medium text-foreground">Token</span>
              <input
                className="h-10 rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring"
                inputMode="numeric"
                value={paymentTokenCredits}
                onChange={(event) => setPaymentTokenCredits(event.target.value)}
              />
            </label>
            <Button className="self-end" type="submit" disabled={createPaymentMutation.isPending}>
              创建订单
            </Button>
          </form>
          {createPaymentMutation.error ? (
            <InlineError error={createPaymentMutation.error} title="充值订单创建失败" />
          ) : null}
          {latestCheckoutUrl ? (
            <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-700 dark:text-emerald-300">
              <div className="font-medium">支付链接已生成</div>
              <a className="mt-2 block break-all font-mono text-xs text-emerald-700 underline dark:text-emerald-300" href={latestCheckoutUrl} target="_blank" rel="noreferrer">
                {latestCheckoutUrl}
              </a>
            </div>
          ) : null}
          {paymentOrdersQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : paymentOrdersQuery.error ? (
            <InlineError error={paymentOrdersQuery.error} title="充值订单加载失败" />
          ) : paymentOrders.length ? (
            <PaginatedRows items={paymentOrders}>
              {({ pageItems }) => (
                <div className="overflow-x-auto rounded-lg border border-border bg-card">
                  <table className="min-w-[58rem] w-full text-sm">
                <thead className="bg-muted/40">
                  <tr>
                    <th className="w-[22%] px-4 py-3 text-left font-medium text-muted-foreground">订单</th>
                    <th className="w-[14%] px-4 py-3 text-left font-medium text-muted-foreground">渠道</th>
                    <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">金额</th>
                    <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">Token</th>
                    <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                    <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">支付链接</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item) => (
                    <tr key={item.id} className="border-t border-border/50 align-top">
                      <td className="break-all px-4 py-3 font-mono text-xs text-foreground">{item.orderNo}</td>
                      <td className="px-4 py-3 text-muted-foreground">{item.provider}</td>
                      <td className="px-4 py-3 text-muted-foreground">{(item.amountMinor / 100).toFixed(2)} {item.currency}</td>
                      <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.tokenCredits)}</td>
                      <td className="px-4 py-3"><StatusBadge tone={item.status === 'PAID' ? 'success' : item.status === 'FAILED' ? 'danger' : 'warning'}>{item.status}</StatusBadge></td>
                      <td className="px-4 py-3">
                        {item.checkoutUrl ? (
                          <a className="text-primary underline" href={item.checkoutUrl} target="_blank" rel="noreferrer">打开</a>
                        ) : (
                          <span className="text-muted-foreground">--</span>
                        )}
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
                <div className="overflow-x-auto rounded-lg border border-border bg-card">
                  <table className="min-w-[44rem] w-full text-sm">
                <thead className="bg-muted/40">
                  <tr>
                    <th className="w-[20%] px-4 py-3 text-left font-medium text-muted-foreground">变动</th>
                    <th className="w-[20%] px-4 py-3 text-left font-medium text-muted-foreground">余额</th>
                    <th className="w-[24%] px-4 py-3 text-left font-medium text-muted-foreground">来源</th>
                    <th className="w-[36%] px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                  </tr>
                </thead>
                <tbody>
                  {pageItems.map((item) => (
                    <tr key={item.id} className="border-t border-border/50">
                      <td className="px-4 py-3 font-medium text-emerald-700 dark:text-emerald-300">+{formatNumber(item.deltaTokenCredits)}</td>
                      <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.balanceAfterTokenCredits)}</td>
                      <td className="px-4 py-3 text-muted-foreground">{item.referenceId ?? item.reason}</td>
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

type CodexAccess = {
  status: string
  tone: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
  maskedKey: string
  model: string
  quota: string
  lastUsed: string
  message?: string
}

function PortalCodexMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-border bg-card p-4 shadow-sm">
      <div className="text-xs font-semibold uppercase text-muted-foreground">{label}</div>
      <div className="mt-2 break-words text-sm font-semibold text-foreground">{value}</div>
    </div>
  )
}

function buildCodexAccess(
  keys: PortalKey[],
  subscriptions: PortalSubscription[],
  redeemStatus: PortalRedeemStatus | undefined,
  ledger: PortalBalanceLedger[],
): CodexAccess {
  const codexKey = keys.find(isCodexPortalKey) ?? keys.find((key) => key.active) ?? keys[0]
  const activeSubscriptionCount = subscriptions.filter((item) => item.status === 'ACTIVE').length
  const tokenCredits = redeemStatus?.currentTokenCredits ?? 0
  const hasQuota = activeSubscriptionCount > 0 || tokenCredits > 0
  const lastLedger = ledger[0]

  if (!codexKey) {
    return {
      status: '未接入',
      tone: 'warning',
      maskedKey: '暂无访问密钥',
      model: 'gpt-5.4',
      quota: formatNumber(tokenCredits),
      lastUsed: formatInstant(lastLedger?.createdAt) || '暂无',
      message: '请先在门户创建或领取个人访问密钥。',
    }
  }

  const model = codexKey.allowedModels?.find((item) => item.toLowerCase().includes('gpt-5'))
    ?? codexKey.allowedModels?.[0]
    ?? 'gpt-5.4'

  if (!codexKey.active) {
    return {
      status: '访问密钥已停用',
      tone: 'warning',
      maskedKey: codexKey.maskedKey,
      model,
      quota: formatNumber(tokenCredits),
      lastUsed: formatInstant(codexKey.lastUsedAt) || '暂无',
      message: '当前访问密钥已停用，请在访问密钥管理页轮换或重新启用。',
    }
  }

  if (isExpired(codexKey.expiresAt)) {
    return {
      status: '授权过期',
      tone: 'warning',
      maskedKey: codexKey.maskedKey,
      model,
      quota: formatNumber(tokenCredits),
      lastUsed: formatInstant(codexKey.lastUsedAt) || '暂无',
      message: '当前访问密钥已过期，请重新生成个人访问密钥。',
    }
  }

  if (!hasQuota) {
    return {
      status: '额度不足',
      tone: 'danger',
      maskedKey: codexKey.maskedKey,
      model,
      quota: formatNumber(tokenCredits),
      lastUsed: formatInstant(codexKey.lastUsedAt) || '暂无',
      message: 'Token 余额和活跃订阅都不可用，Codex 请求会被额度策略拦截。',
    }
  }

  return {
    status: '可用',
    tone: 'success',
    maskedKey: codexKey.maskedKey,
    model,
    quota: activeSubscriptionCount > 0 ? `${activeSubscriptionCount} 个订阅 / ${formatNumber(tokenCredits)} Token` : `${formatNumber(tokenCredits)} Token`,
    lastUsed: formatInstant(codexKey.lastUsedAt) || formatInstant(lastLedger?.createdAt) || '暂无',
  }
}

function isCodexPortalKey(key: PortalKey) {
  return key.allowedProtocolSuites?.includes('responses')
    || key.allowedModels?.some((model) => model.toLowerCase().includes('gpt-5')) === true
    || key.keyName.toLowerCase().includes('codex')
}

function isExpired(value: string | null | undefined) {
  if (!value) {
    return false
  }
  const parsed = new Date(value).getTime()
  return Number.isFinite(parsed) && parsed <= Date.now()
}

function getPortalApiBase() {
  if (typeof window === 'undefined') {
    return '/v1'
  }
  return `${window.location.origin}/v1`
}
