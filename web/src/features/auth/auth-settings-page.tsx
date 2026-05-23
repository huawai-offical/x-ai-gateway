import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  LoaderCircleIcon,
  LockKeyholeIcon,
  ShieldCheckIcon,
} from 'lucide-react'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageColumns } from '@/components/app/page-columns'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { formatInstant } from '@/lib/format'
import { isApiError } from '@/lib/api'
import { getAdminAuthSettings, updateAdminAuthSettings } from './api'
import { rememberLastAdminUsername, useAdminAuth } from './auth-provider'
import type { AdminAuthSettings, AdminAuthSettingsUpdatePayload } from './types'

const ADMIN_AUTH_SETTINGS_QUERY_KEY = ['admin-auth-settings']

export function AuthSettingsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { logout, session } = useAdminAuth()
  const settingsQuery = useQuery({
    queryKey: ADMIN_AUTH_SETTINGS_QUERY_KEY,
    queryFn: getAdminAuthSettings,
  })

  const [username, setUsername] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [formHydrated, setFormHydrated] = useState(false)

  useEffect(() => {
    if (!settingsQuery.data || formHydrated) {
      return
    }

    setUsername(settingsQuery.data.username)
    setFormHydrated(true)
  }, [formHydrated, settingsQuery.data])

  const updateMutation = useMutation({
    mutationFn: (payload: AdminAuthSettingsUpdatePayload) => updateAdminAuthSettings(payload),
    meta: {
      suppressSuccessToast: true,
    },
    onSuccess: async (updated: AdminAuthSettings) => {
      rememberLastAdminUsername(updated.username)
      toast.success('控制台凭证已更新，正在要求重新登录。')
      try {
        await queryClient.invalidateQueries({
          queryKey: ADMIN_AUTH_SETTINGS_QUERY_KEY,
        })
        await logout()
      } finally {
        navigate('/login?reason=credentials-updated', { replace: true })
      }
    },
    onError: (error: unknown) => {
      setFormError(normalizeErrorMessage(error, '保存控制台凭证失败，请稍后重试。'))
    },
  })

  const sourceMeta = useMemo(
    () => describeCredentialSource(settingsQuery.data),
    [settingsQuery.data],
  )

  if (settingsQuery.isLoading) {
    return <PageSkeleton count={2} />
  }

  if (settingsQuery.error || !settingsQuery.data) {
    return (
      <InlineError
        error={settingsQuery.error ?? new Error('控制台认证设置不存在。')}
        title="加载控制台认证设置失败"
        onRetry={() => void settingsQuery.refetch()}
      />
    )
  }

  const settings = settingsQuery.data
  const identityItems = [
    {
      key: 'username',
      label: '当前用户名',
      value: settings.username,
    },
    {
      key: 'source',
      label: '凭证来源',
      value: sourceMeta.label,
    },
    {
      key: 'initialized-at',
      label: '初始化时间',
      value: formatInstant(settings.initializedAt),
    },
    {
      key: 'updated-at',
      label: '最近更新时间',
      value: formatInstant(settings.updatedAt),
    },
  ]

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(null)

    const nextUsername = username.trim()
    const hasNewPassword = newPassword.trim().length > 0

    if (!nextUsername) {
      setFormError('请输入新的控制台账号。')
      return
    }
    if (!currentPassword) {
      setFormError('请输入当前密码，以确认本次修改。')
      return
    }
    if (!hasNewPassword && confirmPassword) {
      setFormError('如果填写了确认密码，请同时输入新密码。')
      return
    }
    if (hasNewPassword && newPassword.length < 12) {
      setFormError('新密码长度至少为 12 位。')
      return
    }
    if (hasNewPassword && newPassword !== confirmPassword) {
      setFormError('两次输入的新密码不一致。')
      return
    }
    if (nextUsername === settings.username && !hasNewPassword) {
      setFormError('至少需要修改账号或填写一个新密码。')
      return
    }

    await updateMutation.mutateAsync({
      username: nextUsername,
      currentPassword,
      newPassword: hasNewPassword ? newPassword : undefined,
    })
  }

  return (
    <PageColumns
      rail={
        <>
          <Card className="border-border/60 bg-card/92 shadow-sm">
            <CardHeader className="border-b border-border/60 pb-4">
              <CardTitle className="flex items-center gap-2 text-base">
                <ShieldCheckIcon className="size-4 text-primary" />
                当前认证状态
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4 p-5">
              <div className="flex flex-wrap gap-2">
                <StatusBadge tone={sourceMeta.tone}>{sourceMeta.label}</StatusBadge>
                <StatusBadge tone="info">{settings.persisted ? '已持久化' : '未持久化'}</StatusBadge>
              </div>
              <div className="rounded-2xl border border-border/70 bg-muted/30 p-4 text-sm leading-6 text-muted-foreground">
                当前登录会话：{session?.username ?? settings.username}
                <br />
                会话过期时间：{formatInstant(session?.expiresAt ?? null)}
              </div>
            </CardContent>
          </Card>
        </>
      }
    >
      <PageSection
        kicker="管理控制台"
        title="控制台认证"
      >
        <InfoGrid items={identityItems} />
      </PageSection>

      <PageSection
        title="修改账号 / 密码"
      >
        <div className="flex flex-col gap-5">
          {formError ? (
            <InlineError
              error={new Error(formError)}
              title="本次修改未通过"
            />
          ) : null}

          <form className="grid gap-5 xl:grid-cols-2" onSubmit={handleSubmit}>
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-foreground" htmlFor="admin-auth-username">
                新账号
              </label>
              <Input
                id="admin-auth-username"
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                disabled={updateMutation.isPending}
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-foreground" htmlFor="admin-auth-current-password">
                当前密码
              </label>
              <Input
                id="admin-auth-current-password"
                type="password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                disabled={updateMutation.isPending}
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-foreground" htmlFor="admin-auth-new-password">
                新密码
              </label>
              <Input
                id="admin-auth-new-password"
                type="password"
                autoComplete="new-password"
                placeholder="留空则只修改账号"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                disabled={updateMutation.isPending}
              />
              <div className="text-xs leading-5 text-muted-foreground">
                新密码最少 12 位；如果只想换账号，这里可以留空。
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-foreground" htmlFor="admin-auth-confirm-password">
                确认新密码
              </label>
              <Input
                id="admin-auth-confirm-password"
                type="password"
                autoComplete="new-password"
                placeholder="再次输入新密码"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                disabled={updateMutation.isPending}
              />
            </div>

            <div className="xl:col-span-2 flex flex-wrap items-center gap-3">
              <Button type="submit" size="lg" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? (
                  <LoaderCircleIcon data-icon="inline-start" className="animate-spin" />
                ) : (
                  <LockKeyholeIcon data-icon="inline-start" />
                )}
                {updateMutation.isPending ? '正在保存并刷新登录状态...' : '保存账号 / 密码'}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="lg"
                onClick={() => {
                  setUsername(settings.username)
                  setCurrentPassword('')
                  setNewPassword('')
                  setConfirmPassword('')
                  setFormError(null)
                }}
                disabled={updateMutation.isPending}
              >
                重置表单
              </Button>
            </div>
          </form>
        </div>
      </PageSection>
    </PageColumns>
  )
}

function describeCredentialSource(settings: AdminAuthSettings | undefined) {
  switch (settings?.credentialSource) {
    case 'RANDOM_BOOTSTRAP':
      return {
        label: '首启随机密码',
        description: '系统首次启动时自动生成随机密码并写入数据库。',
        longDescription:
          '当前凭证来自首次启动时的随机初始化。原始密码只会在启动控制台输出一次，之后所有登录都读取持久化哈希，不会因为服务重启而变化。',
        tone: 'warning' as const,
      }
    case 'CONFIG_PASSWORD':
      return {
        label: '配置密码落库',
        description: '使用启动配置中的明文密码作为初始值后再哈希持久化。',
        longDescription:
          '系统第一次启动时读取 `gateway.admin-console.password`，再将其编码后写入 `system_setting`。之后即使重启，运行时仍以持久化后的账号密码为准。',
        tone: 'info' as const,
      }
    case 'CONFIG_PASSWORD_HASH':
      return {
        label: '配置哈希落库',
        description: '使用启动配置中的已编码密码哈希作为初始值。',
        longDescription:
          '系统第一次启动时直接接收已经带编码前缀的密码哈希并写入持久化存储，适合通过环境变量注入现成的安全哈希。',
        tone: 'info' as const,
      }
    case 'MANUAL_UPDATE':
      return {
        label: '手动轮换',
        description: '账号或密码已经被管理员通过控制台页面修改。',
        longDescription:
          '当前账号或密码已经被人工轮换过。后续服务重启不会影响这里的值，除非再次进入本页面执行修改。',
        tone: 'success' as const,
      }
    default:
      return {
        label: '持久化凭证',
        description: '当前运行时正在使用数据库中的凭证记录。',
        longDescription:
          '控制台登录已经基于持久化凭证工作。若需确认初始化来源，可查看服务启动日志或直接在本页面重新轮换账号与密码。',
        tone: 'neutral' as const,
      }
  }
}

function normalizeErrorMessage(error: unknown, fallback: string) {
  if (isApiError(error)) {
    return error.message
  }
  return error instanceof Error ? error.message : fallback
}
