import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type ProbeJob = {
  id: number
  jobName: string
  probeType: string
  targetRef: string
  lastStatus?: string | null
}

export function OpsProbesPage() {
  const queryClient = useQueryClient()
  const [jobName, setJobName] = useState('')
  const [targetRef, setTargetRef] = useState('')
  const jobsQuery = useQuery({
    queryKey: ['ops-probe-jobs'],
    queryFn: () => apiRequest<ProbeJob[]>('/admin/ops/probes'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/ops/probes', {
        method: 'POST',
        body: JSON.stringify({ jobName, probeType: 'NETWORK_PROXY', targetRef, intervalSeconds: 60, enabled: true }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-probe-jobs'] }),
  })

  const runMutation = useMutation({
    mutationFn: (id: number) => apiRequest(`/admin/ops/probes/${id}/run`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-probe-jobs'] }),
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    createMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Probe jobs</p>
          <h2>主动拨测任务</h2>
        </div>
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={jobName} onChange={(e) => setJobName(e.target.value)} placeholder="任务名称" />
          <input value={targetRef} onChange={(e) => setTargetRef(e.target.value)} placeholder="目标 proxy id" />
          <button type="submit">创建任务</button>
        </form>
      </div>
      <div className="panel panel-wide">
        <div className="card-list">
          {jobsQuery.data?.map((job: ProbeJob) => (
            <div key={job.id} className="detail-card">
              <strong>{job.jobName}</strong>
              <span>{job.probeType}</span>
              <span>{job.targetRef}</span>
              <span>{job.lastStatus ?? 'unknown'}</span>
              <button type="button" onClick={() => runMutation.mutate(job.id)}>立即运行</button>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
