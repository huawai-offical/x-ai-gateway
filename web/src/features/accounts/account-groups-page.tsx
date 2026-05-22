import { type ChangeEvent, type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
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
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { apiRequest } from '@/lib/api'

type AccountGroup = {
  id: number
  groupName: string
  providerType: string
  supportedModels: string[]
  supportedProtocols: string[]
  allowedClientFamilies: string[]
  description?: string | null
  defaultGroup?: boolean
  oauthAccountCount?: number
  apiCredentialCount?: number
  totalAccountCount?: number
  active: boolean
  createdAt?: string | null
  updatedAt?: string | null
}

type AccountGroupForm = {
  groupName: string
  providerType: string
  supportedModelsCsv: string
  supportedProtocolsCsv: string
  allowedClientFamiliesCsv: string
  description: string
  active: boolean
}

type AuthJsonImportForm = {
  accountName: string
  externalAccountId: string
  accessToken: string
  refreshToken: string
  metadataJson: string
  supportedModels: string[]
  active: boolean
  proxyId: string
  tlsFingerprintProfileId: string
  siteProfileId: string
}

type ImportMode = 'single' | 'batch'
type ImportStep = 'group' | 'source' | 'submit'
type CreateStep = 'basic' | 'scope' | 'submit'

type BatchImportOverride = {
  active: boolean
  proxyId: string
  tlsFingerprintProfileId: string
  siteProfileId: string
}

type BatchImportFailure = {
  accountName: string
  message: string
}

type BatchImportResult = {
  total: number
  success: number
  failed: BatchImportFailure[]
}

const CREATE_STEPS: CreateStep[] = ['basic', 'scope', 'submit']
const IMPORT_STEPS: ImportStep[] = ['group', 'source', 'submit']
const OAUTH_PROVIDER_OPTIONS = ['OPENAI_OAUTH', 'GEMINI_OAUTH', 'CLAUDE_ACCOUNT'] as const
const PROTOCOL_OPTIONS = ['openai', 'responses', 'anthropic', 'gemini'] as const
const CLIENT_FAMILY_OPTIONS = ['GENERIC_OPENAI', 'CODEX', 'GEMINI_CLI', 'CLAUDE_CODE'] as const

const PROVIDER_DEFAULTS: Record<string, Pick<AccountGroupForm, 'supportedProtocolsCsv' | 'allowedClientFamiliesCsv'>> = {
  OPENAI_OAUTH: {
    supportedProtocolsCsv: 'openai,responses',
    allowedClientFamiliesCsv: 'GENERIC_OPENAI,CODEX',
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

export function AccountGroupsPage() {
  const queryClient = useQueryClient()
  const [createForm, setCreateForm] = useState<AccountGroupForm>(createEmptyForm())
  const [editingGroupId, setEditingGroupId] = useState<number | null>(null)
  const [editingForm, setEditingForm] = useState<AccountGroupForm>(createEmptyForm())
  const [editingModelPickerOpen, setEditingModelPickerOpen] = useState(false)
  const [editingModelKeyword, setEditingModelKeyword] = useState('')
  const [createModelPickerOpen, setCreateModelPickerOpen] = useState(false)
  const [createModelKeyword, setCreateModelKeyword] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [updateError, setUpdateError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [createStep, setCreateStep] = useState<CreateStep>('basic')

  const [importOpen, setImportOpen] = useState(false)
  const [importStep, setImportStep] = useState<ImportStep>('group')
  const [importMode, setImportMode] = useState<ImportMode>('single')
  const [importGroupId, setImportGroupId] = useState('')
  const [importRaw, setImportRaw] = useState('')
  const [importError, setImportError] = useState<string | null>(null)
  const [importWarning, setImportWarning] = useState<string | null>(null)
  const [singleImportForm, setSingleImportForm] = useState<AuthJsonImportForm>(createEmptyImportForm())
  const [batchImportForms, setBatchImportForms] = useState<AuthJsonImportForm[]>([])
  const [batchOverride, setBatchOverride] = useState<BatchImportOverride>(createEmptyBatchOverride())
  const [importResult, setImportResult] = useState<BatchImportResult | null>(null)

  const groupsQuery = useQuery({
    queryKey: ['account-groups'],
    queryFn: () => apiRequest<AccountGroup[]>('/admin/account-groups'),
  })
  const createModelCatalogQuery = useQuery({
    queryKey: ['account-groups', 'model-catalog', 'create', createForm.providerType],
    queryFn: () =>
      apiRequest<string[]>(
        `/admin/account-groups/model-catalog?providerType=${encodeURIComponent(createForm.providerType)}`,
      ),
    enabled: createOpen,
  })
  const editingModelCatalogQuery = useQuery({
    queryKey: ['account-groups', 'model-catalog', 'edit', editingForm.providerType],
    queryFn: () =>
      apiRequest<string[]>(
        `/admin/account-groups/model-catalog?providerType=${encodeURIComponent(editingForm.providerType)}`,
      ),
    enabled: editingGroupId != null,
  })

  const createMutation = useMutation({
    mutationFn: (payload: ReturnType<typeof buildAccountGroupPayload>) =>
      apiRequest<AccountGroup>('/admin/account-groups', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      setCreateForm(createEmptyForm())
      setCreateError(null)
      setCreateStep('basic')
      setCreateOpen(false)
      queryClient.invalidateQueries({ queryKey: ['account-groups'] })
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ReturnType<typeof buildAccountGroupPayload> }) =>
      apiRequest<AccountGroup>(`/admin/account-groups/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),
    onSuccess: () => {
      setEditingGroupId(null)
      setEditingForm(createEmptyForm())
      setUpdateError(null)
      queryClient.invalidateQueries({ queryKey: ['account-groups'] })
      queryClient.invalidateQueries({ queryKey: ['account-group'] })
    },
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      apiRequest<AccountGroup>(`/admin/account-groups/${id}/status?active=${active}`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account-groups'] })
      queryClient.invalidateQueries({ queryKey: ['account-group'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/account-groups/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: (_data: void, deletedId: number) => {
      if (editingGroupId === deletedId) {
        setEditingGroupId(null)
      }
      queryClient.invalidateQueries({ queryKey: ['account-groups'] })
      queryClient.invalidateQueries({ queryKey: ['account-group'] })
    },
  })

  const importMutation = useMutation({
    mutationFn: async ({ groupId, forms }: { groupId: number; forms: AuthJsonImportForm[] }): Promise<BatchImportResult> => {
      const failed: BatchImportFailure[] = []
      let success = 0

      for (const form of forms) {
        try {
          await apiRequest('/admin/accounts/import-auth-json', {
            method: 'POST',
            body: JSON.stringify(buildAuthJsonImportPayload(groupId, form)),
          })
          success += 1
        } catch (error) {
          failed.push({
            accountName: form.accountName || '(未命名账号)',
            message: resolveErrorMessage(error),
          })
        }
      }

      return {
        total: forms.length,
        success,
        failed,
      } satisfies BatchImportResult
    },
    onSuccess: (result: BatchImportResult) => {
      setImportResult(result)
      if (result.success > 0) {
        queryClient.invalidateQueries({ queryKey: ['account-groups'] })
      }
      if (result.failed.length === 0) {
        setImportOpen(false)
        resetImportState(setImportStep, setImportMode, setImportGroupId, setImportRaw, setImportError, setImportWarning, setSingleImportForm, setBatchImportForms, setBatchOverride, setImportResult)
      }
    },
  })

  const mutationError = toggleMutation.error ?? deleteMutation.error
  const groups = useMemo(() => (groupsQuery.data ?? []) as AccountGroup[], [groupsQuery.data])
  const selectedImportGroup = useMemo(() => groups.find((group) => String(group.id) === importGroupId) ?? null, [groups, importGroupId])
  const editingProtocols = useMemo(
    () => parseCsv(editingForm.supportedProtocolsCsv),
    [editingForm.supportedProtocolsCsv],
  )
  const editingClientFamilies = useMemo(
    () => parseCsv(editingForm.allowedClientFamiliesCsv),
    [editingForm.allowedClientFamiliesCsv],
  )
  const editingModels = useMemo(
    () => parseCsv(editingForm.supportedModelsCsv),
    [editingForm.supportedModelsCsv],
  )
  const createModels = useMemo(
    () => parseCsv(createForm.supportedModelsCsv),
    [createForm.supportedModelsCsv],
  )
  const editingModelCatalog = useMemo(
    () => mergeModelOptions(editingModelCatalogQuery.data ?? [], editingModels),
    [editingModelCatalogQuery.data, editingModels],
  )
  const createModelCatalog = useMemo(
    () => mergeModelOptions(createModelCatalogQuery.data ?? [], createModels),
    [createModelCatalogQuery.data, createModels],
  )
  const filteredEditingModels = useMemo(() => {
    const keyword = editingModelKeyword.trim().toLowerCase()
    if (!keyword) {
      return editingModelCatalog
    }
    return editingModelCatalog.filter((model) => model.toLowerCase().includes(keyword))
  }, [editingModelCatalog, editingModelKeyword])
  const filteredCreateModels = useMemo(() => {
    const keyword = createModelKeyword.trim().toLowerCase()
    if (!keyword) {
      return createModelCatalog
    }
    return createModelCatalog.filter((model) => model.toLowerCase().includes(keyword))
  }, [createModelCatalog, createModelKeyword])

  const createStepIndex = CREATE_STEPS.indexOf(createStep)
  const canCreatePrev = createStepIndex > 0
  const canCreateNext = createStepIndex < CREATE_STEPS.length - 1
  const importStepIndex = IMPORT_STEPS.indexOf(importStep)
  const canImportPrev = importStepIndex > 0
  const canImportNext = importStepIndex < IMPORT_STEPS.length - 1

  const handleCreate = (event: FormEvent) => {
    event.preventDefault()
    try {
      setCreateError(null)
      createMutation.mutate(buildAccountGroupPayload(createForm))
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : '无法创建账号分组。')
    }
  }

  const setEditingProtocols = (nextValues: string[]) => {
    setEditingForm((current) => ({ ...current, supportedProtocolsCsv: toCsv(nextValues) }))
  }

  const setEditingClientFamilies = (nextValues: string[]) => {
    setEditingForm((current) => ({ ...current, allowedClientFamiliesCsv: toCsv(nextValues) }))
  }

  const setEditingModels = (nextValues: string[]) => {
    setEditingForm((current) => ({ ...current, supportedModelsCsv: toCsv(nextValues) }))
  }
  const setCreateModels = (nextValues: string[]) => {
    setCreateForm((current) => ({ ...current, supportedModelsCsv: toCsv(nextValues) }))
  }

  const handleSelectAllVisibleModels = () => {
    setEditingModels(Array.from(new Set([...editingModels, ...filteredEditingModels])))
  }

  const handleClearVisibleModels = () => {
    const visible = new Set(filteredEditingModels)
    setEditingModels(editingModels.filter((model) => !visible.has(model)))
  }
  const handleSelectAllVisibleCreateModels = () => {
    setCreateModels(Array.from(new Set([...createModels, ...filteredCreateModels])))
  }
  const handleClearVisibleCreateModels = () => {
    const visible = new Set(filteredCreateModels)
    setCreateModels(createModels.filter((model) => !visible.has(model)))
  }

  const handleUpdate = (event: FormEvent) => {
    event.preventDefault()
    if (editingGroupId == null) {
      return
    }
    try {
      setUpdateError(null)
      updateMutation.mutate({
        id: editingGroupId,
        payload: buildAccountGroupPayload(editingForm),
      })
    } catch (error) {
      setUpdateError(error instanceof Error ? error.message : '无法更新账号分组。')
    }
  }

  const openImportDialog = () => {
    resetImportState(setImportStep, setImportMode, setImportGroupId, setImportRaw, setImportError, setImportWarning, setSingleImportForm, setBatchImportForms, setBatchOverride, setImportResult)
    if (groups.length) {
      setImportGroupId(String(groups[0].id))
    }
    setImportOpen(true)
  }

  const handleImportSourceFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }
    const text = await file.text()
    setImportRaw(text)
    event.target.value = ''
  }

  const parseImportSource = () => {
    if (!selectedImportGroup) {
      throw new Error('请先选择目标账号分组。')
    }
    if (!importRaw.trim()) {
      throw new Error('请先粘贴认证文本或导入文件。')
    }

    if (importMode === 'single') {
      const parsed = parseAuthJsonImport(importRaw, selectedImportGroup)
      setSingleImportForm(parsed)
      setBatchImportForms([])
      setImportWarning(null)
      return
    }

    const parsedBatch = parseAuthJsonBatch(importRaw, selectedImportGroup)
    setBatchImportForms(parsedBatch.forms)
    setImportWarning(parsedBatch.warnings.length ? parsedBatch.warnings.slice(0, 3).join('；') : null)
  }

  const handleImportNext = () => {
    if (importStep === 'group') {
      setImportError(null)
      setImportStep('source')
      return
    }

    if (importStep === 'source') {
      try {
        setImportError(null)
        setImportResult(null)
        parseImportSource()
        setImportStep('submit')
      } catch (error) {
        setImportError(error instanceof Error ? error.message : '导入内容解析失败。')
      }
    }
  }

  const handleImportSubmit = (event: FormEvent) => {
    event.preventDefault()
    try {
      if (!selectedImportGroup) {
        throw new Error('请先选择目标账号分组。')
      }
      setImportError(null)
      const forms = importMode === 'single'
        ? [singleImportForm]
        : batchImportForms.map((form) => applyBatchOverride(form, batchOverride))
      if (!forms.length) {
        throw new Error('没有可导入的账号记录。')
      }
      importMutation.mutate({
        groupId: selectedImportGroup.id,
        forms,
      })
    } catch (error) {
      setImportError(error instanceof Error ? error.message : '导入账号失败。')
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="账号分组管理"
        title="账号分组与治理"
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={openImportDialog} disabled={!groups.length && !groupsQuery.isPending}>
              导入官方账号
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setCreateError(null)
                setCreateStep('basic')
                setCreateModelKeyword('')
                setCreateModelPickerOpen(false)
                setCreateOpen(true)
              }}
            >
              创建账号分组
            </Button>
          </div>
        )}
      >
        {mutationError ? (
          <InlineError
            error={mutationError}
            title="账号分组操作失败"
          />
        ) : null}

        {groupsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : groupsQuery.error ? (
          <InlineError error={groupsQuery.error} title="账号分组列表加载失败" />
        ) : groups.length ? (
          <PaginatedRows items={groups}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号分组名称</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">账号数</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型数</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近更新</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((group) => {
                      const oauthCount = group.oauthAccountCount ?? 0
                      const apiCount = group.apiCredentialCount ?? 0
                      const totalCount = group.totalAccountCount ?? oauthCount + apiCount
                      return (
                        <tr key={group.id} className="border-b border-border/40 align-top">
                          <td className="px-4 py-3 font-medium text-foreground">
                            {group.groupName}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge tone={group.active ? 'success' : 'warning'}>{group.active ? '启用' : '停用'}</StatusBadge>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{group.providerType}</td>
                          <td className="px-4 py-3 text-muted-foreground">
                            OAuth {formatCount(oauthCount)} / API {formatCount(apiCount)} / 总计 {formatCount(totalCount)}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{formatCount(group.supportedModels.length)}</td>
                          <td className="px-4 py-3 text-muted-foreground">{formatInstant(group.updatedAt)}</td>
                          <td className="px-4 py-3">
                            <Button type="button" variant="outline" size="sm" asChild>
                              <Link to={`/console/account-groups/${group.id}`}>查看</Link>
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
          <EmptyState title="还没有账号分组" />
        )}
      </PageSection>

      <Dialog
        open={editingGroupId != null}
        onOpenChange={(open) => {
          if (!open) {
            setEditingGroupId(null)
            setUpdateError(null)
            setEditingModelPickerOpen(false)
            setEditingModelKeyword('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑账号分组</DialogTitle>
            <DialogDescription>更新账号分组信息。</DialogDescription>
          </DialogHeader>

          <form className="flex flex-col gap-4" onSubmit={handleUpdate}>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">账号分组名称</span>
              <Input
                value={editingForm.groupName}
                onChange={(event) => setEditingForm((current) => ({ ...current, groupName: event.target.value }))}
              />
            </label>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">提供方类型</span>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={editingForm.providerType}
                onChange={(event) =>
                  setEditingForm((current) => applyProviderDefaults(current, event.target.value))
                }
              >
                {OAUTH_PROVIDER_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
            <MultiSelectDropdownField
              label="支持协议"
              options={PROTOCOL_OPTIONS}
              selected={editingProtocols}
              placeholder="请选择协议（留空表示不限制）"
              onToggle={(value) => setEditingProtocols(toggleOption(editingProtocols, value))}
              onSelectAll={() => setEditingProtocols([...PROTOCOL_OPTIONS])}
              onClearAll={() => setEditingProtocols([])}
            />
            <div className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">支持模型</span>
              <div className="flex flex-wrap items-center gap-2">
                <Button type="button" variant="outline" onClick={() => setEditingModelPickerOpen(true)}>
                  选择模型
                </Button>
                <Button type="button" variant="outline" onClick={() => setEditingModels([])} disabled={editingModels.length === 0}>
                  清空模型
                </Button>
                <span className="text-sm text-muted-foreground">
                  已选择 {editingModels.length} 个：{summarizeItems(editingModels, '无', 3)}
                </span>
              </div>
            </div>
            <MultiSelectDropdownField
              label="允许客户端"
              options={CLIENT_FAMILY_OPTIONS}
              selected={editingClientFamilies}
              placeholder="请选择客户端（留空表示全部）"
              onToggle={(value) => setEditingClientFamilies(toggleOption(editingClientFamilies, value))}
              onSelectAll={() => setEditingClientFamilies([...CLIENT_FAMILY_OPTIONS])}
              onClearAll={() => setEditingClientFamilies([])}
            />
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">说明</span>
              <Textarea
                rows={4}
                value={editingForm.description}
                onChange={(event) => setEditingForm((current) => ({ ...current, description: event.target.value }))}
              />
            </label>
            <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-background px-4 py-3">
              <input
                type="checkbox"
                className="size-4 rounded border-border"
                checked={editingForm.active}
                onChange={(event) => setEditingForm((current) => ({ ...current, active: event.target.checked }))}
              />
              <span className="text-sm font-medium text-foreground">启用账号分组</span>
            </label>

            {(updateMutation.error || updateError) ? (
              <InlineError
                error={updateMutation.error ?? new Error(updateError ?? '更新账号分组失败')}
                title="更新账号分组失败"
              />
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditingGroupId(null)}>
                取消
              </Button>
              <Button type="submit" disabled={updateMutation.isPending}>
                保存
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={editingModelPickerOpen}
        onOpenChange={(open) => {
          setEditingModelPickerOpen(open)
          if (!open) {
            setEditingModelKeyword('')
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>支持模型选择</DialogTitle>
            <DialogDescription>选择支持模型。</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">模型筛选</span>
              <Input
                value={editingModelKeyword}
                onChange={(event) => setEditingModelKeyword(event.target.value)}
                placeholder="输入关键字，例如 gpt / gemini / claude"
              />
            </label>

            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={handleSelectAllVisibleModels}>
                全选可见
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={handleClearVisibleModels}>
                清空可见
              </Button>
              <span className="self-center text-sm text-muted-foreground">
                可见 {filteredEditingModels.length} 个，已选 {editingModels.length} 个
              </span>
            </div>

            <div className="max-h-80 overflow-auto rounded-2xl border border-border/60 bg-muted/10 p-3">
              <div className="grid gap-2 md:grid-cols-2">
                {filteredEditingModels.map((model) => (
                  <label key={model} className="flex items-center gap-3 rounded-xl border border-border/60 bg-background px-3 py-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={editingModels.includes(model)}
                      onChange={() => setEditingModels(toggleOption(editingModels, model))}
                    />
                    <span className="text-sm text-foreground">{model}</span>
                  </label>
                ))}
              </div>
              {filteredEditingModels.length === 0 ? (
                <div className="py-6 text-center text-sm text-muted-foreground">没有匹配模型，可调整筛选关键字。</div>
              ) : null}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setEditingModelPickerOpen(false)}>
              完成
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={createModelPickerOpen}
        onOpenChange={(open) => {
          setCreateModelPickerOpen(open)
          if (!open) {
            setCreateModelKeyword('')
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>支持模型选择</DialogTitle>
            <DialogDescription>选择支持模型。</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">模型筛选</span>
              <Input
                value={createModelKeyword}
                onChange={(event) => setCreateModelKeyword(event.target.value)}
                placeholder="输入关键字，例如 gpt / gemini / claude"
              />
            </label>

            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={handleSelectAllVisibleCreateModels}>
                全选可见
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={handleClearVisibleCreateModels}>
                清空可见
              </Button>
              <span className="self-center text-sm text-muted-foreground">
                可见 {filteredCreateModels.length} 个，已选 {createModels.length} 个
              </span>
            </div>

            <div className="max-h-80 overflow-auto rounded-2xl border border-border/60 bg-muted/10 p-3">
              <div className="grid gap-2 md:grid-cols-2">
                {filteredCreateModels.map((model) => (
                  <label key={model} className="flex items-center gap-3 rounded-xl border border-border/60 bg-background px-3 py-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={createModels.includes(model)}
                      onChange={() => setCreateModels(toggleOption(createModels, model))}
                    />
                    <span className="text-sm text-foreground">{model}</span>
                  </label>
                ))}
              </div>
              {filteredCreateModels.length === 0 ? (
                <div className="py-6 text-center text-sm text-muted-foreground">模型库为空或未匹配到结果。</div>
              ) : null}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setCreateModelPickerOpen(false)}>
              完成
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open)
          if (!open) {
            setCreateError(null)
            setCreateModelKeyword('')
            setCreateModelPickerOpen(false)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建账号分组</DialogTitle>
            <DialogDescription>填写账号分组信息。</DialogDescription>
          </DialogHeader>

          <form className="flex flex-col gap-4" onSubmit={handleCreate}>
            <Tabs value={createStep} onValueChange={(value) => setCreateStep(value as CreateStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="scope">2. 能力范围</TabsTrigger>
                <TabsTrigger value="submit">3. 说明与提交</TabsTrigger>
              </TabsList>

              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">账号分组名称</span>
                    <Input
                      value={createForm.groupName}
                      onChange={(event) => setCreateForm((current) => ({ ...current, groupName: event.target.value }))}
                      placeholder="例如：OpenAI OAuth 分组"
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">提供方类型</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={createForm.providerType}
                      onChange={(event) => setCreateForm((current) => applyProviderDefaults(current, event.target.value))}
                    >
                      {OAUTH_PROVIDER_OPTIONS.map((option) => (
                        <option key={option} value={option}>
                          {option}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={createForm.active}
                      onChange={(event) => setCreateForm((current) => ({ ...current, active: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">创建后立即启用</span>
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="scope" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">支持协议（逗号分隔）</span>
                    <Input
                      value={createForm.supportedProtocolsCsv}
                      onChange={(event) => setCreateForm((current) => ({ ...current, supportedProtocolsCsv: event.target.value }))}
                      placeholder="openai,responses"
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">允许客户端（逗号分隔）</span>
                    <Input
                      value={createForm.allowedClientFamiliesCsv}
                      onChange={(event) => setCreateForm((current) => ({ ...current, allowedClientFamiliesCsv: event.target.value }))}
                      placeholder="GENERIC_OPENAI,CODEX"
                    />
                  </label>
                  <div className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">支持模型</span>
                    <div className="flex flex-wrap items-center gap-2">
                      <Button type="button" variant="outline" onClick={() => setCreateModelPickerOpen(true)}>
                        选择模型
                      </Button>
                      <Button type="button" variant="outline" onClick={() => setCreateModels([])} disabled={createModels.length === 0}>
                        清空模型
                      </Button>
                      <span className="text-sm text-muted-foreground">
                        已选择 {createModels.length} 个：{summarizeItems(createModels, '无', 3)}
                      </span>
                    </div>
                    {createModelCatalogQuery.isPending ? (
                      <span className="text-xs text-muted-foreground">正在加载模型库...</span>
                    ) : null}
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="submit" className="pt-3">
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">说明</span>
                  <Textarea
                    rows={4}
                    value={createForm.description}
                    onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))}
                    placeholder="可选说明"
                  />
                </label>
              </TabsContent>
            </Tabs>

            {(createMutation.error || createError) ? (
              <InlineError
                error={createMutation.error ?? new Error(createError ?? '创建账号分组失败')}
                title="创建账号分组失败"
              />
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateStep(CREATE_STEPS[Math.max(0, createStepIndex - 1)])} disabled={!canCreatePrev}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={() => setCreateStep(CREATE_STEPS[Math.min(CREATE_STEPS.length - 1, createStepIndex + 1)])} disabled={!canCreateNext}>
                下一步
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                创建账号分组
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={importOpen}
        onOpenChange={(open) => {
          setImportOpen(open)
          if (!open) {
            resetImportState(setImportStep, setImportMode, setImportGroupId, setImportRaw, setImportError, setImportWarning, setSingleImportForm, setBatchImportForms, setBatchOverride, setImportResult)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>导入官方账号认证</DialogTitle>
            <DialogDescription>选择分组并导入认证信息。</DialogDescription>
          </DialogHeader>

          <form className="flex flex-col gap-4" onSubmit={handleImportSubmit}>
            <Tabs value={importStep} onValueChange={(value) => setImportStep(value as ImportStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="group">1. 归属分组</TabsTrigger>
                <TabsTrigger value="source">2. 导入源</TabsTrigger>
                <TabsTrigger value="submit">3. 提交导入</TabsTrigger>
              </TabsList>

              <TabsContent value="group" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">目标账号分组</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={importGroupId}
                      onChange={(event) => setImportGroupId(event.target.value)}
                    >
                      <option value="">请选择账号分组</option>
                      {groups.map((group) => (
                        <option key={group.id} value={String(group.id)}>
                          {group.groupName} ({group.providerType})
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">导入模式</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={importMode}
                      onChange={(event) => setImportMode(event.target.value as ImportMode)}
                    >
                      <option value="single">单条导入</option>
                      <option value="batch">批量导入</option>
                    </select>
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="source" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">导入文件</span>
                    <Input type="file" accept=".json,.txt,text/plain,application/json" onChange={handleImportSourceFileChange} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">{importMode === 'single' ? 'auth.json 文本' : '批量文本（支持 JSON 数组 / 每行 JSON / 每行 token）'}</span>
                    <Textarea
                      rows={8}
                      value={importRaw}
                      onChange={(event) => setImportRaw(event.target.value)}
                      placeholder={importMode === 'single' ? '{"access_token":"...","refresh_token":"..."}' : '{"access_token":"token-1"}\n{"access_token":"token-2"}'}
                    />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="submit" className="pt-3">
                {importMode === 'single' ? (
                  <div className="grid gap-4 md:grid-cols-2">
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">账号名称</span>
                      <Input value={singleImportForm.accountName} onChange={(event) => setSingleImportForm((current) => ({ ...current, accountName: event.target.value }))} />
                    </label>
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">外部账号 ID</span>
                      <Input value={singleImportForm.externalAccountId} onChange={(event) => setSingleImportForm((current) => ({ ...current, externalAccountId: event.target.value }))} />
                    </label>
                    <label className="flex flex-col gap-2 md:col-span-2">
                      <span className="text-sm font-medium text-foreground">访问令牌</span>
                      <Input value={singleImportForm.accessToken} onChange={(event) => setSingleImportForm((current) => ({ ...current, accessToken: event.target.value }))} />
                    </label>
                    <label className="flex flex-col gap-2 md:col-span-2">
                      <span className="text-sm font-medium text-foreground">刷新令牌（可选）</span>
                      <Input value={singleImportForm.refreshToken} onChange={(event) => setSingleImportForm((current) => ({ ...current, refreshToken: event.target.value }))} />
                    </label>
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">代理 ID（可选）</span>
                      <Input value={singleImportForm.proxyId} onChange={(event) => setSingleImportForm((current) => ({ ...current, proxyId: event.target.value }))} />
                    </label>
                    <label className="flex flex-col gap-2">
                      <span className="text-sm font-medium text-foreground">TLS 画像 ID（可选）</span>
                      <Input value={singleImportForm.tlsFingerprintProfileId} onChange={(event) => setSingleImportForm((current) => ({ ...current, tlsFingerprintProfileId: event.target.value }))} />
                    </label>
                    <label className="flex flex-col gap-2 md:col-span-2">
                      <span className="text-sm font-medium text-foreground">站点画像 ID（可选）</span>
                      <Input value={singleImportForm.siteProfileId} onChange={(event) => setSingleImportForm((current) => ({ ...current, siteProfileId: event.target.value }))} />
                    </label>
                    <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                      <input
                        type="checkbox"
                        className="size-4 rounded border-border"
                        checked={singleImportForm.active}
                        onChange={(event) => setSingleImportForm((current) => ({ ...current, active: event.target.checked }))}
                      />
                      <span className="text-sm font-medium text-foreground">导入后立即启用账号</span>
                    </label>
                    <label className="flex flex-col gap-2 md:col-span-2">
                        <span className="text-sm font-medium text-foreground">元数据 JSON（可选）</span>
                      <Textarea rows={5} value={singleImportForm.metadataJson} onChange={(event) => setSingleImportForm((current) => ({ ...current, metadataJson: event.target.value }))} />
                    </label>
                  </div>
                ) : (
                  <div className="grid gap-4">
                    <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                      本次预计批量导入 {batchImportForms.length} 个账号。
                    </div>
                    <div className="grid gap-4 md:grid-cols-2">
                      <label className="flex flex-col gap-2">
                        <span className="text-sm font-medium text-foreground">统一代理 ID（可选）</span>
                        <Input value={batchOverride.proxyId} onChange={(event) => setBatchOverride((current) => ({ ...current, proxyId: event.target.value }))} />
                      </label>
                      <label className="flex flex-col gap-2">
                        <span className="text-sm font-medium text-foreground">统一 TLS 画像 ID（可选）</span>
                        <Input value={batchOverride.tlsFingerprintProfileId} onChange={(event) => setBatchOverride((current) => ({ ...current, tlsFingerprintProfileId: event.target.value }))} />
                      </label>
                      <label className="flex flex-col gap-2 md:col-span-2">
                        <span className="text-sm font-medium text-foreground">统一站点画像 ID（可选）</span>
                        <Input value={batchOverride.siteProfileId} onChange={(event) => setBatchOverride((current) => ({ ...current, siteProfileId: event.target.value }))} />
                      </label>
                      <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                        <input
                          type="checkbox"
                          className="size-4 rounded border-border"
                          checked={batchOverride.active}
                          onChange={(event) => setBatchOverride((current) => ({ ...current, active: event.target.checked }))}
                        />
                        <span className="text-sm font-medium text-foreground">批量导入后统一启用账号</span>
                      </label>
                    </div>
                    <div className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-sm text-muted-foreground">
                      示例账号：{batchImportForms.slice(0, 3).map((item) => item.accountName || '(自动命名)').join('，') || '无'}
                    </div>
                  </div>
                )}
              </TabsContent>
            </Tabs>

            {(importError || importMutation.error) ? (
              <InlineError error={importMutation.error ?? new Error(importError ?? '导入账号失败')} title="导入账号失败" />
            ) : null}
            {importWarning ? (
              <div className="rounded-2xl border border-border/60 bg-card/80 px-4 py-3 text-sm text-foreground">
                解析提示：{importWarning}
              </div>
            ) : null}
            {importResult ? (
              <div className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-sm text-foreground">
                导入完成：总计 {importResult.total}，成功 {importResult.success}，失败 {importResult.failed.length}。
                {importResult.failed.length ? (
                  <div className="mt-2 text-muted-foreground">
                    失败项（最多展示 5 条）：{importResult.failed.slice(0, 5).map((item) => `${item.accountName}: ${item.message}`).join('；')}
                  </div>
                ) : null}
              </div>
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setImportStep(IMPORT_STEPS[Math.max(0, importStepIndex - 1)])} disabled={!canImportPrev}>
                上一步
              </Button>
              {canImportNext ? (
                <Button type="button" variant="outline" onClick={handleImportNext}>
                  {importStep === 'source' ? '解析并下一步' : '下一步'}
                </Button>
              ) : null}
              <Button type="submit" disabled={importMutation.isPending || (importMode === 'single' ? !singleImportForm.accessToken.trim() : batchImportForms.length === 0)}>
                {importMode === 'single' ? '导入账号' : '批量导入'}
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
                <label key={option} className="flex items-center gap-3 rounded-xl border border-border/60 bg-muted/10 px-3 py-2">
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

function createEmptyForm(): AccountGroupForm {
  return {
    groupName: '',
    providerType: 'OPENAI_OAUTH',
    supportedModelsCsv: '',
    supportedProtocolsCsv: 'openai,responses',
    allowedClientFamiliesCsv: 'GENERIC_OPENAI,CODEX',
    description: '',
    active: true,
  }
}

function createEmptyImportForm(): AuthJsonImportForm {
  return {
    accountName: '',
    externalAccountId: '',
    accessToken: '',
    refreshToken: '',
    metadataJson: '{}',
    supportedModels: [],
    active: true,
    proxyId: '',
    tlsFingerprintProfileId: '',
    siteProfileId: '',
  }
}

function createEmptyBatchOverride(): BatchImportOverride {
  return {
    active: true,
    proxyId: '',
    tlsFingerprintProfileId: '',
    siteProfileId: '',
  }
}

function resetImportState(
  setImportStep: (value: ImportStep) => void,
  setImportMode: (value: ImportMode) => void,
  setImportGroupId: (value: string) => void,
  setImportRaw: (value: string) => void,
  setImportError: (value: string | null) => void,
  setImportWarning: (value: string | null) => void,
  setSingleImportForm: (value: AuthJsonImportForm) => void,
  setBatchImportForms: (value: AuthJsonImportForm[]) => void,
  setBatchOverride: (value: BatchImportOverride) => void,
  setImportResult: (value: BatchImportResult | null) => void,
) {
  setImportStep('group')
  setImportMode('single')
  setImportGroupId('')
  setImportRaw('')
  setImportError(null)
  setImportWarning(null)
  setSingleImportForm(createEmptyImportForm())
  setBatchImportForms([])
  setBatchOverride(createEmptyBatchOverride())
  setImportResult(null)
}

function buildAccountGroupPayload(form: AccountGroupForm) {
  if (!form.groupName.trim()) {
    throw new Error('账号分组名称不能为空。')
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
  const normalized = value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

  return Array.from(new Set(normalized))
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

function parseAuthJsonBatch(raw: string, group: AccountGroup): { forms: AuthJsonImportForm[]; warnings: string[] } {
  const trimmed = raw.trim()
  if (!trimmed) {
    throw new Error('导入内容不能为空。')
  }

  const warnings: string[] = []
  const forms: AuthJsonImportForm[] = []
  const pushToken = (token: string) => {
    forms.push({
      ...createEmptyImportForm(),
      accessToken: token,
    })
  }

  const parseObject = (value: unknown, hint: string) => {
    if (typeof value === 'string') {
      const token = value.trim()
      if (token) {
        pushToken(token)
      }
      return
    }
    if (!isRecord(value)) {
      warnings.push(`${hint} 不是 JSON 对象，已跳过。`)
      return
    }
    try {
      forms.push(parseAuthJsonImport(JSON.stringify(value), group))
    } catch (error) {
      warnings.push(`${hint} 解析失败：${resolveErrorMessage(error)}`)
    }
  }

  try {
    const parsed = JSON.parse(trimmed)
    if (Array.isArray(parsed)) {
      parsed.forEach((item, index) => parseObject(item, `第 ${index + 1} 项`))
    } else if (isRecord(parsed) && Array.isArray(parsed.accounts)) {
      parsed.accounts.forEach((item, index) => parseObject(item, `accounts[${index}]`))
    } else {
      parseObject(parsed, '输入 JSON')
    }
  } catch {
    const lines = trimmed
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)

    lines.forEach((line, index) => {
      if (line.startsWith('{') || line.startsWith('[')) {
        try {
          const parsed = JSON.parse(line)
          if (Array.isArray(parsed)) {
            parsed.forEach((item, innerIndex) => parseObject(item, `第 ${index + 1} 行数组项 ${innerIndex + 1}`))
          } else {
            parseObject(parsed, `第 ${index + 1} 行`)
          }
        } catch {
          warnings.push(`第 ${index + 1} 行不是合法 JSON，已按 token 处理。`)
          pushToken(line)
        }
      } else {
        pushToken(line)
      }
    })
  }

  const filteredForms = forms.filter((form) => form.accessToken.trim())
  if (!filteredForms.length) {
    throw new Error('未解析到可导入的账号认证。')
  }

  return {
    forms: filteredForms,
    warnings,
  }
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
  const supportedModels = extractSupportedModels(parsed, candidate)

  return {
    accountName,
    externalAccountId,
    accessToken,
    refreshToken,
    metadataJson: JSON.stringify(parsed, null, 2),
    supportedModels,
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
  pushCandidate(raw.gemini)
  pushCandidate(raw.claude)

  if (Array.isArray(raw.accounts) && raw.accounts.length && isRecord(raw.accounts[0])) {
    candidates.push(raw.accounts[0])
  }

  if (providerType === 'OPENAI_OAUTH') {
    pushCandidate(raw.openai_oauth)
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
    supportedModels: form.supportedModels,
    active: form.active,
    proxyId: parseOptionalNumber(form.proxyId),
    tlsFingerprintProfileId: parseOptionalNumber(form.tlsFingerprintProfileId),
    siteProfileId: parseOptionalNumber(form.siteProfileId),
  }
}

function applyBatchOverride(form: AuthJsonImportForm, override: BatchImportOverride): AuthJsonImportForm {
  return {
    ...form,
    active: override.active,
    proxyId: override.proxyId.trim() || form.proxyId,
    tlsFingerprintProfileId: override.tlsFingerprintProfileId.trim() || form.tlsFingerprintProfileId,
    siteProfileId: override.siteProfileId.trim() || form.siteProfileId,
  }
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

function extractSupportedModels(root: Record<string, unknown>, candidate: Record<string, unknown>) {
  const values: string[] = []
  const keys = [
    'supported_models',
    'supportedModels',
    'models',
    'model_list',
    'modelList',
    'allow_models',
    'allowed_models',
  ]

  const collectFromValue = (value: unknown) => {
    if (typeof value === 'string') {
      values.push(...value.split(/[,\n;]/).map((item) => item.trim()).filter(Boolean))
      return
    }
    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (typeof item === 'string' || typeof item === 'number') {
          values.push(String(item).trim())
          return
        }
        if (isRecord(item)) {
          const modelName = pickString(item, ['id', 'model', 'name', 'model_id', 'modelId'])
          if (modelName) {
            values.push(modelName)
          }
        }
      })
    }
  }

  const maybeRecords = [candidate, root]
  maybeRecords.forEach((record) => {
    keys.forEach((key) => collectFromValue(record[key]))
  })

  const candidateCapabilities = candidate.capabilities
  if (isRecord(candidateCapabilities)) {
    keys.forEach((key) => collectFromValue(candidateCapabilities[key]))
  }
  const rootCapabilities = root.capabilities
  if (isRecord(rootCapabilities)) {
    keys.forEach((key) => collectFromValue(rootCapabilities[key]))
  }

  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)))
}

function mergeModelOptions(catalog: string[], selected: string[]) {
  return Array.from(new Set([...catalog, ...selected].map((item) => item.trim()).filter(Boolean)))
    .sort((left, right) => left.localeCompare(right))
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function resolveErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '未知错误'
}
