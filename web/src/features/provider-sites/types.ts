export type CapabilityResolution = {
  declaredLevel?: string | null
  implementedLevel?: string | null
  effectiveLevel?: string | null
  blockedReasons: string[]
  lossReasons: string[]
}

export type SurfaceCapability = {
  resourceType: string
  operation: string
  executionCapabilityLevel?: string | null
  renderCapabilityLevel?: string | null
  overallCapabilityLevel?: string | null
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
  surfaces: Record<string, SurfaceCapability>
  sourceRefreshedAt?: string | null
}

export type TranslationPlan = {
  executable: boolean
  ingressProtocol?: string | null
  requestPath?: string | null
  requestedModel?: string | null
  publicModel?: string | null
  resolvedModel?: string | null
  resourceType?: string | null
  operation?: string | null
  requiredFeatures: string[]
  featureLevels: Record<string, string>
  executionKind?: string | null
  executionCapabilityLevel?: string | null
  renderCapabilityLevel?: string | null
  overallCapabilityLevel?: string | null
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
  text?: string | null
  usage?: unknown
  toolCalls?: unknown[]
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

export function resolutionTone(resolution: CapabilityResolution) {
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

export function modelSupportsFeature(model: SiteModelCapability, surface?: string | null) {
  if (!surface) return true
  return Boolean(model.surfaces[surface])
}

export function isChatLikePath(requestPath: string) {
  return requestPath === '/v1/chat/completions' || requestPath === '/v1/responses'
}
