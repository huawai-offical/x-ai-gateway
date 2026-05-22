import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'

type AccessGroup = {
  id: number
  groupName: string
  description?: string | null
  active: boolean
  priority: number
  allowedProtocolSuites: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  allowedClientFamilies: string[]
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
  dailyTokenLimit?: number | null
  planBindingCount: number
  keyGrantCount: number
  planBindings: PlanBinding[]
  keyGrants: KeyGrant[]
}

type PlanBinding = {
  id: number
  planId: number
  planName: string
  active: boolean
  priority: number
}

type KeyGrant = {
  id: number
  distributedKeyId: number
  keyName: string
  keyPrefix: string
  grantMode: string
  active: boolean
  priority: number
}

type PlanOption = { id: number; planName: string }
type KeyOption = { id: number; keyName: string; keyPrefix: string }
type ResolvedPolicy = {
  sourceAccessGroups: string[]
  allowedProtocolSuites: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  allowedClientFamilies: string[]
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
  dailyTokenLimit?: number | null
}

type GroupForm = {
  groupName: string
  description: string
  active: boolean
  priority: string
  allowedProtocolSuites: string
  allowedModels: string
  allowedProviderTypes: string
  allowedClientFamilies: string
  rpmLimit: string
  tpmLimit: string
  concurrencyLimit: string
  dailyTokenLimit: string
}

type EditorStep = 'basic' | 'policy' | 'confirm'

const EDITOR_STEPS: EditorStep[] = ['basic', 'policy', 'confirm']

export function AccessGroupsPage() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editorStep, setEditorStep] = useState<EditorStep>('basic')
  const [detailId, setDetailId] = useState<number | null>(null)
  const [form, setForm] = useState<GroupForm>(emptyForm())
  const [formError, setFormError] = useState<string | null>(null)
  const [planId, setPlanId] = useState('')
  const [keyId, setKeyId] = useState('')
  const [grantMode, setGrantMode] = useState('INHERIT')
  const [resolvedKeyId, setResolvedKeyId] = useState<number | null>(null)

  const groupsQuery = useQuery({
    queryKey: ['user-domain', 'access-groups', keyword],
    queryFn: () => apiRequest<AccessGroup[]>(buildGroupsUrl(keyword)),
  })
  const detailQuery = useQuery({
    queryKey: ['user-domain', 'access-group', detailId],
    queryFn: () => apiRequest<AccessGroup>(`/admin/access-groups/${detailId}`),
    enabled: detailId != null,
  })
  const plansQuery = useQuery({
    queryKey: ['user-domain', 'plans', 'access-group-options'],
    queryFn: () => apiRequest<PlanOption[]>('/admin/plans?active=true'),
  })
  const keysQuery = useQuery({
    queryKey: ['user-domain', 'distributed-keys', 'access-group-options'],
    queryFn: () => apiRequest<KeyOption[]>('/admin/distributed-keys'),
  })
  const resolvedPolicyQuery = useQuery({
    queryKey: ['user-domain', 'access-groups', 'resolved-policy', resolvedKeyId],
    queryFn: () => apiRequest<ResolvedPolicy>(`/admin/access-groups/distributed-keys/${resolvedKeyId}/resolved-policy`),
    enabled: resolvedKeyId != null,
  })
  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ReturnType<typeof buildPayload> }) => {
      if (id == null) {
        return apiRequest<AccessGroup>('/admin/access-groups', { method: 'POST', body: payload })
      }
      return apiRequest<AccessGroup>(`/admin/access-groups/${id}`, { method: 'PUT', body: payload })
    },
    onSuccess: () => {
      closeEditor()
      invalidateGroups(queryClient)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) => apiRequest<void>(`/admin/access-groups/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setDetailId(null)
      invalidateGroups(queryClient)
    },
  })
  const bindPlanMutation = useMutation({
    mutationFn: () => {
      if (detailId == null || !planId) throw new Error('请选择套餐。')
      return apiRequest<AccessGroup>(`/admin/access-groups/${detailId}/plans`, {
        method: 'POST',
        body: { planId: Number(planId), active: true, priority: 100 },
      })
    },
    onSuccess: () => {
      setPlanId('')
      invalidateGroups(queryClient)
    },
  })
  const grantKeyMutation = useMutation({
    mutationFn: () => {
      if (detailId == null || !keyId) throw new Error('请选择分发 Key。')
      return apiRequest<AccessGroup>(`/admin/access-groups/${detailId}/distributed-keys`, {
        method: 'POST',
        body: { distributedKeyId: Number(keyId), grantMode, active: true, priority: 100 },
      })
    },
    onSuccess: () => {
      setKeyId('')
      invalidateGroups(queryClient)
    },
  })
  const removePlanMutation = useMutation({
    mutationFn: (removePlanId: number) => {
      if (detailId == null) throw new Error('请选择访问组。')
      return apiRequest<AccessGroup>(`/admin/access-groups/${detailId}/plans/${removePlanId}`, { method: 'DELETE' })
    },
    onSuccess: () => invalidateGroups(queryClient),
  })
  const removeKeyGrantMutation = useMutation({
    mutationFn: (distributedKeyId: number) => {
      if (detailId == null) throw new Error('请选择访问组。')
      return apiRequest<AccessGroup>(`/admin/access-groups/${detailId}/distributed-keys/${distributedKeyId}`, { method: 'DELETE' })
    },
    onSuccess: () => invalidateGroups(queryClient),
  })

  const groups = useMemo<AccessGroup[]>(() => groupsQuery.data ?? [], [groupsQuery.data])
  const plans = (plansQuery.data ?? []) as PlanOption[]
  const keys = (keysQuery.data ?? []) as KeyOption[]
  const mutationError = saveMutation.error ?? deleteMutation.error ?? bindPlanMutation.error ?? grantKeyMutation.error ?? removePlanMutation.error ?? removeKeyGrantMutation.error
  const selectedGroup = detailQuery.data as AccessGroup | undefined
  const stepIndex = EDITOR_STEPS.indexOf(editorStep)

  const openCreate = () => {
    setEditingId(null)
    setForm(emptyForm())
    setFormError(null)
    setEditorStep('basic')
    setEditorOpen(true)
  }

  const openEdit = (group: AccessGroup) => {
    setEditingId(group.id)
    setForm(formFromGroup(group))
    setFormError(null)
    setEditorStep('basic')
    setEditorOpen(true)
  }

  const save = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      saveMutation.mutate({ id: editingId, payload: buildPayload(form) })
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '保存失败。')
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="用户域"
        title="访问组与权益"
        actions={<Button type="button" onClick={openCreate}>创建访问组</Button>}
      >
        {mutationError ? <InlineError error={mutationError} title="访问组操作失败" /> : null}

        <div className="mb-4 max-w-xl">
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="按名称 / 描述筛选" />
        </div>

        {groupsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : groupsQuery.error ? (
          <InlineError error={groupsQuery.error} title="访问组列表加载失败" />
        ) : groups.length ? (
          <PaginatedRows items={groups}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">访问组</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                  <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议 / 模型</th>
                  <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">绑定</th>
                  <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((group) => (
                  <tr key={group.id} className="border-b border-border/40 align-top">
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{group.groupName}</div>
                      <div className="truncate text-xs text-muted-foreground">{group.description ?? '无描述'}</div>
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={group.active ? 'success' : 'warning'}>{group.active ? '启用' : '停用'}</StatusBadge>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {formatList(group.allowedProtocolSuites) || '不限协议簇'} / {group.allowedModels.length} 个模型
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      套餐 {group.planBindingCount} / Key {group.keyGrantCount}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => setDetailId(group.id)}>查看详情</Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => openEdit(group)}>编辑</Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => deleteMutation.mutate(group.id)}>删除</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="还没有访问组" />
        )}
      </PageSection>

      <Dialog
        open={detailId != null}
        onOpenChange={(open) => {
          if (!open) {
            setDetailId(null)
            setResolvedKeyId(null)
          }
        }}
      >
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>访问组详情</DialogTitle>
            <DialogDescription>查看访问组详情。</DialogDescription>
          </DialogHeader>
          {detailQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : detailQuery.error ? (
            <InlineError error={detailQuery.error} title="访问组详情加载失败" />
          ) : selectedGroup ? (
            <div className="grid gap-5">
              <InfoGrid
                columnsClassName="md:grid-cols-2"
                items={[
                  { key: 'name', label: '名称', value: selectedGroup.groupName },
                  { key: 'status', label: '状态', value: selectedGroup.active ? '启用' : '停用' },
                  { key: 'protocols', label: '协议簇', value: formatList(selectedGroup.allowedProtocolSuites) || '不限' },
                  { key: 'models', label: '模型', value: formatList(selectedGroup.allowedModels) || '不限' },
                  { key: 'providers', label: '提供方', value: formatList(selectedGroup.allowedProviderTypes) || '不限' },
                  { key: 'clients', label: '客户端', value: formatList(selectedGroup.allowedClientFamilies) || '不限' },
                  { key: 'limits', label: '限流', value: `RPM ${formatNumber(selectedGroup.rpmLimit)} / TPM ${formatNumber(selectedGroup.tpmLimit)} / 并发 ${formatNumber(selectedGroup.concurrencyLimit)}` },
                  { key: 'daily', label: '日 Token', value: formatNumber(selectedGroup.dailyTokenLimit) },
                ]}
              />

              <div className="grid gap-4 lg:grid-cols-2">
                <div className="rounded-2xl border border-border/60 p-4">
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <h3 className="text-sm font-semibold text-foreground">套餐绑定</h3>
                    <div className="flex gap-2">
                      <select className="h-9 rounded-md border border-input bg-background px-2 text-sm" value={planId} onChange={(event) => setPlanId(event.target.value)}>
                        <option value="">选择套餐</option>
                        {plans.map((plan) => <option key={plan.id} value={String(plan.id)}>{plan.planName}</option>)}
                      </select>
                      <Button type="button" size="sm" onClick={() => bindPlanMutation.mutate()}>绑定</Button>
                    </div>
                  </div>
                  {selectedGroup.planBindings.length ? (
                    <div className="grid gap-2">
                      {selectedGroup.planBindings.map((binding: PlanBinding) => (
                        <div key={binding.id} className="flex items-center justify-between rounded-xl bg-muted/30 px-3 py-2 text-sm">
                          <span>{binding.planName}</span>
                          <div className="flex items-center gap-2">
                            <StatusBadge tone={binding.active ? 'success' : 'warning'}>{binding.active ? '启用' : '停用'}</StatusBadge>
                            <Button type="button" variant="outline" size="sm" onClick={() => removePlanMutation.mutate(binding.planId)}>移除</Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <EmptyState title="暂无套餐绑定" />
                  )}
                </div>

                <div className="rounded-2xl border border-border/60 p-4">
                  <div className="mb-3 grid gap-2">
                    <h3 className="text-sm font-semibold text-foreground">分发 Key 授权</h3>
                    <div className="grid gap-2 md:grid-cols-[1fr_120px_auto]">
                      <select className="h-9 rounded-md border border-input bg-background px-2 text-sm" value={keyId} onChange={(event) => setKeyId(event.target.value)}>
                        <option value="">选择 Key</option>
                        {keys.map((key) => <option key={key.id} value={String(key.id)}>{key.keyName}</option>)}
                      </select>
                      <select className="h-9 rounded-md border border-input bg-background px-2 text-sm" value={grantMode} onChange={(event) => setGrantMode(event.target.value)}>
                        <option value="INHERIT">继承</option>
                        <option value="OVERRIDE">覆盖</option>
                      </select>
                      <Button type="button" size="sm" onClick={() => grantKeyMutation.mutate()}>授权</Button>
                    </div>
                  </div>
                  {selectedGroup.keyGrants.length ? (
                    <div className="grid gap-2">
                      {selectedGroup.keyGrants.map((grant: KeyGrant) => (
                        <div key={grant.id} className="flex items-center justify-between rounded-xl bg-muted/30 px-3 py-2 text-sm">
                          <span>{grant.keyName}</span>
                          <div className="flex items-center gap-2">
                            <StatusBadge tone={grant.grantMode === 'OVERRIDE' ? 'warning' : 'info'}>{grant.grantMode}</StatusBadge>
                            <Button type="button" variant="outline" size="sm" onClick={() => setResolvedKeyId(grant.distributedKeyId)}>生效策略</Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => removeKeyGrantMutation.mutate(grant.distributedKeyId)}>移除</Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <EmptyState title="暂无 Key 授权" />
                  )}
                </div>
              </div>
              {resolvedKeyId != null ? (
                <div className="rounded-2xl border border-border/60 p-4">
                  <h3 className="mb-3 text-sm font-semibold text-foreground">分发 Key 生效策略</h3>
                  {resolvedPolicyQuery.isPending ? (
                    <PageSkeleton count={1} />
                  ) : resolvedPolicyQuery.error ? (
                    <InlineError error={resolvedPolicyQuery.error} title="生效策略加载失败" />
                  ) : resolvedPolicyQuery.data ? (
                    <InfoGrid
                      columnsClassName="md:grid-cols-2"
                      items={[
                        { key: 'source', label: '来源访问组', value: formatList(resolvedPolicyQuery.data.sourceAccessGroups) || '无' },
                        { key: 'protocols', label: '协议簇', value: formatList(resolvedPolicyQuery.data.allowedProtocolSuites) || '不限' },
                        { key: 'models', label: '模型', value: formatList(resolvedPolicyQuery.data.allowedModels) || '不限' },
                        { key: 'providers', label: '提供方', value: formatList(resolvedPolicyQuery.data.allowedProviderTypes) || '不限' },
                        { key: 'clients', label: '客户端', value: formatList(resolvedPolicyQuery.data.allowedClientFamilies) || '不限' },
                        { key: 'limits', label: '限流', value: `RPM ${formatNumber(resolvedPolicyQuery.data.rpmLimit)} / TPM ${formatNumber(resolvedPolicyQuery.data.tpmLimit)} / 并发 ${formatNumber(resolvedPolicyQuery.data.concurrencyLimit)}` },
                        { key: 'daily', label: '日 Token', value: formatNumber(resolvedPolicyQuery.data.dailyTokenLimit) },
                      ]}
                    />
                  ) : (
                    <EmptyState title="暂无生效策略" />
                  )}
                </div>
              ) : null}
            </div>
          ) : (
            <EmptyState title="未找到访问组详情" />
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={editorOpen} onOpenChange={(open) => (open ? setEditorOpen(true) : closeEditor())}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>{editingId == null ? '创建访问组' : '编辑访问组'}</DialogTitle>
            <DialogDescription>填写访问组信息。</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4" onSubmit={save}>
            <Tabs value={editorStep} onValueChange={(value) => setEditorStep(value as EditorStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="policy">2. 权益策略</TabsTrigger>
                <TabsTrigger value="confirm">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <Input value={form.groupName} onChange={(event) => setForm((current) => ({ ...current, groupName: event.target.value }))} placeholder="访问组名称，例如：default" />
                  <Textarea rows={3} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} placeholder="访问组说明" />
                  <div className="grid gap-4 md:grid-cols-2">
                    <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm">
                      <input type="checkbox" checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} />
                      启用访问组
                    </label>
                    <Input type="number" min={0} value={form.priority} onChange={(event) => setForm((current) => ({ ...current, priority: event.target.value }))} placeholder="优先级" />
                  </div>
                </div>
              </TabsContent>
              <TabsContent value="policy" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <Input value={form.allowedProtocolSuites} onChange={(event) => setForm((current) => ({ ...current, allowedProtocolSuites: event.target.value }))} placeholder="协议簇：openai.native,xiaomi_mimo.openai_compatible" />
                  <Input value={form.allowedProviderTypes} onChange={(event) => setForm((current) => ({ ...current, allowedProviderTypes: event.target.value }))} placeholder="提供方：openai、gemini 或 OPENAI_DIRECT" />
                  <Input value={form.allowedClientFamilies} onChange={(event) => setForm((current) => ({ ...current, allowedClientFamilies: event.target.value }))} placeholder="客户端：CODEX,GENERIC_OPENAI" />
                  <Input type="number" min={1} value={form.rpmLimit} onChange={(event) => setForm((current) => ({ ...current, rpmLimit: event.target.value }))} placeholder="RPM 限制，留空为不限" />
                  <Input type="number" min={1} value={form.tpmLimit} onChange={(event) => setForm((current) => ({ ...current, tpmLimit: event.target.value }))} placeholder="TPM 限制，留空为不限" />
                  <Input type="number" min={1} value={form.concurrencyLimit} onChange={(event) => setForm((current) => ({ ...current, concurrencyLimit: event.target.value }))} placeholder="并发限制，留空为不限" />
                  <Input type="number" min={1} value={form.dailyTokenLimit} onChange={(event) => setForm((current) => ({ ...current, dailyTokenLimit: event.target.value }))} placeholder="日 Token 限制，留空为不限" />
                  <Textarea rows={5} value={form.allowedModels} onChange={(event) => setForm((current) => ({ ...current, allowedModels: event.target.value }))} placeholder="支持模型，每行一个或逗号分隔" />
                </div>
              </TabsContent>
              <TabsContent value="confirm" className="pt-3">
                <InfoGrid
                  columnsClassName="md:grid-cols-2"
                  items={[
                    { key: 'name', label: '名称', value: form.groupName || '未填写' },
                    { key: 'status', label: '状态', value: form.active ? '启用' : '停用' },
                    { key: 'protocols', label: '协议簇', value: form.allowedProtocolSuites || '不限' },
                    { key: 'models', label: '模型', value: splitValues(form.allowedModels).length ? `${splitValues(form.allowedModels).length} 个模型` : '不限' },
                    { key: 'providers', label: '提供方', value: form.allowedProviderTypes || '不限' },
                    { key: 'clients', label: '客户端', value: form.allowedClientFamilies || '不限' },
                    { key: 'rpm', label: 'RPM', value: form.rpmLimit || '不限' },
                    { key: 'daily', label: '日 Token', value: form.dailyTokenLimit || '不限' },
                  ]}
                />
              </TabsContent>
            </Tabs>
            {formError || saveMutation.error ? <InlineError error={saveMutation.error ?? new Error(formError ?? '保存失败')} title="访问组保存失败" /> : null}
            <DialogFooter>
              <Button type="button" variant="outline" disabled={stepIndex === 0} onClick={() => setEditorStep(EDITOR_STEPS[Math.max(0, stepIndex - 1)])}>上一步</Button>
              <Button type="button" variant="outline" disabled={stepIndex === EDITOR_STEPS.length - 1} onClick={() => setEditorStep(EDITOR_STEPS[Math.min(EDITOR_STEPS.length - 1, stepIndex + 1)])}>下一步</Button>
              <Button type="submit" disabled={saveMutation.isPending}>{editingId == null ? '创建访问组' : '保存变更'}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )

  function closeEditor() {
    setEditorOpen(false)
    setEditingId(null)
    setEditorStep('basic')
    setForm(emptyForm())
    setFormError(null)
  }
}

function emptyForm(): GroupForm {
  return {
    groupName: '',
    description: '',
    active: true,
    priority: '100',
    allowedProtocolSuites: 'openai.native,anthropic.native,gemini.native',
    allowedModels: '',
    allowedProviderTypes: '',
    allowedClientFamilies: '',
    rpmLimit: '',
    tpmLimit: '',
    concurrencyLimit: '',
    dailyTokenLimit: '',
  }
}

function formFromGroup(group: AccessGroup): GroupForm {
  return {
    groupName: group.groupName,
    description: group.description ?? '',
    active: group.active,
    priority: String(group.priority),
    allowedProtocolSuites: group.allowedProtocolSuites.join(','),
    allowedModels: group.allowedModels.join('\n'),
    allowedProviderTypes: group.allowedProviderTypes.join(','),
    allowedClientFamilies: group.allowedClientFamilies.join(','),
    rpmLimit: group.rpmLimit == null ? '' : String(group.rpmLimit),
    tpmLimit: group.tpmLimit == null ? '' : String(group.tpmLimit),
    concurrencyLimit: group.concurrencyLimit == null ? '' : String(group.concurrencyLimit),
    dailyTokenLimit: group.dailyTokenLimit == null ? '' : String(group.dailyTokenLimit),
  }
}

function buildPayload(form: GroupForm) {
  if (!form.groupName.trim()) throw new Error('访问组名称不能为空。')
  return {
    groupName: form.groupName.trim(),
    description: form.description.trim() || null,
    active: form.active,
    priority: parseOptionalNumber(form.priority) ?? 100,
    allowedProtocolSuites: splitValues(form.allowedProtocolSuites),
    allowedModels: splitValues(form.allowedModels),
    allowedProviderTypes: splitValues(form.allowedProviderTypes),
    allowedClientFamilies: splitValues(form.allowedClientFamilies),
    rpmLimit: parseOptionalNumber(form.rpmLimit),
    tpmLimit: parseOptionalNumber(form.tpmLimit),
    concurrencyLimit: parseOptionalNumber(form.concurrencyLimit),
    dailyTokenLimit: parseOptionalNumber(form.dailyTokenLimit),
  }
}

function splitValues(value: string) {
  return value
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function parseOptionalNumber(value: string) {
  if (!value.trim()) return null
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error('数值必须是非负数字。')
  }
  return Math.round(parsed)
}

function buildGroupsUrl(keyword: string) {
  const params = new URLSearchParams()
  if (keyword.trim()) params.set('keyword', keyword.trim())
  const query = params.toString()
  return query ? `/admin/access-groups?${query}` : '/admin/access-groups'
}

function invalidateGroups(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['user-domain', 'access-groups'] })
  queryClient.invalidateQueries({ queryKey: ['user-domain', 'access-group'] })
}

function formatList(values: string[]) {
  return values.slice(0, 3).join(', ')
}

function formatNumber(value?: number | null) {
  if (value == null) return '不限'
  return value.toLocaleString('zh-CN')
}
