import { NavLink, Outlet } from 'react-router-dom'

const navItems = [
  { to: '/keys', label: 'Keys' },
  { to: '/account-pools', label: '账号池' },
  { to: '/network/proxies', label: '代理池' },
  { to: '/network/tls-profiles', label: 'TLS 指纹' },
]

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="hero">
        <p className="eyebrow">x-ai-gateway / phase 4 admin</p>
        <div className="hero-copy">
          <span className="status-pill">control plane</span>
          <h1>第二轮后台流程已经进入可操作界面。</h1>
          <p className="lead">
            这一版前端直接对接 `DistributedKey` 策略、账号池 OAuth 和网络治理接口，先把流程跑通，再逐步做细节优化。
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
