import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'

type ExternalApp = {
  id: number
  appName: string
  slug: string
  iframeUrl: string
  allowedOrigin: string
  sandboxPermissions?: string | null
  signingSecretFingerprint?: string | null
  enabled: boolean
  navEnabled: boolean
  description?: string | null
}

type SignedContext = {
  slug: string
  origin: string
  context: string
  signature: string
  launchUrl: string
  expiresAt: string
}

export function ExternalAppsPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [detail, setDetail] = useState<ExternalApp | null>(null)
  const [preview, setPreview] = useState<SignedContext | null>(null)
  const [form, setForm] = useState({
    appName: '',
    slug: '',
    iframeUrl: '',
    allowedOrigin: '',
    sandboxPermissions: 'allow-scripts allow-forms allow-popups',
    signingSecret: '',
    enabled: true,
    navEnabled: true,
    description: '',
  })

  const appsQuery = useQuery({
    queryKey: ['integrations', 'external-apps'],
    queryFn: () => apiRequest<ExternalApp[]>('/admin/integrations/external-apps'),
  })
  const createMutation = useMutation({
    mutationFn: () => apiRequest<ExternalApp>('/admin/integrations/external-apps', {
      method: 'POST',
      body: JSON.stringify(form),
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'external-apps'] })
      setOpen(false)
      setForm({
        appName: '',
        slug: '',
        iframeUrl: '',
        allowedOrigin: '',
        sandboxPermissions: 'allow-scripts allow-forms allow-popups',
        signingSecret: '',
        enabled: true,
        navEnabled: true,
        description: '',
      })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) => apiRequest<void>(`/admin/integrations/external-apps/${id}`, { method: 'DELETE', responseType: 'void' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['integrations', 'external-apps'] }),
  })
  const previewMutation = useMutation({
    mutationFn: (app: ExternalApp) =>
      apiRequest<SignedContext>(`/admin/integrations/external-apps/${app.id}/signed-context?origin=${encodeURIComponent(app.allowedOrigin)}`),
    onSuccess: setPreview,
  })

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="外部应用"
        title="控制台扩展应用"
        actions={<Button type="button" onClick={() => setOpen(true)}>创建扩展应用</Button>}
      >
        {(createMutation.error || deleteMutation.error || previewMutation.error) ? (
          <InlineError error={createMutation.error ?? deleteMutation.error ?? previewMutation.error} title="扩展应用操作失败" />
        ) : null}
        {appsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : appsQuery.error ? (
          <InlineError error={appsQuery.error} title="扩展应用加载失败" />
        ) : appsQuery.data?.length ? (
          <PaginatedRows items={(appsQuery.data ?? []) as ExternalApp[]}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">应用</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Origin</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Secret 指纹</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((app) => (
                      <tr key={app.id} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-medium text-foreground">{app.appName}</div>
                          <div className="truncate text-muted-foreground">{app.slug}</div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <StatusBadge tone={app.enabled ? 'success' : 'warning'}>{app.enabled ? '启用' : '停用'}</StatusBadge>
                            <StatusBadge tone={app.navEnabled ? 'info' : 'warning'}>{app.navEnabled ? '导航' : '隐藏'}</StatusBadge>
                          </div>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{app.allowedOrigin}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{app.signingSecretFingerprint ?? '-'}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" size="sm" variant="outline" onClick={() => setDetail(app)}>详情</Button>
                            <Button type="button" size="sm" variant="outline" onClick={() => previewMutation.mutate(app)}>签名</Button>
                            {app.enabled && app.navEnabled ? (
                              <Button type="button" size="sm" variant="outline" asChild>
                                <Link to={`/integrations/extensions/${app.slug}`}>运行</Link>
                              </Button>
                            ) : (
                              <Button type="button" size="sm" variant="outline" disabled>运行</Button>
                            )}
                            <Button type="button" size="sm" variant="outline" onClick={() => deleteMutation.mutate(app.id)}>删除</Button>
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
          <EmptyState title="还没有控制台扩展应用" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建控制台扩展应用</DialogTitle>
            <DialogDescription>填写扩展应用信息。</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="应用名称" value={form.appName} onChange={(value) => setForm({ ...form, appName: value })} />
            <Field label="Slug" value={form.slug} onChange={(value) => setForm({ ...form, slug: value })} />
            <Field label="iframe URL" value={form.iframeUrl} onChange={(value) => setForm({ ...form, iframeUrl: value })} className="md:col-span-2" />
            <Field label="允许 Origin" value={form.allowedOrigin} onChange={(value) => setForm({ ...form, allowedOrigin: value })} />
            <Field label="Sandbox 权限" value={form.sandboxPermissions} onChange={(value) => setForm({ ...form, sandboxPermissions: value })} />
            <Field label="签名 Secret" value={form.signingSecret} onChange={(value) => setForm({ ...form, signingSecret: value })} className="md:col-span-2" />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>取消</Button>
            <Button type="button" onClick={() => createMutation.mutate()} disabled={createMutation.isPending || !form.appName || !form.slug || !form.iframeUrl || !form.allowedOrigin}>创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={detail != null} onOpenChange={(next) => !next && setDetail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>扩展应用详情</DialogTitle>
            <DialogDescription>查看扩展应用详情。</DialogDescription>
          </DialogHeader>
          {detail ? (
            <InfoGrid
              items={[
                { key: 'appName', label: '应用名称', value: detail.appName },
                { key: 'slug', label: 'Slug', value: detail.slug },
                { key: 'iframeUrl', label: 'iframe URL', value: detail.iframeUrl },
                { key: 'origin', label: '允许 Origin', value: detail.allowedOrigin },
                { key: 'sandbox', label: 'Sandbox', value: detail.sandboxPermissions ?? '-' },
                { key: 'fingerprint', label: 'Secret 指纹', value: detail.signingSecretFingerprint ?? '-' },
              ]}
              columnsClassName="md:grid-cols-2"
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={preview != null} onOpenChange={(next) => !next && setPreview(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>签名上下文预览</DialogTitle>
            <DialogDescription>查看签名上下文。</DialogDescription>
          </DialogHeader>
          {preview ? <CodePanel title="Launch URL" code={preview.launchUrl} /> : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Field({ label, value, onChange, className }: { label: string; value: string; onChange: (value: string) => void; className?: string }) {
  return (
    <label className={`flex flex-col gap-2 ${className ?? ''}`}>
      <span className="text-sm font-medium text-foreground">{label}</span>
      <Input value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  )
}
