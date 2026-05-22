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
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type TlsProfile = {
  id: number
  profileName: string
  profileCode: string
  settingsJson?: string | null
  description?: string | null
  active: boolean
  updatedAt?: string | null
}

type TlsProfileForm = {
  profileName: string
  profileCode: string
  settingsJson: string
  headers: HeaderRow[]
  description: string
  active: boolean
}

type EditStep = 'basic' | 'settings' | 'submit'
const EDIT_STEPS: EditStep[] = ['basic', 'settings', 'submit']

type HeaderRow = {
  id: string
  key: string
  value: string
}

const DEFAULT_TLS_PRESETS: Array<Omit<TlsProfileForm, 'active'>> = [
  {
    profileName: 'Codex CLI',
    profileCode: 'codex-cli',
    settingsJson: '',
    description: '模拟 Codex CLI / App API 常见请求头。',
    headers: [
      headerRow('accept', 'application/json'),
      headerRow('content-type', 'application/json'),
      headerRow('user-agent', 'codex_cli_rs/x-ai-gateway'),
      headerRow('x-client-family', 'CODEX'),
      headerRow('openai-beta', 'responses=v1'),
    ],
  },
  {
    profileName: 'Claude Code',
    profileCode: 'claude-code',
    settingsJson: '',
    description: '模拟 Claude Code / Anthropic Messages 常见请求头。',
    headers: [
      headerRow('accept', 'application/json'),
      headerRow('content-type', 'application/json'),
      headerRow('user-agent', 'claude-code/x-ai-gateway'),
      headerRow('anthropic-version', '2023-06-01'),
    ],
  },
  {
    profileName: 'Web Browser Chrome',
    profileCode: 'web-browser-chrome',
    settingsJson: '',
    description: '模拟桌面 Chrome 浏览器常见 header 画像。',
    headers: [
      headerRow('accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'),
      headerRow('accept-language', 'zh-CN,zh;q=0.9,en;q=0.8'),
      headerRow('user-agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36'),
      headerRow('sec-ch-ua-mobile', '?0'),
      headerRow('sec-ch-ua-platform', '"Windows"'),
    ],
  },
]

export function TlsProfilesPage() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [step, setStep] = useState<EditStep>('basic')
  const [form, setForm] = useState<TlsProfileForm>(createEmptyForm())

  const profilesQuery = useQuery({
    queryKey: ['tls-profiles'],
    queryFn: () => apiRequest<TlsProfile[]>('/admin/network/tls-profiles'),
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number | null; payload: TlsProfileForm }) => {
      const body = {
        profileName: payload.profileName.trim(),
        profileCode: payload.profileCode.trim(),
        settingsJson: buildSettingsJson(payload),
        description: payload.description.trim() || null,
        active: payload.active,
      }
      if (id == null) {
        return apiRequest<TlsProfile>('/admin/network/tls-profiles', {
          method: 'POST',
          body: JSON.stringify(body),
        })
      }
      return apiRequest<TlsProfile>(`/admin/network/tls-profiles/${id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
    },
    onSuccess: () => {
      setOpen(false)
      setEditingId(null)
      setStep('basic')
      setForm(createEmptyForm())
      queryClient.invalidateQueries({ queryKey: ['tls-profiles'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/network/tls-profiles/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tls-profiles'] })
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!form.profileName.trim() || !form.profileCode.trim()) return
    saveMutation.mutate({ id: editingId, payload: form })
  }

  const handleOpenCreate = () => {
    setOpen(true)
    setEditingId(null)
    setStep('basic')
    setForm(createEmptyForm())
  }

  const handleOpenEdit = (item: TlsProfile) => {
    setOpen(true)
    setEditingId(item.id)
    setStep('basic')
    setForm({
      profileName: item.profileName,
      profileCode: item.profileCode,
      settingsJson: settingsWithoutHeaders(item.settingsJson ?? ''),
      headers: headersFromSettings(item.settingsJson ?? ''),
      description: item.description ?? '',
      active: item.active,
    })
  }

  const handleDelete = (item: TlsProfile) => {
    if (!window.confirm(`确认删除 TLS 指纹画像“${item.profileName}”吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  const stepIndex = EDIT_STEPS.indexOf(step)
  const profiles = useMemo(
    () => [...(profilesQuery.data ?? [])].sort((left, right) => left.profileName.localeCompare(right.profileName)),
    [profilesQuery.data],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="TLS 指纹"
        title="TLS 指纹画像"
        actions={(
          <Button type="button" onClick={handleOpenCreate}>
            新增画像
          </Button>
        )}
      >
        {(saveMutation.error || deleteMutation.error) ? (
          <InlineError error={saveMutation.error ?? deleteMutation.error} title="TLS 指纹操作失败" />
        ) : null}
        {profilesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : profilesQuery.error ? (
          <InlineError error={profilesQuery.error} title="TLS 指纹列表加载失败" />
        ) : profiles.length ? (
          <PaginatedRows items={profiles}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">画像名称</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">画像代码</th>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">备注</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">更新</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((item) => (
                      <tr key={item.id} className="border-b border-border/40 align-top">
                        <td className="truncate px-4 py-3 text-foreground">{item.profileName}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.profileCode}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{item.description ?? '无'}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={item.active ? 'success' : 'warning'}>
                            {item.active ? '启用' : '停用'}
                          </StatusBadge>
                        </td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{formatInstant(item.updatedAt)}</td>
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
          <EmptyState title="当前没有 TLS 指纹画像" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingId == null ? '新增 TLS 指纹画像' : '编辑 TLS 指纹画像'}</DialogTitle>
            <DialogDescription>填写 TLS 指纹信息。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <Tabs value={step} onValueChange={(value) => setStep(value as EditStep)}>
              <TabsList variant="line">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="settings">2. 画像设置</TabsTrigger>
                <TabsTrigger value="submit">3. 提交确认</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">常用画像</span>
                    <select
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                      aria-label="常用画像"
                      value=""
                      onChange={(event) => {
                        const preset = DEFAULT_TLS_PRESETS.find((item) => item.profileCode === event.target.value)
                        if (!preset) return
                        setForm((current) => ({
                          ...current,
                          ...preset,
                          headers: preset.headers.map((row) => ({ ...row, id: nextHeaderRowId() })),
                        }))
                      }}
                    >
                      <option value="">选择常用画像</option>
                      {DEFAULT_TLS_PRESETS.map((preset) => (
                        <option key={preset.profileCode} value={preset.profileCode}>{preset.profileName}</option>
                      ))}
                    </select>
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">画像名称</span>
                    <Input value={form.profileName} onChange={(event) => setForm((current) => ({ ...current, profileName: event.target.value }))} placeholder="画像名称" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">画像代码</span>
                    <Input value={form.profileCode} onChange={(event) => setForm((current) => ({ ...current, profileCode: event.target.value }))} placeholder="chrome-like" />
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="settings" className="pt-3">
                <div className="grid gap-4">
                  <div className="grid gap-3 rounded-2xl border border-border/60 bg-muted/10 p-4">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-sm font-medium text-foreground">Header 键值对</span>
                      <Button type="button" variant="outline" size="sm" onClick={() => setForm((current) => ({ ...current, headers: [...current.headers, headerRow('', '')] }))}>
                        增加一行
                      </Button>
                    </div>
                    <div className="grid gap-2">
                      {form.headers.map((row) => (
                        <div key={row.id} className="grid gap-2 md:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)_auto]">
                          <Input
                            aria-label="Header 名称"
                            value={row.key}
                            onChange={(event) => setForm((current) => updateHeaderRow(current, row.id, { key: event.target.value }))}
                            placeholder="例如 user-agent"
                          />
                          <Input
                            aria-label="Header 值"
                            value={row.value}
                            onChange={(event) => setForm((current) => updateHeaderRow(current, row.id, { value: event.target.value }))}
                            placeholder="Header 值"
                          />
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setForm((current) => removeHeaderRow(current, row.id))}
                            disabled={form.headers.length <= 1}
                          >
                            删除
                          </Button>
                        </div>
                      ))}
                    </div>
                  </div>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">其他设置 JSON</span>
                    <Input value={form.settingsJson} onChange={(event) => setForm((current) => ({ ...current, settingsJson: event.target.value }))} placeholder='例如 {"ja3":"..."}' />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">备注（可选）</span>
                    <Input value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} placeholder="例如：移动端浏览器画像" />
                  </label>
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.active}
                      onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">启用该画像</span>
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="submit" className="pt-3">
                <div className="rounded-2xl border border-border/60 bg-muted/20 p-4 text-sm text-foreground">
                  <div>画像名称：{form.profileName || '未填写'}</div>
                  <div className="mt-1">画像代码：{form.profileCode || '未填写'}</div>
                  <div className="mt-1">Header：{formatCount(form.headers.filter((row) => row.key.trim()).length)} 项</div>
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
              <Button type="submit" disabled={saveMutation.isPending || !form.profileName.trim() || !form.profileCode.trim()}>
                {editingId == null ? '创建' : '保存'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function createEmptyForm(): TlsProfileForm {
  return {
    profileName: '',
    profileCode: '',
    settingsJson: '',
    headers: [headerRow('', '')],
    description: '',
    active: true,
  }
}

function headerRow(key: string, value: string): HeaderRow {
  return { id: nextHeaderRowId(), key, value }
}

function nextHeaderRowId() {
  return Math.random().toString(36).slice(2)
}

function updateHeaderRow(form: TlsProfileForm, id: string, patch: Partial<HeaderRow>): TlsProfileForm {
  return {
    ...form,
    headers: form.headers.map((row) => (row.id === id ? { ...row, ...patch } : row)),
  }
}

function removeHeaderRow(form: TlsProfileForm, id: string): TlsProfileForm {
  const headers = form.headers.filter((row) => row.id !== id)
  return { ...form, headers: headers.length ? headers : [headerRow('', '')] }
}

function headersFromSettings(settingsJson: string): HeaderRow[] {
  const parsed = parseSettings(settingsJson)
  const headers = parsed && typeof parsed.headers === 'object' && parsed.headers != null && !Array.isArray(parsed.headers)
    ? Object.entries(parsed.headers as Record<string, unknown>).map(([key, value]) => headerRow(key, String(value ?? '')))
    : []
  return headers.length ? headers : [headerRow('', '')]
}

function settingsWithoutHeaders(settingsJson: string) {
  const parsed = parseSettings(settingsJson)
  if (!parsed) return settingsJson
  const { headers: _headers, ...rest } = parsed
  return Object.keys(rest).length ? JSON.stringify(rest) : ''
}

function buildSettingsJson(form: TlsProfileForm) {
  const base = parseSettings(form.settingsJson) ?? {}
  const headers: Record<string, string> = {}
  form.headers.forEach((row) => {
    const key = row.key.trim()
    if (!key) return
    headers[key] = row.value
  })
  const settings = {
    ...base,
    ...(Object.keys(headers).length ? { headers } : {}),
  }
  return Object.keys(settings).length ? JSON.stringify(settings) : null
}

function parseSettings(settingsJson: string): Record<string, unknown> | null {
  if (!settingsJson.trim()) return null
  try {
    const parsed = JSON.parse(settingsJson)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : null
  } catch {
    return null
  }
}

function formatCount(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}
