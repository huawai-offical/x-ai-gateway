export type DebugPreset = {
  id: string
  label: string
  hint: string
  protocol: string
  method: string
  requestPath: string
  requestedModel: string
  body: string
  formFields?: string
  fileRefs?: string
}

export const DEBUG_PRESETS: DebugPreset[] = [
  {
    id: 'chat',
    label: '对话',
    hint: '快速预演 `/v1/chat/completions` 的白盒翻译链路。',
    protocol: 'openai',
    method: 'POST',
    requestPath: '/v1/chat/completions',
    requestedModel: 'gpt-4o',
    body: '{"model":"gpt-4o","messages":[{"role":"user","content":"hello"}]}',
  },
  {
    id: 'responses',
    label: 'Responses 响应',
    hint: '检查 `/v1/responses` 的规范化计划与上游载荷。',
    protocol: 'openai',
    method: 'POST',
    requestPath: '/v1/responses',
    requestedModel: 'gpt-4.1-mini',
    body: '{"model":"gpt-4.1-mini","input":"summarize this request"}',
  },
  {
    id: 'embeddings',
    label: '向量嵌入',
    hint: '验证 `/v1/embeddings` 的路由与能力降级。',
    protocol: 'openai',
    method: 'POST',
    requestPath: '/v1/embeddings',
    requestedModel: 'text-embedding-3-small',
    body: '{"model":"text-embedding-3-small","input":"hello"}',
  },
  {
    id: 'audio-transcription',
    label: '音频',
    hint: '验证 multipart 资源路径、fileRefs 与资源响应归一化。',
    protocol: 'openai',
    method: 'POST',
    requestPath: '/v1/audio/transcriptions',
    requestedModel: 'gpt-4o-mini-transcribe',
    body: '{}',
    formFields: '{"model":"gpt-4o-mini-transcribe","language":"zh"}',
    fileRefs: '[{"fieldName":"file","fileKey":"file-123"}]',
  },
]
