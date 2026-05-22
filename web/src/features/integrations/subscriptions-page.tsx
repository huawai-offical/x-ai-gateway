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
import type { NotificationChannel, OutboundSubscription } from './types'

type CreateStep = 'basic' | 'filter'
const CREATE_STEPS: CreateStep[] = ['basic', 'filter']

export function SubscriptionsPage() {
  const queryClient = useQueryClient()
  const [subscriptionName, setSubscriptionName] = useState('')
  const [channelId, setChannelId] = useState('')
  const [eventType, setEventType] = useState('ALERT_OPENED')
  const [severity, setSeverity] = useState('HIGH')
  const [entityType, setEntityType] = useState('CREDENTIAL')
  const [open, setOpen] = useState(false)
  const [step, setStep] = useState<CreateStep>('basic')

  const channelsQuery = useQuery({
    queryKey: ['integrations', 'channels'],
    queryFn: () => apiRequest<NotificationChannel[]>('/admin/integrations/channels'),
  })
  const subscriptionsQuery = useQuery({
    queryKey: ['integrations', 'subscriptions'],
    queryFn: () => apiRequest<OutboundSubscription[]>('/admin/integrations/subscriptions'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<OutboundSubscription>('/admin/integrations/subscriptions', {
        method: 'POST',
        body: JSON.stringify({
          subscriptionName,
          channelId: Number(channelId),
          eventType,
          severity,
          entityType,
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'subscriptions'] })
      setSubscriptionName('')
      setStep('basic')
      setOpen(false)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest<void>(`/admin/integrations/subscriptions/${id}`, {
        method: 'DELETE',
        responseType: 'void',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['integrations', 'subscriptions'] })
    },
  })

  const stepIndex = CREATE_STEPS.indexOf(step)
  const canPrev = stepIndex > 0
  const canNext = stepIndex < CREATE_STEPS.length - 1

  const handleDelete = (item: OutboundSubscription) => {
    if (!window.confirm(`确认删除订阅“${item.subscriptionName}”吗？`)) {
      return
    }
    deleteMutation.mutate(item.id)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="生效订阅"
        title="订阅列表"
        actions={(
          <Button
            type="button"
            onClick={() => {
              setStep('basic')
              setOpen(true)
            }}
          >
            创建订阅
          </Button>
        )}
      >
        {(channelsQuery.error || subscriptionsQuery.error || createMutation.error || deleteMutation.error) ? (
          <InlineError error={channelsQuery.error ?? subscriptionsQuery.error ?? createMutation.error ?? deleteMutation.error} title="订阅配置失败" />
        ) : null}
        {subscriptionsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : subscriptionsQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {subscriptionsQuery.data.map((item: OutboundSubscription) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.subscriptionName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                  <StatusBadge tone={item.enabled ? 'success' : 'warning'}>
                    {item.enabled ? '已启用' : '已停用'}
                  </StatusBadge>
                  <div className="text-foreground">{item.eventType ?? 'ALL'} / {item.severity ?? 'ALL'} / {item.entityType ?? 'ALL'}</div>
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
          <EmptyState title="还没有事件订阅" />
        )}
      </PageSection>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建事件订阅</DialogTitle>
            <DialogDescription>填写订阅信息。</DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-4">
            <Tabs value={step} onValueChange={(value) => setStep(value as CreateStep)}>
              <TabsList variant="line">
                <TabsTrigger value="basic">1. 基础信息</TabsTrigger>
                <TabsTrigger value="filter">2. 过滤条件</TabsTrigger>
              </TabsList>
              <TabsContent value="basic" className="pt-3">
                <div className="grid gap-4">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">订阅名称</span>
                    <Input value={subscriptionName} onChange={(event) => setSubscriptionName(event.target.value)} placeholder="请输入订阅名称" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">通知通道</span>
                    <Select value={channelId} onValueChange={setChannelId}>
                      <SelectTrigger className="w-full bg-background" aria-label="通知通道">
                        <SelectValue placeholder="选择通知通道" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {(channelsQuery.data ?? []).map((item: NotificationChannel) => (
                            <SelectItem key={item.id} value={String(item.id)}>
                              {item.channelName}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </label>
                </div>
              </TabsContent>
              <TabsContent value="filter" className="pt-3">
                <div className="grid gap-4 md:grid-cols-3">
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">事件类型</span>
                    <Input value={eventType} onChange={(event) => setEventType(event.target.value)} placeholder="请输入事件类型" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">严重级别</span>
                    <Input value={severity} onChange={(event) => setSeverity(event.target.value)} placeholder="请输入严重级别" />
                  </label>
                  <label className="flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">实体类型</span>
                    <Input value={entityType} onChange={(event) => setEntityType(event.target.value)} placeholder="请输入实体类型" />
                  </label>
                </div>
              </TabsContent>
            </Tabs>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setStep(CREATE_STEPS[Math.max(0, stepIndex - 1)])} disabled={!canPrev}>
                上一步
              </Button>
              <Button type="button" variant="outline" onClick={() => setStep(CREATE_STEPS[Math.min(CREATE_STEPS.length - 1, stepIndex + 1)])} disabled={!canNext}>
                下一步
              </Button>
              <Button type="button" onClick={() => createMutation.mutate()} disabled={!subscriptionName || !channelId || createMutation.isPending}>
                创建订阅
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
