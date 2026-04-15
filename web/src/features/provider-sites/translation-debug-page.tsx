import { type FormEvent, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedMutation } from '../../lib/typed-react-query'
import {
  isChatLikePath,
  isDebugExecutablePath,
  isMultipartResourcePath,
  type AdminChatExecuteResponse,
  type AdminResourceExecuteResponse,
  type TranslationPlan,
} from './types'

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

  const canExecute = isDebugExecutablePath(requestPath)
  const explainResult = explainMutation.data
  const executeResult = executeMutation.data
  const resourceExecuteResult = resourceExecuteMutation.data

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Translation explain</p>
          <h2>翻译执行解释</h2>
          <p className="empty-state">支持 explain / execute 双模式；资源路径会自动切到 `/admin/resource/execute`。</p>
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
            <button type="submit" disabled={explainMutation.isPending}>查看 Explain</button>
            <button type="button" onClick={handleExecute} disabled={!canExecute || executeMutation.isPending}>
              {isChatLikePath(requestPath) ? '执行 Chat 调试' : '执行资源调试'}
            </button>
          </div>
          {!canExecute ? <p className="empty-state">当前 requestPath 暂不支持执行调试。</p> : null}
          {inputError ? <p className="empty-state">{inputError}</p> : null}
        </form>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Explain result</p>
          <h3>Explain 结果</h3>
        </div>
        {explainResult ? (
          <div className="card-list">
            <div className="detail-card">
              <strong>{String(explainResult.executable)}</strong>
              <span>{explainResult.executionKind ?? '-'}</span>
              <span>backend: {explainResult.executionBackend ?? '-'}</span>
              <span>objectMode: {explainResult.objectMode ?? '-'}</span>
              <span>protocol: {explainResult.ingressProtocol ?? '-'}</span>
              <span>resource / operation: {explainResult.resourceType ?? '-'} / {explainResult.operation ?? '-'}</span>
              <span>execution / render / overall: {explainResult.executionCapabilityLevel ?? '-'} / {explainResult.renderCapabilityLevel ?? '-'} / {explainResult.overallCapabilityLevel ?? '-'}</span>
              <span>resolvedModel: {explainResult.resolvedModel ?? '-'}</span>
            </div>
            <div className="detail-grid">
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
        {isChatLikePath(requestPath) && executeResult ? (
          <div className="card-list">
            <div className="detail-card">
              <strong>{executeResult.requestId}</strong>
              <span>backend: {executeResult.executionBackend ?? '无'}</span>
              <span>{executeResult.text ?? '无文本输出'}</span>
            </div>
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
            <div className="detail-card">
              <strong>{resourceExecuteResult.executionBackend ?? '无 backend'}</strong>
              <span>status: {resourceExecuteResult.statusCode}</span>
              <span>contentType: {resourceExecuteResult.contentType ?? '未知'}</span>
              <span>upstreamPath: {resourceExecuteResult.upstreamPath ?? '无'}</span>
              <span>objectMode: {resourceExecuteResult.objectMode ?? '无'}</span>
              {typeof resourceExecuteResult.binaryLength === 'number' ? <span>binaryLength: {resourceExecuteResult.binaryLength}</span> : null}
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(resourceExecuteResult.routeSelection, null, 2)}</pre>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(resourceExecuteResult.plan, null, 2)}</pre>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(resourceExecuteResult.responseJson ?? resourceExecuteResult.responseText ?? null, null, 2)}</pre>
            </div>
          </div>
        ) : (
          <p className="empty-state">执行调试后可在这里对照 explain、backend 与真实 route/result。</p>
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
