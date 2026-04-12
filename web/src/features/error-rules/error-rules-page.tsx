import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type ErrorRule = {
  id: number
  action: string
  protocol?: string | null
  requestPath?: string | null
  errorCode?: string | null
  rewriteCode?: string | null
}

export function ErrorRulesPage() {
  const queryClient = useQueryClient()
  const [requestPath, setRequestPath] = useState('/v1/chat/completions')
  const [errorCode, setErrorCode] = useState('UPSTREAM_ERROR')
  const [action, setAction] = useState('REWRITE')

  const rulesQuery = useQuery({
    queryKey: ['error-rules'],
    queryFn: () => apiRequest<ErrorRule[]>('/admin/error-rules'),
  })
  const previewQuery = useMutation({
    mutationFn: () =>
      apiRequest<{ matchedRules: ErrorRule[] }>('/admin/error-rules/preview', {
        method: 'POST',
        body: JSON.stringify({
          protocol: 'openai',
          requestPath,
          httpStatus: 500,
          errorCode,
          matchScope: 'UPSTREAM',
        }),
      }),
  })
  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/error-rules', {
        method: 'POST',
        body: JSON.stringify({
          enabled: true,
          priority: 100,
          protocol: 'openai',
          requestPath,
          errorCode,
          matchScope: 'UPSTREAM',
          action,
          rewriteStatus: 502,
          rewriteCode: action === 'REWRITE' ? 'REWRITTEN_ERROR' : 'BLOCKED_BY_RULE',
          rewriteMessage: '规则命中后的错误输出',
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['error-rules'] }),
  })

  const handleCreate = (event: FormEvent) => {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Error rules</p>
          <h2>结构化错误规则中心</h2>
        </div>
        <form className="inline-form" onSubmit={handleCreate}>
          <input value={requestPath} onChange={(e) => setRequestPath(e.target.value)} placeholder="request path" />
          <input value={errorCode} onChange={(e) => setErrorCode(e.target.value)} placeholder="error code" />
          <select value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="REWRITE">REWRITE</option>
            <option value="BLOCK">BLOCK</option>
            <option value="DOWNGRADE">DOWNGRADE</option>
            <option value="PASSTHROUGH">PASSTHROUGH</option>
          </select>
          <button type="submit">创建规则</button>
          <button type="button" onClick={() => previewQuery.mutate()}>Preview</button>
        </form>
      </div>
      <div className="panel">
        <div className="card-list">
          {rulesQuery.data?.map((item: ErrorRule) => (
            <div key={item.id} className="detail-card">
              <strong>{item.action}</strong>
              <span>{item.requestPath ?? '-'}</span>
              <span>{item.errorCode ?? '-'}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="panel">
        <div className="card-list">
          {previewQuery.data?.matchedRules?.map((item: ErrorRule) => (
            <div key={item.id} className="detail-card">
              <strong>match #{item.id}</strong>
              <span>{item.action}</span>
              <span>{item.rewriteCode ?? '-'}</span>
            </div>
          ))}
          {!previewQuery.data && <p className="empty-state">点击 Preview 查看命中结果。</p>}
        </div>
      </div>
    </section>
  )
}
