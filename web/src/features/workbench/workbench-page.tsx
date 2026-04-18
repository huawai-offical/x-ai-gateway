import { type FormEvent, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
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
} from '../provider-sites/types'

type WorkbenchTab = 'request' | 'plan' | 'execute' | 'trace' | 'raw'

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

export function WorkbenchPage() {
  const [searchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState<WorkbenchTab>('request')
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
    onSuccess: () => setActiveTab('plan'),
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
    onSuccess: () => setActiveTab('execute'),
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
    onSuccess: () => setActiveTab('execute'),
  })

  const explainResult = explainMutation.data
  const executeResult = executeMutation.data
  const resourceExecuteResult = resourceExecuteMutation.data
  const activePlan = resourceExecuteResult?.plan ?? executeResult?.plan ?? explainResult ?? null
  const activeRequestId = resourceExecuteResult?.requestId ?? executeResult?.requestId ?? searchParams.get('requestId') ?? null
  const activeGatewayResourceKey = resourceExecuteResult?.gatewayResourceKey ?? null

  const traceQuery = useTypedQuery<ObservabilityTraceResponse>({
    queryKey: ['workbench-trace', activeRequestId],
    queryFn: () => apiRequest<ObservabilityTraceResponse>(`/admin/observability/traces/${encodeURIComponent(activeRequestId ?? '')}`),
    enabled: Boolean(activeRequestId),
  })

  const canExecute = isDebugExecutablePath(requestPath)
  const executeTarget = canExecute
    ? isChatLikePath(requestPath)
      ? '/admin/chat/execute'
      : '/admin/resource/execute'
    : '当前路径不可执行'

  const activePreset = useMemo(
    () => DEBUG_PRESETS.find((preset) => preset.method === method && preset.requestPath === requestPath) ?? null,
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
      { label: 'model', value: requestedModel },
    ],
    [activePreset?.label, executeTarget, method, multipartMode, requestPath, requestedModel],
  )

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
    setActiveTab('request')
  }

  const handleClearResults = () => {
    explainMutation.reset()
    executeMutation.reset()
    resourceExecuteMutation.reset()
    setInputError(null)
    setActiveTab('request')
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

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Translation workbench</p>
          <h2>执行工作台</h2>
          <p className="empty-state">默认阅读路径固定为 Request → Plan → Execute → Trace → Raw，原始 JSON 退到末级视图。</p>
        </div>
        <div className="inline-actions">
          {(['request', 'plan', 'execute', 'trace', 'raw'] as WorkbenchTab[]).map((tab) => (
            <button
              key={tab}
              type="button"
              className={`secondary-button${activeTab === tab ? ' active' : ''}`}
              onClick={() => setActiveTab(tab)}
            >
              {tabLabel(tab)}
            </button>
          ))}
        </div>
      </div>

      {activeTab === 'request' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Request</p>
            <h3>请求输入</h3>
          </div>
          <div className="detail-grid">
            {requestSummary.map((item) => (
              <div key={item.label} className="detail-card">
                <strong>{item.label}</strong>
                <span>{item.value}</span>
              </div>
            ))}
          </div>
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
              </>
            ) : null}
            <div className="inline-actions">
              <button type="submit" disabled={explainMutation.isPending}>查看 Plan</button>
              <button type="button" onClick={handleExecute} disabled={!canExecute || executeMutation.isPending || resourceExecuteMutation.isPending}>
                {isChatLikePath(requestPath) ? '执行 Chat 调试' : '执行资源调试'}
              </button>
              <button type="button" className="secondary-button" onClick={handleClearResults}>清空结果</button>
            </div>
            {!canExecute ? <p className="empty-state">当前 requestPath 暂不支持执行调试。</p> : null}
            {inputError ? <p className="empty-state">{inputError}</p> : null}
          </form>
        </div>
      ) : null}

      {activeTab === 'plan' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Plan</p>
            <h3>计划语义</h3>
            <p className="empty-state">Explain 与 Execute 共用同一套 route / render / fallback 语义。</p>
          </div>
          {planSummary.length ? (
            <>
              <div className="detail-grid">
                {planSummary.map((item) => (
                  <div key={item.label} className="detail-card">
                    <strong>{item.label}</strong>
                    <span>{item.value}</span>
                  </div>
                ))}
              </div>
              {activePlan?.requiredFeatures?.length ? (
                <div className="feature-list">
                  {activePlan.requiredFeatures.map((feature) => (
                    <div key={feature} className="feature-badge native">
                      <strong>{formatRequiredFeature(feature)}</strong>
                      <small>{resolveFeatureLevel(activePlan.featureLevels, feature)}</small>
                    </div>
                  ))}
                </div>
              ) : null}
              <div className="detail-grid">
                {(activePlan?.blockerReasons ?? []).length ? (
                  <div className="detail-card">
                    <strong>blockerReasons</strong>
                    <span>{activePlan?.blockerReasons.join('；')}</span>
                  </div>
                ) : null}
                {(activePlan?.degradations ?? []).length ? (
                  <div className="detail-card">
                    <strong>degradations</strong>
                    <span>{activePlan?.degradations.join('；')}</span>
                  </div>
                ) : null}
                {(activePlan?.blockers ?? []).length ? (
                  <div className="detail-card">
                    <strong>blockers</strong>
                    <span>{activePlan?.blockers.join('；')}</span>
                  </div>
                ) : null}
              </div>
            </>
          ) : (
            <p className="empty-state">先在 Request 页执行 explain 或 execute，这里才会出现 plan。</p>
          )}
        </div>
      ) : null}

      {activeTab === 'execute' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Execute</p>
            <h3>执行结果</h3>
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
          ) : (
            <p className="empty-state">先执行调试，再在这里查看结果摘要。</p>
          )}
          {isChatLikePath(requestPath) && executeResult ? (
            <div className="card-list">
              <div className="detail-card">
                <strong>Chat output</strong>
                <span>{executeResult.text ?? '无文本输出'}</span>
              </div>
              <div className="detail-card">
                <strong>Trace anchor</strong>
                <span>{executeResult.requestId}</span>
              </div>
            </div>
          ) : !isChatLikePath(requestPath) && resourceExecuteResult ? (
            <div className="card-list">
              {resourceExecuteResult.canonicalResponse ? (
                <div className="detail-card">
                  <strong>Canonical summary</strong>
                  <span>responseKind: {resourceExecuteResult.canonicalResponse.responseKind ?? '无'}</span>
                  <span>objectType: {resourceExecuteResult.canonicalResponse.objectType ?? '无'}</span>
                  <span>objectId: {resourceExecuteResult.canonicalResponse.objectId ?? '无'}</span>
                  <span>status: {resourceExecuteResult.canonicalResponse.status ?? '无'}</span>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {activeTab === 'trace' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Trace</p>
            <h3>联查视图</h3>
          </div>
          {!activeRequestId ? (
            <p className="empty-state">执行调试后会自动拿到 requestId，并在这里展示 trace。</p>
          ) : traceQuery.isLoading ? (
            <p className="empty-state">正在加载 trace…</p>
          ) : traceQuery.error ? (
            <p className="empty-state">{traceQuery.error instanceof Error ? traceQuery.error.message : 'trace 查询失败。'}</p>
          ) : traceQuery.data ? (
            <>
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
                  <strong>routeDecision</strong>
                  <span>{traceQuery.data.routeDecision?.selectionSource ?? '无'}</span>
                </div>
                <div className="detail-card">
                  <strong>async resource</strong>
                  <span>{traceQuery.data.asyncResourceSummary?.resourceKey ?? '无'}</span>
                </div>
              </div>
              {traceQuery.data.asyncResourceSummary ? (
                <div className="detail-grid">
                  <div className="detail-card">
                    <strong>resourceType</strong>
                    <span>{traceQuery.data.asyncResourceSummary.resourceType ?? '-'}</span>
                  </div>
                  <div className="detail-card">
                    <strong>resourceStatus</strong>
                    <span>{traceQuery.data.asyncResourceSummary.status ?? '-'}</span>
                  </div>
                  <div className="detail-card">
                    <strong>upstreamObjectId</strong>
                    <span>{traceQuery.data.asyncResourceSummary.upstreamObjectId ?? '-'}</span>
                  </div>
                </div>
              ) : null}
              <div className="inline-actions">
                <Link className="action-link" to={`/traces?requestId=${encodeURIComponent(activeRequestId)}`}>打开 Trace Workbench</Link>
              </div>
            </>
          ) : (
            <p className="empty-state">暂无 trace 数据。</p>
          )}
        </div>
      ) : null}

      {activeTab === 'raw' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Raw</p>
            <h3>原始明细</h3>
          </div>
          <div className="card-list">
            {explainResult ? (
              <div className="code-block">
                <pre>{JSON.stringify(explainResult, null, 2)}</pre>
              </div>
            ) : null}
            {executeResult ? (
              <div className="code-block">
                <pre>{JSON.stringify(executeResult, null, 2)}</pre>
              </div>
            ) : null}
            {resourceExecuteResult ? (
              <div className="code-block">
                <pre>{JSON.stringify(resourceExecuteResult, null, 2)}</pre>
              </div>
            ) : null}
            {traceQuery.data ? (
              <div className="code-block">
                <pre>{JSON.stringify(traceQuery.data, null, 2)}</pre>
              </div>
            ) : null}
            {!explainResult && !executeResult && !resourceExecuteResult && !traceQuery.data ? (
              <p className="empty-state">还没有可展示的 raw 数据。</p>
            ) : null}
          </div>
        </div>
      ) : null}
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

function tabLabel(tab: WorkbenchTab) {
  switch (tab) {
    case 'request':
      return 'Request'
    case 'plan':
      return 'Plan'
    case 'execute':
      return 'Execute'
    case 'trace':
      return 'Trace'
    case 'raw':
      return 'Raw'
  }
}
