import { Fragment, useEffect, useMemo, useRef, useState, type CSSProperties, type PropsWithChildren } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import {
  ChevronDownIcon,
  ChevronRightIcon,
  KeyRoundIcon,
  LogOutIcon,
  PanelLeftCloseIcon,
  PanelLeftOpenIcon,
  SearchIcon,
  MenuIcon,
  ShieldCheckIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { navigationGroups, type ResolvedRouteMeta } from '@/app/navigation'
import { GlobalActivityBar } from './global-activity-bar'
import { ThemeSwitch } from './theme-switch'
import { useAdminAuth } from '@/features/auth/auth-provider'
import { formatInstant } from '@/lib/format'

const SIDEBAR_STORAGE_KEY = 'x-ai-gateway:sidebar-collapsed'
const NAV_GROUPS_STORAGE_KEY = 'x-ai-gateway:nav-expanded-groups'

type AppShellProps = PropsWithChildren<{
  route: ResolvedRouteMeta
}>

export function AppShell({ route, children }: AppShellProps) {
  const navigate = useNavigate()
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    if (typeof window === 'undefined') {
      return false
    }

    return window.localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true'
  })
  const [expandedNavGroups, setExpandedNavGroups] = useState<string[] | null>(() => {
    if (typeof window === 'undefined') {
      return null
    }

    const raw = window.localStorage.getItem(NAV_GROUPS_STORAGE_KEY)
    if (!raw) {
      return null
    }

    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : null
    } catch {
      return null
    }
  })
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  const [commandOpen, setCommandOpen] = useState(false)
  const [headerHeight, setHeaderHeight] = useState(0)
  const { logout, session } = useAdminAuth()
  const headerRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(sidebarCollapsed))
  }, [sidebarCollapsed])

  useEffect(() => {
    if (expandedNavGroups == null) {
      return
    }
    window.localStorage.setItem(NAV_GROUPS_STORAGE_KEY, JSON.stringify(expandedNavGroups))
  }, [expandedNavGroups])

  useEffect(() => {
    const node = headerRef.current
    if (!node) return undefined

    const updateHeaderHeight = () => {
      setHeaderHeight(Math.ceil(node.getBoundingClientRect().height))
    }

    updateHeaderHeight()

    if (typeof ResizeObserver === 'undefined') {
      return undefined
    }

    const observer = new ResizeObserver(() => {
      updateHeaderHeight()
    })

    observer.observe(node)

    return () => {
      observer.disconnect()
    }
  }, [])

  const sidebar = useMemo(
    () => (
      <SidebarContent
        collapsed={sidebarCollapsed}
        currentNav={route.navTo}
        expandedGroups={expandedNavGroups}
        onToggleGroup={(groupLabel) => {
          setExpandedNavGroups((current) => {
            const activeGroups = navigationGroups
              .filter((group) => group.items.some((item) => item.to === route.navTo))
              .map((group) => group.label)
            const next = new Set(current ?? activeGroups)
            if (next.has(groupLabel)) {
              next.delete(groupLabel)
            } else {
              next.add(groupLabel)
            }
            return Array.from(next)
          })
        }}
        onNavigate={() => setMobileSidebarOpen(false)}
      />
    ),
    [expandedNavGroups, route.navTo, sidebarCollapsed],
  )

  async function handleLogout() {
    await logout()
    navigate('/login?reason=logged-out', { replace: true })
  }

  const effectiveHeaderHeight = headerHeight || 104
  const shellStyle = useMemo(
    () =>
      ({
        '--app-shell-header-height': `${effectiveHeaderHeight}px`,
        background: 'var(--surface-shell)',
      }) as CSSProperties,
    [effectiveHeaderHeight],
  )

  return (
    <div
      style={shellStyle}
      className="h-svh overflow-hidden"
    >
      <div className="flex h-full overflow-hidden">
        <aside
          data-testid="app-shell-sidebar"
          className={cn(
            'hidden h-full shrink-0 border-r border-sidebar-border/70 bg-sidebar/90 shadow-[inset_-1px_0_0_rgba(255,255,255,0.04)] backdrop-blur-xl transition-[width] duration-200 ease-out md:flex md:flex-col',
            sidebarCollapsed ? 'md:w-[5.5rem]' : 'md:w-[18rem]',
          )}
        >
          {sidebar}
        </aside>

        <Sheet open={mobileSidebarOpen} onOpenChange={setMobileSidebarOpen}>
          <SheetContent
            side="left"
            className="h-svh w-[18rem] max-w-[calc(100vw-1rem)] border-r border-sidebar-border/70 bg-sidebar/96 p-0 backdrop-blur-xl"
          >
            <SheetHeader className="sr-only">
              <SheetTitle>导航菜单</SheetTitle>
              <SheetDescription>切换控制台功能区域。</SheetDescription>
            </SheetHeader>
            <div className="flex h-full min-h-0 flex-col">{sidebar}</div>
          </SheetContent>
        </Sheet>

        <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
          <header
            ref={headerRef}
            data-testid="app-shell-header"
            className="z-40 shrink-0 border-b border-border/60 bg-background/78 shadow-[0_1px_0_rgba(255,255,255,0.05)] backdrop-blur-2xl supports-[backdrop-filter]:bg-background/72"
          >
            <div className="relative">
              <GlobalActivityBar />
              <div className="mx-auto flex max-w-[92rem] flex-col gap-3 px-4 py-3 lg:px-6">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 flex-1 items-start gap-3">
                    <Button
                      variant="outline"
                      size="icon-sm"
                      className="border-border/55 bg-card/65 text-muted-foreground shadow-[0_1px_0_rgba(255,255,255,0.06)] hover:bg-muted/70 hover:text-foreground md:hidden"
                      onClick={() => setMobileSidebarOpen(true)}
                    >
                      <MenuIcon />
                      <span className="sr-only">打开导航</span>
                    </Button>
                    <Button
                      variant="outline"
                      size="icon-sm"
                      className="hidden border-border/55 bg-card/65 text-muted-foreground shadow-[0_1px_0_rgba(255,255,255,0.06)] hover:bg-muted/70 hover:text-foreground md:inline-flex"
                      onClick={() => setSidebarCollapsed((value) => !value)}
                    >
                      {sidebarCollapsed ? <PanelLeftOpenIcon /> : <PanelLeftCloseIcon />}
                      <span className="sr-only">切换侧边栏</span>
                    </Button>

                    <div className="min-w-0 space-y-1.5">
                      <Breadcrumb>
                        <BreadcrumbList>
                          {route.breadcrumbs.map((item, index) => (
                            <Fragment key={`${item.label}-${index}`}>
                              <BreadcrumbItem>
                                {item.to ? (
                                  <BreadcrumbLink asChild>
                                    <Link to={item.to}>{item.label}</Link>
                                  </BreadcrumbLink>
                                ) : index === route.breadcrumbs.length - 1 ? (
                                  <BreadcrumbPage>{item.label}</BreadcrumbPage>
                                ) : (
                                  <span>{item.label}</span>
                                )}
                              </BreadcrumbItem>
                              {index < route.breadcrumbs.length - 1 ? <BreadcrumbSeparator /> : null}
                            </Fragment>
                          ))}
                        </BreadcrumbList>
                      </Breadcrumb>
                      <div className="space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                            {route.title}
                          </h1>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="flex shrink-0 items-start gap-3">
                    <ThemeSwitch className="hidden lg:inline-flex" />
                    <Button
                      variant="outline"
                      className="hidden min-w-[12rem] justify-start gap-2 border-border/55 bg-card/65 text-muted-foreground shadow-[0_1px_0_rgba(255,255,255,0.06)] hover:bg-muted/70 hover:text-foreground lg:inline-flex"
                      onClick={() => setCommandOpen(true)}
                    >
                      <SearchIcon data-icon="inline-start" />
                      搜索
                    </Button>
                    <AccountMenu
                      expiresAt={session?.expiresAt ?? null}
                      username={session?.username ?? 'admin'}
                      onOpenSettings={() => navigate('/settings/admin-auth')}
                      onLogout={handleLogout}
                    />
                  </div>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-3 xl:hidden">
                  <ThemeSwitch />
                  <Button
                    variant="outline"
                    className="justify-start gap-2 border-border/55 bg-card/65 text-muted-foreground shadow-[0_1px_0_rgba(255,255,255,0.06)] hover:bg-muted/70 hover:text-foreground"
                    onClick={() => setCommandOpen(true)}
                  >
                    <SearchIcon data-icon="inline-start" />
                    搜索
                  </Button>
                </div>
              </div>
            </div>
          </header>

          <CommandPalette open={commandOpen} onOpenChange={setCommandOpen} />

          <main
            data-testid="app-shell-main"
            className="scrollbar-subtle min-h-0 flex-1 overflow-y-auto overscroll-contain"
          >
            <div className="mx-auto flex min-h-full w-full max-w-[92rem] flex-col gap-5 px-4 py-5 lg:px-6">
              {children}
            </div>
          </main>
        </div>
      </div>
    </div>
  )
}

function CommandPalette({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [query, setQuery] = useState('')
  const entries = useMemo(() => {
    const navigationEntries = navigationGroups.flatMap((group) =>
      group.items.map((item) => ({
        label: item.label,
        group: group.label,
        to: item.to,
        keywords: `${group.label} ${item.label}`,
      })),
    )
    return [
      ...navigationEntries,
      {
        label: '按请求 ID 排查',
        group: '观测',
        to: '/console/request-logs',
        keywords: 'request id 请求 失败 日志 trace',
      },
      {
        label: '新增上游凭证',
        group: '接入',
        to: '/console/credentials',
        keywords: 'codex 上游凭证 官方账号 auth json api key secret 账号分组',
      },
      {
        label: '按客户端实例定位',
        group: '接入',
        to: '/console/integrations/external-apps',
        keywords: 'client instance deep link plugin 客户端实例',
      },
    ]
  }, [])
  const normalizedQuery = query.trim().toLowerCase()
  const visibleEntries = entries
    .filter((entry) => {
      if (!normalizedQuery) return true
      return `${entry.label} ${entry.group} ${entry.keywords}`.toLowerCase().includes(normalizedQuery)
    })
    .slice(0, 8)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(92vw,42rem)]">
        <DialogHeader>
          <DialogTitle>控制台搜索</DialogTitle>
          <DialogDescription className="sr-only">按页面、请求 ID、账号分组或客户端实例快速定位控制台入口。</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <Input
            value={query}
            autoFocus
            placeholder="输入页面、密钥、账号分组、请求 ID 或客户端实例"
            onChange={(event) => setQuery(event.target.value)}
          />
          <div className="flex max-h-[22rem] flex-col gap-2 overflow-y-auto">
            {visibleEntries.length ? (
              visibleEntries.map((entry) => (
                <Link
                  key={`${entry.group}-${entry.label}-${entry.to}`}
                  to={entry.to}
                  onClick={() => onOpenChange(false)}
                  className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-background px-3 py-2.5 text-sm transition-colors hover:bg-muted"
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-foreground">{entry.label}</span>
                    <span className="block truncate text-xs text-muted-foreground">{entry.group}</span>
                  </span>
                  <span className="shrink-0 text-xs text-muted-foreground">打开</span>
                </Link>
              ))
            ) : (
              <div className="rounded-lg border border-border/60 bg-muted/30 px-3 py-4 text-sm text-muted-foreground">
                没有匹配的控制台入口
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function AccountMenu({
  username,
  expiresAt,
  onOpenSettings,
  onLogout,
}: {
  username: string
  expiresAt: string | null
  onOpenSettings: () => void
  onLogout: () => Promise<void>
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          className="min-w-0 max-w-[15rem] justify-between gap-2 border-border/55 bg-card/65 shadow-[0_1px_0_rgba(255,255,255,0.06)] hover:bg-muted/70"
        >
          <div className="flex min-w-0 items-center gap-2">
            <ShieldCheckIcon data-icon="inline-start" />
            <span className="truncate">{username}</span>
          </div>
          <ChevronDownIcon data-icon="inline-end" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72 min-w-72">
        <DropdownMenuLabel className="flex flex-col gap-1">
          <span className="text-sm font-medium text-foreground">{username}</span>
          <span className="text-xs text-muted-foreground">
            {expiresAt ? `会话过期：${formatInstant(expiresAt)}` : '会话有效中'}
          </span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem onClick={onOpenSettings}>
            <KeyRoundIcon data-icon="inline-start" />
            控制台认证
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => void onLogout()}>
            <LogOutIcon data-icon="inline-start" />
            登出
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function SidebarContent({
  collapsed,
  currentNav,
  expandedGroups,
  onToggleGroup,
  onNavigate,
}: {
  collapsed: boolean
  currentNav: string
  expandedGroups: string[] | null
  onToggleGroup: (groupLabel: string) => void
  onNavigate: () => void
}) {
  const activeGroupLabels = useMemo(
    () =>
      new Set(
        navigationGroups
          .filter((group) => group.items.some((item) => item.to === currentNav))
          .map((group) => group.label),
      ),
    [currentNav],
  )

  return (
    <div className="flex h-full min-h-0 flex-col text-sidebar-foreground">
      <div
        data-testid="app-shell-sidebar-brand"
        className={cn(
          'flex min-h-[var(--app-shell-header-height)] flex-col justify-center gap-3 border-b border-sidebar-border/70 px-3 py-4',
          collapsed && 'px-2',
        )}
      >
        <div className={cn('flex items-center gap-3', collapsed && 'justify-center')}>
          <img src="/logo.svg" alt="" className="size-10 shrink-0" />
          {!collapsed ? (
            <div className="min-w-0">
              <div className="text-xs font-medium uppercase tracking-[0.18em] text-sidebar-foreground/62">
                x-ai-gateway
              </div>
              <div className="truncate text-base font-semibold text-foreground">管理控制台</div>
            </div>
          ) : null}
        </div>
      </div>

      <div className="scrollbar-subtle min-h-0 flex-1 overflow-y-auto px-2 py-4">
        <nav className="flex flex-col gap-4">
          {navigationGroups.map((group) => {
            const groupActive = activeGroupLabels.has(group.label)
            const expanded = !collapsed && (groupActive || (expandedGroups == null ? false : expandedGroups.includes(group.label)))
            return (
              <div key={group.label} className="flex flex-col gap-2">
                {!collapsed ? (
                  <button
                    type="button"
                    onClick={() => onToggleGroup(group.label)}
                    className={cn(
                      'flex items-center justify-between gap-2 rounded-xl px-2 py-1.5 text-left text-[11px] font-semibold tracking-normal text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
                      groupActive && 'bg-sidebar-accent/70 text-sidebar-accent-foreground',
                    )}
                  >
                    <span className="truncate">{group.label}</span>
                    {expanded ? <ChevronDownIcon /> : <ChevronRightIcon />}
                  </button>
                ) : null}
                <div className={cn('flex flex-col gap-1', !collapsed && !expanded && 'hidden')}>
                {group.items.map((item) => {
                  const Icon = item.icon
                  return (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
                      title={item.label}
                      onClick={onNavigate}
                      className={({ isActive }: { isActive: boolean }) =>
                        cn(
                          'group flex items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-sm text-sidebar-foreground/82 transition-all hover:border-sidebar-border hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
                          (isActive || currentNav === item.to) &&
                            'active border-sidebar-primary/35 bg-sidebar-primary text-sidebar-primary-foreground shadow-sm ring-1 ring-sidebar-primary/25',
                          collapsed && 'justify-center px-2',
                        )
                      }
                    >
                      <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-background/72 text-sidebar-foreground/68 shadow-[0_1px_0_rgba(255,255,255,0.04)] ring-1 ring-sidebar-border/60 group-hover:bg-sidebar/80 group-hover:text-sidebar-accent-foreground group-[.active]:bg-sidebar-primary-foreground/18 group-[.active]:text-sidebar-primary-foreground">
                        <Icon className="size-4" />
                      </div>
                      {!collapsed ? (
                        <div className="min-w-0">
                          <div className="truncate font-medium">{item.label}</div>
                        </div>
                      ) : null}
                    </NavLink>
                  )
                })}
                </div>
              </div>
            )
          })}
        </nav>
      </div>

      <div className={cn('border-t border-sidebar-border/70 px-3 py-4', collapsed && 'px-2')}>
        {!collapsed ? (
          <>
            <Separator />
            <div className="pt-3 text-xs text-muted-foreground">任务：X-227</div>
          </>
        ) : (
          <div className="flex items-center justify-center pt-1">
            <img src="/logo.svg" alt="" className="size-6" />
          </div>
        )}
      </div>
    </div>
  )
}
