import { NavLink, Outlet } from 'react-router-dom'

const navItems = [
  { to: '/keys', label: 'Keys' },
  { to: '/account-pools', label: '账号池' },
  { to: '/network/proxies', label: '代理池' },
  { to: '/network/tls-profiles', label: 'TLS 指纹' },
  { to: '/ops', label: 'Ops' },
  { to: '/error-rules', label: '错误规则' },
  { to: '/provider-sites', label: '站点档案' },
  { to: '/capability-matrix', label: '能力矩阵' },
  { to: '/translation-debug', label: '执行解释' },
  { to: '/operations/upgrades', label: '升级回滚' },
]

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="hero">
        <p className="eyebrow">x-ai-gateway / phase 4 admin</p>
        <div className="hero-copy">
          <span className="status-pill">control plane</span>
          <h1>全厂商自动翻译闭环已经开始切换到站点真相源。</h1>
          <p className="lead">
            这一版前端开始承载 `provider site / capability snapshot / translation explainability`，让目录、预检、执行和后台调试共享同一套站点能力视图。
          </p>
        </div>
        <nav className="nav-bar">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="page-shell">
        <Outlet />
      </main>
    </div>
  )
}
