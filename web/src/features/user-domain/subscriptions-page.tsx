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
import { formatInstant } from '@/lib/format'

type UserOption = {
  id: number
  email: string
  displayName?: string | null
  active: boolean
}

type PlanOption = {
  id: number
  planName: string
  active: boolean
}

type UserSubscription = {
  id: number
  userId: number
  userEmail: string
  planId: number
  planName: string
  status: string
  startsAt: string
  expiresAt?: string | null
  autoRenew: boolean
  notes?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

type SubscriptionFormState = {
  userId: string
  planId: string
  status: string
  startsAtLocal: string
  expiresAtLocal: string
  autoRenew: boolean
  notes: string
}

type StatusFilter = 'ALL' | 'ACTIVE' | 'PAUSED' | 'EXPIRED' | 'CANCELED'
type SubscriptionStep = 'relation' | 'lifecycle' | 'submit'

const SUBSCRIPTION_STEPS: SubscriptionStep[] = ['relation', 'lifecycle', 'submit']
const SUBSCRIPTION_STATUS_OPTIONS = ['ACTIVE', 'PAUSED', 'EXPIRED', 'CANCELED'] as const

export function SubscriptionsPage() {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [userFilter, setUserFilter] = useState('ALL')
  const [planFilter, setPlanFilter] = useState('ALL')
  const [selectedSubscriptionId, setSelectedSubscriptionId] = useState<number | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorSubscriptionId, setEditorSubscriptionId] = useState<number | null>(null)
  const [editorStep, setEditorStep] = useState<SubscriptionStep>('relation')
  const [form, setForm] = useState<SubscriptionFormState>(createEmptyForm())
  const [formError, setFormError] = useState<string | null>(null)

  const usersQuery = useQuery({
    queryKey: ['user-domain', 'users', 'options'],
    queryFn: () => apiRequest<UserOption[]>('/admin/users?active=true'),
  })
  const plansQuery = useQuery({
    queryKey: ['user-domain', 'plans', 'options'],
    queryFn: () => apiRequest<PlanOption[]>('/admin/plans?active=true'),
  })
  const subscriptionsQuery = useQuery({
    queryKey: ['user-domain', 'subscriptions', statusFilter, userFilter, planFilter],
    queryFn: () => apiRequest<UserSubscription[]>(buildSubscriptionsUrl(statusFilter, userFilter, planFilter)),
  })
  const subscriptionDetailQuery = useQuery({
    queryKey: ['user-domain', 'subscription', selectedSubscriptionId],
    queryFn: () => apiRequest<UserSubscription>(`/admin/subscriptions/${selectedSubscriptionId}`),
    enabled: selectedSubscriptionId != null,
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ReturnType<typeof buildPayload> }) => {
      if (id == null) {
        return apiRequest<UserSubscription>('/admin/subscriptions', {
          method: 'POST',
          body: JSON.stringify(payload),
        })
      }
      return apiRequest<UserSubscription>(`/admin/subscriptions/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
    },
    onSuccess: () => {
      setEditorOpen(false)
      setEditorSubscriptionId(null)
      setEditorStep('relation')
      setForm(createEmptyForm())
      setFormError(null)
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'subscriptions'] })
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'subscription'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/subscriptions/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'subscriptions'] })
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'subscription'] })
      if (selectedSubscriptionId != null) {
        setSelectedSubscriptionId(null)
      }
    },
  })

  const users = useMemo(() => (usersQuery.data ?? []) as UserOption[], [usersQuery.data])
  const plans = useMemo(() => (plansQuery.data ?? []) as PlanOption[], [plansQuery.data])
  const subscriptions = useMemo(() => (subscriptionsQuery.data ?? []) as UserSubscription[], [subscriptionsQuery.data])
  const mutationError = saveMutation.error ?? deleteMutation.error
  const stepIndex = SUBSCRIPTION_STEPS.indexOf(editorStep)

  const handleOpenCreate = () => {
    setEditorSubscriptionId(null)
    setEditorStep('relation')
    setForm({
      ...createEmptyForm(),
      userId: users.length ? String(users[0].id) : '',
      planId: plans.length ? String(plans[0].id) : '',
    })
    setFormError(null)
    setEditorOpen(true)
  }

  const handleOpenEdit = (item: UserSubscription) => {
    setEditorSubscriptionId(item.id)
    setEditorStep('relation')
    setForm({
      userId: String(item.userId),
      planId: String(item.planId),
      status: item.status,
      startsAtLocal: toLocalDateTime(item.startsAt),
      expiresAtLocal: toLocalDateTime(item.expiresAt),
      autoRenew: item.autoRenew,
      notes: item.notes ?? '',
    })
    setFormError(null)
    setEditorOpen(true)
  }

  const handleDelete = (item: UserSubscription) => {
    if (!window.confirm(`确认删除订阅 #${item.id} 吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  const handleSave = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      const payloadForm = {
        ...form,
        userId: form.userId || (users[0] ? String(users[0].id) : ''),
        planId: form.planId || (plans[0] ? String(plans[0].id) : ''),
      }
      saveMutation.mutate({
        id: editorSubscriptionId,
        payload: buildPayload(payloadForm),
      })
    } catch (error) {
      setFormError(resolveErrorMessage(error))
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="用户域"
        title="订阅关系"
        actions={(
          <Button type="button" onClick={handleOpenCreate}>
            创建订阅
          </Button>
        )}
      >
        {mutationError ? (
          <InlineError error={mutationError} title="订阅操作失败" />
        ) : null}

        <div className="mb-4 grid gap-4 md:grid-cols-3">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">状态筛选</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
            >
              <option value="ALL">全部状态</option>
              {SUBSCRIPTION_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">用户筛选</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={userFilter}
              onChange={(event) => setUserFilter(event.target.value)}
            >
              <option value="ALL">全部用户</option>
              {users.map((item) => (
                <option key={item.id} value={String(item.id)}>
                  {item.email}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">套餐筛选</span>
            <select
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              value={planFilter}
              onChange={(event) => setPlanFilter(event.target.value)}
            >
              <option value="ALL">全部套餐</option>
              {plans.map((item) => (
                <option key={item.id} value={String(item.id)}>
                  {item.planName}
                </option>
              ))}
            </select>
          </label>
        </div>

        {subscriptionsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : subscriptionsQuery.error ? (
          <InlineError error={subscriptionsQuery.error} title="订阅列表加载失败" />
        ) : subscriptions.length ? (
          <PaginatedRows items={subscriptions}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">用户</th>
                  <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">套餐</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                  <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">生效区间</th>
                  <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">自动续期</th>
                  <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">更新时间</th>
                  <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((item) => (
                  <tr key={item.id} className="border-b border-border/40 align-top">
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{item.userEmail}</div>
                      <div className="text-xs text-muted-foreground">#{item.userId}</div>
                    </td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{item.planName}</div>
                      <div className="text-xs text-muted-foreground">#{item.planId}</div>
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={toneForStatus(item.status)}>{item.status}</StatusBadge>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {formatInstant(item.startsAt)} ~ {formatInstant(item.expiresAt)}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{item.autoRenew ? '是' : '否'}</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.updatedAt)}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => setSelectedSubscriptionId(item.id)}>
                          查看详情
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => handleOpenEdit(item)}>
                          编辑
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => handleDelete(item)}>
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
          <EmptyState title="还没有订阅关系" />
        )}
      </PageSection>

      <Dialog
        open={selectedSubscriptionId != null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedSubscriptionId(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>订阅详情</DialogTitle>
            <DialogDescription>查看订阅详情。</DialogDescription>
          </DialogHeader>
          {subscriptionDetailQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : subscriptionDetailQuery.error ? (
            <InlineError error={subscriptionDetailQuery.error} title="订阅详情加载失败" />
          ) : subscriptionDetailQuery.data ? (
            <InfoGrid
              items={[
                { key: 'id', label: '订阅 ID', value: subscriptionDetailQuery.data.id },
                { key: 'user', label: '用户', value: `${subscriptionDetailQuery.data.userEmail} (#${subscriptionDetailQuery.data.userId})` },
                { key: 'plan', label: '套餐', value: `${subscriptionDetailQuery.data.planName} (#${subscriptionDetailQuery.data.planId})` },
                { key: 'status', label: '状态', value: subscriptionDetailQuery.data.status },
                { key: 'startsAt', label: '生效时间', value: formatInstant(subscriptionDetailQuery.data.startsAt) },
                { key: 'expiresAt', label: '失效时间', value: formatInstant(subscriptionDetailQuery.data.expiresAt) },
                { key: 'autoRenew', label: '自动续期', value: subscriptionDetailQuery.data.autoRenew ? '是' : '否' },
                { key: 'notes', label: '备注', value: subscriptionDetailQuery.data.notes ?? '无' },
                { key: 'createdAt', label: '创建时间', value: formatInstant(subscriptionDetailQuery.data.createdAt) },
                { key: 'updatedAt', label: '更新时间', value: formatInstant(subscriptionDetailQuery.data.updatedAt) },
              ]}
              columnsClassName="md:grid-cols-2"
            />
          ) : (
            <EmptyState title="未找到订阅详情" />
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={editorOpen}
        onOpenChange={(open) => {
          setEditorOpen(open)
          if (!open) {
            setEditorStep('relation')
            setFormError(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editorSubscriptionId == null ? '创建订阅' : '编辑订阅'}</DialogTitle>
            <DialogDescription>填写订阅信息。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSave}>
            <Tabs value={editorStep} onValueChange={(value) => setEditorStep(value as SubscriptionStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="relation">1. 关联信息</TabsTrigger>
                <TabsTrigger value="lifecycle">2. 生命周期</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="relation" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">选择用户</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={form.userId}
                      onChange={(event) => setForm((current) => ({ ...current, userId: event.target.value }))}
                    >
                      <option value="">请选择用户</option>
                      {users.map((item) => (
                        <option key={item.id} value={String(item.id)}>
                          {item.email}{item.displayName ? ` (${item.displayName})` : ''}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">选择套餐</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={form.planId}
                      onChange={(event) => setForm((current) => ({ ...current, planId: event.target.value }))}
                    >
                      <option value="">请选择套餐</option>
                      {plans.map((item) => (
                        <option key={item.id} value={String(item.id)}>
                          {item.planName}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="lifecycle" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">订阅状态</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={form.status}
                      onChange={(event) => setForm((current) => ({ ...current, status: event.target.value }))}
                    >
                      {SUBSCRIPTION_STATUS_OPTIONS.map((status) => (
                        <option key={status} value={status}>
                          {status}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">生效时间</span>
                    <Input
                      type="datetime-local"
                      value={form.startsAtLocal}
                      onChange={(event) => setForm((current) => ({ ...current, startsAtLocal: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">失效时间（可选）</span>
                    <Input
                      type="datetime-local"
                      value={form.expiresAtLocal}
                      onChange={(event) => setForm((current) => ({ ...current, expiresAtLocal: event.target.value }))}
                    />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.autoRenew}
                      onChange={(event) => setForm((current) => ({ ...current, autoRenew: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">自动续期</span>
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">备注（可选）</span>
                    <Textarea
                      rows={4}
                      value={form.notes}
                      onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))}
                    />
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="submit" className="pt-3">
                <InfoGrid
                  items={[
                    { key: 'userId', label: '用户 ID', value: form.userId || '未选择' },
                    { key: 'planId', label: '套餐 ID', value: form.planId || '未选择' },
                    { key: 'status', label: '状态', value: form.status },
                    { key: 'startsAt', label: '生效时间', value: form.startsAtLocal || '未设置' },
                    { key: 'expiresAt', label: '失效时间', value: form.expiresAtLocal || '自动按套餐时长' },
                    { key: 'autoRenew', label: '自动续期', value: form.autoRenew ? '是' : '否' },
                    { key: 'notes', label: '备注', value: form.notes || '无' },
                  ]}
                  columnsClassName="md:grid-cols-2"
                />
              </TabsContent>
            </Tabs>

            {(formError || saveMutation.error) ? (
              <InlineError error={saveMutation.error ?? new Error(formError ?? '订阅保存失败')} title="订阅保存失败" />
            ) : null}

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditorStep(SUBSCRIPTION_STEPS[Math.max(0, stepIndex - 1)])}
                disabled={stepIndex === 0}
              >
                上一步
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditorStep(SUBSCRIPTION_STEPS[Math.min(SUBSCRIPTION_STEPS.length - 1, stepIndex + 1)])}
                disabled={stepIndex === SUBSCRIPTION_STEPS.length - 1}
              >
                下一步
              </Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                {editorSubscriptionId == null ? '创建订阅' : '保存变更'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function createEmptyForm(): SubscriptionFormState {
  return {
    userId: '',
    planId: '',
    status: 'ACTIVE',
    startsAtLocal: nowLocalDateTime(),
    expiresAtLocal: '',
    autoRenew: false,
    notes: '',
  }
}

function buildSubscriptionsUrl(statusFilter: StatusFilter, userFilter: string, planFilter: string) {
  const params = new URLSearchParams()
  if (statusFilter !== 'ALL') {
    params.set('status', statusFilter)
  }
  if (userFilter !== 'ALL') {
    params.set('userId', userFilter)
  }
  if (planFilter !== 'ALL') {
    params.set('planId', planFilter)
  }
  const query = params.toString()
  return query ? `/admin/subscriptions?${query}` : '/admin/subscriptions'
}

function buildPayload(form: SubscriptionFormState) {
  const userId = Number(form.userId)
  const planId = Number(form.planId)
  if (!Number.isFinite(userId) || userId <= 0) {
    throw new Error('请选择有效用户。')
  }
  if (!Number.isFinite(planId) || planId <= 0) {
    throw new Error('请选择有效套餐。')
  }
  return {
    userId,
    planId,
    status: form.status,
    startsAt: toIsoFromLocal(form.startsAtLocal),
    expiresAt: toIsoFromLocal(form.expiresAtLocal),
    autoRenew: form.autoRenew,
    notes: form.notes.trim() || null,
  }
}

function nowLocalDateTime() {
  const now = new Date()
  const offsetMs = now.getTimezoneOffset() * 60 * 1000
  return new Date(now.getTime() - offsetMs).toISOString().slice(0, 16)
}

function toIsoFromLocal(value: string) {
  if (!value.trim()) {
    return null
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    throw new Error('时间格式不合法。')
  }
  return date.toISOString()
}

function toLocalDateTime(value?: string | null) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const offsetMs = date.getTimezoneOffset() * 60 * 1000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}

function toneForStatus(status: string) {
  switch (status) {
    case 'ACTIVE':
      return 'success' as const
    case 'PAUSED':
      return 'warning' as const
    case 'EXPIRED':
      return 'danger' as const
    case 'CANCELED':
      return 'info' as const
    default:
      return 'neutral' as const
  }
}

function resolveErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return '未知错误'
}
