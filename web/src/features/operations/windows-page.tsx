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
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type MaintenanceWindow = {
  id: number
  windowName: string
  scopeType?: string | null
  scopeRef?: string | null
  startsAt: string
  endsAt: string
  enabled: boolean
  activeNow: boolean
  description?: string | null
  updatedAt?: string | null
}

type MaintenanceWindowForm = {
  windowName: string
  scopeType: string
  scopeRef: string
  startsAt: string
  endsAt: string
  enabled: boolean
  description: string
}

type EditStep = 'basic' | 'scope' | 'submit'
const EDIT_STEPS: EditStep[] = ['basic', 'scope', 'submit']

export function WindowsPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [step, setStep] = useState<EditStep>('basic')
  const [form, setForm] = useState<MaintenanceWindowForm>(createEmptyForm())

  const windowsQuery = useQuery({
    queryKey: ['operations', 'maintenance-windows'],
    queryFn: () => apiRequest<MaintenanceWindow[]>('/admin/operations/maintenance-windows'),
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: MaintenanceWindowForm }) => {
      const body = {
        windowName: payload.windowName.trim(),
        scopeType: payload.scopeType.trim() || null,
        scopeRef: payload.scopeRef.trim() || null,
        startsAt: payload.startsAt.trim(),
        endsAt: payload.endsAt.trim(),
        enabled: payload.enabled,
        description: payload.description.trim() || null,
      }
      if (id == null) {
        return apiRequest<MaintenanceWindow>('/admin/operations/maintenance-windows', {
          method: 'POST',
          body: JSON.stringify(body),
        })
      }
      return apiRequest<MaintenanceWindow>(`/admin/operations/maintenance-windows/${id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['operations', 'maintenance-windows'] })
      setOpen(false)
      setEditingId(null)
      setStep('basic')
      setForm(createEmptyForm())
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/operations/maintenance-windows/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['operations', 'maintenance-windows'] })
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!form.windowName.trim() || !form.startsAt.trim() || !form.endsAt.trim()) {
      return
    }
    saveMutation.mutate({ id: editingId, payload: form })
  }

  const handleOpenCreate = () => {
    setOpen(true)
    setEditingId(null)
    setStep('basic')
    setForm(createEmptyForm())
  }

  const handleOpenEdit = (item: MaintenanceWindow) => {
    setOpen(true)
    setEditingId(item.id)
    setStep('basic')
    setForm({
      windowName: item.windowName,
      scopeType: item.scopeType ?? 'ALL',
      scopeRef: item.scopeRef ?? '',
      startsAt: item.startsAt,
      endsAt: item.endsAt,
      enabled: item.enabled,
      description: item.description ?? '',
    })
  }

  const handleDelete = (item: MaintenanceWindow) => {
    if (!window.confirm(`确认删除维护窗口“${item.windowName}”吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  const windows = useMemo(
    () => [...(windowsQuery.data ?? [])].sort((left, right) => left.windowName.localeCompare(right.windowName)),
    [windowsQuery.data],
  )
  const stepIndex = EDIT_STEPS.indexOf(step)

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="维护窗口"
        title="维护窗口"
        actions={(
          <Button type="button" onClick={handleOpenCreate}>
            新增维护窗口
          </Button>
        )}
      >
        {(saveMutation.error || deleteMutation.error) ? (
          <InlineError error={saveMutation.error ?? deleteMutation.error} title="维护窗口操作失败" />
        ) : null}
        {windowsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : windowsQuery.error ? (
          <InlineError error={windowsQuery.error} title="维护窗口加载失败" />
        ) : windows.length ? (
          <PaginatedRows items={windows}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">窗口名称</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">范围</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">范围引用</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">开始</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">结束</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((item) => (
                      <tr key={item.id} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{item.windowName}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{maintenanceScopeTypeLabel(item.scopeType)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.scopeRef ?? '全部'}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(item.startsAt)}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(item.endsAt)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={item.activeNow ? 'success' : 'warning'}>
                            {item.activeNow ? '当前命中' : '未命中'}
                          </StatusBadge>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" variant="outline" size="sm" onClick={() => handleOpenEdit(item)}>
                              编辑
                            </Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => handleDelete(item)} disabled={deleteMutation.isPending}>
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
          <EmptyState title="当前没有维护窗口" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingId == null ? '新增维护窗口' : '编辑维护窗口'}</DialogTitle>
            <DialogDescription />
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <Tabs value={step} onValueChange={(value) => setStep(value as EditStep)}>
              <TabsList variant="line">
                <TabsTrigger value="basic">1. 时间设置</TabsTrigger>
                <TabsTrigger value="scope">2. 生效范围</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">窗口名称</span>
                    <Input value={form.windowName} onChange={(event) => setForm((current) => ({ ...current, windowName: event.target.value }))} placeholder="例如：夜间升级窗口" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">开始时间（ISO-8601）</span>
                    <Input value={form.startsAt} onChange={(event) => setForm((current) => ({ ...current, startsAt: event.target.value }))} placeholder="2026-04-23T22:00:00Z" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">结束时间（ISO-8601）</span>
                    <Input value={form.endsAt} onChange={(event) => setForm((current) => ({ ...current, endsAt: event.target.value }))} placeholder="2026-04-23T23:00:00Z" />
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="scope" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">范围类型</span>
                    <Select value={form.scopeType} onValueChange={(value) => setForm((current) => ({ ...current, scopeType: value }))}>
                      <SelectTrigger className="w-full bg-background" aria-label="范围类型">
                        <SelectValue placeholder="选择范围类型" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value="ALL">全部</SelectItem>
                          <SelectItem value="SITE_PROFILE">站点档案</SelectItem>
                          <SelectItem value="PROVIDER">提供方</SelectItem>
                          <SelectItem value="PROXY">代理</SelectItem>
                          <SelectItem value="POOL">资源池</SelectItem>
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">范围引用（可选）</span>
                    <Input value={form.scopeRef} onChange={(event) => setForm((current) => ({ ...current, scopeRef: event.target.value }))} placeholder="例如：proxy-01 / site-12" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">备注（可选）</span>
                    <Input value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} placeholder="例如：只允许升级，不做回滚" />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.enabled}
                      onChange={(event) => setForm((current) => ({ ...current, enabled: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">启用该维护窗口</span>
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="submit" className="pt-3">
                <div className="rounded-2xl border border-border/60 bg-muted/20 p-4 text-sm text-foreground">
                  <div>窗口名称：{form.windowName || '未填写'}</div>
                  <div className="mt-1">范围：{maintenanceScopeTypeLabel(form.scopeType)} / {form.scopeRef.trim() || '全部'}</div>
                  <div className="mt-1">开始：{form.startsAt || '未填写'}</div>
                  <div className="mt-1">结束：{form.endsAt || '未填写'}</div>
                  <div className="mt-1">状态：{form.enabled ? '启用' : '停用'}</div>
                </div>
              </TabsContent>
            </Tabs>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setStep(EDIT_STEPS[Math.max(0, stepIndex - 1)])} disabled={stepIndex === 0}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={() => setStep(EDIT_STEPS[Math.min(EDIT_STEPS.length - 1, stepIndex + 1)])} disabled={stepIndex === EDIT_STEPS.length - 1}>
                下一步
              </Button>
              <Button type="submit" disabled={saveMutation.isPending || !form.windowName.trim() || !form.startsAt.trim() || !form.endsAt.trim()}>
                {editingId == null ? '创建' : '保存'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function createEmptyForm(): MaintenanceWindowForm {
  return (
    {
      windowName: '',
      scopeType: 'ALL',
      scopeRef: '',
      startsAt: new Date(Date.now() + 3600_000).toISOString(),
      endsAt: new Date(Date.now() + 7200_000).toISOString(),
      enabled: true,
      description: '',
    }
  )
}

function maintenanceScopeTypeLabel(value?: string | null) {
  switch ((value ?? 'ALL').toUpperCase()) {
    case 'ALL':
      return '全部'
    case 'SITE_PROFILE':
      return '站点档案'
    case 'PROVIDER':
      return '提供方'
    case 'PROXY':
      return '代理'
    case 'POOL':
      return '资源池'
    default:
      return value ?? '全部'
  }
}
