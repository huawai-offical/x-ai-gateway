import { type FormEvent, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedMutation } from '../../lib/typed-react-query'
import {
  type CapabilityResolution,
  featureLabel,
  isChatLikePath,
  resolutionTone,
  type AdminChatExecuteResponse,
  type TranslationPlan,
} from './types'

type ExecutePayload = {
  distributedKeyPrefix: string
  protocol: string
  requestPath: string
  requestedModel: string
  systemPrompt?: string
  userPrompt: string
  temperature?: number
  maxTokens?: number
}

export function TranslationDebugPage() {
  const [searchParams] = useSearchParams()
  const [distributedKeyPrefix, setDistributedKeyPrefix] = useState(searchParams.get('distributedKeyPrefix') ?? 'sk-gw-test')
  const [protocol, setProtocol] = useState(searchParams.get('protocol') ?? 'openai')
  const [requestPath, setRequestPath] = useState(searchParams.get('requestPath') ?? '/v1/chat/completions')
  const [requestedModel, setRequestedModel] = useState(searchParams.get('requestedModel') ?? 'gpt-4o')
  const [body, setBody] = useState(searchParams.get('body') ?? '{"model":"gpt-4o","messages":[{"role":"user","content":"hello"}]}')
  const [inputError, setInputError] = useState<string | null>(null)

  const explainMutation = useTypedMutation<TranslationPlan, void>({
    mutationFn: async () => {
      const parsedBody = parseJsonBody(body)
      return apiRequest<TranslationPlan>('/admin/translation/explain', {
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

  const executeMutation = useTypedMutation<AdminChatExecuteResponse, void>({
    mutationFn: async () => {
      const payload = buildExecutePayload({
        distributedKeyPrefix,
        protocol,
        requestPath,
        requestedModel,
        body,
      })
      return apiRequest<AdminChatExecuteResponse>('/admin/chat/execute', {
        method: 'POST',
        body: JSON.stringify(payload),
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
      await executeMutation.mutateAsync()
    } catch (error) {
      setInputError(error instanceof Error ? error.message : '执行调试失败。')
    }
  }

  const canExecute = isChatLikePath(requestPath)
  const explainResult = explainMutation.data
  const executeResult = executeMutation.data

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Translation explain</p>
          <h2>翻译执行解释</h2>
          <p className="empty-state">支持 explain / execute 双模式；非 chat 资源本轮保持 explain-only。</p>
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
            <button type="submit" disabled={explainMutation.isPending}>查看 Explain</button>
            <button type="button" onClick={handleExecute} disabled={!canExecute || executeMutation.isPending}>
              执行 Chat 调试
            </button>
            {explainResult?.siteProfileId ? (
              <Link className="action-link" to={`/provider-sites/${explainResult.siteProfileId}`}>
                跳转到站点详情
              </Link>
            ) : null}
          </div>
          {!canExecute ? <p className="empty-state">当前 requestPath 不属于 chat / responses，仅支持 explain。</p> : null}
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
              <span>{explainResult.providerFamily ?? '-'}</span>
              <span>{explainResult.executionKind ?? '-'}</span>
              <span>selection: {explainResult.selectionSource ?? '-'}</span>
              <span>siteProfileId: {explainResult.siteProfileId ?? '-'}</span>
              <span>declared / implemented / effective: {explainResult.overallDeclaredLevel ?? '-'} / {explainResult.overallImplementedLevel ?? '-'} / {explainResult.overallEffectiveLevel ?? '-'}</span>
              <span>auth / path / error: {explainResult.authStrategy ?? '-'} / {explainResult.pathStrategy ?? '-'} / {explainResult.errorSchemaStrategy ?? '-'}</span>
              <span>upstreamObjectMode: {explainResult.upstreamObjectMode ?? '-'}</span>
            </div>
            <div className="feature-list">
              {(Object.entries(explainResult.featureResolutions ?? {}) as Array<[string, CapabilityResolution]>).map(([feature, resolution]) => (
                <button key={feature} type="button" className={`feature-badge ${resolutionTone(resolution)}`}>
                  {featureLabel(feature)}
                  <small>{resolution.declaredLevel ?? '-'}/{resolution.implementedLevel ?? '-'}/{resolution.effectiveLevel ?? '-'}</small>
                </button>
              ))}
            </div>
            <div className="detail-grid">
              {explainResult.lossReasons.length ? (
                <div className="detail-card">
                  <strong>lossReasons</strong>
                  <span>{explainResult.lossReasons.join('；')}</span>
                </div>
              ) : null}
              {explainResult.blockedReasons.length ? (
                <div className="detail-card">
                  <strong>blockedReasons</strong>
                  <span>{explainResult.blockedReasons.join('；')}</span>
                </div>
              ) : null}
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(explainResult.requestMapping, null, 2)}</pre>
            </div>
            <div className="code-block">
              <pre>{JSON.stringify(explainResult.responseMapping, null, 2)}</pre>
            </div>
          </div>
        ) : (
          <p className="empty-state">提交请求后查看 explain 结果。</p>
        )}
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Execute result</p>
          <h3>Chat 执行调试</h3>
        </div>
        {executeResult ? (
          <div className="card-list">
            <div className="detail-card">
              <strong>{executeResult.requestId}</strong>
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
        ) : (
          <p className="empty-state">执行 chat 调试后可在这里对照 explain 与真实 route/result。</p>
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

function buildExecutePayload(args: {
  distributedKeyPrefix: string
  protocol: string
  requestPath: string
  requestedModel: string
  body: string
}): ExecutePayload {
  const parsedBody = parseJsonBody(args.body) as Record<string, unknown>
  const messages = extractMessages(parsedBody)
  const systemPrompt = messages.filter((item) => item.role === 'system').map((item) => item.text).join('\n\n').trim()
  const userPrompt = messages.filter((item) => item.role === 'user').map((item) => item.text).join('\n\n').trim()

  if (!userPrompt) {
    throw new Error('当前请求体无法提取 user prompt，暂不支持执行调试。')
  }

  return {
    distributedKeyPrefix: args.distributedKeyPrefix,
    protocol: args.protocol,
    requestPath: args.requestPath,
    requestedModel: args.requestedModel,
    systemPrompt: systemPrompt || undefined,
    userPrompt,
    temperature: toNumber(parsedBody.temperature),
    maxTokens: toNumber(parsedBody.max_output_tokens ?? parsedBody.max_completion_tokens ?? parsedBody.max_tokens),
  }
}

function extractMessages(body: Record<string, unknown>) {
  if (Array.isArray(body.messages)) {
    return body.messages
      .map((message) => normaliseMessage(message))
      .filter((message): message is { role: string; text: string } => Boolean(message))
  }

  if (typeof body.input === 'string') {
    return [{ role: 'user', text: body.input }]
  }

  if (Array.isArray(body.input)) {
    return body.input
      .flatMap((item) => {
        if (typeof item === 'string') {
          return [{ role: 'user', text: item }]
        }
        const direct = normaliseMessage(item)
        if (direct) return [direct]
        if (isRecord(item) && Array.isArray(item.content)) {
          const text = extractText(item.content)
          if (text) {
            return [{ role: String(item.role ?? 'user'), text }]
          }
        }
        return []
      })
  }

  return []
}

function normaliseMessage(message: unknown) {
  if (!isRecord(message)) return null
  const role = typeof message.role === 'string' ? message.role : 'user'
  const text = extractText(message.content)
  return text ? { role, text } : null
}

function extractText(content: unknown): string {
  if (typeof content === 'string') return content
  if (!Array.isArray(content)) return ''
  return content
    .flatMap((item) => {
      if (typeof item === 'string') return [item]
      if (!isRecord(item)) return []
      if (typeof item.text === 'string') return [item.text]
      if (item.type === 'text' && typeof item.text === 'string') return [item.text]
      if (item.type === 'input_text' && typeof item.text === 'string') return [item.text]
      if (isRecord(item.text) && typeof item.text.value === 'string') return [item.text.value]
      return []
    })
    .join('\n')
    .trim()
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function toNumber(value: unknown) {
  return typeof value === 'number' ? value : undefined
}
