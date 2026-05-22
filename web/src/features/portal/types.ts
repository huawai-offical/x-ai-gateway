export type PortalSession = {
  authenticated: boolean
  userId?: number | null
  email?: string | null
  displayName?: string | null
  authenticatedAt?: string | null
  expiresAt?: string | null
}

export type PortalProfile = {
  userId: number
  email: string
  displayName?: string | null
  active: boolean
  emailVerified: boolean
  totpEnabled: boolean
  passkeyCount: number
  lastLoginAt?: string | null
  createdAt?: string | null
}

export type PortalSecurityStatus = {
  emailVerified: boolean
  emailVerifiedAt?: string | null
  totpEnabled: boolean
  totpVerifiedAt?: string | null
  passkeyEnabled: boolean
  passkeyCount: number
  emailVerificationRequiredForKeyCreation: boolean
}

export type PortalPasskeyCredential = {
  id: number
  credentialId: string
  credentialName: string
  rpId: string
  origin: string
  transports: string[]
  signCount: number
  lastUsedAt?: string | null
  createdAt?: string | null
}

export type PortalSocialOAuthIdentity = {
  id: number
  provider: string
  externalSubject: string
  email?: string | null
  displayName?: string | null
  lastLoginAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type PortalSocialOAuthProvider = {
  provider: string
  displayName: string
  authorizationEndpoint: string
  defaultScopes: string[]
}

export type PortalEmailVerificationStartResponse = {
  verificationId: string
  verificationCode: string
  expiresAt?: string | null
}

export type PortalTotpSetupResponse = {
  secret: string
  otpauthUri: string
}

export type PortalSubscription = {
  id: number
  planId: number
  planName: string
  status: string
  startsAt: string
  expiresAt?: string | null
  autoRenew: boolean
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
  dailyTokenLimit?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type PortalKey = {
  id: number
  keyName: string
  maskedKey: string
  active: boolean
  allowedProtocolSuites: string[]
  allowedModels: string[]
  expiresAt?: string | null
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
  lastUsedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type PortalKeyCreatePayload = {
  keyName: string
  allowedProtocolSuites?: string[]
  allowedModels?: string[]
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
}

export type PortalKeyCreateResponse = {
  key: PortalKey
  fullKey: string
  secretNotice: string
}

export type PortalAnnouncement = {
  id: string
  title: string
  summary: string
  body?: string | null
  read: boolean
  publishedAt?: string | null
}

export type PortalRedeemStatus = {
  available: boolean
  message: string
  currentTokenCredits: number
}

export type PortalRedeemResponse = {
  success: boolean
  message: string
  campaignName?: string | null
  deltaTokenCredits: number
  balanceAfterTokenCredits: number
  redeemedAt?: string | null
}

export type PortalBalanceLedger = {
  id: number
  deltaTokenCredits: number
  balanceAfterTokenCredits: number
  reason: string
  referenceType?: string | null
  referenceId?: string | null
  createdAt?: string | null
}

export type PortalPaymentOrder = {
  id: number
  orderNo: string
  provider: string
  amountMinor: number
  currency: string
  tokenCredits: number
  status: string
  providerTradeNo?: string | null
  providerInstanceCode?: string | null
  checkoutUrl?: string | null
  checkoutMethod?: string | null
  checkoutExpiresAt?: string | null
  refundAmountMinor: number
  refundedAt?: string | null
  disputedAt?: string | null
  reconciledAt?: string | null
  reconcileStatus?: string | null
  paidAt?: string | null
  createdAt?: string | null
}

export type PortalPaymentOrderCreatePayload = {
  provider?: string
  amountMinor: number
  currency?: string
  tokenCredits: number
  metadataJson?: string
}

export type PortalUsageSummary = {
  requestCount: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
  cacheHitTokens: number
  recentUsage: PortalUsageItem[]
}

export type PortalUsageItem = {
  requestId: string
  distributedKeyId?: number | null
  protocol: string
  modelGroup: string
  providerType: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  completeness: string
  createdAt?: string | null
}

export type PortalChannelStatus = {
  siteProfileId: number
  profileCode: string
  displayName: string
  siteKind: string
  active: boolean
  healthState: string
  blockedReason?: string | null
  supportedProtocols: string[]
  refreshedAt?: string | null
}
