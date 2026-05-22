import { Outlet, useLocation } from 'react-router-dom'
import { AppShell } from '@/components/app/app-shell'
import { resolveRouteMeta } from './navigation'

export function AppLayout() {
  const location = useLocation()
  const route = resolveRouteMeta(location.pathname)

  return (
    <AppShell route={route}>
      <Outlet />
    </AppShell>
  )
}
