export type CredentialResponse = {
  id: number
  credentialName: string
  providerType: string
  baseUrl: string
  authKind: string
  supportedModels: string[]
  secretFingerprint: string
  credentialMetadata: Record<string, unknown>
  active: boolean
  cooldownUntil?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  lastErrorAt?: string | null
  lastUsedAt?: string | null
  totalRequestCount?: number
  successfulRequestCount?: number
  failedRequestCount?: number
  canceledRequestCount?: number
  totalTokenCount?: number
  totalCacheHitTokenCount?: number
  totalCacheWriteTokenCount?: number
  totalSavedInputTokenCount?: number
  requestSuccessRate?: number
  cacheHitRate?: number
  totalDurationMs?: number
  durationSampleCount?: number
  avgDurationMs?: number
  totalFirstTokenMs?: number
  firstTokenSampleCount?: number
  avgFirstTokenMs?: number
  lastFirstTokenMs?: number | null
  minFirstTokenMs?: number | null
  maxFirstTokenMs?: number | null
  proxyId?: number | null
  tlsFingerprintProfileId?: number | null
  siteProfileId?: number | null
  groupId?: number | null
  groupName?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type UpstreamCredentialInventoryResponse = {
  sourceType: 'API_KEY' | 'AUTH_JSON_ACCOUNT'
  sourceId: number
  rowKey: string
  displayName: string
  providerType: string
  authKind?: string | null
  baseUrl?: string | null
  supportedModels: string[]
  secretFingerprint?: string | null
  externalAccountId?: string | null
  metadata?: Record<string, unknown>
  active: boolean
  frozen?: boolean | null
  healthy?: boolean | null
  refreshStatus?: string | null
  refreshFailureCount?: number | null
  cooldownUntil?: string | null
  lastErrorCode?: string | null
  lastErrorMessage?: string | null
  lastErrorAt?: string | null
  lastUsedAt?: string | null
  lastRefreshAt?: string | null
  tokenExpiresAt?: string | null
  nextRefreshAfter?: string | null
  proxyId?: number | null
  tlsFingerprintProfileId?: number | null
  siteProfileId?: number | null
  groupId?: number | null
  groupName?: string | null
  totalRequestCount?: number
  successfulRequestCount?: number
  failedRequestCount?: number
  canceledRequestCount?: number
  totalTokenCount?: number
  totalCacheHitTokenCount?: number
  totalCacheWriteTokenCount?: number
  totalSavedInputTokenCount?: number
  requestSuccessRate?: number
  cacheHitRate?: number
  totalDurationMs?: number
  durationSampleCount?: number
  avgDurationMs?: number
  totalFirstTokenMs?: number
  firstTokenSampleCount?: number
  avgFirstTokenMs?: number
  lastFirstTokenMs?: number | null
  minFirstTokenMs?: number | null
  maxFirstTokenMs?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type CredentialConnectivityResponse = {
  providerType: string
  baseUrl: string
  reachable: boolean
  latencyMs: number
  discoveredModelCount: number
  sampleModels: string[]
  message: string
}

export type CredentialModelRefreshResponse = {
  credentialId: number
  modelCount: number
  sampleModels: string[]
  refreshedAt?: string | null
}

export type CredentialFormState = {
  credentialName: string
  providerType: string
  baseUrl: string
  authKind: string
  secret: string
  metadataJson: string
  active: boolean
  proxyId: string
  tlsFingerprintProfileId: string
  siteProfileId: string
  groupId: string
  supportedModels: string[]
}

export const PROVIDER_TYPE_OPTIONS = [
  'OPENAI_DIRECT',
  'OPENAI_COMPATIBLE',
  'ANTHROPIC_DIRECT',
  'GEMINI_DIRECT',
  'OLLAMA_DIRECT',
] as const

export const AUTH_KIND_OPTIONS = [
  'API_KEY',
  'ACCESS_TOKEN',
  'GOOGLE_ACCESS_TOKEN',
] as const

export function createEmptyCredentialForm(): CredentialFormState {
  return {
    credentialName: '',
    providerType: 'OPENAI_DIRECT',
    baseUrl: '',
    authKind: 'API_KEY',
    secret: '',
    metadataJson: '',
    active: true,
    proxyId: '',
    tlsFingerprintProfileId: '',
    siteProfileId: '',
    groupId: '',
    supportedModels: [],
  }
}

export function credentialToFormState(credential: CredentialResponse): CredentialFormState {
  return {
    credentialName: credential.credentialName,
    providerType: credential.providerType,
    baseUrl: credential.baseUrl,
    authKind: credential.authKind,
    secret: '',
    metadataJson: Object.keys(credential.credentialMetadata ?? {}).length
      ? JSON.stringify(credential.credentialMetadata, null, 2)
      : '',
    active: credential.active,
    proxyId: credential.proxyId == null ? '' : String(credential.proxyId),
    tlsFingerprintProfileId: credential.tlsFingerprintProfileId == null ? '' : String(credential.tlsFingerprintProfileId),
    siteProfileId: credential.siteProfileId == null ? '' : String(credential.siteProfileId),
    groupId: credential.groupId == null ? '' : String(credential.groupId),
    supportedModels: Array.isArray(credential.supportedModels) ? credential.supportedModels : [],
  }
}

export function buildCredentialPayload(form: CredentialFormState) {
  const metadata = parseCredentialMetadata(form.metadataJson)
  return {
    credentialName: form.credentialName.trim(),
    providerType: form.providerType,
    baseUrl: form.baseUrl.trim(),
    authKind: form.authKind,
    secret: form.secret.trim(),
    credentialMetadata: metadata,
    active: form.active,
    proxyId: parseOptionalNumber(form.proxyId),
    tlsFingerprintProfileId: parseOptionalNumber(form.tlsFingerprintProfileId),
    siteProfileId: parseOptionalNumber(form.siteProfileId),
    groupId: parseOptionalNumber(form.groupId),
    supportedModels: normalizeModels(form.supportedModels),
  }
}

function parseCredentialMetadata(metadataJson: string) {
  if (!metadataJson.trim()) {
    return {}
  }

  try {
    const parsed = JSON.parse(metadataJson)
    if (parsed == null || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error('凭证 metadata 必须是 JSON 对象。')
    }
    return parsed as Record<string, unknown>
  } catch (error) {
    if (error instanceof Error && error.message === '凭证 metadata 必须是 JSON 对象。') {
      throw error
    }
    throw new Error('凭证 metadata 不是合法的 JSON。')
  }
}

function parseOptionalNumber(value: string) {
  if (!value.trim()) {
    return null
  }

  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error('数字字段必须填写有效数字。')
  }
  return parsed
}

function normalizeModels(models: string[]) {
  const deduplicated = new Map<string, string>()
  for (const raw of models) {
    const value = raw.trim()
    if (!value) {
      continue
    }
    const key = value.toLowerCase()
    if (!deduplicated.has(key)) {
      deduplicated.set(key, value)
    }
  }
  return Array.from(deduplicated.values())
}
