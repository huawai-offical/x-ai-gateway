import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'
import { useState } from 'react'

type ReleaseArtifact = {
  id: number
  versionName: string
  artifactRef: string
}

type UpgradeJob = {
  id: number
  status: string
  message: string
}

export function UpgradesPage() {
  const queryClient = useQueryClient()
  const [versionName, setVersionName] = useState('')
  const [artifactRef, setArtifactRef] = useState('')
  const [targetReleaseArtifactId, setTargetReleaseArtifactId] = useState('')
  const releasesQuery = useQuery({
    queryKey: ['release-artifacts'],
    queryFn: () => apiRequest<ReleaseArtifact[]>('/admin/upgrades/releases'),
  })
  const upgradesQuery = useQuery({
    queryKey: ['upgrades'],
    queryFn: () => apiRequest<UpgradeJob[]>('/admin/upgrades'),
  })
  const releaseMutation = useMutation({
    mutationFn: () => apiRequest('/admin/upgrades/releases', { method: 'POST', body: JSON.stringify({ versionName, artifactRef }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['release-artifacts'] }),
  })
  const upgradeMutation = useMutation({
    mutationFn: () => apiRequest('/admin/upgrades', { method: 'POST', body: JSON.stringify({ targetReleaseArtifactId: Number(targetReleaseArtifactId), confirm: true }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['upgrades'] }),
  })

  return (
    <section className="page-grid">
      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Release</p>
          <h2>发布制品</h2>
        </div>
        <div className="inline-form">
          <input value={versionName} onChange={(e) => setVersionName(e.target.value)} placeholder="version" />
          <input value={artifactRef} onChange={(e) => setArtifactRef(e.target.value)} placeholder="artifact ref" />
          <button type="button" onClick={() => releaseMutation.mutate()}>登记制品</button>
        </div>
        <div className="card-list">
          {releasesQuery.data?.map((item: ReleaseArtifact) => (
            <div key={item.id} className="detail-card">
              <strong>{item.versionName}</strong>
              <span>{item.artifactRef}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Upgrade</p>
          <h2>升级任务</h2>
        </div>
        <div className="inline-form">
          <input value={targetReleaseArtifactId} onChange={(e) => setTargetReleaseArtifactId(e.target.value)} placeholder="target release id" />
          <button type="button" onClick={() => upgradeMutation.mutate()}>执行升级</button>
        </div>
        <div className="card-list">
          {upgradesQuery.data?.map((item: UpgradeJob) => (
            <div key={item.id} className="detail-card">
              <strong>upgrade #{item.id}</strong>
              <span>{item.status}</span>
              <span>{item.message}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
