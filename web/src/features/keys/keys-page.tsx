import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowUpRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type DistributedKey = {
  id: number
  keyName: string
  keyPrefix: string
  maskedKey?: string | null
  description?: string | null
  active: boolean
  allowedProtocolSuites: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  allowedClientFamilies: string[]
  requireClientFamilyMatch: boolean
  expiresAt?: string | null
  budgetLimitMicros?: number | null
  budgetWindowSeconds?: number | null
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
  stickySessionTtlSeconds?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

type CreateKeyForm = {
  keyName: string
  description: string
  active: boolean
  templateId: string
  allowedProtocolSuites: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  budgetLimitMicros: string
  budgetWindowSeconds: string
  rpmLimit: string
  tpmLimit: string
  concurrencyLimit: string
  stickySessionTtlSeconds: string
  allowedClientFamilies: string[]
  requireClientFamilyMatch: boolean
  expiresAt: string
}

type CreateKeyResponse = {
  record: DistributedKey
  fullKey: string
}

type StepId = 'basic' | 'scope' | 'limits' | 'client' | 'review'
const STEPS: StepId[] = ['basic', 'scope', 'limits', 'client', 'review']

const PROTOCOL_SUITE_OPTIONS = [
  'openai.native',
  'openai_compatible.generic',
  'azure_openai.openai_compatible',
  'deepseek.openai_compatible',
  'xiaomi_mimo.openai_compatible',
  'qwen.openai_compatible',
  'moonshot.openai_compatible',
  'siliconflow.openai_compatible',
  'volcengine.openai_compatible',
  'minimax.openai_compatible',
  'dify.openai_compatible',
  'grok.openai_compatible',
  'mistral.openai_compatible',
  'cohere.openai_compatible',
  'jina.openai_compatible',
  'together.openai_compatible',
  'fireworks.openai_compatible',
  'openrouter.openai_compatible',
  'perplexity.openai_compatible',
  'anthropic.native',
  'gemini.native',
  'vertex_ai.gemini_native',
  'ollama.native',
] as const
const PROVIDER_OPTIONS = ['OPENAI_DIRECT', 'OPENAI_COMPATIBLE', 'ANTHROPIC_DIRECT', 'GEMINI_DIRECT', 'OLLAMA_DIRECT'] as const
const CLIENT_FAMILY_OPTIONS = ['GENERIC_OPENAI', 'CODEX', 'GEMINI_CLI', 'CLAUDE_CODE'] as const

const MODEL_CATALOG: Array<{ model: string; providerType: (typeof PROVIDER_OPTIONS)[number] }> = [
  { model: 'gpt-4.1', providerType: 'OPENAI_DIRECT' },
  { model: 'gpt-4o', providerType: 'OPENAI_DIRECT' },
  { model: 'gpt-4o-mini', providerType: 'OPENAI_DIRECT' },
  { model: 'o3', providerType: 'OPENAI_DIRECT' },
  { model: 'claude-3-7-sonnet', providerType: 'ANTHROPIC_DIRECT' },
  { model: 'claude-3-5-sonnet', providerType: 'ANTHROPIC_DIRECT' },
  { model: 'gemini-2.5-pro', providerType: 'GEMINI_DIRECT' },
  { model: 'gemini-2.5-flash', providerType: 'GEMINI_DIRECT' },
  { model: 'deepseek-chat', providerType: 'OPENAI_COMPATIBLE' },
  { model: 'qwen-max', providerType: 'OPENAI_COMPATIBLE' },
  { model: 'llama3.1:8b', providerType: 'OLLAMA_DIRECT' },
  { model: 'qwen2.5:7b', providerType: 'OLLAMA_DIRECT' },
]

const KEY_TEMPLATES = [
  {
    id: 'balanced',
    label: '平衡默认',
    hint: '适合大多数团队环境，兼顾安全与吞吐。',
    patch: {
      allowedProtocolSuites: ['openai.native'],
      allowedProviderTypes: ['OPENAI_DIRECT'],
      allowedModels: ['gpt-4o-mini'],
      allowedClientFamilies: ['GENERIC_OPENAI'],
      requireClientFamilyMatch: true,
      budgetWindowSeconds: '3600',
      rpmLimit: '120',
      tpmLimit: '120000',
      concurrencyLimit: '8',
      stickySessionTtlSeconds: '180',
      budgetLimitMicros: '2000000',
      expiresAtDays: 30,
    },
  },
  {
    id: 'throughput',
    label: '高吞吐',
    hint: '适合批处理场景，放宽并发与 TPS。',
    patch: {
      allowedProtocolSuites: ['openai.native', 'openai_compatible.generic', 'deepseek.openai_compatible', 'xiaomi_mimo.openai_compatible'],
      allowedProviderTypes: ['OPENAI_DIRECT', 'OPENAI_COMPATIBLE'],
      allowedModels: ['gpt-4o-mini', 'deepseek-chat'],
      allowedClientFamilies: ['GENERIC_OPENAI', 'CODEX'],
      requireClientFamilyMatch: false,
      budgetWindowSeconds: '3600',
      rpmLimit: '400',
      tpmLimit: '300000',
      concurrencyLimit: '20',
      stickySessionTtlSeconds: '60',
      budgetLimitMicros: '5000000',
      expiresAtDays: 14,
    },
  },
  {
    id: 'strict',
    label: '安全收敛',
    hint: '严格限制调用面，适合生产高风险场景。',
    patch: {
      allowedProtocolSuites: ['openai.native'],
      allowedProviderTypes: ['OPENAI_DIRECT'],
      allowedModels: ['gpt-4.1'],
      allowedClientFamilies: ['GENERIC_OPENAI'],
      requireClientFamilyMatch: true,
      budgetWindowSeconds: '3600',
      rpmLimit: '60',
      tpmLimit: '80000',
      concurrencyLimit: '4',
      stickySessionTtlSeconds: '300',
      budgetLimitMicros: '1000000',
      expiresAtDays: 7,
    },
  },
] as const

export function KeysPage() {
  const queryClient = useQueryClient()
  const [activeStep, setActiveStep] = useState<StepId>('basic')
  const [form, setForm] = useState<CreateKeyForm>(createEmptyForm())
  const [formError, setFormError] = useState<string | null>(null)
  const [createdSecret, setCreatedSecret] = useState<string | null>(null)
  const [createdSecretName, setCreatedSecretName] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [modelProviderFilter, setModelProviderFilter] = useState<'ALL' | (typeof PROVIDER_OPTIONS)[number]>('ALL')
  const [modelKeyword, setModelKeyword] = useState('')

  const keysQuery = useQuery({
    queryKey: ['distributed-keys'],
    queryFn: () => apiRequest<DistributedKey[]>('/admin/distributed-keys'),
  })

  const createMutation = useMutation({
    mutationFn: (payload: ReturnType<typeof buildCreatePayload>) =>
      apiRequest<CreateKeyResponse>('/admin/distributed-keys', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    onSuccess: (result: CreateKeyResponse) => {
      setCreatedSecret(result.fullKey)
      setCreatedSecretName(result.record.keyName)
      queryClient.invalidateQueries({ queryKey: ['distributed-keys'] })
    },
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      apiRequest<DistributedKey>(`/admin/distributed-keys/${id}/status?active=${active}`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['distributed-keys'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/distributed-keys/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['distributed-keys'] })
    },
  })

  const mutationError = createMutation.error ?? toggleMutation.error ?? deleteMutation.error
  const payloadPreview = useMemo(() => {
    try {
      return JSON.stringify(buildCreatePayload(form), null, 2)
    } catch {
      return '// 请先补全必填项'
    }
  }, [form])

  const currentStepIndex = STEPS.indexOf(activeStep)
  const canGoPrev = currentStepIndex > 0
  const canGoNext = currentStepIndex < STEPS.length - 1
  const keys = (keysQuery.data ?? []) as DistributedKey[]

  const filteredModels = useMemo(() => {
    const normalizedKeyword = modelKeyword.trim().toLowerCase()
    return MODEL_CATALOG.filter((item) => {
      const providerMatched = modelProviderFilter === 'ALL' || item.providerType === modelProviderFilter
      const keywordMatched = !normalizedKeyword || item.model.toLowerCase().includes(normalizedKeyword)
      return providerMatched && keywordMatched
    })
  }, [modelKeyword, modelProviderFilter])

  const handleNext = () => {
    if (!canGoNext) {
      return
    }
    setActiveStep(STEPS[currentStepIndex + 1])
  }

  const handlePrev = () => {
    if (!canGoPrev) {
      return
    }
    setActiveStep(STEPS[currentStepIndex - 1])
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      createMutation.mutate(buildCreatePayload(form))
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '创建访问密钥失败。')
    }
  }

  const handleDelete = (item: DistributedKey) => {
    if (!window.confirm(`确认删除访问密钥“${item.keyName}”吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  const applyTemplate = (templateId: string) => {
    const template = KEY_TEMPLATES.find((item) => item.id === templateId)
    if (!template) {
      return
    }
    setForm((current) => ({
      ...current,
      templateId,
      allowedProtocolSuites: [...template.patch.allowedProtocolSuites],
      allowedProviderTypes: [...template.patch.allowedProviderTypes],
      allowedModels: [...template.patch.allowedModels],
      allowedClientFamilies: [...template.patch.allowedClientFamilies],
      requireClientFamilyMatch: template.patch.requireClientFamilyMatch,
      budgetLimitMicros: template.patch.budgetLimitMicros,
      budgetWindowSeconds: template.patch.budgetWindowSeconds,
      rpmLimit: template.patch.rpmLimit,
      tpmLimit: template.patch.tpmLimit,
      concurrencyLimit: template.patch.concurrencyLimit,
      stickySessionTtlSeconds: template.patch.stickySessionTtlSeconds,
      expiresAt: toDatetimeLocalValue(template.patch.expiresAtDays),
    }))
  }

  const handleSelectAllVisibleModels = () => {
    setForm((current) => ({
      ...current,
      allowedModels: Array.from(new Set([...current.allowedModels, ...filteredModels.map((item) => item.model)])),
    }))
  }

  const handleClearVisibleModels = () => {
    const visible = new Set(filteredModels.map((item) => item.model))
    setForm((current) => ({
      ...current,
      allowedModels: current.allowedModels.filter((model) => !visible.has(model)),
    }))
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="访问密钥清单"
        title="策略对象列表"
        actions={(
          <Button
            type="button"
            onClick={() => {
              setForm(createEmptyForm())
              setFormError(null)
              setCreatedSecret(null)
              setCreatedSecretName(null)
              setActiveStep('basic')
              setModelProviderFilter('ALL')
              setModelKeyword('')
              setCreateOpen(true)
            }}
          >
            创建访问密钥
          </Button>
        )}
      >
        {(toggleMutation.error || deleteMutation.error) ? (
          <InlineError error={toggleMutation.error ?? deleteMutation.error} title="访问密钥操作失败" />
        ) : null}
        {keysQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : keysQuery.error ? (
          <InlineError error={keysQuery.error} title="访问密钥列表加载失败" />
        ) : keys.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {keys.map((item: DistributedKey) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.keyName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-4 p-5">
                  <div className="text-sm text-muted-foreground">{item.keyPrefix}</div>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge tone={item.active ? 'success' : 'warning'}>{item.active ? '启用' : '停用'}</StatusBadge>
                    <StatusBadge>{item.allowedProtocolSuites.length ? item.allowedProtocolSuites.join(', ') : '全部协议簇'}</StatusBadge>
                  </div>
                  <div className="text-sm text-muted-foreground">允许提供方：{item.allowedProviderTypes.length ? item.allowedProviderTypes.join(', ') : '全部'}</div>
                  <div className="text-sm text-muted-foreground">最近更新：{formatInstant(item.updatedAt)}</div>
                  <div className="flex flex-wrap gap-2">
                    <Button asChild variant="outline" size="sm">
                      <Link to={`/keys/${item.id}`}>
                        查看策略
                        <ArrowUpRightIcon data-icon="inline-end" />
                      </Link>
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => toggleMutation.mutate({ id: item.id, active: !item.active })}
                      disabled={toggleMutation.isPending}
                    >
                      {item.active ? '停用' : '启用'}
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
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="还没有访问密钥策略" />
        )}
      </PageSection>

      <Dialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open)
          if (!open) {
            setFormError(null)
            setCreatedSecret(null)
            setCreatedSecretName(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建访问密钥</DialogTitle>
            <DialogDescription>优先通过选择、模板和批量筛选完成策略配置，尽量减少手填。</DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <Tabs value={activeStep} onValueChange={(value) => setActiveStep(value as StepId)}>
              <TabsList variant="line" className="w-full justify-start overflow-x-auto">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="scope">2. 协议与模型</TabsTrigger>
                <TabsTrigger value="limits">3. 配额与限流</TabsTrigger>
                <TabsTrigger value="client">4. 客户端限制</TabsTrigger>
                <TabsTrigger value="review">5. 预览提交</TabsTrigger>
              </TabsList>

              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">策略模板</span>
                    <Select value={form.templateId} onValueChange={(value) => setForm((current) => ({ ...current, templateId: value }))}>
                      <SelectTrigger className="w-full bg-background" aria-label="策略模板">
                        <SelectValue placeholder="选择模板" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {KEY_TEMPLATES.map((template) => (
                            <SelectItem key={template.id} value={template.id}>
                              {template.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </label>
                  <div className="flex items-end">
                    <Button type="button" variant="outline" onClick={() => applyTemplate(form.templateId)} className="w-full">
                      应用模板默认值
                    </Button>
                  </div>
                  <div className="rounded-2xl border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground md:col-span-2">
                    {KEY_TEMPLATES.find((item) => item.id === form.templateId)?.hint}
                  </div>

                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">访问密钥名称</span>
                    <Input
                      value={form.keyName}
                      onChange={(event) => setForm((current) => ({ ...current, keyName: event.target.value }))}
                      placeholder="例如：Production Default Key"
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">过期时间</span>
                    <Input
                      type="datetime-local"
                      value={form.expiresAt}
                      onChange={(event) => setForm((current) => ({ ...current, expiresAt: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2 md:col-span-2">
                    <span className="text-sm font-medium text-foreground">说明</span>
                    <Input
                      value={form.description}
                      onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                      placeholder="可选说明"
                    />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="scope" className="pt-3">
                <div className="flex flex-col gap-4">
                  <OptionToggleGroup
                    title="允许厂商协议簇"
                    options={PROTOCOL_SUITE_OPTIONS}
                    selected={form.allowedProtocolSuites}
                    onToggle={(value) => setForm((current) => ({ ...current, allowedProtocolSuites: toggleOption(current.allowedProtocolSuites, value) }))}
                  />

                  <OptionToggleGroup
                    title="允许提供方"
                    options={PROVIDER_OPTIONS}
                    selected={form.allowedProviderTypes}
                    onToggle={(value) => setForm((current) => ({ ...current, allowedProviderTypes: toggleOption(current.allowedProviderTypes, value) }))}
                  />

                  <div className="rounded-2xl border border-border/60 bg-muted/10 px-4 py-4">
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <div className="text-sm font-medium text-foreground">允许模型</div>
                        <div className="text-xs text-muted-foreground">已选择 {form.allowedModels.length} 个模型，支持按 provider + 关键词批量筛选。</div>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={handleSelectAllVisibleModels}>
                          全选可见
                        </Button>
                        <Button type="button" variant="outline" size="sm" onClick={handleClearVisibleModels}>
                          清空可见
                        </Button>
                      </div>
                    </div>
                    <div className="mb-3 grid gap-3 md:grid-cols-2">
                      <label className="flex flex-col gap-2">
                        <span className="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">Provider 筛选</span>
                        <Select value={modelProviderFilter} onValueChange={(value) => setModelProviderFilter(value as typeof modelProviderFilter)}>
                          <SelectTrigger className="w-full bg-background" aria-label="Provider 筛选">
                            <SelectValue placeholder="选择 provider" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectGroup>
                              <SelectItem value="ALL">全部</SelectItem>
                              {PROVIDER_OPTIONS.map((option) => (
                                <SelectItem key={option} value={option}>
                                  {option}
                                </SelectItem>
                              ))}
                            </SelectGroup>
                          </SelectContent>
                        </Select>
                      </label>
                      <label className="flex flex-col gap-2">
                        <span className="text-xs font-medium uppercase tracking-[0.14em] text-muted-foreground">模型关键字</span>
                        <Input value={modelKeyword} onChange={(event) => setModelKeyword(event.target.value)} placeholder="例如 gpt / gemini / claude" />
                      </label>
                    </div>
                    <div className="grid gap-2 md:grid-cols-2">
                      {filteredModels.map((item) => (
                        <label key={item.model} className="flex items-center gap-3 rounded-xl border border-border/60 bg-background px-3 py-2">
                          <input
                            type="checkbox"
                            className="size-4 rounded border-border"
                            checked={form.allowedModels.includes(item.model)}
                            onChange={() =>
                              setForm((current) => ({
                                ...current,
                                allowedModels: toggleOption(current.allowedModels, item.model),
                              }))
                            }
                          />
                          <span className="min-w-0 text-sm text-foreground">{item.model}</span>
                          <StatusBadge tone="info">{item.providerType}</StatusBadge>
                        </label>
                      ))}
                    </div>
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="limits" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">预算上限（micros）</span>
                    <Input
                      type="number"
                      value={form.budgetLimitMicros}
                      onChange={(event) => setForm((current) => ({ ...current, budgetLimitMicros: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">预算窗口（秒）</span>
                    <Input
                      type="number"
                      value={form.budgetWindowSeconds}
                      onChange={(event) => setForm((current) => ({ ...current, budgetWindowSeconds: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">RPM</span>
                    <Input
                      type="number"
                      value={form.rpmLimit}
                      onChange={(event) => setForm((current) => ({ ...current, rpmLimit: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">TPM</span>
                    <Input
                      type="number"
                      value={form.tpmLimit}
                      onChange={(event) => setForm((current) => ({ ...current, tpmLimit: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">并发上限</span>
                    <Input
                      type="number"
                      value={form.concurrencyLimit}
                      onChange={(event) => setForm((current) => ({ ...current, concurrencyLimit: event.target.value }))}
                    />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">会话粘性 TTL（秒）</span>
                    <Input
                      type="number"
                      value={form.stickySessionTtlSeconds}
                      onChange={(event) => setForm((current) => ({ ...current, stickySessionTtlSeconds: event.target.value }))}
                    />
                  </label>
                </div>
              </TabsContent>

              <TabsContent value="client" className="pt-3">
                <div className="grid gap-4 md:grid-cols-2">
                  <OptionToggleGroup
                    title="允许客户端家族"
                    options={CLIENT_FAMILY_OPTIONS}
                    selected={form.allowedClientFamilies}
                    onToggle={(value) => setForm((current) => ({ ...current, allowedClientFamilies: toggleOption(current.allowedClientFamilies, value) }))}
                    className="md:col-span-2"
                  />
                  <label className="flex items-center gap-3 rounded-2xl border border-border/60 bg-muted/20 px-4 py-3">
                    <input
                      type="checkbox"
                      className="size-4 rounded border-border"
                      checked={form.requireClientFamilyMatch}
                      onChange={(event) => setForm((current) => ({ ...current, requireClientFamilyMatch: event.target.checked }))}
                    />
                    <span className="text-sm font-medium text-foreground">强制客户端家族匹配</span>
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

              <TabsContent value="review" className="pt-3">
                <CodePanel title="提交预览" code={payloadPreview} />
              </TabsContent>
            </Tabs>

            {(mutationError || formError) ? (
              <InlineError
                error={mutationError ?? new Error(formError ?? '访问密钥操作失败')}
                title="访问密钥操作失败"
              />
            ) : null}

            {createdSecret ? (
              <div className="rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-4">
                <div className="mb-3 text-sm text-amber-900">新密钥“{createdSecretName ?? ''}”仅展示一次，请立即妥善保存。</div>
                <CodePanel title="fullKey" code={createdSecret} />
              </div>
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={handlePrev} disabled={!canGoPrev}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={handleNext} disabled={!canGoNext}>
                下一步
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                创建访问密钥
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function OptionToggleGroup({
  title,
  options,
  selected,
  onToggle,
  className,
}: {
  title: string
  options: readonly string[]
  selected: string[]
  onToggle: (value: string) => void
  className?: string
}) {
  return (
    <div className={className}>
      <div className="mb-2 text-sm font-medium text-foreground">{title}</div>
      <div className="grid gap-2 md:grid-cols-2">
        {options.map((option) => (
          <label key={option} className="flex items-center gap-3 rounded-xl border border-border/60 bg-background px-3 py-2">
            <input
              type="checkbox"
              className="size-4 rounded border-border"
              checked={selected.includes(option)}
              onChange={() => onToggle(option)}
            />
            <span className="text-sm text-foreground">{option}</span>
          </label>
        ))}
      </div>
    </div>
  )
}

function createEmptyForm(): CreateKeyForm {
  return {
    keyName: '',
    description: '',
    active: true,
    templateId: 'balanced',
    allowedProtocolSuites: ['openai.native'],
    allowedModels: ['gpt-4o-mini'],
    allowedProviderTypes: ['OPENAI_DIRECT'],
    budgetLimitMicros: '2000000',
    budgetWindowSeconds: '3600',
    rpmLimit: '120',
    tpmLimit: '120000',
    concurrencyLimit: '8',
    stickySessionTtlSeconds: '180',
    allowedClientFamilies: ['GENERIC_OPENAI'],
    requireClientFamilyMatch: true,
    expiresAt: toDatetimeLocalValue(30),
  }
}

function buildCreatePayload(form: CreateKeyForm) {
  const keyName = form.keyName.trim()
  if (!keyName) {
    throw new Error('访问密钥名称不能为空。')
  }

  return {
    keyName,
    description: form.description.trim() || null,
    active: form.active,
    allowedProtocolSuites: Array.from(new Set(form.allowedProtocolSuites.map(normalizeProtocolSuite).filter(Boolean))),
    allowedModels: Array.from(new Set(form.allowedModels)),
    allowedProviderTypes: Array.from(new Set(form.allowedProviderTypes.map((item) => item.toUpperCase()))),
    expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null,
    budgetLimitMicros: parseOptionalNumber(form.budgetLimitMicros),
    budgetWindowSeconds: parseOptionalNumber(form.budgetWindowSeconds),
    rpmLimit: parseOptionalNumber(form.rpmLimit),
    tpmLimit: parseOptionalNumber(form.tpmLimit),
    concurrencyLimit: parseOptionalNumber(form.concurrencyLimit),
    stickySessionTtlSeconds: parseOptionalNumber(form.stickySessionTtlSeconds),
    allowedClientFamilies: Array.from(new Set(form.allowedClientFamilies.map((item) => item.toUpperCase()))),
    requireClientFamilyMatch: form.requireClientFamilyMatch,
  }
}

function toggleOption(current: string[], nextValue: string) {
  if (current.includes(nextValue)) {
    return current.filter((item) => item !== nextValue)
  }
  return [...current, nextValue]
}

function normalizeProtocolSuite(value: string) {
  return value.trim().toLowerCase().replaceAll('-', '_').replaceAll('/', '.')
}

function parseOptionalNumber(value: string) {
  if (!value.trim()) {
    return null
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error('数字字段必须填写有效数字。')
  }
  return parsed
}

function toDatetimeLocalValue(daysFromNow: number) {
  const target = new Date(Date.now() + daysFromNow * 24 * 60 * 60 * 1000)
  const offset = target.getTimezoneOffset()
  const local = new Date(target.getTime() - offset * 60 * 1000)
  return local.toISOString().slice(0, 16)
}
