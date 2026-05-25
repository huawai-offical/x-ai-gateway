import { type ChangeEvent, type FormEvent, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowUpRightIcon, ClipboardCheckIcon, PlayCircleIcon, RefreshCwIcon, RotateCcwIcon, ShieldOffIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { CodePanel } from '@/components/app/code-panel'
import { useConfirm } from '@/components/app/confirm-provider'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import { useTypedQuery, useTypedMutation } from '@/lib/typed-react-query'

type AccountGroup = {
  id: number
  groupName: string
  providerType: string
  supportedModels?: string[]
  supportedProtocols?: string[]
  allowedClientFamilies?: string[]
  description?: string | null
  defaultGroup?: boolean
  oauthAccountCount?: number
  apiCredentialCount?: number
  totalAccountCount?: number
  active?: boolean
  createdAt?: string | null
  updatedAt?: string | null
}
type Account = {
  id: number
  accountName: string
  providerType: string
  externalAccountId?: string | null
  supportedModels?: string[]
  active?: boolean
  healthy: boolean
  frozen: boolean
  lastErrorMessage?: string | null
  lastRefreshAt?: string | null
  lastUsedAt?: string | null
  tokenExpiresAt?: string | null
  refreshStatus?: string | null
  refreshFailureCount?: number | null
  nextRefreshAfter?: string | null
  cooldownUntil?: string | null
  quotaRemainingTokens?: number | null
  quotaRemainingRequests?: number | null
  totalRequestCount?: number | null
  successfulRequestCount?: number | null
  failedRequestCount?: number | null
  totalTokenCount?: number | null
  totalCacheHitTokenCount?: number | null
  requestSuccessRate?: number | null
  cacheHitRate?: number | null
}
type AccountModelRefreshResponse = {
  accountId: number
  modelCount: number
  sampleModels: string[]
  refreshedAt?: string | null
}
type Credential = {
  id: number
  credentialName: string
  providerType: string
  active: boolean
  groupName?: string | null
  lastUsedAt?: string | null
}
type AccountDetail = {
  id: number
  groupId?: number | null
  accountName: string
  providerType: string
  externalAccountId?: string | null
  active: boolean
  frozen: boolean
  healthy: boolean
  lastErrorMessage?: string | null
  proxyId?: number | null
  tlsFingerprintProfileId?: number | null
  lastRefreshAt?: string | null
  lastUsedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}
type CredentialDetail = {
  id: number
  credentialName: string
  providerType: string
  baseUrl: string
  authKind: string
  secretFingerprint: string
  credentialMetadata?: Record<string, unknown>
  active: boolean
  cooldownUntil?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  lastErrorAt?: string | null
  lastUsedAt?: string | null
  proxyId?: number | null
  tlsFingerprintProfileId?: number | null
  siteProfileId?: number | null
  groupId?: number | null
  groupName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}
type DistributedKey = {
  id: number
  keyName: string
  keyPrefix?: string | null
  maskedKey?: string | null
  active: boolean
  allowedProtocolSuites?: string[]
  allowedModels?: string[]
  allowedProviderTypes?: string[]
  allowedClientFamilies?: string[]
}

type RuntimeBatchRecoveryItem = {
  accountId: number
  accountName: string
  category: 'safe' | 'blocked' | 'alreadyReady'
  status: string
  reason: string
  recommendedAction: string
  errorSummary: string
  executionStatus?: string | null
  executionError?: string | null
}

type RuntimeBatchRecoveryResult = {
  operation: string
  generatedAt: string
  dryRunOnly: boolean
  executed?: boolean
  refreshQuota?: boolean
  totals: {
    total: number
    safe: number
    blocked: number
    alreadyReady: number
    executed?: number
    failed?: number
    skipped?: number
  }
  items: RuntimeBatchRecoveryItem[]
  auditEventId?: number | null
  auditEventTitle?: string | null
}

type ImportStep = 'file' | 'mapping' | 'submit'
const IMPORT_STEPS: ImportStep[] = ['file', 'mapping', 'submit']

type AccountGroupForm = {
  groupName: string
  providerType: string
  supportedModelsCsv: string
  supportedProtocolsCsv: string
  allowedClientFamiliesCsv: string
  description: string
  active: boolean
}

const PROVIDER_OPTIONS = ['OPENAI_OAUTH', 'CODEX_OAUTH', 'GEMINI_OAUTH', 'CLAUDE_ACCOUNT'] as const
const PROTOCOL_OPTIONS = ['openai', 'responses', 'anthropic', 'gemini'] as const
const CLIENT_FAMILY_OPTIONS = ['GENERIC_OPENAI', 'CODEX', 'GEMINI_CLI', 'CLAUDE_CODE'] as const

const PROVIDER_DEFAULTS: Record<string, Pick<AccountGroupForm, 'supportedProtocolsCsv' | 'allowedClientFamiliesCsv'>> = {
  OPENAI_OAUTH: {
    supportedProtocolsCsv: 'openai,responses',
    allowedClientFamiliesCsv: 'GENERIC_OPENAI',
  },
  CODEX_OAUTH: {
    supportedProtocolsCsv: 'openai,responses',
    allowedClientFamiliesCsv: 'CODEX',
  },
  GEMINI_OAUTH: {
    supportedProtocolsCsv: 'gemini,openai',
    allowedClientFamiliesCsv: 'GEMINI_CLI,GENERIC_OPENAI',
  },
  CLAUDE_ACCOUNT: {
    supportedProtocolsCsv: 'anthropic',
    allowedClientFamiliesCsv: 'CLAUDE_CODE',
  },
}

type AuthJsonImportForm = {
  accountName: string
  externalAccountId: string
  accessToken: string
  refreshToken: string
  metadataJson: string
  active: boolean
  proxyId: string
  tlsFingerprintProfileId: string
  siteProfileId: string
}

type OfficialCodexResponsesSmokeResponse = {
  accountId: number
  status: string
  classification?: 'PASS' | 'FAIL' | 'SKIPPED' | 'UNSUPPORTED' | 'NO_PERMISSION' | 'BUDGET_BLOCKED' | string | null
  skippedReason?: string | null
  model: string
  dryRun: boolean
  routeEligible: boolean
  routeBlockReason?: string | null
  httpStatus?: number | null
  upstreamRequestId?: string | null
  upstreamResponseId?: string | null
  durationMs?: number | null
  failureType?: string | null
  failureMessage?: string | null
  checkedAt?: string | null
  message?: string | null
  recordReplayFixture?: {
    schemaVersion?: string | null
    replayMode?: string | null
    replayPolicy?: {
      network?: string | null
      billableOperations?: string | null
      writeOperations?: string | null
      secretMaterial?: string | null
      fixtureSource?: string | null
      dryRunEvidenceAccepted?: boolean | null
      liveExecutionRequiresDryRunFalse?: boolean | null
      liveExecutionRequiresRouteEligible?: boolean | null
      liveExecutionRequiresBudgetAvailable?: boolean | null
    } | null
  } | null
}

type OfficialAccountQuotaResponse = {
  accountId: number
  groupId?: number | null
  accountName: string
  externalAccountId?: string | null
  quotaStatus?: string | null
  refreshStatus?: string | null
  routeEligible: boolean
  routeBlockReason?: string | null
  lastRefreshResultJson?: string | null
}

type ImportResultSummary = {
  accountId: number
  accountName: string
  externalAccountId?: string | null
  status: string
  routeEligible?: boolean | null
  routeBlockReason?: string | null
}

const DEFAULT_CODEX_RUNTIME_MODEL = 'gpt-5.4@low'

export function AccountGroupDetailPage() {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [distributedKeyId, setDistributedKeyId] = useState('')
  const [bindingProviderType, setBindingProviderType] = useState('OPENAI_DIRECT')
  const [editOpen, setEditOpen] = useState(false)
  const [editForm, setEditForm] = useState<AccountGroupForm>(createEmptyGroupForm())
  const [editError, setEditError] = useState<string | null>(null)
  const [modelPickerOpen, setModelPickerOpen] = useState(false)
  const [modelKeyword, setModelKeyword] = useState('')
  const [importOpen, setImportOpen] = useState(false)
  const [importStep, setImportStep] = useState<ImportStep>('file')
  const [importError, setImportError] = useState<string | null>(null)
  const [importForm, setImportForm] = useState<AuthJsonImportForm>(createEmptyImportForm())
  const [lastImportResult, setLastImportResult] = useState<ImportResultSummary | null>(null)
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null)
  const [selectedCredentialId, setSelectedCredentialId] = useState<number | null>(null)
  const [runtimeSmokeByAccountId, setRuntimeSmokeByAccountId] = useState<Record<number, OfficialCodexResponsesSmokeResponse>>({})
  const [batchPreflightOpen, setBatchPreflightOpen] = useState(false)
  const [batchRecoveryResult, setBatchRecoveryResult] = useState<RuntimeBatchRecoveryResult | null>(null)
  const confirm = useConfirm()
  const groupQuery = useTypedQuery<AccountGroup>({
    queryKey: ['account-group', id],
    queryFn: () => apiRequest<AccountGroup>(`/admin/account-groups/${id}`),
    enabled: Boolean(id),
  })
  const accountsQuery = useTypedQuery<Account[]>({
    queryKey: ['accounts', id],
    queryFn: () => apiRequest<Account[]>(`/admin/accounts/group/${id}`),
    enabled: Boolean(id),
  })
  const credentialsQuery = useTypedQuery<Credential[]>({
    queryKey: ['credentials', 'group', id],
    queryFn: () => apiRequest<Credential[]>(`/admin/credentials/group/${id}`),
    enabled: Boolean(id),
  })
  const distributedKeysQuery = useTypedQuery<DistributedKey[]>({
    queryKey: ['distributed-keys'],
    queryFn: () => apiRequest<DistributedKey[]>('/admin/distributed-keys'),
  })
  const accountDetailQuery = useTypedQuery<AccountDetail>({
    queryKey: ['account', selectedAccountId],
    queryFn: () => apiRequest<AccountDetail>(`/admin/accounts/${selectedAccountId}`),
    enabled: selectedAccountId != null,
  })
  const credentialDetailQuery = useTypedQuery<CredentialDetail>({
    queryKey: ['credential', selectedCredentialId],
    queryFn: () => apiRequest<CredentialDetail>(`/admin/credentials/${selectedCredentialId}`),
    enabled: selectedCredentialId != null,
  })
  const modelCatalogQuery = useTypedQuery<string[]>({
    queryKey: ['account-groups', 'model-catalog', 'detail', editForm.providerType],
    queryFn: () =>
      apiRequest<string[]>(
        `/admin/account-groups/model-catalog?providerType=${encodeURIComponent(editForm.providerType)}`,
      ),
    enabled: editOpen,
  })
  const updateGroupMutation = useTypedMutation<AccountGroup, { id: number; payload: ReturnType<typeof buildAccountGroupPayload> }>({
    mutationFn: ({ id, payload }: { id: number; payload: ReturnType<typeof buildAccountGroupPayload> }) =>
      apiRequest<AccountGroup>(`/admin/account-groups/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      setEditOpen(false)
      setEditError(null)
      queryClient.invalidateQueries({ queryKey: ['account-group', id] })
      queryClient.invalidateQueries({ queryKey: ['account-groups'] })
    },
  })
  const toggleGroupMutation = useTypedMutation<AccountGroup, { id: number; active: boolean }>({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      apiRequest<AccountGroup>(`/admin/account-groups/${id}/status?active=${active}`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account-group', id] })
      queryClient.invalidateQueries({ queryKey: ['account-groups'] })
    },
  })
  const deleteGroupMutation = useTypedMutation<void, number>({
    mutationFn: (groupId: number) =>
      apiRequest<void>(`/admin/account-groups/${groupId}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account-groups'] })
      navigate('/console/account-groups')
    },
  })
  const bindMutation = useTypedMutation<unknown, void>({
    mutationFn: () =>
      apiRequest(`/admin/account-groups/${id}/bindings`, {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyId: Number(distributedKeyId),
          providerType: bindingProviderType,
        }),
      }),
    onSuccess: () => setDistributedKeyId(''),
  })
  const freezeMutation = useTypedMutation<Account, { accountId: number; frozen: boolean }>({
    mutationFn: ({ accountId, frozen }: { accountId: number; frozen: boolean }) =>
      apiRequest<Account>(`/admin/accounts/${accountId}/freeze?frozen=${frozen}`, {
        method: 'POST',
      }),
    onSuccess: () => invalidateRuntimeQueries(queryClient, id, selectedAccountId),
  })
  const resetRuntimeMutation = useTypedMutation<Account, number>({
    mutationFn: (accountId: number) =>
      apiRequest<Account>(`/admin/accounts/${accountId}/runtime-reset`, {
        method: 'POST',
      }),
    onSuccess: () => invalidateRuntimeQueries(queryClient, id, selectedAccountId),
  })
  const modelRefreshMutation = useTypedMutation<AccountModelRefreshResponse, number>({
    mutationFn: (accountId: number) =>
      apiRequest<AccountModelRefreshResponse>(`/admin/accounts/${accountId}/refresh-models`, {
        method: 'POST',
      }),
    onSuccess: () => invalidateRuntimeQueries(queryClient, id, selectedAccountId),
  })
  const quotaRefreshMutation = useTypedMutation<unknown, number>({
    mutationFn: (accountId: number) =>
      apiRequest(`/admin/accounts/${accountId}/official/quota-refresh`, {
        method: 'POST',
        body: JSON.stringify({}),
      }),
    onSuccess: () => invalidateRuntimeQueries(queryClient, id, selectedAccountId),
  })
  const smokeMutation = useTypedMutation<OfficialCodexResponsesSmokeResponse, number>({
    mutationFn: (accountId: number) =>
      apiRequest<OfficialCodexResponsesSmokeResponse>(`/admin/accounts/${accountId}/official/codex/responses-smoke`, {
        method: 'POST',
        body: JSON.stringify({
          model: DEFAULT_CODEX_RUNTIME_MODEL,
          input: 'healthcheck',
          dryRun: true,
          timeoutSeconds: 15,
        }),
      }),
    onSuccess: (result: OfficialCodexResponsesSmokeResponse) => {
      setRuntimeSmokeByAccountId((current) => ({ ...current, [result.accountId]: result }))
      invalidateRuntimeQueries(queryClient, id, selectedAccountId)
    },
  })
  const importMutation = useTypedMutation<Account | OfficialAccountQuotaResponse, void>({
    mutationFn: async (): Promise<Account | OfficialAccountQuotaResponse> => {
      if (!groupQuery.data) {
        throw new Error('上游账号组/凭证池信息未就绪，无法导入。')
      }
      if (isCodexGroup(groupQuery.data)) {
        return apiRequest<OfficialAccountQuotaResponse>('/admin/accounts/official/import', {
          method: 'POST',
          body: JSON.stringify(buildOfficialCodexImportPayload(groupQuery.data, importForm)),
        })
      }
      return apiRequest<Account>('/admin/accounts/import-auth-json', {
        method: 'POST',
        body: JSON.stringify(buildAuthJsonImportPayload(groupQuery.data.id, importForm)),
      })
    },
    onSuccess: (result: Account | OfficialAccountQuotaResponse) => {
      if (groupQuery.data) {
        setLastImportResult(normalizeImportResult(result))
      }
      setImportError(null)
      setImportStep('file')
      setImportForm(createEmptyImportForm())
      setImportOpen(false)
      queryClient.invalidateQueries({ queryKey: ['accounts', id] })
      queryClient.invalidateQueries({ queryKey: ['credentials', 'group', id] })
    },
  })
  const batchPreflightMutation = useTypedMutation<RuntimeBatchRecoveryResult, void>({
    mutationFn: () =>
      apiRequest<RuntimeBatchRecoveryResult>(`/admin/account-groups/${id}/codex-runtime/batch-recovery-preflight`, {
          method: 'POST',
          body: JSON.stringify({
            execute: false,
            refreshQuota: false,
          accountIds: (accountsQuery.data ?? [])
            .filter((account) => isCodexRuntimeAccount(account, groupQuery.data))
            .map((account) => account.id),
          reason: 'console-preflight',
        }),
      }),
    onSuccess: (result: RuntimeBatchRecoveryResult) => setBatchRecoveryResult(result),
  })
  const batchRecoveryMutation = useTypedMutation<RuntimeBatchRecoveryResult, void>({
    mutationFn: () =>
      apiRequest<RuntimeBatchRecoveryResult>(`/admin/account-groups/${id}/codex-runtime/batch-recovery`, {
          method: 'POST',
          body: JSON.stringify({
            execute: true,
            refreshQuota: false,
          accountIds: (accountsQuery.data ?? [])
            .filter((account) => isCodexRuntimeAccount(account, groupQuery.data))
            .map((account) => account.id),
          reason: 'console-batch-recovery',
        }),
      }),
    onSuccess: (result: RuntimeBatchRecoveryResult) => {
      setBatchRecoveryResult(result)
      invalidateRuntimeQueries(queryClient, id, selectedAccountId)
    },
  })

  const handleBind = (event: FormEvent) => {
    event.preventDefault()
    if (!distributedKeyId.trim()) return
    bindMutation.mutate()
  }

  const openEditDialog = (group: AccountGroup) => {
    setEditForm(groupToForm(group))
    setEditError(null)
    setModelKeyword('')
    setModelPickerOpen(false)
    setEditOpen(true)
  }

  const handleUpdateGroup = (event: FormEvent) => {
    event.preventDefault()
    if (!group) {
      return
    }
    try {
      setEditError(null)
      updateGroupMutation.mutate({
        id: group.id,
        payload: buildAccountGroupPayload(editForm),
      })
    } catch (error) {
      setEditError(error instanceof Error ? error.message : '无法更新上游账号组/凭证池。')
    }
  }

  const handleDeleteGroup = async () => {
    if (!group) {
      return
    }
    const confirmed = await confirm({
      title: '删除上游账号组/凭证池',
      description: `确认删除“${group.groupName}”吗？删除后会回到上游账号组/凭证池列表。`,
      confirmLabel: '删除',
      destructive: true,
    })
    if (confirmed) {
      deleteGroupMutation.mutate(group.id)
    }
  }

  const handleImportSubmit = (event: FormEvent) => {
    event.preventDefault()
    try {
      setImportError(null)
      importMutation.mutate()
    } catch (error) {
      setImportError(error instanceof Error ? error.message : '导入 auth.json 失败。')
    }
  }

  const handleAuthJsonFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file || !groupQuery.data) {
      return
    }

    try {
      const text = await file.text()
      const parsed = parseAuthJsonImport(text, groupQuery.data)
      setImportError(null)
      setImportForm(parsed)
      setImportStep('mapping')
    } catch (error) {
      setImportError(error instanceof Error ? error.message : 'auth.json 解析失败。')
    } finally {
      event.target.value = ''
    }
  }

  const group = groupQuery.data
  const isCodexRuntimeGroup = isCodexGroup(group)
  const editProtocols = useMemo(
    () => parseCsv(editForm.supportedProtocolsCsv),
    [editForm.supportedProtocolsCsv],
  )
  const editClientFamilies = useMemo(
    () => parseCsv(editForm.allowedClientFamiliesCsv),
    [editForm.allowedClientFamiliesCsv],
  )
  const editModels = useMemo(
    () => parseCsv(editForm.supportedModelsCsv),
    [editForm.supportedModelsCsv],
  )
  const modelOptions = useMemo(
    () => mergeModelOptions(modelCatalogQuery.data ?? [], editModels),
    [editModels, modelCatalogQuery.data],
  )
  const filteredModelOptions = useMemo(() => {
    const keyword = modelKeyword.trim().toLowerCase()
    if (!keyword) {
      return modelOptions
    }
    return modelOptions.filter((model) => model.toLowerCase().includes(keyword))
  }, [modelKeyword, modelOptions])
  const setEditProtocols = (nextValues: string[]) => {
    setEditForm((current) => ({ ...current, supportedProtocolsCsv: toCsv(nextValues) }))
  }
  const setEditClientFamilies = (nextValues: string[]) => {
    setEditForm((current) => ({ ...current, allowedClientFamiliesCsv: toCsv(nextValues) }))
  }
  const setEditModels = (nextValues: string[]) => {
    setEditForm((current) => ({ ...current, supportedModelsCsv: toCsv(nextValues) }))
  }
  const runtimeAccounts = useMemo(
    () => (accountsQuery.data ?? []).filter((account: Account) => isCodexRuntimeAccount(account, group)),
    [accountsQuery.data, group],
  )
  const distributedKeyOptions = useMemo(
    () => (distributedKeysQuery.data ?? []).filter((key: DistributedKey) =>
      isDistributedKeyCompatibleWithGroup(key, group, credentialsQuery.data ?? [])),
    [credentialsQuery.data, distributedKeysQuery.data, group],
  )
  const selectedBindingKey = distributedKeyOptions.find((key) => String(key.id) === distributedKeyId)
  const bindingProviderOptions = useMemo(
    () => providerOptionsForDistributedKeyBinding(selectedBindingKey ? [selectedBindingKey] : distributedKeyOptions, group, credentialsQuery.data ?? []),
    [credentialsQuery.data, distributedKeyOptions, group, selectedBindingKey],
  )
  useEffect(() => {
    if (!bindingProviderOptions.includes(bindingProviderType)) {
      setBindingProviderType(bindingProviderOptions[0] ?? resolveRouteProviderType(group?.providerType))
    }
  }, [bindingProviderOptions, bindingProviderType, group?.providerType])
  const runtimeBatchPreflight = useMemo(
    () => buildRuntimeBatchPreflight(runtimeAccounts),
    [runtimeAccounts],
  )
  const runtimeBatchResult = batchRecoveryResult ?? runtimeBatchPreflight
  const importStepIndex = IMPORT_STEPS.indexOf(importStep)
  const canPrev = importStepIndex > 0
  const canNext = importStepIndex < IMPORT_STEPS.length - 1
  const groupItems = useMemo(
    () =>
      group
        ? [
      { key: 'provider', label: '提供方类型', value: group.providerType },
      { key: 'group-id', label: '上游账号组/凭证池 ID', value: group.id },
      { key: 'group-default', label: '系统默认分组', value: group.defaultGroup ? '是' : '否' },
      { key: 'group-active', label: '启用状态', value: group.active === false ? '停用' : '启用' },
      { key: 'group-accounts', label: '账号数', value: formatCount(group.oauthAccountCount ?? accountsQuery.data?.length ?? 0) },
      { key: 'group-api', label: 'API Key 账号数', value: formatCount(group.apiCredentialCount ?? credentialsQuery.data?.length ?? 0) },
      { key: 'group-total', label: '总账号数', value: formatCount(group.totalAccountCount ?? ((group.oauthAccountCount ?? accountsQuery.data?.length ?? 0) + (group.apiCredentialCount ?? credentialsQuery.data?.length ?? 0))) },
      { key: 'created-at', label: '创建时间', value: formatInstant(group.createdAt) },
      { key: 'updated-at', label: '更新时间', value: formatInstant(group.updatedAt) },
      { key: 'description', label: '说明', value: group.description ?? '无' },
          ]
        : [],
    [accountsQuery.data?.length, credentialsQuery.data?.length, group],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="上游账号组/凭证池详情"
        title={group?.groupName ?? '上游账号组/凭证池'}
        actions={
          group ? (
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={() => openEditDialog(group)}>
                编辑上游账号组/凭证池
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => toggleGroupMutation.mutate({ id: group.id, active: !(group.active ?? true) })}
                disabled={toggleGroupMutation.isPending}
              >
                {group.active === false ? '启用上游账号组/凭证池' : '停用上游账号组/凭证池'}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setImportOpen(true)
                  setImportStep('file')
                  setImportError(null)
                  setImportForm(createEmptyImportForm())
                }}
              >
                导入 auth.json 接入
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => void handleDeleteGroup()}
                disabled={deleteGroupMutation.isPending || group.defaultGroup}
              >
                删除上游账号组/凭证池
              </Button>
            </div>
          ) : null
        }
      >
        {groupQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : groupQuery.error ? (
          <InlineError error={groupQuery.error} title="上游账号组/凭证池详情加载失败" />
        ) : group ? (
          <div className="flex flex-col gap-6">
            <InfoGrid items={groupItems} columnsClassName="md:grid-cols-2 xl:grid-cols-3" />

            <div className="grid gap-4 lg:grid-cols-3">
              <div className="rounded-xl border border-border/45 bg-muted/10 p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.04)]">
                <div className="text-sm font-semibold text-foreground">支持协议</div>
                <div className="mt-2 flex flex-wrap gap-2">
                  {(group.supportedProtocols?.length ? group.supportedProtocols : ['无']).map((item) => (
                    <StatusBadge key={item} tone="info">{item}</StatusBadge>
                  ))}
                </div>
              </div>
              <div className="rounded-xl border border-border/45 bg-muted/10 p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.04)]">
                <div className="text-sm font-semibold text-foreground">允许客户端</div>
                <div className="mt-2 flex flex-wrap gap-2">
                  {(group.allowedClientFamilies?.length ? group.allowedClientFamilies : ['全部']).map((item) => (
                    <StatusBadge key={item} tone="info">{item}</StatusBadge>
                  ))}
                </div>
              </div>
              <div className="rounded-xl border border-border/45 bg-muted/10 p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.04)]">
                <div className="text-sm font-semibold text-foreground">支持模型</div>
                <div className="mt-2 text-sm text-muted-foreground">
                  {group.supportedModels?.length ? summarizeItems(group.supportedModels, '无', 6) : '无'}
                </div>
              </div>
            </div>

            {bindMutation.error ? <InlineError error={bindMutation.error} title="绑定分布式 Key 失败" /> : null}
            {distributedKeysQuery.error ? <InlineError error={distributedKeysQuery.error} title="分布式 Key 列表加载失败" /> : null}
            {updateGroupMutation.error ? <InlineError error={updateGroupMutation.error} title="上游账号组/凭证池保存失败" /> : null}
            {toggleGroupMutation.error ? <InlineError error={toggleGroupMutation.error} title="上游账号组/凭证池状态更新失败" /> : null}
            {deleteGroupMutation.error ? <InlineError error={deleteGroupMutation.error} title="上游账号组/凭证池删除失败" /> : null}

            <form className="flex flex-col gap-4 rounded-xl border border-border/45 bg-muted/15 p-4 md:flex-row md:items-end" onSubmit={handleBind}>
              <label className="flex min-w-0 flex-1 flex-col gap-2">
                <span className="text-sm font-medium text-foreground">分布式 Key</span>
                <select
                  aria-label="分布式 Key"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={distributedKeyId}
                  onChange={(event) => setDistributedKeyId(event.target.value)}
                  disabled={distributedKeysQuery.isPending || !distributedKeyOptions.length}
                >
                  <option value="">{distributedKeysQuery.isPending ? '正在加载访问 Key...' : '选择要绑定的访问 Key'}</option>
                  {distributedKeyOptions.map((key: DistributedKey) => (
                    <option key={key.id} value={key.id}>
                      {key.keyName} / {key.maskedKey ?? key.keyPrefix ?? `ID ${key.id}`} / {key.allowedClientFamilies?.join(', ') || '通用'}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex min-w-0 flex-1 flex-col gap-2">
                <span className="text-sm font-medium text-foreground">运行时 provider</span>
                <select
                  aria-label="运行时 provider"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={bindingProviderType}
                  onChange={(event) => setBindingProviderType(event.target.value)}
                >
                  {bindingProviderOptions.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
              <Button type="submit" disabled={bindMutation.isPending || !distributedKeyId.trim()}>
                绑定到访问密钥
              </Button>
            </form>

            {lastImportResult ? (
              <div role="status" className="rounded-xl border border-border/45 bg-card/80 p-4">
                <div className="mb-3 flex flex-wrap items-center gap-2">
                  <StatusBadge tone={lastImportResult.routeEligible === false ? 'warning' : 'success'}>
                    {lastImportResult.status}
                  </StatusBadge>
                  <span className="text-sm font-medium text-foreground">auth.json 导入结果已由后端脱敏保存</span>
                </div>
                <InfoGrid
                  columnsClassName="md:grid-cols-2 xl:grid-cols-4"
                  items={[
                    { key: 'import-account-id', label: '账号 ID', value: lastImportResult.accountId },
                    { key: 'import-account-name', label: '账号名称', value: lastImportResult.accountName },
                    { key: 'import-external', label: '外部身份', value: lastImportResult.externalAccountId ?? '后端生成' },
                    {
                      key: 'import-route',
                      label: '路由状态',
                      value: lastImportResult.routeEligible === false ? (lastImportResult.routeBlockReason ?? '不可路由') : '可路由',
                    },
                  ]}
                />
              </div>
            ) : null}
          </div>
        ) : (
          <EmptyState title="未找到上游账号组/凭证池" />
        )}
      </PageSection>

      {isCodexRuntimeGroup ? (
        <PageSection
          kicker="Codex 运行态"
          title="热切换、负载均衡与失败恢复"
          actions={
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setBatchPreflightOpen(true)
                setBatchRecoveryResult(null)
                batchPreflightMutation.mutate()
              }}
              disabled={batchPreflightMutation.isPending}
            >
              <ClipboardCheckIcon data-icon="inline-start" />
              批量恢复预检
            </Button>
          }
        >
          <div className="flex flex-col gap-5">
            {freezeMutation.error ? <InlineError error={freezeMutation.error} title="账号隔离状态更新失败" /> : null}
            {resetRuntimeMutation.error ? <InlineError error={resetRuntimeMutation.error} title="运行态恢复失败" /> : null}
            {modelRefreshMutation.error ? <InlineError error={modelRefreshMutation.error} title="模型刷新失败" /> : null}
            {quotaRefreshMutation.error ? <InlineError error={quotaRefreshMutation.error} title="Quota 刷新失败" /> : null}
            {smokeMutation.error ? <InlineError error={smokeMutation.error} title="Codex dry-run 验证失败" /> : null}
            {batchPreflightMutation.error ? <InlineError error={batchPreflightMutation.error} title="批量恢复预检失败" /> : null}
            {batchRecoveryMutation.error ? <InlineError error={batchRecoveryMutation.error} title="批量恢复执行失败" /> : null}

            <InfoGrid
              columnsClassName="md:grid-cols-2 xl:grid-cols-4"
              items={[
                { key: 'runtime-total', label: '候选账号', value: runtimeAccounts.length },
                { key: 'runtime-ready', label: '可路由', value: runtimeAccounts.filter((account: Account) => !account.frozen && account.healthy).length },
                { key: 'runtime-frozen', label: '已隔离', value: runtimeAccounts.filter((account: Account) => account.frozen).length },
                { key: 'runtime-failed', label: '失败账号', value: runtimeAccounts.filter((account: Account) => !account.healthy || (account.refreshFailureCount ?? 0) > 0).length },
              ]}
            />

            {runtimeAccounts.length ? (
              <PaginatedRows items={runtimeAccounts}>
                {({ pageItems }) => (
                  <div className="scrollbar-subtle overflow-x-auto rounded-xl border border-border/45 bg-card/82">
                    <table className="min-w-[1080px] w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号</th>
                      <th className="w-[15%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">运行态</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Quota</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">负载指标</th>
                      <th className="w-[19%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近错误</th>
                      <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">详情</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((account: Account) => {
                      const smokeResult = runtimeSmokeByAccountId[account.id]
                      const smokeClassification = smokeResult?.classification ?? smokeResult?.status
                      const smokeReason =
                        smokeResult?.skippedReason ??
                        (smokeResult && !smokeResult.routeEligible ? smokeResult.routeBlockReason ?? '不可路由' : null)
                      return (
                        <tr key={account.id} className="border-b border-border/40 align-top">
                          <td className="px-4 py-3">
                            <div className="truncate font-medium text-foreground">{account.accountName}</div>
                            <div className="mt-1 truncate text-xs text-muted-foreground">{account.supportedModels?.join(', ') || DEFAULT_CODEX_RUNTIME_MODEL}</div>
                            <div className="mt-2 flex flex-wrap gap-1">
                              <StatusBadge tone="info">Responses</StatusBadge>
                            </div>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex flex-col gap-2">
                              <StatusBadge tone={runtimeStatusTone(account)}>
                                {runtimeStatusLabel(account)}
                              </StatusBadge>
                              <span className="text-xs text-muted-foreground">
                                刷新 {account.refreshStatus ?? 'UNKNOWN'} / 失败 {account.refreshFailureCount ?? 0}
                              </span>
                              {account.cooldownUntil ? (
                                <span className="text-xs text-muted-foreground">冷却至 {formatInstant(account.cooldownUntil)}</span>
                              ) : null}
                            </div>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            <div>剩余 Token {formatOptionalNumber(account.quotaRemainingTokens)}</div>
                            <div>剩余请求 {formatOptionalNumber(account.quotaRemainingRequests)}</div>
                            <div className="mt-1 text-xs">最近刷新 {formatInstant(account.lastRefreshAt)}</div>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            <div>请求 {formatOptionalNumber(account.totalRequestCount)}</div>
                            <div>成功率 {formatPercent(account.requestSuccessRate)}</div>
                            <div>缓存命中 {formatPercent(account.cacheHitRate)}</div>
                            <div className="mt-1.5 flex items-center gap-1 text-xs font-medium text-primary">
                              <span className="h-1.5 w-1.5 rounded-full bg-primary animate-pulse"></span>
                              健康路由权重: 1.0
                            </div>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            <div className="line-clamp-2">{account.lastErrorMessage ?? '无'}</div>
                            {smokeResult ? (
                              <div className="mt-2 rounded-xl bg-muted/20 px-3 py-2 text-xs flex flex-col gap-2 border border-border/40 shadow-sm transition-all duration-300 hover:shadow-md">
                                <div className="flex items-center justify-between border-b border-border/20 pb-1.5">
                                  <span className="font-semibold text-foreground flex items-center gap-1.5">
                                    <span className={cn(
                                      "h-1.5 w-1.5 rounded-full animate-pulse",
                                      "bg-primary"
                                    )} />
                                    预检结果
                                  </span>
                                  <span className={cn(
                                    "rounded-full border border-border/60 bg-muted/30 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-foreground"
                                  )}>
                                    {smokeClassification} · {smokeResult.status}
                                  </span>
                                </div>
                                <div className="flex flex-col gap-1 text-[11px]">
                                  <div className="flex items-center justify-between">
                                    <span className="text-muted-foreground">路由合格状态</span>
                                    <span className="font-medium text-foreground">
                                      {smokeResult.routeEligible ? '可路由' : '不可路由'}
                                    </span>
                                  </div>
                                  {!smokeResult.routeEligible && smokeResult.routeBlockReason && (
                                    <div className="flex items-center justify-between">
                                      <span className="text-muted-foreground">拦截原由</span>
                                      <span className="font-mono font-medium text-foreground">{smokeResult.routeBlockReason}</span>
                                    </div>
                                  )}
                                  {smokeReason && (
                                    <div className="flex items-center justify-between">
                                      <span className="text-muted-foreground">跳过原因</span>
                                      <span className="font-mono font-medium text-foreground">{smokeReason}</span>
                                    </div>
                                  )}
                                </div>
                                {smokeResult.recordReplayFixture && (
                                  <div className="mt-1 border-t border-border/20 pt-1.5 flex flex-col gap-1 text-[10px]">
                                    <div className="font-semibold text-foreground/80 flex items-center justify-between">
                                      <span>录制与回放</span>
                                      <span className="rounded px-1 font-bold text-foreground bg-muted/30">
                                        网络：{smokeResult.recordReplayFixture.replayPolicy?.network === 'disabled_by_default' ? '关闭' : '开启'}
                                      </span>
                                    </div>
                                    <div className="flex justify-between text-muted-foreground mt-0.5">
                                      <span>回放模式：<span className="font-mono text-foreground/70">{smokeResult.recordReplayFixture.replayMode}</span></span>
                                      <span>密钥材料：<span className="font-mono">{smokeResult.recordReplayFixture.replayPolicy?.secretMaterial ? '已挂载' : '无'}</span></span>
                                    </div>
                                  </div>
                                )}
                              </div>
                            ) : (
                              <div className="mt-2 rounded-xl border border-dashed border-border/45 bg-muted/10 px-3 py-2 text-xs text-muted-foreground">
                                尚未执行预检
                              </div>
                            )}
                          </td>
                          <td className="px-4 py-3">
                            <Button type="button" variant="outline" size="sm" onClick={() => setSelectedAccountId(account.id)}>
                              查看
                            </Button>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            ) : (
              <EmptyState title="当前 Codex 上游账号组/凭证池还没有可观测的运行态账号" />
            )}
          </div>
        </PageSection>
      ) : null}

      <PageSection
        kicker="上游账号组/凭证池成员"
        title="池内账号"
      >
        {accountsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : accountsQuery.error ? (
          <InlineError error={accountsQuery.error} title="账号列表加载失败" />
        ) : accountsQuery.data?.length ? (
          <PaginatedRows items={accountsQuery.data ?? []}>
            {({ pageItems }) => (
              <div className="scrollbar-subtle overflow-x-auto rounded-xl border border-border/45 bg-card/82">
                <table className="min-w-[900px] w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[28%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号名称</th>
                  <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">健康状态</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">冻结状态</th>
                  <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近使用</th>
                  <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((account: Account) => (
                  <tr key={account.id} className="border-b border-border/40">
                    <td className="truncate px-4 py-3 font-medium text-foreground">{account.accountName}</td>
                    <td className="truncate px-4 py-3 text-muted-foreground">{account.providerType}</td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={account.healthy ? 'success' : 'danger'}>
                        {account.healthy ? '健康' : '异常'}
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={account.frozen ? 'warning' : 'info'}>
                        {account.frozen ? '冻结' : '启用'}
                      </StatusBadge>
                    </td>
                    <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(account.lastUsedAt)}</td>
                    <td className="px-4 py-3">
                      <Button type="button" variant="outline" size="sm" onClick={() => setSelectedAccountId(account.id)}>
                        查看详情
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="当前上游账号组/凭证池还没有账号" />
        )}
      </PageSection>

      <PageSection kicker="上游账号组/凭证池成员" title="池内 API Key 账号">
        {credentialsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : credentialsQuery.error ? (
          <InlineError error={credentialsQuery.error} title="API Key 账号列表加载失败" />
        ) : credentialsQuery.data?.length ? (
          <PaginatedRows items={credentialsQuery.data ?? []}>
            {({ pageItems }) => (
              <div className="scrollbar-subtle overflow-x-auto rounded-xl border border-border/45 bg-card/82">
                <table className="min-w-[900px] w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[28%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号名称</th>
                  <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                  <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">上游账号组/凭证池</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近使用</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((credential: Credential) => (
                  <tr key={credential.id} className="border-b border-border/40">
                    <td className="truncate px-4 py-3 font-medium text-foreground">{credential.credentialName}</td>
                    <td className="truncate px-4 py-3 text-muted-foreground">{credential.providerType}</td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={credential.active ? 'success' : 'warning'}>
                        {credential.active ? '启用' : '停用'}
                      </StatusBadge>
                    </td>
                    <td className="truncate px-4 py-3 text-muted-foreground">{credential.groupName ?? '未分组'}</td>
                    <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(credential.lastUsedAt)}</td>
                    <td className="px-4 py-3">
                      <Button type="button" variant="outline" size="sm" onClick={() => setSelectedCredentialId(credential.id)}>
                        查看详情
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="当前上游账号组/凭证池还没有 API Key 账号" />
        )}
      </PageSection>

      <Dialog
        open={editOpen}
        onOpenChange={(open) => {
          setEditOpen(open)
          if (!open) {
            setEditError(null)
            setModelKeyword('')
            setModelPickerOpen(false)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>编辑上游账号组/凭证池</DialogTitle>
            <DialogDescription>更新上游账号与凭证池基础信息和能力范围。</DialogDescription>
          </DialogHeader>

          <form className="flex flex-col gap-4" onSubmit={handleUpdateGroup}>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">上游账号组/凭证池名称</span>
                <Input
                  value={editForm.groupName}
                  onChange={(event) => setEditForm((current) => ({ ...current, groupName: event.target.value }))}
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">提供方类型</span>
                <select
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={editForm.providerType}
                  onChange={(event) => setEditForm((current) => applyProviderDefaults(current, event.target.value))}
                >
                  {PROVIDER_OPTIONS.map((option) => (
                    <option key={option} value={option}>{option}</option>
                  ))}
                </select>
              </label>
            </div>

            <MultiSelectDropdownField
              label="支持协议"
              options={PROTOCOL_OPTIONS}
              selected={editProtocols}
              placeholder="请选择协议（留空表示不限制）"
              onToggle={(value) => setEditProtocols(toggleOption(editProtocols, value))}
              onSelectAll={() => setEditProtocols([...PROTOCOL_OPTIONS])}
              onClearAll={() => setEditProtocols([])}
            />
            <MultiSelectDropdownField
              label="允许客户端"
              options={CLIENT_FAMILY_OPTIONS}
              selected={editClientFamilies}
              placeholder="请选择客户端（留空表示全部）"
              onToggle={(value) => setEditClientFamilies(toggleOption(editClientFamilies, value))}
              onSelectAll={() => setEditClientFamilies([...CLIENT_FAMILY_OPTIONS])}
              onClearAll={() => setEditClientFamilies([])}
            />

            <div className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">支持模型</span>
              <div className="flex flex-wrap items-center gap-2">
                <Button type="button" variant="outline" onClick={() => setModelPickerOpen(true)}>
                  选择模型
                </Button>
                <Button type="button" variant="outline" onClick={() => setEditModels([])} disabled={editModels.length === 0}>
                  清空模型
                </Button>
                <span className="text-sm text-muted-foreground">
                  已选择 {editModels.length} 个：{summarizeItems(editModels, '无', 3)}
                </span>
              </div>
            </div>

            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">说明</span>
              <Textarea
                rows={4}
                value={editForm.description}
                onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))}
              />
            </label>
            <label className="flex items-center gap-3 rounded-xl border border-border/45 bg-muted/14 px-4 py-3">
              <input
                type="checkbox"
                className="size-4 rounded border-border"
                checked={editForm.active}
                onChange={(event) => setEditForm((current) => ({ ...current, active: event.target.checked }))}
              />
              <span className="text-sm font-medium text-foreground">启用上游账号组/凭证池</span>
            </label>

            {(editError || updateGroupMutation.error) ? (
              <InlineError error={updateGroupMutation.error ?? new Error(editError ?? '上游账号组/凭证池保存失败')} title="上游账号组/凭证池保存失败" />
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditOpen(false)}>
                取消
              </Button>
              <Button type="submit" disabled={updateGroupMutation.isPending}>
                保存
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={modelPickerOpen}
        onOpenChange={(open) => {
          setModelPickerOpen(open)
          if (!open) {
            setModelKeyword('')
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>支持模型选择</DialogTitle>
            <DialogDescription>选择此上游账号组/凭证池支持的模型。</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">模型筛选</span>
              <Input
                value={modelKeyword}
                onChange={(event) => setModelKeyword(event.target.value)}
                placeholder="输入关键字，例如 gpt / gemini / claude"
              />
            </label>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setEditModels(Array.from(new Set([...editModels, ...filteredModelOptions])))}>
                全选可见
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={() => {
                const visible = new Set(filteredModelOptions)
                setEditModels(editModels.filter((model) => !visible.has(model)))
              }}>
                清空可见
              </Button>
              <span className="self-center text-sm text-muted-foreground">
                可见 {filteredModelOptions.length} 个，已选 {editModels.length} 个
              </span>
            </div>
            <div className="scrollbar-subtle max-h-80 overflow-auto rounded-xl border border-border/45 bg-muted/10 p-3">
              <div className="grid gap-2 md:grid-cols-2">
                {filteredModelOptions.map((model) => (
                  <label key={model} className="flex items-center gap-3 rounded-xl border border-border/60 bg-background px-3 py-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={editModels.includes(model)}
                      onChange={() => setEditModels(toggleOption(editModels, model))}
                    />
                    <span className="text-sm text-foreground">{model}</span>
                  </label>
                ))}
              </div>
              {filteredModelOptions.length === 0 ? (
                <div className="py-6 text-center text-sm text-muted-foreground">没有匹配模型，可调整筛选关键字。</div>
              ) : null}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setModelPickerOpen(false)}>
              完成
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={batchPreflightOpen} onOpenChange={setBatchPreflightOpen}>
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>{runtimeBatchResult.dryRunOnly ? 'Codex 批量恢复预检' : 'Codex 批量恢复结果'}</DialogTitle>
            <DialogDescription>
              {runtimeBatchResult.dryRunOnly
                ? '当前为 dry-run 预检。'
                : '已返回批量恢复结果。'}
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            {batchPreflightMutation.isPending ? <PageSkeleton count={1} /> : null}
            {batchPreflightMutation.error ? <InlineError error={batchPreflightMutation.error} title="批量恢复预检失败" /> : null}
            {batchRecoveryMutation.error ? <InlineError error={batchRecoveryMutation.error} title="批量恢复执行失败" /> : null}
            <InfoGrid
              columnsClassName="md:grid-cols-2 xl:grid-cols-4"
              items={[
                { key: 'preflight-total', label: '账号总数', value: runtimeBatchResult.totals.total },
                { key: 'preflight-safe', label: '可恢复候选', value: runtimeBatchResult.totals.safe },
                { key: 'preflight-blocked', label: '阻断候选', value: runtimeBatchResult.totals.blocked },
                { key: 'preflight-executed', label: '已执行', value: runtimeBatchResult.totals.executed ?? 0 },
              ]}
            />
            {runtimeBatchResult.items.length ? (
              <PaginatedRows items={runtimeBatchResult.items}>
                {({ pageItems }) => (
                  <div className="scrollbar-subtle overflow-x-auto rounded-xl border border-border/45 bg-card/82">
                    <table className="min-w-[1060px] w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[17%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号</th>
                      <th className="w-[11%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">分类</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">原因</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">建议动作</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">执行</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">错误摘要</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((item) => (
                      <tr key={`${item.category}-${item.accountId}`} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="truncate font-medium text-foreground">{item.accountName}</div>
                          <div className="mt-1 text-xs text-muted-foreground">ID {item.accountId}</div>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={item.category === 'blocked' ? 'danger' : item.category === 'safe' ? 'success' : 'info'}>
                            {item.category}
                          </StatusBadge>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.status}</td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div className="line-clamp-3">{item.reason}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{item.recommendedAction}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={executionTone(item.executionStatus)}>
                            {item.executionStatus ?? 'PREFLIGHT'}
                          </StatusBadge>
                          {item.executionError ? (
                            <div className="mt-1 line-clamp-2 text-xs text-destructive">{item.executionError}</div>
                          ) : null}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div className="line-clamp-3">{item.errorSummary}</div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                    </table>
                  </div>
                )}
              </PaginatedRows>
            ) : (
              <EmptyState title="当前上游账号组/凭证池没有可预检的 Codex 运行态账号" />
            )}
            <CodePanel
              title="runtime-batch-recovery.redacted.json"
              code={JSON.stringify(runtimeBatchResult, null, 2)}
            />
            {runtimeBatchResult.auditEventId ? (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/45 bg-muted/14 px-4 py-3">
                <div>
                  <div className="text-sm font-medium text-foreground">{runtimeBatchResult.auditEventTitle ?? 'Codex Runtime 批量恢复审计事件'}</div>
                  <div className="text-xs text-muted-foreground">事件 ID {runtimeBatchResult.auditEventId}，已按上游账号组/凭证池过滤系统事件。</div>
                </div>
                <Button type="button" variant="outline" asChild>
                  <Link to={batchAuditEventsPath(id)}>
                    查看审计事件
                    <ArrowUpRightIcon data-icon="inline-end" />
                  </Link>
                </Button>
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setBatchPreflightOpen(false)}>
              关闭
            </Button>
            <Button
              type="button"
              onClick={() => batchRecoveryMutation.mutate()}
              disabled={batchRecoveryMutation.isPending || runtimeBatchResult.totals.safe <= 0}
            >
              <RotateCcwIcon data-icon="inline-start" />
              执行批量恢复
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={selectedAccountId != null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedAccountId(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>账号详情</DialogTitle>
            <DialogDescription>查看账号详情。</DialogDescription>
          </DialogHeader>
          {accountDetailQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : accountDetailQuery.error ? (
            <InlineError error={accountDetailQuery.error} title="账号详情加载失败" />
          ) : accountDetailQuery.data ? (
            <div className="flex flex-col gap-4">
              <InfoGrid
                items={[
                  { key: 'id', label: '账号 ID', value: accountDetailQuery.data.id },
                  { key: 'groupId', label: '上游账号组/凭证池 ID', value: accountDetailQuery.data.groupId ?? '未分组' },
                  { key: 'accountName', label: '账号名称', value: accountDetailQuery.data.accountName },
                  { key: 'providerType', label: '提供方', value: accountDetailQuery.data.providerType },
                  { key: 'externalAccountId', label: '外部账号 ID', value: accountDetailQuery.data.externalAccountId ?? '无' },
                  { key: 'active', label: '启用状态', value: accountDetailQuery.data.active ? '启用' : '停用' },
                  { key: 'frozen', label: '冻结状态', value: accountDetailQuery.data.frozen ? '冻结' : '未冻结' },
                  { key: 'healthy', label: '健康状态', value: accountDetailQuery.data.healthy ? '健康' : '异常' },
                  { key: 'lastError', label: '最近错误', value: accountDetailQuery.data.lastErrorMessage ?? '无' },
                  { key: 'proxyId', label: '代理 ID', value: accountDetailQuery.data.proxyId ?? '无' },
                  { key: 'tls', label: 'TLS 画像 ID', value: accountDetailQuery.data.tlsFingerprintProfileId ?? '无' },
                  { key: 'lastRefresh', label: '最近刷新', value: formatInstant(accountDetailQuery.data.lastRefreshAt) },
                  { key: 'lastUsed', label: '最近使用', value: formatInstant(accountDetailQuery.data.lastUsedAt) },
                  { key: 'createdAt', label: '创建时间', value: formatInstant(accountDetailQuery.data.createdAt) },
                  { key: 'updatedAt', label: '更新时间', value: formatInstant(accountDetailQuery.data.updatedAt) },
                ]}
                columnsClassName="md:grid-cols-2"
              />
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    const account = accountDetailQuery.data
                    if (!account) {
                      return
                    }
                    freezeMutation.mutate({ accountId: account.id, frozen: !account.frozen })
                  }}
                  disabled={freezeMutation.isPending}
                >
                  <ShieldOffIcon data-icon="inline-start" />
                  {accountDetailQuery.data.frozen ? '恢复路由' : '隔离账号'}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    const account = accountDetailQuery.data
                    if (!account) {
                      return
                    }
                    resetRuntimeMutation.mutate(account.id)
                  }}
                  disabled={resetRuntimeMutation.isPending}
                >
                  <RotateCcwIcon data-icon="inline-start" />
                  重置运行态
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    const account = accountDetailQuery.data
                    if (!account) {
                      return
                    }
                    modelRefreshMutation.mutate(account.id)
                  }}
                  disabled={modelRefreshMutation.isPending}
                >
                  <RefreshCwIcon data-icon="inline-start" />
                  刷新模型
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    const account = accountDetailQuery.data
                    if (!account) {
                      return
                    }
                    quotaRefreshMutation.mutate(account.id)
                  }}
                  disabled={quotaRefreshMutation.isPending}
                >
                  <RefreshCwIcon data-icon="inline-start" />
                  刷新 quota
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    const account = accountDetailQuery.data
                    if (!account) {
                      return
                    }
                    smokeMutation.mutate(account.id)
                  }}
                  disabled={smokeMutation.isPending}
                >
                  <PlayCircleIcon data-icon="inline-start" />
                  dry-run 验证
                </Button>
              </div>
            </div>
          ) : (
            <EmptyState title="未找到账号详情" />
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={selectedCredentialId != null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedCredentialId(null)
          }
        }}
      >
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>API Key 账号详情</DialogTitle>
            <DialogDescription>查看账号详情。</DialogDescription>
          </DialogHeader>
          {credentialDetailQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : credentialDetailQuery.error ? (
            <InlineError error={credentialDetailQuery.error} title="API Key 账号详情加载失败" />
          ) : credentialDetailQuery.data ? (
            <div className="flex flex-col gap-4">
              <InfoGrid
                items={[
                  { key: 'id', label: '账号 ID', value: credentialDetailQuery.data.id },
                  { key: 'credentialName', label: '账号名称', value: credentialDetailQuery.data.credentialName },
                  { key: 'providerType', label: '提供方', value: credentialDetailQuery.data.providerType },
                  { key: 'authKind', label: '认证类型', value: credentialDetailQuery.data.authKind },
                  { key: 'group', label: '上游账号组/凭证池', value: credentialDetailQuery.data.groupName ?? '未分组' },
                  { key: 'groupId', label: '上游账号组/凭证池 ID', value: credentialDetailQuery.data.groupId ?? '未分组' },
                  { key: 'active', label: '启用状态', value: credentialDetailQuery.data.active ? '启用' : '停用' },
                  { key: 'fingerprint', label: '指纹', value: credentialDetailQuery.data.secretFingerprint },
                  { key: 'baseUrl', label: '基础 URL', value: credentialDetailQuery.data.baseUrl },
                  { key: 'siteProfileId', label: '站点画像 ID', value: credentialDetailQuery.data.siteProfileId ?? '自动绑定' },
                  { key: 'proxyId', label: '代理 ID', value: credentialDetailQuery.data.proxyId ?? '无' },
                  { key: 'tls', label: 'TLS 画像 ID', value: credentialDetailQuery.data.tlsFingerprintProfileId ?? '无' },
                  { key: 'cooldown', label: '冷却截止', value: formatInstant(credentialDetailQuery.data.cooldownUntil) },
                  { key: 'lastErrorCode', label: '最近错误码', value: credentialDetailQuery.data.lastErrorCode ?? '无' },
                  { key: 'lastErrorMessage', label: '最近错误', value: credentialDetailQuery.data.lastErrorMessage ?? '无' },
                  { key: 'lastErrorAt', label: '最近错误时间', value: formatInstant(credentialDetailQuery.data.lastErrorAt) },
                  { key: 'lastUsed', label: '最近使用', value: formatInstant(credentialDetailQuery.data.lastUsedAt) },
                  { key: 'createdAt', label: '创建时间', value: formatInstant(credentialDetailQuery.data.createdAt) },
                  { key: 'updatedAt', label: '更新时间', value: formatInstant(credentialDetailQuery.data.updatedAt) },
                ]}
                columnsClassName="md:grid-cols-2"
              />
              <CodePanel
                title="元数据 JSON"
                code={JSON.stringify(credentialDetailQuery.data.credentialMetadata ?? {}, null, 2)}
              />
            </div>
          ) : (
            <EmptyState title="未找到 API Key 账号详情" />
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={importOpen}
        onOpenChange={(open) => {
          setImportOpen(open)
          if (!open) {
            setImportError(null)
            setImportStep('file')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>导入 auth.json</DialogTitle>
            <DialogDescription>上传并导入 auth.json。</DialogDescription>
          </DialogHeader>

          <form className="flex flex-col gap-4" onSubmit={handleImportSubmit}>
            <Tabs value={importStep} onValueChange={(value) => setImportStep(value as ImportStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="file">1. 上传文件</TabsTrigger>
                <TabsTrigger value="mapping">2. 字段映射</TabsTrigger>
                <TabsTrigger value="submit">3. 提交导入</TabsTrigger>
              </TabsList>

              <TabsContent value="file" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">auth.json 文件</span>
                    <Input type="file" accept=".json,application/json" onChange={handleAuthJsonFileChange} />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="mapping" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">账号名称</span>
                    <Input value={importForm.accountName} onChange={(event) => setImportForm((current) => ({ ...current, accountName: event.target.value }))} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">外部账号 ID</span>
                    <Input value={importForm.externalAccountId} onChange={(event) => setImportForm((current) => ({ ...current, externalAccountId: event.target.value }))} />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">访问令牌</span>
                    <Input value={importForm.accessToken} onChange={(event) => setImportForm((current) => ({ ...current, accessToken: event.target.value }))} placeholder="从 auth.json 自动提取，可按需修改" />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">刷新令牌（可选）</span>
                    <Input value={importForm.refreshToken} onChange={(event) => setImportForm((current) => ({ ...current, refreshToken: event.target.value }))} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">代理 ID（可选）</span>
                    <Input value={importForm.proxyId} onChange={(event) => setImportForm((current) => ({ ...current, proxyId: event.target.value }))} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">TLS 画像 ID（可选）</span>
                    <Input value={importForm.tlsFingerprintProfileId} onChange={(event) => setImportForm((current) => ({ ...current, tlsFingerprintProfileId: event.target.value }))} />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">站点画像 ID（可选）</span>
                    <Input value={importForm.siteProfileId} onChange={(event) => setImportForm((current) => ({ ...current, siteProfileId: event.target.value }))} />
                  </label>
                  <label className="flex items-center gap-3 rounded-xl border border-border/45 bg-muted/14 px-4 py-3 md:col-span-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={importForm.active}
                      onChange={(event) => setImportForm((current) => ({ ...current, active: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">导入后立即启用账号</span>
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="submit" className="pt-3">
                <div className="flex flex-col gap-4">
                  <CodePanel title="导入预览" code={buildImportPreview(importForm)} />
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">metadata JSON（可选）</span>
                    <Textarea rows={6} value={importForm.metadataJson} onChange={(event) => setImportForm((current) => ({ ...current, metadataJson: event.target.value }))} />
                  </label>
                </div>
              </TabsContent>
            </Tabs>

            {(importError || importMutation.error) ? (
              <InlineError error={importMutation.error ?? new Error(importError ?? '导入 auth.json 失败')} title="导入 auth.json 失败" />
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setImportStep(IMPORT_STEPS[Math.max(0, importStepIndex - 1)])} disabled={!canPrev}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={() => setImportStep(IMPORT_STEPS[Math.min(IMPORT_STEPS.length - 1, importStepIndex + 1)])} disabled={!canNext}>
                下一步
              </Button>
              <Button type="submit" disabled={importMutation.isPending || !importForm.accessToken.trim()}>
                导入账号
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function formatCount(value?: number | null) {
  if (value == null || Number.isNaN(value)) {
    return '--'
  }
  return Math.max(0, Math.round(value)).toLocaleString('zh-CN')
}

function summarizeItems(items: string[], fallback: string, maxItems = 2) {
  if (!items.length) {
    return fallback
  }
  if (items.length <= maxItems) {
    return items.join(', ')
  }
  return `${items.slice(0, maxItems).join(', ')} +${items.length - maxItems}`
}

function MultiSelectDropdownField({
  label,
  options,
  selected,
  placeholder,
  onToggle,
  onSelectAll,
  onClearAll,
}: {
  label: string
  options: readonly string[]
  selected: string[]
  placeholder: string
  onToggle: (value: string) => void
  onSelectAll: () => void
  onClearAll: () => void
}) {
  const [open, setOpen] = useState(false)
  const selectionLabel = selected.length
    ? `${selected.length} 项：${summarizeItems(selected, '', 2)}`
    : placeholder
  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-medium text-foreground">{label}</span>
      <div className="rounded-md border border-input bg-background">
        <Button
          type="button"
          variant="ghost"
          className="w-full justify-start rounded-md px-3"
          aria-label={`${label}下拉选择`}
          aria-expanded={open}
          onClick={() => setOpen((current) => !current)}
        >
          {selectionLabel}
        </Button>
        {open ? (
          <div className="flex flex-col gap-2 border-t border-border/60 p-3">
            <div className="flex flex-wrap gap-3 text-xs">
              <button type="button" className="text-primary" onClick={onSelectAll}>全选</button>
              <button type="button" className="text-primary" onClick={onClearAll}>清空</button>
            </div>
            <div className="grid gap-2 md:grid-cols-2">
              {options.map((option) => (
                <label key={option} className="flex items-center gap-3 rounded-lg border border-border/45 bg-muted/10 px-3 py-2">
                  <input
                    type="checkbox"
                    className="size-4 rounded border-border"
                    checked={selected.includes(option)}
                    onChange={() => onToggle(option)}
                  />
                  <span className="text-sm text-foreground">{option}</span>
                </label>
              ))}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}

function createEmptyGroupForm(): AccountGroupForm {
  return {
    groupName: '',
    providerType: 'OPENAI_OAUTH',
    supportedModelsCsv: '',
    supportedProtocolsCsv: 'openai,responses',
    allowedClientFamiliesCsv: 'GENERIC_OPENAI',
    description: '',
    active: true,
  }
}

function groupToForm(group: AccountGroup): AccountGroupForm {
  return {
    groupName: group.groupName,
    providerType: group.providerType,
    supportedModelsCsv: (group.supportedModels ?? []).join(','),
    supportedProtocolsCsv: (group.supportedProtocols ?? []).join(','),
    allowedClientFamiliesCsv: (group.allowedClientFamilies ?? []).join(','),
    description: group.description ?? '',
    active: group.active ?? true,
  }
}

function buildAccountGroupPayload(form: AccountGroupForm) {
  if (!form.groupName.trim()) {
    throw new Error('上游账号组/凭证池名称不能为空。')
  }

  return {
    groupName: form.groupName.trim(),
    providerType: form.providerType,
    supportedModels: parseCsv(form.supportedModelsCsv),
    supportedProtocols: parseCsv(form.supportedProtocolsCsv),
    allowedClientFamilies: parseCsv(form.allowedClientFamiliesCsv),
    description: form.description.trim() || null,
    active: form.active,
  }
}

function parseCsv(value: string) {
  return Array.from(new Set(
    value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean),
  ))
}

function toCsv(values: string[]) {
  return parseCsv(values.join(',')).join(',')
}

function toggleOption(current: string[], nextValue: string) {
  if (current.includes(nextValue)) {
    return current.filter((item) => item !== nextValue)
  }
  return [...current, nextValue]
}

function applyProviderDefaults(current: AccountGroupForm, providerType: string): AccountGroupForm {
  const defaults = PROVIDER_DEFAULTS[providerType]
  if (!defaults) {
    return { ...current, providerType, supportedModelsCsv: '' }
  }
  return {
    ...current,
    providerType,
    supportedModelsCsv: '',
    supportedProtocolsCsv: defaults.supportedProtocolsCsv,
    allowedClientFamiliesCsv: defaults.allowedClientFamiliesCsv,
  }
}

function mergeModelOptions(catalog: string[], selected: string[]) {
  return Array.from(new Set([...catalog, ...selected].map((item) => item.trim()).filter(Boolean)))
    .sort((left, right) => left.localeCompare(right))
}

function createEmptyImportForm(): AuthJsonImportForm {
  return {
    accountName: '',
    externalAccountId: '',
    accessToken: '',
    refreshToken: '',
    metadataJson: '{}',
    active: true,
    proxyId: '',
    tlsFingerprintProfileId: '',
    siteProfileId: '',
  }
}

function invalidateRuntimeQueries(
  queryClient: ReturnType<typeof useQueryClient>,
  groupId: string | undefined,
  accountId: number | null,
) {
  queryClient.invalidateQueries({ queryKey: ['accounts', groupId] })
  if (accountId != null) {
    queryClient.invalidateQueries({ queryKey: ['account', accountId] })
  }
}

function isCodexGroup(group: AccountGroup | null | undefined) {
  if (!group) {
    return false
  }
  return group.providerType === 'CODEX_OAUTH' || group.allowedClientFamilies?.includes('CODEX') === true
}

function isCodexRuntimeAccount(account: Account, group: AccountGroup | null | undefined) {
  return account.providerType === 'CODEX_OAUTH' || isCodexGroup(group)
}

function isDistributedKeyCompatibleWithGroup(key: DistributedKey, group: AccountGroup | null | undefined, credentials: Credential[]) {
  if (!key.active) {
    return false
  }
  if (!group) {
    return true
  }
  const providerTypes = routeProviderCandidates(group, credentials)
  const providerMatches = !key.allowedProviderTypes?.length || key.allowedProviderTypes.some((providerType) => providerTypes.includes(providerType))
  const clientFamilyMatches = !key.allowedClientFamilies?.length || !group.allowedClientFamilies?.length || key.allowedClientFamilies.some((family) => group.allowedClientFamilies?.includes(family))
  return providerMatches && clientFamilyMatches
}

function providerOptionsForDistributedKeyBinding(keys: DistributedKey[], group: AccountGroup | null | undefined, credentials: Credential[]) {
  const allowedByKeys = new Set<string>()
  keys.forEach((key) => {
    key.allowedProviderTypes?.forEach((providerType) => allowedByKeys.add(providerType))
  })
  if (!allowedByKeys.size) {
    return ['OPENAI_DIRECT', 'OPENAI_COMPATIBLE', 'ANTHROPIC_DIRECT', 'GEMINI_DIRECT', 'OLLAMA_DIRECT']
  }
  const providerTypes = routeProviderCandidates(group, credentials)
  const ordered = [
    ...providerTypes,
    'OPENAI_COMPATIBLE',
    'OPENAI_DIRECT',
    'ANTHROPIC_DIRECT',
    'GEMINI_DIRECT',
    'OLLAMA_DIRECT',
  ]
  return Array.from(new Set(ordered.filter((providerType) => allowedByKeys.has(providerType))))
}

function routeProviderCandidates(group: AccountGroup | null | undefined, credentials: Credential[]) {
  const values = new Set<string>()
  credentials.forEach((credential) => {
    if (credential.providerType) {
      values.add(credential.providerType)
    }
  })
  values.add(resolveRouteProviderType(group?.providerType))
  return Array.from(values)
}

function buildRuntimeBatchPreflight(accounts: Account[]): RuntimeBatchRecoveryResult {
  const items = accounts.map((account) => buildRuntimeBatchPreflightItem(account))
  return {
    operation: 'codex-runtime-recovery',
    generatedAt: new Date().toISOString(),
    dryRunOnly: true,
    executed: false,
    refreshQuota: false,
    totals: {
      total: accounts.length,
      safe: items.filter((item) => item.category === 'safe').length,
      blocked: items.filter((item) => item.category === 'blocked').length,
      alreadyReady: items.filter((item) => item.category === 'alreadyReady').length,
      executed: 0,
      failed: 0,
      skipped: 0,
    },
    items,
  }
}

function buildRuntimeBatchPreflightItem(account: Account): RuntimeBatchRecoveryItem {
  const status = runtimeStatusLabel(account)
  const errorSummary = redactRuntimeError(account.lastErrorMessage)
  if (isSecurityBlockedRuntimeError(account.lastErrorMessage)) {
    return {
      accountId: account.id,
      accountName: account.accountName,
      category: 'blocked',
      status,
      reason: '最近错误包含权限、策略、安全或禁用语义，批量恢复前需要人工复核。',
      recommendedAction: '人工核验账号授权、组织策略和 auth.json 来源后再单独处理。',
      errorSummary,
    }
  }

  const recoveryReasons = [
    account.frozen ? '账号已隔离' : null,
    account.cooldownUntil ? `冷却至 ${formatInstant(account.cooldownUntil)}` : null,
    !account.healthy ? '健康状态异常' : null,
    (account.refreshFailureCount ?? 0) > 0 ? `刷新失败 ${account.refreshFailureCount} 次` : null,
    account.refreshStatus === 'FAILED' || account.refreshStatus === 'QUOTA_FAILED' ? `刷新状态 ${account.refreshStatus}` : null,
  ].filter(Boolean)

  if (recoveryReasons.length) {
    return {
      accountId: account.id,
      accountName: account.accountName,
      category: 'safe',
      status,
      reason: recoveryReasons.join('；'),
      recommendedAction: '可按批量恢复策略尝试重置运行态、解除隔离并重新刷新 quota。',
      errorSummary,
    }
  }

  return {
    accountId: account.id,
    accountName: account.accountName,
    category: 'alreadyReady',
    status,
    reason: '账号当前健康、未隔离且未处于冷却。',
    recommendedAction: '无需批量恢复。',
    errorSummary,
  }
}

function isSecurityBlockedRuntimeError(message?: string | null) {
  if (!message) {
    return false
  }
  return /policy|permission|security|forbidden|disabled|revoked|unauthorized|not\s+allowed/i.test(message)
}

function redactRuntimeError(message?: string | null) {
  if (!message?.trim()) {
    return '无'
  }
  return message
    .replace(/(sk-[A-Za-z0-9_-]{8})[A-Za-z0-9_-]+/g, '$1***')
    .replace(/Bearer\s+[A-Za-z0-9._-]+/gi, 'Bearer ***')
    .slice(0, 140)
}

function resolveRouteProviderType(providerType: string | undefined) {
  if (providerType === 'GEMINI_OAUTH' || providerType === 'ANTIGRAVITY_OAUTH') {
    return 'GEMINI_DIRECT'
  }
  if (providerType === 'CLAUDE_ACCOUNT' || providerType === 'CLAUDE_PLAN') {
    return 'ANTHROPIC_DIRECT'
  }
  return 'OPENAI_DIRECT'
}

function batchAuditEventsPath(groupId: string | undefined) {
  const entityRef = `account-group:${groupId ?? ''}`
  return `/console/ops/system-events?eventType=CODEX_RUNTIME_BATCH_RECOVERY&entityRef=${encodeURIComponent(entityRef)}`
}

function runtimeStatusTone(account: Account) {
  if (account.frozen || account.cooldownUntil) return 'warning'
  if (!account.healthy) return 'danger'
  if (account.refreshStatus === 'FAILED' || account.refreshStatus === 'QUOTA_FAILED') return 'danger'
  return 'success'
}

function runtimeStatusLabel(account: Account) {
  if (account.frozen) return '已隔离'
  if (account.cooldownUntil) return '冷却中'
  if (!account.healthy) return '异常'
  if (account.refreshStatus === 'FAILED' || account.refreshStatus === 'QUOTA_FAILED') return '刷新失败'
  return '可路由'
}

function executionTone(status?: string | null) {
  if (status === 'EXECUTED') return 'success' as const
  if (status === 'FAILED') return 'danger' as const
  if (status === 'SKIPPED') return 'warning' as const
  return 'info' as const
}

function formatOptionalNumber(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) {
    return '--'
  }
  return value.toLocaleString('zh-CN')
}

function formatPercent(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) {
    return '--'
  }
  return `${Math.round(value * 100)}%`
}

function parseAuthJsonImport(raw: string, group: AccountGroup): AuthJsonImportForm {
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    throw new Error('auth.json 不是合法的 JSON。')
  }

  if (!isRecord(parsed)) {
    throw new Error('auth.json 根节点必须是 JSON 对象。')
  }

  const candidate = resolveAuthJsonCandidate(parsed, group.providerType)
  const accessToken = pickString(candidate, ['access_token', 'accessToken', 'token', 'session_key', 'sessionKey', 'api_key', 'apiKey'])
  if (!accessToken) {
    throw new Error('未在 auth.json 中找到 access token，请检查字段后重试。')
  }

  const accountName = pickString(candidate, ['account_name', 'accountName', 'name', 'email']) ?? `${group.groupName}-import-${Date.now()}`
  const externalAccountId = pickString(candidate, ['account_id', 'accountId', 'external_account_id', 'externalAccountId', 'sub', 'user_id', 'uid']) ?? `${group.providerType.toLowerCase()}:${Date.now()}`
  const refreshToken = pickString(candidate, ['refresh_token', 'refreshToken']) ?? ''
  const proxyId = pickString(candidate, ['proxy_id', 'proxyId']) ?? ''
  const tlsFingerprintProfileId = pickString(candidate, ['tls_fingerprint_profile_id', 'tlsFingerprintProfileId']) ?? ''
  const siteProfileId = pickString(candidate, ['site_profile_id', 'siteProfileId']) ?? ''

  return {
    accountName,
    externalAccountId,
    accessToken,
    refreshToken,
    metadataJson: JSON.stringify(parsed, null, 2),
    active: true,
    proxyId,
    tlsFingerprintProfileId,
    siteProfileId,
  }
}

function resolveAuthJsonCandidate(raw: Record<string, unknown>, providerType: string) {
  const candidates: Array<Record<string, unknown>> = [raw]

  const pushCandidate = (value: unknown) => {
    if (isRecord(value)) {
      candidates.push(value)
    }
  }

  pushCandidate(raw.auth)
  pushCandidate(raw.oauth)
  pushCandidate(raw.session)
  pushCandidate(raw.tokens)
  pushCandidate(raw.token)
  pushCandidate(raw.openai)
  pushCandidate(raw.codex)
  pushCandidate(raw.codex_oauth)
  pushCandidate(raw.gemini)
  pushCandidate(raw.claude)

  if (Array.isArray(raw.accounts) && raw.accounts.length && isRecord(raw.accounts[0])) {
    candidates.push(raw.accounts[0])
  }

  if (providerType === 'OPENAI_OAUTH') {
    pushCandidate(raw.openai_oauth)
    pushCandidate(raw.openai)
  }
  if (providerType === 'CODEX_OAUTH') {
    pushCandidate(raw.openai_oauth)
    pushCandidate(raw.codex_oauth)
    pushCandidate(raw.codex)
    pushCandidate(raw.openai)
  }
  if (providerType === 'GEMINI_OAUTH') {
    pushCandidate(raw.gemini_oauth)
    pushCandidate(raw.gemini)
  }
  if (providerType === 'CLAUDE_ACCOUNT') {
    pushCandidate(raw.claude_account)
    pushCandidate(raw.claude)
  }

  for (const candidate of candidates) {
    if (pickString(candidate, ['access_token', 'accessToken', 'token', 'session_key', 'sessionKey', 'api_key', 'apiKey'])) {
      return candidate
    }
  }

  return raw
}

function buildAuthJsonImportPayload(groupId: number, form: AuthJsonImportForm) {
  const accessToken = form.accessToken.trim()
  if (!accessToken) {
    throw new Error('accessToken 不能为空。')
  }

  return {
    groupId,
    accountName: form.accountName.trim() || null,
    externalAccountId: form.externalAccountId.trim() || null,
    accessToken,
    refreshToken: form.refreshToken.trim() || null,
    metadataJson: form.metadataJson.trim() || '{}',
    active: form.active,
    proxyId: parseOptionalNumber(form.proxyId),
    tlsFingerprintProfileId: parseOptionalNumber(form.tlsFingerprintProfileId),
    siteProfileId: parseOptionalNumber(form.siteProfileId),
  }
}

function buildOfficialCodexImportPayload(group: AccountGroup, form: AuthJsonImportForm) {
  const genericPayload = buildAuthJsonImportPayload(group.id, form)
  return {
    accountType: 'CODEX',
    groupId: group.id,
    accountName: genericPayload.accountName,
    externalAccountId: genericPayload.externalAccountId,
    accessToken: genericPayload.accessToken,
    refreshToken: genericPayload.refreshToken,
    metadataJson: genericPayload.metadataJson,
    active: genericPayload.active,
    proxyId: genericPayload.proxyId,
    tlsFingerprintProfileId: genericPayload.tlsFingerprintProfileId,
    siteProfileId: genericPayload.siteProfileId,
    supportedModels: group.supportedModels ?? [],
    refreshQuotaAfterImport: true,
  }
}

function normalizeImportResult(result: Account | OfficialAccountQuotaResponse): ImportResultSummary {
  if (isOfficialAccountQuotaResponse(result)) {
    const status = parseImportStatus(result.lastRefreshResultJson) ?? result.quotaStatus ?? result.refreshStatus ?? '导入完成'
    return {
      accountId: result.accountId,
      accountName: result.accountName,
      externalAccountId: result.externalAccountId,
      status,
      routeEligible: result.routeEligible,
      routeBlockReason: result.routeBlockReason,
    }
  }
  const account = result
  return {
    accountId: account.id,
    accountName: account.accountName,
    externalAccountId: account.externalAccountId,
    status: '导入完成',
    routeEligible: account.healthy && !account.frozen,
  }
}

function isOfficialAccountQuotaResponse(result: Account | OfficialAccountQuotaResponse): result is OfficialAccountQuotaResponse {
  return 'accountId' in result
}

function parseImportStatus(lastRefreshResultJson?: string | null) {
  if (!lastRefreshResultJson?.trim()) {
    return null
  }
  try {
    const parsed = JSON.parse(lastRefreshResultJson) as Record<string, unknown>
    const status = typeof parsed.status === 'string' ? parsed.status : null
    const trigger = typeof parsed.trigger === 'string' ? parsed.trigger : null
    return [status, trigger].filter(Boolean).join(' / ') || null
  } catch {
    return null
  }
}

function buildImportPreview(form: AuthJsonImportForm) {
  return JSON.stringify(
    {
      accountName: form.accountName,
      externalAccountId: form.externalAccountId,
      accessToken: maskSecret(form.accessToken),
      refreshToken: maskSecret(form.refreshToken),
      active: form.active,
      proxyId: form.proxyId || null,
      tlsFingerprintProfileId: form.tlsFingerprintProfileId || null,
      siteProfileId: form.siteProfileId || null,
    },
    null,
    2,
  )
}

function parseOptionalNumber(value: string) {
  if (!value.trim()) {
    return null
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error('数字字段必须填写有效数字。')
  }
  return parsed
}

function pickString(source: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = source[key]
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
      return String(value)
    }
  }
  return null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function maskSecret(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return ''
  }
  if (trimmed.length <= 8) {
    return '*'.repeat(trimmed.length)
  }
  return `${trimmed.slice(0, 4)}***${trimmed.slice(-4)}`
}
