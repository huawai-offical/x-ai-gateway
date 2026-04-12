import { type FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type TranslationPlan = {
  executable: boolean
  providerFamily?: string | null
  siteProfileId?: number | null
  executionKind?: string | null
  capabilityLevel?: string | null
  lossReasons: string[]
  blockedReasons: string[]
  authStrategy?: string | null
  pathStrategy?: string | null
  errorSchemaStrategy?: string | null
  requestMapping: Record<string, unknown>
  responseMapping: Record<string, unknown>
}

export function TranslationDebugPage() {
  const [distributedKeyPrefix, setDistributedKeyPrefix] = useState('sk-gw-test')
  const [protocol, setProtocol] = useState('openai')
  const [requestPath, setRequestPath] = useState('/v1/chat/completions')
  const [requestedModel, setRequestedModel] = useState('gpt-4o')
  const [body, setBody] = useState('{"model":"gpt-4o","messages":[{"role":"user","content":"hello"}]}')

  const explainMutation = useMutation({
    mutationFn: () =>
      apiRequest<TranslationPlan>('/admin/translation/explain', {
        method: 'POST',
        body: JSON.stringify({
          distributedKeyPrefix,
          protocol,
          requestPath,
          requestedModel,
          body: JSON.parse(body),
        }),
      }),
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    explainMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Translation explain</p>
          <h2>翻译执行解释</h2>
        </div>
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={distributedKeyPrefix} onChange={(e) => setDistributedKeyPrefix(e.target.value)} placeholder="distributedKeyPrefix" />
          <input value={protocol} onChange={(e) => setProtocol(e.target.value)} placeholder="protocol" />
          <input value={requestPath} onChange={(e) => setRequestPath(e.target.value)} placeholder="requestPath" />
          <input value={requestedModel} onChange={(e) => setRequestedModel(e.target.value)} placeholder="requestedModel" />
          <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={6} />
          <button type="submit">Explain</button>
        </form>
      </div>
      <div className="panel">
        {explainMutation.data ? (
          <div className="card-list">
            <div className="detail-card">
              <strong>{String(explainMutation.data.executable)}</strong>
              <span>{explainMutation.data.providerFamily ?? '-'}</span>
              <span>{explainMutation.data.executionKind ?? '-'}</span>
              <span>{explainMutation.data.capabilityLevel ?? '-'}</span>
              <span>{explainMutation.data.authStrategy ?? '-'}</span>
              <span>{explainMutation.data.pathStrategy ?? '-'}</span>
              <span>{explainMutation.data.errorSchemaStrategy ?? '-'}</span>
            </div>
            <pre className="detail-card">{JSON.stringify(explainMutation.data.requestMapping, null, 2)}</pre>
            <pre className="detail-card">{JSON.stringify(explainMutation.data.responseMapping, null, 2)}</pre>
            {explainMutation.data.lossReasons.length ? (
              <pre className="detail-card">{JSON.stringify(explainMutation.data.lossReasons, null, 2)}</pre>
            ) : null}
            {explainMutation.data.blockedReasons.length ? (
              <pre className="detail-card">{JSON.stringify(explainMutation.data.blockedReasons, null, 2)}</pre>
            ) : null}
          </div>
        ) : (
          <p className="empty-state">提交请求后查看 explain 结果。</p>
        )}
      </div>
    </section>
  )
}
