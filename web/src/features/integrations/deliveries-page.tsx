import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRightIcon, RefreshCwIcon, SendHorizontalIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { formatInstant } from '@/lib/format'
import { apiRequest } from '@/lib/api'
import type { NotificationChannel, OutboundDelivery } from './types'
import { useTypedQuery, useTypedMutation } from '@/lib/typed-react-query'

export function DeliveriesPage() {
  const queryClient = useQueryClient()
  const [eventType, setEventType] = useState('')
  const [deliveryStatus, setDeliveryStatus] = useState('')
  const [testChannelId, setTestChannelId] = useState('')

  const channelsQuery = useTypedQuery<NotificationChannel[]>({
    queryKey: ['integrations', 'channels'],
    queryFn: () => apiRequest<NotificationChannel[]>('/admin/integrations/channels'),
  })
  const deliveriesQuery = useTypedQuery<OutboundDelivery[]>({
    queryKey: ['integrations', 'deliveries', eventType, deliveryStatus],
    queryFn: () => {
      const search = new URLSearchParams()
      if (eventType) search.set('eventType', eventType)
      if (deliveryStatus) search.set('deliveryStatus', deliveryStatus)
      const query = search.toString()
      return apiRequest<OutboundDelivery[]>(`/admin/integrations/deliveries${query ? `?${query}` : ''}`)
    },
  })

  const replayMutation = useTypedMutation<OutboundDelivery, number>({
    mutationFn: (id: number) => apiRequest<OutboundDelivery>(`/admin/integrations/deliveries/${id}/replay`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['integrations', 'deliveries'] }),
  })
  const testMutation = useTypedMutation<OutboundDelivery, void>({
    mutationFn: () =>
      apiRequest<OutboundDelivery>('/admin/integrations/test-delivery', {
        method: 'POST',
        body: JSON.stringify({
          channelId: Number(testChannelId),
          eventType: 'ALERT_OPENED',
          severity: 'INFO',
          entityType: 'SYSTEM',
          entityRef: 'test',
          summary: 'manual test delivery',
          details: { source: 'g5-ui-test' },
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['integrations', 'deliveries'] }),
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="overflow-hidden rounded-xl border border-border/45 bg-card/82 p-5 shadow-[0_1px_2px_rgba(15,23,42,0.06)]">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6 relative z-10">
          <div className="space-y-1.5 max-w-xl">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-border/45 bg-muted/14 px-3 py-1 text-xs font-semibold text-foreground">
              <span className="h-1.5 w-1.5 rounded-full bg-primary animate-pulse" />
              白盒排障控制台
            </span>
          </div>
          
          {/* Stepper Steps */}
          <div className="flex flex-wrap items-center gap-4 text-xs font-medium">
            <div className="flex items-center gap-2 rounded-xl border border-border/40 bg-background/50 p-2.5 shadow-sm">
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-primary/10 text-[10px] font-bold text-primary">1</span>
              <span>发送测试投递</span>
            </div>
            <span className="text-muted-foreground hidden sm:inline">➔</span>
            <div className="flex items-center gap-2 rounded-xl border border-border/40 bg-background/50 p-2.5 shadow-sm">
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-primary/10 text-[10px] font-bold text-primary">2</span>
              <span>落库审计 payload</span>
            </div>
            <span className="text-muted-foreground hidden sm:inline">➔</span>
            <div className="flex items-center gap-2 rounded-xl border border-border/40 bg-background/50 p-2.5 shadow-sm">
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-primary/10 text-[10px] font-bold text-primary">3</span>
              <span>失败一键重放</span>
            </div>
          </div>
        </div>
      </div>

      <PageSection
        kicker="投递记录"
        title="投递记录、重试与重放"
      >
        <div className="grid items-end gap-4 rounded-xl border border-border/45 bg-muted/12 p-4 md:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_18rem_auto]">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">事件类型</span>
            <Input value={eventType} onChange={(event) => setEventType(event.target.value)} placeholder="请输入事件类型" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">投递状态</span>
            <Input value={deliveryStatus} onChange={(event) => setDeliveryStatus(event.target.value)} placeholder="请输入投递状态" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">测试通道</span>
            <Select value={testChannelId} onValueChange={setTestChannelId}>
              <SelectTrigger className="w-full bg-background/80" aria-label="test channel">
                <SelectValue placeholder="选择测试 channel" />
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
          <div className="flex items-end">
            <Button
              type="button" 
              className={cn(
                "w-full transition-all duration-300",
                testMutation.isPending && "bg-primary/20 border-primary animate-pulse"
              )}
              onClick={() => testMutation.mutate()} 
              disabled={!testChannelId || testMutation.isPending}
            >
              {testMutation.isPending ? (
                <span className="flex items-center gap-1.5">
                  <span className="h-1.5 w-1.5 bg-primary rounded-full animate-ping" />
                  投递中...
                </span>
              ) : (
                <>
                  <SendHorizontalIcon className="w-4 h-4 mr-2" />
                  发送测试投递
                </>
              )}
            </Button>
          </div>
        </div>
        {(channelsQuery.error || deliveriesQuery.error || replayMutation.error || testMutation.error) ? (
          <div className="mt-4">
            <InlineError
              error={channelsQuery.error ?? deliveriesQuery.error ?? replayMutation.error ?? testMutation.error}
              title="投递操作失败"
            />
          </div>
        ) : null}
      </PageSection>

      <PageSection kicker="最近投递" title="最近投递">
        {deliveriesQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : deliveriesQuery.data?.length ? (
          <div className="grid gap-4">
            {deliveriesQuery.data.map((item: OutboundDelivery) => {
              const isReplaying = replayMutation.isPending && replayMutation.variables === item.id;
              return (
                <Card key={item.id} className="overflow-hidden border-border/45 bg-card/82 shadow-[0_1px_2px_rgba(15,23,42,0.06)] backdrop-blur transition-colors duration-200 hover:border-border/55">
                  <CardHeader className="gap-2 border-b border-border/40 px-5 py-4 bg-muted/20">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-2">
                        <span className="h-2 w-2 rounded-full bg-primary/60 shrink-0" />
                        <CardTitle className="text-base font-semibold tracking-tight">{item.eventType}</CardTitle>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <StatusBadge tone={item.deliveryStatus === 'SUCCEEDED' ? 'success' : 'warning'}>
                          {item.deliveryStatus}
                        </StatusBadge>
                        <StatusBadge>第 {item.attemptCount} 次投递</StatusBadge>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-4 p-5">
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 text-xs">
                      <div className="bg-muted/30 p-2.5 rounded-lg border border-border/20">
                        <div className="text-muted-foreground mb-0.5">响应摘要</div>
                        <div className="font-semibold text-foreground truncate">{item.responseSummary ?? item.lastError ?? '等待投递结果'}</div>
                      </div>
                      <div className="bg-muted/30 p-2.5 rounded-lg border border-border/20">
                        <div className="text-muted-foreground mb-0.5">发生时间</div>
                        <div className="font-semibold text-foreground">{formatInstant(item.occurredAt)}</div>
                      </div>
                      <div className="bg-muted/30 p-2.5 rounded-lg border border-border/20">
                        <div className="text-muted-foreground mb-0.5">下次重试</div>
                        <div className="font-semibold text-foreground">{item.nextRetryAt ? formatInstant(item.nextRetryAt) : '无重试'}</div>
                      </div>
                      <div className="bg-muted/30 p-2.5 rounded-lg border border-border/20">
                        <div className="text-muted-foreground mb-0.5">HTTP 状态码</div>
                        <div className="font-semibold text-foreground font-mono">{item.responseCode ?? '-'}</div>
                      </div>
                    </div>

                    <CodePanel title="载荷 payload" code={item.payloadJson} className="overflow-hidden rounded-xl border border-border/50 max-h-56" />

                    <div className="flex flex-wrap gap-2 pt-2 border-t border-border/20">
                      {item.requestId ? (
                        <Button asChild variant="outline" size="sm" className="rounded-lg">
                          <Link to={`/traces?requestId=${encodeURIComponent(item.requestId)}`}>
                            查看 Trace
                            <ArrowUpRightIcon className="w-3.5 h-3.5 ml-1.5 text-primary" />
                          </Link>
                        </Button>
                      ) : null}
                      {item.deliveryStatus !== 'SUCCEEDED' ? (
                        <Button 
                          type="button" 
                          variant="outline" 
                          size="sm" 
                          className={cn(
                            "rounded-lg transition-all duration-300",
                            isReplaying && "bg-primary/10 border-primary/30 animate-pulse text-primary"
                          )}
                          onClick={() => replayMutation.mutate(item.id)}
                          disabled={replayMutation.isPending}
                        >
                          {isReplaying ? (
                            <span className="flex items-center gap-1.5">
                              <RefreshCwIcon className="w-3.5 h-3.5 animate-spin" />
                              正在重放投递...
                            </span>
                          ) : (
                            <>
                              <RefreshCwIcon className="w-3.5 h-3.5 mr-1.5 text-primary" />
                              一键重放
                            </>
                          )}
                        </Button>
                      ) : null}
                    </div>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        ) : (
          <EmptyState title="当前没有投递记录" />
        )}
      </PageSection>
    </div>
  )
}
