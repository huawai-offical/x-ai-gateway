export function isChatLikePath(requestPath: string) {
  return requestPath === '/v1/chat/completions'
    || requestPath === '/v1/responses'
    || requestPath === '/v1/messages'
    || (requestPath.startsWith('/v1beta/models/')
      && (requestPath.includes(':generateContent') || requestPath.includes(':streamGenerateContent')))
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
    || requestPath === '/v1/files'
    || /^\/v1\/files\/[^/]+$/.test(requestPath)
    || /^\/v1\/files\/[^/]+\/content$/.test(requestPath)
}
