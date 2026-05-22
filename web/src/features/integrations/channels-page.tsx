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
import { apiRequest } from '@/lib/api'
import type { NotificationChannel, WebhookEndpoint } from './types'

type CreateStep = 'basic' | 'target'
const CREATE_STEPS: CreateStep[] = ['basic', 'target']

export function ChannelsPage() {
  const queryClient = useQueryClient()
  const [channelName, setChannelName] = useState('')
  const [channelType, setChannelType] = useState('WEBHOOK')
  const [webhookEndpointId, setWebhookEndpointId] = useState('')
  const [emailTo, setEmailTo] = useState('')
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<CreateStep>('basic')

  const channelsQuery = useQuery({
    queryKey: ['integrations', 'channels'],
    queryFn: () => apiRequest<NotificationChannel[]>('/admin/integrations/channels'),
  })
  const webhooksQuery = useQuery({
    queryKey: ['integrations', 'webhooks'],
    queryFn: () => apiRequest<WebhookEndpoint[]>('/admin/integrations/webhooks'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<NotificationChannel>('/admin/integrations/channels', {
        method: 'POST',
        body: JSON.stringify({
          channelName,
          channelType,
          webhookEndpointId: channelType === 'EMAIL' ? null : Number(webhookEndpointId),
          emailTo: channelType === 'EMAIL' ? emailTo : null,
          templateMode: 'DEFAULT',
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'channels'] })
      setChannelName('')
      setWebhookEndpointId('')
      setEmailTo('')
      setStep('basic')
      setOpen(false)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/integrations/channels/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'channels'] })
    },
  })

  const stepIndex = CREATE_STEPS.indexOf(step)
  const canPrev = stepIndex > 0
  const canNext = stepIndex < CREATE_STEPS.length - 1

  const handleDelete = (item: NotificationChannel) => {
    if (!window.confirm(`确认删除通知通道“${item.channelName}”吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="通道清单"
        title="已配置通道"
        actions={(
          <Button
            type="button"
            onClick={() => {
              setStep('basic')
              setOpen(true)
            }}
          >
            创建通道
          </Button>
        )}
      >
        {(channelsQuery.error || webhooksQuery.error || createMutation.error || deleteMutation.error) ? (
          <InlineError error={channelsQuery.error ?? webhooksQuery.error ?? createMutation.error ?? deleteMutation.error} title="通知通道操作失败" />
        ) : null}
        {channelsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : channelsQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {channelsQuery.data.map((item: NotificationChannel) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.channelName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                  <StatusBadge tone={item.enabled ? 'success' : 'warning'}>
                    {item.enabled ? '已启用' : '已停用'}
                  </StatusBadge>
                  <div className="text-foreground">
                    {item.channelType === 'EMAIL' ? item.emailTo : `终端 #${item.webhookEndpointId ?? '-'}`}
                  </div>
                  <div className="pt-1">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={deleteMutation.isPending}
                      onClick={() => handleDelete(item)}
                    >
                      删除
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="还没有通知通道" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建通知通道</DialogTitle>
            <DialogDescription>填写通道信息。</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <Tabs value={step} onValueChange={(value) => setStep(value as CreateStep)}>
              <TabsList variant="line">
                <TabsTrigger value="basic">1. 通道基础</TabsTrigger>
                <TabsTrigger value="target">2. 投递目标</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">通道名称</span>
                    <Input value={channelName} onChange={(event) => setChannelName(event.target.value)} placeholder="请输入通道名称" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">通道类型</span>
                    <Select value={channelType} onValueChange={setChannelType}>
                      <SelectTrigger className="w-full bg-background" aria-label="通道类型">
                        <SelectValue placeholder="选择通道类型" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value="WEBHOOK">回调</SelectItem>
                          <SelectItem value="IM_WEBHOOK">即时消息回调</SelectItem>
                          <SelectItem value="EMAIL">邮件</SelectItem>
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="target" className="pt-3">
                {channelType === 'EMAIL' ? (
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">邮件接收人</span>
                    <Input value={emailTo} onChange={(event) => setEmailTo(event.target.value)} placeholder="ops@example.com" />
                  </label>
                ) : (
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">回调终端</span>
                    <Select value={webhookEndpointId} onValueChange={setWebhookEndpointId}>
                      <SelectTrigger className="w-full bg-background" aria-label="回调终端">
                        <SelectValue placeholder="选择回调终端" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {(webhooksQuery.data ?? []).map((item: WebhookEndpoint) => (
                            <SelectItem key={item.id} value={String(item.id)}>
                              {item.endpointName}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </label>
                )}
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
                disabled={!channelName || (channelType === 'EMAIL' ? !emailTo : !webhookEndpointId) || createMutation.isPending}
              >
                创建通道
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
