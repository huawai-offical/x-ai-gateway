export type CapabilityResolution = {
  declaredLevel?: string | null
  implementedLevel?: string | null
  effectiveLevel?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  blockedReasons: string[]
  lossReasons: string[]
}

export type SurfaceCapability = {
  resourceType: string
  operation: string
  surface?: string | null
  normalizedPath?: string | null
  preferredBackend?: string | null
  supportedBackends: string[]
  supportStatus?: string | null
  degradationLevel?: string | null
  executionCapabilityLevel?: string | null
  renderCapabilityLevel?: string | null
  overallCapabilityLevel?: string | null
  blockerReasons: string[]
  lossReasons: string[]
  requiredFeatures: string[]
  featureResolutions: Record<string, CapabilityResolution>
}

export type ProviderSite = {
  id: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  authStrategy: string
  pathStrategy: string
  modelAddressingStrategy: string
  errorSchemaStrategy: string
  baseUrlPattern?: string | null
  description?: string | null
  active: boolean
  healthState: string
  blockedReason?: string | null
  supportedProtocols: string[]
  compatibilitySurface: string
  credentialRequirements: string[]
  streamTransport?: string | null
  fallbackStrategy?: string | null
  cooldownCredentialCount: number
  cooldownUntil?: string | null
  preferredBackend?: string | null
  supportedBackends: string[]
  features: Record<string, CapabilityResolution>
  surfaces: Record<string, SurfaceCapability>
  modelCount: number
  refreshedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type CapabilityMatrixRow = {
  siteProfileId: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  authStrategy: string
  pathStrategy: string
  errorSchemaStrategy: string
  healthState: string
  blockedReason?: string | null
  supportedProtocols: string[]
  compatibilitySurface: string
  credentialRequirements: string[]
  streamTransport?: string | null
  fallbackStrategy?: string | null
  cooldownCredentialCount: number
  cooldownUntil?: string | null
  preferredBackend?: string | null
  supportedBackends: string[]
  features: Record<string, CapabilityResolution>
  surfaces: Record<string, SurfaceCapability>
  supportsResponses: boolean
  supportsEmbeddings: boolean
  supportsAudio: boolean
  supportsImages: boolean
  supportsModeration: boolean
  supportsFiles: boolean
  supportsUploads: boolean
  supportsBatches: boolean
  supportsTuning: boolean
  supportsRealtime: boolean
}

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

export type SurfaceDossierItemResponse = {
  surfaceKey: string
  operation: string
  normalizedPath?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  overallCapabilityLevel?: string | null
  blockerReasons: string[]
  lossReasons: string[]
}

export type ProviderSiteDossierResponse = {
  site: ProviderSite
  capabilities: SiteModelCapability[]
  blockedSurfaces: SurfaceDossierItemResponse[]
  degradedSurfaces: SurfaceDossierItemResponse[]
  acceptedExceptions: SurfaceDossierItemResponse[]
  recommendedActions: string[]
}

export type TraceLookupResponse = {
  requestId?: string | null
  gatewayResourceKey?: string | null
  upstreamObjectId?: string | null
  matches: RequestLogEntry[]
  trace?: ObservabilityTraceResponse | null
}

export type SiteModelCapability = {
  id: number
  modelName: string
  modelKey: string
  supportedProtocols: string[]
  supportsChat: boolean
  supportsTools: boolean
  supportsImageInput: boolean
  supportsEmbeddings: boolean
  supportsCache: boolean
  supportsThinking: boolean
  supportsVisibleReasoning: boolean
  supportsReasoningReuse: boolean
  reasoningTransport?: string | null
  capabilityLevel: string
  preferredBackend?: string | null
  supportedBackends: string[]
  surfaces: Record<string, SurfaceCapability>
  sourceRefreshedAt?: string | null
}

export type TranslationPlan = {
  executable: boolean
  ingressProtocol?: string | null
  requestPath?: string | null
  normalizedPath?: string | null
  surface?: string | null
  requestedModel?: string | null
  publicModel?: string | null
  resolvedModel?: string | null
  resourceType?: string | null
  operation?: string | null
  requiredFeatures: string[]
  featureLevels: Record<string, string>
  executionKind?: string | null
  executionBackend?: string | null
  supportStatus?: string | null
  objectMode?: string | null
  routeSelectionMode?: string | null
  supportedBackends: string[]
  backendReason?: string | null
  routePolicyReason?: string | null
  renderPolicyReason?: string | null
  fallbackPolicyReason?: string | null
  degradationLevel?: string | null
  executionCapabilityLevel?: string | null
  renderCapabilityLevel?: string | null
  overallCapabilityLevel?: string | null
  blockerReasons: string[]
  degradations: string[]
  blockers: string[]
}

export type RouteSelectionPreview = {
  selection: unknown
  requestedSemantics: {
    resourceType: string
    operation: string
    requiredFeatures: string[]
    requiresRouteSelection: boolean
  }
  canonicalRequest: Record<string, unknown>
  plan: TranslationPlan
  candidateEvaluations: unknown[]
}

export type ExecutionPreview = {
  selection: unknown
  canonicalRequest: Record<string, unknown>
  plan: TranslationPlan
  providerBinding: unknown
  providerOptions: Record<string, unknown>
}

export type AdminChatExecuteResponse = {
  requestId: string
  routeSelection: unknown
  plan?: TranslationPlan | null
  executionBackend?: string | null
  text?: string | null
  usage?: unknown
  toolCalls?: unknown[]
}

export type CanonicalResourceEvent = {
  eventType?: string | null
  objectType?: string | null
  objectId?: string | null
  lifecyclePhase?: string | null
  status?: string | null
  details?: Record<string, unknown> | null
}

export type CanonicalResourceDegradation = {
  code?: string | null
  message?: string | null
  level?: string | null
  blocker: boolean
}

export type CanonicalResourceResponse = {
  resourceType?: string | null
  operation?: string | null
  responseKind?: string | null
  objectType?: string | null
  objectId?: string | null
  status?: string | null
  events: CanonicalResourceEvent[]
  degradations: CanonicalResourceDegradation[]
  body?: unknown
  binaryLength?: number | null
  metadata?: Record<string, unknown> | null
}

export type AdminResourceExecuteResponse = {
  requestId?: string | null
  gatewayResourceKey?: string | null
  routeSelection: unknown
  plan: TranslationPlan
  executionBackend?: string | null
  upstreamPath?: string | null
  objectMode?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  blockerReasons: string[]
  statusCode: number
  contentType?: string | null
  responseJson?: unknown
  responseText?: string | null
  binaryLength?: number | null
  canonicalResponse?: CanonicalResourceResponse | null
}

export type RequestLogEntry = {
  requestId: string
  gatewayResourceKey?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  responseKind?: string | null
  responseObjectType?: string | null
  responseObjectId?: string | null
  responseStatus?: string | null
  [key: string]: unknown
}

export type RouteDecisionEntry = {
  requestId?: string | null
  selectionSource?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  [key: string]: unknown
}

export type CacheHitEntry = {
  cacheKind?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  [key: string]: unknown
}

export type UpstreamCacheReferenceEntry = {
  externalCacheRef?: string | null
  status?: string | null
  [key: string]: unknown
}

export type AsyncResourceSummary = {
  resourceKey: string
  resourceType?: string | null
  status?: string | null
  normalizedStatus?: string | null
  objectMode?: string | null
  upstreamObjectId?: string | null
  eventCount?: number | null
  [key: string]: unknown
}

export type AsyncResourceDetail = {
  lifecycle?: unknown
  transitions?: unknown[]
  lineage?: unknown
  artifacts?: unknown[]
  requestPayloadJson?: unknown
  responsePayloadJson?: unknown
  metadataJson?: unknown
}

export type ObservabilityTraceResponse = {
  requestLog?: RequestLogEntry | null
  routeDecision?: RouteDecisionEntry | null
  cacheHits: CacheHitEntry[]
  upstreamCacheReferences: UpstreamCacheReferenceEntry[]
  asyncResourceSummary?: AsyncResourceSummary | null
  asyncResourceDetail?: AsyncResourceDetail | null
}

export type ProviderSiteDraft = {
  profileCode: string
  displayName: string
  siteKind: string
  baseUrlPattern: string
  description: string
  active: boolean
}

export const SITE_KIND_OPTIONS = [
  'OPENAI_DIRECT',
  'OPENAI_COMPATIBLE_GENERIC',
  'AZURE_OPENAI',
  'DEEPSEEK',
  'GROK',
  'MISTRAL',
  'COHERE',
  'TOGETHER',
  'FIREWORKS',
  'OPENROUTER',
  'ANTHROPIC_DIRECT',
  'GEMINI_DIRECT',
  'OLLAMA_DIRECT',
  'VERTEX_AI',
] as const

const FEATURE_LABELS: Record<string, string> = {
  response_object: 'Responses',
  embeddings: 'Embeddings',
  audio_transcription: 'Audio',
  image_generation: 'Images',
  moderation: 'Moderation',
  file_object: 'Files',
  upload_create: 'Uploads',
  batch_create: 'Batches',
  tuning_create: 'Tuning',
  realtime_client_secret: 'Realtime',
}

export function featureLabel(feature: string) {
  return FEATURE_LABELS[feature] ?? feature
}

export function formatInstant(value?: string | null) {
  if (!value) return '无'
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    timeZone: 'Asia/Shanghai',
  })
}

export function isAcceptedExceptionReason(reason?: string | null) {
  if (!reason) return false
  const normalized = reason.toLowerCase()
  return normalized.includes('accepted exception') || normalized.includes('accepted-exception')
}

export function resolutionTone(resolution: CapabilityResolution) {
  const supportStatus = resolution.supportStatus?.toLowerCase()
  if (supportStatus === 'blocked') {
    return 'blocked'
  }
  if (supportStatus === 'degraded') {
    return 'lossy'
  }
  if (resolution.blockedReasons.length || resolution.effectiveLevel === 'UNSUPPORTED' || resolution.effectiveLevel === 'unsupported') {
    return 'blocked'
  }
  if (resolution.lossReasons.length || resolution.effectiveLevel === 'LOSSY' || resolution.effectiveLevel === 'lossy') {
    return 'lossy'
  }
  if (resolution.effectiveLevel === 'EMULATED' || resolution.effectiveLevel === 'emulated') {
    return 'emulated'
  }
  return 'native'
}

export function matchesResolutionFilter(rowFeatures: Record<string, CapabilityResolution>, filter: string) {
  if (filter === 'all') return true
  const values = Object.values(rowFeatures)
  if (filter === 'blocked') {
    return values.some((resolution) => resolutionTone(resolution) === 'blocked')
  }
  if (filter === 'lossy') {
    return values.some((resolution) => resolutionTone(resolution) === 'lossy')
  }
  return true
}

export function summarizeSurfaceFeatureStatuses(surface: SurfaceCapability) {
  return surface.requiredFeatures
    .map((feature) => {
      const resolution = surface.featureResolutions[feature]
      const status = resolution?.supportStatus ?? resolution?.effectiveLevel ?? '-'
      return `${feature}:${status}`
    })
    .join(', ')
}

export function modelSupportsFeature(model: SiteModelCapability, surface?: string | null) {
  if (!surface) return true
  return Boolean(model.surfaces[surface])
}

export function isChatLikePath(requestPath: string) {
  return requestPath === '/v1/chat/completions'
    || requestPath === '/v1/responses'
    || requestPath === '/v1/messages'
    || (requestPath.startsWith('/v1beta/models/') && (requestPath.includes(':generateContent') || requestPath.includes(':streamGenerateContent')))
}

export function isMultipartResourcePath(requestPath: string) {
  return requestPath === '/v1/audio/transcriptions'
    || requestPath === '/v1/audio/translations'
    || requestPath === '/v1/images/edits'
    || requestPath === '/v1/images/variations'
    || requestPath === '/v1/files'
    || /^\/v1\/uploads\/[^/]+\/parts$/.test(requestPath)
}

export function isDebugExecutablePath(requestPath: string) {
  if (isChatLikePath(requestPath)) return true
  if (isMultipartResourcePath(requestPath)) return true
  return requestPath === '/v1/embeddings'
    || requestPath === '/v1/audio/speech'
    || requestPath === '/v1/images/generations'
    || requestPath === '/v1/moderations'
    || requestPath === '/v1/uploads'
    || /^\/v1\/uploads\/[^/]+$/.test(requestPath)
    || requestPath === '/v1/batches'
    || /^\/v1\/batches\/[^/]+$/.test(requestPath)
    || /^\/v1\/batches\/[^/]+\/cancel$/.test(requestPath)
    || requestPath === '/v1/fine_tuning/jobs'
    || /^\/v1\/fine_tuning\/jobs\/[^/]+$/.test(requestPath)
    || /^\/v1\/fine_tuning\/jobs\/[^/]+\/cancel$/.test(requestPath)
    || requestPath === '/v1/realtime/client_secrets'
    || requestPath === '/v1/files'
    || /^\/v1\/files\/[^/]+$/.test(requestPath)
    || /^\/v1\/files\/[^/]+\/content$/.test(requestPath)
}
