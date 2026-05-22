export type CatalogCandidateView = {
  credentialId?: number | null
  credentialName?: string | null
  providerType?: string | null
  siteProfileId?: number | null
  providerFamily?: string | null
  siteKind?: string | null
  authStrategy?: string | null
  pathStrategy?: string | null
  errorSchemaStrategy?: string | null
  baseUrl?: string | null
  modelName?: string | null
  modelKey?: string | null
  supportedProtocols: string[]
  supportsChat?: boolean
  supportsTools?: boolean
  supportsImageInput?: boolean
  supportsEmbeddings?: boolean
  supportsCache?: boolean
  supportsThinking?: boolean
  supportsVisibleReasoning?: boolean
  supportsReasoningReuse?: boolean
  reasoningTransport?: string | null
  capabilityLevel?: string | null
}

export type RouteCandidateView = {
  candidate?: CatalogCandidateView | null
  bindingId?: number | null
  bindingPriority?: number | null
  bindingWeight?: number | null
  capabilityLevel?: string | null
  capabilityRank?: number | null
}

export type RouteCandidateEvaluation = {
  candidate?: RouteCandidateView | null
  eligible: boolean
  healthState?: string | null
  cooldownUntil?: string | null
  affinityMatched?: boolean
  selectionSource?: string | null
  totalScore?: number | null
  scoreBreakdown: string[]
  exclusionReasons: string[]
}

export type RouteExecutionAttempt = {
  attempt: number
  credentialId?: number | null
  providerType?: string | null
  outcome?: string | null
  detail?: string | null
}

export type RouteSelectionResult = {
  distributedKeyId?: number | null
  distributedKeyPrefix?: string | null
  requestedModel?: string | null
  publicModel?: string | null
  resolvedModelKey?: string | null
  protocol?: string | null
  prefixHash?: string | null
  fingerprint?: string | null
  modelGroup?: string | null
  clientFamily?: string | null
  governanceNotes: string[]
  governanceReservationKey?: string | null
  selectionSource?: string | null
  selectedCandidate?: RouteCandidateView | null
  candidates: RouteCandidateView[]
  candidateEvaluations: RouteCandidateEvaluation[]
  attempts: RouteExecutionAttempt[]
}

export type GatewayUsageView = {
  rawPromptTokens: number
  promptTokens: number
  completionTokens: number
  reasoningTokens: number
  cacheHitTokens: number
  cacheWriteTokens: number
  upstreamCacheHitTokens: number
  upstreamCacheWriteTokens: number
  savedInputTokens: number
  cachedContentRef?: string | null
  totalTokens: number
  completeness: 'NONE' | 'PARTIAL' | 'FINAL'
  source: 'NONE' | 'DIRECT_RESPONSE' | 'PROVIDER_FINAL' | 'LAST_VISIBLE'
  nativeUsagePayload?: unknown
}

export type CanonicalExecutionPlan = {
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
  selection: RouteSelectionResult
  requestedSemantics: {
    resourceType: string
    operation: string
    requiredFeatures: string[]
    requiresRouteSelection: boolean
  }
  canonicalRequest: Record<string, unknown>
  plan: CanonicalExecutionPlan
  candidateEvaluations: RouteCandidateEvaluation[]
}

export type ExecutionPreview = {
  selection: RouteSelectionResult
  canonicalRequest: Record<string, unknown>
  plan: CanonicalExecutionPlan
  providerBinding: unknown
  providerOptions: Record<string, unknown>
  translatedUpstreamPayload: ExecutionPreviewPayload
  providerBindingSummary: ExecutionPreviewBindingSummary
  normalizedResponsePreview: NormalizedResponsePreview
}

export type ExecutionPreviewBindingSummary = {
  bindingId?: number | null
  bindingPriority?: number | null
  bindingWeight?: number | null
  capabilityLevel?: string | null
  siteProfileId?: number | null
  credentialId?: number | null
  providerType?: string | null
  providerFamily?: string | null
  siteKind?: string | null
  baseUrl?: string | null
  modelKey?: string | null
}

export type ExecutionPreviewPayloadPart = {
  type?: string | null
  text?: string | null
  mimeType?: string | null
  uri?: string | null
  name?: string | null
  toolCallId?: string | null
  toolName?: string | null
}

export type ExecutionPreviewPayloadMessage = {
  role?: string | null
  text?: string | null
  parts: ExecutionPreviewPayloadPart[]
}

export type ExecutionPreviewPayload = {
  providerType?: string | null
  resolvedModel?: string | null
  requestPath?: string | null
  objectMode?: string | null
  messages: ExecutionPreviewPayloadMessage[]
  providerOptions: Record<string, unknown>
}

export type NormalizedResponsePreview = {
  surface?: string | null
  objectMode?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  notes: string[]
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

export type AdminChatExecuteResponse = {
  requestId: string
  routeSelection: RouteSelectionResult
  plan?: CanonicalExecutionPlan | null
  executionBackend?: string | null
  text?: string | null
  usage?: GatewayUsageView | null
  toolCalls?: unknown[]
}

export type AdminResourceExecuteResponse = {
  requestId?: string | null
  gatewayResourceKey?: string | null
  routeSelection: RouteSelectionResult
  plan: CanonicalExecutionPlan
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

export function featureLabel(feature: string) {
  return FEATURE_LABELS[feature] ?? feature
}

const FEATURE_LABELS: Record<string, string> = {
  response_object: 'Responses 响应',
  embeddings: '向量嵌入',
  audio_transcription: '音频转写',
  image_generation: '图片生成',
  moderation: '内容审核',
  file_object: '文件对象',
  upload_create: '上传任务',
  batch_create: '批处理任务',
  tuning_create: '微调任务',
}
