import { apiClient } from '@/lib/api'
import type {
  PortalAnnouncement,
  PortalBalanceLedger,
  PortalChannelStatus,
  PortalEmailVerificationStartResponse,
  PortalKey,
  PortalKeyCreatePayload,
  PortalKeyCreateResponse,
  PortalPasskeyCredential,
  PortalPaymentOrder,
  PortalPaymentOrderCreatePayload,
  PortalProfile,
  PortalRedeemResponse,
  PortalRedeemStatus,
  PortalSecurityStatus,
  PortalSession,
  PortalSocialOAuthIdentity,
  PortalSocialOAuthProvider,
  PortalSubscription,
  PortalTotpSetupResponse,
  PortalUsageSummary,
} from './types'

export type PortalLoginPayload = {
  email: string
  password: string
}

export type PortalRegisterPayload = PortalLoginPayload & {
  displayName?: string | null
}

export function getPortalSession() {
  return apiClient.get<PortalSession>('/portal/auth/session')
}

export function loginPortal(payload: PortalLoginPayload) {
  return apiClient.post<PortalSession>('/portal/auth/login', {
    body: payload,
  })
}

export function registerPortal(payload: PortalRegisterPayload) {
  return apiClient.post<PortalSession>('/portal/auth/register', {
    body: payload,
  })
}

export function logoutPortal() {
  return apiClient.post<void>('/portal/auth/logout', {
    responseType: 'void',
  })
}

export function getPortalProfile() {
  return apiClient.get<PortalProfile>('/portal/profile')
}

export function getPortalSecurityStatus() {
  return apiClient.get<PortalSecurityStatus>('/portal/auth/security/status')
}

export function listPortalPasskeys() {
  return apiClient.get<PortalPasskeyCredential[]>('/portal/auth/security/passkeys')
}

export function deletePortalPasskey(id: number) {
  return apiClient.delete<PortalPasskeyCredential[]>(`/portal/auth/security/passkeys/${id}`)
}

export function startPortalEmailVerification() {
  return apiClient.post<PortalEmailVerificationStartResponse>('/portal/auth/email-verification/start')
}

export function confirmPortalEmailVerification(verificationId: string, verificationCode: string) {
  return apiClient.post<PortalSecurityStatus>('/portal/auth/email-verification/confirm', {
    body: { verificationId, verificationCode },
  })
}

export function setupPortalTotp() {
  return apiClient.post<PortalTotpSetupResponse>('/portal/auth/totp/setup')
}

export function enablePortalTotp(code: string) {
  return apiClient.post<PortalSecurityStatus>('/portal/auth/totp/enable', {
    body: { code },
  })
}

export function disablePortalTotp(code: string) {
  return apiClient.post<PortalSecurityStatus>('/portal/auth/totp/disable', {
    body: { code },
  })
}

export function listPortalOAuthProviders() {
  return apiClient.get<PortalSocialOAuthProvider[]>('/portal/auth/oauth/providers')
}

export function listPortalOAuthIdentities() {
  return apiClient.get<PortalSocialOAuthIdentity[]>('/portal/auth/oauth/identities')
}

export function unlinkPortalOAuthIdentity(identity: PortalSocialOAuthIdentity) {
  return apiClient.delete<PortalSocialOAuthIdentity[]>(`/portal/auth/oauth/${identity.provider}/identities`, {
    body: {
      identityId: identity.id,
      externalSubject: identity.externalSubject,
    },
  })
}

export function listPortalSubscriptions() {
  return apiClient.get<PortalSubscription[]>('/portal/subscriptions')
}

export function listPortalKeys() {
  return apiClient.get<PortalKey[]>('/portal/keys')
}

export function createPortalKey(payload: PortalKeyCreatePayload) {
  return apiClient.post<PortalKeyCreateResponse>('/portal/keys', {
    body: payload,
  })
}

export function rotatePortalKey(id: number) {
  return apiClient.post<PortalKeyCreateResponse>(`/portal/keys/${id}/rotate`)
}

export function disablePortalKey(id: number) {
  return apiClient.post<PortalKey>(`/portal/keys/${id}/disable`)
}

export function listPortalAnnouncements() {
  return apiClient.get<PortalAnnouncement[]>('/portal/announcements')
}

export function markPortalAnnouncementRead(id: string) {
  return apiClient.post<PortalAnnouncement>(`/portal/announcements/${id}/read`)
}

export function getPortalRedeemStatus() {
  return apiClient.get<PortalRedeemStatus>('/portal/redeem/status')
}

export function redeemPortalCode(code: string) {
  return apiClient.post<PortalRedeemResponse>('/portal/redeem', {
    body: { code },
  })
}

export function listPortalBalanceLedger() {
  return apiClient.get<PortalBalanceLedger[]>('/portal/balance-ledger')
}

export function listPortalPaymentOrders() {
  return apiClient.get<PortalPaymentOrder[]>('/portal/orders')
}

export function createPortalPaymentOrder(payload: PortalPaymentOrderCreatePayload) {
  return apiClient.post<PortalPaymentOrder>('/portal/orders', {
    body: payload,
  })
}

export function getPortalUsageSummary() {
  return apiClient.get<PortalUsageSummary>('/portal/usage/summary')
}

export function listPortalChannelStatuses() {
  return apiClient.get<PortalChannelStatus[]>('/portal/channels/status')
}
