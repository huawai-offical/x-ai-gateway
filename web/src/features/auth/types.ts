export type AdminAuthChallenge = {
  challengeId: string
  mathPrompt: string
  issuedAt: string
  expiresAt: string
  powAlgorithm: string
  powSalt: string
  powDifficulty: number
}

export type AdminLoginPayload = {
  username: string
  password: string
  challengeId: string
  mathAnswer: number
  powNonce: string
}

export type AdminSession = {
  authenticated: boolean
  username: string | null
  authenticatedAt: string | null
  expiresAt: string | null
}

export type AdminAuthSettings = {
  username: string
  persisted: boolean
  credentialSource: string
  initializedAt: string | null
  updatedAt: string | null
}

export type AdminAuthSettingsUpdatePayload = {
  username: string
  currentPassword: string
  newPassword?: string
}

export type PowSolveState =
  | { status: 'idle'; nonce: string; attempts: number }
  | { status: 'solving'; nonce: string; attempts: number }
  | { status: 'solved'; nonce: string; attempts: number }
  | { status: 'failed'; nonce: string; attempts: number; message: string }
