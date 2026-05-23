import { type ChangeEvent, type FormEvent, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { apiRequest } from '@/lib/api'
import { useTypedMutation, useTypedQuery } from '@/lib/typed-react-query'
import type { ProviderProtocolEndpoint, ProviderSite } from '../provider-sites/types'
import {
  AUTH_KIND_OPTIONS,
  buildCredentialPayload,
  createEmptyCredentialForm,
  credentialToFormState,
  type CredentialConnectivityResponse,
  type CredentialFormState,
  type CredentialModelRefreshResponse,
  type CredentialResponse,
  type UpstreamCredentialInventoryResponse,
} from './types'

type CreateStep = 'identity' | 'provider' | 'secret' | 'binding' | 'network' | 'models' | 'submit'
type CreateMode = 'single' | 'batch'
type CreateSource = 'secret' | 'codexAuthJson'
type CodexImportMode = 'paths' | 'paste'
const CREATE_STEPS: CreateStep[] = ['identity', 'provider', 'secret', 'binding', 'network', 'models', 'submit']
const LAST_CREATE_STEP: CreateStep = 'submit'

type BatchCreateFailure = {
  credentialName: string
  message: string
}

type BatchCreateResult = {
  total: number
  success: number
  failed: BatchCreateFailure[]
}

type CredentialCreatePayload = ReturnType<typeof buildCredentialPayload>

type CodexImportPayload = {
  sourceLabel: string
  groupId: number
  accountName?: string | null
  authJsonContent?: string | null
  authJsonFilePath?: string | null
  active: boolean
  proxyId: number | null
  tlsFingerprintProfileId: number | null
  siteProfileId: number | null
  supportedModels: string[]
}

type CodexImportRequestBody = Omit<CodexImportPayload, 'sourceLabel'>

type CodexImportFailure = {
  sourceLabel: string
  message: string
}

type CodexImportResult = {
  total: number
  success: number
  failed: CodexImportFailure[]
}

type AccountGroupOption = {
  id: number
  groupName: string
  providerType?: string | null
  supportedModels?: string[]
  allowedClientFamilies?: string[]
  defaultGroup?: boolean
}

type ProxyOption = {
  id: number
  proxyName: string
  proxyUrl: string
  active: boolean
  lastStatus?: string | null
}

type TlsProfileOption = {
  id: number
  profileName: string
  profileCode: string
  active: boolean
}

type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'

type ProviderEndpointOption = {
  site: ProviderSite
  endpoint: ProviderProtocolEndpoint
}

export function CredentialsPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CredentialFormState>(createEmptyCredentialForm())
  const [formError, setFormError] = useState<string | null>(null)
  const [connectivityResult, setConnectivityResult] = useState<CredentialConnectivityResponse | null>(null)
  const [refreshMessage, setRefreshMessage] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [createStep, setCreateStep] = useState<CreateStep>('identity')
  const [createSource, setCreateSource] = useState<CreateSource>('secret')
  const [createMode, setCreateMode] = useState<CreateMode>('single')
  const [codexImportMode, setCodexImportMode] = useState<CodexImportMode>('paths')
  const [codexAuthJsonPathsRaw, setCodexAuthJsonPathsRaw] = useState('')
  const [codexAuthJsonRaw, setCodexAuthJsonRaw] = useState('')
  const [bulkSecretsRaw, setBulkSecretsRaw] = useState('')
  const [batchCreateResult, setBatchCreateResult] = useState<BatchCreateResult | null>(null)
  const [codexImportResult, setCodexImportResult] = useState<CodexImportResult | null>(null)
  const [editingCredentialId, setEditingCredentialId] = useState<number | null>(null)
  const [editingForm, setEditingForm] = useState<CredentialFormState>(createEmptyCredentialForm())
  const [editingError, setEditingError] = useState<string | null>(null)
  const [selectedInventoryRow, setSelectedInventoryRow] = useState<UpstreamCredentialInventoryResponse | null>(null)
  const [modelKeyword, setModelKeyword] = useState('')
  const [editingModelKeyword, setEditingModelKeyword] = useState('')

  const credentialInventoryQuery = useTypedQuery<UpstreamCredentialInventoryResponse[]>({
    queryKey: ['credentials', 'inventory'],
    queryFn: () => apiRequest<UpstreamCredentialInventoryResponse[]>('/admin/credentials/inventory'),
  })
  const accountGroupsQuery = useTypedQuery<AccountGroupOption[]>({
    queryKey: ['account-groups', 'options'],
    queryFn: () => apiRequest<AccountGroupOption[]>('/admin/account-groups'),
  })
  const proxiesQuery = useTypedQuery<ProxyOption[]>({
    queryKey: ['network-proxies'],
    queryFn: () => apiRequest<ProxyOption[]>('/admin/network/proxies'),
  })
  const tlsProfilesQuery = useTypedQuery<TlsProfileOption[]>({
    queryKey: ['tls-profiles'],
    queryFn: () => apiRequest<TlsProfileOption[]>('/admin/network/tls-profiles'),
  })
  const providerSitesQuery = useTypedQuery<ProviderSite[]>({
    queryKey: ['provider-sites', 'credential-options'],
    queryFn: () => apiRequest<ProviderSite[]>('/admin/provider-sites'),
  })
  const providerSiteOptions = useMemo(
    () => normalizeCredentialProviderSites(providerSitesQuery.data ?? []),
    [providerSitesQuery.data],
  )
  const providerEndpointOptions = useMemo(
    () => flattenProviderEndpointOptions(providerSiteOptions),
    [providerSiteOptions],
  )
  const selectedProviderEndpoints = useMemo(
    () => form.protocolEndpointIds
      .map((endpointId) => findProviderEndpoint(providerEndpointOptions, endpointId))
      .filter((option): option is ProviderEndpointOption => option != null),
    [form.protocolEndpointIds, providerEndpointOptions],
  )
  const selectedEditingProviderEndpoint = useMemo(
    () => findProviderEndpoint(providerEndpointOptions, editingForm.protocolEndpointId),
    [editingForm.protocolEndpointId, providerEndpointOptions],
  )
  const modelCatalogQuery = useTypedQuery<string[]>({
    queryKey: ['credentials', 'model-catalog', createSource, form.providerType, form.groupId],
    queryFn: () => {
      if (createSource === 'codexAuthJson') {
        return apiRequest<string[]>('/admin/account-groups/model-catalog?providerType=CODEX_OAUTH')
      }
      return apiRequest<string[]>(`/admin/credentials/model-catalog?providerType=${encodeURIComponent(form.providerType)}`)
    },
    enabled: createOpen,
  })
  const editingModelCatalogQuery = useTypedQuery<string[]>({
    queryKey: ['credentials', 'model-catalog', 'edit', editingForm.providerType],
    queryFn: () =>
      apiRequest<string[]>(
        `/admin/credentials/model-catalog?providerType=${encodeURIComponent(editingForm.providerType)}`,
      ),
    enabled: editingCredentialId != null,
  })
  const createCredentialMutation = useTypedMutation<CredentialResponse[], CredentialCreatePayload>({
    mutationFn: (payload) =>
      apiRequest<CredentialResponse[]>('/admin/credentials/multi-endpoint', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      setForm(createEmptyCredentialForm())
      setConnectivityResult(null)
      setRefreshMessage(null)
      setFormError(null)
      setCreateStep('identity')
      setCreateOpen(false)
      invalidateCredentialData(queryClient)
    },
  })

  const createBatchCredentialMutation = useTypedMutation<BatchCreateResult, { payloads: CredentialCreatePayload[] }>({
    mutationFn: async ({ payloads }) => {
      const failed: BatchCreateFailure[] = []
      let success = 0
      for (const payload of payloads) {
        try {
          const responses = await apiRequest<CredentialResponse[]>('/admin/credentials/multi-endpoint', {
            method: 'POST',
            body: JSON.stringify(payload),
          })
          success += responses.length
        } catch (error) {
          failed.push({
            credentialName: payload.credentialName,
            message: resolveErrorMessage(error),
          })
        }
      }

      return {
        total: payloads.reduce((total, payload) => total + Math.max(payload.protocolEndpointIds.length, 1), 0),
        success,
        failed,
      }
    },
    onSuccess: (result) => {
      setBatchCreateResult(result)
      if (result.success > 0) {
        invalidateCredentialData(queryClient)
      }
      if (result.failed.length === 0) {
        setForm(createEmptyCredentialForm())
        setBulkSecretsRaw('')
        setFormError(null)
        setCreateStep('identity')
        setCreateMode('single')
        setCreateOpen(false)
      }
    },
  })

  const createCodexAuthJsonMutation = useTypedMutation<CodexImportResult, { payloads: CodexImportPayload[] }>({
    mutationFn: async ({ payloads }) => {
      const failed: CodexImportFailure[] = []
      let success = 0
      for (const payload of payloads) {
        try {
          await apiRequest('/admin/accounts/import-auth-json', {
            method: 'POST',
            body: JSON.stringify(toCodexImportRequestBody(payload)),
          })
          success += 1
        } catch (error) {
          failed.push({
            sourceLabel: payload.sourceLabel,
            message: resolveErrorMessage(error),
          })
        }
      }

      return {
        total: payloads.length,
        success,
        failed,
      }
    },
    onSuccess: (result) => {
      setCodexImportResult(result)
      if (result.success > 0) {
        invalidateCredentialData(queryClient)
        queryClient.invalidateQueries({ queryKey: ['account-groups'] })
        queryClient.invalidateQueries({ queryKey: ['account-groups', 'options'] })
      }
      if (result.failed.length === 0) {
        setForm(createEmptyCredentialForm())
        setCodexAuthJsonPathsRaw('')
        setCodexAuthJsonRaw('')
        setFormError(null)
        setCreateStep('identity')
        setCreateSource('secret')
        setCodexImportMode('paths')
        setCreateOpen(false)
      }
    },
  })

  const updateCredentialMutation = useTypedMutation<CredentialResponse, { id: number; payload: ReturnType<typeof buildCredentialPayload> }>({
    mutationFn: ({ id, payload }) =>
      apiRequest<CredentialResponse>(`/admin/credentials/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      setEditingCredentialId(null)
      setEditingForm(createEmptyCredentialForm())
      setEditingError(null)
      invalidateCredentialData(queryClient)
    },
  })

  const connectivityMutation = useTypedMutation<CredentialConnectivityResponse, ReturnType<typeof buildCredentialPayload>>({
    mutationFn: (payload) =>
      apiRequest<CredentialConnectivityResponse>('/admin/credentials/test-connectivity', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: (data) => {
      setConnectivityResult(data)
      setFormError(null)
    },
  })

  const toggleCredentialMutation = useTypedMutation<CredentialResponse, { id: number; active: boolean }>({
    mutationFn: ({ id, active }) =>
      apiRequest<CredentialResponse>(`/admin/credentials/${id}/status?active=${active}`, {
        method: 'POST',
      }),
    onSuccess: () => {
      invalidateCredentialData(queryClient)
    },
  })

  const refreshCredentialMutation = useTypedMutation<CredentialModelRefreshResponse, number>({
    mutationFn: (credentialId) =>
      apiRequest<CredentialModelRefreshResponse>(`/admin/credentials/${credentialId}/refresh-models`, {
        method: 'POST',
      }),
    meta: {
      actionName: '刷新凭证模型',
      successMessage: (data) => {
        const result = data as CredentialModelRefreshResponse
        return `模型刷新完成：发现 ${result.modelCount} 个模型。`
      },
    },
    onSuccess: (data) => {
      setRefreshMessage(`模型刷新完成：发现 ${data.modelCount} 个模型。`)
      invalidateCredentialData(queryClient)
    },
  })

  const deleteCredentialMutation = useTypedMutation<void, number>({
    mutationFn: (credentialId) =>
      apiRequest<void>(`/admin/credentials/${credentialId}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      invalidateCredentialData(queryClient)
    },
  })

  const handleCreateCredential = (event: FormEvent) => {
    event.preventDefault()
    if (createStep !== LAST_CREATE_STEP) {
      const stepIndex = CREATE_STEPS.indexOf(createStep)
      setCreateStep(CREATE_STEPS[Math.min(CREATE_STEPS.length - 1, stepIndex + 1)])
      return
    }
    try {
      setFormError(null)
      setBatchCreateResult(null)
      requireAccountGroup(form.groupId)

      if (createSource === 'codexAuthJson') {
        const payloads = buildCodexImportPayloads({
          mode: codexImportMode,
          rawPaths: codexAuthJsonPathsRaw,
          rawContent: codexAuthJsonRaw,
          form,
          accountGroups,
        })
        createCodexAuthJsonMutation.mutate({ payloads })
        return
      }

      if (createMode === 'single') {
        const payload = buildCredentialPayload(form)
        createCredentialMutation.mutate(payload)
        return
      }

      const secrets = parseSecretLines(bulkSecretsRaw)
      if (!secrets.length) {
        throw new Error('批量导入至少需要一条密钥记录。')
      }

      const namePrefix = form.credentialName.trim() || `${form.providerType.toLowerCase()}-secret`
      const payloads = secrets.map((secret, index) =>
        buildCredentialPayload({
          ...form,
          credentialName: `${namePrefix}-${String(index + 1).padStart(2, '0')}`,
          secret,
        }),
      )
      createBatchCredentialMutation.mutate({ payloads })
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '无法创建凭证。')
    }
  }

  const handleConnectivityTest = () => {
    if (createSource === 'codexAuthJson' || createMode === 'batch') {
      return
    }
    try {
      const payload = buildCredentialPayload(form)
      setFormError(null)
      connectivityMutation.mutate(payload)
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '无法测试联通性。')
    }
  }

  const handleOpenEditCredential = (credential: CredentialResponse) => {
    setEditingCredentialId(credential.id)
    setEditingForm(credentialToFormState(credential))
    setEditingError(null)
  }

  const handleOpenEditInventoryRow = (row: UpstreamCredentialInventoryResponse) => {
    if (row.sourceType !== 'API_KEY') {
      return
    }
    handleOpenEditCredential(inventoryRowToCredential(row, providerSiteOptions))
    setEditingModelKeyword('')
  }

  const handleUpdateCredential = (event: FormEvent) => {
    event.preventDefault()
    if (editingCredentialId == null) {
      return
    }
    try {
      setEditingError(null)
      requireAccountGroup(editingForm.groupId)
      updateCredentialMutation.mutate({
        id: editingCredentialId,
        payload: buildCredentialPayload(editingForm),
      })
    } catch (error) {
      setEditingError(error instanceof Error ? error.message : '无法保存凭证。')
    }
  }

  const handleBulkSecretFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    const text = await file.text()
    setBulkSecretsRaw(text)
    event.target.value = ''
  }

  const handleCodexAuthJsonFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    if (!files.length) {
      return
    }
    const contents = await Promise.all(files.map((file) => file.text()))
    setCodexAuthJsonRaw((current) => [current.trim(), ...contents.map((content) => content.trim())]
      .filter(Boolean)
      .join('\n'))
    setCodexImportMode('paste')
    event.target.value = ''
  }

  const handleDeleteInventoryRow = (row: UpstreamCredentialInventoryResponse) => {
    if (row.sourceType !== 'API_KEY') {
      return
    }
    if (!window.confirm(`确认删除凭证“${row.displayName}”吗？`)) {
      return
    }
    deleteCredentialMutation.mutate(row.sourceId)
    setSelectedInventoryRow(null)
  }

  const credentialRows = credentialInventoryQuery.data ?? []
  const accountGroups = Array.isArray(accountGroupsQuery.data) ? accountGroupsQuery.data : []
  const proxyOptions = proxiesQuery.data ?? []
  const tlsProfileOptions = tlsProfilesQuery.data ?? []
  const defaultGroup = accountGroups.find((group) => group.defaultGroup) ?? accountGroups[0]
  const codexGroup = accountGroups.find(isCodexAccountGroup)
  const effectiveDefaultGroup = createSource === 'codexAuthJson'
    ? (codexGroup ?? defaultGroup)
    : defaultGroup
  const createStepIndex = CREATE_STEPS.indexOf(createStep)
  const canPrev = createStepIndex > 0
  const canNext = createStepIndex < CREATE_STEPS.length - 1
  const canShowConnectivityTest = createStep === 'secret' && createSource === 'secret'
  const canShowCreateButton = createStep === LAST_CREATE_STEP
  const parsedBulkSecrets = useMemo(() => parseSecretLines(bulkSecretsRaw), [bulkSecretsRaw])
  const parsedCodexPaths = useMemo(() => parsePathLines(codexAuthJsonPathsRaw), [codexAuthJsonPathsRaw])
  const parsedCodexContentCount = useMemo(() => estimateJsonDocumentCount(codexAuthJsonRaw), [codexAuthJsonRaw])
  const modelOptions = useMemo(
    () => mergeModelOptions(modelCatalogQuery.data ?? [], form.supportedModels),
    [form.supportedModels, modelCatalogQuery.data],
  )
  const filteredModelOptions = useMemo(() => {
    const keyword = modelKeyword.trim().toLowerCase()
    if (!keyword) {
      return modelOptions
    }
    return modelOptions.filter((model) => model.toLowerCase().includes(keyword))
  }, [modelKeyword, modelOptions])
  const editingModelOptions = useMemo(
    () => mergeModelOptions(editingModelCatalogQuery.data ?? [], editingForm.supportedModels),
    [editingForm.supportedModels, editingModelCatalogQuery.data],
  )
  const filteredEditingModelOptions = useMemo(
    () => filterOptions(editingModelOptions, editingModelKeyword),
    [editingModelKeyword, editingModelOptions],
  )
  const createPending = createCredentialMutation.isPending || createBatchCredentialMutation.isPending || createCodexAuthJsonMutation.isPending

  const openApiKeyCredentialDialog = () => {
    setFormError(null)
    setConnectivityResult(null)
    setBatchCreateResult(null)
    setCodexImportResult(null)
    setCreateStep('identity')
    setCreateSource('secret')
    setCreateMode('single')
    setCodexImportMode('paths')
    setBulkSecretsRaw('')
    setCodexAuthJsonPathsRaw('')
    setCodexAuthJsonRaw('')
    setModelKeyword('')
    setForm({
      ...createEmptyCredentialForm(),
      groupId: defaultGroup ? String(defaultGroup.id) : '',
    })
    setCreateOpen(true)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="上游凭证"
        title="已录入凭证"
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              onClick={openApiKeyCredentialDialog}
            >
              新增上游凭证
            </Button>
          </div>
        )}
      >
        {(refreshCredentialMutation.error || deleteCredentialMutation.error || toggleCredentialMutation.error || updateCredentialMutation.error) ? (
          <InlineError
            error={refreshCredentialMutation.error ?? deleteCredentialMutation.error ?? toggleCredentialMutation.error ?? updateCredentialMutation.error}
            title="凭证操作失败"
          />
        ) : null}
        {refreshMessage ? (
          <div className="rounded-xl border border-border/45 bg-card/75 px-4 py-3 text-sm text-foreground">
            {refreshMessage}
          </div>
        ) : null}

        {credentialInventoryQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : credentialInventoryQuery.error ? (
          <InlineError error={credentialInventoryQuery.error} title="凭证列表加载失败" />
        ) : credentialRows.length ? (
          <PaginatedRows items={credentialRows}>
            {({ pageItems }) => (
              <div className="scrollbar-subtle overflow-x-auto rounded-xl border border-border/45 bg-card/82">
                <table className="w-full min-w-[960px] table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[25%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号名称</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">类型</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[15%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号分组</th>
                      <th className="w-[13%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近使用</th>
                      <th className="w-[7%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">详情</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.rowKey} className="border-b border-border/40 align-middle">
                        <td className="truncate px-4 py-3 font-medium text-foreground" title={row.displayName}>{row.displayName}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={row.sourceType === 'AUTH_JSON_ACCOUNT' ? 'info' : 'neutral'}>
                            {sourceTypeLabel(row.sourceType)}
                          </StatusBadge>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={rowStatusTone(row)}>
                            {rowStatusLabel(row)}
                          </StatusBadge>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground" title={row.providerType}>{row.providerType}</td>
                        <td className="px-4 py-3">
                          <button
                            type="button"
                            className="max-w-full truncate text-left font-medium text-primary hover:underline"
                            title={row.groupName ?? '未归组'}
                            onClick={() => setSelectedInventoryRow(row)}
                          >
                            {row.groupName ?? '未归组'}
                          </button>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(row.lastUsedAt)}</td>
                        <td className="px-4 py-3">
                          <Button type="button" variant="outline" size="sm" onClick={() => setSelectedInventoryRow(row)}>
                            查看
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
          <EmptyState title="还没有上游凭证" />
        )}
      </PageSection>

      <Dialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open)
          if (!open) {
            setFormError(null)
            setConnectivityResult(null)
            setBatchCreateResult(null)
            setCodexImportResult(null)
          }
        }}
      >
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>创建上游凭证</DialogTitle>
            <DialogDescription className="sr-only">录入凭证信息并提交。</DialogDescription>
          </DialogHeader>

          <form className="flex flex-col gap-4" onSubmit={handleCreateCredential}>
            <Tabs value={createStep} onValueChange={(value) => setCreateStep(value as CreateStep)}>
              <TabsList variant="line" className="w-full justify-start overflow-x-auto">
                <TabsTrigger value="identity">1. 凭证身份</TabsTrigger>
                <TabsTrigger value="provider">2. 提供方</TabsTrigger>
                <TabsTrigger value="secret">3. 密钥导入</TabsTrigger>
                <TabsTrigger value="binding">4. 账号绑定</TabsTrigger>
                <TabsTrigger value="network">5. 网络安全</TabsTrigger>
                <TabsTrigger value="models">6. 模型范围</TabsTrigger>
                <TabsTrigger value="submit">7. 提交确认</TabsTrigger>
              </TabsList>

              <TabsContent value="identity" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">凭证类型</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={createSource}
                      onChange={(event) => {
                        const nextSource = event.target.value as CreateSource
                        const nextGroup = nextSource === 'codexAuthJson' ? (codexGroup ?? defaultGroup) : defaultGroup
                        setCreateSource(nextSource)
                        setBatchCreateResult(null)
                        setCodexImportResult(null)
                        setConnectivityResult(null)
                        setFormError(null)
                        setCreateMode('single')
                        setForm((current) => ({
                          ...current,
                          groupId: nextGroup ? String(nextGroup.id) : current.groupId,
                          siteProfileId: nextSource === 'codexAuthJson' ? '' : current.siteProfileId,
                          protocolEndpointId: nextSource === 'codexAuthJson' ? '' : current.protocolEndpointId,
                          protocolEndpointIds: nextSource === 'codexAuthJson' ? [] : current.protocolEndpointIds,
                        }))
                      }}
                    >
                      <option value="secret">API Key / Secret</option>
                      <option value="codexAuthJson">Codex auth.json</option>
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">
                      {createSource === 'codexAuthJson' ? '账号名称前缀' : (createMode === 'single' ? '凭证名称' : '凭证名前缀')}
                    </span>
                    <Input
                      value={form.credentialName}
                      onChange={(event) => setForm((current) => ({ ...current, credentialName: event.target.value }))}
                      placeholder={createSource === 'codexAuthJson' ? '例如：Codex' : (createMode === 'single' ? '例如：OpenAI 主账号 Key' : '例如：OpenAI-Prod')}
                    />
                  </label>
                  {createSource === 'secret' ? (
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">创建模式</span>
                      <select
                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                        value={createMode}
                        onChange={(event) => setCreateMode(event.target.value as CreateMode)}
                      >
                        <option value="single">单条创建</option>
                        <option value="batch">批量导入</option>
                      </select>
                    </label>
                  ) : (
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">导入方式</span>
                      <select
                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                        value={codexImportMode}
                        onChange={(event) => setCodexImportMode(event.target.value as CodexImportMode)}
                      >
                        <option value="paths">文件路径</option>
                        <option value="paste">粘贴 JSON</option>
                      </select>
                    </label>
                  )}
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.active}
                      onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">创建后立即启用</span>
                  </label>
                </div>
                {createSource === 'secret' && createMode === 'batch' ? (
                  <div className="mt-4 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                    批量模式会将文本按换行切分，一行一个密钥；系统将按“前缀-序号”自动生成凭证名称。
                  </div>
                ) : null}
              </TabsContent>

              <TabsContent value="provider" className="pt-3">
                {createSource === 'codexAuthJson' ? (
                  <div className="grid gap-4 md:grid-cols-2">
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">提供方</span>
                      <Input value="CODEX_OAUTH" readOnly />
                    </label>
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">客户端族</span>
                      <Input value="CODEX" readOnly />
                    </label>
                  </div>
                ) : (
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="flex flex-col gap-2 md:col-span-2">
                      <span className="text-sm font-medium text-foreground">厂商协议入口</span>
                      <div className="grid max-h-64 gap-2 overflow-y-auto rounded-md border border-input bg-background p-2">
                        {providerSitesQuery.isPending ? (
                          <div className="px-2 py-1 text-sm text-muted-foreground">加载中</div>
                        ) : null}
                        {!providerSitesQuery.isPending && providerEndpointOptions.length === 0 ? (
                          <div className="px-2 py-1 text-sm text-muted-foreground">暂无可选厂商协议入口</div>
                        ) : null}
                        {providerEndpointOptions.map((option) => {
                          const endpointId = String(option.endpoint.id)
                          return (
                            <label
                              key={option.endpoint.id}
                              className="flex min-h-10 items-center gap-3 rounded-md px-2 py-2 hover:bg-muted/40"
                            >
                              <input
                                type="checkbox"
                                className="size-4 rounded border-border"
                                checked={form.protocolEndpointIds.includes(endpointId)}
                                disabled={!isSelectableProviderEndpoint(option)}
                                onChange={() => {
                                  setConnectivityResult(null)
                                  setForm((current) => toggleProviderEndpointOnForm(current, endpointId, providerEndpointOptions))
                                }}
                              />
                              <span className="min-w-0 flex-1 text-sm text-foreground">
                                {providerEndpointOptionLabel(option)}
                              </span>
                            </label>
                          )
                        })}
                      </div>
                    </div>
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">提供方类型</span>
                      <Input value={form.providerType} readOnly />
                    </label>
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">认证类型</span>
                      <select
                        className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                        value={form.authKind}
                        onChange={(event) => setForm((current) => ({ ...current, authKind: event.target.value }))}
                      >
                        {AUTH_KIND_OPTIONS.map((option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="flex flex-col gap-2 md:col-span-2">
                      <span className="text-sm font-medium text-foreground">Base URL</span>
                      <Input value={selectedEndpointBaseUrlPreview(selectedProviderEndpoints, form.baseUrl)} readOnly />
                    </label>
                  </div>
                )}
              </TabsContent>

              <TabsContent value="secret" className="pt-3">
                {createSource === 'codexAuthJson' ? (
                  <div className="grid gap-4">
                    {codexImportMode === 'paths' ? (
                      <>
                        <label className="flex flex-col gap-2">
                          <span className="text-sm font-medium text-foreground">auth.json 文件路径</span>
                          <Textarea
                            rows={8}
                            value={codexAuthJsonPathsRaw}
                            onChange={(event) => setCodexAuthJsonPathsRaw(event.target.value)}
                            placeholder={'C:/Users/you/Desktop/auth/account-01.json\nC:/Users/you/Desktop/auth/account-02.json'}
                          />
                        </label>
                        <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                          当前解析到 {parsedCodexPaths.length} 个文件路径。
                        </div>
                      </>
                    ) : (
                      <>
                        <label className="flex flex-col gap-2">
                          <span className="text-sm font-medium text-foreground">选择 auth.json 文件</span>
                          <Input type="file" accept=".json,application/json" multiple onChange={handleCodexAuthJsonFileChange} />
                        </label>
                        <label className="flex flex-col gap-2">
                          <span className="text-sm font-medium text-foreground">auth.json 内容</span>
                          <Textarea
                            rows={10}
                            value={codexAuthJsonRaw}
                            onChange={(event) => setCodexAuthJsonRaw(event.target.value)}
                            placeholder='{"auth_mode":"login","tokens":{...}}'
                          />
                        </label>
                        <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                          当前解析到 {parsedCodexContentCount} 份 JSON 内容。
                        </div>
                      </>
                    )}
                  </div>
                ) : createMode === 'single' ? (
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">访问密钥</span>
                    <Input
                      type="password"
                      value={form.secret}
                      onChange={(event) => setForm((current) => ({ ...current, secret: event.target.value }))}
                      placeholder="输入上游密钥"
                    />
                  </label>
                ) : (
                  <div className="grid gap-4">
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">导入纯文本文件（.txt）</span>
                      <Input type="file" accept=".txt,text/plain" onChange={handleBulkSecretFileChange} />
                    </label>
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">批量密钥文本（每行一条）</span>
                      <Textarea
                        rows={8}
                        value={bulkSecretsRaw}
                        onChange={(event) => setBulkSecretsRaw(event.target.value)}
                        placeholder={'sk-xxx\nsk-yyy\nsk-zzz'}
                      />
                    </label>
                    <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                      当前解析到 {parsedBulkSecrets.length} 条可导入密钥。
                    </div>
                  </div>
                )}
              </TabsContent>

              <TabsContent value="binding" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">所属账号分组</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={form.groupId}
                      onChange={(event) => setForm((current) => ({ ...current, groupId: event.target.value }))}
                    >
                      <option value="" disabled>请选择账号分组</option>
                      {effectiveDefaultGroup ? (
                        <option value={String(effectiveDefaultGroup.id)}>
                          {effectiveDefaultGroup.groupName}{effectiveDefaultGroup.defaultGroup ? '（默认分组）' : ''}
                        </option>
                      ) : null}
                      {accountGroups
                        .filter((group) => !effectiveDefaultGroup || group.id !== effectiveDefaultGroup.id)
                        .map((group) => (
                          <option key={group.id} value={String(group.id)}>
                            {group.groupName}
                          </option>
                      ))}
                    </select>
                  </label>
                  {!accountGroups.length ? (
                    <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground md:col-span-2">
                      暂无可用账号分组，请先创建账号分组后再录入上游凭证。
                    </div>
                  ) : null}
                </div>
              </TabsContent>

              <TabsContent value="network" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <SearchableIdSelect
                    label="代理"
                    value={form.proxyId}
                    options={proxyOptions.map(proxyToSelectOption)}
                    onChange={(value) => setForm((current) => ({ ...current, proxyId: value }))}
                    emptyLabel="不绑定代理"
                  />
                  <SearchableIdSelect
                    label="TLS 指纹"
                    value={form.tlsFingerprintProfileId}
                    options={tlsProfileOptions.map(tlsProfileToSelectOption)}
                    onChange={(value) => setForm((current) => ({ ...current, tlsFingerprintProfileId: value }))}
                    emptyLabel="不绑定 TLS 指纹"
                  />
                </div>
              </TabsContent>

              <TabsContent value="models" className="pt-3">
                <div className="rounded-2xl border border-border/60 bg-muted/10 p-4">
                  <ModelSelector
                    selected={form.supportedModels}
                    options={filteredModelOptions}
                    keyword={modelKeyword}
                    loading={modelCatalogQuery.isPending}
                    onKeywordChange={setModelKeyword}
                    onChange={(models) => setForm((current) => ({ ...current, supportedModels: models }))}
                  />
                </div>
              </TabsContent>

              <TabsContent value="submit" className="pt-3">
                <div className="grid gap-4">
                  <div className="rounded-2xl border border-border/60 bg-muted/10 p-4">
                    <div className="mb-3 text-sm font-medium text-foreground">提交前确认</div>
                    <div className="grid gap-3 text-sm md:grid-cols-2">
                      <div>
                        <div className="text-xs text-muted-foreground">创建模式</div>
                        <div className="font-medium text-foreground">
                          {createSource === 'codexAuthJson'
                            ? (codexImportMode === 'paths' ? 'Codex 文件路径导入' : 'Codex 粘贴导入')
                            : (createMode === 'single' ? '单条创建' : '批量导入')}
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">厂商/API 入口</div>
                        <div className="font-medium text-foreground">
                          {createSource === 'codexAuthJson'
                            ? 'CODEX_OAUTH / auth.json'
                            : `${selectedProviderEndpoints.length ? selectedProviderEndpoints.map(providerEndpointOptionLabel).join('；') : '未选择'} / ${form.authKind}`}
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">
                          {createSource === 'codexAuthJson' ? '账号名称前缀' : (createMode === 'single' ? '凭证名称' : '凭证名前缀')}
                        </div>
                        <div className="font-medium text-foreground">{form.credentialName.trim() || '未填写'}</div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">账号分组</div>
                        <div className="font-medium text-foreground">
                          {resolveGroupName(accountGroups, form.groupId) ?? '未选择'}
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-muted-foreground">支持模型</div>
                        <div className="font-medium text-foreground">
                          {form.supportedModels.length ? `${form.supportedModels.length} 个已选模型` : '由后端自动匹配'}
                        </div>
                      </div>
                      {createSource === 'codexAuthJson' ? (
                        <div>
                          <div className="text-xs text-muted-foreground">导入数量</div>
                          <div className="font-medium text-foreground">
                            {codexImportMode === 'paths' ? `${parsedCodexPaths.length} 个文件路径` : `${parsedCodexContentCount} 份 JSON 内容`}
                          </div>
                        </div>
                      ) : null}
                    </div>
                  </div>
                  {createSource === 'secret' ? (
                    <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">元数据 JSON</span>
                    <Textarea
                      rows={6}
                      value={form.metadataJson}
                      onChange={(event) => setForm((current) => ({ ...current, metadataJson: event.target.value }))}
                      placeholder='可选，例如 {"organization":"prod"}'
                    />
                    </label>
                  ) : null}
                  {createSource === 'secret' && createMode === 'batch' ? (
                    <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                      预计创建 {parsedBulkSecrets.length * Math.max(selectedProviderEndpoints.length, 1)} 条凭证，名称前缀为 {form.credentialName.trim() || `${form.providerType.toLowerCase()}-secret`}。
                    </div>
                  ) : null}
                  {batchCreateResult ? (
                    <div className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-sm text-foreground">
                      批量创建完成：总计 {batchCreateResult.total}，成功 {batchCreateResult.success}，失败 {batchCreateResult.failed.length}。
                      {batchCreateResult.failed.length ? (
                        <div className="mt-2 text-sm text-muted-foreground">
                          失败项（最多展示 5 条）：{batchCreateResult.failed.slice(0, 5).map((item) => `${item.credentialName}: ${item.message}`).join('；')}
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                  {codexImportResult ? (
                    <div className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-sm text-foreground">
                      Codex auth.json 导入完成：总计 {codexImportResult.total}，成功 {codexImportResult.success}，失败 {codexImportResult.failed.length}。
                      {codexImportResult.failed.length ? (
                        <div className="mt-2 text-sm text-muted-foreground">
                          失败项（最多展示 5 条）：{codexImportResult.failed.slice(0, 5).map((item) => `${item.sourceLabel}: ${item.message}`).join('；')}
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </TabsContent>
            </Tabs>

            {(formError || createCredentialMutation.error || createBatchCredentialMutation.error || createCodexAuthJsonMutation.error || connectivityMutation.error) ? (
              <InlineError
                error={createCredentialMutation.error ?? createBatchCredentialMutation.error ?? createCodexAuthJsonMutation.error ?? connectivityMutation.error ?? new Error(formError ?? '凭证录入失败')}
                title="凭证录入失败"
              />
            ) : null}
            {connectivityResult ? (
              <div className="rounded-2xl border border-border/60 bg-card/80 px-4 py-3 text-sm text-foreground">
                {connectivityResult.message} 发现 {connectivityResult.discoveredModelCount} 个模型，耗时 {connectivityResult.latencyMs}ms。
              </div>
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateStep(CREATE_STEPS[Math.max(0, createStepIndex - 1)])} disabled={!canPrev}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={() => setCreateStep(CREATE_STEPS[Math.min(CREATE_STEPS.length - 1, createStepIndex + 1)])} disabled={!canNext}>
                下一步
              </Button>
              {canShowConnectivityTest ? (
                <Button type="button" variant="outline" onClick={handleConnectivityTest} disabled={connectivityMutation.isPending || createMode === 'batch' || form.protocolEndpointIds.length !== 1}>
                  测试联通性
                </Button>
              ) : null}
              {canShowCreateButton ? (
                <Button type="submit" disabled={createPending || !form.groupId || (createSource === 'secret' && form.protocolEndpointIds.length === 0)}>
                  {createSource === 'codexAuthJson' ? '导入 auth.json' : (createMode === 'single' ? '创建凭证' : '批量创建')}
                </Button>
              ) : null}
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={editingCredentialId != null}
        onOpenChange={(open) => {
          if (!open) {
            setEditingCredentialId(null)
            setEditingError(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>编辑上游凭证</DialogTitle>
            <DialogDescription className="sr-only">更新凭证信息。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleUpdateCredential}>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">凭证名称</span>
                <Input
                  value={editingForm.credentialName}
                  onChange={(event) => setEditingForm((current) => ({ ...current, credentialName: event.target.value }))}
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">账号分组</span>
                <select
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={editingForm.groupId}
                  onChange={(event) => setEditingForm((current) => ({ ...current, groupId: event.target.value }))}
                >
                  <option value="" disabled>请选择账号分组</option>
                  {accountGroups.map((group) => (
                    <option key={group.id} value={String(group.id)}>
                      {group.groupName}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-2 md:col-span-2">
                <span className="text-sm font-medium text-foreground">厂商协议入口</span>
                <select
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={editingForm.protocolEndpointId}
                  onChange={(event) => setEditingForm((current) => applyProviderEndpointToForm(current, event.target.value, providerEndpointOptions))}
                >
                  <option value="" disabled>{providerSitesQuery.isPending ? '加载中' : '请选择厂商协议入口'}</option>
                  {providerEndpointOptions.map((option) => (
                    <option key={option.endpoint.id} value={String(option.endpoint.id)} disabled={!isSelectableProviderEndpoint(option)}>
                      {providerEndpointOptionLabel(option)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">提供方类型</span>
                <Input value={editingForm.providerType} readOnly />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">认证类型</span>
                <select
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={editingForm.authKind}
                  onChange={(event) => setEditingForm((current) => ({ ...current, authKind: event.target.value }))}
                >
                  {AUTH_KIND_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-2 md:col-span-2">
                <span className="text-sm font-medium text-foreground">Base URL</span>
                <Input value={selectedEditingProviderEndpoint?.endpoint.baseUrl ?? editingForm.baseUrl} readOnly />
              </label>
              <label className="flex flex-col gap-2 md:col-span-2">
                <span className="text-sm font-medium text-foreground">更新密钥（可选）</span>
                <Input
                  type="password"
                  value={editingForm.secret}
                  onChange={(event) => setEditingForm((current) => ({ ...current, secret: event.target.value }))}
                  placeholder="留空则保留当前密钥"
                />
              </label>
              <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                <input
                  type="checkbox"
                  className="size-4 rounded border-border"
                  checked={editingForm.active}
                  onChange={(event) => setEditingForm((current) => ({ ...current, active: event.target.checked }))}
                />
                <span className="text-sm font-medium text-foreground">启用凭证</span>
              </label>
              <SearchableIdSelect
                label="代理"
                value={editingForm.proxyId}
                options={proxyOptions.map(proxyToSelectOption)}
                onChange={(value) => setEditingForm((current) => ({ ...current, proxyId: value }))}
                emptyLabel="不绑定代理"
              />
              <SearchableIdSelect
                label="TLS 指纹"
                value={editingForm.tlsFingerprintProfileId}
                options={tlsProfileOptions.map(tlsProfileToSelectOption)}
                onChange={(value) => setEditingForm((current) => ({ ...current, tlsFingerprintProfileId: value }))}
                emptyLabel="不绑定 TLS 指纹"
              />
              <div className="md:col-span-2">
                <div className="rounded-2xl border border-border/60 bg-muted/10 p-4">
                  <ModelSelector
                    selected={editingForm.supportedModels}
                    options={filteredEditingModelOptions}
                    keyword={editingModelKeyword}
                    loading={editingModelCatalogQuery.isPending}
                    onKeywordChange={setEditingModelKeyword}
                    onChange={(models) => setEditingForm((current) => ({ ...current, supportedModels: models }))}
                  />
                </div>
              </div>
              <label className="flex flex-col gap-2 md:col-span-2">
                <span className="text-sm font-medium text-foreground">元数据 JSON</span>
                <Textarea
                  rows={5}
                  value={editingForm.metadataJson}
                  onChange={(event) => setEditingForm((current) => ({ ...current, metadataJson: event.target.value }))}
                  placeholder='可选，例如 {"organization":"prod"}'
                />
              </label>
            </div>
            {editingError || updateCredentialMutation.error ? (
              <InlineError
                error={updateCredentialMutation.error ?? new Error(editingError ?? '保存凭证失败')}
                title="保存凭证失败"
              />
            ) : null}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditingCredentialId(null)}>
                取消
              </Button>
              <Button type="submit" disabled={updateCredentialMutation.isPending}>
                保存修改
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={selectedInventoryRow != null} onOpenChange={(open) => !open && setSelectedInventoryRow(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{selectedInventoryRow?.displayName ?? '上游凭证详情'}</DialogTitle>
            <DialogDescription className="sr-only">查看上游凭证详情与可用操作。</DialogDescription>
          </DialogHeader>
          {selectedInventoryRow ? (
            <div className="flex flex-col gap-4">
              <div className="grid gap-3 text-sm md:grid-cols-2">
                {[
                  ['类型', sourceTypeLabel(selectedInventoryRow.sourceType)],
                  ['提供方', selectedInventoryRow.providerType],
                  ['账号分组', selectedInventoryRow.groupName ?? '未归组'],
                  ['认证类型', selectedInventoryRow.authKind ?? '-'],
                  ['状态', rowStatusLabel(selectedInventoryRow)],
                  ['最近使用', formatInstant(selectedInventoryRow.lastUsedAt)],
                  ['最近刷新', formatInstant(selectedInventoryRow.lastRefreshAt)],
                  ['代理', resolveProxyLabel(proxyOptions, selectedInventoryRow.proxyId)],
                  ['TLS 指纹', resolveTlsProfileLabel(tlsProfileOptions, selectedInventoryRow.tlsFingerprintProfileId)],
                  ['支持模型', selectedInventoryRow.supportedModels?.length ? selectedInventoryRow.supportedModels.join(', ') : '由分组自动匹配'],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-xl border border-border/60 bg-muted/10 px-3 py-2">
                    <div className="text-xs text-muted-foreground">{label}</div>
                    <div className="mt-1 truncate font-medium text-foreground" title={String(value)}>{value}</div>
                  </div>
                ))}
              </div>
              {selectedInventoryRow.lastErrorMessage ? (
                <div className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {selectedInventoryRow.lastErrorMessage}
                </div>
              ) : null}
              <div className="flex flex-wrap gap-2">
                {selectedInventoryRow.sourceType === 'API_KEY' ? (
                  <>
                    <Button type="button" variant="outline" onClick={() => handleOpenEditInventoryRow(selectedInventoryRow)}>
                      编辑
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => toggleCredentialMutation.mutate({ id: selectedInventoryRow.sourceId, active: !selectedInventoryRow.active })}
                      disabled={toggleCredentialMutation.isPending}
                    >
                      {selectedInventoryRow.active ? '停用' : '启用'}
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => refreshCredentialMutation.mutate(selectedInventoryRow.sourceId)}
                      disabled={refreshCredentialMutation.isPending}
                    >
                      刷新模型
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => handleDeleteInventoryRow(selectedInventoryRow)}
                      disabled={deleteCredentialMutation.isPending}
                    >
                      删除
                    </Button>
                  </>
                ) : (
                  <Button type="button" variant="outline" asChild>
                    <Link to={selectedInventoryRow.groupId == null ? '/console/account-groups' : `/console/account-groups/${selectedInventoryRow.groupId}`}>打开账号分组</Link>
                  </Button>
                )}
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}

type SearchableOption = {
  value: string
  label: string
  description?: string
}

function SearchableIdSelect({
  label,
  value,
  options,
  onChange,
  emptyLabel,
}: {
  label: string
  value: string
  options: SearchableOption[]
  onChange: (value: string) => void
  emptyLabel: string
}) {
  const [keyword, setKeyword] = useState('')
  const visibleOptions = useMemo(
    () => options.filter((option) => optionMatches(option, keyword)),
    [keyword, options],
  )

  return (
    <div className="flex flex-col gap-2">
      <span className="text-sm font-medium text-foreground">{label}</span>
      <Input
        aria-label={`搜索${label}`}
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        placeholder={`搜索${label}`}
      />
      <select
        aria-label={label}
        className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{emptyLabel}</option>
        {visibleOptions.map((option) => (
          <option key={option.value} value={option.value}>
            {option.description ? `${option.label} - ${option.description}` : option.label}
          </option>
        ))}
      </select>
    </div>
  )
}

function ModelSelector({
  selected,
  options,
  keyword,
  loading,
  onKeywordChange,
  onChange,
}: {
  selected: string[]
  options: string[]
  keyword: string
  loading: boolean
  onKeywordChange: (keyword: string) => void
  onChange: (models: string[]) => void
}) {
  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-medium text-foreground">支持模型</span>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onChange([...options])}
            disabled={!options.length}
          >
            全选可见
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onChange([])}
            disabled={!selected.length}
          >
            清空
          </Button>
        </div>
      </div>
      <label className="mb-3 flex flex-col gap-2">
        <span className="text-xs font-medium text-muted-foreground">模型筛选</span>
        <Input
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
          placeholder="输入关键字，例如 gpt / gemini / claude"
        />
      </label>
      {loading ? (
        <div className="text-sm text-muted-foreground">正在加载模型库...</div>
      ) : options.length ? (
        <div className="grid max-h-56 gap-2 overflow-auto md:grid-cols-2">
          {options.map((model) => (
            <label key={model} className="flex items-center gap-3 rounded-xl border border-border/60 bg-background px-3 py-2">
              <input
                type="checkbox"
                className="size-4 rounded border-border"
                checked={selected.includes(model)}
                onChange={() => onChange(toggleOption(selected, model))}
              />
              <span className="text-sm text-foreground">{model}</span>
            </label>
          ))}
        </div>
      ) : (
        <div className="text-sm text-muted-foreground">模型库暂无可选项，提交时将由后端按账号分组或提供方自动匹配。</div>
      )}
    </>
  )
}

function parseSecretLines(raw: string) {
  const seen = new Set<string>()
  const secrets: string[] = []
  for (const line of raw.split(/\r?\n/)) {
    const value = line.trim()
    if (!value || seen.has(value)) {
      continue
    }
    seen.add(value)
    secrets.push(value)
  }
  return secrets
}

function parsePathLines(raw: string) {
  const seen = new Set<string>()
  const paths: string[] = []
  for (const line of raw.split(/\r?\n/)) {
    const value = line.trim().replace(/^['"]|['"]$/g, '')
    if (!value || seen.has(value)) {
      continue
    }
    seen.add(value)
    paths.push(value)
  }
  return paths
}

function estimateJsonDocumentCount(raw: string) {
  try {
    return parseJsonDocuments(raw).length
  } catch {
    return raw.trim() ? 1 : 0
  }
}

function parseJsonDocuments(raw: string) {
  const trimmed = raw.trim()
  if (!trimmed) {
    return []
  }
  const parsed = tryParseJson(trimmed)
  if (parsed.ok) {
    const value = parsed.value
    if (Array.isArray(value)) {
      return value.map((item) => stringifyJsonObject(item))
    }
    if (value && typeof value === 'object' && Array.isArray((value as { accounts?: unknown }).accounts)) {
      return ((value as { accounts: unknown[] }).accounts).map((item) => stringifyJsonObject(item))
    }
    return [stringifyJsonObject(value)]
  }

  const documents = raw
    .split(/\n(?=\s*\{)/)
    .map((part) => part.trim())
    .filter(Boolean)
  if (documents.length <= 1) {
    throw new Error('auth.json 内容不是合法 JSON。')
  }
  return documents.map((document) => stringifyJsonObject(JSON.parse(document)))
}

function tryParseJson(raw: string): { ok: true; value: unknown } | { ok: false } {
  try {
    return { ok: true, value: JSON.parse(raw) }
  } catch {
    return { ok: false }
  }
}

function stringifyJsonObject(value: unknown) {
  if (value == null || Array.isArray(value) || typeof value !== 'object') {
    throw new Error('auth.json 内容必须是 JSON 对象或对象数组。')
  }
  return JSON.stringify(value)
}

function buildCodexImportPayloads({
  mode,
  rawPaths,
  rawContent,
  form,
  accountGroups,
}: {
  mode: CodexImportMode
  rawPaths: string
  rawContent: string
  form: CredentialFormState
  accountGroups: AccountGroupOption[]
}) {
  const groupId = requireNumericAccountGroup(form.groupId)
  const selectedGroup = accountGroups.find((group) => group.id === groupId)
  if (selectedGroup && !isCodexAccountGroup(selectedGroup)) {
    throw new Error('Codex auth.json 需要选择 CODEX_OAUTH 账号分组。')
  }
  const baseName = form.credentialName.trim() || 'Codex auth'
  const shared = {
    groupId,
    active: form.active,
    proxyId: parseOptionalInputNumber(form.proxyId),
    tlsFingerprintProfileId: parseOptionalInputNumber(form.tlsFingerprintProfileId),
    siteProfileId: parseOptionalInputNumber(form.siteProfileId),
    protocolEndpointId: parseOptionalInputNumber(form.protocolEndpointId),
    supportedModels: normalizeModelSelection(form.supportedModels),
  }

  if (mode === 'paths') {
    const paths = parsePathLines(rawPaths)
    if (!paths.length) {
      throw new Error('请至少填写一个 auth.json 文件路径。')
    }
    return paths.map((path, index) => ({
      ...shared,
      sourceLabel: `文件 ${index + 1}`,
      accountName: buildIndexedName(baseName, index, paths.length),
      authJsonFilePath: path,
    }))
  }

  const documents = parseJsonDocuments(rawContent)
  if (!documents.length) {
    throw new Error('请粘贴 auth.json 内容。')
  }
  return documents.map((content, index) => ({
    ...shared,
    sourceLabel: `JSON ${index + 1}`,
    accountName: buildIndexedName(baseName, index, documents.length),
    authJsonContent: content,
  }))
}

function buildIndexedName(baseName: string, index: number, total: number) {
  if (total <= 1) {
    return baseName
  }
  return `${baseName}-${String(index + 1).padStart(2, '0')}`
}

function toCodexImportRequestBody(payload: CodexImportPayload): CodexImportRequestBody {
  return {
    groupId: payload.groupId,
    accountName: payload.accountName,
    authJsonContent: payload.authJsonContent,
    authJsonFilePath: payload.authJsonFilePath,
    active: payload.active,
    proxyId: payload.proxyId,
    tlsFingerprintProfileId: payload.tlsFingerprintProfileId,
    siteProfileId: payload.siteProfileId,
    supportedModels: payload.supportedModels,
  }
}

function requireNumericAccountGroup(groupId: string) {
  requireAccountGroup(groupId)
  const parsed = Number(groupId)
  if (!Number.isFinite(parsed)) {
    throw new Error('请选择有效的账号分组。')
  }
  return parsed
}

function parseOptionalInputNumber(value: string) {
  if (!value.trim()) {
    return null
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error('数字字段必须填写有效数字。')
  }
  return parsed
}

function normalizeModelSelection(models: string[]) {
  const deduplicated = new Map<string, string>()
  for (const raw of models) {
    const value = raw.trim()
    if (!value) {
      continue
    }
    const key = value.toLowerCase()
    if (!deduplicated.has(key)) {
      deduplicated.set(key, value)
    }
  }
  return Array.from(deduplicated.values())
}

function isCodexAccountGroup(group: AccountGroupOption) {
  return group.providerType === 'CODEX_OAUTH'
    || group.allowedClientFamilies?.some((family) => family.toUpperCase() === 'CODEX')
}

function mergeModelOptions(catalog: string[], selected: string[]) {
  const deduplicated = new Map<string, string>()
  for (const model of [...catalog, ...selected]) {
    const normalized = model.trim()
    if (!normalized) {
      continue
    }
    const key = normalized.toLowerCase()
    if (!deduplicated.has(key)) {
      deduplicated.set(key, normalized)
    }
  }
  return Array.from(deduplicated.values()).sort((left, right) => left.localeCompare(right))
}

function filterOptions(options: string[], keyword: string) {
  const normalizedKeyword = keyword.trim().toLowerCase()
  if (!normalizedKeyword) {
    return options
  }
  return options.filter((option) => option.toLowerCase().includes(normalizedKeyword))
}

function normalizeCredentialProviderSites(sites: ProviderSite[]) {
  return [...sites]
    .sort((left, right) => {
      const leftVendor = (left.vendorName ?? left.vendorCode ?? '').localeCompare(right.vendorName ?? right.vendorCode ?? '')
      if (leftVendor !== 0) {
        return leftVendor
      }
      return left.displayName.localeCompare(right.displayName)
    })
}

function flattenProviderEndpointOptions(sites: ProviderSite[]): ProviderEndpointOption[] {
  return sites.flatMap((site) =>
    (site.protocolEndpoints ?? []).map((endpoint) => ({
      site,
      endpoint,
    })),
  )
}

function applyProviderEndpointToForm(
  form: CredentialFormState,
  protocolEndpointId: string,
  endpointOptions: ProviderEndpointOption[],
): CredentialFormState {
  const option = findProviderEndpoint(endpointOptions, protocolEndpointId)
  if (!option) {
    return {
      ...form,
      protocolEndpointId,
      protocolEndpointIds: protocolEndpointId ? [protocolEndpointId] : [],
      siteProfileId: '',
      providerType: 'OPENAI_COMPATIBLE',
      baseUrl: '',
    }
  }
  return {
    ...form,
    protocolEndpointId,
    protocolEndpointIds: [protocolEndpointId],
    siteProfileId: String(option.endpoint.siteProfileId),
    providerType: option.endpoint.providerType,
    baseUrl: option.endpoint.baseUrl,
  }
}

function toggleProviderEndpointOnForm(
  form: CredentialFormState,
  protocolEndpointId: string,
  endpointOptions: ProviderEndpointOption[],
): CredentialFormState {
  const nextIds = form.protocolEndpointIds.includes(protocolEndpointId)
    ? form.protocolEndpointIds.filter((id) => id !== protocolEndpointId)
    : [...form.protocolEndpointIds, protocolEndpointId]
  if (!nextIds.length) {
    return {
      ...form,
      protocolEndpointId: '',
      protocolEndpointIds: [],
      siteProfileId: '',
      providerType: 'OPENAI_COMPATIBLE',
      baseUrl: '',
    }
  }
  const primary = nextIds[0]
  const option = findProviderEndpoint(endpointOptions, primary)
  return {
    ...form,
    protocolEndpointId: primary,
    protocolEndpointIds: nextIds,
    siteProfileId: option ? String(option.endpoint.siteProfileId) : '',
    providerType: option ? option.endpoint.providerType : form.providerType,
    baseUrl: option ? option.endpoint.baseUrl : form.baseUrl,
  }
}

function findProviderEndpoint(endpointOptions: ProviderEndpointOption[], protocolEndpointId: string) {
  const id = Number(protocolEndpointId)
  if (!Number.isFinite(id)) {
    return null
  }
  return endpointOptions.find((option) => option.endpoint.id === id) ?? null
}

function providerTypeForSiteKind(siteKind: string) {
  switch (siteKind) {
    case 'OPENAI_DIRECT':
    case 'AZURE_OPENAI':
      return 'OPENAI_DIRECT'
    case 'ANTHROPIC_DIRECT':
      return 'ANTHROPIC_DIRECT'
    case 'GEMINI_DIRECT':
    case 'VERTEX_AI':
      return 'GEMINI_DIRECT'
    case 'OLLAMA_DIRECT':
      return 'OLLAMA_DIRECT'
    default:
      return 'OPENAI_COMPATIBLE'
  }
}

function isSelectableProviderEndpoint(option: ProviderEndpointOption) {
  return option.site.active && option.endpoint.active && Boolean(option.endpoint.baseUrl?.trim())
}

function providerEndpointOptionLabel(option: ProviderEndpointOption) {
  const vendor = option.site.vendorName ?? option.site.vendorCode ?? '未归属厂商'
  const status = isSelectableProviderEndpoint(option) ? '' : '（不可用）'
  return `${vendor} / ${option.site.displayName} / ${option.endpoint.displayName} / ${option.endpoint.protocolSuite}${status}`
}

function selectedEndpointBaseUrlPreview(options: ProviderEndpointOption[], fallback: string) {
  if (options.length === 0) {
    return fallback
  }
  if (options.length === 1) {
    return options[0].endpoint.baseUrl
  }
  return options.map((option) => `${option.endpoint.displayName}: ${option.endpoint.baseUrl}`).join('；')
}

function toggleOption(current: string[], nextValue: string) {
  if (current.includes(nextValue)) {
    return current.filter((item) => item !== nextValue)
  }
  return [...current, nextValue]
}

function sourceTypeLabel(sourceType: UpstreamCredentialInventoryResponse['sourceType']) {
  return sourceType === 'AUTH_JSON_ACCOUNT' ? 'auth.json 账号' : 'API Key'
}

function rowStatusLabel(row: UpstreamCredentialInventoryResponse) {
  if (!row.active) {
    return '停用'
  }
  if (row.sourceType === 'AUTH_JSON_ACCOUNT') {
    if (row.frozen) {
      return '冻结'
    }
    if (row.healthy === false) {
      return '异常'
    }
    return row.refreshStatus ?? '启用'
  }
  return '启用'
}

function rowStatusTone(row: UpstreamCredentialInventoryResponse): StatusTone {
  if (!row.active) {
    return 'warning'
  }
  if (row.sourceType === 'AUTH_JSON_ACCOUNT') {
    if (row.frozen || row.healthy === false) {
      return 'danger'
    }
    if (row.refreshStatus === 'ACCESS_ONLY') {
      return 'warning'
    }
    return 'success'
  }
  return 'success'
}

function inventoryRowToCredential(row: UpstreamCredentialInventoryResponse, providerSites: ProviderSite[]): CredentialResponse {
  const endpointOption = row.protocolEndpointId == null
    ? null
    : flattenProviderEndpointOptions(providerSites).find((option) => option.endpoint.id === row.protocolEndpointId)
  const site = endpointOption?.site ?? (row.siteProfileId == null
    ? null
    : providerSites.find((item) => item.id === row.siteProfileId))
  return {
    id: row.sourceId,
    credentialName: row.displayName,
    providerType: endpointOption?.endpoint.providerType ?? (site == null ? row.providerType : providerTypeForSiteKind(site.siteKind)),
    baseUrl: endpointOption?.endpoint.baseUrl ?? site?.baseUrlPattern ?? row.baseUrl ?? '',
    authKind: row.authKind ?? 'API_KEY',
    supportedModels: row.supportedModels ?? [],
    secretFingerprint: row.secretFingerprint ?? '',
    credentialMetadata: row.metadata ?? {},
    active: row.active,
    cooldownUntil: row.cooldownUntil,
    lastErrorCode: row.lastErrorCode,
    lastErrorMessage: row.lastErrorMessage,
    lastErrorAt: row.lastErrorAt,
    lastUsedAt: row.lastUsedAt,
    totalRequestCount: row.totalRequestCount,
    successfulRequestCount: row.successfulRequestCount,
    failedRequestCount: row.failedRequestCount,
    canceledRequestCount: row.canceledRequestCount,
    totalTokenCount: row.totalTokenCount,
    totalCacheHitTokenCount: row.totalCacheHitTokenCount,
    totalCacheWriteTokenCount: row.totalCacheWriteTokenCount,
    totalSavedInputTokenCount: row.totalSavedInputTokenCount,
    requestSuccessRate: row.requestSuccessRate,
    cacheHitRate: row.cacheHitRate,
    totalDurationMs: row.totalDurationMs,
    durationSampleCount: row.durationSampleCount,
    avgDurationMs: row.avgDurationMs,
    totalFirstTokenMs: row.totalFirstTokenMs,
    firstTokenSampleCount: row.firstTokenSampleCount,
    avgFirstTokenMs: row.avgFirstTokenMs,
    lastFirstTokenMs: row.lastFirstTokenMs,
    minFirstTokenMs: row.minFirstTokenMs,
    maxFirstTokenMs: row.maxFirstTokenMs,
    proxyId: row.proxyId,
    tlsFingerprintProfileId: row.tlsFingerprintProfileId,
    siteProfileId: row.siteProfileId,
    protocolEndpointId: row.protocolEndpointId,
    groupId: row.groupId,
    groupName: row.groupName,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  }
}

function proxyToSelectOption(proxy: ProxyOption): SearchableOption {
  return {
    value: String(proxy.id),
    label: `${proxy.proxyName} (#${proxy.id})`,
    description: `${proxy.active ? '启用' : '停用'} ${proxy.proxyUrl}`,
  }
}

function tlsProfileToSelectOption(profile: TlsProfileOption): SearchableOption {
  return {
    value: String(profile.id),
    label: `${profile.profileName} (#${profile.id})`,
    description: `${profile.active ? '启用' : '停用'} ${profile.profileCode}`,
  }
}

function optionMatches(option: SearchableOption, keyword: string) {
  const normalized = keyword.trim().toLowerCase()
  if (!normalized) {
    return true
  }
  return `${option.value} ${option.label} ${option.description ?? ''}`.toLowerCase().includes(normalized)
}

function resolveProxyLabel(options: ProxyOption[], id?: number | null) {
  if (id == null) {
    return '未绑定'
  }
  const option = options.find((item) => item.id === id)
  return option ? `${option.proxyName} (#${option.id})` : `#${id}`
}

function resolveTlsProfileLabel(options: TlsProfileOption[], id?: number | null) {
  if (id == null) {
    return '未绑定'
  }
  const option = options.find((item) => item.id === id)
  return option ? `${option.profileName} (#${option.id})` : `#${id}`
}

function invalidateCredentialData(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['credentials'] })
  queryClient.invalidateQueries({ queryKey: ['credentials', 'inventory'] })
}

function resolveErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '未知错误'
}

function requireAccountGroup(groupId: string) {
  if (!groupId.trim()) {
    throw new Error('请选择账号分组后再保存上游凭证。')
  }
}

function resolveGroupName(groups: AccountGroupOption[], groupId: string) {
  const parsed = Number(groupId)
  if (!Number.isFinite(parsed)) {
    return null
  }
  return groups.find((group) => group.id === parsed)?.groupName ?? null
}
