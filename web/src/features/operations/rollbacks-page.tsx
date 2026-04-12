import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'
import { useState } from 'react'

type RollbackJob = {
  id: number
  status: string
  message: string
}

export function RollbacksPage() {
  const queryClient = useQueryClient()
  const [upgradeJobId, setUpgradeJobId] = useState('')
  const [releaseArtifactId, setReleaseArtifactId] = useState('')
  const [backupJobId, setBackupJobId] = useState('')
  const rollbacksQuery = useQuery({
    queryKey: ['rollbacks'],
    queryFn: () => apiRequest<RollbackJob[]>('/admin/rollbacks'),
  })
  const rollbackMutation = useMutation({
    mutationFn: () => apiRequest(`/admin/rollbacks?upgradeJobId=${upgradeJobId}&releaseArtifactId=${releaseArtifactId}&backupJobId=${backupJobId}`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['rollbacks'] }),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Rollback</p>
          <h2>回滚任务</h2>
        </div>
        <div className="inline-form">
          <input value={upgradeJobId} onChange={(e) => setUpgradeJobId(e.target.value)} placeholder="upgrade job id" />
          <input value={releaseArtifactId} onChange={(e) => setReleaseArtifactId(e.target.value)} placeholder="release artifact id" />
          <input value={backupJobId} onChange={(e) => setBackupJobId(e.target.value)} placeholder="backup job id" />
          <button type="button" onClick={() => rollbackMutation.mutate()}>执行回滚</button>
        </div>
      </div>
      <div className="panel panel-wide">
        <div className="card-list">
          {rollbacksQuery.data?.map((item: RollbackJob) => (
            <div key={item.id} className="detail-card">
              <strong>rollback #{item.id}</strong>
              <span>{item.status}</span>
              <span>{item.message}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
