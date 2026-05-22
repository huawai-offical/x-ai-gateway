import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { createPortalKey, disablePortalKey, getPortalSession, listPortalKeys, rotatePortalKey } from './api'
import { formatNumber } from './portal-format'
import { PortalFrame } from './portal-shell'
import type { PortalKey, PortalKeyCreateResponse } from './types'

const DEFAULT_FORM = {
  keyName: '我的门户 Key',
  allowedProtocolSuites: 'openai',
  allowedModels: '',
  rpmLimit: '60',
  tpmLimit: '120000',
  concurrencyLimit: '2',
}

export function PortalKeysPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState(DEFAULT_FORM)
  const [secret, setSecret] = useState<PortalKeyCreateResponse | null>(null)
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const keysQuery = useQuery({
    queryKey: ['portal', 'keys'],
    queryFn: listPortalKeys,
    enabled: Boolean(sessionQuery.data?.authenticated),
  })
  const createMutation = useMutation({
    mutationFn: () => createPortalKey({
      keyName: form.keyName.trim(),
      allowedProtocolSuites: splitValues(form.allowedProtocolSuites),
      allowedModels: splitValues(form.allowedModels),
      rpmLimit: Number(form.rpmLimit) || null,
      tpmLimit: Number(form.tpmLimit) || null,
      concurrencyLimit: Number(form.concurrencyLimit) || null,
    }),
    onSuccess: (response: PortalKeyCreateResponse) => {
      setSecret(response)
      setOpen(false)
      setForm(DEFAULT_FORM)
      queryClient.invalidateQueries({ queryKey: ['portal', 'keys'] })
    },
  })
  const rotateMutation = useMutation({
    mutationFn: rotatePortalKey,
    onSuccess: (response: PortalKeyCreateResponse) => {
      setSecret(response)
      queryClient.invalidateQueries({ queryKey: ['portal', 'keys'] })
    },
  })
  const disableMutation = useMutation({
    mutationFn: disablePortalKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['portal', 'keys'] }),
  })

  const handleCreate = (event: FormEvent) => {
    event.preventDefault()
    createMutation.mutate()
  }

  if (sessionQuery.isPending) {
    return <PortalFrame><PageSkeleton count={2} /></PortalFrame>
  }
  if (sessionQuery.error) {
    return <PortalFrame><InlineError error={sessionQuery.error} title="门户会话加载失败" /></PortalFrame>
  }
  if (!sessionQuery.data?.authenticated) {
    return <Navigate to="/portal/login" replace />
  }

  const keys = (keysQuery.data ?? []) as PortalKey[]

  return (
    <PortalFrame>
      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div>
            <p className="text-sm font-medium text-primary">访问密钥</p>
            <CardTitle className="text-3xl">我的访问密钥</CardTitle>
          </div>
          <Button type="button" onClick={() => setOpen(true)}>创建 Key</Button>
        </CardHeader>
        <CardContent>
          {(createMutation.error || rotateMutation.error || disableMutation.error) ? (
            <InlineError error={createMutation.error ?? rotateMutation.error ?? disableMutation.error} title="Key 操作失败" />
          ) : null}
          {keysQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : keysQuery.error ? (
            <InlineError error={keysQuery.error} title="Key 加载失败" />
          ) : keys.length ? (
            <PaginatedRows items={keys}>
              {({ pageItems }) => (
                <div className="overflow-hidden rounded-2xl border border-border/60">
                  <table className="w-full table-fixed text-sm">
                    <thead className="bg-muted/40">
                      <tr>
                        <th className="w-[22%] px-4 py-3 text-left font-medium text-muted-foreground">名称</th>
                        <th className="w-[22%] px-4 py-3 text-left font-medium text-muted-foreground">掩码</th>
                        <th className="w-[12%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                        <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">限制</th>
                        <th className="w-[14%] px-4 py-3 text-left font-medium text-muted-foreground">最近使用</th>
                        <th className="w-[12%] px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pageItems.map((item) => (
                        <tr key={item.id} className="border-t border-border/50 align-top">
                          <td className="truncate px-4 py-3 font-medium text-foreground">{item.keyName}</td>
                          <td className="truncate px-4 py-3 font-mono text-xs text-muted-foreground">{item.maskedKey}</td>
                          <td className="px-4 py-3">
                            <StatusBadge tone={item.active ? 'success' : 'warning'}>{item.active ? '启用' : '停用'}</StatusBadge>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            RPM {formatNumber(item.rpmLimit)} / 并发 {formatNumber(item.concurrencyLimit)}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.lastUsedAt) || '暂无'}</td>
                          <td className="px-4 py-3">
                            <div className="flex flex-wrap gap-2">
                              <Button type="button" size="sm" variant="outline" onClick={() => rotateMutation.mutate(item.id)}>轮换</Button>
                              {item.active ? (
                                <Button type="button" size="sm" variant="outline" onClick={() => disableMutation.mutate(item.id)}>禁用</Button>
                              ) : null}
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
            <EmptyState title="暂无访问密钥" />
          )}
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建门户访问密钥</DialogTitle>
            <DialogDescription>模型支持用逗号或换行分隔。</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={handleCreate}>
            <Input value={form.keyName} onChange={(event) => setForm((current) => ({ ...current, keyName: event.target.value }))} placeholder="Key 名称" />
            <Input value={form.allowedProtocolSuites} onChange={(event) => setForm((current) => ({ ...current, allowedProtocolSuites: event.target.value }))} placeholder="协议，例如 openai,responses" />
            <textarea
              className="min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
              value={form.allowedModels}
              onChange={(event) => setForm((current) => ({ ...current, allowedModels: event.target.value }))}
              placeholder="模型白名单，可留空"
            />
            <div className="grid gap-3 md:grid-cols-3">
              <Input type="number" value={form.rpmLimit} onChange={(event) => setForm((current) => ({ ...current, rpmLimit: event.target.value }))} placeholder="RPM" />
              <Input type="number" value={form.tpmLimit} onChange={(event) => setForm((current) => ({ ...current, tpmLimit: event.target.value }))} placeholder="TPM" />
              <Input type="number" value={form.concurrencyLimit} onChange={(event) => setForm((current) => ({ ...current, concurrencyLimit: event.target.value }))} placeholder="并发" />
            </div>
            <DialogFooter>
              <Button type="submit" disabled={createMutation.isPending}>创建并显示一次性 Secret</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={secret != null} onOpenChange={(nextOpen) => !nextOpen && setSecret(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>一次性 Secret</DialogTitle>
          </DialogHeader>
          {secret ? <CodePanel title={secret.key.keyName} code={secret.fullKey} /> : null}
        </DialogContent>
      </Dialog>
    </PortalFrame>
  )
}

function splitValues(value: string) {
  return value
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}
