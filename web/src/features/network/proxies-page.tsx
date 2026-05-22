import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowUpRightIcon } from 'lucide-react'
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
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'

type ProxyItem = {
  id: number
  proxyName: string
  proxyUrl: string
  active: boolean
  description?: string | null
}

type ProxyFormState = {
  proxyName: string
  proxyUrl: string
  description: string
  active: boolean
}

type EditStep = 'basic' | 'advanced' | 'submit'
const EDIT_STEPS: EditStep[] = ['basic', 'advanced', 'submit']
const PROXY_SCHEME_OPTIONS = [
  { value: 'http', label: 'HTTP', example: 'http://127.0.0.1:7890' },
  { value: 'https', label: 'HTTPS', example: 'https://proxy.example.com:443' },
  { value: 'socks', label: 'SOCKS', example: 'socks://127.0.0.1:1080' },
  { value: 'socks4', label: 'SOCKS4', example: 'socks4://127.0.0.1:1080' },
  { value: 'socks5', label: 'SOCKS5', example: 'socks5://127.0.0.1:1080' },
] as const

export function ProxiesPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [step, setStep] = useState<EditStep>('basic')
  const [form, setForm] = useState<ProxyFormState>(createEmptyForm())

  const proxiesQuery = useQuery({
    queryKey: ['network-proxies'],
    queryFn: () => apiRequest<ProxyItem[]>('/admin/network/proxies'),
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: ProxyFormState }) => {
      const body = {
        proxyName: payload.proxyName.trim(),
        proxyUrl: payload.proxyUrl.trim(),
        description: payload.description.trim() || null,
        active: payload.active,
      }
      if (id == null) {
        return apiRequest<ProxyItem>('/admin/network/proxies', {
          method: 'POST',
          body: JSON.stringify(body),
        })
      }
      return apiRequest<ProxyItem>(`/admin/network/proxies/${id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
    },
    onSuccess: () => {
      setOpen(false)
      setEditingId(null)
      setStep('basic')
      setForm(createEmptyForm())
      queryClient.invalidateQueries({ queryKey: ['network-proxies'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/network/proxies/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['network-proxies'] })
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!form.proxyName.trim() || !form.proxyUrl.trim()) return
    saveMutation.mutate({ id: editingId, payload: form })
  }

  const handleOpenCreate = () => {
    setOpen(true)
    setEditingId(null)
    setStep('basic')
    setForm(createEmptyForm())
  }

  const handleOpenEdit = (item: ProxyItem) => {
    setOpen(true)
    setEditingId(item.id)
    setStep('basic')
    setForm({
      proxyName: item.proxyName,
      proxyUrl: item.proxyUrl,
      description: item.description ?? '',
      active: item.active,
    })
  }

  const handleDelete = (item: ProxyItem) => {
    if (!window.confirm(`确认删除代理“${item.proxyName}”吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  const sortedProxies = useMemo(
    () => [...(proxiesQuery.data ?? [])].sort((left, right) => left.proxyName.localeCompare(right.proxyName)),
    [proxiesQuery.data],
  )

  const stepIndex = EDIT_STEPS.indexOf(step)
  const selectedProxyScheme = resolveProxyScheme(form.proxyUrl)
  const proxyUrlSupported = !form.proxyUrl.trim() || isSupportedProxyUrl(form.proxyUrl)

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="网络代理"
        title="代理池"
        actions={(
          <Button type="button" onClick={handleOpenCreate}>
            新增代理
          </Button>
        )}
      >
        {(saveMutation.error || deleteMutation.error) ? (
          <InlineError error={saveMutation.error ?? deleteMutation.error} title="代理操作失败" />
        ) : null}
        {proxiesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : proxiesQuery.error ? (
          <InlineError error={proxiesQuery.error} title="代理列表加载失败" />
        ) : sortedProxies.length ? (
          <PaginatedRows items={sortedProxies}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">代理名称</th>
                      <th className="w-[40%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">代理地址</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">启用</th>
                      <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((proxy) => (
                      <tr key={proxy.id} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{proxy.proxyName}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{proxy.proxyUrl}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={proxy.active ? 'success' : 'warning'}>
                            {proxy.active ? '启用' : '停用'}
                          </StatusBadge>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" variant="outline" size="sm" onClick={() => handleOpenEdit(proxy)}>
                              编辑
                            </Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => handleDelete(proxy)} disabled={deleteMutation.isPending}>
                              删除
                            </Button>
                            <Button asChild variant="outline" size="sm">
                              <Link to={`/network/proxies/${proxy.id}`}>
                                详情
                                <ArrowUpRightIcon data-icon="inline-end" />
                              </Link>
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
          <EmptyState title="当前没有代理节点" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingId == null ? '新增代理' : '编辑代理'}</DialogTitle>
            <DialogDescription>填写代理节点信息。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <Tabs value={step} onValueChange={(value) => setStep(value as EditStep)}>
              <TabsList variant="line">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="advanced">2. 选项</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">代理名称</span>
                    <Input value={form.proxyName} onChange={(event) => setForm((current) => ({ ...current, proxyName: event.target.value }))} placeholder="代理名称" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">代理协议</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      value={selectedProxyScheme}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          proxyUrl: applyProxyScheme(current.proxyUrl, event.target.value),
                        }))
                      }
                    >
                      {PROXY_SCHEME_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">代理地址</span>
                    <Input value={form.proxyUrl} onChange={(event) => setForm((current) => ({ ...current, proxyUrl: event.target.value }))} placeholder="socks5://127.0.0.1:1080" />
                  </label>
                  <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm leading-6 text-muted-foreground">
                    支持主流代理 URL：`http://host:port`、`https://host:port`、`socks://host:port`、`socks4://host:port`、`socks5://host:port`。
                  </div>
                  {!proxyUrlSupported ? (
                    <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">
                      代理地址需要使用 http、https、socks、socks4 或 socks5 协议，并包含 host。
                    </div>
                  ) : null}
                </div>
              </TabsContent>
              <TabsContent value="advanced" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">备注（可选）</span>
                    <Input value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} placeholder="例如：北京出口节点" />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.active}
                      onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">启用该代理节点</span>
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="submit" className="pt-3">
                <div className="rounded-2xl border border-border/60 bg-muted/20 p-4 text-sm text-foreground">
                  <div>代理名称：{form.proxyName || '未填写'}</div>
                  <div className="mt-1">代理地址：{form.proxyUrl || '未填写'}</div>
                  <div className="mt-1">状态：{form.active ? '启用' : '停用'}</div>
                  <div className="mt-1">备注：{form.description.trim() || '无'}</div>
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
              <Button type="submit" disabled={saveMutation.isPending || !form.proxyName.trim() || !form.proxyUrl.trim() || !proxyUrlSupported}>
                {editingId == null ? '创建' : '保存'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function resolveProxyScheme(proxyUrl: string) {
  const scheme = proxyUrl.split(':', 1)[0]?.toLowerCase()
  return PROXY_SCHEME_OPTIONS.some((option) => option.value === scheme) ? scheme : 'http'
}

function applyProxyScheme(proxyUrl: string, scheme: string) {
  const option = PROXY_SCHEME_OPTIONS.find((item) => item.value === scheme)
  const trimmed = proxyUrl.trim()
  if (!trimmed) {
    return option?.example ?? `${scheme}://`
  }
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed)) {
    return trimmed.replace(/^[a-z][a-z0-9+.-]*:\/\//i, `${scheme}://`)
  }
  return `${scheme}://${trimmed}`
}

function isSupportedProxyUrl(proxyUrl: string) {
  try {
    const parsed = new URL(proxyUrl.trim())
    const scheme = parsed.protocol.replace(':', '').toLowerCase()
    return PROXY_SCHEME_OPTIONS.some((option) => option.value === scheme) && Boolean(parsed.hostname)
  } catch {
    return false
  }
}

function createEmptyForm(): ProxyFormState {
  return {
    proxyName: '',
    proxyUrl: '',
    description: '',
    active: true,
  }
}
