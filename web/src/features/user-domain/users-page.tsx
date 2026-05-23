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

type GatewayUser = {
  id: number
  email: string
  displayName?: string | null
  active: boolean
  subscriptionCount: number
  lastLoginAt?: string | null
  notes?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

type UserFormState = {
  email: string
  displayName: string
  active: boolean
  notes: string
}

type UserStep = 'basic' | 'notes' | 'submit'
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'

const USER_STEPS: UserStep[] = ['basic', 'notes', 'submit']

export function UsersPage() {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [keyword, setKeyword] = useState('')
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ALL')
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editorUserId, setEditorUserId] = useState<number | null>(null)
  const [editorStep, setEditorStep] = useState<UserStep>('basic')
  const [form, setForm] = useState<UserFormState>(createEmptyForm())
  const [formError, setFormError] = useState<string | null>(null)

  const usersQuery = useQuery({
    queryKey: ['user-domain', 'users', keyword, activeFilter],
    queryFn: () => apiRequest<GatewayUser[]>(buildUsersUrl(keyword, activeFilter)),
  })
  const userDetailQuery = useQuery({
    queryKey: ['user-domain', 'user', selectedUserId],
    queryFn: () => apiRequest<GatewayUser>(`/admin/users/${selectedUserId}`),
    enabled: selectedUserId != null,
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ReturnType<typeof buildPayload> }) => {
      if (id == null) {
        return apiRequest<GatewayUser>('/admin/users', {
          method: 'POST',
          body: JSON.stringify(payload),
        })
      }
      return apiRequest<GatewayUser>(`/admin/users/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
    },
    onSuccess: () => {
      setEditorOpen(false)
      setEditorUserId(null)
      setEditorStep('basic')
      setForm(createEmptyForm())
      setFormError(null)
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'user'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/users/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'user'] })
      if (selectedUserId != null) {
        setSelectedUserId(null)
      }
    },
  })

  const users = useMemo(() => (usersQuery.data ?? []) as GatewayUser[], [usersQuery.data])
  const mutationError = saveMutation.error ?? deleteMutation.error
  const stepIndex = USER_STEPS.indexOf(editorStep)

  const handleOpenCreate = () => {
    setEditorUserId(null)
    setEditorStep('basic')
    setForm(createEmptyForm())
    setFormError(null)
    setEditorOpen(true)
  }

  const handleOpenEdit = (user: GatewayUser) => {
    setEditorUserId(user.id)
    setEditorStep('basic')
    setForm({
      email: user.email,
      displayName: user.displayName ?? '',
      active: user.active,
      notes: user.notes ?? '',
    })
    setFormError(null)
    setEditorOpen(true)
  }

  const handleDelete = async (user: GatewayUser) => {
    const confirmed = await confirm({
      title: '删除用户',
      description: `确认删除“${user.email}”吗？该操作会立即移除这个用户。`,
      confirmLabel: '删除',
      destructive: true,
    })
    if (!confirmed) {
      return
    }
    deleteMutation.mutate(user.id)
  }

  const handleSave = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      saveMutation.mutate({
        id: editorUserId,
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
        title="用户清单"
        actions={(
          <Button type="button" onClick={handleOpenCreate}>
            创建用户
          </Button>
        )}
      >
        {mutationError ? (
          <InlineError error={mutationError} title="用户操作失败" />
        ) : null}

        <div className="mb-4 grid gap-4 md:grid-cols-2">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">关键词筛选</span>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="邮箱 / 昵称"
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

        {usersQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : usersQuery.error ? (
          <InlineError error={usersQuery.error} title="用户列表加载失败" />
        ) : users.length ? (
          <PaginatedRows items={users}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[28%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">用户邮箱</th>
                  <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">昵称</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">订阅数</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近登录</th>
                  <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((user) => (
                  <tr key={user.id} className="border-b border-border/40 align-top">
                    <td className="px-4 py-3 font-medium text-foreground">{user.email}</td>
                    <td className="px-4 py-3 text-muted-foreground">{user.displayName ?? '未设置'}</td>
                    <td className="px-4 py-3">
                      <StatusBadge tone={user.active ? 'success' : 'warning'}>
                        {user.active ? '启用' : '停用'}
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{formatCount(user.subscriptionCount)}</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatInstant(user.lastLoginAt)}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={() => setSelectedUserId(user.id)}>
                          查看详情
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => handleOpenEdit(user)}>
                          编辑
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={() => void handleDelete(user)}>
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
          <EmptyState title="还没有用户记录" />
        )}
      </PageSection>

      <Dialog
        open={selectedUserId != null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedUserId(null)
          }
        }}
      >
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>用户详情</DialogTitle>
            <DialogDescription>查看用户详情。</DialogDescription>
          </DialogHeader>
          {userDetailQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : userDetailQuery.error ? (
            <InlineError error={userDetailQuery.error} title="用户详情加载失败" />
          ) : userDetailQuery.data ? (
            <InfoGrid
              items={[
                { key: 'id', label: '用户 ID', value: userDetailQuery.data.id },
                { key: 'email', label: '用户邮箱', value: userDetailQuery.data.email },
                { key: 'displayName', label: '昵称', value: userDetailQuery.data.displayName ?? '未设置' },
                { key: 'active', label: '状态', value: userDetailQuery.data.active ? '启用' : '停用' },
                { key: 'subscriptionCount', label: '订阅数', value: formatCount(userDetailQuery.data.subscriptionCount) },
                { key: 'lastLoginAt', label: '最近登录', value: formatInstant(userDetailQuery.data.lastLoginAt) },
                { key: 'notes', label: '备注', value: userDetailQuery.data.notes ?? '无' },
                { key: 'createdAt', label: '创建时间', value: formatInstant(userDetailQuery.data.createdAt) },
                { key: 'updatedAt', label: '更新时间', value: formatInstant(userDetailQuery.data.updatedAt) },
              ]}
              columnsClassName="md:grid-cols-2"
            />
          ) : (
            <EmptyState title="未找到用户详情" />
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
            <DialogTitle>{editorUserId == null ? '创建用户' : '编辑用户'}</DialogTitle>
            <DialogDescription>填写用户信息。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSave}>
            <Tabs value={editorStep} onValueChange={(value) => setEditorStep(value as UserStep)}>
              <TabsList variant="line" className="w-full justify-start">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="notes">2. 备注信息</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">用户邮箱</span>
                    <Input
                      value={form.email}
                      onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
                      placeholder="例如：user@example.com"
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">用户昵称（可选）</span>
                    <Input
                      value={form.displayName}
                      onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
                      placeholder="例如：Alice"
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
                </div>
              </TabsContent>
              <TabsContent value="notes" className="pt-3">
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">备注（可选）</span>
                  <Textarea
                    rows={6}
                    value={form.notes}
                    onChange={(event) => setForm((current) => ({ ...current, notes: event.target.value }))}
                    placeholder="记录用户来源、用途等信息"
                  />
                </label>
              </TabsContent>
              <TabsContent value="submit" className="pt-3">
                <InfoGrid
                  items={[
                    { key: 'email', label: '用户邮箱', value: form.email || '未填写' },
                    { key: 'displayName', label: '用户昵称', value: form.displayName || '未设置' },
                    { key: 'active', label: '启用状态', value: form.active ? '启用' : '停用' },
                    { key: 'notes', label: '备注', value: form.notes || '无' },
                  ]}
                  columnsClassName="md:grid-cols-2"
                />
              </TabsContent>
            </Tabs>

            {(formError || saveMutation.error) ? (
              <InlineError error={saveMutation.error ?? new Error(formError ?? '用户保存失败')} title="用户保存失败" />
            ) : null}

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditorStep(USER_STEPS[Math.max(0, stepIndex - 1)])}
                disabled={stepIndex === 0}
              >
                上一步
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditorStep(USER_STEPS[Math.min(USER_STEPS.length - 1, stepIndex + 1)])}
                disabled={stepIndex === USER_STEPS.length - 1}
              >
                下一步
              </Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                {editorUserId == null ? '创建用户' : '保存变更'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function buildUsersUrl(keyword: string, activeFilter: ActiveFilter) {
  const params = new URLSearchParams()
  if (keyword.trim()) {
    params.set('keyword', keyword.trim())
  }
  if (activeFilter !== 'ALL') {
    params.set('active', activeFilter === 'ACTIVE' ? 'true' : 'false')
  }
  const query = params.toString()
  return query ? `/admin/users?${query}` : '/admin/users'
}

function createEmptyForm(): UserFormState {
  return {
    email: '',
    displayName: '',
    active: true,
    notes: '',
  }
}

function buildPayload(form: UserFormState) {
  const email = form.email.trim().toLowerCase()
  if (!email) {
    throw new Error('用户邮箱不能为空。')
  }
  return {
    email,
    displayName: form.displayName.trim() || null,
    active: form.active,
    notes: form.notes.trim() || null,
  }
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
