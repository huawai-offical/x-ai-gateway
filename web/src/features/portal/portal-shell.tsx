import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { Card, CardHeader, CardTitle } from '@/components/ui/card'

const NAV_ITEMS = [
  { to: '/portal', label: '概览', end: true },
  { to: '/portal/subscriptions', label: '订阅' },
  { to: '/portal/keys', label: '访问密钥' },
  { to: '/portal/redeem', label: '兑换与余额' },
  { to: '/portal/invitations', label: '我的邀请' },
  { to: '/portal/usage', label: '用量' },
  { to: '/portal/status', label: '服务状态' },
  { to: '/portal/orders', label: '订单' },
  { to: '/portal/security', label: '安全中心' },
]

export function PortalFrame({ children }: { children: ReactNode }) {
  return (
    <main className="min-h-svh bg-background px-4 py-6 text-foreground md:py-8">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6">
        <nav className="flex flex-wrap gap-2 rounded-lg border border-border bg-card/95 p-2 shadow-sm backdrop-blur">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }: { isActive: boolean }) =>
                `rounded-md px-3 py-2 text-sm font-medium transition md:px-4 ${
                  isActive ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        {children}
      </div>
    </main>
  )
}

export function Metric({ title, value }: { title: string; value: number | string }) {
  return (
    <Card className="border-border bg-card/95 shadow-md backdrop-blur">
      <CardHeader className="pb-2">
        <p className="text-sm font-medium text-muted-foreground">{title}</p>
        <CardTitle className="text-2xl text-foreground md:text-3xl">{value}</CardTitle>
      </CardHeader>
    </Card>
  )
}
