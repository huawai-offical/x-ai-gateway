import { useMutation, useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type InstallationState = {
  id: number
  status: string
  bootstrapCompleted: boolean
  metadataJson?: string | null
}

export function InstallPage() {
  const stateQuery = useQuery({
    queryKey: ['install-state'],
    queryFn: () => apiRequest<InstallationState>('/admin/install/state'),
  })
  const bootstrapMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/install/bootstrap', {
        method: 'POST',
        body: JSON.stringify({ adminEmail: 'admin@example.com', environmentName: 'local' }),
      }),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Install</p>
          <h2>安装向导与状态</h2>
        </div>
        <button type="button" onClick={() => bootstrapMutation.mutate()}>执行 bootstrap</button>
        <div className="code-block">
          <pre>{JSON.stringify(stateQuery.data, null, 2)}</pre>
        </div>
      </div>
    </section>
  )
}
