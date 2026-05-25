export type RequestLogEntry = {
  id?: number
  requestId: string
  distributedKeyId?: number | null
  distributedKeyPrefix?: string | null
  protocol?: string | null
  requestPath?: string | null
  resourceType?: string | null
  operation?: string | null
  requestedModel?: string | null
  publicModel?: string | null
  resolvedModelKey?: string | null
  modelGroup?: string | null
  providerType?: string | null
  credentialId?: number | null
  selectionSource?: string | null
  executionBackend?: string | null
  gatewayResourceKey?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  responseKind?: string | null
  responseObjectType?: string | null
  responseObjectId?: string | null
  responseStatus?: string | null
  canonicalEventCount?: number | null
  status?: string | null
  startedAt?: string | null
  completedAt?: string | null
  createdAt?: string | null
  durationMs?: number | null
  errorCode?: string | null
  errorMessage?: string | null
  [key: string]: unknown
}

export type RouteDecisionEntry = {
  id?: number
  requestId?: string | null
  distributedKeyId?: number | null
  distributedKeyPrefix?: string | null
  requestedModel?: string | null
  publicModel?: string | null
  resolvedModelKey?: string | null
  protocol?: string | null
  requestPath?: string | null
  resourceType?: string | null
  operation?: string | null
  modelGroup?: string | null
  selectionSource?: string | null
  executionBackend?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  selectedCredentialId?: number | null
  selectedProviderType?: string | null
  selectedBaseUrl?: string | null
  candidateCount?: number | null
  candidateSummaryJson?: string | null
  createdAt?: string | null
  [key: string]: unknown
}

export type CacheHitEntry = {
  id?: number
  requestId?: string | null
  distributedKeyId?: number | null
  protocol?: string | null
  requestPath?: string | null
  resourceType?: string | null
  operation?: string | null
  providerType?: string | null
  credentialId?: number | null
  modelGroup?: string | null
  prefixHash?: string | null
  fingerprint?: string | null
  cacheKind?: string | null
  executionBackend?: string | null
  supportStatus?: string | null
  degradationLevel?: string | null
  objectMode?: string | null
  cacheHitTokens?: number | null
  cacheWriteTokens?: number | null
  savedInputTokens?: number | null
  cachedContentRef?: string | null
  createdAt?: string | null
  [key: string]: unknown
}

export type UpstreamCacheReferenceEntry = {
  id?: number
  distributedKeyId?: number | null
  providerType?: string | null
  credentialId?: number | null
  modelGroup?: string | null
  prefixHash?: string | null
  externalCacheRef?: string | null
  status?: string | null
  expireAt?: string | null
  lastUsedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
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
  createdAt?: string | null
  updatedAt?: string | null
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

export type TraceDetailEntry = {
  id?: number
  requestId?: string | null
  stage?: string | null
  direction?: string | null
  contentKind?: string | null
  payloadJson?: unknown
  metadataJson?: unknown
  payloadHash?: string | null
  metadataHash?: string | null
  originalLength?: number | null
  storedLength?: number | null
  metadataOriginalLength?: number | null
  metadataStoredLength?: number | null
  truncated?: boolean | null
  metadataTruncated?: boolean | null
  redacted?: boolean | null
  metadataRedacted?: boolean | null
  expiresAt?: string | null
  createdAt?: string | null
  [key: string]: unknown
}

export type ObservabilityTraceResponse = {
  requestLog?: RequestLogEntry | null
  routeDecision?: RouteDecisionEntry | null
  cacheHits: CacheHitEntry[]
  upstreamCacheReferences: UpstreamCacheReferenceEntry[]
  traceDetails?: TraceDetailEntry[]
  asyncResourceSummary?: AsyncResourceSummary | null
  asyncResourceDetail?: AsyncResourceDetail | null
}

export type TraceLookupResponse = {
  requestId?: string | null
  gatewayResourceKey?: string | null
  upstreamObjectId?: string | null
  matches: RequestLogEntry[]
  trace?: ObservabilityTraceResponse | null
}
