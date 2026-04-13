import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedMutation, useTypedQuery } from '../../lib/typed-react-query'
import { formatInstant, type ProviderSite } from './types'

type RefreshResponse = ProviderSite[]

export function ProviderSitesPage() {
  const queryClient = useQueryClient()
  const [compatibilitySurface, setCompatibilitySurface] = useState('all')
  const [healthState, setHealthState] = useState('all')
  const [siteKind, setSiteKind] = useState('all')
  const [selectedIds, setSelectedIds] = useState<number[]>([])

  const query = useTypedQuery<ProviderSite[]>({
    queryKey: ['provider-sites'],
    queryFn: () => apiRequest<ProviderSite[]>('/admin/provider-sites'),
  })

  const refreshMutation = useTypedMutation<RefreshResponse, number[] | undefined>({
    mutationFn: (siteProfileIds?: number[]) =>
      apiRequest<RefreshResponse>('/admin/provider-sites/refresh-capabilities', {
        method: 'POST',
        body: JSON.stringify(siteProfileIds?.length ? { siteProfileIds } : {}),
      }),
    onSuccess: () => {
      setSelectedIds([])
      queryClient.invalidateQueries({ queryKey: ['provider-sites'] })
      queryClient.invalidateQueries({ queryKey: ['capability-matrix'] })
    },
  })

  const sites = query.data ?? []
  const filteredSites = sites.filter((site) => {
    if (compatibilitySurface !== 'all' && site.compatibilitySurface !== compatibilitySurface) return false
    if (healthState !== 'all' && site.healthState !== healthState) return false
    if (siteKind !== 'all' && site.siteKind !== siteKind) return false
    return true
  })

  const toggleSelection = (siteId: number) => {
    setSelectedIds((current) =>
      current.includes(siteId)
        ? current.filter((item) => item !== siteId)
        : [...current, siteId],
    )
  }

  const availableHealthStates = Array.from(new Set(sites.map((site) => site.healthState))).sort()
  const availableSiteKinds = Array.from(new Set(sites.map((site) => site.siteKind))).sort()
  const availableSurfaces = Array.from(new Set(sites.map((site) => site.compatibilitySurface))).sort()

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Provider sites</p>
          <h2>站点档案与能力快照</h2>
          <p className="empty-state">当前按站点统一展示 health、fallback、cooldown 与 capability truth。</p>
        </div>
        <div className="inline-form">
          <label className="stacked-form">
            <span>兼容面</span>
            <select value={compatibilitySurface} onChange={(event) => setCompatibilitySurface(event.target.value)}>
              <option value="all">全部</option>
              {availableSurfaces.map((value: string) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <label className="stacked-form">
            <span>健康状态</span>
            <select value={healthState} onChange={(event) => setHealthState(event.target.value)}>
              <option value="all">全部</option>
              {availableHealthStates.map((value: string) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <label className="stacked-form">
            <span>站点类型</span>
            <select value={siteKind} onChange={(event) => setSiteKind(event.target.value)}>
              <option value="all">全部</option>
              {availableSiteKinds.map((value: string) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="inline-actions">
          <Link className="action-link" to="/provider-sites/new">新建站点档案</Link>
          <button type="button" onClick={() => refreshMutation.mutate(selectedIds)} disabled={refreshMutation.isPending || !selectedIds.length}>
            刷新选中站点
          </button>
          <button type="button" onClick={() => refreshMutation.mutate([])} disabled={refreshMutation.isPending || !sites.length}>
            刷新全部 Active 站点
          </button>
        </div>
        <p className="empty-state">当前筛选结果 {filteredSites.length} 个站点，已选 {selectedIds.length} 个。</p>
        <div className="card-list">
          {filteredSites.map((item: ProviderSite) => (
            <div key={item.id} className="detail-card">
              <div className="inline-actions">
                <label className="checkbox-line">
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(item.id)}
                    onChange={() => toggleSelection(item.id)}
                    aria-label={`选择 ${item.displayName}`}
                  />
                  <span>批量刷新</span>
                </label>
                <Link className="action-link" to={`/provider-sites/${item.id}`}>查看详情</Link>
                <button type="button" onClick={() => refreshMutation.mutate([item.id])} disabled={refreshMutation.isPending}>
                  刷新快照
                </button>
              </div>
              <strong>{item.displayName}</strong>
              <span>{item.providerFamily} / {item.siteKind}</span>
              <span>{item.authStrategy} / {item.pathStrategy}</span>
              <span>health: {item.healthState}</span>
              <span>surface: {item.compatibilitySurface}</span>
              <span>fallback: {item.fallbackStrategy ?? '无'}</span>
              <span>cooldown: {item.cooldownCredentialCount} / {formatInstant(item.cooldownUntil)}</span>
              <span>transport: {item.streamTransport ?? '无'}</span>
              <span>protocols: {item.supportedProtocols.join(', ') || '-'}</span>
              <span>models: {item.modelCount}</span>
              {item.blockedReason ? <span>{item.blockedReason}</span> : null}
            </div>
          ))}
          {!filteredSites.length ? <p className="empty-state">暂无符合筛选条件的站点档案。</p> : null}
        </div>
      </div>
    </section>
  )
}
