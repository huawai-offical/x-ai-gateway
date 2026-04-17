import { type FormEvent, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedMutation, useTypedQuery } from '../../lib/typed-react-query'
import {
  featureLabel,
  isChatLikePath,
  isDebugExecutablePath,
  isMultipartResourcePath,
  type AdminChatExecuteResponse,
  type AdminResourceExecuteResponse,
  type ObservabilityTraceResponse,
  type TranslationPlan,
} from './types'

type DebugPreset = {
  id: string
  label: string
  description: string
  protocol: string
  method: string
  requestPath: string
  requestedModel: string
  body: string
  formFields?: string
  fileRefs?: string
}

const DEBUG_PRESETS: DebugPreset[] = [
  {
    id: 'chat',
    label: 'Chat',
    description: '快速调试 `/v1/chat/completions`。',
    protocol: 'openai',
    method: 'POST',
    requestPath: '/v1/chat/completions',
    requestedModel: 'gpt-4o',
    body: '{"model":"gpt-4o","messages":[{"role":"user","content":"hello"}]}',
  },
  {
    id: 'embeddings',
    label: 'Embeddings',
    description: '验证 `/v1/embeddings` 的执行解释。',
    protocol: 'openai',
    method: 'POST',
    requestPath: '/v1/embeddings',
    requestedModel: 'text-embedding-3-small',
    body: '{"model":"text-embedding-3-small","input":"hello"}',
  },
  {
    id: 'file-content',
    label: 'File Content',
    description: '检查文件内容读取的资源调试。',
    protocol: 'openai',
    method: 'GET',
    requestPath: '/v1/files/file_123/content',
    requestedModel: 'gpt-4o-mini',
    body: '{}',
  },
  {
    id: 'audio-transcription',
    label: 'Audio',
    description: '验证 multipart 资源路径与 fileRefs。',
    protocol: 'openai',
    method: 'POST',
    requestPath: '/v1/audio/transcriptions',
    requestedModel: 'gpt-4o-mini-transcribe',
    body: '{}',
    formFields: '{"model":"gpt-4o-mini-transcribe","language":"zh"}',
    fileRefs: '[{"fieldName":"file","fileKey":"file-123"}]',
  },
]

export function TranslationDebugPage() {
  const [searchParams] = useSearchParams()
  const [distributedKeyPrefix, setDistributedKeyPrefix] = useState(searchParams.get('distributedKeyPrefix') ?? 'sk-gw-test')
  const [protocol, setProtocol] = useState(searchParams.get('protocol') ?? 'openai')
  const [method, setMethod] = useState(searchParams.get('method') ?? 'POST')
  const [requestPath, setRequestPath] = useState(searchParams.get('requestPath') ?? '/v1/chat/completions')
  const [requestedModel, setRequestedModel] = useState(searchParams.get('requestedModel') ?? 'gpt-4o')
  const [body, setBody] = useState(searchParams.get('body') ?? '{"model":"gpt-4o","messages":[{"role":"user","content":"hello"}]}')
  const [formFields, setFormFields] = useState(searchParams.get('formFields') ?? '{"model":"gpt-4o-mini-transcribe"}')
  const [fileRefs, setFileRefs] = useState(searchParams.get('fileRefs') ?? '[{"fieldName":"file","fileKey":"file-123"}]')
  const [inputError, setInputError] = useState<string | null>(null)
  const multipartMode = isMultipartResourcePath(requestPath)

  const explainMutation = useTypedMutation<TranslationPlan, void>({
    mutationFn: async () => {
      const parsedBody = multipartMode
        ? buildMultipartExplainBody(requestedModel, formFields, fileRefs)
        : parseJsonBody(body)
      return apiRequest<TranslationPlan>('/admin/translation/explain', {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyPrefix,
          protocol,
          method,
          requestPath,
          requestedModel,
          body: parsedBody,
        }),
      })
    },
  })

  const executeMutation = useTypedMutation<AdminChatExecuteResponse, void>({
    mutationFn: async () => {
      const parsedBody = parseJsonBody(body)
      return apiRequest<AdminChatExecuteResponse>('/admin/chat/execute', {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyPrefix,
          protocol,
          requestPath,
          requestedModel,
          body: parsedBody,
        }),
      })
    },
  })

  const resourceExecuteMutation = useTypedMutation<AdminResourceExecuteResponse, void>({
    mutationFn: async () => {
      const parsedBody = multipartMode ? safeParseJsonBody(body) : parseJsonBody(body)
      return apiRequest<AdminResourceExecuteResponse>('/admin/resource/execute', {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyPrefix,
          protocol,
          method,
          requestPath,
          requestedModel,
          body: parsedBody,
          formFields: multipartMode ? parseJsonObject(formFields) : undefined,
          fileRefs: multipartMode ? parseJsonArray(fileRefs) : undefined,
        }),
      })
    },
  })

  const canExecute = isDebugExecutablePath(requestPath)
  const explainResult = explainMutation.data
  const executeResult = executeMutation.data
  const resourceExecuteResult = resourceExecuteMutation.data
  const activePlan = resourceExecuteResult?.plan ?? executeResult?.plan ?? explainResult ?? null
  const activeRequestId = resourceExecuteResult?.requestId ?? executeResult?.requestId ?? null
  const activeGatewayResourceKey = resourceExecuteResult?.gatewayResourceKey ?? null

  const traceQuery = useTypedQuery<ObservabilityTraceResponse>({
    queryKey: ['observability-trace', activeRequestId],
    queryFn: () => apiRequest<ObservabilityTraceResponse>(`/admin/observability/traces/${encodeURIComponent(activeRequestId ?? '')}`),
    enabled: Boolean(activeRequestId),
  })

  const executeTarget = canExecute
    ? isChatLikePath(requestPath)
      ? '/admin/chat/execute'
      : '/admin/resource/execute'
    : '当前路径不可执行'

  const activePreset = useMemo(
    () =>
      DEBUG_PRESETS.find((preset) => preset.method === method && preset.requestPath === requestPath)
      ?? null,
    [method, requestPath],
  )
  const bodyDraft = useMemo(() => inspectJsonDraft(body), [body])
  const formFieldsDraft = useMemo(
    () => (multipartMode ? inspectJsonDraft(formFields, 'object') : null),
    [formFields, multipartMode],
  )
  const fileRefsDraft = useMemo(
    () => (multipartMode ? inspectJsonDraft(fileRefs, 'array') : null),
    [fileRefs, multipartMode],
  )

  const requestSummary = useMemo(
    () => [
      { label: '预设', value: activePreset?.label ?? '自定义' },
      { label: '模式', value: multipartMode ? 'multipart resource' : isChatLikePath(requestPath) ? 'chat-like' : 'resource' },
      { label: '执行端点', value: executeTarget },
      { label: 'method', value: method },
      { label: 'path', value: requestPath },
    ],
    [activePreset?.label, executeTarget, method, multipartMode, requestPath],
  )

  const explainSummary = explainResult
    ? [
        { label: '可执行', value: String(explainResult.executable) },
        { label: 'backend', value: explainResult.executionBackend ?? '-' },
        { label: 'support', value: explainResult.supportStatus ?? '-' },
        { label: 'degradation', value: explainResult.degradationLevel ?? '-' },
        { label: 'surface', value: explainResult.surface ?? '-' },
        { label: 'operation', value: explainResult.operation ?? '-' },
        { label: 'normalizedPath', value: explainResult.normalizedPath ?? '-' },
        { label: 'resolvedModel', value: explainResult.resolvedModel ?? '-' },
      ]
    : []

  const planSummary = activePlan
    ? [
        { label: 'routeSelectionMode', value: activePlan.routeSelectionMode ?? '-' },
        { label: 'routePolicyReason', value: activePlan.routePolicyReason ?? '-' },
        { label: 'renderPolicyReason', value: activePlan.renderPolicyReason ?? '-' },
        { label: 'fallbackPolicyReason', value: activePlan.fallbackPolicyReason ?? '-' },
        { label: 'supportStatus', value: activePlan.supportStatus ?? '-' },
        { label: 'degradationLevel', value: activePlan.degradationLevel ?? '-' },
        { label: 'objectMode', value: activePlan.objectMode ?? '-' },
      ]
    : []

  const executeSummary = isChatLikePath(requestPath)
    ? executeResult
      ? [
          { label: 'requestId', value: executeResult.requestId },
          { label: 'gatewayResourceKey', value: activeGatewayResourceKey ?? '-' },
          { label: 'backend', value: executeResult.executionBackend ?? '-' },
          { label: 'text', value: executeResult.text ?? '无文本输出' },
        ]
      : []
    : resourceExecuteResult
      ? [
          { label: 'requestId', value: resourceExecuteResult.requestId ?? '-' },
          { label: 'gatewayResourceKey', value: resourceExecuteResult.gatewayResourceKey ?? '-' },
          { label: 'backend', value: resourceExecuteResult.executionBackend ?? '无 backend' },
          { label: 'status', value: String(resourceExecuteResult.statusCode) },
          { label: 'contentType', value: resourceExecuteResult.contentType ?? '未知' },
          { label: 'upstreamPath', value: resourceExecuteResult.upstreamPath ?? '无' },
          { label: 'binaryLength', value: String(resourceExecuteResult.binaryLength ?? '-') },
        ]
      : []

  const applyPreset = (preset: DebugPreset) => {
    setProtocol(preset.protocol)
    setMethod(preset.method)
    setRequestPath(preset.requestPath)
    setRequestedModel(preset.requestedModel)
    setBody(preset.body)
    setFormFields(preset.formFields ?? '{"model":"gpt-4o-mini-transcribe"}')
    setFileRefs(preset.fileRefs ?? '[{"fieldName":"file","fileKey":"file-123"}]')
    setInputError(null)
  }

  const handleClearResults = () => {
    explainMutation.reset()
    executeMutation.reset()
    resourceExecuteMutation.reset()
    setInputError(null)
  }

  const handleExplain = async (event: FormEvent) => {
    event.preventDefault()
    try {
      setInputError(null)
      await explainMutation.mutateAsync()
    } catch (error) {
      setInputError(error instanceof Error ? error.message : '请求体解析失败。')
    }
  }

  const handleExecute = async () => {
    try {
      setInputError(null)
      if (isChatLikePath(requestPath)) {
        await executeMutation.mutateAsync()
        return
      }
      await resourceExecuteMutation.mutateAsync()
    } catch (error) {
      setInputError(error instanceof Error ? error.message : '执行调试失败。')
    }
  }

  const handleFormatBody = () => {
    try {
      setBody(formatJson(body))
      setInputError(null)
    } catch (error) {
      setInputError(error instanceof Error ? error.message : 'request body 格式化失败。')
    }
  }

  const handleFormatFormFields = () => {
    try {
      setFormFields(formatJson(formFields))
      setInputError(null)
    } catch (error) {
      setInputError(error instanceof Error ? error.message : 'formFields 格式化失败。')
    }
  }

  const handleFormatFileRefs = () => {
    try {
      setFileRefs(formatJson(fileRefs))
      setInputError(null)
    } catch (error) {
      setInputError(error instanceof Error ? error.message : 'fileRefs 格式化失败。')
    }
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Translation explain</p>
          <h2>翻译执行解释</h2>
          <p className="empty-state">支持 explain / execute 双模式；资源路径会自动切到 `/admin/resource/execute`。</p>
        </div>
        <div className="detail-grid">
          {requestSummary.map((item) => (
            <div key={item.label} className="detail-card">
              <strong>{item.label}</strong>
              <span>{item.value}</span>
            </div>
          ))}
        </div>
        <p className="accent-copy">
          {activePreset
            ? `${activePreset.label} 预设已载入：${activePreset.description}`
            : '当前为自定义调试请求，可以直接修改 method、path 和 payload。'}
        </p>
        <div className="stack-bar">
          <span>body · {bodyDraft.summary}</span>
          <span>执行目标 · {executeTarget}</span>
          {multipartMode && formFieldsDraft ? <span>formFields · {formFieldsDraft.summary}</span> : null}
          {multipartMode && fileRefsDraft ? <span>fileRefs · {fileRefsDraft.summary}</span> : null}
        </div>
        <div className="preset-strip" aria-label="调试预设">
          {DEBUG_PRESETS.map((preset) => (
            <button
              key={preset.id}
              type="button"
              className={`secondary-button${activePreset?.id === preset.id ? ' active' : ''}`}
              onClick={() => applyPreset(preset)}
              title={preset.description}
            >
              {preset.label}
            </button>
          ))}
        </div>
        <form className="stacked-form" onSubmit={handleExplain}>
          <div className="form-grid">
            <label>
              <span>distributedKeyPrefix</span>
              <input value={distributedKeyPrefix} onChange={(event) => setDistributedKeyPrefix(event.target.value)} />
            </label>
            <label>
              <span>protocol</span>
              <input value={protocol} onChange={(event) => setProtocol(event.target.value)} />
            </label>
            <label>
              <span>method</span>
              <input value={method} onChange={(event) => setMethod(event.target.value.toUpperCase())} />
            </label>
            <label>
              <span>requestPath</span>
              <input value={requestPath} onChange={(event) => setRequestPath(event.target.value)} />
            </label>
            <label>
              <span>requestedModel</span>
              <input value={requestedModel} onChange={(event) => setRequestedModel(event.target.value)} />
            </label>
          </div>
          <label>
            <span>request body</span>
            <textarea value={body} onChange={(event) => setBody(event.target.value)} rows={8} />
          </label>
          <div className="inline-actions">
            <button type="button" className="secondary-button" onClick={handleFormatBody}>格式化 body</button>
            <button
              type="button"
              className="secondary-button"
              onClick={() => applyPreset(activePreset ?? DEBUG_PRESETS[0])}
            >
              恢复示例
            </button>
            <button type="button" className="secondary-button" onClick={handleClearResults}>清空结果</button>
          </div>
          {multipartMode ? (
            <>
              <label>
                <span>formFields JSON</span>
                <textarea value={formFields} onChange={(event) => setFormFields(event.target.value)} rows={5} />
              </label>
              <label>
                <span>fileRefs JSON</span>
                <textarea value={fileRefs} onChange={(event) => setFileRefs(event.target.value)} rows={4} />
              </label>
              <div className="inline-actions">
                <button type="button" className="secondary-button" onClick={handleFormatFormFields}>格式化 formFields</button>
                <button type="button" className="secondary-button" onClick={handleFormatFileRefs}>格式化 fileRefs</button>
              </div>
            </>
          ) : null}
          <div className="inline-actions">
            <button type="submit" disabled={explainMutation.isPending}>查看 Explain</button>
            <button type="button" onClick={handleExecute} disabled={!canExecute || executeMutation.isPending || resourceExecuteMutation.isPending}>
              {isChatLikePath(requestPath) ? '执行 Chat 调试' : '执行资源调试'}
            </button>
          </div>
          {!canExecute ? <p className="empty-state">当前 requestPath 暂不支持执行调试。</p> : null}
          {inputError ? <p className="empty-state">{inputError}</p> : null}
        </form>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Shared plan</p>
          <h3>计划语义</h3>
          <p className="empty-state">Explain 与 Execute 统一读取同一套计划语义字段。</p>
        </div>
        {planSummary.length ? (
          <div className="detail-grid">
            {planSummary.map((item) => (
              <div key={item.label} className="detail-card">
                <strong>{item.label}</strong>
                <span>{item.value}</span>
              </div>
            ))}
          </div>
        ) : (
          <p className="empty-state">查看 explain 或执行调试后，这里会展示 route / render / fallback 语义。</p>
        )}
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Explain result</p>
          <h3>Explain 结果</h3>
        </div>
        {explainResult ? (
          <div className="card-list">
            <div className="detail-grid">
              {explainSummary.map((item) => (
                <div key={item.label} className="detail-card">
                  <strong>{item.label}</strong>
                  <span>{item.value}</span>
                </div>
              ))}
            </div>
            {explainResult.requiredFeatures.length ? (
              <div className="feature-list">
                {explainResult.requiredFeatures.map((feature) => (
                  <div key={feature} className="feature-badge native">
                    <strong>{formatRequiredFeature(feature)}</strong>
                    <small>{resolveFeatureLevel(explainResult.featureLevels, feature)}</small>
                  </div>
                ))}
              </div>
            ) : null}
            <div className="detail-grid">
              {explainResult.blockerReasons.length ? (
                <div className="detail-card">
                  <strong>blockerReasons</strong>
                  <span>{explainResult.blockerReasons.join('；')}</span>
                </div>
              ) : null}
              {explainResult.degradations.length ? (
                <div className="detail-card">
                  <strong>degradations</strong>
                  <span>{explainResult.degradations.join('；')}</span>
                </div>
              ) : null}
              {explainResult.blockers.length ? (
                <div className="detail-card">
                  <strong>blockers</strong>
                  <span>{explainResult.blockers.join('；')}</span>
                </div>
              ) : null}
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(explainResult, null, 2)}</pre>
            </div>
          </div>
        ) : (
          <p className="empty-state">提交请求后查看 explain 结果。</p>
        )}
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Execute result</p>
          <h3>{isChatLikePath(requestPath) ? 'Chat 执行调试' : '资源执行调试'}</h3>
        </div>
        {executeSummary.length ? (
          <div className="detail-grid">
            {executeSummary.map((item) => (
              <div key={item.label} className="detail-card">
                <strong>{item.label}</strong>
                <span>{item.value}</span>
              </div>
            ))}
          </div>
        ) : null}
        {isChatLikePath(requestPath) && executeResult ? (
          <div className="card-list">
            <div className="code-block">
              <pre>{JSON.stringify(executeResult.routeSelection, null, 2)}</pre>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(executeResult.usage, null, 2)}</pre>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(executeResult.toolCalls ?? [], null, 2)}</pre>
            </div>
          </div>
        ) : !isChatLikePath(requestPath) && resourceExecuteResult ? (
          <div className="card-list">
            {resourceExecuteResult.canonicalResponse ? (
              <div className="detail-grid">
                <div className="detail-card">
                  <strong>canonical</strong>
                  <span>responseKind: {resourceExecuteResult.canonicalResponse.responseKind ?? '无'}</span>
                  <span>objectType: {resourceExecuteResult.canonicalResponse.objectType ?? '无'}</span>
                  <span>objectId: {resourceExecuteResult.canonicalResponse.objectId ?? '无'}</span>
                  <span>status: {resourceExecuteResult.canonicalResponse.status ?? '无'}</span>
                  <span>events: {resourceExecuteResult.canonicalResponse.events.length}</span>
                  <span>degradations: {resourceExecuteResult.canonicalResponse.degradations.length}</span>
                </div>
              </div>
            ) : null}
            <div className="code-block">
              <pre>{JSON.stringify(resourceExecuteResult.routeSelection, null, 2)}</pre>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(resourceExecuteResult.plan, null, 2)}</pre>
            </div>
            {resourceExecuteResult.canonicalResponse ? (
              <div className="code-block">
                <pre>{JSON.stringify(resourceExecuteResult.canonicalResponse, null, 2)}</pre>
              </div>
            ) : null}
            <div className="code-block">
              <pre>{JSON.stringify(resourceExecuteResult.responseJson ?? resourceExecuteResult.responseText ?? null, null, 2)}</pre>
            </div>
          </div>
        ) : (
          <p className="empty-state">执行调试后可在这里对照 explain、backend 与真实 route/result。</p>
        )}
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Observability trace</p>
          <h3>联查视图</h3>
          <p className="empty-state">当执行结果里拿到 requestId 后，这里会自动拉取 route / cache / async 资源联查结果。</p>
        </div>
        {!activeRequestId ? (
          <p className="empty-state">执行调试后可在这里查看 observability trace。</p>
        ) : traceQuery.isLoading ? (
          <p className="empty-state">正在加载 trace…</p>
        ) : traceQuery.error ? (
          <p className="empty-state">{traceQuery.error instanceof Error ? traceQuery.error.message : 'trace 查询失败。'}</p>
        ) : traceQuery.data ? (
          <div className="card-list">
            <div className="detail-grid">
              <div className="detail-card">
                <strong>requestId</strong>
                <span>{activeRequestId}</span>
              </div>
              <div className="detail-card">
                <strong>gatewayResourceKey</strong>
                <span>{activeGatewayResourceKey ?? traceQuery.data.requestLog?.gatewayResourceKey ?? '-'}</span>
              </div>
              <div className="detail-card">
                <strong>trace support</strong>
                <span>{traceQuery.data.requestLog?.supportStatus ?? '-'}</span>
              </div>
              <div className="detail-card">
                <strong>trace degradation</strong>
                <span>{traceQuery.data.requestLog?.degradationLevel ?? '-'}</span>
              </div>
            </div>
            <div className="detail-grid">
              <div className="detail-card">
                <strong>routeDecision</strong>
                <span>{traceQuery.data.routeDecision?.selectionSource ?? '无 route decision'}</span>
              </div>
              <div className="detail-card">
                <strong>cacheHits</strong>
                <span>{traceQuery.data.cacheHits.length}</span>
              </div>
              <div className="detail-card">
                <strong>upstream refs</strong>
                <span>{traceQuery.data.upstreamCacheReferences.length}</span>
              </div>
              <div className="detail-card">
                <strong>async resource</strong>
                <span>{traceQuery.data.asyncResourceSummary?.resourceKey ?? '无'}</span>
              </div>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(traceQuery.data.requestLog ?? null, null, 2)}</pre>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(traceQuery.data.routeDecision ?? null, null, 2)}</pre>
            </div>
            {traceQuery.data.asyncResourceSummary ? (
              <div className="detail-grid">
                <div className="detail-card">
                  <strong>async summary</strong>
                  <span>resourceKey: {traceQuery.data.asyncResourceSummary.resourceKey}</span>
                  <span>resourceType: {traceQuery.data.asyncResourceSummary.resourceType ?? '无'}</span>
                  <span>status: {traceQuery.data.asyncResourceSummary.status ?? '无'}</span>
                  <span>upstreamObjectId: {traceQuery.data.asyncResourceSummary.upstreamObjectId ?? '无'}</span>
                </div>
              </div>
            ) : null}
            {traceQuery.data.asyncResourceDetail ? (
              <div className="code-block">
                <pre>{JSON.stringify(traceQuery.data.asyncResourceDetail, null, 2)}</pre>
              </div>
            ) : null}
          </div>
        ) : (
          <p className="empty-state">暂无 trace 数据。</p>
        )}
      </div>
    </section>
  )
}

function parseJsonBody(value: string) {
  try {
    return JSON.parse(value)
  } catch (error) {
    throw new Error(`JSON 解析失败：${error instanceof Error ? error.message : '请求体 JSON 非法。'}`)
  }
}

function safeParseJsonBody(value: string) {
  if (!value.trim()) return {}
  return parseJsonBody(value)
}

function parseJsonObject(value: string) {
  const parsed = safeParseJsonBody(value)
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error('formFields 必须是 JSON object。')
  }
  return parsed
}

function parseJsonArray(value: string) {
  const parsed = safeParseJsonBody(value)
  if (!Array.isArray(parsed)) {
    throw new Error('fileRefs 必须是 JSON array。')
  }
  return parsed
}

function buildMultipartExplainBody(requestedModel: string, formFields: string, fileRefs: string) {
  const payload = parseJsonObject(formFields) as Record<string, unknown>
  if (!payload.model && requestedModel) {
    payload.model = requestedModel
  }
  const refs = parseJsonArray(fileRefs)
  if (refs.length) {
    payload.fileRefs = refs
  }
  return payload
}

function formatJson(value: string) {
  return JSON.stringify(parseJsonBody(value), null, 2)
}

function inspectJsonDraft(value: string, expectedType: 'json' | 'object' | 'array' = 'json') {
  const trimmed = value.trim()
  if (!trimmed) {
    return {
      valid: false,
      summary: '空白',
    }
  }

  try {
    const parsed = JSON.parse(value)
    if (expectedType === 'object') {
      return {
        valid: typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed),
        summary:
          typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
            ? `${Object.keys(parsed).length} 个字段`
            : '需为 object',
      }
    }

    if (expectedType === 'array') {
      return {
        valid: Array.isArray(parsed),
        summary: Array.isArray(parsed) ? `${parsed.length} 个引用` : '需为 array',
      }
    }

    return {
      valid: true,
      summary: Array.isArray(parsed) ? `JSON array · ${parsed.length} 项` : 'JSON 有效',
    }
  } catch {
    return {
      valid: false,
      summary: 'JSON 非法',
    }
  }
}

function resolveFeatureLevel(levels: Record<string, string>, feature: string) {
  return levels[feature] ?? levels[feature.toLowerCase()] ?? levels[feature.toUpperCase()] ?? '-'
}

function formatRequiredFeature(feature: string) {
  return featureLabel(feature.toLowerCase())
}
