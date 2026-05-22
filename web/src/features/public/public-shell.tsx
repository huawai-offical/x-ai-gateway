import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { ArrowRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'

const NAV_ITEMS = [
  { to: '/', label: '首页', end: true },
]

export function PublicFrame({ children }: { children: ReactNode }) {
  return (
    <main className="min-h-svh bg-background text-foreground">
      <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-3 px-4 py-3 md:flex-row md:items-center md:justify-between">
          <Link to="/" className="flex items-center gap-2 font-semibold tracking-tight text-foreground">
            <img src="/logo.svg" alt="" className="size-9" />
            x-ai-gateway
          </Link>
          <nav className="flex flex-wrap items-center gap-1">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }: { isActive: boolean }) =>
                  `rounded-lg px-3 py-2 text-sm font-medium transition ${
                    isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="flex flex-wrap gap-2">
            <Button asChild size="sm" variant="outline">
              <Link to="/portal/login">客户登录</Link>
            </Button>
            <Button asChild size="sm">
              <Link to="/portal/register">
                开始接入
                <ArrowRightIcon data-icon="inline-end" />
              </Link>
            </Button>
          </div>
        </div>
      </header>
      {children}
      <footer className="border-t border-border bg-background">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-3 px-4 py-6 text-sm text-muted-foreground md:flex-row md:items-center md:justify-between">
          <span>x-ai-gateway 公开站点</span>
          <div className="flex flex-wrap gap-4">
            <Link className="hover:text-foreground" to="/portal">门户</Link>
            <Link className="hover:text-foreground" to="/console">控制台</Link>
          </div>
        </div>
      </footer>
    </main>
  )
}

export function PublicBand({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`border-t border-border bg-background ${className}`}>
      <div className="mx-auto w-full max-w-7xl px-4 py-10 md:py-14">
        {children}
      </div>
    </section>
  )
}
