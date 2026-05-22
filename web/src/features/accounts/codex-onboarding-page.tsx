import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowUpRightIcon, CheckCircle2Icon, ShieldCheckIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import { useTypedQuery } from '@/lib/typed-react-query'

type AccountGroup = {
  id: number
  groupName: string
  providerType: string
  supportedModels?: string[]
  supportedProtocols?: string[]
  allowedClientFamilies?: string[]
  totalAccountCount?: number
  active: boolean
}

type DistributedKey = {
  id: number
  keyName: string
  maskedKey: string
  active: boolean
  allowedProtocolSuites: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  allowedClientFamilies: string[]
  requireClientFamilyMatch: boolean
  rpmLimit?: number | null
  tpmLimit?: number | null
  lastUsedAt?: string | null
}

type DistributedKeyCreateResponse = {
  record: DistributedKey
  fullKey: string
  oneTimeExportToken?: string | null
  oneTimeExportExpiresAt?: string | null
}

type ClientInstance = {
  id: number
  distributedKeyId: number
  distributedKeyName?: string | null
  maskedKey?: string | null
  instanceId: string
  displayName: string
  clientFamily: string
  workspaceHint?: string | null
  pluginName?: string | null
  pluginVersion?: string | null
  deepLinkScheme?: string | null
  status: string
  lastAuthorizedAt?: string | null
  lastRequestAt?: string | null
  lastRequestId?: string | null
}

type ClientInstanceAuthorization = {
  clientInstanceId: number
  instanceId: string
  clientFamily: string
  grantToken: string
  expiresAt: string
  consumed: boolean
  revoked: boolean
  deepLinkUrl: string
  pluginMessageJson: string
  warning: string
}

type OnboardingPack = {
  keyName: string
  maskedKey: string
  baseUrl: string
  apiBaseUrl: string
  secretPolicy: string
  clientConfigs: Array<{ name: string; clientFamily: string; format: string; content: string }>
  deepLinks: Array<{ label: string; scheme: string; url: string; warning: string }>
  mcpServerConfig?: string | null
  prompts: string[]
  skills: string[]
  smokeTests: string[]
  troubleshooting: string[]
}

type ClientInstanceForm = {
  instanceId: string
  displayName: string
  workspaceHint: string
  pluginName: string
  pluginVersion: string
  deepLinkScheme: string
}

const DEFAULT_MODEL = 'gpt-5.4@low'

export function CodexOnboardingPage() {
  const queryClient = useQueryClient()
  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [selectedKeyId, setSelectedKeyId] = useState('')
  const [selectedClientInstanceId, setSelectedClientInstanceId] = useState('')
  const [providedSecretExportToken, setProvidedSecretExportToken] = useState('')
  const [createdKeySecret, setCreatedKeySecret] = useState<DistributedKeyCreateResponse | null>(null)
  const [authorization, setAuthorization] = useState<ClientInstanceAuthorization | null>(null)
  const [secretDialogOpen, setSecretDialogOpen] = useState(false)
  const [clientForm, setClientForm] = useState<ClientInstanceForm>({
    instanceId: 'codex-cli-default',
    displayName: 'Codex CLI 默认实例',
    workspaceHint: 'default',
    pluginName: 'codex-cli',
    pluginVersion: 'latest',
    deepLinkScheme: 'xag',
  })

  const groupsQuery = useTypedQuery<AccountGroup[]>({
    queryKey: ['codex-onboarding', 'account-groups'],
    queryFn: () => apiRequest<AccountGroup[]>('/admin/account-groups'),
  })
  const keysQuery = useTypedQuery<DistributedKey[]>({
    queryKey: ['codex-onboarding', 'distributed-keys'],
    queryFn: () => apiRequest<DistributedKey[]>('/admin/distributed-keys'),
  })
  const codexGroups = useMemo(
    () => (groupsQuery.data ?? []).filter(isCodexGroup),
    [groupsQuery.data],
  )
  const selectedGroup = useMemo(
    () => codexGroups.find((group) => String(group.id) === selectedGroupId) ?? codexGroups[0] ?? null,
    [codexGroups, selectedGroupId],
  )
  const codexKeys = useMemo(
    () => (keysQuery.data ?? []).filter(isCodexKey),
    [keysQuery.data],
  )
  const effectiveSelectedKeyId = selectedKeyId || (codexKeys[0]?.id ? String(codexKeys[0].id) : '')
  const instancesQuery = useTypedQuery<ClientInstance[]>({
    queryKey: ['codex-onboarding', 'client-instances', effectiveSelectedKeyId],
    queryFn: () => apiRequest<ClientInstance[]>(`/admin/client-instances?distributedKeyId=${effectiveSelectedKeyId}`),
    enabled: Boolean(effectiveSelectedKeyId),
  })
  const selectedKey = useMemo(
    () => codexKeys.find((key) => String(key.id) === effectiveSelectedKeyId) ?? createdKeySecret?.record ?? codexKeys[0] ?? null,
    [codexKeys, createdKeySecret, effectiveSelectedKeyId],
  )
  const selectedClientInstance = useMemo(
    () =>
      (instancesQuery.data ?? []).find((instance) => String(instance.id) === selectedClientInstanceId)
        ?? (instancesQuery.data ?? [])[0]
        ?? null,
    [instancesQuery.data, selectedClientInstanceId],
  )
  const onboardingPackQuery = useTypedQuery<OnboardingPack>({
    queryKey: ['codex-onboarding', 'onboarding-pack', selectedKey?.id],
    queryFn: () =>
      apiRequest<OnboardingPack>(
        `/admin/distributed-keys/${selectedKey?.id}/onboarding-pack?baseUrl=${encodeURIComponent(window.location.origin)}`,
      ),
    enabled: Boolean(selectedKey?.id),
  })

  const createGroupMutation = useMutation({
    mutationFn: () =>
      apiRequest<AccountGroup>('/admin/account-groups', {
        method: 'POST',
        body: JSON.stringify({
          groupName: `codex-group-${Date.now()}`,
          providerType: 'CODEX_OAUTH',
          supportedModels: [DEFAULT_MODEL],
          supportedProtocols: ['openai', 'responses'],
          allowedClientFamilies: ['CODEX'],
          description: 'Codex CLI / App API 官方账号分组，由接入向导创建。',
          active: true,
        }),
      }),
    onSuccess: (group: AccountGroup) => {
      setSelectedGroupId(String(group.id))
      queryClient.invalidateQueries({ queryKey: ['codex-onboarding', 'account-groups'] })
    },
  })

  const createKeyMutation = useMutation({
    mutationFn: async () => {
      const groupId = selectedGroup?.id
      if (!groupId) {
        throw new Error('请先选择或创建 Codex 账号分组。')
      }
      const created = await apiRequest<DistributedKeyCreateResponse>('/admin/distributed-keys', {
        method: 'POST',
        body: JSON.stringify({
          keyName: `codex-cli-${Date.now()}`,
          description: 'Codex CLI 专用访问密钥，由接入向导创建。',
          active: false,
          allowedProtocolSuites: ['openai', 'responses'],
          allowedModels: [DEFAULT_MODEL],
          allowedProviderTypes: ['OPENAI_DIRECT'],
          allowedClientFamilies: ['CODEX'],
          requireClientFamilyMatch: true,
          rpmLimit: 60,
          tpmLimit: 120000,
          concurrencyLimit: 4,
          stickySessionTtlSeconds: 1800,
        }),
      })
      await apiRequest(`/admin/account-groups/${groupId}/bindings`, {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyId: created.record.id,
          providerType: 'OPENAI_DIRECT',
          priority: 10,
          active: true,
        }),
      })
      await apiRequest<DistributedKey>(`/admin/distributed-keys/${created.record.id}/status?active=true`, {
        method: 'POST',
      })
      return created
    },
    onSuccess: (created: DistributedKeyCreateResponse) => {
      setCreatedKeySecret(created)
      setSelectedKeyId(String(created.record.id))
      setSecretDialogOpen(true)
      queryClient.invalidateQueries({ queryKey: ['codex-onboarding', 'distributed-keys'] })
    },
  })

  const registerClientMutation = useMutation({
    mutationFn: () => {
      if (!selectedKey?.id) {
        throw new Error('请先选择或创建访问密钥。')
      }
      return apiRequest<ClientInstance>('/admin/client-instances', {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyId: selectedKey.id,
          clientFamily: 'CODEX',
          instanceId: clientForm.instanceId,
          displayName: clientForm.displayName,
          workspaceHint: clientForm.workspaceHint,
          pluginName: clientForm.pluginName,
          pluginVersion: clientForm.pluginVersion,
          deepLinkScheme: clientForm.deepLinkScheme,
          metadataJson: JSON.stringify({
            onboardingSource: 'codex-onboarding',
            createdFromConsole: true,
          }),
          active: true,
        }),
      })
    },
    onSuccess: (instance: ClientInstance) => {
      setSelectedClientInstanceId(String(instance.id))
      queryClient.invalidateQueries({ queryKey: ['codex-onboarding', 'client-instances'] })
    },
  })

  const issueAuthorizationMutation = useMutation({
    mutationFn: () => {
      const instance = selectedClientInstance
      if (!instance) {
        throw new Error('请先注册或选择客户端实例。')
      }
      const usesNewKeySecret = createdKeySecret != null && selectedKey?.id === createdKeySecret.record.id
      const fullKey = usesNewKeySecret ? createdKeySecret.fullKey : undefined
      const secretExportGrantToken = fullKey ? undefined : providedSecretExportToken.trim()
      if (!fullKey && !secretExportGrantToken) {
        throw new Error('复用已有访问密钥时，需要提供一次性导出令牌才能发行授权。')
      }
      return apiRequest<ClientInstanceAuthorization>(`/admin/client-instances/${instance.id}/authorizations`, {
        method: 'POST',
        body: JSON.stringify({
          format: 'config_toml',
          baseUrl: window.location.origin,
          source: 'CODEX_ONBOARDING',
          ttlSeconds: 600,
          fullKey,
          secretExportGrantToken,
          pluginName: clientForm.pluginName || instance.pluginName,
          pluginVersion: clientForm.pluginVersion || instance.pluginVersion,
        }),
      })
    },
    onSuccess: (result: ClientInstanceAuthorization) => {
      setAuthorization(result)
      queryClient.invalidateQueries({ queryKey: ['codex-onboarding', 'client-instances'] })
    },
  })

  const firstError =
    groupsQuery.error
    ?? keysQuery.error
    ?? instancesQuery.error
    ?? onboardingPackQuery.error
    ?? createGroupMutation.error
    ?? createKeyMutation.error
    ?? registerClientMutation.error
    ?? issueAuthorizationMutation.error

  const steps = [
    {
      label: '账号分组',
      status: selectedGroup ? 'READY' : 'BLOCKED',
      detail: selectedGroup ? `${selectedGroup.groupName} / ${selectedGroup.totalAccountCount ?? 0} 个账号` : '需要一个 Codex 专用账号分组',
    },
    {
      label: '访问密钥',
      status: selectedKey ? 'READY' : 'BLOCKED',
      detail: selectedKey ? selectedKey.maskedKey : '创建或复用 Codex 专用访问密钥',
    },
    {
      label: '客户端实例',
      status: selectedClientInstance ? 'READY' : selectedKey ? 'SKIPPED' : 'BLOCKED',
      detail: selectedClientInstance ? selectedClientInstance.instanceId : '注册后才能生成 Deep Link 授权',
    },
    {
      label: 'Deep Link 授权',
      status: authorization ? 'READY' : selectedClientInstance ? 'SKIPPED' : 'BLOCKED',
      detail: authorization ? `过期时间 ${formatInstant(authorization.expiresAt)}` : '一次性授权，默认 10 分钟有效',
    },
  ]

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="Codex 运营"
        title="Codex 接入向导"
        actions={
          <Button type="button" variant="outline" asChild>
            <Link to="/console/account-groups">
              查看账号分组
              <ArrowUpRightIcon data-icon="inline-end" />
            </Link>
          </Button>
        }
      >
        {firstError ? <InlineError error={firstError} title="Codex 接入向导操作失败" /> : null}
        {groupsQuery.isPending || keysQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : (
          <div className="grid gap-4 lg:grid-cols-4">
            {steps.map((step) => (
              <Card key={step.label} className="border-border/70 bg-card/92">
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between gap-3">
                    <CardTitle className="text-base">{step.label}</CardTitle>
                    <StatusBadge tone={statusTone(step.status)}>{stepStatusLabel(step.status)}</StatusBadge>
                  </div>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">{step.detail}</CardContent>
              </Card>
            ))}
          </div>
        )}
      </PageSection>

      <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
        <PageSection kicker="步骤 1" title="账号分组与官方账号">
          <div className="flex flex-col gap-4">
            {codexGroups.length ? (
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">Codex 账号分组</span>
                <select
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                  value={selectedGroup?.id ? String(selectedGroup.id) : ''}
                  onChange={(event) => setSelectedGroupId(event.target.value)}
                >
                  {codexGroups.map((group) => (
                    <option key={group.id} value={group.id}>
                      {group.groupName} / {group.totalAccountCount ?? 0} 个账号
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <EmptyState title="还没有 Codex 专用账号分组" />
            )}
            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={() => createGroupMutation.mutate()} disabled={createGroupMutation.isPending}>
                创建 Codex 账号分组
              </Button>
              {selectedGroup ? (
                <Button type="button" variant="outline" asChild>
                  <Link to={`/console/account-groups/${selectedGroup.id}`}>
                    导入 auth.json
                    <ArrowUpRightIcon data-icon="inline-end" />
                  </Link>
                </Button>
              ) : null}
            </div>
            {selectedGroup ? (
              <InfoGrid
                columnsClassName="md:grid-cols-2"
                items={[
                  { key: 'group', label: '账号分组', value: selectedGroup.groupName },
                  { key: 'models', label: '模型', value: selectedGroup.supportedModels?.join(', ') || DEFAULT_MODEL },
                  { key: 'protocols', label: '协议', value: selectedGroup.supportedProtocols?.join(', ') || 'openai,responses' },
                  { key: 'families', label: '客户端族', value: selectedGroup.allowedClientFamilies?.join(', ') || 'CODEX' },
                ]}
              />
            ) : null}
          </div>
        </PageSection>

        <PageSection kicker="步骤 2" title="访问密钥与账号分组绑定">
          <div className="flex flex-col gap-4">
            {codexKeys.length ? (
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">Codex 访问密钥</span>
                <select
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                  value={selectedKey?.id ? String(selectedKey.id) : ''}
                  onChange={(event) => setSelectedKeyId(event.target.value)}
                >
                  {codexKeys.map((key) => (
                    <option key={key.id} value={key.id}>
                      {key.keyName} / {key.maskedKey} / {key.active ? '启用' : '停用'}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <EmptyState title="还没有 Codex 专用访问密钥" />
            )}
            <Button type="button" onClick={() => createKeyMutation.mutate()} disabled={createKeyMutation.isPending || !selectedGroup}>
              创建并绑定 Codex 访问密钥
            </Button>
            {selectedKey ? (
              <InfoGrid
                columnsClassName="md:grid-cols-2"
                items={[
                  { key: 'key', label: '访问密钥', value: selectedKey.keyName },
                  { key: 'masked', label: '脱敏值', value: selectedKey.maskedKey },
                  { key: 'families', label: '客户端族', value: selectedKey.allowedClientFamilies.join(', ') || 'CODEX' },
                  { key: 'sticky', label: '状态', value: selectedKey.active ? '启用' : '停用' },
                ]}
              />
            ) : null}
          </div>
        </PageSection>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
        <PageSection kicker="步骤 3" title="客户端实例">
          <div className="flex flex-col gap-4">
            <div className="grid gap-3 md:grid-cols-2">
              <Field label="实例 ID" value={clientForm.instanceId} onChange={(value) => setClientForm({ ...clientForm, instanceId: value })} />
              <Field label="显示名称" value={clientForm.displayName} onChange={(value) => setClientForm({ ...clientForm, displayName: value })} />
              <Field label="工作区提示" value={clientForm.workspaceHint} onChange={(value) => setClientForm({ ...clientForm, workspaceHint: value })} />
              <Field label="Deep Link 协议" value={clientForm.deepLinkScheme} onChange={(value) => setClientForm({ ...clientForm, deepLinkScheme: value })} />
              <Field label="插件名称" value={clientForm.pluginName} onChange={(value) => setClientForm({ ...clientForm, pluginName: value })} />
              <Field label="插件版本" value={clientForm.pluginVersion} onChange={(value) => setClientForm({ ...clientForm, pluginVersion: value })} />
            </div>
            <Button
              type="button"
              onClick={() => registerClientMutation.mutate()}
              disabled={registerClientMutation.isPending || !selectedKey || !clientForm.instanceId.trim()}
            >
              注册客户端实例
            </Button>
            {instancesQuery.data?.length ? (
              <div className="grid gap-3">
                {instancesQuery.data.map((instance) => (
                  <button
                    key={instance.id}
                    type="button"
                    className="rounded-lg border border-border/60 bg-background p-3 text-left text-sm transition-colors hover:bg-muted"
                    onClick={() => setSelectedClientInstanceId(String(instance.id))}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-medium text-foreground">{instance.displayName}</span>
                      <StatusBadge tone={instance.status === 'ACTIVE' ? 'success' : 'warning'}>{clientStatusLabel(instance.status)}</StatusBadge>
                    </div>
                    <div className="mt-1 text-muted-foreground">{instance.instanceId} / {instance.workspaceHint ?? 'default'}</div>
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        </PageSection>

        <PageSection kicker="步骤 4" title="Deep Link 与插件授权">
          <div className="flex flex-col gap-4">
            {!createdKeySecret || selectedKey?.id !== createdKeySecret.record.id ? (
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">一次性导出令牌</span>
                <Input
                  value={providedSecretExportToken}
                  onChange={(event) => setProvidedSecretExportToken(event.target.value)}
                  placeholder="复用已有访问密钥时必填"
                />
              </label>
            ) : null}
            <Button
              type="button"
              onClick={() => issueAuthorizationMutation.mutate()}
              disabled={issueAuthorizationMutation.isPending || !selectedClientInstance}
            >
              发行一次性授权
            </Button>
            {authorization ? (
              <div className="flex flex-col gap-3">
                <InfoGrid
                  columnsClassName="md:grid-cols-2"
                  items={[
                    { key: 'grant', label: '授权令牌', value: authorization.grantToken.slice(0, 16) + '...' },
                    { key: 'expires', label: '过期时间', value: formatInstant(authorization.expiresAt) },
                    { key: 'consumed', label: '是否消费', value: authorization.consumed ? '已消费' : '未消费' },
                    { key: 'revoked', label: '是否撤销', value: authorization.revoked ? '已撤销' : '未撤销' },
                  ]}
                />
                <CodePanel title="Deep Link 链接" code={authorization.deepLinkUrl} />
                <CodePanel title="插件消息" code={authorization.pluginMessageJson} />
              </div>
            ) : (
              <EmptyState title="尚未生成 Deep Link 授权" />
            )}
          </div>
        </PageSection>
      </div>

      <PageSection kicker="步骤 5" title="配置片段与验证">
        {onboardingPackQuery.isPending && selectedKey ? (
          <PageSkeleton count={1} />
        ) : onboardingPackQuery.data ? (
          <div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
            <div className="flex flex-col gap-3">
              <InfoGrid
                columnsClassName="md:grid-cols-2"
                items={[
                  { key: 'apiBase', label: 'API 基地址', value: onboardingPackQuery.data.apiBaseUrl },
                  { key: 'policy', label: 'Secret 策略', value: onboardingPackQuery.data.secretPolicy },
                ]}
              />
              {onboardingPackQuery.data.clientConfigs.slice(0, 2).map((snippet) => (
                <CodePanel key={`${snippet.name}-${snippet.format}`} title={snippet.name} code={snippet.content} />
              ))}
            </div>
            <Card className="border-border/70 bg-card/92">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <ShieldCheckIcon data-icon="inline-start" />
                  验证与排障
                </CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-3">
                {onboardingPackQuery.data.smokeTests.slice(0, 2).map((smoke) => (
                  <CodePanel key={smoke} title="验证命令" code={smoke} />
                ))}
                <div className="flex flex-wrap gap-2">
                  <Button type="button" variant="outline" asChild>
                    <Link to="/console/request-logs">按请求 ID 排查</Link>
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        ) : (
          <EmptyState title="请选择访问密钥" />
        )}
      </PageSection>

      <Dialog open={secretDialogOpen} onOpenChange={setSecretDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>完整访问密钥仅显示一次</DialogTitle>
          </DialogHeader>
          {createdKeySecret ? (
            <div className="flex flex-col gap-4">
              <CodePanel title="完整访问密钥" code={createdKeySecret.fullKey} />
              {createdKeySecret.oneTimeExportToken ? (
                <CodePanel title="一次性导出令牌" code={createdKeySecret.oneTimeExportToken} />
              ) : null}
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <CheckCircle2Icon data-icon="inline-start" />
                仅保存在当前页面内存。
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Field({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <label className="flex flex-col gap-2">
      <span className="text-sm font-medium text-foreground">{label}</span>
      <Input value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  )
}

function isCodexGroup(group: AccountGroup) {
  return group.providerType === 'CODEX_OAUTH' || group.allowedClientFamilies?.includes('CODEX')
}

function isCodexKey(key: DistributedKey) {
  return key.allowedClientFamilies?.includes('CODEX') || key.allowedProtocolSuites?.includes('responses')
}

function statusTone(status: string) {
  if (status === 'READY') return 'success'
  if (status === 'SKIPPED') return 'info'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

function stepStatusLabel(status: string) {
  switch (status) {
    case 'READY':
      return '就绪'
    case 'SKIPPED':
      return '待完成'
    case 'FAILED':
      return '失败'
    default:
      return '待处理'
  }
}

function clientStatusLabel(status: string) {
  switch (status) {
    case 'ACTIVE':
      return '启用'
    case 'INACTIVE':
      return '停用'
    default:
      return status
  }
}
