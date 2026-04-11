import { useMutation } from '@tanstack/react-query'
import { useParams, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

export function OauthConnectPage() {
  const { provider = 'openai_oauth' } = useParams()
  const [searchParams] = useSearchParams()
  const poolId = Number(searchParams.get('poolId') ?? 0)

  const startMutation = useMutation({
    mutationFn: () =>
      apiRequest<{ authorizationUrl: string }>(`/admin/oauth/${provider}/start`, {
        method: 'POST',
        body: JSON.stringify({ poolId }),
      }),
    onSuccess: (result: { authorizationUrl: string }) => {
      window.location.href = result.authorizationUrl
    },
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">OAuth connect</p>
          <h2>发起 {provider} 授权连接</h2>
        </div>
        <p className="empty-state">poolId: {poolId || '未提供'}</p>
        <button onClick={() => startMutation.mutate()} disabled={startMutation.isPending || !poolId}>
          开始授权
        </button>
      </div>
    </section>
  )
}
