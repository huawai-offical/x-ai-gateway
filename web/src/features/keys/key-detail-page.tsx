import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
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
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'

type DistributedKey = {
  id: number
  keyName: string
  description?: string | null
  active: boolean
  allowedProtocolSuites: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  allowedClientFamilies: string[]
  requireClientFamilyMatch: boolean
  budgetLimitMicros?: number | null
  budgetWindowSeconds?: number | null
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
}

type ClientConfig = {
  keyName: string
  clientFamily: string
  format: string
  maskedKey: string
  warning: string
  config: string
}

export function KeyDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const keysQuery = useQuery({
    queryKey: ['distributed-keys'],
    queryFn: () => apiRequest<DistributedKey[]>('/admin/distributed-keys'),
  })

  const current = useMemo(
    () => keysQuery.data?.find((item: DistributedKey) => String(item.id) === id),
    [id, keysQuery.data],
  )

  const [draft, setDraft] = useState<DistributedKey | null>(null)
  const [configOpen, setConfigOpen] = useState(false)
  const [configFormat, setConfigFormat] = useState('config_toml')
  const [configClientFamily, setConfigClientFamily] = useState('GENERIC_OPENAI')
  const [configBaseUrl, setConfigBaseUrl] = useState('http://localhost:8080')
  const activeDraft = draft && current && draft.id === current.id ? draft : current ?? null

  const updateMutation = useMutation({
    mutationFn: async () => {
      if (!activeDraft) return
      return apiRequest(`/admin/distributed-keys/${activeDraft.id}`, {
        method: 'PUT',
        body: JSON.stringify(activeDraft),
      })
    },
    onSuccess: () => {
      setDraft(null)
      queryClient.invalidateQueries({ queryKey: ['distributed-keys'] })
    },
  })

  const rotateMutation = useMutation({
    mutationFn: async () => {
      if (!activeDraft) return
      return apiRequest<{ fullKey: string }>(`/admin/distributed-keys/${activeDraft.id}/rotate`, {
        method: 'POST',
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['distributed-keys'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: async () => {
      if (!activeDraft) return
      return apiRequest<void>(`/admin/distributed-keys/${activeDraft.id}`, {
        method: 'DELETE',
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['distributed-keys'] })
      navigate('/keys')
    },
  })

  const exportConfigMutation = useMutation({
    mutationFn: async () => {
      if (!activeDraft) return null
      const params = new URLSearchParams({
        format: configFormat,
        clientFamily: configClientFamily,
      })
      if (configBaseUrl.trim()) {
        params.set('baseUrl', configBaseUrl.trim())
      }
      return apiRequest<ClientConfig>(`/admin/distributed-keys/${activeDraft.id}/client-config?${params.toString()}`)
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    updateMutation.mutate()
  }

  if (keysQuery.isPending) {
    return <PageSkeleton count={1} />
  }

  if (keysQuery.error) {
    return <InlineError error={keysQuery.error} title="访问密钥详情加载失败" />
  }

  if (!activeDraft) {
    return <EmptyState title="未找到指定访问密钥" />
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="访问密钥策略" title={activeDraft.keyName}>
        <div className="flex flex-col gap-6">
          <InfoGrid
            items={[
              { key: 'active', label: '状态', value: <StatusBadge tone={activeDraft.active ? 'success' : 'warning'}>{activeDraft.active ? '启用' : '停用'}</StatusBadge> },
              { key: 'protocols', label: '允许协议', value: activeDraft.allowedProtocolSuites.join(', ') || '全部' },
              { key: 'models', label: '允许模型', value: activeDraft.allowedModels.join(', ') || '全部' },
              { key: 'providers', label: '允许提供方', value: activeDraft.allowedProviderTypes.join(', ') || '全部' },
            ]}
          />

          <form className="grid gap-4" onSubmit={handleSubmit}>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">名称</span>
                <Input value={activeDraft.keyName} onChange={(event) => setDraft({ ...activeDraft, keyName: event.target.value })} />
              </label>

              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">允许提供方</span>
                <Input
                  value={activeDraft.allowedProviderTypes.join(',')}
                  onChange={(event) => setDraft({ ...activeDraft, allowedProviderTypes: splitCsv(event.target.value) })}
                  placeholder="OPENAI_DIRECT,GEMINI_DIRECT"
                />
              </label>
            </div>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">预算 micros</span>
                <Input
                  type="number"
                  value={activeDraft.budgetLimitMicros ?? ''}
                  onChange={(event) => setDraft({ ...activeDraft, budgetLimitMicros: toNullableNumber(event.target.value) })}
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">预算窗口秒</span>
                <Input
                  type="number"
                  value={activeDraft.budgetWindowSeconds ?? ''}
                  onChange={(event) => setDraft({ ...activeDraft, budgetWindowSeconds: toNullableNumber(event.target.value) })}
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">RPM</span>
                <Input
                  type="number"
                  value={activeDraft.rpmLimit ?? ''}
                  onChange={(event) => setDraft({ ...activeDraft, rpmLimit: toNullableNumber(event.target.value) })}
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">TPM</span>
                <Input
                  type="number"
                  value={activeDraft.tpmLimit ?? ''}
                  onChange={(event) => setDraft({ ...activeDraft, tpmLimit: toNullableNumber(event.target.value) })}
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">并发</span>
                <Input
                  type="number"
                  value={activeDraft.concurrencyLimit ?? ''}
                  onChange={(event) => setDraft({ ...activeDraft, concurrencyLimit: toNullableNumber(event.target.value) })}
                />
              </label>
            </div>

            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">允许客户端家族</span>
              <Input
                value={activeDraft.allowedClientFamilies.join(',')}
                onChange={(event) => setDraft({ ...activeDraft, allowedClientFamilies: splitCsv(event.target.value) })}
                placeholder="GENERIC_OPENAI,CODEX"
              />
            </label>

            <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
              <input
                type="checkbox"
                className="size-4 rounded border-border"
                checked={activeDraft.active}
                onChange={(event) => setDraft({ ...activeDraft, active: event.target.checked })}
              />
              <span className="text-sm font-medium text-foreground">启用访问密钥</span>
            </label>

            <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
              <input
                type="checkbox"
                className="size-4 rounded border-border"
                checked={activeDraft.requireClientFamilyMatch}
                onChange={(event) => setDraft({ ...activeDraft, requireClientFamilyMatch: event.target.checked })}
              />
              <span className="text-sm font-medium text-foreground">强制客户端家族匹配</span>
            </label>

            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={updateMutation.isPending}>
                保存策略
              </Button>
              <Button type="button" variant="outline" onClick={() => current && setDraft(current)}>
                重置
              </Button>
              <Button type="button" variant="outline" onClick={() => rotateMutation.mutate()} disabled={rotateMutation.isPending}>
                轮换密钥
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  if (!window.confirm(`确认删除访问密钥“${activeDraft.keyName}”吗？`)) {
                    return
                  }
                  deleteMutation.mutate()
                }}
                disabled={deleteMutation.isPending}
              >
                删除密钥
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setConfigOpen(true)
                  exportConfigMutation.mutate()
                }}
                disabled={exportConfigMutation.isPending}
              >
                导出客户端配置
              </Button>
            </div>
          </form>

          {(updateMutation.error || rotateMutation.error || deleteMutation.error || exportConfigMutation.error) ? (
            <InlineError
              error={updateMutation.error ?? rotateMutation.error ?? deleteMutation.error ?? exportConfigMutation.error}
              title="访问密钥操作失败"
            />
          ) : null}

          {rotateMutation.data?.fullKey ? (
            <CodePanel title="新 fullKey（仅展示一次）" code={rotateMutation.data.fullKey} />
          ) : null}
        </div>
      </PageSection>

      <Dialog open={configOpen} onOpenChange={setConfigOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>导出客户端配置</DialogTitle>
            <DialogDescription>
              生成 Codex / OpenAI-compatible 客户端配置模板。为避免泄露，导出内容只展示 masked key。
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 md:grid-cols-3">
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">格式</span>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={configFormat}
                onChange={(event) => setConfigFormat(event.target.value)}
              >
                <option value="config_toml">config.toml</option>
                <option value="auth_json">auth.json</option>
                <option value="env">环境变量</option>
              </select>
            </label>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">客户端家族</span>
              <select
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={configClientFamily}
                onChange={(event) => setConfigClientFamily(event.target.value)}
              >
                <option value="GENERIC_OPENAI">GENERIC_OPENAI</option>
                <option value="CODEX">CODEX</option>
                <option value="GEMINI_CLI">GEMINI_CLI</option>
                <option value="CLAUDE_CODE">CLAUDE_CODE</option>
              </select>
            </label>
            <label className="flex flex-col gap-2">
              <span className="text-sm font-medium text-foreground">Gateway 地址</span>
              <Input value={configBaseUrl} onChange={(event) => setConfigBaseUrl(event.target.value)} />
            </label>
          </div>

          {exportConfigMutation.data ? (
            <div className="space-y-4">
              <InfoGrid
                items={[
                  { key: 'keyName', label: 'Key 名称', value: exportConfigMutation.data.keyName },
                  { key: 'format', label: '导出格式', value: exportConfigMutation.data.format },
                  { key: 'maskedKey', label: 'Masked Key', value: exportConfigMutation.data.maskedKey },
                  { key: 'warning', label: '安全提示', value: exportConfigMutation.data.warning },
                ]}
                columnsClassName="md:grid-cols-2"
              />
              <CodePanel title="客户端配置模板" code={exportConfigMutation.data.config} />
            </div>
          ) : exportConfigMutation.error ? (
            <InlineError error={exportConfigMutation.error} title="客户端配置导出失败" />
          ) : null}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setConfigOpen(false)}>
              关闭
            </Button>
            <Button type="button" onClick={() => exportConfigMutation.mutate()} disabled={exportConfigMutation.isPending}>
              重新生成
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function splitCsv(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function toNullableNumber(value: string) {
  if (!value.trim()) return null
  return Number(value)
}
