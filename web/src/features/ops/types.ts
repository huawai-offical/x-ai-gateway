export type OpsTrafficSnapshot = {
  observedAt: string
  qps: number
  errorRate: number
  p95LatencyMs: number
  providerFailures: number
  activeAlerts: number
  affectedEntities: string[]
}

export type OpsSummary = {
  snapshot: OpsTrafficSnapshot
  alerts: AlertEvent[]
  recentLogs: unknown[]
}

export type OpsSloRisk = {
  scopeType: string
  scopeRef?: string | null
  policyName: string
  burnRate: number
  errorBudgetRemainingRatio: number
  riskLevel: string
  suspectedCauses: string[]
  suggestedActions: string[]
}

export type OpsSloSummary = {
  observedAt?: string | null
  summary: {
    requestCount: number
    failedRequestCount: number
    errorRate: number
    errorBudgetRatio: number
    errorBudgetRemainingRatio: number
    burnRate: number
    riskLevel: string
    silencedAlertCount: number
  }
  risks: OpsSloRisk[]
  recommendedActions: string[]
}

export type CapacityPressureItem = {
  distributedKeyId: number
  keyName: string
  maskedKey: string
  pressureLevel: string
  budgetLimitMicros?: number | null
  currentBudgetMicros?: number | null
  remainingBudgetMicros?: number | null
  rpmLimit?: number | null
  currentRpm?: number | null
  remainingRpm?: number | null
  tpmLimit?: number | null
  currentTpm?: number | null
  remainingTpm?: number | null
  concurrencyLimit?: number | null
  currentConcurrency?: number | null
  remainingConcurrency?: number | null
  notes: string[]
}

export type AnalyticsBreakdownItem = {
  key: string
  count: number
  cacheHitTokens: number
  cacheWriteTokens: number
  savedInputTokens: number
}

export type AnalyticsCountBreakdownItem = {
  key: string
  count: number
}

export type OpsCapacitySummary = {
  observedAt?: string | null
  distributedKeys: CapacityPressureItem[]
  providerRanking?: AnalyticsBreakdownItem[]
  modelGroupRanking?: AnalyticsBreakdownItem[]
  credentialRanking?: AnalyticsBreakdownItem[]
  alerts?: unknown[]
  recommendedActions: string[]
}

export type OpsAnalyticsTimelineBucket = {
  bucketStart: string
  routeDecisionCount: number
  cacheHitCount: number
  cacheHitTokens: number
  cacheWriteTokens: number
  savedInputTokens: number
  usageRecordCount: number
  totalTokens: number
  failedRequestCount: number
  p95LatencyMs: number
}

export type DistributedKeyAnalyticsItem = {
  distributedKeyId?: number | null
  keyName: string
  keyPrefix: string
  routeDecisionCount: number
  cacheHitCount: number
  cacheHitTokens: number
  cacheWriteTokens: number
  savedInputTokens: number
  usageRecordCount: number
  finalUsageRecordCount: number
  partialUsageRecordCount: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  failedRequestCount: number
  avgLatencyMs: number
  cacheHitRatio: number
}

export type OpsAnalyticsOverview = {
  sampledFrom?: string | null
  sampledTo?: string | null
  bucketMinutes: number
  sampledRouteDecisionCount: number
  sampledCacheHitCount: number
  sampledActiveCacheReferenceCount: number
  sampledUsageRecordCount: number
  sampledFinalUsageRecordCount: number
  sampledPartialUsageRecordCount: number
  totalCacheHitTokens: number
  totalCacheWriteTokens: number
  totalSavedInputTokens: number
  providerBreakdown: AnalyticsBreakdownItem[]
  protocolBreakdown: AnalyticsBreakdownItem[]
  selectionSourceBreakdown: AnalyticsBreakdownItem[]
  modelGroupBreakdown: AnalyticsBreakdownItem[]
  cacheSourceBreakdown: AnalyticsBreakdownItem[]
  usageCompletenessBreakdown: AnalyticsCountBreakdownItem[]
  distributedKeyBreakdown?: DistributedKeyAnalyticsItem[]
  timeline: OpsAnalyticsTimelineBucket[]
}

export type HealthMetric = {
  totalRequests: number
  successfulRequests: number
  failedRequests: number
  canceledRequests: number
  successRate: number
  availabilityRate: number
  errorRate: number
  avgDurationMs: number
  lastSuccessfulAt?: string | null
  lastFailedAt?: string | null
}

export type CredentialHealthMetric = HealthMetric & {
  credentialId: number
  providerType: string
  credentialLabel?: string | null
  credentialPrefix?: string | null
}

export type ProviderHealthMetric = HealthMetric & {
  providerType: string
}

export type OpsHealthOverview = {
  sampledFrom: string
  sampledTo: string
  total: HealthMetric
  credentials: CredentialHealthMetric[]
  providers: ProviderHealthMetric[]
}

export type AlertRule = {
  id: number
  ruleName: string
  metricKey: string
  comparisonOperator: string
  thresholdValue: number
  severity: string
}

export type AlertEvent = {
  id: number
  ruleId?: number | null
  eventType?: string | null
  title: string
  severity: string
  status: string
  message: string
  entityType?: string | null
  entityRef?: string | null
  metricValue?: number | null
  acknowledgedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type RouteGuardPolicy = {
  id: number
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
  effectiveUntil?: string | null
  priority: number
  enabled: boolean
  description?: string | null
  retryPolicy?: string | null
  fallbackPolicy?: string | null
  circuitBreakerPolicy?: string | null
  rateLimitPolicy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type RouteGuardDraft = {
  id?: number
  policyName: string
  targetType: string
  providerType: string
  siteProfileId: string
  credentialId: string
  accountId: string
  proxyId: string
  policyMode: string
  actionType: string
  ttlSeconds: string
  priority: string
  enabled: boolean
  description: string
  retryPolicy: string
  fallbackPolicy: string
  circuitBreakerPolicy: string
  rateLimitPolicy: string
}

export type RoutingPolicyRuntimePlan = {
  maxAttempts: number
  fallbackEnabled: boolean
  fallbackOrder: string[]
  circuitBreakerEnabled: boolean
  circuitFailureThreshold?: number | null
  rateLimitEnabled: boolean
  requestsPerMinute?: number | null
  sourcePolicyIds: number[]
  warnings: string[]
}

export type RoutingPolicyRuntimeState = {
  runtimeKey: string
  policyId?: number | null
  targetRef: string
  state: string
  failureCount: number
  openUntil?: string | null
  currentWindowCount: number
  windowExpiresAt?: string | null
  reason?: string | null
}

export type AutoActionRule = {
  id: number
  ruleName: string
  eventType: string
  severity?: string | null
  entityType?: string | null
  actionType: string
  ttlSeconds?: number | null
  recoveryMode: string
  enabled: boolean
  description?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type AutoActionDraft = {
  id?: number
  ruleName: string
  eventType: string
  severity: string
  entityType: string
  actionType: string
  ttlSeconds: string
  recoveryMode: string
  enabled: boolean
  description: string
}

export type QuarantineRecord = {
  id: number
  targetType: string
  providerType?: string | null
  siteProfileId?: number | null
  credentialId?: number | null
  accountId?: number | null
  proxyId?: number | null
  sourceRuleId?: number | null
  sourceEventId?: number | null
  actionType: string
  recoveryMode: string
  reason: string
  status: string
  startedAt: string
  expiresAt?: string | null
  releasedAt?: string | null
  releaseReason?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type CredentialHealthScore = {
  sourceType?: 'API_KEY' | 'AUTH_JSON_ACCOUNT' | string
  sourceId?: number | null
  credentialId?: number | null
  accountId?: number | null
  credentialName: string
  displayName?: string | null
  providerType: string
  siteProfileId?: number | null
  proxyId?: number | null
  active: boolean
  frozen?: boolean | null
  score: number
  healthState: string
  summary?: string | null
  reason?: string | null
  cooldownUntil?: string | null
  observedAt?: string | null
  activeGuardPolicies?: string[]
  recentActions?: string[]
  effectiveUntil?: string | null
}

export type SiteHealthScore = {
  siteProfileId: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  active: boolean
  score: number
  healthState: string
  summary?: string | null
  reason?: string | null
  activeCredentialCount?: number
  blockedCredentialCount?: number
  activeQuarantineCount?: number
  cooldownCredentialCount?: number
  observedAt?: string | null
  effectiveUntil?: string | null
}

export type GovernanceHealthScores = {
  sites: SiteHealthScore[]
  credentials: CredentialHealthScore[]
}

export type AlertSilence = {
  id: number
  silenceName: string
  eventType?: string | null
  severity?: string | null
  entityType?: string | null
  entityRef?: string | null
  startsAt?: string | null
  endsAt?: string | null
  enabled: boolean
  reason?: string | null
}

export type AlertSilenceDraft = {
  silenceName: string
  eventType: string
  severity: string
  entityType: string
  entityRef: string
  startsAt: string
  endsAt: string
  enabled: boolean
  reason: string
}

export type GovernanceTab = 'error-policies' | 'route-guards' | 'simulation'

export type PolicySimulationInput = {
  providerType: string
  protocol: string
  model: string
  requestPath: string
  httpStatus: string
  errorCode: string
  matchScope: string
  message: string
  siteProfileId: string
  credentialId: string
  accountId: string
  proxyId: string
}

export type PolicyChainNode = {
  id: string
  kind: 'ERROR_RULE' | 'ROUTE_GUARD' | 'QUARANTINE' | 'DECISION'
  title: string
  summary: string
  detail: string
  matched: boolean
  tone: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
}

export type PolicySimulationResult = {
  finalAction: string
  finalReason: string
  matchedErrorRules: number
  matchedRouteGuards: number
  matchedQuarantines: number
  nodes: PolicyChainNode[]
}

export function createDefaultRouteGuardDraft(): RouteGuardDraft {
  return {
    policyName: '',
    targetType: 'CREDENTIAL',
    providerType: 'OPENAI_DIRECT',
    siteProfileId: '',
    credentialId: '',
    accountId: '',
    proxyId: '',
    policyMode: 'ENFORCE',
    actionType: 'QUARANTINE',
    ttlSeconds: '300',
    priority: '100',
    enabled: true,
    description: '',
    retryPolicy: '',
    fallbackPolicy: '',
    circuitBreakerPolicy: '',
    rateLimitPolicy: '',
  }
}

export function routeGuardToDraft(policy: RouteGuardPolicy): RouteGuardDraft {
  return {
    id: policy.id,
    policyName: policy.policyName,
    targetType: policy.targetType,
    providerType: policy.providerType ?? '',
    siteProfileId: policy.siteProfileId == null ? '' : String(policy.siteProfileId),
    credentialId: policy.credentialId == null ? '' : String(policy.credentialId),
    accountId: policy.accountId == null ? '' : String(policy.accountId),
    proxyId: policy.proxyId == null ? '' : String(policy.proxyId),
    policyMode: policy.policyMode,
    actionType: policy.actionType,
    ttlSeconds: policy.ttlSeconds == null ? '' : String(policy.ttlSeconds),
    priority: String(policy.priority),
    enabled: policy.enabled,
    description: policy.description ?? '',
    retryPolicy: policy.retryPolicy ?? '',
    fallbackPolicy: policy.fallbackPolicy ?? '',
    circuitBreakerPolicy: policy.circuitBreakerPolicy ?? '',
    rateLimitPolicy: policy.rateLimitPolicy ?? '',
  }
}

export function createDefaultAutoActionDraft(): AutoActionDraft {
  return {
    ruleName: '',
    eventType: 'REQUEST_ERROR_RATIO',
    severity: 'HIGH',
    entityType: 'CREDENTIAL',
    actionType: 'QUARANTINE',
    ttlSeconds: '300',
    recoveryMode: 'AUTO_RESUME',
    enabled: true,
    description: '',
  }
}

export function autoActionToDraft(rule: AutoActionRule): AutoActionDraft {
  return {
    id: rule.id,
    ruleName: rule.ruleName,
    eventType: rule.eventType,
    severity: rule.severity ?? '',
    entityType: rule.entityType ?? '',
    actionType: rule.actionType,
    ttlSeconds: rule.ttlSeconds == null ? '' : String(rule.ttlSeconds),
    recoveryMode: rule.recoveryMode,
    enabled: rule.enabled,
    description: rule.description ?? '',
  }
}

export function createDefaultAlertSilenceDraft(): AlertSilenceDraft {
  return {
    silenceName: '',
    eventType: 'REQUEST_ERROR_RATIO',
    severity: 'HIGH',
    entityType: 'CREDENTIAL',
    entityRef: '',
    startsAt: '',
    endsAt: '',
    enabled: true,
    reason: '',
  }
}

export function createDefaultPolicySimulationInput(): PolicySimulationInput {
  return {
    providerType: 'OPENAI_DIRECT',
    protocol: 'openai',
    model: 'gpt-4o',
    requestPath: '/v1/chat/completions',
    httpStatus: '500',
    errorCode: 'UPSTREAM_ERROR',
    matchScope: 'UPSTREAM',
    message: '',
    siteProfileId: '',
    credentialId: '',
    accountId: '',
    proxyId: '',
  }
}
