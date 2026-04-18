import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { apiRequest } from '../../lib/api'

type MaintenanceWindow = {
  id: number
  windowName: string
  startsAt: string
  endsAt: string
  enabled: boolean
  activeNow: boolean
  description?: string | null
}

export function WindowsPage() {
  const queryClient = useQueryClient()
  const [windowName, setWindowName] = useState('')
  const [startsAt, setStartsAt] = useState('2026-04-18T22:00:00Z')
  const [endsAt, setEndsAt] = useState('2026-04-18T23:00:00Z')
  const [description, setDescription] = useState('')

  const windowsQuery = useQuery({
    queryKey: ['operations', 'maintenance-windows'],
    queryFn: () => apiRequest<MaintenanceWindow[]>('/admin/operations/maintenance-windows'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<MaintenanceWindow>('/admin/operations/maintenance-windows', {
        method: 'POST',
        body: JSON.stringify({
          windowName,
          startsAt,
          endsAt,
          enabled: true,
          description: description || null,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['operations', 'maintenance-windows'] })
      setWindowName('')
      setDescription('')
    },
  })

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Maintenance Window</p>
          <h2>维护窗口</h2>
        </div>
        <div className="inline-form">
          <input value={windowName} onChange={(event) => setWindowName(event.target.value)} placeholder="window name" />
          <input value={startsAt} onChange={(event) => setStartsAt(event.target.value)} placeholder="starts at" />
          <input value={endsAt} onChange={(event) => setEndsAt(event.target.value)} placeholder="ends at" />
        </div>
        <div className="inline-form">
          <input value={description} onChange={(event) => setDescription(event.target.value)} placeholder="description" />
          <button type="button" onClick={() => createMutation.mutate()} disabled={!windowName}>
            创建维护窗口
          </button>
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {windowsQuery.data?.map((item: MaintenanceWindow) => (
            <div key={item.id} className="detail-card">
              <strong>{item.windowName}</strong>
              <span>{item.activeNow ? '当前命中' : '当前未命中'}</span>
              <span>{item.startsAt} → {item.endsAt}</span>
              <span>{item.description ?? '-'}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
