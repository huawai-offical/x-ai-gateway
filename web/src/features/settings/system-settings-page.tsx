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
import { apiClient } from '@/lib/api'
import { formatInstant } from '@/lib/format'

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
  updatedAt?: string | null
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

export function SystemSettingsPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<SystemSettingsForm | null>(null)

  const settingsQuery = useQuery({
    queryKey: SYSTEM_SETTINGS_QUERY_KEY,
    queryFn: () => apiClient.get<SystemSettings>('/admin/settings'),
  })

  const saveMutation = useMutation({
    mutationFn: (payload: SystemSettings) =>
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

  const mergedError = settingsQuery.error ?? saveMutation.error ?? resetMutation.error

  const currentForm = form ?? (settingsQuery.data ? toForm(settingsQuery.data) : null)

  const updateForm = (updater: (current: SystemSettingsForm) => SystemSettingsForm) => {
    setForm((previous) => {
      const base = previous ?? (settingsQuery.data ? toForm(settingsQuery.data) : null)
      if (!base) return previous
      return updater(base)
    })
  }

  if (settingsQuery.isPending || !currentForm) {
    return <PageSkeleton count={2} />
  }

  const current = settingsQuery.data
  const payload = toPayload(currentForm)
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
          <MetricCard label="指纹最大 Token 数" value={currentForm.upstreamCache.fingerprintMaxPrefixTokens} />
          <MetricCard label="最近更新时间" value={formatInstant(current?.updatedAt)} />
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
  }
}

function toPayload(form: SystemSettingsForm): SystemSettings {
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
  }
}

function withCurrentOption(options: string[], currentValue: string) {
  if (options.includes(currentValue)) {
    return options
  }
  return [currentValue, ...options]
}
