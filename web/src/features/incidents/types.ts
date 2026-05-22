import type { OutboundDelivery } from '../integrations/types'

export type IncidentEntityResponse = {
  entityType: string
  entityRef: string
  title: string
  summary: string
  severity: string
  status: string
  source: string
}

export type IncidentTimelineEventResponse = {
  eventType: string
  title: string
  description: string
  severity: string
  entityType?: string | null
  entityRef?: string | null
  source: string
  occurredAt: string
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
  activeQuarantineCount: number
  cooldownCredentialCount: number
  observedAt?: string | null
}

export type CredentialHealthScore = {
  credentialId: number
  credentialName: string
  providerType: string
  siteProfileId?: number | null
  accountId?: number | null
  active: boolean
  score: number
  healthState: string
  summary?: string | null
  cooldownUntil?: string | null
  observedAt?: string | null
  activeGuardPolicies: string[]
  recentActions: string[]
}

export type GovernanceHealthScoreResponse = {
  sites: SiteHealthScore[]
  credentials: CredentialHealthScore[]
}

export type OpsAlertEvent = {
  id: number
  ruleId?: number | null
  eventType: string
  severity: string
  title: string
  message: string
  status: string
  entityType?: string | null
  entityRef?: string | null
  metricValue?: number | null
  acknowledgedAt?: string | null
  createdAt: string
  updatedAt: string
}

export type AlertSilenceResponse = {
  id: number
  silenceName: string
  eventType: string
  severity?: string | null
  entityType?: string | null
  entityRef?: string | null
  startsAt: string
  endsAt: string
  enabled: boolean
  reason?: string | null
  createdAt: string
  updatedAt: string
}

export type QuarantineRecordResponse = {
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
  createdAt: string
  updatedAt: string
}

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
  alerts: OpsAlertEvent[]
  recentLogs: Array<Record<string, unknown>>
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
  observedAt: string
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
  breakdowns: Array<Record<string, unknown>>
  risks: OpsSloRisk[]
  recommendedActions: string[]
}

export type DistributedKeyPressure = {
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

export type OpsCapacitySummary = {
  observedAt: string
  distributedKeys: DistributedKeyPressure[]
  providerBreakdowns: Array<Record<string, unknown>>
  siteBreakdowns: Array<Record<string, unknown>>
  accountBreakdowns: Array<Record<string, unknown>>
  proxyBreakdowns: Array<Record<string, unknown>>
  recommendedActions: string[]
}

export type IncidentSummaryResponse = {
  opsSummary: OpsSummary
  sloSummary: OpsSloSummary
  capacitySummary: OpsCapacitySummary
  healthScores: GovernanceHealthScoreResponse
  incidents: OpsAlertEvent[]
  silences: AlertSilenceResponse[]
  quarantines: QuarantineRecordResponse[]
  affectedEntities: IncidentEntityResponse[]
  timeline: IncidentTimelineEventResponse[]
  recommendedActions: string[]
}

export type IncidentDeliveriesView = OutboundDelivery[]
