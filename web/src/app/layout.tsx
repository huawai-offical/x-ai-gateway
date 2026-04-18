import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import type { NavigationIcon } from './navigation'
import { navigationGroups, resolveRouteMeta } from './navigation'

function NavigationGlyph({ icon }: { icon: NavigationIcon }) {
  switch (icon) {
    case 'key':
      return <path d="M8 14a4 4 0 1 1 3.54-5.86L20 8v3h-2v2h-2v2h-3.46A4 4 0 0 1 8 14Z" />
    case 'accounts':
      return (
        <>
          <path d="M8 11a3 3 0 1 0-3-3 3 3 0 0 0 3 3Z" />
          <path d="M16.5 10a2.5 2.5 0 1 0-2.5-2.5A2.5 2.5 0 0 0 16.5 10Z" />
          <path d="M3.5 18a4.5 4.5 0 0 1 9 0" />
          <path d="M14 17.5a3.5 3.5 0 0 1 7 0" />
        </>
      )
    case 'proxy':
      return (
        <>
          <path d="M5 6h5v5H5z" />
          <path d="M14 6h5v5h-5z" />
          <path d="M9 8.5h6" />
          <path d="M12 8.5v7" />
          <path d="M9 18h6" />
        </>
      )
    case 'tls':
      return (
        <>
          <path d="M12 3 6 5.5v5c0 4.2 2.6 7.2 6 8.5 3.4-1.3 6-4.3 6-8.5v-5Z" />
          <path d="M10 11.5V10a2 2 0 1 1 4 0v1.5" />
          <path d="M9 11.5h6V16H9z" />
        </>
      )
    case 'probe':
      return (
        <>
          <path d="M4 18h16" />
          <path d="m6 15 3-4 3 2 5-7 1 2" />
          <path d="M18 8h2v2" />
        </>
      )
    case 'overview':
      return (
        <>
          <path d="M4 13h4v7H4z" />
          <path d="M10 9h4v11h-4z" />
          <path d="M16 5h4v15h-4z" />
        </>
      )
    case 'alert':
      return (
        <>
          <path d="M12 4 3.5 19h17L12 4Z" />
          <path d="M12 10v4" />
          <path d="M12 17h.01" />
        </>
      )
    case 'logs':
      return (
        <>
          <path d="M6 5h12v14H6z" />
          <path d="M9 9h6" />
          <path d="M9 13h6" />
          <path d="M9 17h4" />
        </>
      )
    case 'site':
      return (
        <>
          <path d="M4 19h16" />
          <path d="M6 19V5h12v14" />
          <path d="M9 9h2" />
          <path d="M13 9h2" />
          <path d="M9 13h2" />
          <path d="M13 13h2" />
        </>
      )
    case 'matrix':
      return (
        <>
          <path d="M5 5h14v14H5z" />
          <path d="M5 10h14" />
          <path d="M10 5v14" />
          <path d="M14.5 14.5h.01" />
        </>
      )
    case 'explain':
      return (
        <>
          <path d="M7 7h10v10H7z" />
          <path d="M10 10h4" />
          <path d="M10 13h4" />
          <path d="m4 4 2 2" />
          <path d="m18 18 2 2" />
        </>
      )
    case 'rules':
      return (
        <>
          <path d="M7 4h10v4H7z" />
          <path d="M7 12h10v8H7z" />
          <path d="M12 8v4" />
        </>
      )
    case 'install':
      return (
        <>
          <path d="M12 4v10" />
          <path d="m8.5 10.5 3.5 3.5 3.5-3.5" />
          <path d="M5 19h14" />
        </>
      )
    case 'backup':
      return (
        <>
          <path d="M5 8a7 7 0 1 1 0 8" />
          <path d="M5 4v4h4" />
          <path d="M12 9v4l3 2" />
        </>
      )
    case 'upgrade':
      return (
        <>
          <path d="M12 20V10" />
          <path d="m8.5 13.5 3.5-3.5 3.5 3.5" />
          <path d="M5 5h14" />
        </>
      )
    case 'history':
      return (
        <>
          <path d="M4 12a8 8 0 1 0 2.3-5.66" />
          <path d="M4 5v5h5" />
          <path d="M12 8v5l3 2" />
        </>
      )
    case 'integration':
      return (
        <>
          <path d="M5 12h14" />
          <path d="M8 7h8" />
          <path d="M8 17h8" />
          <path d="M5 12a2 2 0 1 0 0 .01" />
          <path d="M19 12a2 2 0 1 0 0 .01" />
        </>
      )
  }
}

function SidebarRailGlyph({ collapsed }: { collapsed: boolean }) {
  if (collapsed) {
    return (
      <>
        <path d="M8 6l6 6-6 6" />
        <path d="M4 4v16" />
      </>
    )
  }

  return (
    <>
      <path d="m16 6-6 6 6 6" />
      <path d="M20 4v16" />
    </>
  )
}

export function AppLayout() {
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({})
  const currentRoute = resolveRouteMeta(location.pathname)

  return (
    <div className={`app-shell${sidebarOpen ? ' menu-open' : ''}${sidebarCollapsed ? ' menu-collapsed' : ''}`}>
      <button
        type="button"
        className={`shell-backdrop${sidebarOpen ? ' visible' : ''}`}
        aria-label="关闭导航菜单"
        onClick={() => setSidebarOpen(false)}
      />

      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-topline">
            <span className="sidebar-brand-mark" aria-hidden="true">
              X
            </span>
            <div className="sidebar-brand-text">
              <p className="eyebrow">x-ai-gateway</p>
              <p className="sidebar-brand-title">Admin Console</p>
            </div>
          </div>
          <p className="sidebar-brand-copy">
            统一承载多协议网关的配置、站点、网络与运行观测，收口成可持续扩展的后台工作台。
          </p>
        </div>

        <div className="sidebar-scroll-region">
          <div className="sidebar-section-list">
            {navigationGroups.map((group) => {
              const isExpanded = expandedGroups[group.label] ?? true
              const showItems = sidebarCollapsed || isExpanded

              return (
                <section key={group.label} className={`sidebar-section${isExpanded ? '' : ' collapsed'}`}>
                  <button
                    type="button"
                    className="sidebar-section-toggle"
                    aria-expanded={isExpanded}
                    onClick={() =>
                      setExpandedGroups((current) => ({
                        ...current,
                        [group.label]: !(current[group.label] ?? true),
                      }))
                    }
                  >
                    <span className="sidebar-section-heading">
                      <p className="sidebar-section-title">{group.label}</p>
                      <span className="sidebar-section-count">{String(group.items.length).padStart(2, '0')}</span>
                    </span>
                    <span className="sidebar-section-chevron" aria-hidden="true">
                      {isExpanded ? '−' : '+'}
                    </span>
                  </button>
                  {showItems ? (
                    <nav className="sidebar-submenu" aria-label={`${group.label} 二级菜单`}>
                      {group.items.map((item) => (
                        <NavLink
                          key={item.to}
                          to={item.to}
                          end={item.end}
                          title={`${group.label} / ${item.label}`}
                          aria-label={`${group.label} / ${item.label}`}
                          onClick={() => setSidebarOpen(false)}
                          className={({ isActive }: { isActive: boolean }) =>
                            `sidebar-link${isActive || currentRoute.navTo === item.to ? ' active' : ''}`
                          }
                        >
                          <span className="sidebar-link-topline">
                            <span className="sidebar-link-icon" aria-hidden="true">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                                <NavigationGlyph icon={item.icon} />
                              </svg>
                            </span>
                            <span className="sidebar-link-label">{item.label}</span>
                          </span>
                          <span className="sidebar-link-hint">{item.description}</span>
                        </NavLink>
                      ))}
                    </nav>
                  ) : null}
                </section>
              )
            })}
          </div>
        </div>

        <div className="sidebar-rail-dock">
          <button
            type="button"
            className="sidebar-rail-toggle"
            aria-label={sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'}
            title={sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'}
            onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <SidebarRailGlyph collapsed={sidebarCollapsed} />
            </svg>
            <span className="sidebar-rail-toggle-label">{sidebarCollapsed ? '展开菜单' : '收起菜单'}</span>
          </button>
        </div>
      </aside>

      <div className="workspace">
        <header className="workspace-header">
          <div className="workspace-toolbar">
            <div className="workspace-toolbar-actions">
              <button type="button" className="sidebar-toggle" onClick={() => setSidebarOpen(true)}>
                菜单
              </button>
            </div>
            <p className="workspace-breadcrumb">
              {currentRoute.groupLabel} / {currentRoute.navLabel}
            </p>
            <span className="status-pill workspace-pill">web admin</span>
          </div>

          <div className="workspace-heading">
            <div className="workspace-copy">
              <p className="eyebrow">{currentRoute.groupLabel}</p>
              <h1>{currentRoute.title}</h1>
              <p className="lead">{currentRoute.description}</p>
            </div>
          </div>
        </header>

        <main className="page-shell">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
