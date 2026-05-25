import { apiClient } from '@/lib/api'
import type {
  ErrorRule,
  ErrorRulePreviewRequest,
  ErrorRulePreviewResponse,
} from '@/features/error-rules/types'
import type {
  OpsAnalyticsOverview,
  OpsCapacitySummary,
  OpsHealthOverview,
  OpsSloSummary,
  OpsSummary,
  AlertEvent,
  AlertRule,
  AlertSilence,
  AutoActionRule,
  GovernanceHealthScores,
  QuarantineRecord,
  RouteGuardPolicy,
  RoutingPolicyRuntimePlan,
  RoutingPolicyRuntimeState,
} from './types'
import type { OutboundDelivery } from '../integrations/types'

type ErrorRulePayload = {
  enabled: boolean
  priority: number
  providerType?: string | null
  protocol?: string | null
  modelPattern?: string | null
  requestPath?: string | null
  httpStatus?: number | null
  errorCode?: string | null
  matchScope?: string | null
  action: string
  rewriteStatus?: number | null
  rewriteCode?: string | null
  rewriteMessage?: string | null
  downgradePolicy?: string | null
}

type RouteGuardPayload = {
  policyName: string
  targetType: string
  providerType?: string | null
  siteProfileId?: number | null
  credentialId?: number | null
  accountId?: number | null
  proxyId?: number | null
  policyMode: string
  actionType: string
  ttlSeconds?: number | null
  priority?: number | null
  enabled?: boolean | null
  description?: string | null
  retryPolicy?: string | null
  fallbackPolicy?: string | null
  circuitBreakerPolicy?: string | null
  rateLimitPolicy?: string | null
}

type AutoActionPayload = {
  ruleName: string
  eventType: string
  severity?: string | null
  entityType?: string | null
  actionType: string
  ttlSeconds?: number | null
  recoveryMode: string
  enabled?: boolean | null
  description?: string | null
}

type AlertSilencePayload = {
  silenceName: string
  eventType?: string | null
  severity?: string | null
  entityType?: string | null
  entityRef?: string | null
  startsAt?: string | null
  endsAt?: string | null
  enabled?: boolean | null
  reason?: string | null
}

export const governanceApi = {
  listErrorRules: () => apiClient.get<ErrorRule[]>('/admin/error-rules'),
  saveErrorRule: (payload: ErrorRulePayload & { id?: number }) =>
    payload.id == null
      ? apiClient.post<ErrorRule>('/admin/error-rules', { body: omitId(payload) })
      : apiClient.put<ErrorRule>(`/admin/error-rules/${payload.id}`, { body: omitId(payload) }),
  deleteErrorRule: (id: number) =>
    apiClient.delete<void>(`/admin/error-rules/${id}`, { responseType: 'void' }),
  previewErrorRules: (payload: ErrorRulePreviewRequest) =>
    apiClient.post<ErrorRulePreviewResponse>('/admin/error-rules/preview', { body: payload }),
  listRouteGuards: () => apiClient.get<RouteGuardPolicy[]>('/admin/ops/policies/route-guards'),
  routingRuntimePlan: () => apiClient.get<RoutingPolicyRuntimePlan>('/admin/ops/policies/routing-runtime-plan'),
  routingRuntimeStates: () => apiClient.get<RoutingPolicyRuntimeState[]>('/admin/ops/policies/routing-runtime-states'),
  resetRoutingRuntimeStates: () =>
    apiClient.post<void>('/admin/ops/policies/routing-runtime-states/reset', { responseType: 'void' }),
  saveRouteGuard: (payload: RouteGuardPayload & { id?: number }) =>
    payload.id == null
      ? apiClient.post<RouteGuardPolicy>('/admin/ops/policies/route-guards', { body: omitId(payload) })
      : apiClient.put<RouteGuardPolicy>(`/admin/ops/policies/route-guards/${payload.id}`, { body: omitId(payload) }),
  deleteRouteGuard: (id: number) =>
    apiClient.delete<void>(`/admin/ops/policies/route-guards/${id}`, { responseType: 'void' }),
  listAutoActions: () => apiClient.get<AutoActionRule[]>('/admin/ops/policies/auto-actions'),
  saveAutoAction: (payload: AutoActionPayload & { id?: number }) =>
    payload.id == null
      ? apiClient.post<AutoActionRule>('/admin/ops/policies/auto-actions', { body: omitId(payload) })
      : apiClient.put<AutoActionRule>(`/admin/ops/policies/auto-actions/${payload.id}`, { body: omitId(payload) }),
  deleteAutoAction: (id: number) =>
    apiClient.delete<void>(`/admin/ops/policies/auto-actions/${id}`, { responseType: 'void' }),
  listQuarantines: (status?: string) =>
    apiClient.get<QuarantineRecord[]>('/admin/ops/quarantines', {
      params: status ? { status } : undefined,
    }),
  releaseQuarantine: (id: number, releaseReason: string) =>
    apiClient.post<QuarantineRecord>(`/admin/ops/quarantines/${id}/release`, {
      body: { releaseReason },
    }),
  listHealthScores: () => apiClient.get<GovernanceHealthScores>('/admin/ops/health-scores'),
  listAlertRules: () => apiClient.get<AlertRule[]>('/admin/ops/alerts/rules'),
  createAlertRule: (payload: Omit<AlertRule, 'id'>) =>
    apiClient.post<AlertRule>('/admin/ops/alerts/rules', { body: payload }),
  listAlertEvents: () => apiClient.get<AlertEvent[]>('/admin/ops/alerts', { params: { status: 'OPEN' } }),
  acknowledgeAlert: (id: number) => apiClient.post<AlertEvent>(`/admin/ops/alerts/${id}/ack`),
  listAlertSilences: () => apiClient.get<AlertSilence[]>('/admin/ops/alerts/silences'),
  saveAlertSilence: (payload: AlertSilencePayload) =>
    apiClient.post<AlertSilence>('/admin/ops/alerts/silences', { body: payload }),
  listOutboundDeliveries: () => apiClient.get<OutboundDelivery[]>('/admin/integrations/deliveries'),
}

export const opsApi = {
  summary: () => apiClient.get<OpsSummary>('/admin/ops/summary'),
  sloSummary: () => apiClient.get<OpsSloSummary>('/admin/ops/slo'),
  capacitySummary: () => apiClient.get<OpsCapacitySummary>('/admin/ops/capacity'),
  health: (params: { from?: string | null; to?: string | null }) =>
    apiClient.get<OpsHealthOverview>('/admin/observability/health', {
      params: {
        from: params.from,
        to: params.to,
      },
    }),
  analyticsOverview: (params?: {
    distributedKeyId?: number | null
    providerType?: string | null
    from?: string | null
    to?: string | null
    bucketMinutes?: number | null
  }) =>
    apiClient.get<OpsAnalyticsOverview>('/admin/analytics/overview', {
      params: {
        distributedKeyId: params?.distributedKeyId,
        providerType: params?.providerType,
        from: params?.from,
        to: params?.to,
        bucketMinutes: params?.bucketMinutes,
      },
    }),
}

function omitId<T extends { id?: number }>(payload: T): Omit<T, 'id'> {
  const { id, ...rest } = payload
  void id
  return rest
}

export type GovernanceApi = typeof governanceApi
export type OpsApi = typeof opsApi
