const textEncoder = new TextEncoder()

type SolvePowOptions = {
  batchSize?: number
  maxAttempts?: number
  signal?: AbortSignal
  onProgress?: (attempts: number) => void
}

export async function solvePowChallenge(
  challengeId: string,
  powSalt: string,
  difficulty: number,
  options?: SolvePowOptions,
) {
  const normalizedDifficulty = Math.max(0, difficulty)
  if (normalizedDifficulty === 0) {
    options?.onProgress?.(0)
    return '0'
  }

  const maxAttempts = options?.maxAttempts ?? 500_000
  const batchSize = Math.max(32, options?.batchSize ?? 512)
  const prefix = '0'.repeat(normalizedDifficulty)

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    if (options?.signal?.aborted) {
      throw new DOMException('POW 计算已取消。', 'AbortError')
    }

    const nonce = attempt.toString(16)
    const payload = `${challengeId}:${powSalt}:${nonce}`
    const hashHex = await sha256Hex(payload)
    if (hashHex.startsWith(prefix)) {
      options?.onProgress?.(attempt + 1)
      return nonce
    }

    if ((attempt + 1) % batchSize === 0) {
      options?.onProgress?.(attempt + 1)
      await yieldToBrowser()
    }
  }

  throw new Error('未在预期次数内求出 POW，请刷新 challenge 后重试。')
}

async function sha256Hex(input: string) {
  const cryptoImpl = resolveCrypto()
  const digest = await cryptoImpl.subtle.digest('SHA-256', textEncoder.encode(input))
  return Array.from(new Uint8Array(digest), toHexByte).join('')
}

function resolveCrypto() {
  const cryptoImpl = globalThis.crypto
  if (!cryptoImpl?.subtle) {
    throw new Error('当前环境不支持 Web Crypto，无法计算 POW。')
  }
  return cryptoImpl
}

function toHexByte(value: number) {
  return value.toString(16).padStart(2, '0')
}

function yieldToBrowser() {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, 0)
  })
}
