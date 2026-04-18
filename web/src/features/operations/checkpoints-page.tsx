import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type RecoveryCheckpoint = {
  id: number
  checkpointName: string
  changePlanId?: number | null
  status: string
  verificationStatus?: string | null
  verificationMessage?: string | null
  metadataSnapshotPath?: string | null
  runtimeSnapshotPath?: string | null
  dataSnapshotPath?: string | null
}

export function CheckpointsPage() {
  const queryClient = useQueryClient()
  const checkpointsQuery = useQuery({
    queryKey: ['operations', 'checkpoints'],
    queryFn: () => apiRequest<RecoveryCheckpoint[]>('/admin/operations/recovery-checkpoints'),
  })

  const verifyMutation = useMutation({
    mutationFn: (checkpointId: number) =>
      apiRequest<RecoveryCheckpoint>(`/admin/operations/recovery-checkpoints/${checkpointId}/verify`, {
        method: 'POST',
        body: JSON.stringify({ verifiedBy: 'console' }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['operations', 'checkpoints'] }),
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Recovery Checkpoint</p>
          <h2>恢复检查点</h2>
        </div>
        <p className="panel-copy">
          这里集中查看 metadata / runtime / data 三类快照的产物，并执行 checkpoint 校验。
        </p>
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {checkpointsQuery.data?.map((item: RecoveryCheckpoint) => (
            <div key={item.id} className="detail-card">
              <strong>{item.checkpointName}</strong>
              <span>来源计划：{item.changePlanId ?? '-'}</span>
              <span>状态：{item.status}</span>
              <span>校验：{item.verificationStatus ?? '-'}</span>
              <span>{item.verificationMessage ?? '-'}</span>
              <div className="inline-actions">
                <button type="button" onClick={() => verifyMutation.mutate(item.id)}>校验 checkpoint</button>
              </div>
              <details>
                <summary>快照产物</summary>
                <ul className="compact-list">
                  <li>metadata: {item.metadataSnapshotPath ?? '-'}</li>
                  <li>runtime: {item.runtimeSnapshotPath ?? '-'}</li>
                  <li>data: {item.dataSnapshotPath ?? '-'}</li>
                </ul>
              </details>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
