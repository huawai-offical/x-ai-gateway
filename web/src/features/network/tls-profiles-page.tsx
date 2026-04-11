import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'

type TlsProfile = { id: number; profileName: string; profileCode: string }

export function TlsProfilesPage() {
  const queryClient = useQueryClient()
  const [profileName, setProfileName] = useState('')
  const [profileCode, setProfileCode] = useState('')

  const profilesQuery = useQuery({
    queryKey: ['tls-profiles'],
    queryFn: () => apiRequest<TlsProfile[]>('/admin/network/tls-profiles'),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/network/tls-profiles', {
        method: 'POST',
        body: JSON.stringify({ profileName, profileCode }),
      }),
    onSuccess: () => {
      setProfileName('')
      setProfileCode('')
      queryClient.invalidateQueries({ queryKey: ['tls-profiles'] })
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!profileName.trim() || !profileCode.trim()) return
    createMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">TLS fingerprint</p>
          <h2>TLS 指纹画像</h2>
        </div>
        <form className="inline-form" onSubmit={handleSubmit}>
          <input value={profileName} onChange={(e) => setProfileName(e.target.value)} placeholder="画像名称" />
          <input value={profileCode} onChange={(e) => setProfileCode(e.target.value)} placeholder="chrome-like" />
          <button type="submit">创建</button>
        </form>
      </div>

      <div className="panel panel-wide">
        <div className="card-list">
          {profilesQuery.data?.map((item: TlsProfile) => (
            <div key={item.id} className="detail-card">
              <strong>{item.profileName}</strong>
              <span>{item.profileCode}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
