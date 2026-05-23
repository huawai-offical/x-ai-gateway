import { startTransition, useEffect, useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import {
  ArrowDownIcon,
  ArrowUpIcon,
  ArrowUpRightIcon,
  EditIcon,
  PowerIcon,
  Trash2Icon,
  FlaskConicalIcon,
  GripVerticalIcon,
  RefreshCcwIcon,
  RouteIcon,
  ShieldAlertIcon,
  ShuffleIcon,
  SparklesIcon,
} from 'lucide-react'
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/app/empty-state'
import { useConfirm } from '@/components/app/confirm-provider'
import { InlineError } from '@/components/app/inline-error'
import { MetricCard } from '@/components/app/metric-card'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { cn } from '@/lib/utils'
import { useTypedQuery } from '@/lib/typed-react-query'
import {
  createDefaultErrorRuleDraft,
  errorRuleToDraft,
  type ErrorRule,
  type ErrorRuleDraft,
} from '@/features/error-rules/types'
import { governanceApi } from './api'
import {
  createDefaultPolicySimulationInput,
  createDefaultRouteGuardDraft,
  routeGuardToDraft,
  type GovernanceTab,
  type PolicyChainNode,
  type PolicySimulationInput,
  type PolicySimulationResult,
  type QuarantineRecord,
  type RouteGuardDraft,
  type RouteGuardPolicy,
  type RoutingPolicyRuntimePlan,
  type RoutingPolicyRuntimeState,
} from './types'

const errorActionOptions = ['REWRITE', 'BLOCK', 'DOWNGRADE', 'PASSTHROUGH']
const matchScopeOptions = ['UPSTREAM', 'GATEWAY']
const targetTypeOptions = ['PROVIDER_TYPE', 'SITE_PROFILE', 'CREDENTIAL', 'ACCOUNT', 'PROXY']
const providerOptions = ['OPENAI_DIRECT', 'OPENAI_COMPATIBLE', 'GEMINI_DIRECT', 'ANTHROPIC_DIRECT', 'OLLAMA_DIRECT']
const policyModeOptions = ['ENFORCE', 'OVERRIDE_ALLOW', 'OVERRIDE_BLOCK']
const governanceActionOptions = ['NONE', 'QUARANTINE', 'COOLDOWN', 'DRAIN']

export function GovernancePage() {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab = normalizeTab(searchParams.get('tab'))
  const [errorRuleDraft, setErrorRuleDraft] = useState<ErrorRuleDraft>(createDefaultErrorRuleDraft())
  const [routeGuardDraft, setRouteGuardDraft] = useState<RouteGuardDraft>(createDefaultRouteGuardDraft())
  const [simulationInput, setSimulationInput] = useState<PolicySimulationInput>(createDefaultPolicySimulationInput())
  const [errorRuleOrder, setErrorRuleOrder] = useState<number[]>([])
  const [routeGuardOrder, setRouteGuardOrder] = useState<number[]>([])
  const [draggingErrorRuleId, setDraggingErrorRuleId] = useState<number | null>(null)
  const [draggingRouteGuardId, setDraggingRouteGuardId] = useState<number | null>(null)

  const errorRulesQuery = useTypedQuery<ErrorRule[]>({
    queryKey: ['governance-error-rules'],
    queryFn: governanceApi.listErrorRules,
  })
  const routeGuardsQuery = useTypedQuery<RouteGuardPolicy[]>({
    queryKey: ['governance-route-guards'],
    queryFn: governanceApi.listRouteGuards,
  })
  const routingRuntimePlanQuery = useTypedQuery<RoutingPolicyRuntimePlan>({
    queryKey: ['governance-routing-runtime-plan'],
    queryFn: governanceApi.routingRuntimePlan,
  })
  const routingRuntimeStatesQuery = useTypedQuery<RoutingPolicyRuntimeState[]>({
    queryKey: ['governance-routing-runtime-states'],
    queryFn: governanceApi.routingRuntimeStates,
  })
  const quarantinesQuery = useTypedQuery<QuarantineRecord[]>({
    queryKey: ['governance-quarantines'],
    queryFn: () => governanceApi.listQuarantines('ACTIVE'),
  })

  useEffect(() => {
    if (!errorRulesQuery.data) return
    setErrorRuleOrder(errorRulesQuery.data.map((rule) => rule.id))
  }, [errorRulesQuery.data])

  useEffect(() => {
    if (!routeGuardsQuery.data) return
    setRouteGuardOrder(routeGuardsQuery.data.map((rule) => rule.id))
  }, [routeGuardsQuery.data])

  const orderedErrorRules = useMemo(
    () => orderByLocalPriority(errorRulesQuery.data ?? [], errorRuleOrder),
    [errorRuleOrder, errorRulesQuery.data],
  )
  const orderedRouteGuards = useMemo(
    () => orderByLocalPriority(routeGuardsQuery.data ?? [], routeGuardOrder),
    [routeGuardOrder, routeGuardsQuery.data],
  )

  const errorPolicyMetrics = useMemo(() => {
    const rules = errorRulesQuery.data ?? []
    return {
      total: rules.length,
      rewrite: rules.filter((rule) => rule.action === 'REWRITE').length,
      downgrade: rules.filter((rule) => rule.action === 'DOWNGRADE').length,
      blocked: rules.filter((rule) => rule.action === 'BLOCK').length,
    }
  }, [errorRulesQuery.data])

  const routeGuardMetrics = useMemo(() => {
    const rules = routeGuardsQuery.data ?? []
    return {
      total: rules.length,
      enforce: rules.filter((rule) => rule.policyMode === 'ENFORCE').length,
      overrideAllow: rules.filter((rule) => rule.policyMode === 'OVERRIDE_ALLOW').length,
      overrideBlock: rules.filter((rule) => rule.policyMode === 'OVERRIDE_BLOCK').length,
      retry: rules.filter((rule) => hasPolicyJson(rule.retryPolicy)).length,
      fallback: rules.filter((rule) => hasPolicyJson(rule.fallbackPolicy)).length,
      circuit: rules.filter((rule) => hasPolicyJson(rule.circuitBreakerPolicy)).length,
      rateLimit: rules.filter((rule) => hasPolicyJson(rule.rateLimitPolicy)).length,
    }
  }, [routeGuardsQuery.data])

  const isErrorRuleOrderDirty = useMemo(
    () => hasOrderChanged(errorRulesQuery.data ?? [], errorRuleOrder),
    [errorRuleOrder, errorRulesQuery.data],
  )
  const isRouteGuardOrderDirty = useMemo(
    () => hasOrderChanged(routeGuardsQuery.data ?? [], routeGuardOrder),
    [routeGuardOrder, routeGuardsQuery.data],
  )

  const saveErrorRuleMutation = useMutation({
    mutationFn: governanceApi.saveErrorRule,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['governance-error-rules'] })
      setErrorRuleDraft(createDefaultErrorRuleDraft())
    },
  })

  const saveRouteGuardMutation = useMutation({
    mutationFn: governanceApi.saveRouteGuard,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['governance-route-guards'] })
      setRouteGuardDraft(createDefaultRouteGuardDraft())
    },
  })

  const deleteErrorRuleMutation = useMutation({
    mutationFn: governanceApi.deleteErrorRule,
    onSuccess: async (_: void, id: number) => {
      await queryClient.invalidateQueries({ queryKey: ['governance-error-rules'] })
      setErrorRuleOrder((current) => current.filter((item) => item !== id))
      setErrorRuleDraft((current) => (current.id === id ? createDefaultErrorRuleDraft() : current))
    },
  })

  const deleteRouteGuardMutation = useMutation({
    mutationFn: governanceApi.deleteRouteGuard,
    onSuccess: async (_: void, id: number) => {
      await queryClient.invalidateQueries({ queryKey: ['governance-route-guards'] })
      setRouteGuardOrder((current) => current.filter((item) => item !== id))
      setRouteGuardDraft((current) => (current.id === id ? createDefaultRouteGuardDraft() : current))
    },
  })

  const persistErrorOrderMutation = useMutation({
    mutationFn: async (rules: ErrorRule[]) => {
      for (const [index, rule] of rules.entries()) {
        await governanceApi.saveErrorRule({
          id: rule.id,
          ...toErrorRulePayload(rule),
          priority: (index + 1) * 10,
        })
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['governance-error-rules'] }),
  })

  const persistRouteGuardOrderMutation = useMutation({
    mutationFn: async (rules: RouteGuardPolicy[]) => {
      for (const [index, rule] of rules.entries()) {
        await governanceApi.saveRouteGuard({
          id: rule.id,
          ...toRouteGuardPayload(rule),
          priority: (index + 1) * 10,
        })
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['governance-route-guards'] }),
  })

  const resetRoutingRuntimeStatesMutation = useMutation({
    mutationFn: governanceApi.resetRoutingRuntimeStates,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['governance-routing-runtime-states'] }),
  })

  const simulationMutation = useMutation({
    mutationFn: async (input: PolicySimulationInput) => {
      const preview = await governanceApi.previewErrorRules({
        providerType: toNullableString(input.providerType),
        protocol: toNullableString(input.protocol),
        model: toNullableString(input.model),
        requestPath: toNullableString(input.requestPath),
        httpStatus: toNullableNumber(input.httpStatus),
        errorCode: toNullableString(input.errorCode),
        matchScope: toNullableString(input.matchScope),
        message: toNullableString(input.message),
      })

      return buildPolicySimulation({
        input,
        errorRules: preview.matchedRules,
        routeGuards: routeGuardsQuery.data ?? [],
        quarantines: quarantinesQuery.data ?? [],
      })
    },
  })

  const handleDeleteErrorRule = async (rule: ErrorRule) => {
    const confirmed = await confirm({
      title: '删除错误规则',
      description: `确认删除错误规则 #${rule.id} 吗？该操作会立即移除这条规则。`,
      confirmLabel: '删除',
      destructive: true,
    })
    if (confirmed) {
      deleteErrorRuleMutation.mutate(rule.id)
    }
  }

  const handleDeleteRouteGuard = async (policy: RouteGuardPolicy) => {
    const confirmed = await confirm({
      title: '删除路由守卫',
      description: `确认删除“${policy.policyName}”吗？该操作会立即移除这条路由守卫策略。`,
      confirmLabel: '删除',
      destructive: true,
    })
    if (confirmed) {
      deleteRouteGuardMutation.mutate(policy.id)
    }
  }

  const pageError =
    errorRulesQuery.error
    ?? routeGuardsQuery.error
    ?? routingRuntimePlanQuery.error
    ?? routingRuntimeStatesQuery.error
    ?? quarantinesQuery.error
    ?? saveErrorRuleMutation.error
    ?? saveRouteGuardMutation.error
    ?? deleteErrorRuleMutation.error
    ?? deleteRouteGuardMutation.error
    ?? persistErrorOrderMutation.error
    ?? persistRouteGuardOrderMutation.error
    ?? resetRoutingRuntimeStatesMutation.error
    ?? simulationMutation.error

  return (
    <div className="flex flex-col gap-6">
      {pageError ? <InlineError error={pageError} title="治理编排台加载失败" /> : null}

      <PageSection
        kicker="治理编排"
        title="错误、路由与降级编排"
        actions={
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm">
              <Link to="/ops/alerts">
                返回运营视图
                <ArrowUpRightIcon data-icon="inline-end" />
              </Link>
            </Button>
            <StatusBadge tone="info">错误规则 {errorRulesQuery.data?.length ?? 0}</StatusBadge>
            <StatusBadge tone="warning">路由守卫 {routeGuardsQuery.data?.length ?? 0}</StatusBadge>
            <StatusBadge tone="danger">生效隔离 {quarantinesQuery.data?.length ?? 0}</StatusBadge>
          </div>
        }
      >
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard label="错误策略" value={errorPolicyMetrics.total} hint={`${errorPolicyMetrics.rewrite} rewrite / ${errorPolicyMetrics.downgrade} downgrade`} />
          <MetricCard label="路由守卫" value={routeGuardMetrics.total} hint={`${routeGuardMetrics.retry} 重试 / ${routeGuardMetrics.fallback} 回退`} />
          <MetricCard label="运行时尝试次数" value={routingRuntimePlanQuery.data?.maxAttempts ?? '-'} hint={routingRuntimePlanQuery.data?.fallbackEnabled ? `fallback ${routingRuntimePlanQuery.data.fallbackOrder.length || 'on'}` : '未启用 fallback'} />
          <MetricCard label="受守卫对象" value={quarantinesQuery.data?.length ?? 0} hint="当前生效中的隔离数量" />
        </div>

        <Tabs value={activeTab} onValueChange={handleTabChange} className="gap-4">
          <TabsList variant="line" className="w-full justify-start overflow-x-auto">
            <TabsTrigger value="error-policies">错误策略</TabsTrigger>
            <TabsTrigger value="route-guards">路由守卫</TabsTrigger>
            <TabsTrigger value="simulation">模拟预览</TabsTrigger>
          </TabsList>

          <TabsContent value="error-policies" className="mt-0">
            {errorRulesQuery.isPending ? (
              <PageSkeleton count={2} />
            ) : (
              <div className="grid gap-4 xl:grid-cols-[minmax(0,1.25fr)_24rem]">
                <Card className="border-border/60 bg-card/96 shadow-sm">
                  <CardHeader className="gap-3 border-b border-border/60">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="flex flex-col gap-1">
                        <CardTitle className="text-base">错误策略链</CardTitle>
                      </div>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={!isErrorRuleOrderDirty || persistErrorOrderMutation.isPending}
                        onClick={() => persistErrorOrderMutation.mutate(orderedErrorRules)}
                      >
                        <ShuffleIcon data-icon="inline-start" />
                        保存排序
                      </Button>
                    </div>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-3 p-5">
                    {orderedErrorRules.length ? (
                      orderedErrorRules.map((rule, index) => (
                        <PolicyCard
                          key={rule.id}
                          draggable
                          title={rule.requestPath ?? '未限定请求路径'}
                          badges={[
                            <PolicyActionBadge key="action" action={rule.action} />,
                            <StatusBadge key="scope">{rule.matchScope ?? 'UPSTREAM'}</StatusBadge>,
                            <StatusBadge key="enabled" tone={rule.enabled ? 'success' : 'danger'}>
                              {rule.enabled ? '启用' : '停用'}
                            </StatusBadge>,
                          ]}
                          dragHandle={<GripVerticalIcon className="size-4 text-muted-foreground" />}
                          onDragStart={() => setDraggingErrorRuleId(rule.id)}
                          onDragOver={(event) => event.preventDefault()}
                          onDrop={() => {
                            if (draggingErrorRuleId == null || draggingErrorRuleId === rule.id) return
                            setErrorRuleOrder((current) => reorderIds(current, draggingErrorRuleId, rule.id))
                            setDraggingErrorRuleId(null)
                          }}
                          footer={(
                            <div className="flex flex-wrap items-center gap-1.5">
                              <Button type="button" size="icon" variant="outline" aria-label={`编辑规则 ${rule.id}`} onClick={() => setErrorRuleDraft(errorRuleToDraft(rule))}>
                                <EditIcon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="outline"
                                aria-label={rule.enabled ? `停用规则 ${rule.id}` : `启用规则 ${rule.id}`}
                                onClick={() => saveErrorRuleMutation.mutate({ id: rule.id, ...toErrorRulePayload(rule), enabled: !rule.enabled })}
                              >
                                <PowerIcon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="outline"
                                aria-label={`删除规则 ${rule.id}`}
                                disabled={deleteErrorRuleMutation.isPending}
                                onClick={() => void handleDeleteErrorRule(rule)}
                              >
                                <Trash2Icon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="ghost"
                                aria-label={`上移规则 ${rule.id}`}
                                disabled={index === 0}
                                onClick={() => setErrorRuleOrder((current) => moveId(current, rule.id, 'up'))}
                              >
                                <ArrowUpIcon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="ghost"
                                aria-label={`下移规则 ${rule.id}`}
                                disabled={index === orderedErrorRules.length - 1}
                                onClick={() => setErrorRuleOrder((current) => moveId(current, rule.id, 'down'))}
                              >
                                <ArrowDownIcon />
                              </Button>
                            </div>
                          )}
                        />
                      ))
                    ) : (
                      <EmptyState
                        title="当前还没有错误策略"
                        icon={<ShieldAlertIcon className="size-5" />}
                      />
                    )}
                  </CardContent>
                </Card>

                <PolicyFormCard
                title={errorRuleDraft.id ? `编辑规则 #${errorRuleDraft.id}` : '新建错误规则'}
                  footer={(
                    <div className="flex gap-2">
                      <Button type="submit" form="error-rule-form" disabled={saveErrorRuleMutation.isPending}>
                        {errorRuleDraft.id ? '保存规则' : '创建规则'}
                      </Button>
                      <Button type="button" variant="secondary" onClick={() => setErrorRuleDraft(createDefaultErrorRuleDraft())}>
                        重置
                      </Button>
                    </div>
                  )}
                >
                  <form
                    id="error-rule-form"
                    className="grid gap-4"
                    onSubmit={(event) => {
                      event.preventDefault()
                      saveErrorRuleMutation.mutate({
                        id: errorRuleDraft.id,
                        ...toErrorRulePayload(errorRuleDraft),
                      })
                    }}
                  >
                    <div className="grid gap-4 md:grid-cols-2">
                      <FormField label="协议">
                        <Input value={errorRuleDraft.protocol} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, protocol: event.target.value }))} />
                      </FormField>
                      <FormField label="请求路径">
                        <Input value={errorRuleDraft.requestPath} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, requestPath: event.target.value }))} />
                      </FormField>
                      <FormField label="错误码">
                        <Input value={errorRuleDraft.errorCode} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, errorCode: event.target.value }))} />
                      </FormField>
                      <FormField label="优先级">
                        <Input value={errorRuleDraft.priority} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, priority: event.target.value }))} />
                      </FormField>
                      <FormField label="动作">
                        <SimpleSelect value={errorRuleDraft.action} onChange={(value) => setErrorRuleDraft((current) => ({ ...current, action: value }))} options={errorActionOptions} />
                      </FormField>
                      <FormField label="匹配范围">
                        <SimpleSelect value={errorRuleDraft.matchScope} onChange={(value) => setErrorRuleDraft((current) => ({ ...current, matchScope: value }))} options={matchScopeOptions} />
                      </FormField>
                      <FormField label="HTTP 状态码">
                        <Input value={errorRuleDraft.httpStatus} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, httpStatus: event.target.value }))} />
                      </FormField>
                      <FormField label="提供方类型">
                        <Input value={errorRuleDraft.providerType} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, providerType: event.target.value }))} placeholder="可选" />
                      </FormField>
                    </div>
                    <FormField label="重写错误码">
                      <Input value={errorRuleDraft.rewriteCode} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, rewriteCode: event.target.value }))} />
                    </FormField>
                    <FormField label="重写提示文案">
                      <Textarea rows={4} value={errorRuleDraft.rewriteMessage} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, rewriteMessage: event.target.value }))} />
                    </FormField>
                    <FormField label="降级策略">
                      <Input value={errorRuleDraft.downgradePolicy} onChange={(event) => setErrorRuleDraft((current) => ({ ...current, downgradePolicy: event.target.value }))} placeholder="例如 fallback:gpt-4o-mini" />
                    </FormField>
                  </form>
                </PolicyFormCard>
              </div>
            )}
          </TabsContent>

          <TabsContent value="route-guards" className="mt-0">
            {routeGuardsQuery.isPending ? (
              <PageSkeleton count={2} />
            ) : (
              <div className="flex flex-col gap-4">
                <RoutingRuntimeStatesPanel
                  states={routingRuntimeStatesQuery.data ?? []}
                  pending={routingRuntimeStatesQuery.isPending}
                  resetting={resetRoutingRuntimeStatesMutation.isPending}
                  onReset={() => resetRoutingRuntimeStatesMutation.mutate()}
                />
                <div className="grid gap-4 xl:grid-cols-[minmax(0,1.25fr)_24rem]">
                  <Card className="border-border/60 bg-card/96 shadow-sm">
                  <CardHeader className="gap-3 border-b border-border/60">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="flex flex-col gap-1">
                        <CardTitle className="text-base">路由守卫链</CardTitle>
                      </div>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={!isRouteGuardOrderDirty || persistRouteGuardOrderMutation.isPending}
                        onClick={() => persistRouteGuardOrderMutation.mutate(orderedRouteGuards)}
                      >
                        <ShuffleIcon data-icon="inline-start" />
                        保存排序
                      </Button>
                    </div>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-3 p-5">
                    {orderedRouteGuards.length ? (
                      orderedRouteGuards.map((policy, index) => (
                        <PolicyCard
                          key={policy.id}
                          draggable
                          title={policy.policyName}
                          badges={[
                            <PolicyActionBadge key="action" action={policy.actionType} />,
                            <StatusBadge key="mode" tone={policy.policyMode === 'OVERRIDE_ALLOW' ? 'success' : policy.policyMode === 'OVERRIDE_BLOCK' ? 'danger' : 'warning'}>
                              {policy.policyMode}
                            </StatusBadge>,
                            <StatusBadge key="enabled" tone={policy.enabled ? 'success' : 'danger'}>
                              {policy.enabled ? '启用' : '停用'}
                            </StatusBadge>,
                            ...(hasPolicyJson(policy.retryPolicy) ? [<StatusBadge key="retry" tone="info">重试</StatusBadge>] : []),
                            ...(hasPolicyJson(policy.fallbackPolicy) ? [<StatusBadge key="fallback" tone="info">回退</StatusBadge>] : []),
                            ...(hasPolicyJson(policy.circuitBreakerPolicy) ? [<StatusBadge key="circuit" tone="warning">熔断</StatusBadge>] : []),
                            ...(hasPolicyJson(policy.rateLimitPolicy) ? [<StatusBadge key="rate" tone="warning">限流</StatusBadge>] : []),
                          ]}
                          dragHandle={<GripVerticalIcon className="size-4 text-muted-foreground" />}
                          onDragStart={() => setDraggingRouteGuardId(policy.id)}
                          onDragOver={(event) => event.preventDefault()}
                          onDrop={() => {
                            if (draggingRouteGuardId == null || draggingRouteGuardId === policy.id) return
                            setRouteGuardOrder((current) => reorderIds(current, draggingRouteGuardId, policy.id))
                            setDraggingRouteGuardId(null)
                          }}
                          footer={(
                            <div className="flex flex-wrap items-center gap-1.5">
                              <Button type="button" size="icon" variant="outline" aria-label={`编辑守卫 ${policy.id}`} onClick={() => setRouteGuardDraft(routeGuardToDraft(policy))}>
                                <EditIcon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="outline"
                                aria-label={policy.enabled ? `停用守卫 ${policy.id}` : `启用守卫 ${policy.id}`}
                                onClick={() => saveRouteGuardMutation.mutate({ id: policy.id, ...toRouteGuardPayload(policy), enabled: !policy.enabled })}
                              >
                                <PowerIcon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="outline"
                                aria-label={`删除守卫 ${policy.id}`}
                                disabled={deleteRouteGuardMutation.isPending}
                                onClick={() => void handleDeleteRouteGuard(policy)}
                              >
                                <Trash2Icon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="ghost"
                                aria-label={`上移守卫 ${policy.id}`}
                                disabled={index === 0}
                                onClick={() => setRouteGuardOrder((current) => moveId(current, policy.id, 'up'))}
                              >
                                <ArrowUpIcon />
                              </Button>
                              <Button
                                type="button"
                                size="icon"
                                variant="ghost"
                                aria-label={`下移守卫 ${policy.id}`}
                                disabled={index === orderedRouteGuards.length - 1}
                                onClick={() => setRouteGuardOrder((current) => moveId(current, policy.id, 'down'))}
                              >
                                <ArrowDownIcon />
                              </Button>
                            </div>
                          )}
                        />
                      ))
                    ) : (
                      <EmptyState
                        title="当前还没有路由守卫"
                        icon={<RouteIcon className="size-5" />}
                      />
                    )}
                  </CardContent>
                </Card>

                <PolicyFormCard
                  title={routeGuardDraft.id ? `编辑守卫 #${routeGuardDraft.id}` : '新建路由守卫'}
                  footer={(
                    <div className="flex gap-2">
                      <Button type="submit" form="route-guard-form" disabled={saveRouteGuardMutation.isPending}>
                        {routeGuardDraft.id ? '保存守卫' : '创建守卫'}
                      </Button>
                      <Button type="button" variant="secondary" onClick={() => setRouteGuardDraft(createDefaultRouteGuardDraft())}>
                        重置
                      </Button>
                    </div>
                  )}
                >
                  <form
                    id="route-guard-form"
                    className="grid gap-4"
                    onSubmit={(event) => {
                      event.preventDefault()
                      saveRouteGuardMutation.mutate({
                        id: routeGuardDraft.id,
                        ...toRouteGuardPayload(routeGuardDraft),
                      })
                    }}
                  >
                    <div className="grid gap-4 md:grid-cols-2">
                      <FormField label="策略名称">
                        <Input value={routeGuardDraft.policyName} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, policyName: event.target.value }))} placeholder="guard-openai-primary" />
                      </FormField>
                      <FormField label="目标类型">
                        <SimpleSelect value={routeGuardDraft.targetType} onChange={(value) => setRouteGuardDraft((current) => ({ ...current, targetType: value }))} options={targetTypeOptions} />
                      </FormField>
                      <FormField label="策略模式">
                        <SimpleSelect value={routeGuardDraft.policyMode} onChange={(value) => setRouteGuardDraft((current) => ({ ...current, policyMode: value }))} options={policyModeOptions} />
                      </FormField>
                      <FormField label="动作类型">
                        <SimpleSelect value={routeGuardDraft.actionType} onChange={(value) => setRouteGuardDraft((current) => ({ ...current, actionType: value }))} options={governanceActionOptions} />
                      </FormField>
                      <FormField label="优先级">
                        <Input value={routeGuardDraft.priority} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, priority: event.target.value }))} />
                      </FormField>
                      <FormField label="TTL 秒数">
                        <Input value={routeGuardDraft.ttlSeconds} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, ttlSeconds: event.target.value }))} />
                      </FormField>
                      <FormField label="提供方类型">
                        <SimpleSelect value={routeGuardDraft.providerType} onChange={(value) => setRouteGuardDraft((current) => ({ ...current, providerType: value }))} options={providerOptions} />
                      </FormField>
                      <FormField label="站点配置 ID">
                        <Input value={routeGuardDraft.siteProfileId} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, siteProfileId: event.target.value }))} placeholder="仅 SITE_PROFILE" />
                      </FormField>
                      <FormField label="凭证 ID">
                        <Input value={routeGuardDraft.credentialId} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, credentialId: event.target.value }))} placeholder="仅 CREDENTIAL" />
                      </FormField>
                      <FormField label="账号 ID">
                        <Input value={routeGuardDraft.accountId} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, accountId: event.target.value }))} placeholder="仅 ACCOUNT" />
                      </FormField>
                      <FormField label="代理 ID">
                        <Input value={routeGuardDraft.proxyId} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, proxyId: event.target.value }))} placeholder="仅 PROXY" />
                      </FormField>
                    </div>
                    <FormField label="说明">
                      <Textarea rows={4} value={routeGuardDraft.description} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, description: event.target.value }))} />
                    </FormField>
                    <FormField label="重试策略 JSON">
                      <Textarea rows={3} value={routeGuardDraft.retryPolicy} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, retryPolicy: event.target.value }))} placeholder='{"maxAttempts":2}' />
                    </FormField>
                    <FormField label="回退策略 JSON">
                      <Textarea rows={3} value={routeGuardDraft.fallbackPolicy} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, fallbackPolicy: event.target.value }))} placeholder='{"enabled":true,"order":["score","priority"]}' />
                    </FormField>
                    <FormField label="熔断策略 JSON">
                      <Textarea rows={3} value={routeGuardDraft.circuitBreakerPolicy} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, circuitBreakerPolicy: event.target.value }))} placeholder='{"enabled":true,"failureThreshold":3}' />
                    </FormField>
                    <FormField label="限流策略 JSON">
                      <Textarea rows={3} value={routeGuardDraft.rateLimitPolicy} onChange={(event) => setRouteGuardDraft((current) => ({ ...current, rateLimitPolicy: event.target.value }))} placeholder='{"enabled":true,"rpm":60}' />
                    </FormField>
                  </form>
                  </PolicyFormCard>
                </div>
              </div>
            )}
          </TabsContent>

          <TabsContent value="simulation" className="mt-0">
            <div className="grid gap-4 xl:grid-cols-[24rem_minmax(0,1fr)]">
              <PolicyFormCard
                title="模拟输入"
                footer={(
                  <Button type="submit" form="simulation-form" disabled={simulationMutation.isPending}>
                    <FlaskConicalIcon data-icon="inline-start" />
                    运行模拟
                  </Button>
                )}
              >
                <form
                  id="simulation-form"
                  className="grid gap-4"
                  onSubmit={(event) => {
                    event.preventDefault()
                    simulationMutation.mutate(simulationInput)
                  }}
                >
                  <FormField label="提供方类型">
                    <SimpleSelect value={simulationInput.providerType} onChange={(value) => setSimulationInput((current) => ({ ...current, providerType: value }))} options={providerOptions} />
                  </FormField>
                  <FormField label="协议">
                    <Input value={simulationInput.protocol} onChange={(event) => setSimulationInput((current) => ({ ...current, protocol: event.target.value }))} />
                  </FormField>
                  <FormField label="模型">
                    <Input value={simulationInput.model} onChange={(event) => setSimulationInput((current) => ({ ...current, model: event.target.value }))} />
                  </FormField>
                  <FormField label="请求路径">
                    <Input value={simulationInput.requestPath} onChange={(event) => setSimulationInput((current) => ({ ...current, requestPath: event.target.value }))} />
                  </FormField>
                  <div className="grid gap-4 md:grid-cols-2">
                    <FormField label="HTTP 状态码">
                      <Input value={simulationInput.httpStatus} onChange={(event) => setSimulationInput((current) => ({ ...current, httpStatus: event.target.value }))} />
                    </FormField>
                    <FormField label="错误码">
                      <Input value={simulationInput.errorCode} onChange={(event) => setSimulationInput((current) => ({ ...current, errorCode: event.target.value }))} />
                    </FormField>
                    <FormField label="站点配置 ID">
                      <Input value={simulationInput.siteProfileId} onChange={(event) => setSimulationInput((current) => ({ ...current, siteProfileId: event.target.value }))} />
                    </FormField>
                    <FormField label="凭证 ID">
                      <Input value={simulationInput.credentialId} onChange={(event) => setSimulationInput((current) => ({ ...current, credentialId: event.target.value }))} />
                    </FormField>
                    <FormField label="账号 ID">
                      <Input value={simulationInput.accountId} onChange={(event) => setSimulationInput((current) => ({ ...current, accountId: event.target.value }))} />
                    </FormField>
                    <FormField label="代理 ID">
                      <Input value={simulationInput.proxyId} onChange={(event) => setSimulationInput((current) => ({ ...current, proxyId: event.target.value }))} />
                    </FormField>
                  </div>
                  <FormField label="错误信息">
                    <Textarea rows={4} value={simulationInput.message} onChange={(event) => setSimulationInput((current) => ({ ...current, message: event.target.value }))} />
                  </FormField>
                </form>
              </PolicyFormCard>

              {simulationMutation.isPending ? (
                <PageSkeleton count={2} />
              ) : simulationMutation.data ? (
                <GovernanceChainPreview result={simulationMutation.data} />
              ) : (
                <Card className="border-border/60 bg-card/96 shadow-sm">
                  <CardHeader className="gap-2 border-b border-border/60">
                    <CardTitle className="text-base">命中链预览</CardTitle>
                  </CardHeader>
                  <CardContent className="p-5">
                    <EmptyState
                      title="还没有运行模拟"
                      icon={<SparklesIcon className="size-5" />}
                    />
                  </CardContent>
                </Card>
              )}
            </div>
          </TabsContent>
        </Tabs>
      </PageSection>
    </div>
  )

  function handleTabChange(nextValue: string) {
    const nextTab = normalizeTab(nextValue)
    startTransition(() => {
      const nextParams = new URLSearchParams(searchParams)
      nextParams.set('tab', nextTab)
      setSearchParams(nextParams, { replace: true })
    })
  }
}

function GovernanceChainPreview({ result }: { result: PolicySimulationResult }) {
  return (
    <div className="grid gap-4">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="最终动作" value={result.finalAction} hint={result.finalReason} />
        <MetricCard label="错误策略命中" value={result.matchedErrorRules} hint="错误策略命中数" />
        <MetricCard label="Guard 命中" value={result.matchedRouteGuards} hint="Route Guard 命中数" />
        <MetricCard label="隔离命中" value={result.matchedQuarantines} hint="生效隔离命中数" />
      </div>

      <Card className="border-border/60 bg-card/96 shadow-sm">
        <CardHeader className="gap-2 border-b border-border/60">
          <CardTitle className="text-base">治理链路</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3 p-5">
          {result.nodes.map((node) => (
            <div
              key={node.id}
              className={cn(
                'rounded-2xl border px-4 py-4',
                node.tone !== 'neutral' && 'border-border/60 bg-muted/20 text-foreground',
                node.tone === 'neutral' && 'border-border/60 bg-background/90 text-foreground',
              )}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex min-w-0 flex-col gap-1">
                  <div className="text-xs font-medium uppercase tracking-[0.16em] text-current/70">{nodeKindLabel(node.kind)}</div>
                  <div className="font-medium">{node.title}</div>
                </div>
                <StatusBadge tone={node.tone === 'neutral' ? 'neutral' : node.tone}>
                  {node.matched ? '已命中' : '已跳过'}
                </StatusBadge>
              </div>
              <div className="mt-3 text-sm leading-6">{node.summary}</div>
              <div className="mt-2 text-sm leading-6 text-current/80">{node.detail}</div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}

function PolicyFormCard({
  title,
  children,
  footer,
}: {
  title: string
  children: React.ReactNode
  footer?: React.ReactNode
}) {
  return (
    <Card className="border-border/60 bg-card/96 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 p-5">
        {children}
        {footer ? <div className="flex flex-wrap gap-2">{footer}</div> : null}
      </CardContent>
    </Card>
  )
}

function RoutingRuntimeStatesPanel({
  states,
  pending,
  resetting,
  onReset,
}: {
  states: RoutingPolicyRuntimeState[]
  pending: boolean
  resetting: boolean
  onReset: () => void
}) {
  const openCount = states.filter((state) => state.state === 'OPEN').length
  const activeRateWindows = states.filter((state) => state.currentWindowCount > 0).length

  return (
    <Card className="border-border/60 bg-card/96 shadow-sm">
      <CardHeader className="gap-3 border-b border-border/60">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex flex-col gap-1">
            <CardTitle className="text-base">路由运行时状态</CardTitle>
            <div className="flex flex-wrap gap-2">
              <StatusBadge tone={openCount > 0 ? 'danger' : 'success'}>{openCount} 个打开窗口</StatusBadge>
              <StatusBadge tone={activeRateWindows > 0 ? 'warning' : 'neutral'}>{activeRateWindows} 个限流窗口</StatusBadge>
            </div>
          </div>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={resetting || !states.length}
            onClick={onReset}
          >
            <RefreshCcwIcon data-icon="inline-start" />
            重置状态
          </Button>
        </div>
      </CardHeader>
      <CardContent className="p-5">
        {pending ? (
          <PageSkeleton count={1} />
        ) : states.length ? (
          <div className="grid gap-3 lg:grid-cols-2">
            {states.map((state) => (
              <div key={state.runtimeKey} className="rounded-2xl border border-border/60 bg-background/90 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="break-all font-medium text-foreground">{state.targetRef}</div>
                    <div className="mt-1 break-all text-xs text-muted-foreground">{state.runtimeKey}</div>
                  </div>
                  <StatusBadge tone={toneForRuntimeState(state.state)}>{state.state}</StatusBadge>
                </div>
                <div className="mt-3 grid gap-2 text-sm text-muted-foreground sm:grid-cols-2">
                  <span>策略 #{state.policyId ?? '-'}</span>
                  <span>失败次数 {state.failureCount}</span>
                  <span>窗口计数 {state.currentWindowCount}</span>
                  <span>{state.openUntil ? `打开截止 ${state.openUntil}` : '打开截止 -'}</span>
                </div>
                {state.reason ? (
                  <div className="mt-2 break-all text-sm text-muted-foreground">{state.reason}</div>
                ) : null}
              </div>
            ))}
          </div>
        ) : (
          <EmptyState
            title="当前没有路由运行时状态"
            icon={<RouteIcon className="size-5" />}
          />
        )}
      </CardContent>
    </Card>
  )
}

function PolicyCard({
  title,
  badges,
  footer,
  dragHandle,
  ...props
}: React.ComponentProps<'div'> & {
  title: string
  badges: React.ReactNode[]
  footer: React.ReactNode
  dragHandle?: React.ReactNode
}) {
  return (
    <div
      className="rounded-3xl border border-border/60 bg-background/90 px-4 py-4 shadow-sm transition-shadow hover:shadow-md"
      {...props}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          {dragHandle ? (
            <div className="mt-0.5 flex size-8 items-center justify-center rounded-2xl bg-muted/60">
              {dragHandle}
            </div>
          ) : null}
          <div className="flex min-w-0 flex-col gap-2">
            <div className="font-medium text-foreground">{title}</div>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">{badges}</div>
      </div>
      <div className="mt-4">{footer}</div>
    </div>
  )
}

function PolicyActionBadge({ action }: { action: string }) {
  const normalized = action.toUpperCase()
  const tone =
    normalized === 'REWRITE' || normalized === 'PASSTHROUGH'
      ? 'info'
      : normalized === 'DOWNGRADE' || normalized === 'COOLDOWN'
        ? 'warning'
        : normalized === 'NONE'
          ? 'neutral'
          : 'danger'

  return <StatusBadge tone={tone}>{normalized}</StatusBadge>
}

function nodeKindLabel(kind: string) {
  switch (kind) {
    case 'ERROR_RULE':
      return '错误规则'
    case 'ROUTE_GUARD':
      return '路由守卫'
    case 'QUARANTINE':
      return '隔离'
    case 'DECISION':
      return '决策'
    default:
      return kind
  }
}

function toneForRuntimeState(state: string): React.ComponentProps<typeof StatusBadge>['tone'] {
  const normalized = state.toUpperCase()
  if (normalized === 'OPEN') return 'danger'
  if (normalized === 'CLOSED') return 'success'
  if (normalized === 'HALF_OPEN' || normalized === 'RATE_WINDOW') return 'warning'
  return 'neutral'
}

function FormField({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <label className="flex flex-col gap-2">
      <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{label}</span>
      {children}
    </label>
  )
}

function SimpleSelect({
  value,
  onChange,
  options,
}: {
  value: string
  onChange: (nextValue: string) => void
  options: string[]
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="w-full bg-background">
        <SelectValue placeholder="请选择" />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          {options.map((option) => (
            <SelectItem key={option} value={option}>
              {option}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

function normalizeTab(value: string | null): GovernanceTab {
  if (value === 'route-guards' || value === 'simulation') return value
  return 'error-policies'
}

function orderByLocalPriority<T extends { id: number; priority: number }>(items: T[], order: number[]) {
  if (!order.length) {
    return [...items].sort((left, right) => left.priority - right.priority)
  }

  const ranking = new Map(order.map((id, index) => [id, index]))
  return [...items].sort((left, right) => {
    const leftRank = ranking.get(left.id) ?? Number.MAX_SAFE_INTEGER
    const rightRank = ranking.get(right.id) ?? Number.MAX_SAFE_INTEGER
    if (leftRank !== rightRank) return leftRank - rightRank
    return left.priority - right.priority
  })
}

function hasOrderChanged<T extends { id: number; priority: number }>(items: T[], order: number[]) {
  if (!items.length || !order.length) return false
  const current = [...items].sort((left, right) => left.priority - right.priority).map((item) => item.id)
  return current.join(',') !== order.join(',')
}

function reorderIds(ids: number[], sourceId: number, targetId: number) {
  const next = [...ids]
  const sourceIndex = next.indexOf(sourceId)
  const targetIndex = next.indexOf(targetId)
  if (sourceIndex === -1 || targetIndex === -1) return next
  next.splice(sourceIndex, 1)
  next.splice(targetIndex, 0, sourceId)
  return next
}

function moveId(ids: number[], sourceId: number, direction: 'up' | 'down') {
  const current = [...ids]
  const index = current.indexOf(sourceId)
  if (index === -1) return current
  const targetIndex = direction === 'up' ? index - 1 : index + 1
  if (targetIndex < 0 || targetIndex >= current.length) return current
  const [item] = current.splice(index, 1)
  current.splice(targetIndex, 0, item)
  return current
}

function describeRouteGuardTarget(item: {
  targetType: string
  providerType?: string | null
  siteProfileId?: number | null
  credentialId?: number | null
  accountId?: number | null
  proxyId?: number | null
}) {
  switch (item.targetType) {
    case 'PROVIDER_TYPE':
      return item.providerType ?? '-'
    case 'SITE_PROFILE':
      return `站点配置 ID=${item.siteProfileId ?? '-'}`
    case 'CREDENTIAL':
      return `凭证 ID=${item.credentialId ?? '-'}`
    case 'ACCOUNT':
      return `账号 ID=${item.accountId ?? '-'}`
    case 'PROXY':
      return `代理 ID=${item.proxyId ?? '-'}`
    default:
      return '-'
  }
}

function buildPolicySimulation({
  input,
  errorRules,
  routeGuards,
  quarantines,
}: {
  input: PolicySimulationInput
  errorRules: ErrorRule[]
  routeGuards: RouteGuardPolicy[]
  quarantines: QuarantineRecord[]
}): PolicySimulationResult {
  const now = Date.now()
  const nodes: PolicyChainNode[] = []
  const matchedPolicies = routeGuards
    .filter((policy) => policy.enabled)
    .filter((policy) => matchesRouteGuard(policy, input))
    .filter((policy) => isPolicyActive(policy, now))
    .toSorted((left, right) => {
      if (left.priority !== right.priority) return left.priority - right.priority
      return new Date(left.createdAt ?? 0).getTime() - new Date(right.createdAt ?? 0).getTime()
    })

  errorRules.forEach((rule) => {
    nodes.push({
      id: `error-${rule.id}`,
      kind: 'ERROR_RULE',
      title: `错误规则 #${rule.id}`,
      summary: `${rule.action} · ${rule.requestPath ?? '全部路径'} · ${rule.errorCode ?? '全部错误码'}`,
      detail: rule.rewriteMessage ?? rule.downgradePolicy ?? '命中错误策略。',
      matched: true,
      tone: toneForErrorAction(rule.action),
    })
  })

  matchedPolicies.forEach((policy) => {
    nodes.push({
      id: `guard-${policy.id}`,
      kind: 'ROUTE_GUARD',
      title: policy.policyName,
      summary: `${policy.policyMode} · ${policy.actionType} · ${describeRouteGuardTarget(policy)}`,
      detail: describeRoutePolicyRuntime(policy),
      matched: true,
      tone: toneForRouteGuard(policy),
    })
  })

  const activeQuarantines = quarantines
    .filter((item) => item.status === 'ACTIVE')
    .filter((item) => matchesQuarantine(item, input))
    .filter((item) => isQuarantineActive(item, now))

  activeQuarantines.forEach((item) => {
    nodes.push({
      id: `quarantine-${item.id}`,
      kind: 'QUARANTINE',
      title: `隔离记录 #${item.id}`,
      summary: `${item.actionType} · ${describeRouteGuardTarget(item)}`,
      detail: item.reason,
      matched: true,
      tone: item.actionType === 'COOLDOWN' ? 'warning' : 'danger',
    })
  })

  let routeDecision = 'ALLOW'
  let routeReason = '未命中 route guard 或隔离规则。'
  const allowOverride = matchedPolicies.find((policy) => policy.policyMode === 'OVERRIDE_ALLOW')

  if (allowOverride) {
    routeDecision = 'OVERRIDE_ALLOW'
    routeReason = '命中人工放行治理规则。'
  } else if (activeQuarantines.length) {
    routeDecision = activeQuarantines[0].actionType === 'COOLDOWN' ? 'COOLDOWN' : 'QUARANTINE'
    routeReason = activeQuarantines[0].reason
  } else {
    const blockingPolicy = matchedPolicies.find((policy) => policy.policyMode !== 'OVERRIDE_ALLOW')
    if (blockingPolicy) {
      routeDecision = `${blockingPolicy.policyMode}:${blockingPolicy.actionType}`
      routeReason = describeBlockingReason(blockingPolicy)
    }
  }

  const errorDecision = errorRules.length
    ? `${errorRules[0].action}${errorRules[0].rewriteCode ? `:${errorRules[0].rewriteCode}` : ''}`
    : 'NO_ERROR_POLICY'

  const finalAction =
    routeDecision === 'ALLOW' && errorDecision === 'NO_ERROR_POLICY'
      ? 'ALLOW'
      : `Route ${routeDecision} / Error ${errorDecision}`

  const finalReason =
    routeDecision === 'ALLOW' && errorDecision === 'NO_ERROR_POLICY'
      ? '当前请求不会被治理或错误规则拦截。'
      : `${routeReason}${errorRules.length ? ` 同时命中 ${errorRules.length} 条错误策略。` : ''}`

  nodes.push({
    id: 'decision',
    kind: 'DECISION',
    title: '最终决策',
    summary: finalAction,
    detail: finalReason,
    matched: true,
    tone:
      routeDecision === 'ALLOW' && errorDecision === 'NO_ERROR_POLICY'
        ? 'success'
        : routeDecision.includes('ALLOW')
          ? 'info'
          : routeDecision.includes('COOLDOWN') || errorDecision.includes('DOWNGRADE')
            ? 'warning'
            : 'danger',
  })

  if (!nodes.length) {
    nodes.push({
      id: 'empty',
      kind: 'DECISION',
      title: '未命中',
      summary: '当前没有命中任何治理或错误规则。',
      detail: '你可以调整 requestPath、errorCode 或目标范围后再次模拟。',
      matched: false,
      tone: 'neutral',
    })
  }

  return {
    finalAction,
    finalReason,
    matchedErrorRules: errorRules.length,
    matchedRouteGuards: matchedPolicies.length,
    matchedQuarantines: activeQuarantines.length,
    nodes,
  }
}

function matchesRouteGuard(policy: RouteGuardPolicy, input: PolicySimulationInput) {
  if (policy.providerType && policy.providerType !== toNullableString(input.providerType)) {
    return false
  }

  switch (policy.targetType) {
    case 'PROVIDER_TYPE':
      return policy.providerType != null && policy.providerType === toNullableString(input.providerType)
    case 'SITE_PROFILE':
      return policy.siteProfileId === toNullableNumber(input.siteProfileId)
    case 'CREDENTIAL':
      return policy.credentialId === toNullableNumber(input.credentialId)
    case 'ACCOUNT':
      return policy.accountId === toNullableNumber(input.accountId)
    case 'PROXY':
      return policy.proxyId === toNullableNumber(input.proxyId)
    default:
      return false
  }
}

function matchesQuarantine(record: QuarantineRecord, input: PolicySimulationInput) {
  if (record.providerType && record.providerType !== toNullableString(input.providerType)) {
    return false
  }

  switch (record.targetType) {
    case 'PROVIDER_TYPE':
      return record.providerType != null && record.providerType === toNullableString(input.providerType)
    case 'SITE_PROFILE':
      return record.siteProfileId === toNullableNumber(input.siteProfileId)
    case 'CREDENTIAL':
      return record.credentialId === toNullableNumber(input.credentialId)
    case 'ACCOUNT':
      return record.accountId === toNullableNumber(input.accountId)
    case 'PROXY':
      return record.proxyId === toNullableNumber(input.proxyId)
    default:
      return false
  }
}

function isPolicyActive(policy: RouteGuardPolicy, now: number) {
  if (!policy.effectiveUntil) return true
  return new Date(policy.effectiveUntil).getTime() > now
}

function isQuarantineActive(record: QuarantineRecord, now: number) {
  if (!record.expiresAt) return true
  return new Date(record.expiresAt).getTime() > now
}

function describeBlockingReason(policy: RouteGuardPolicy) {
  if (policy.policyMode === 'OVERRIDE_BLOCK') {
    return '命中人工阻断治理规则。'
  }

  switch (policy.actionType) {
    case 'COOLDOWN':
      return '命中临时冷却治理规则。'
    case 'QUARANTINE':
    case 'DRAIN':
      return '命中隔离治理规则。'
    default:
      return '命中治理阻断规则。'
  }
}

function toneForErrorAction(action: string) {
  const normalized = action.toUpperCase()
  if (normalized === 'DOWNGRADE') return 'warning'
  if (normalized === 'REWRITE' || normalized === 'PASSTHROUGH') return 'info'
  return 'danger'
}

function toneForRouteGuard(policy: RouteGuardPolicy) {
  if (policy.policyMode === 'OVERRIDE_ALLOW') return 'success'
  if (policy.actionType === 'COOLDOWN') return 'warning'
  if (policy.actionType === 'NONE' && policy.policyMode === 'ENFORCE') return 'danger'
  return policy.policyMode === 'OVERRIDE_BLOCK' ? 'danger' : 'warning'
}

function toErrorRulePayload(rule: ErrorRuleDraft | ErrorRule) {
  return {
    enabled: rule.enabled,
    priority: toRequiredNumber(rule.priority),
    providerType: toNullableString(rule.providerType),
    protocol: toNullableString(rule.protocol),
    modelPattern: toNullableString(rule.modelPattern),
    requestPath: toNullableString(rule.requestPath),
    httpStatus: toNullableNumber(rule.httpStatus),
    errorCode: toNullableString(rule.errorCode),
    matchScope: toNullableString(rule.matchScope),
    action: rule.action,
    rewriteStatus: toNullableNumber(rule.rewriteStatus),
    rewriteCode: toNullableString(rule.rewriteCode),
    rewriteMessage: toNullableString(rule.rewriteMessage),
    downgradePolicy: toNullableString(rule.downgradePolicy),
  }
}

function toRouteGuardPayload(rule: RouteGuardDraft | RouteGuardPolicy) {
  return {
    policyName: rule.policyName,
    targetType: rule.targetType,
    providerType: rule.targetType === 'PROVIDER_TYPE' ? toNullableString(rule.providerType) : null,
    siteProfileId: rule.targetType === 'SITE_PROFILE' ? toNullableNumber(rule.siteProfileId) : null,
    credentialId: rule.targetType === 'CREDENTIAL' ? toNullableNumber(rule.credentialId) : null,
    accountId: rule.targetType === 'ACCOUNT' ? toNullableNumber(rule.accountId) : null,
    proxyId: rule.targetType === 'PROXY' ? toNullableNumber(rule.proxyId) : null,
    policyMode: rule.policyMode,
    actionType: rule.actionType,
    ttlSeconds: toNullableNumber(rule.ttlSeconds),
    priority: toNullableNumber(rule.priority) ?? 100,
    enabled: rule.enabled,
    description: toNullableString(rule.description),
    retryPolicy: toNullableString(rule.retryPolicy),
    fallbackPolicy: toNullableString(rule.fallbackPolicy),
    circuitBreakerPolicy: toNullableString(rule.circuitBreakerPolicy),
    rateLimitPolicy: toNullableString(rule.rateLimitPolicy),
  }
}

function hasPolicyJson(value: string | null | undefined) {
  return value != null && value.trim() !== ''
}

function describeRoutePolicyRuntime(policy: RouteGuardPolicy) {
  const details = [
    policy.description,
    hasPolicyJson(policy.retryPolicy) ? `重试=${policy.retryPolicy}` : null,
    hasPolicyJson(policy.fallbackPolicy) ? `回退=${policy.fallbackPolicy}` : null,
    hasPolicyJson(policy.circuitBreakerPolicy) ? `熔断=${policy.circuitBreakerPolicy}` : null,
    hasPolicyJson(policy.rateLimitPolicy) ? `限流=${policy.rateLimitPolicy}` : null,
  ].filter(Boolean)

  return details.length ? details.join(' | ') : '命中路由守卫。'
}

function toNullableString(value: string | null | undefined) {
  return value == null || value.trim() === '' ? null : value.trim()
}

function toNullableNumber(value: string | number | null | undefined) {
  if (typeof value === 'number') return Number.isNaN(value) ? null : value
  if (value == null || value === '') return null
  const parsed = Number(value)
  return Number.isNaN(parsed) ? null : parsed
}

function toRequiredNumber(value: string | number | null | undefined) {
  return toNullableNumber(value) ?? 0
}
