import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type BackupJob = {
  id: number
  status: string
  snapshotPath?: string | null
}

export function BackupsPage() {
  const queryClient = useQueryClient()
  const backupsQuery = useQuery({
    queryKey: ['backups'],
    queryFn: () => apiRequest<BackupJob[]>('/admin/backups'),
  })
  const createMutation = useMutation({
    mutationFn: (dryRun: boolean) => apiRequest('/admin/backups', { method: 'POST', body: JSON.stringify({ dryRun }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backups'] }),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Backups</p>
          <h2>备份与恢复</h2>
        </div>
        <div className="inline-actions">
          <button type="button" onClick={() => createMutation.mutate(true)}>Dry run</button>
          <button type="button" onClick={() => createMutation.mutate(false)}>创建备份</button>
        </div>
      </div>
      <div className="panel panel-wide">
        <div className="card-list">
          {backupsQuery.data?.map((item: BackupJob) => (
            <div key={item.id} className="detail-card">
              <strong>backup #{item.id}</strong>
              <span>{item.status}</span>
              <span>{item.snapshotPath ?? '-'}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
