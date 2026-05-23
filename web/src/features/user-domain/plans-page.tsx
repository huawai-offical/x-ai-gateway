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
import { useConfirm } from '@/components/app/confirm-provider'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type SubscriptionPlan = {
  id: number
  planName: string
  description?: string | null
  active: boolean
  defaultDurationDays: number
  maxActiveKeys: number
  rpmLimit: number
  tpmLimit: number
  concurrencyLimit: number
  dailyTokenLimit: number
  activeSubscriptionCount: number
  createdAt?: string | null
  updatedAt?: string | null
}

type PlanFormState = {
  planName: string
  description: string
  active: boolean
  defaultDurationDays: string
  maxActiveKeys: string
  rpmLimit: string
  tpmLimit: string
  concurrencyLimit: string
  dailyTokenLimit: string
}

type PlanStep = 'basic' | 'quota' | 'submit'
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'

const PLAN_STEPS: PlanStep[] = ['basic', 'quota', 'submit']

export function PlansPage() {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [keyword, setKeyword] = useState('')
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ALL')
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorPlanId, setEditorPlanId] = useState<number | null>(null)
  const [editorStep, setEditorStep] = useState<PlanStep>('basic')
  const [form, setForm] = useState<PlanFormState>(createEmptyForm())
  const [formError, setFormError] = useState<string | null>(null)

  const plansQuery = useQuery({
    queryKey: ['user-domain', 'plans', keyword, activeFilter],
    queryFn: () => apiRequest<SubscriptionPlan[]>(buildPlansUrl(keyword, activeFilter)),
  })
  const planDetailQuery = useQuery({
    queryKey: ['user-domain', 'plan', selectedPlanId],
    queryFn: () => apiRequest<SubscriptionPlan>(`/admin/plans/${selectedPlanId}`),
    enabled: selectedPlanId != null,
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ReturnType<typeof buildPayload> }) => {
      if (id == null) {
        return apiRequest<SubscriptionPlan>('/admin/plans', {
          method: 'POST',
          body: JSON.stringify(payload),
        })
      }
      return apiRequest<SubscriptionPlan>(`/admin/plans/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
    },
    onSuccess: () => {
      setEditorOpen(false)
      setEditorPlanId(null)
      setEditorStep('basic')
      setForm(createEmptyForm())
      setFormError(null)
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'plans'] })
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'plan'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/plans/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'plans'] })
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'plan'] })
      if (selectedPlanId != null) {
        setSelectedPlanId(null)
      }
    },
  })

  const plans = useMemo(() => (plansQuery.data ?? []) as SubscriptionPlan[], [plansQuery.data])
  const mutationError = saveMutation.error ?? deleteMutation.error
  const stepIndex = PLAN_STEPS.indexOf(editorStep)

  const handleOpenCreate = () => {
    setEditorPlanId(null)
    setEditorStep('basic')
    setForm(createEmptyForm())
    setFormError(null)
    setEditorOpen(true)
  }

  const handleOpenEdit = (plan: SubscriptionPlan) => {
    setEditorPlanId(plan.id)
    setEditorStep('basic')
    setForm({
      planName: plan.planName,
      description: plan.description ?? '',
      active: plan.active,
      defaultDurationDays: String(plan.defaultDurationDays),
      maxActiveKeys: String(plan.maxActiveKeys),
      rpmLimit: String(plan.rpmLimit),
      tpmLimit: String(plan.tpmLimit),
      concurrencyLimit: String(plan.concurrencyLimit),
      dailyTokenLimit: String(plan.dailyTokenLimit),
    })
    setFormError(null)
    setEditorOpen(true)
  }

  const handleDelete = async (plan: SubscriptionPlan) => {
    const confirmed = await confirm({
      title: '删除套餐',
      description: `确认删除“${plan.planName}”吗？该操作会立即移除这条套餐配置。`,
      confirmLabel: '删除',
      destructive: true,
    })
    if (!confirmed) {
      return
    }
    deleteMutation.mutate(plan.id)
  }

  const handleSave = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      saveMutation.mutate({
        id: editorPlanId,
        payload: buildPayload(form),
      })
    } catch (error) {
      setFormError(resolveErrorMessage(error))
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="用户域"
        title="套餐管理"
        actions={(
          <Button type="button" onClick={handleOpenCreate}>
            创建套餐
          </Button>
        )}
      >
        {mutationError ? (
          <InlineError error={mutationError} title="套餐操作失败" />
        ) : null}

        <div className="mb-4 grid gap-4 md:grid-cols-2">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">关键词筛选</span>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="套餐名称 / 描述"
            />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">状态筛选</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={activeFilter}
              onChange={(event) => setActiveFilter(event.target.value as ActiveFilter)}
            >
              <option value="ALL">全部状态</option>
              <option value="ACTIVE">仅启用</option>
              <option value="INACTIVE">仅停用</option>
            </select>
          </label>
        </div>

        {plansQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : plansQuery.error ? (
          <InlineError error={plansQuery.error} title="套餐列表加载失败" />
        ) : plans.length ? (
          <PaginatedRows items={plans}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">套餐名称</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                  <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">默认周期 / Key 数</th>
                  <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">限流配额</th>
                  <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">订阅数</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((plan) => (
                  <tr key={plan.id} className="border-b border-border/40 align-top">
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{plan.planName}</div>
                      <div className="text-xs text-muted-foreground">{plan.description ?? '无描述'}</div>
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={plan.active ? 'success' : 'warning'}>
                        {plan.active ? '启用' : '停用'}
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {plan.defaultDurationDays} 天 / {plan.maxActiveKeys} 个 key
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      RPM {formatCount(plan.rpmLimit)} / TPM {formatCount(plan.tpmLimit)}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{formatCount(plan.activeSubscriptionCount)}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => setSelectedPlanId(plan.id)}>
                          查看详情
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => handleOpenEdit(plan)}>
                          编辑
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => void handleDelete(plan)}>
                          删除
                        </Button>
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
          <EmptyState title="还没有套餐记录" />
        )}
      </PageSection>

      <Dialog
        open={selectedPlanId != null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedPlanId(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>套餐详情</DialogTitle>
            <DialogDescription>查看套餐详情。</DialogDescription>
          </DialogHeader>
          {planDetailQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : planDetailQuery.error ? (
            <InlineError error={planDetailQuery.error} title="套餐详情加载失败" />
          ) : planDetailQuery.data ? (
            <InfoGrid
              items={[
                { key: 'id', label: '套餐 ID', value: planDetailQuery.data.id },
                { key: 'planName', label: '套餐名称', value: planDetailQuery.data.planName },
                { key: 'active', label: '状态', value: planDetailQuery.data.active ? '启用' : '停用' },
                { key: 'defaultDurationDays', label: '默认时长', value: `${planDetailQuery.data.defaultDurationDays} 天` },
                { key: 'maxActiveKeys', label: '可用 key 上限', value: formatCount(planDetailQuery.data.maxActiveKeys) },
                { key: 'rpmLimit', label: 'RPM 限额', value: formatCount(planDetailQuery.data.rpmLimit) },
                { key: 'tpmLimit', label: 'TPM 限额', value: formatCount(planDetailQuery.data.tpmLimit) },
                { key: 'concurrencyLimit', label: '并发上限', value: formatCount(planDetailQuery.data.concurrencyLimit) },
                { key: 'dailyTokenLimit', label: '日 token 上限', value: formatCount(planDetailQuery.data.dailyTokenLimit) },
                { key: 'activeSubscriptionCount', label: '关联订阅数', value: formatCount(planDetailQuery.data.activeSubscriptionCount) },
                { key: 'description', label: '描述', value: planDetailQuery.data.description ?? '无' },
                { key: 'createdAt', label: '创建时间', value: formatInstant(planDetailQuery.data.createdAt) },
                { key: 'updatedAt', label: '更新时间', value: formatInstant(planDetailQuery.data.updatedAt) },
              ]}
              columnsClassName="md:grid-cols-2"
            />
          ) : (
            <EmptyState title="未找到套餐详情" />
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={editorOpen}
        onOpenChange={(open) => {
          setEditorOpen(open)
          if (!open) {
            setEditorStep('basic')
            setFormError(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editorPlanId == null ? '创建套餐' : '编辑套餐'}</DialogTitle>
            <DialogDescription>填写套餐信息。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSave}>
            <Tabs value={editorStep} onValueChange={(value) => setEditorStep(value as PlanStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="quota">2. 配额参数</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">套餐名称</span>
                    <Input
                      value={form.planName}
                      onChange={(event) => setForm((current) => ({ ...current, planName: event.target.value }))}
                      placeholder="例如：starter"
                    />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.active}
                      onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">创建后立即启用</span>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">套餐描述（可选）</span>
                    <Textarea
                      rows={4}
                      value={form.description}
                      onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                    />
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="quota" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">默认时长（天）</span>
                    <Input
                      type="number"
                      min={1}
                      value={form.defaultDurationDays}
                      onChange={(event) => setForm((current) => ({ ...current, defaultDurationDays: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">最大可用 key 数</span>
                    <Input
                      type="number"
                      min={1}
                      value={form.maxActiveKeys}
                      onChange={(event) => setForm((current) => ({ ...current, maxActiveKeys: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">RPM</span>
                    <Input
                      type="number"
                      min={1}
                      value={form.rpmLimit}
                      onChange={(event) => setForm((current) => ({ ...current, rpmLimit: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">TPM</span>
                    <Input
                      type="number"
                      min={1}
                      value={form.tpmLimit}
                      onChange={(event) => setForm((current) => ({ ...current, tpmLimit: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">并发上限</span>
                    <Input
                      type="number"
                      min={1}
                      value={form.concurrencyLimit}
                      onChange={(event) => setForm((current) => ({ ...current, concurrencyLimit: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">日 token 上限</span>
                    <Input
                      type="number"
                      min={1}
                      value={form.dailyTokenLimit}
                      onChange={(event) => setForm((current) => ({ ...current, dailyTokenLimit: event.target.value }))}
                    />
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="submit" className="pt-3">
                <InfoGrid
                  items={[
                    { key: 'planName', label: '套餐名称', value: form.planName || '未填写' },
                    { key: 'active', label: '启用状态', value: form.active ? '启用' : '停用' },
                    { key: 'duration', label: '默认时长', value: `${form.defaultDurationDays || '--'} 天` },
                    { key: 'maxActiveKeys', label: '最大 key 数', value: form.maxActiveKeys || '--' },
                    { key: 'rpmLimit', label: 'RPM', value: form.rpmLimit || '--' },
                    { key: 'tpmLimit', label: 'TPM', value: form.tpmLimit || '--' },
                    { key: 'concurrencyLimit', label: '并发上限', value: form.concurrencyLimit || '--' },
                    { key: 'dailyTokenLimit', label: '日 token 上限', value: form.dailyTokenLimit || '--' },
                    { key: 'description', label: '描述', value: form.description || '无' },
                  ]}
                  columnsClassName="md:grid-cols-2"
                />
              </TabsContent>
            </Tabs>

            {(formError || saveMutation.error) ? (
              <InlineError error={saveMutation.error ?? new Error(formError ?? '套餐保存失败')} title="套餐保存失败" />
            ) : null}

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditorStep(PLAN_STEPS[Math.max(0, stepIndex - 1)])}
                disabled={stepIndex === 0}
              >
                上一步
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditorStep(PLAN_STEPS[Math.min(PLAN_STEPS.length - 1, stepIndex + 1)])}
                disabled={stepIndex === PLAN_STEPS.length - 1}
              >
                下一步
              </Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                {editorPlanId == null ? '创建套餐' : '保存变更'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function createEmptyForm(): PlanFormState {
  return {
    planName: '',
    description: '',
    active: true,
    defaultDurationDays: '30',
    maxActiveKeys: '3',
    rpmLimit: '60',
    tpmLimit: '120000',
    concurrencyLimit: '2',
    dailyTokenLimit: '1000000',
  }
}

function buildPlansUrl(keyword: string, activeFilter: ActiveFilter) {
  const params = new URLSearchParams()
  if (keyword.trim()) {
    params.set('keyword', keyword.trim())
  }
  if (activeFilter !== 'ALL') {
    params.set('active', activeFilter === 'ACTIVE' ? 'true' : 'false')
  }
  const query = params.toString()
  return query ? `/admin/plans?${query}` : '/admin/plans'
}

function buildPayload(form: PlanFormState) {
  const planName = form.planName.trim()
  if (!planName) {
    throw new Error('套餐名称不能为空。')
  }
  return {
    planName,
    description: form.description.trim() || null,
    active: form.active,
    defaultDurationDays: parsePositiveInt(form.defaultDurationDays, '默认时长'),
    maxActiveKeys: parsePositiveInt(form.maxActiveKeys, '最大 key 数'),
    rpmLimit: parsePositiveInt(form.rpmLimit, 'RPM'),
    tpmLimit: parsePositiveInt(form.tpmLimit, 'TPM'),
    concurrencyLimit: parsePositiveInt(form.concurrencyLimit, '并发上限'),
    dailyTokenLimit: parsePositiveInt(form.dailyTokenLimit, '日 token 上限'),
  }
}

function parsePositiveInt(value: string, label: string) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${label}必须是大于 0 的数字。`)
  }
  return Math.round(parsed)
}

function formatCount(value?: number | null) {
  if (value == null || Number.isNaN(value)) {
    return '--'
  }
  return Math.max(0, Math.round(value)).toLocaleString('zh-CN')
}

function resolveErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '未知错误'
}
