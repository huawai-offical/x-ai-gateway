export type WebhookEndpoint = {
  id: number
  endpointName: string
  endpointUrl: string
  signingMode: string
  timeoutMs: number
  enabled: boolean
  secretFingerprint?: string | null
}

export type NotificationChannel = {
  id: number
  channelName: string
  channelType: string
  webhookEndpointId?: number | null
  emailTo?: string | null
  templateMode: string
  enabled: boolean
}

export type OutboundSubscription = {
  id: number
  subscriptionName: string
  channelId: number
  eventType?: string | null
  severity?: string | null
  entityType?: string | null
  providerType?: string | null
  siteProfileId?: number | null
  enabled: boolean
}

export type OutboundDelivery = {
  id: number
  eventId: string
  eventType: string
  channelId: number
  entityType?: string | null
  entityRef?: string | null
  requestId?: string | null
  gatewayResourceKey?: string | null
  upstreamObjectId?: string | null
  deliveryStatus: string
  attemptCount: number
  nextRetryAt?: string | null
  lastError?: string | null
  responseCode?: number | null
  responseSummary?: string | null
  payloadJson: string
  occurredAt: string
  deliveredAt?: string | null
}

export type RunbookLink = {
  id: number
  linkName: string
  eventType?: string | null
  entityType?: string | null
  linkUrl: string
  description?: string | null
  enabled: boolean
}
