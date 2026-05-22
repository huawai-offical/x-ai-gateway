import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowUpRightIcon, BellRingIcon, ShieldAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { MetricCard } from '@/components/app/metric-card'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { useTypedQuery } from '@/lib/typed-react-query'
import { governanceApi } from './api'
import {
  autoActionToDraft,
  createDefaultAlertSilenceDraft,
  createDefaultAutoActionDraft,
  type AlertRule,
  type AutoActionDraft,
  type AutoActionRule,
  type QuarantineRecord,
} from './types'

export function OpsAlertsPage() {
  const queryClient = useQueryClient()
  const [ruleName, setRuleName] = useState('')
  const [metricKey, setMetricKey] = useState('qps')
  const [thresholdValue, setThresholdValue] = useState('1')
  const [autoActionDraft, setAutoActionDraft] = useState<AutoActionDraft>(createDefaultAutoActionDraft())
  const [silenceDraft, setSilenceDraft] = useState(createDefaultAlertSilenceDraft())

  const alertRulesQuery = useTypedQuery<AlertRule[]>({
    queryKey: ['ops-alert-rules'],
    queryFn: governanceApi.listAlertRules,
  })
  const alertsQuery = useTypedQuery({
    queryKey: ['ops-alert-events'],
    queryFn: governanceApi.listAlertEvents,
  })
  const autoActionsQuery = useTypedQuery<AutoActionRule[]>({
    queryKey: ['ops-auto-actions'],
    queryFn: governanceApi.listAutoActions,
  })
  const healthScoresQuery = useTypedQuery({
    queryKey: ['ops-health-scores'],
    queryFn: governanceApi.listHealthScores,
  })
  const silencesQuery = useTypedQuery({
    queryKey: ['ops-alert-silences'],
    queryFn: governanceApi.listAlertSilences,
  })
  const quarantinesQuery = useTypedQuery<QuarantineRecord[]>({
    queryKey: ['ops-quarantines'],
    queryFn: () => governanceApi.listQuarantines(),
  })
  const outboundDeliveriesQuery = useTypedQuery({
    queryKey: ['ops-outbound-deliveries'],
    queryFn: governanceApi.listOutboundDeliveries,
  })

  const createAlertRuleMutation = useMutation({
    mutationFn: () =>
      governanceApi.createAlertRule({
        ruleName,
        metricKey,
        comparisonOperator: '>',
        thresholdValue: Number(thresholdValue),
        severity: 'HIGH',
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['ops-alert-rules'] })
      setRuleName('')
    },
  })

  const createAutoActionMutation = useMutation({
    mutationFn: () =>
      governanceApi.saveAutoAction({
        id: autoActionDraft.id,
        ruleName: autoActionDraft.ruleName,
        eventType: autoActionDraft.eventType,
        severity: toNullableString(autoActionDraft.severity),
        entityType: toNullableString(autoActionDraft.entityType),
        actionType: autoActionDraft.actionType,
        ttlSeconds: toNullableNumber(autoActionDraft.ttlSeconds),
        recoveryMode: autoActionDraft.recoveryMode,
        enabled: autoActionDraft.enabled,
        description: toNullableString(autoActionDraft.description),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['ops-auto-actions'] })
      setAutoActionDraft(createDefaultAutoActionDraft())
    },
  })

  const deleteAutoActionMutation = useMutation({
    mutationFn: (id: number) => governanceApi.deleteAutoAction(id),
    onSuccess: async (_: void, id: number) => {
      await queryClient.invalidateQueries({ queryKey: ['ops-auto-actions'] })
      setAutoActionDraft((current) => (current.id === id ? createDefaultAutoActionDraft() : current))
    },
  })

  const acknowledgeAlertMutation = useMutation({
    mutationFn: (id: number) => governanceApi.acknowledgeAlert(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-alert-events'] }),
  })

  const createSilenceMutation = useMutation({
    mutationFn: () =>
      governanceApi.saveAlertSilence({
        silenceName: silenceDraft.silenceName,
        eventType: toNullableString(silenceDraft.eventType),
        severity: toNullableString(silenceDraft.severity),
        entityType: toNullableString(silenceDraft.entityType),
        entityRef: toNullableString(silenceDraft.entityRef),
        startsAt: toNullableString(silenceDraft.startsAt),
        endsAt: toNullableString(silenceDraft.endsAt),
        enabled: silenceDraft.enabled,
        reason: toNullableString(silenceDraft.reason),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['ops-alert-silences'] })
      setSilenceDraft(createDefaultAlertSilenceDraft())
    },
  })

  const releaseMutation = useMutation({
    mutationFn: (id: number) => governanceApi.releaseQuarantine(id, 'manual-release-from-ui'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-quarantines'] }),
  })

  const pageError =
    alertRulesQuery.error
    ?? alertsQuery.error
    ?? autoActionsQuery.error
    ?? healthScoresQuery.error
    ?? silencesQuery.error
    ?? quarantinesQuery.error
    ?? outboundDeliveriesQuery.error
    ?? createAlertRuleMutation.error
    ?? createAutoActionMutation.error
    ?? deleteAutoActionMutation.error
    ?? acknowledgeAlertMutation.error
    ?? createSilenceMutation.error
    ?? releaseMutation.error

  const drainRecords = useMemo(
    () => quarantinesQuery.data?.filter((item) => item.actionType === 'DRAIN') ?? [],
    [quarantinesQuery.data],
  )

  const topMetrics = useMemo(
    () => [
      { label: '开放告警', value: alertsQuery.data?.length ?? 0, hint: '当前待处理告警' },
      { label: '告警规则', value: alertRulesQuery.data?.length ?? 0, hint: '阈值与告警触发规则' },
      { label: '自动动作', value: autoActionsQuery.data?.length ?? 0, hint: '告警到动作的自动闭环' },
      { label: '隔离记录', value: quarantinesQuery.data?.filter((item) => item.status === 'ACTIVE').length ?? 0, hint: '当前生效中的隔离记录' },
    ],
    [alertRulesQuery.data, alertsQuery.data, autoActionsQuery.data, quarantinesQuery.data],
  )
  const healthScoreRows = useMemo(
    () => [
      ...((healthScoresQuery.data?.sites ?? []).map((site) => ({ type: 'site' as const, site }))),
      ...((healthScoresQuery.data?.credentials ?? []).map((credential) => ({ type: 'credential' as const, credential }))),
    ],
    [healthScoresQuery.data],
  )

  return (
    <div className="flex flex-col gap-6">
      {pageError ? <InlineError error={pageError} title="运营视图加载失败" /> : null}

      <PageSection
        kicker="运维"
        title="告警、静默与隔离运营台"
        actions={
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm">
              <Link to="/ops/governance?tab=error-policies">
                打开治理编排
                <ArrowUpRightIcon data-icon="inline-end" />
              </Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link to="/ops">
                返回总览
                <ArrowUpRightIcon data-icon="inline-end" />
              </Link>
            </Button>
          </div>
        }
      >
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {topMetrics.map((metric) => (
            <MetricCard key={metric.label} label={metric.label} value={metric.value} hint={metric.hint} />
          ))}
        </div>
      </PageSection>

      <PageSection kicker="健康评分" title="治理健康分">
        {healthScoresQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : (
          <PaginatedRows items={healthScoreRows}>
            {({ pageItems }) => (
              <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full min-w-[1040px] table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">对象</th>
                  <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">类型</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">提供方</th>
                  <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">评分</th>
                  <th className="w-[13%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">健康状态</th>
                  <th className="w-[13%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">关联 ID</th>
                  <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">说明</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((row) => {
                  if (row.type === 'site') {
                    const site = row.site
                    return (
                      <tr key={`site:${site.siteProfileId}`} className="border-b border-border/40 align-middle">
                        <td className="truncate px-4 py-3 font-medium text-foreground" title={site.displayName}>{site.displayName}</td>
                        <td className="px-4 py-3"><StatusBadge tone="info">站点</StatusBadge></td>
                        <td className="truncate px-4 py-3 text-muted-foreground" title={site.siteKind}>{site.siteKind}</td>
                        <td className="px-4 py-3 text-foreground">{site.score}</td>
                        <td className="px-4 py-3"><StatusBadge tone={healthScoreTone(site.healthState)}>{site.healthState}</StatusBadge></td>
                        <td className="truncate px-4 py-3 text-muted-foreground">站点配置 {site.siteProfileId}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground" title={site.summary ?? site.reason ?? ''}>
                          {site.summary ?? site.reason ?? '无治理阻断'}
                        </td>
                      </tr>
                    )
                  }
                  const item = row.credential
                  return (
                    <tr key={`${item.sourceType ?? 'API_KEY'}:${item.sourceId ?? item.credentialId ?? item.accountId}`} className="border-b border-border/40 align-middle">
                      <td className="truncate px-4 py-3 font-medium text-foreground" title={item.displayName ?? item.credentialName}>
                        {item.displayName ?? item.credentialName}
                      </td>
                      <td className="px-4 py-3"><StatusBadge tone={item.sourceType === 'AUTH_JSON_ACCOUNT' ? 'info' : 'neutral'}>{healthSourceLabel(item.sourceType)}</StatusBadge></td>
                      <td className="truncate px-4 py-3 text-muted-foreground" title={item.providerType}>{item.providerType}</td>
                      <td className="px-4 py-3 text-foreground">{item.score}</td>
                      <td className="px-4 py-3"><StatusBadge tone={healthScoreTone(item.healthState)}>{item.healthState}</StatusBadge></td>
                      <td className="truncate px-4 py-3 text-muted-foreground">{healthSourceRef(item)}</td>
                      <td className="truncate px-4 py-3 text-muted-foreground" title={item.summary ?? item.reason ?? ''}>
                        {item.summary ?? item.reason ?? '可参与路由'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        )}
      </PageSection>

      <PageSection kicker="告警" title="开放告警">
        {alertsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : alertsQuery.data?.length ? (
          <div className="grid gap-4 xl:grid-cols-2">
            {alertsQuery.data.map((item) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex flex-col gap-1">
                      <CardTitle className="text-base">{item.title}</CardTitle>
                      <div className="text-sm text-muted-foreground">{item.entityType ?? 'SYSTEM'} / {item.entityRef ?? '-'}</div>
                    </div>
                    <StatusBadge tone={item.severity === 'HIGH' ? 'danger' : 'warning'}>{item.severity}</StatusBadge>
                  </div>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5">
                  <div className="text-sm leading-6 text-foreground">{item.message}</div>
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" size="sm" variant="outline" onClick={() => acknowledgeAlertMutation.mutate(item.id)}>
                      确认告警
                    </Button>
                    <Button asChild type="button" size="sm">
                      <Link to="/ops/governance?tab=simulation">
                        去模拟命中链
                        <ArrowUpRightIcon data-icon="inline-end" />
                      </Link>
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState
            title="当前没有开放告警"
            icon={<BellRingIcon className="size-5" />}
          />
        )}
      </PageSection>

      <PageSection kicker="告警规则" title="告警规则">
        <form className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]" onSubmit={handleAlertRuleSubmit}>
          <FormField label="ruleName">
            <Input value={ruleName} onChange={(event) => setRuleName(event.target.value)} placeholder="规则名称" />
          </FormField>
          <FormField label="metricKey">
            <Input value={metricKey} onChange={(event) => setMetricKey(event.target.value)} placeholder="指标键" />
          </FormField>
          <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_auto]">
            <FormField label="thresholdValue">
              <Input value={thresholdValue} onChange={(event) => setThresholdValue(event.target.value)} placeholder="阈值" />
            </FormField>
            <div className="flex items-end">
              <Button type="submit" disabled={createAlertRuleMutation.isPending}>创建告警规则</Button>
            </div>
          </div>
        </form>

        {alertRulesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : alertRulesQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {alertRulesQuery.data.map((item) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.ruleName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-2 p-5 text-sm text-muted-foreground">
                  <div className="text-foreground">{item.metricKey} {item.comparisonOperator} {item.thresholdValue}</div>
                  <StatusBadge tone="warning">{item.severity}</StatusBadge>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前还没有告警规则" />
        )}
      </PageSection>

      <PageSection kicker="自动动作" title="自动动作">
        <form className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_16rem_auto]" onSubmit={handleAutoActionSubmit}>
          <FormField label="ruleName">
            <Input value={autoActionDraft.ruleName} onChange={(event) => setAutoActionDraft((current) => ({ ...current, ruleName: event.target.value }))} placeholder="自动动作规则名" />
          </FormField>
          <FormField label="eventType">
            <Input value={autoActionDraft.eventType} onChange={(event) => setAutoActionDraft((current) => ({ ...current, eventType: event.target.value }))} placeholder="事件类型" />
          </FormField>
          <FormField label="actionType">
            <Input value={autoActionDraft.actionType} onChange={(event) => setAutoActionDraft((current) => ({ ...current, actionType: event.target.value }))} placeholder="QUARANTINE / COOLDOWN" />
          </FormField>
          <div className="flex items-end gap-2">
            <Button type="submit" disabled={createAutoActionMutation.isPending}>
              {autoActionDraft.id ? '保存自动动作' : '创建自动动作'}
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={createAutoActionMutation.isPending}
              onClick={() => setAutoActionDraft(createDefaultAutoActionDraft())}
            >
              重置
            </Button>
          </div>
        </form>

        {autoActionsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : autoActionsQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {autoActionsQuery.data.map((rule) => (
              <Card key={rule.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{rule.ruleName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                  <div className="text-foreground">{rule.eventType} / {rule.entityType ?? 'ALL'}</div>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge tone="warning">{rule.actionType}</StatusBadge>
                    <StatusBadge>{rule.recoveryMode}</StatusBadge>
                  </div>
                  <div className="text-foreground">{rule.description ?? '按事件和实体类型自动触发治理动作。'}</div>
                  <div className="flex flex-wrap gap-2 border-t border-border/60 pt-3">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => setAutoActionDraft(autoActionToDraft(rule))}
                    >
                      编辑动作
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={deleteAutoActionMutation.isPending}
                      onClick={() => {
                        if (!window.confirm(`确认删除自动动作“${rule.ruleName}”吗？`)) return
                        deleteAutoActionMutation.mutate(rule.id)
                      }}
                    >
                      删除动作
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有自动动作" />
        )}
      </PageSection>

      <PageSection kicker="静默" title="告警静默">
        <form className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)]" onSubmit={handleSilenceSubmit}>
          <FormField label="silenceName">
            <Input value={silenceDraft.silenceName} onChange={(event) => setSilenceDraft((current) => ({ ...current, silenceName: event.target.value }))} placeholder="静默名称" />
          </FormField>
          <FormField label="eventType">
            <Input value={silenceDraft.eventType} onChange={(event) => setSilenceDraft((current) => ({ ...current, eventType: event.target.value }))} placeholder="事件类型" />
          </FormField>
          <FormField label="entityRef">
            <Input value={silenceDraft.entityRef} onChange={(event) => setSilenceDraft((current) => ({ ...current, entityRef: event.target.value }))} placeholder="对象引用" />
          </FormField>
          <FormField label="reason" className="xl:col-span-2">
            <Textarea rows={4} value={silenceDraft.reason} onChange={(event) => setSilenceDraft((current) => ({ ...current, reason: event.target.value }))} placeholder="静默原因" />
          </FormField>
          <div className="flex items-end">
            <Button type="submit" disabled={createSilenceMutation.isPending}>创建告警静默</Button>
          </div>
        </form>

        {silencesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : silencesQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {silencesQuery.data.map((silence) => (
              <Card key={silence.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{silence.silenceName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                  <div className="text-foreground">{silence.eventType ?? 'ALL'} / {silence.entityType ?? 'ALL'}</div>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge tone={silence.enabled ? 'success' : 'danger'}>{silence.enabled ? '启用' : '停用'}</StatusBadge>
                    {silence.severity ? <StatusBadge>{silence.severity}</StatusBadge> : null}
                  </div>
                  <div className="text-foreground">{silence.reason ?? '无额外原因'}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有告警静默" />
        )}
      </PageSection>

      <PageSection
        kicker="熔断 / Drain"
        title="熔断 / DRAIN 记录"
      >
        {quarantinesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : drainRecords.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {drainRecords.map((item) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.targetType}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                  <div className="text-foreground">{describeTarget(item)}</div>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge tone="danger">{item.actionType}</StatusBadge>
                    <StatusBadge tone={item.status === 'ACTIVE' ? 'warning' : 'success'}>{item.status}</StatusBadge>
                  </div>
                  <div className="text-foreground">{item.reason}</div>
                  <div className="text-xs text-muted-foreground">
                    开始于 {item.startedAt}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有 DRAIN 记录" />
        )}
      </PageSection>

      <PageSection kicker="隔离" title="隔离记录">
        {quarantinesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : quarantinesQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {quarantinesQuery.data.map((item) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.targetType}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5 text-sm text-muted-foreground">
                  <div className="text-foreground">{describeTarget(item)}</div>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge tone={item.status === 'ACTIVE' ? 'danger' : 'success'}>{item.status}</StatusBadge>
                    <StatusBadge tone="warning">{item.actionType}</StatusBadge>
                  </div>
                  <div className="text-foreground">{item.reason}</div>
                  <Button type="button" size="sm" variant="outline" onClick={() => releaseMutation.mutate(item.id)}>
                    解除隔离
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有隔离记录" icon={<ShieldAlertIcon className="size-5" />} />
        )}
      </PageSection>

      <PageSection kicker="外发" title="外发投递状态">
        {outboundDeliveriesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : outboundDeliveriesQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {outboundDeliveriesQuery.data.map((delivery) => (
              <Card key={delivery.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{delivery.eventType}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-2 p-5 text-sm text-muted-foreground">
                  <div className="text-foreground">{delivery.deliveryStatus} / 尝试 {delivery.attemptCount}</div>
                  <div className="text-foreground">{delivery.entityType ?? 'SYSTEM'} / {delivery.entityRef ?? '-'}</div>
                  <div className="text-foreground">{delivery.responseSummary ?? delivery.lastError ?? '等待投递结果'}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有外发投递记录" />
        )}
      </PageSection>
    </div>
  )

  function handleAlertRuleSubmit(event: FormEvent) {
    event.preventDefault()
    createAlertRuleMutation.mutate()
  }

  function handleAutoActionSubmit(event: FormEvent) {
    event.preventDefault()
    createAutoActionMutation.mutate()
  }

  function handleSilenceSubmit(event: FormEvent) {
    event.preventDefault()
    createSilenceMutation.mutate()
  }
}

function FormField({
  label,
  children,
  className,
}: {
  label: string
  children: React.ReactNode
  className?: string
}) {
  return (
    <label className={className ? `flex flex-col gap-2 ${className}` : 'flex flex-col gap-2'}>
      <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{label}</span>
      {children}
    </label>
  )
}

function describeTarget(item: QuarantineRecord) {
  switch (item.targetType) {
    case 'PROVIDER_TYPE':
      return item.providerType ?? '-'
    case 'SITE_PROFILE':
      return `siteProfileId=${item.siteProfileId ?? '-'}`
    case 'CREDENTIAL':
      return `credentialId=${item.credentialId ?? '-'}`
    case 'ACCOUNT':
      return `accountId=${item.accountId ?? '-'}`
    case 'PROXY':
      return `proxyId=${item.proxyId ?? '-'}`
    default:
      return '-'
  }
}

function healthSourceLabel(sourceType?: string | null) {
  if (sourceType === 'AUTH_JSON_ACCOUNT') return '账号凭证'
  if (sourceType === 'API_KEY') return 'Key 凭证'
  return '凭证'
}

function healthSourceRef(item: { sourceType?: string | null; credentialId?: number | null; accountId?: number | null; sourceId?: number | null; siteProfileId?: number | null }) {
  if (item.sourceType === 'AUTH_JSON_ACCOUNT') {
    return `账号 ID ${item.accountId ?? item.sourceId ?? '-'}`
  }
  return `凭证 ID ${item.credentialId ?? item.sourceId ?? '-'}${item.siteProfileId ? ` / 站点 ${item.siteProfileId}` : ''}`
}

function healthScoreTone(state: string): React.ComponentProps<typeof StatusBadge>['tone'] {
  const normalized = state.toUpperCase()
  if (normalized === 'HEALTHY') return 'success'
  if (normalized === 'DEGRADED' || normalized === 'COOLDOWN') return 'warning'
  if (normalized === 'INACTIVE') return 'neutral'
  return 'danger'
}

function toNullableString(value: string | null | undefined) {
  return value == null || value.trim() === '' ? null : value.trim()
}

function toNullableNumber(value: string | null | undefined) {
  if (value == null || value.trim() === '') return null
  const parsed = Number(value)
  return Number.isNaN(parsed) ? null : parsed
}
