import { type FormEvent, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedQuery } from '../../lib/typed-react-query'
import { type TraceLookupResponse } from '../provider-sites/types'

const TRACE_TABS = ['overview', 'graph', 'entity', 'actions', 'raw'] as const
type TraceTab = (typeof TRACE_TABS)[number]

export function TracesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [requestId, setRequestId] = useState(searchParams.get('requestId') ?? '')
  const [gatewayResourceKey, setGatewayResourceKey] = useState(searchParams.get('gatewayResourceKey') ?? '')
  const [upstreamObjectId, setUpstreamObjectId] = useState(searchParams.get('upstreamObjectId') ?? '')
  const [activeTab, setActiveTab] = useState<TraceTab>('overview')

  const submittedRequestId = searchParams.get('requestId') ?? ''
  const submittedGatewayResourceKey = searchParams.get('gatewayResourceKey') ?? ''
  const submittedUpstreamObjectId = searchParams.get('upstreamObjectId') ?? ''
  const providerType = searchParams.get('providerType') ?? ''
  const requestPath = searchParams.get('requestPath') ?? ''

  const query = useTypedQuery<TraceLookupResponse>({
    queryKey: ['trace-lookup', submittedRequestId, submittedGatewayResourceKey, submittedUpstreamObjectId],
    queryFn: () => apiRequest<TraceLookupResponse>(buildTraceLookupUrl(submittedRequestId, submittedGatewayResourceKey, submittedUpstreamObjectId)),
    enabled: Boolean(submittedRequestId || submittedGatewayResourceKey || submittedUpstreamObjectId),
  })

  const trace = query.data?.trace
  const steps = useMemo(
    () => [
      { title: 'Request log', value: trace?.requestLog?.requestId ?? '未命中', summary: trace?.requestLog?.responseKind ?? trace?.requestLog?.supportStatus ?? '-' },
      { title: 'Route decision', value: trace?.routeDecision?.selectionSource ?? '未命中', summary: trace?.routeDecision?.degradationLevel ?? '-' },
      { title: 'Cache', value: String(trace?.cacheHits.length ?? 0), summary: 'cache hits' },
      { title: 'Async resource', value: trace?.asyncResourceSummary?.resourceKey ?? '未命中', summary: trace?.asyncResourceSummary?.status ?? '-' },
    ],
    [trace],
  )

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const next = new URLSearchParams()
    if (requestId.trim()) next.set('requestId', requestId.trim())
    if (gatewayResourceKey.trim()) next.set('gatewayResourceKey', gatewayResourceKey.trim())
    if (upstreamObjectId.trim()) next.set('upstreamObjectId', upstreamObjectId.trim())
    if (providerType) next.set('providerType', providerType)
    if (requestPath) next.set('requestPath', requestPath)
    setSearchParams(next)
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Trace workbench</p>
          <h2>联查工作台</h2>
          <p className="empty-state">按 requestId、gatewayResourceKey、upstreamObjectId 串起 request、route、cache、async resource 和上游线索。</p>
        </div>
        <form className="stacked-form" onSubmit={handleSubmit}>
          <div className="form-grid">
            <label>
              <span>requestId</span>
              <input value={requestId} onChange={(event) => setRequestId(event.target.value)} placeholder="req-..." />
            </label>
            <label>
              <span>gatewayResourceKey</span>
              <input value={gatewayResourceKey} onChange={(event) => setGatewayResourceKey(event.target.value)} placeholder="file_123" />
            </label>
            <label>
              <span>upstreamObjectId</span>
              <input value={upstreamObjectId} onChange={(event) => setUpstreamObjectId(event.target.value)} placeholder="upstream-..." />
            </label>
          </div>
          <div className="inline-actions">
            <button type="submit">查询 Trace</button>
            <Link className="action-link" to="/traces">清空</Link>
          </div>
        </form>
        {(providerType || requestPath) ? (
          <div className="stack-bar">
            {providerType ? <span>providerType · {providerType}</span> : null}
            {requestPath ? <span>requestPath · {requestPath}</span> : null}
          </div>
        ) : null}
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Trace tabs</p>
          <h3>工作台视图</h3>
        </div>
        <div className="inline-actions">
          {TRACE_TABS.map((tab) => (
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

        {!submittedRequestId && !submittedGatewayResourceKey && !submittedUpstreamObjectId ? (
          <p className="empty-state">输入至少一个联查锚点后开始查看 trace。</p>
        ) : query.isPending ? (
          <p className="empty-state">正在加载 trace…</p>
        ) : query.error ? (
          <p className="empty-state">{query.error instanceof Error ? query.error.message : 'trace 查询失败。'}</p>
        ) : query.data ? (
          <>
            {activeTab === 'overview' ? (
              <div className="detail-grid">
                <div className="detail-card">
                  <strong>requestId</strong>
                  <span>{query.data.requestId ?? trace?.requestLog?.requestId ?? '-'}</span>
                </div>
                <div className="detail-card">
                  <strong>gatewayResourceKey</strong>
                  <span>{query.data.gatewayResourceKey ?? trace?.requestLog?.gatewayResourceKey ?? '-'}</span>
                </div>
                <div className="detail-card">
                  <strong>supportStatus</strong>
                  <span>{trace?.requestLog?.supportStatus ?? trace?.routeDecision?.supportStatus ?? '-'}</span>
                </div>
                <div className="detail-card">
                  <strong>degradationLevel</strong>
                  <span>{trace?.requestLog?.degradationLevel ?? trace?.routeDecision?.degradationLevel ?? '-'}</span>
                </div>
                <div className="detail-card">
                  <strong>responseKind</strong>
                  <span>{trace?.requestLog?.responseKind ?? '-'}</span>
                </div>
                <div className="detail-card">
                  <strong>object</strong>
                  <span>{trace?.requestLog?.responseObjectType ?? '-'} / {trace?.requestLog?.responseObjectId ?? '-'}</span>
                </div>
              </div>
            ) : null}

            {activeTab === 'graph' ? (
              <div className="card-list">
                {steps.map((step) => (
                  <div key={step.title} className="detail-card">
                    <strong>{step.title}</strong>
                    <span>{step.value}</span>
                    <span>{step.summary}</span>
                  </div>
                ))}
              </div>
            ) : null}

            {activeTab === 'entity' ? (
              <div className="card-list">
                {query.data.matches.map((match) => (
                  <div key={match.requestId} className="detail-card">
                    <strong>{match.requestId}</strong>
                    <span>gatewayResourceKey: {String(match.gatewayResourceKey ?? '-')}</span>
                    <span>objectMode: {String(match.objectMode ?? '-')}</span>
                    <span>status: {String(match.responseStatus ?? '-')}</span>
                  </div>
                ))}
                {trace?.asyncResourceSummary ? (
                  <div className="detail-card">
                    <strong>Async resource</strong>
                    <span>{trace.asyncResourceSummary.resourceKey}</span>
                    <span>{trace.asyncResourceSummary.resourceType ?? '-'} / {trace.asyncResourceSummary.status ?? '-'}</span>
                    <span>upstreamObjectId: {trace.asyncResourceSummary.upstreamObjectId ?? '-'}</span>
                  </div>
                ) : null}
                {!query.data.matches.length && !trace?.asyncResourceSummary ? (
                  <p className="empty-state">当前 trace 没有额外实体摘要。</p>
                ) : null}
              </div>
            ) : null}

            {activeTab === 'actions' ? (
              <div className="card-list">
                <div className="detail-card">
                  <strong>去 Workbench</strong>
                  <span>带 requestPath 和 requestId 回到执行工作台复盘。</span>
                  <div className="inline-actions">
                    <Link
                      className="action-link"
                      to={`/workbench?requestId=${encodeURIComponent(query.data.requestId ?? '')}${requestPath ? `&requestPath=${encodeURIComponent(requestPath)}` : ''}`}
                    >
                      打开 Workbench
                    </Link>
                  </div>
                </div>
                <div className="detail-card">
                  <strong>去 Incidents</strong>
                  <span>按 requestId 或对象锚点回到事件指挥台。</span>
                  <div className="inline-actions">
                    <Link
                      className="action-link"
                      to={`/incidents?entityType=REQUEST&entityRef=${encodeURIComponent(query.data.requestId ?? submittedRequestId ?? '')}`}
                    >
                      打开 Incident
                    </Link>
                  </div>
                </div>
                {providerType ? (
                  <div className="detail-card">
                    <strong>回站点档案</strong>
                    <span>按 providerType / requestPath 继续定位站点限制与 blocker。</span>
                    <div className="inline-actions">
                      <Link
                        className="action-link"
                        to={`/provider-sites?siteKind=${encodeURIComponent(providerType)}`}
                      >
                        查看站点列表
                      </Link>
                    </div>
                  </div>
                ) : null}
              </div>
            ) : null}

            {activeTab === 'raw' ? (
              <div className="code-block">
                <pre>{JSON.stringify(query.data, null, 2)}</pre>
              </div>
            ) : null}
          </>
        ) : (
          <p className="empty-state">没有查到对应 trace。</p>
        )}
      </div>
    </section>
  )
}

function buildTraceLookupUrl(requestId?: string, gatewayResourceKey?: string, upstreamObjectId?: string) {
  const params = new URLSearchParams()
  if (requestId) params.set('requestId', requestId)
  if (gatewayResourceKey) params.set('gatewayResourceKey', gatewayResourceKey)
  if (upstreamObjectId) params.set('upstreamObjectId', upstreamObjectId)
  return `/admin/traces/lookup?${params.toString()}`
}

function tabLabel(tab: TraceTab) {
  switch (tab) {
    case 'overview':
      return 'Overview'
    case 'graph':
      return 'Trace graph'
    case 'entity':
      return 'Entity'
    case 'actions':
      return 'Actions'
    case 'raw':
      return 'Raw'
  }
}
