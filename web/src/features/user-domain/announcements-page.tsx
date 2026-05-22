import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type Announcement = {
  id: number
  title: string
  summary?: string | null
  body?: string | null
  status: string
  audienceType: string
  audienceUserId?: number | null
  audienceUserEmail?: string | null
  audiencePlanId?: number | null
  audiencePlanName?: string | null
  audienceAccessGroupId?: number | null
  audienceAccessGroupName?: string | null
  publishedAt?: string | null
  expiresAt?: string | null
  updatedAt?: string | null
}

type UserOption = { id: number; email: string; displayName?: string | null }
type PlanOption = { id: number; planName: string }
type AccessGroupOption = { id: number; groupName: string }

type FormState = {
  title: string
  summary: string
  body: string
  status: string
  audienceType: string
  audienceUserId: string
  audiencePlanId: string
  audienceAccessGroupId: string
  expiresAtLocal: string
}

const STATUS_OPTIONS = ['DRAFT', 'PUBLISHED', 'ARCHIVED'] as const
const AUDIENCE_OPTIONS = ['GLOBAL', 'USER', 'PLAN', 'ACCESS_GROUP'] as const

export function AnnouncementsPage() {
  const queryClient = useQueryClient()
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<FormState>(emptyForm())
  const [formError, setFormError] = useState<string | null>(null)

  const announcementsQuery = useQuery({
    queryKey: ['user-domain', 'announcements'],
    queryFn: () => apiRequest<Announcement[]>('/admin/announcements'),
  })
  const usersQuery = useQuery({
    queryKey: ['user-domain', 'users', 'announcement-options'],
    queryFn: () => apiRequest<UserOption[]>('/admin/users?active=true'),
  })
  const plansQuery = useQuery({
    queryKey: ['user-domain', 'plans', 'announcement-options'],
    queryFn: () => apiRequest<PlanOption[]>('/admin/plans?active=true'),
  })
  const accessGroupsQuery = useQuery({
    queryKey: ['user-domain', 'access-groups', 'announcement-options'],
    queryFn: () => apiRequest<AccessGroupOption[]>('/admin/access-groups?active=true'),
  })
  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ReturnType<typeof buildPayload> }) => {
      if (id == null) {
        return apiRequest<Announcement>('/admin/announcements', { method: 'POST', body: payload })
      }
      return apiRequest<Announcement>(`/admin/announcements/${id}`, { method: 'PUT', body: payload })
    },
    onSuccess: () => {
      setEditorOpen(false)
      setEditingId(null)
      setForm(emptyForm())
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'announcements'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) => apiRequest<void>(`/admin/announcements/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user-domain', 'announcements'] }),
  })

  const announcements = useMemo<Announcement[]>(() => announcementsQuery.data ?? [], [announcementsQuery.data])
  const users = (usersQuery.data ?? []) as UserOption[]
  const plans = (plansQuery.data ?? []) as PlanOption[]
  const accessGroups = (accessGroupsQuery.data ?? []) as AccessGroupOption[]

  const openCreate = () => {
    setEditingId(null)
    setForm(emptyForm())
    setFormError(null)
    setEditorOpen(true)
  }

  const openEdit = (item: Announcement) => {
    setEditingId(item.id)
    setForm({
      title: item.title,
      summary: item.summary ?? '',
      body: item.body ?? '',
      status: item.status,
      audienceType: item.audienceType,
      audienceUserId: item.audienceUserId ? String(item.audienceUserId) : '',
      audiencePlanId: item.audiencePlanId ? String(item.audiencePlanId) : '',
      audienceAccessGroupId: item.audienceAccessGroupId ? String(item.audienceAccessGroupId) : '',
      expiresAtLocal: toLocalDateTime(item.expiresAt),
    })
    setFormError(null)
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
    <PageSection
      kicker="用户域"
      title="公告中心"
      actions={<Button type="button" onClick={openCreate}>发布公告</Button>}
    >
      {saveMutation.error || deleteMutation.error ? (
        <InlineError error={saveMutation.error ?? deleteMutation.error} title="公告操作失败" />
      ) : null}

      {announcementsQuery.isPending ? (
        <PageSkeleton count={1} />
      ) : announcementsQuery.error ? (
        <InlineError error={announcementsQuery.error} title="公告列表加载失败" />
      ) : announcements.length ? (
        <PaginatedRows items={announcements}>
          {({ pageItems }) => (
            <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
              <table className="w-full table-fixed text-sm">
            <thead className="bg-muted/30">
              <tr>
                <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">标题</th>
                <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">受众</th>
                <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">发布时间</th>
                <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((item) => (
                <tr key={item.id} className="border-b border-border/40 align-top">
                  <td className="px-4 py-3">
                    <div className="font-medium text-foreground">{item.title}</div>
                    <div className="truncate text-xs text-muted-foreground">{item.summary ?? '无摘要'}</div>
                  </td>
                  <td className="px-4 py-3"><StatusBadge tone={item.status === 'PUBLISHED' ? 'success' : 'warning'}>{item.status}</StatusBadge></td>
                  <td className="px-4 py-3 text-muted-foreground">{audienceLabel(item)}</td>
                  <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.publishedAt)}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" variant="outline" size="sm" onClick={() => openEdit(item)}>编辑</Button>
                      <Button type="button" variant="outline" size="sm" onClick={() => deleteMutation.mutate(item.id)}>删除</Button>
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
        <EmptyState title="还没有公告" />
      )}

      <Dialog open={editorOpen} onOpenChange={setEditorOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editingId == null ? '发布公告' : '编辑公告'}</DialogTitle>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={save}>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium">标题</span>
                <Input value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium">状态</span>
                <select className="h-10 rounded-md border border-input bg-background px-3 text-sm" value={form.status} onChange={(event) => setForm((current) => ({ ...current, status: event.target.value }))}>
                  {STATUS_OPTIONS.map((status) => <option key={status} value={status}>{status}</option>)}
                </select>
              </label>
              <label className="flex flex-col gap-2 md:col-span-2">
                <span className="text-sm font-medium">摘要</span>
                <Input value={form.summary} onChange={(event) => setForm((current) => ({ ...current, summary: event.target.value }))} />
              </label>
              <label className="flex flex-col gap-2 md:col-span-2">
                <span className="text-sm font-medium">正文</span>
                <Textarea rows={5} value={form.body} onChange={(event) => setForm((current) => ({ ...current, body: event.target.value }))} />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium">受众类型</span>
                <select className="h-10 rounded-md border border-input bg-background px-3 text-sm" value={form.audienceType} onChange={(event) => setForm((current) => ({ ...current, audienceType: event.target.value }))}>
                  {AUDIENCE_OPTIONS.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
              </label>
              {form.audienceType === 'USER' ? (
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium">选择用户</span>
                  <select className="h-10 rounded-md border border-input bg-background px-3 text-sm" value={form.audienceUserId} onChange={(event) => setForm((current) => ({ ...current, audienceUserId: event.target.value }))}>
                    <option value="">请选择用户</option>
                    {users.map((user) => <option key={user.id} value={String(user.id)}>{user.email}</option>)}
                  </select>
                </label>
              ) : null}
              {form.audienceType === 'PLAN' ? (
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium">选择套餐</span>
                  <select className="h-10 rounded-md border border-input bg-background px-3 text-sm" value={form.audiencePlanId} onChange={(event) => setForm((current) => ({ ...current, audiencePlanId: event.target.value }))}>
                    <option value="">请选择套餐</option>
                    {plans.map((plan) => <option key={plan.id} value={String(plan.id)}>{plan.planName}</option>)}
                  </select>
                </label>
              ) : null}
              {form.audienceType === 'ACCESS_GROUP' ? (
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium">选择访问组</span>
                  <select className="h-10 rounded-md border border-input bg-background px-3 text-sm" value={form.audienceAccessGroupId} onChange={(event) => setForm((current) => ({ ...current, audienceAccessGroupId: event.target.value }))}>
                    <option value="">请选择访问组</option>
                    {accessGroups.map((group) => <option key={group.id} value={String(group.id)}>{group.groupName}</option>)}
                  </select>
                </label>
              ) : null}
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium">过期时间</span>
                <Input type="datetime-local" value={form.expiresAtLocal} onChange={(event) => setForm((current) => ({ ...current, expiresAtLocal: event.target.value }))} />
              </label>
            </div>
            {formError || saveMutation.error ? <InlineError error={saveMutation.error ?? new Error(formError ?? '保存失败')} title="公告保存失败" /> : null}
            <DialogFooter>
              <Button type="submit" disabled={saveMutation.isPending}>保存公告</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </PageSection>
  )
}

function emptyForm(): FormState {
  return {
    title: '',
    summary: '',
    body: '',
    status: 'PUBLISHED',
    audienceType: 'GLOBAL',
    audienceUserId: '',
    audiencePlanId: '',
    audienceAccessGroupId: '',
    expiresAtLocal: '',
  }
}

function buildPayload(form: FormState) {
  if (!form.title.trim()) {
    throw new Error('公告标题不能为空。')
  }
  return {
    title: form.title.trim(),
    summary: form.summary.trim() || null,
    body: form.body.trim() || null,
    status: form.status,
    audienceType: form.audienceType,
    audienceUserId: form.audienceType === 'USER' ? Number(form.audienceUserId) : null,
    audiencePlanId: form.audienceType === 'PLAN' ? Number(form.audiencePlanId) : null,
    audienceAccessGroupId: form.audienceType === 'ACCESS_GROUP' ? Number(form.audienceAccessGroupId) : null,
    expiresAt: toIsoFromLocal(form.expiresAtLocal),
  }
}

function audienceLabel(item: Announcement) {
  if (item.audienceType === 'USER') return item.audienceUserEmail ?? `用户 #${item.audienceUserId}`
  if (item.audienceType === 'PLAN') return item.audiencePlanName ?? `套餐 #${item.audiencePlanId}`
  if (item.audienceType === 'ACCESS_GROUP') return item.audienceAccessGroupName ?? `访问组 #${item.audienceAccessGroupId}`
  return '全体用户'
}

function toIsoFromLocal(value: string) {
  if (!value.trim()) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) throw new Error('时间格式不合法。')
  return date.toISOString()
}

function toLocalDateTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const offsetMs = date.getTimezoneOffset() * 60 * 1000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}
