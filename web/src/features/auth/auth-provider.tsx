/* eslint-disable react-refresh/only-export-components */
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Navigate, useLocation } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { AUTH_UNAUTHORIZED_EVENT, isApiError } from '@/lib/api'
import { getAdminSession, logoutAdminConsole } from './api'
import type { AdminSession } from './types'

export const AUTH_LOGIN_NOTICE_STORAGE_KEY = 'x-ai-gateway:login-notice'
export const AUTH_LAST_USERNAME_STORAGE_KEY = 'x-ai-gateway:last-admin-username'

type AdminAuthContextValue = {
  status: 'idle' | 'loading' | 'authenticated' | 'unauthenticated'
  session: AdminSession | null
  isAuthenticated: boolean
  refreshSession: () => Promise<AdminSession | null>
  completeLogin: (session: AdminSession) => void
  logout: () => Promise<void>
}

const AdminAuthContext = createContext<AdminAuthContextValue | null>(null)

export function AdminAuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient()
  const [bootstrapOnMount] = useState(() => shouldBootstrapAdminAuthOnMount())
  const [status, setStatus] = useState<AdminAuthContextValue['status']>(bootstrapOnMount ? 'loading' : 'idle')
  const [session, setSession] = useState<AdminSession | null>(null)

  useEffect(() => {
    if (!bootstrapOnMount) {
      return undefined
    }

    let cancelled = false

    const bootstrapSession = async () => {
      try {
        const nextSession = await getAdminSession()
        if (cancelled) return

        if (nextSession.authenticated) {
          clearAuthLoginNotice()
          rememberLastAdminUsername(nextSession.username)
          setSession(nextSession)
          setStatus('authenticated')
          return
        }

        setSession(null)
        setStatus('unauthenticated')
      } catch (error) {
        if (cancelled) return
        setSession(null)
        setStatus('unauthenticated')
        if (!isApiError(error) || error.status !== 401) {
          console.error('加载控制台会话失败。', error)
        }
      }
    }

    void bootstrapSession()
    return () => {
      cancelled = true
    }
  }, [bootstrapOnMount])

  useEffect(() => {
    const handleUnauthorized = () => {
      writeAuthLoginNotice('expired')
      queryClient.clear()
      setSession(null)
      setStatus('unauthenticated')
    }

    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => {
      window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, handleUnauthorized)
    }
  }, [queryClient])

  const value = useMemo<AdminAuthContextValue>(
    () => ({
      status,
      session,
      isAuthenticated: status === 'authenticated' && Boolean(session?.authenticated),
      refreshSession: async () => {
        setStatus('loading')
        try {
          const nextSession = await getAdminSession()
          if (nextSession.authenticated) {
            clearAuthLoginNotice()
            setSession(nextSession)
            setStatus('authenticated')
            return nextSession
          }
        } catch (error) {
          if (!isApiError(error) || error.status !== 401) {
            console.error('刷新控制台会话失败。', error)
          }
        }

        setSession(null)
        setStatus('unauthenticated')
        return null
      },
      completeLogin: (nextSession) => {
        clearAuthLoginNotice()
        rememberLastAdminUsername(nextSession.username)
        queryClient.clear()
        setSession(nextSession)
        setStatus(nextSession.authenticated ? 'authenticated' : 'unauthenticated')
      },
      logout: async () => {
        try {
          await logoutAdminConsole()
        } finally {
          queryClient.clear()
          setSession(null)
          setStatus('unauthenticated')
        }
      },
    }),
    [queryClient, session, status],
  )

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>
}

export function useAdminAuth() {
  const context = useContext(AdminAuthContext)
  if (!context) {
    throw new Error('useAdminAuth must be used within AdminAuthProvider.')
  }
  return context
}

export function RequireAdminAuth({ children }: PropsWithChildren) {
  const location = useLocation()
  const { status, isAuthenticated, refreshSession } = useAdminAuth()

  useEffect(() => {
    if (status === 'idle') {
      void refreshSession()
    }
  }, [refreshSession, status])

  if (status === 'idle' || status === 'loading') {
    return <AdminAuthLoadingState />
  }

  if (!isAuthenticated) {
    const redirect = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace />
  }

  return <>{children}</>
}

function AdminAuthLoadingState() {
  return (
    <div className="flex min-h-svh items-center justify-center bg-[radial-gradient(circle_at_top_left,_rgba(8,145,178,0.12),_transparent_24%),radial-gradient(circle_at_bottom_right,_rgba(59,130,246,0.14),_transparent_24%),linear-gradient(180deg,_rgba(248,250,252,1),_rgba(241,245,249,1))] px-4">
      <Card className="w-full max-w-lg border-border/70 bg-background/96 shadow-xl backdrop-blur">
        <CardHeader className="border-b border-border/60">
          <CardTitle>正在恢复控制台会话</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4 pt-6">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-10 w-full" />
        </CardContent>
      </Card>
    </div>
  )
}

export function consumeAuthLoginNotice() {
  if (typeof window === 'undefined') {
    return null
  }

  const value = window.sessionStorage.getItem(AUTH_LOGIN_NOTICE_STORAGE_KEY)
  if (value) {
    window.sessionStorage.removeItem(AUTH_LOGIN_NOTICE_STORAGE_KEY)
  }
  return value
}

export function readRememberedAdminUsername() {
  if (typeof window === 'undefined') {
    return ''
  }

  return window.localStorage.getItem(AUTH_LAST_USERNAME_STORAGE_KEY) ?? ''
}

export function rememberLastAdminUsername(value: string | null) {
  if (typeof window === 'undefined') {
    return
  }

  if (!value) {
    window.localStorage.removeItem(AUTH_LAST_USERNAME_STORAGE_KEY)
    return
  }

  window.localStorage.setItem(AUTH_LAST_USERNAME_STORAGE_KEY, value)
}

function writeAuthLoginNotice(value: string) {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.setItem(AUTH_LOGIN_NOTICE_STORAGE_KEY, value)
}

function clearAuthLoginNotice() {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.removeItem(AUTH_LOGIN_NOTICE_STORAGE_KEY)
}

function shouldBootstrapAdminAuthOnMount() {
  if (typeof window === 'undefined') {
    return true
  }
  const path = window.location.pathname
  return path === '/login' || path === '/console' || path.startsWith('/console/')
}
