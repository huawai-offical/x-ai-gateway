import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCwIcon, RotateCcwIcon, SaveIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { InlineError } from '@/components/app/inline-error'
import { MetricCard } from '@/components/app/metric-card'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { Input } from '@/components/ui/input'
import { apiClient } from '@/lib/api'
import { getPortalRegistrationPolicyForAdmin, updatePortalRegistrationPolicyForAdmin } from '@/features/auth/api'
import type { PortalRegistrationPolicy, PortalRegistrationPolicyUpdatePayload } from '@/features/auth/types'

type SystemSettings = {
  upstreamCache: {
    enabled: boolean
    stickyByDistributedKey: boolean
    prefixAffinityEnabled: boolean
    fingerprintAffinityEnabled: boolean
    affinityTtl: string
    fingerprintMaxPrefixTokens: number
    keyPrefix: string
  }
  upstream: {
    sdkTimeoutMs: number
    sdkStreamTimeoutMs: number
    httpTimeoutMs: number
    httpStreamTimeoutMs: number
  }
  socialOAuth: {
    enabled: boolean
    providers: Array<{
      provider: string
      displayName: string
      enabled: boolean
      clientId?: string | null
      clientSecretConfigured: boolean
      scopes: string[]
      configuredForLogin: boolean
    }>
    updatedAt?: string | null
  }
  updatedAt?: string | null
}

type SystemSettingsPayload = Omit<SystemSettings, 'socialOAuth'> & {
  socialOAuth: {
    enabled: boolean
    providers: Array<{
      provider: string
      enabled: boolean
      clientId?: string | null
      clientSecret?: string | null
      clearClientSecret?: boolean
    }>
  }
}

type SystemSettingsForm = {
  upstreamCache: {
    enabled: 'true' | 'false'
    stickyByDistributedKey: 'true' | 'false'
    prefixAffinityEnabled: 'true' | 'false'
    fingerprintAffinityEnabled: 'true' | 'false'
    affinityTtl: string
    fingerprintMaxPrefixTokens: string
    keyPrefix: string
  }
  upstream: {
    sdkTimeoutMs: string
    sdkStreamTimeoutMs: string
    httpTimeoutMs: string
    httpStreamTimeoutMs: string
  }
  socialOAuth: {
    enabled: 'true' | 'false'
    providers: Array<{
      provider: string
      displayName: string
      enabled: 'true' | 'false'
      clientId: string
      clientSecret: string
      clearClientSecret: 'true' | 'false'
      clientSecretConfigured: boolean
      configuredForLogin: boolean
      scopes: string[]
    }>
  }
}

type RegistrationPolicyForm = {
  allowedEmailDomainsText: string
  allowedRegistrationChannels: string[]
  inviteCodeRequired: 'true' | 'false'
  emailVerificationRequiredForKeyCreation: 'true' | 'false'
}

const SYSTEM_SETTINGS_QUERY_KEY = ['system-settings']

const BOOL_OPTIONS = [
  { value: 'true', label: '开启' },
  { value: 'false', label: '关闭' },
] as const

const TTL_OPTIONS = ['PT5M', 'PT10M', 'PT20M', 'PT30M', 'PT1H']
const PREFIX_TOKENS_OPTIONS = ['512', '1024', '2048', '4096', '8192']
const TIMEOUT_OPTIONS = ['30000', '60000', '120000', '180000', '300000', '600000']
const KEY_PREFIX_OPTIONS = ['cache:', 'xai:', 'upstream:']
const REGISTRATION_CHANNEL_OPTIONS = [
  { value: 'PASSWORD', label: '邮箱密码' },
  { value: 'INVITE_CODE', label: '邀请码' },
  { value: 'SOCIAL_OAUTH', label: '社交 OAuth' },
] as const

export function SystemSettingsPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SystemSettingsForm | null>(null)
  const [registrationForm, setRegistrationForm] = useState<RegistrationPolicyForm | null>(null)

  const settingsQuery = useQuery({
    queryKey: SYSTEM_SETTINGS_QUERY_KEY,
    queryFn: () => apiClient.get<SystemSettings>('/admin/settings'),
  })
  const registrationPolicyQuery = useQuery({
    queryKey: ['admin', 'portal-registration-policy'],
    queryFn: getPortalRegistrationPolicyForAdmin,
  })

  const saveMutation = useMutation({
    mutationFn: (payload: SystemSettingsPayload) =>
      apiClient.put<SystemSettings>('/admin/settings', { body: payload }),
    onSuccess: (nextValue: SystemSettings) => {
      queryClient.setQueryData(SYSTEM_SETTINGS_QUERY_KEY, nextValue)
      setForm(toForm(nextValue))
    },
  })

  const resetMutation = useMutation({
    mutationFn: () => apiClient.post<SystemSettings>('/admin/settings/reset'),
    onSuccess: (nextValue: SystemSettings) => {
      queryClient.setQueryData(SYSTEM_SETTINGS_QUERY_KEY, nextValue)
      setForm(toForm(nextValue))
    },
  })
  const saveRegistrationPolicyMutation = useMutation({
    mutationFn: (payload: PortalRegistrationPolicyUpdatePayload) => updatePortalRegistrationPolicyForAdmin({
      allowedEmailDomains: payload.allowedEmailDomains,
      allowedRegistrationChannels: payload.allowedRegistrationChannels,
      inviteCodeRequired: payload.inviteCodeRequired,
      inviteCodes: null,
      emailVerificationRequiredForKeyCreation: payload.emailVerificationRequiredForKeyCreation,
    }),
    onSuccess: (nextValue: PortalRegistrationPolicy) => {
      queryClient.setQueryData(['admin', 'portal-registration-policy'], nextValue)
      setRegistrationForm(toRegistrationForm(nextValue))
    },
  })

  const mergedError = settingsQuery.error ?? registrationPolicyQuery.error ?? saveMutation.error ?? resetMutation.error ?? saveRegistrationPolicyMutation.error

  const currentForm = form ?? (settingsQuery.data ? toForm(settingsQuery.data) : null)
  const currentRegistrationForm = registrationForm ?? (
    registrationPolicyQuery.data ? toRegistrationForm(registrationPolicyQuery.data) : null
  )

  const updateForm = (updater: (current: SystemSettingsForm) => SystemSettingsForm) => {
    setForm((previous) => {
      const base = previous ?? (settingsQuery.data ? toForm(settingsQuery.data) : null)
      if (!base) return previous
      return updater(base)
    })
  }

  const updateSocialOAuthProvider = (
    index: number,
    patch: Partial<SystemSettingsForm['socialOAuth']['providers'][number]>,
  ) => {
    updateForm((prev) => ({
      ...prev,
      socialOAuth: {
        ...prev.socialOAuth,
        providers: prev.socialOAuth.providers.map((provider, providerIndex) => (
          providerIndex === index ? { ...provider, ...patch } : provider
        )),
      },
    }))
  }

  const updateRegistrationForm = (updater: (current: RegistrationPolicyForm) => RegistrationPolicyForm) => {
    setRegistrationForm((previous) => {
      const base = previous ?? (registrationPolicyQuery.data ? toRegistrationForm(registrationPolicyQuery.data) : null)
      if (!base) return previous
      return updater(base)
    })
  }

  if (settingsQuery.isPending || !currentForm) {
    return <PageSkeleton count={2} />
  }

  const payload = toPayload(currentForm)
  const registrationPayload = currentRegistrationForm ? toRegistrationPayload(currentRegistrationForm) : null
  const saveDisabled = saveMutation.isPending || resetMutation.isPending

  const cacheTtlOptions = withCurrentOption(TTL_OPTIONS, currentForm.upstreamCache.affinityTtl)
  const keyPrefixOptions = withCurrentOption(KEY_PREFIX_OPTIONS, currentForm.upstreamCache.keyPrefix)

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="系统设置"
        title="系统运行参数"
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => settingsQuery.refetch()}
              disabled={settingsQuery.isFetching || saveDisabled}
            >
              <RefreshCwIcon data-icon="inline-start" />
              刷新
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => resetMutation.mutate()}
              disabled={saveDisabled}
            >
              <RotateCcwIcon data-icon="inline-start" />
              恢复默认
            </Button>
            <Button
              type="button"
              onClick={() => saveMutation.mutate(payload)}
              disabled={saveDisabled}
            >
              <SaveIcon data-icon="inline-start" />
              保存参数
            </Button>
          </div>
        )}
      >
        {mergedError ? <InlineError error={mergedError} title="系统参数操作失败" /> : null}

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="缓存开关" value={currentForm.upstreamCache.enabled === 'true' ? '开启' : '关闭'} />
          <MetricCard label="前缀亲和" value={currentForm.upstreamCache.prefixAffinityEnabled === 'true' ? '开启' : '关闭'} />
          <MetricCard label="社交 OAuth" value={currentForm.socialOAuth.enabled === 'true' ? '开启' : '关闭'} />
          <MetricCard label="指纹最大 Token 数" value={currentForm.upstreamCache.fingerprintMaxPrefixTokens} />
        </div>
      </PageSection>

      <div className="grid gap-6 xl:grid-cols-2">
        <PageSection kicker="缓存策略" title="上游缓存">
          <Card className="border-border/60 bg-card/92 shadow-sm">
            <CardHeader className="border-b border-border/60">
              <CardTitle className="text-base">缓存行为</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 p-5 md:grid-cols-2">
              <SelectField
                label="总开关"
                value={currentForm.upstreamCache.enabled}
                options={BOOL_OPTIONS}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstreamCache: { ...prev.upstreamCache, enabled: value as 'true' | 'false' },
                }))}
              />
              <SelectField
                label="按分发 Key 粘性"
                value={currentForm.upstreamCache.stickyByDistributedKey}
                options={BOOL_OPTIONS}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstreamCache: { ...prev.upstreamCache, stickyByDistributedKey: value as 'true' | 'false' },
                }))}
              />
              <SelectField
                label="前缀亲和"
                value={currentForm.upstreamCache.prefixAffinityEnabled}
                options={BOOL_OPTIONS}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstreamCache: { ...prev.upstreamCache, prefixAffinityEnabled: value as 'true' | 'false' },
                }))}
              />
              <SelectField
                label="指纹亲和"
                value={currentForm.upstreamCache.fingerprintAffinityEnabled}
                options={BOOL_OPTIONS}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstreamCache: { ...prev.upstreamCache, fingerprintAffinityEnabled: value as 'true' | 'false' },
                }))}
              />
              <SelectField
                label="亲和过期时间（TTL）"
                value={currentForm.upstreamCache.affinityTtl}
                options={cacheTtlOptions.map((option) => ({ value: option, label: option }))}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstreamCache: { ...prev.upstreamCache, affinityTtl: value },
                }))}
              />
              <SelectField
                label="指纹最大前缀 Token 数"
                value={currentForm.upstreamCache.fingerprintMaxPrefixTokens}
                options={PREFIX_TOKENS_OPTIONS.map((option) => ({ value: option, label: option }))}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstreamCache: { ...prev.upstreamCache, fingerprintMaxPrefixTokens: value },
                }))}
              />
              <div className="md:col-span-2">
                <SelectField
                  label="缓存 Key 前缀"
                  value={currentForm.upstreamCache.keyPrefix}
                  options={keyPrefixOptions.map((option) => ({ value: option, label: option }))}
                  onValueChange={(value) => updateForm((prev) => ({
                    ...prev,
                    upstreamCache: { ...prev.upstreamCache, keyPrefix: value },
                  }))}
                />
              </div>
            </CardContent>
          </Card>
        </PageSection>

        <PageSection kicker="上游超时" title="上游运行时">
          <Card className="border-border/60 bg-card/92 shadow-sm">
            <CardHeader className="border-b border-border/60">
              <CardTitle className="text-base">超时参数（毫秒）</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 p-5 md:grid-cols-2">
              <SelectField
                label="SDK 超时时间"
                value={currentForm.upstream.sdkTimeoutMs}
                options={TIMEOUT_OPTIONS.map((option) => ({ value: option, label: option }))}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstream: { ...prev.upstream, sdkTimeoutMs: value },
                }))}
              />
              <SelectField
                label="SDK 流式超时时间"
                value={currentForm.upstream.sdkStreamTimeoutMs}
                options={TIMEOUT_OPTIONS.map((option) => ({ value: option, label: option }))}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstream: { ...prev.upstream, sdkStreamTimeoutMs: value },
                }))}
              />
              <SelectField
                label="HTTP 超时时间"
                value={currentForm.upstream.httpTimeoutMs}
                options={TIMEOUT_OPTIONS.map((option) => ({ value: option, label: option }))}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstream: { ...prev.upstream, httpTimeoutMs: value },
                }))}
              />
              <SelectField
                label="HTTP 流式超时时间"
                value={currentForm.upstream.httpStreamTimeoutMs}
                options={TIMEOUT_OPTIONS.map((option) => ({ value: option, label: option }))}
                onValueChange={(value) => updateForm((prev) => ({
                  ...prev,
                  upstream: { ...prev.upstream, httpStreamTimeoutMs: value },
                }))}
              />
            </CardContent>
          </Card>
        </PageSection>
      </div>

      <PageSection kicker="Portal 身份" title="社交 OAuth 登录">
        <Card className="border-border/60 bg-card/92 shadow-sm">
          <CardHeader className="border-b border-border/60">
            <CardTitle className="text-base">第三方登录配置</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-5 p-5">
            <SelectField
              label="总开关"
              value={currentForm.socialOAuth.enabled}
              options={BOOL_OPTIONS}
              onValueChange={(value) => updateForm((prev) => ({
                ...prev,
                socialOAuth: { ...prev.socialOAuth, enabled: value as 'true' | 'false' },
              }))}
            />
            <div className="grid gap-4 xl:grid-cols-2">
              {currentForm.socialOAuth.providers.map((provider, index) => (
                <div key={provider.provider} className="rounded-lg border border-border/60 p-4">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <div className="font-medium text-foreground">{provider.displayName}</div>
                      <div className="text-xs text-muted-foreground">{provider.provider}</div>
                    </div>
                    <StatusBadge tone={provider.configuredForLogin ? 'success' : 'warning'}>
                      {provider.configuredForLogin ? '可登录' : '未就绪'}
                    </StatusBadge>
                  </div>
                  <div className="mt-4 grid gap-3">
                    <SelectField
                      label="Provider 开关"
                      value={provider.enabled}
                      options={BOOL_OPTIONS}
                      onValueChange={(value) => updateSocialOAuthProvider(index, { enabled: value as 'true' | 'false' })}
                    />
                    <label className="flex min-w-0 flex-col gap-2">
                      <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">Client ID</span>
                      <Input
                        value={provider.clientId}
                        onChange={(event) => updateSocialOAuthProvider(index, { clientId: event.target.value })}
                        placeholder="OAuth Client ID"
                      />
                    </label>
                    <label className="flex min-w-0 flex-col gap-2">
                      <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">Client Secret</span>
                      <Input
                        type="password"
                        value={provider.clientSecret}
                        onChange={(event) => updateSocialOAuthProvider(index, { clientSecret: event.target.value })}
                        placeholder={provider.clientSecretConfigured ? '已配置，留空则保留' : '未配置'}
                      />
                    </label>
                    <SelectField
                      label="清除 Secret"
                      value={provider.clearClientSecret}
                      options={BOOL_OPTIONS}
                      onValueChange={(value) => updateSocialOAuthProvider(index, { clearClientSecret: value as 'true' | 'false' })}
                    />
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </PageSection>

      {currentRegistrationForm ? (
        <PageSection
          kicker="Portal 注册"
          title="注册渠道策略"
          actions={(
            <Button
              type="button"
              onClick={() => registrationPayload && saveRegistrationPolicyMutation.mutate(registrationPayload)}
              disabled={saveRegistrationPolicyMutation.isPending}
            >
              <SaveIcon data-icon="inline-start" />
              保存注册策略
            </Button>
          )}
        >
          <Card className="border-border/60 bg-card/92 shadow-sm">
            <CardContent className="grid gap-5 p-5">
              <div className="grid gap-3 md:grid-cols-3">
                {REGISTRATION_CHANNEL_OPTIONS.map((option) => {
                  const selected = currentRegistrationForm.allowedRegistrationChannels.includes(option.value)
                  return (
                    <Button
                      key={option.value}
                      type="button"
                      variant={selected ? 'default' : 'outline'}
                      onClick={() => updateRegistrationForm((prev) => toggleRegistrationChannel(prev, option.value))}
                    >
                      {option.label}
                    </Button>
                  )
                })}
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <SelectField
                  label="注册必须邀请码"
                  value={currentRegistrationForm.inviteCodeRequired}
                  options={BOOL_OPTIONS}
                  onValueChange={(value) => updateRegistrationForm((prev) => ({ ...prev, inviteCodeRequired: value as 'true' | 'false' }))}
                />
                <SelectField
                  label="创建 Key 前需验证邮箱"
                  value={currentRegistrationForm.emailVerificationRequiredForKeyCreation}
                  options={BOOL_OPTIONS}
                  onValueChange={(value) => updateRegistrationForm((prev) => ({ ...prev, emailVerificationRequiredForKeyCreation: value as 'true' | 'false' }))}
                />
                <label className="flex min-w-0 flex-col gap-2 md:col-span-2">
                  <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">允许邮箱域名</span>
                  <Input
                    value={currentRegistrationForm.allowedEmailDomainsText}
                    onChange={(event) => updateRegistrationForm((prev) => ({ ...prev, allowedEmailDomainsText: event.target.value }))}
                    placeholder="example.com, company.com"
                  />
                </label>
                <div className="flex min-w-0 flex-col gap-2 md:col-span-2">
                  <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">邀请码</span>
                  <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-3 py-2 text-sm">
                    <StatusBadge tone={registrationPolicyQuery.data?.inviteCodesConfigured ? 'success' : 'warning'}>
                      {registrationPolicyQuery.data?.inviteCodesConfigured ? '已有可用库存' : '暂无可用库存'}
                    </StatusBadge>
                    <span className="text-muted-foreground">邀请码库存请在用户域的“邀请码”页面创建、停用和查看核销记录。</span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </PageSection>
      ) : null}

      <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
        <div className="flex flex-wrap items-center gap-3">
          <StatusBadge tone={saveMutation.isPending ? 'warning' : 'info'}>
            {saveMutation.isPending ? '保存中' : '参数已加载'}
          </StatusBadge>
          <StatusBadge tone={resetMutation.isPending ? 'warning' : 'neutral'}>
            {resetMutation.isPending ? '恢复默认中' : '可恢复默认'}
          </StatusBadge>
        </div>
        <div className="mt-2">页面采用下拉选择为主，避免手填引入格式错误。保存后会立即生效并回写到系统配置存储。</div>
      </div>
    </div>
  )
}

function SelectField({
  label,
  value,
  options,
  onValueChange,
}: {
  label: string
  value: string
  options: ReadonlyArray<{ value: string; label: string }>
  onValueChange: (value: string) => void
}) {
  return (
    <label className="flex min-w-0 flex-col gap-2">
      <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{label}</span>
      <Select value={value} onValueChange={onValueChange}>
        <SelectTrigger className="w-full bg-background">
          <SelectValue placeholder={`选择 ${label}`} />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {options.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
    </label>
  )
}

function toForm(settings: SystemSettings): SystemSettingsForm {
  return {
    upstreamCache: {
      enabled: String(settings.upstreamCache.enabled) as 'true' | 'false',
      stickyByDistributedKey: String(settings.upstreamCache.stickyByDistributedKey) as 'true' | 'false',
      prefixAffinityEnabled: String(settings.upstreamCache.prefixAffinityEnabled) as 'true' | 'false',
      fingerprintAffinityEnabled: String(settings.upstreamCache.fingerprintAffinityEnabled) as 'true' | 'false',
      affinityTtl: settings.upstreamCache.affinityTtl,
      fingerprintMaxPrefixTokens: String(settings.upstreamCache.fingerprintMaxPrefixTokens),
      keyPrefix: settings.upstreamCache.keyPrefix,
    },
    upstream: {
      sdkTimeoutMs: String(settings.upstream.sdkTimeoutMs),
      sdkStreamTimeoutMs: String(settings.upstream.sdkStreamTimeoutMs),
      httpTimeoutMs: String(settings.upstream.httpTimeoutMs),
      httpStreamTimeoutMs: String(settings.upstream.httpStreamTimeoutMs),
    },
    socialOAuth: {
      enabled: String(settings.socialOAuth?.enabled ?? false) as 'true' | 'false',
      providers: (settings.socialOAuth?.providers ?? []).map((provider) => ({
        provider: provider.provider,
        displayName: provider.displayName,
        enabled: String(provider.enabled) as 'true' | 'false',
        clientId: provider.clientId ?? '',
        clientSecret: '',
        clearClientSecret: 'false',
        clientSecretConfigured: provider.clientSecretConfigured,
        configuredForLogin: provider.configuredForLogin,
        scopes: provider.scopes ?? [],
      })),
    },
  }
}

function toPayload(form: SystemSettingsForm): SystemSettingsPayload {
  return {
    upstreamCache: {
      enabled: form.upstreamCache.enabled === 'true',
      stickyByDistributedKey: form.upstreamCache.stickyByDistributedKey === 'true',
      prefixAffinityEnabled: form.upstreamCache.prefixAffinityEnabled === 'true',
      fingerprintAffinityEnabled: form.upstreamCache.fingerprintAffinityEnabled === 'true',
      affinityTtl: form.upstreamCache.affinityTtl,
      fingerprintMaxPrefixTokens: Number(form.upstreamCache.fingerprintMaxPrefixTokens),
      keyPrefix: form.upstreamCache.keyPrefix,
    },
    upstream: {
      sdkTimeoutMs: Number(form.upstream.sdkTimeoutMs),
      sdkStreamTimeoutMs: Number(form.upstream.sdkStreamTimeoutMs),
      httpTimeoutMs: Number(form.upstream.httpTimeoutMs),
      httpStreamTimeoutMs: Number(form.upstream.httpStreamTimeoutMs),
    },
    socialOAuth: {
      enabled: form.socialOAuth.enabled === 'true',
      providers: form.socialOAuth.providers.map((provider) => ({
        provider: provider.provider,
        enabled: provider.enabled === 'true',
        clientId: provider.clientId.trim() || null,
        clientSecret: provider.clientSecret.trim() || null,
        clearClientSecret: provider.clearClientSecret === 'true',
      })),
    },
  }
}

function toRegistrationForm(policy: PortalRegistrationPolicy): RegistrationPolicyForm {
  return {
    allowedEmailDomainsText: (policy.allowedEmailDomains ?? []).join(', '),
    allowedRegistrationChannels: policy.allowedRegistrationChannels ?? ['PASSWORD', 'INVITE_CODE'],
    inviteCodeRequired: String(policy.inviteCodeRequired) as 'true' | 'false',
    emailVerificationRequiredForKeyCreation: String(policy.emailVerificationRequiredForKeyCreation) as 'true' | 'false',
  }
}

function toRegistrationPayload(form: RegistrationPolicyForm): PortalRegistrationPolicyUpdatePayload {
  return {
    allowedEmailDomains: splitList(form.allowedEmailDomainsText),
    allowedRegistrationChannels: form.allowedRegistrationChannels,
    inviteCodeRequired: form.inviteCodeRequired === 'true',
    inviteCodes: null,
    emailVerificationRequiredForKeyCreation: form.emailVerificationRequiredForKeyCreation === 'true',
  }
}

function toggleRegistrationChannel(form: RegistrationPolicyForm, channel: string): RegistrationPolicyForm {
  const exists = form.allowedRegistrationChannels.includes(channel)
  return {
    ...form,
    allowedRegistrationChannels: exists
      ? form.allowedRegistrationChannels.filter((value) => value !== channel)
      : [...form.allowedRegistrationChannels, channel],
  }
}

function splitList(value: string) {
  return value
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function withCurrentOption(options: string[], currentValue: string) {
  if (options.includes(currentValue)) {
    return options
  }
  return [currentValue, ...options]
}
