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
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import type { RunbookLink } from './types'

type RunbookFormState = {
  linkName: string
  linkUrl: string
  eventType: string
  entityType: string
  description: string
  enabled: boolean
}

type EditStep = 'basic' | 'match' | 'submit'
const EDIT_STEPS: EditStep[] = ['basic', 'match', 'submit']

export function RunbooksPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<EditStep>('basic')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<RunbookFormState>(createEmptyForm())

  const runbooksQuery = useQuery({
    queryKey: ['integrations', 'runbooks'],
    queryFn: () => apiRequest<RunbookLink[]>('/admin/integrations/runbooks'),
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: RunbookFormState }) => {
      const body = {
        linkName: payload.linkName.trim(),
        linkUrl: payload.linkUrl.trim(),
        eventType: payload.eventType.trim() || null,
        entityType: payload.entityType.trim() || null,
        description: payload.description.trim() || null,
        enabled: payload.enabled,
      }
      if (id == null) {
        return apiRequest<RunbookLink>('/admin/integrations/runbooks', {
          method: 'POST',
          body: JSON.stringify(body),
        })
      }
      return apiRequest<RunbookLink>(`/admin/integrations/runbooks/${id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'runbooks'] })
      setOpen(false)
      setStep('basic')
      setEditingId(null)
      setForm(createEmptyForm())
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/integrations/runbooks/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'runbooks'] })
    },
  })

  const stepIndex = EDIT_STEPS.indexOf(step)
  const canPrev = stepIndex > 0
  const canNext = stepIndex < EDIT_STEPS.length - 1
  const sortedRunbooks = useMemo(
    () => [...(runbooksQuery.data ?? [])].sort((left, right) => left.linkName.localeCompare(right.linkName)),
    [runbooksQuery.data],
  )

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    saveMutation.mutate({ id: editingId, payload: form })
  }

  const handleOpenCreate = () => {
    setEditingId(null)
    setForm(createEmptyForm())
    setStep('basic')
    setOpen(true)
  }

  const handleOpenEdit = (item: RunbookLink) => {
    setEditingId(item.id)
    setForm({
      linkName: item.linkName,
      linkUrl: item.linkUrl,
      eventType: item.eventType ?? '',
      entityType: item.entityType ?? '',
      description: item.description ?? '',
      enabled: item.enabled,
    })
    setStep('basic')
    setOpen(true)
  }

  const handleToggle = (item: RunbookLink) => {
    saveMutation.mutate({
      id: item.id,
      payload: {
        linkName: item.linkName,
        linkUrl: item.linkUrl,
        eventType: item.eventType ?? '',
        entityType: item.entityType ?? '',
        description: item.description ?? '',
        enabled: !item.enabled,
      },
    })
  }

  const handleDelete = (item: RunbookLink) => {
    if (!window.confirm(`确认删除排障文档“${item.linkName}”吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="排障文档"
        title="排障文档链接"
        actions={(
          <Button type="button" onClick={handleOpenCreate}>
            新增排障文档
          </Button>
        )}
      >
        {(runbooksQuery.error || saveMutation.error || deleteMutation.error) ? (
          <InlineError error={runbooksQuery.error ?? saveMutation.error ?? deleteMutation.error} title="排障文档操作失败" />
        ) : null}

        {runbooksQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : sortedRunbooks.length ? (
          <PaginatedRows items={sortedRunbooks}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">名称</th>
                      <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">链接</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">事件</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">实体</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">更新时间</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((item) => (
                      <tr key={item.id} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{item.linkName}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.linkUrl}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.eventType ?? '全部'}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.entityType ?? '全部'}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={item.enabled ? 'success' : 'warning'}>{item.enabled ? '启用' : '停用'}</StatusBadge>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(item.updatedAt)}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" variant="outline" size="sm" onClick={() => handleOpenEdit(item)}>
                              编辑
                            </Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => handleToggle(item)} disabled={saveMutation.isPending}>
                              {item.enabled ? '停用' : '启用'}
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => handleDelete(item)}
                              disabled={deleteMutation.isPending}
                            >
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
          <EmptyState title="当前还没有排障文档链接" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingId == null ? '新增排障文档' : '编辑排障文档'}</DialogTitle>
            <DialogDescription>填写排障文档信息。</DialogDescription>
          </DialogHeader>

          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <Tabs value={step} onValueChange={(value) => setStep(value as EditStep)}>
              <TabsList variant="line">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="match">2. 匹配条件</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>

              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">名称</span>
                    <Input value={form.linkName} onChange={(event) => setForm((current) => ({ ...current, linkName: event.target.value }))} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">链接 URL</span>
                    <Input value={form.linkUrl} onChange={(event) => setForm((current) => ({ ...current, linkUrl: event.target.value }))} />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">描述（可选）</span>
                    <Textarea rows={4} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="match" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">事件类型（可选）</span>
                    <Input value={form.eventType} onChange={(event) => setForm((current) => ({ ...current, eventType: event.target.value }))} placeholder="例如 ALERT_OPENED" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">实体类型（可选）</span>
                    <Input value={form.entityType} onChange={(event) => setForm((current) => ({ ...current, entityType: event.target.value }))} placeholder="例如 CREDENTIAL" />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 md:col-span-2">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.enabled}
                      onChange={(event) => setForm((current) => ({ ...current, enabled: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">启用该排障文档</span>
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="submit" className="pt-3">
                <div className="rounded-2xl border border-border/60 bg-muted/20 p-4 text-sm text-foreground">
                  <div>名称：{form.linkName || '未填写'}</div>
                  <div className="mt-1">链接：{form.linkUrl || '未填写'}</div>
                  <div className="mt-1">事件：{form.eventType || '全部'}</div>
                  <div className="mt-1">实体：{form.entityType || '全部'}</div>
                  <div className="mt-1">状态：{form.enabled ? '启用' : '停用'}</div>
                </div>
              </TabsContent>
            </Tabs>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setStep(EDIT_STEPS[Math.max(0, stepIndex - 1)])} disabled={!canPrev}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={() => setStep(EDIT_STEPS[Math.min(EDIT_STEPS.length - 1, stepIndex + 1)])} disabled={!canNext}>
                下一步
              </Button>
              <Button type="submit" disabled={saveMutation.isPending || !form.linkName.trim() || !form.linkUrl.trim()}>
                {editingId == null ? '创建' : '保存'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function createEmptyForm(): RunbookFormState {
  return {
    linkName: '',
    linkUrl: '',
    eventType: '',
    entityType: '',
    description: '',
    enabled: true,
  }
}
