import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/app/empty-state'
import { useConfirm } from '@/components/app/confirm-provider'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'
import type { WebhookEndpoint } from './types'

type CreateStep = 'basic' | 'security'
const CREATE_STEPS: CreateStep[] = ['basic', 'security']

export function WebhooksPage() {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [endpointName, setEndpointName] = useState('')
  const [endpointUrl, setEndpointUrl] = useState('')
  const [secret, setSecret] = useState('')
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<CreateStep>('basic')

  const webhooksQuery = useQuery({
    queryKey: ['integrations', 'webhooks'],
    queryFn: () => apiRequest<WebhookEndpoint[]>('/admin/integrations/webhooks'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<WebhookEndpoint>('/admin/integrations/webhooks', {
        method: 'POST',
        body: JSON.stringify({
          endpointName,
          endpointUrl,
          secret,
          signingMode: 'HMAC_SHA256',
          timeoutMs: 5000,
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'webhooks'] })
      setEndpointName('')
      setEndpointUrl('')
      setSecret('')
      setStep('basic')
      setOpen(false)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/integrations/webhooks/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'webhooks'] })
    },
  })

  const stepIndex = CREATE_STEPS.indexOf(step)
  const canPrev = stepIndex > 0
  const canNext = stepIndex < CREATE_STEPS.length - 1

  const handleDelete = async (item: WebhookEndpoint) => {
    const confirmed = await confirm({
      title: '删除回调终端',
      description: `确认删除“${item.endpointName}”吗？该操作会立即移除这条回调终端。`,
      confirmLabel: '删除',
      destructive: true,
    })
    if (!confirmed) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="终端清单"
        title="已配置回调终端"
        actions={(
          <Button
            type="button"
            onClick={() => {
              setStep('basic')
              setOpen(true)
            }}
          >
            创建终端
          </Button>
        )}
      >
        {(createMutation.error || deleteMutation.error) ? (
          <InlineError error={createMutation.error ?? deleteMutation.error} title="回调终端操作失败" />
        ) : null}
        {webhooksQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : webhooksQuery.error ? (
          <InlineError error={webhooksQuery.error} title="回调终端列表加载失败" />
        ) : webhooksQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {webhooksQuery.data.map((item: WebhookEndpoint) => (
              <Card key={item.id} className="border-border/45 bg-card/82 shadow-[0_1px_2px_rgba(15,23,42,0.06)]">
                <CardHeader className="gap-2 border-b border-border/45">
                  <CardTitle className="text-base">{item.endpointName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge tone={item.enabled ? 'success' : 'warning'}>
                      {item.enabled ? '已启用' : '已停用'}
                    </StatusBadge>
                    <StatusBadge>{item.signingMode}</StatusBadge>
                  </div>
                  <div className="text-foreground">超时时间：{item.timeoutMs} ms</div>
                  <div className="text-foreground">secret 指纹：{item.secretFingerprint ?? '未配置'}</div>
                  <div className="text-foreground">回调地址：{item.endpointUrl}</div>
                  <div className="pt-1">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={deleteMutation.isPending}
                      onClick={() => void handleDelete(item)}
                    >
                      删除
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="还没有回调终端" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建回调终端</DialogTitle>
            <DialogDescription>填写回调终端信息。</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <Tabs value={step} onValueChange={(value) => setStep(value as CreateStep)}>
              <TabsList variant="line">
                <TabsTrigger value="basic">1. 终端信息</TabsTrigger>
                <TabsTrigger value="security">2. 签名配置</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">终端名称</span>
                    <Input value={endpointName} onChange={(event) => setEndpointName(event.target.value)} placeholder="请输入终端名称" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">终端 URL</span>
                    <Input value={endpointUrl} onChange={(event) => setEndpointUrl(event.target.value)} placeholder="https://example.com/hook" />
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="security" className="pt-3">
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">密钥</span>
                  <Input value={secret} onChange={(event) => setSecret(event.target.value)} placeholder="可选密钥" />
                </label>
              </TabsContent>
            </Tabs>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setStep(CREATE_STEPS[Math.max(0, stepIndex - 1)])} disabled={!canPrev}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={() => setStep(CREATE_STEPS[Math.min(CREATE_STEPS.length - 1, stepIndex + 1)])} disabled={!canNext}>
                下一步
              </Button>
              <Button
                type="button"
                onClick={() => createMutation.mutate()}
                disabled={!endpointName || !endpointUrl || createMutation.isPending}
              >
                创建终端
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
