import { type FormEvent, useState } from 'react'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { formatInstant } from '@/lib/format'
import {
  confirmPortalEmailVerification,
  deletePortalPasskey,
  disablePortalTotp,
  enablePortalTotp,
  getPortalProfile,
  getPortalSecurityStatus,
  getPortalSession,
  listPortalOAuthIdentities,
  listPortalOAuthProviders,
  listPortalPasskeys,
  setupPortalTotp,
  startPortalOAuth,
  startPortalEmailVerification,
  unlinkPortalOAuthIdentity,
} from './api'
import { Metric, PortalFrame } from './portal-shell'
import type {
  PortalEmailVerificationStartResponse,
  PortalPasskeyCredential,
  PortalSocialOAuthIdentity,
  PortalSocialOAuthProvider,
  PortalSocialOAuthStartResponse,
} from './types'

export function PortalSecurityPage() {
  const queryClient = useQueryClient()
  const [emailVerificationId, setEmailVerificationId] = useState('')
  const [emailVerificationCode, setEmailVerificationCode] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const [profileQuery, securityQuery, passkeysQuery, identitiesQuery, providersQuery] = useQueries({
    queries: [
      {
        queryKey: ['portal', 'profile'],
        queryFn: getPortalProfile,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
      {
        queryKey: ['portal', 'security-status'],
        queryFn: getPortalSecurityStatus,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
      {
        queryKey: ['portal', 'passkeys'],
        queryFn: listPortalPasskeys,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
      {
        queryKey: ['portal', 'oauth-identities'],
        queryFn: listPortalOAuthIdentities,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
      {
        queryKey: ['portal', 'oauth-providers'],
        queryFn: listPortalOAuthProviders,
        enabled: Boolean(sessionQuery.data?.authenticated),
      },
    ],
  })
  const startEmailMutation = useMutation({
    mutationFn: startPortalEmailVerification,
    onSuccess: (response: PortalEmailVerificationStartResponse) => {
      setEmailVerificationId(response.verificationId)
      setEmailVerificationCode(response.verificationCode)
    },
  })
  const confirmEmailMutation = useMutation({
    mutationFn: () => confirmPortalEmailVerification(emailVerificationId, emailVerificationCode),
    onSuccess: () => invalidateSecurity(queryClient),
  })
  const setupTotpMutation = useMutation({ mutationFn: setupPortalTotp })
  const enableTotpMutation = useMutation({
    mutationFn: () => enablePortalTotp(totpCode),
    onSuccess: () => {
      setTotpCode('')
      invalidateSecurity(queryClient)
    },
  })
  const disableTotpMutation = useMutation({
    mutationFn: () => disablePortalTotp(totpCode),
    onSuccess: () => {
      setTotpCode('')
      invalidateSecurity(queryClient)
    },
  })
  const deletePasskeyMutation = useMutation({
    mutationFn: deletePortalPasskey,
    onSuccess: () => invalidateSecurity(queryClient),
  })
  const unlinkIdentityMutation = useMutation({
    mutationFn: unlinkPortalOAuthIdentity,
    onSuccess: () => invalidateSecurity(queryClient),
  })
  const startOAuthMutation = useMutation({
    mutationFn: (provider: PortalSocialOAuthProvider) => startPortalOAuth(provider.provider, '/portal/security'),
    onSuccess: (response: PortalSocialOAuthStartResponse) => {
      window.location.assign(response.authorizationUrl)
    },
  })

  const handleConfirmEmail = (event: FormEvent) => {
    event.preventDefault()
    if (emailVerificationId.trim() && emailVerificationCode.trim()) {
      confirmEmailMutation.mutate()
    }
  }

  const handleTotpEnable = (event: FormEvent) => {
    event.preventDefault()
    if (totpCode.trim()) {
      enableTotpMutation.mutate()
    }
  }

  if (sessionQuery.isPending) {
    return <PortalFrame><PageSkeleton count={2} /></PortalFrame>
  }
  if (sessionQuery.error) {
    return <PortalFrame><InlineError error={sessionQuery.error} title="门户会话加载失败" /></PortalFrame>
  }
  if (!sessionQuery.data?.authenticated) {
    return <Navigate to="/portal/login" replace />
  }

  const profile = profileQuery.data
  const security = securityQuery.data
  const passkeys = (passkeysQuery.data ?? []) as PortalPasskeyCredential[]
  const identities = (identitiesQuery.data ?? []) as PortalSocialOAuthIdentity[]
  const providers = (providersQuery.data ?? []) as PortalSocialOAuthProvider[]
  const firstError = profileQuery.error ?? securityQuery.error ?? passkeysQuery.error ?? identitiesQuery.error ?? providersQuery.error
  const mutationError = startEmailMutation.error
    ?? confirmEmailMutation.error
    ?? setupTotpMutation.error
    ?? enableTotpMutation.error
    ?? disableTotpMutation.error
    ?? deletePasskeyMutation.error
    ?? startOAuthMutation.error
    ?? unlinkIdentityMutation.error

  return (
    <PortalFrame>
      <div className="grid gap-4 md:grid-cols-4">
        <Metric title="邮箱验证" value={security?.emailVerified ? '已验证' : '未验证'} />
        <Metric title="动态验证码" value={security?.totpEnabled ? '已启用' : '未启用'} />
        <Metric title="Passkey 凭据" value={security?.passkeyCount ?? 0} />
        <Metric title="社交身份" value={identities.length} />
      </div>

      {firstError ? <InlineError error={firstError} title="安全中心加载失败" /> : null}
      {mutationError ? <InlineError error={mutationError} title="安全操作失败" /> : null}

        <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <p className="text-sm font-medium text-primary">安全</p>
          <CardTitle className="text-3xl">安全中心</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 lg:grid-cols-2">
          {profileQuery.isPending || securityQuery.isPending ? (
            <PageSkeleton count={2} />
          ) : (
            <>
              <div className="rounded-2xl border border-border/60 p-4">
                <div className="font-medium text-foreground">{profile?.displayName || profile?.email}</div>
                <div className="mt-1 text-sm text-muted-foreground">{profile?.email}</div>
                <div className="mt-4 grid gap-2 text-sm text-muted-foreground">
                  <div>账号状态：<StatusBadge tone={profile?.active ? 'success' : 'danger'}>{profile?.active ? '启用' : '停用'}</StatusBadge></div>
                  <div>创建时间：{formatInstant(profile?.createdAt) || '暂无'}</div>
                  <div>最近登录：{formatInstant(profile?.lastLoginAt) || '暂无'}</div>
                </div>
              </div>

              <div className="rounded-2xl border border-border/60 p-4">
                <div className="font-medium text-foreground">验证状态</div>
                <div className="mt-4 grid gap-3 text-sm">
                  <SecurityLine label="邮箱" ok={security?.emailVerified === true} detail={formatInstant(security?.emailVerifiedAt) || '未验证'} />
                  <SecurityLine label="动态验证码" ok={security?.totpEnabled === true} detail={formatInstant(security?.totpVerifiedAt) || '未启用'} />
                  <SecurityLine label="Passkey 凭据" ok={security?.passkeyEnabled === true} detail={`${security?.passkeyCount ?? 0} 个凭据`} />
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <section className="grid gap-6 xl:grid-cols-2">
        <Card className="border-border bg-card/95 shadow-lg">
          <CardHeader>
            <CardTitle>邮箱验证</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Button type="button" variant="outline" onClick={() => startEmailMutation.mutate()} disabled={startEmailMutation.isPending}>
              发送验证邮件
            </Button>
            {startEmailMutation.data ? (
              <div className="rounded-2xl border border-primary/20 bg-primary/10 p-4 text-sm text-foreground">
                验证 ID：{startEmailMutation.data.verificationId}
                <br />
                验证码：{startEmailMutation.data.verificationCode}
                <br />
                过期：{formatInstant(startEmailMutation.data.expiresAt) || '暂无'}
              </div>
            ) : null}
            <form className="grid gap-3 md:grid-cols-[1fr_1fr_auto]" onSubmit={handleConfirmEmail}>
              <Input value={emailVerificationId} onChange={(event) => setEmailVerificationId(event.target.value)} placeholder="验证 ID" />
              <Input value={emailVerificationCode} onChange={(event) => setEmailVerificationCode(event.target.value)} placeholder="验证码" />
              <Button type="submit" disabled={confirmEmailMutation.isPending}>确认</Button>
            </form>
          </CardContent>
        </Card>

        <Card className="border-border bg-card/95 shadow-lg">
          <CardHeader>
            <CardTitle>动态验证码</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={() => setupTotpMutation.mutate()} disabled={setupTotpMutation.isPending}>
                生成动态验证码密钥
              </Button>
              <Button type="button" variant="outline" onClick={() => disableTotpMutation.mutate()} disabled={disableTotpMutation.isPending || !totpCode.trim()}>
                禁用动态验证码
              </Button>
            </div>
            {setupTotpMutation.data ? (
              <div className="rounded-2xl border border-primary/20 bg-primary/10 p-4 text-sm text-foreground">
                密钥：<span className="font-mono">{setupTotpMutation.data.secret}</span>
                <br />
                地址：<span className="break-all font-mono text-xs">{setupTotpMutation.data.otpauthUri}</span>
              </div>
            ) : null}
            <form className="grid gap-3 md:grid-cols-[1fr_auto]" onSubmit={handleTotpEnable}>
              <Input value={totpCode} onChange={(event) => setTotpCode(event.target.value)} placeholder="6 位验证码" />
              <Button type="submit" disabled={enableTotpMutation.isPending}>启用</Button>
            </form>
          </CardContent>
        </Card>
      </section>

      <section className="grid gap-6 xl:grid-cols-2">
        <Card className="border-border bg-card/95 shadow-lg">
          <CardHeader>
            <CardTitle>Passkey 凭据</CardTitle>
          </CardHeader>
          <CardContent>
            {passkeysQuery.isPending ? (
              <PageSkeleton count={1} />
            ) : passkeys.length ? (
              <div className="space-y-3">
                {passkeys.map((item: PortalPasskeyCredential) => (
                  <div key={item.id} className="rounded-2xl border border-border/60 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="font-medium text-foreground">{item.credentialName}</div>
                        <div className="mt-1 break-all font-mono text-xs text-muted-foreground">{item.credentialId}</div>
                        <div className="mt-2 text-xs text-muted-foreground">{item.origin} / {item.rpId}</div>
                      </div>
                      <Button type="button" size="sm" variant="outline" onClick={() => deletePasskeyMutation.mutate(item.id)}>
                        删除
                      </Button>
                    </div>
                    <div className="mt-3 text-xs text-muted-foreground">最近使用：{formatInstant(item.lastUsedAt) || '暂无'}</div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState title="暂无 Passkey 凭据" />
            )}
          </CardContent>
        </Card>

        <Card className="border-border bg-card/95 shadow-lg">
          <CardHeader>
            <CardTitle>社交身份</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap gap-2">
              {providers.map((provider: PortalSocialOAuthProvider) => (
                <Button
                  key={provider.provider}
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => startOAuthMutation.mutate(provider)}
                  disabled={startOAuthMutation.isPending}
                >
                  绑定 {provider.displayName}
                </Button>
              ))}
            </div>
            {identitiesQuery.isPending ? (
              <PageSkeleton count={1} />
            ) : identities.length ? (
              <div className="space-y-3">
                {identities.map((identity: PortalSocialOAuthIdentity) => (
                  <OAuthIdentityRow
                    key={identity.id}
                    identity={identity}
                    onUnlink={() => unlinkIdentityMutation.mutate(identity)}
                  />
                ))}
              </div>
            ) : (
              <EmptyState title="暂无绑定社交身份" />
            )}
          </CardContent>
        </Card>
      </section>
    </PortalFrame>
  )
}

function SecurityLine({ label, ok, detail }: { label: string; ok: boolean; detail: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="font-medium text-foreground">{label}</span>
      <span className="flex items-center gap-2 text-muted-foreground">
        <StatusBadge tone={ok ? 'success' : 'warning'}>{ok ? '已启用' : '未完成'}</StatusBadge>
        {detail}
      </span>
    </div>
  )
}

function OAuthIdentityRow({ identity, onUnlink }: { identity: PortalSocialOAuthIdentity; onUnlink: () => void }) {
  return (
    <div className="rounded-2xl border border-border/60 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="font-medium text-foreground">{identity.displayName || identity.email || identity.provider}</div>
          <div className="mt-1 text-sm text-muted-foreground">{identity.provider} / {identity.email ?? identity.externalSubject}</div>
          <div className="mt-2 text-xs text-muted-foreground">最近登录：{formatInstant(identity.lastLoginAt) || '暂无'}</div>
        </div>
        <Button type="button" size="sm" variant="outline" onClick={onUnlink}>解绑</Button>
      </div>
    </div>
  )
}

function invalidateSecurity(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['portal', 'profile'] })
  queryClient.invalidateQueries({ queryKey: ['portal', 'security-status'] })
  queryClient.invalidateQueries({ queryKey: ['portal', 'passkeys'] })
  queryClient.invalidateQueries({ queryKey: ['portal', 'oauth-identities'] })
}
