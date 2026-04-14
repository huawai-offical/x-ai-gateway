import { type FormEvent, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedMutation, useTypedQuery } from '../../lib/typed-react-query'
import {
  formatInstant,
  modelSupportsFeature,
  SITE_KIND_OPTIONS,
  type ProviderSite,
  type ProviderSiteDraft,
  type SiteModelCapability,
} from './types'

const DEFAULT_DRAFT: ProviderSiteDraft = {
  profileCode: '',
  displayName: '',
  siteKind: 'OPENAI_DIRECT',
  baseUrlPattern: '',
  description: '',
  active: true,
}

export function ProviderSiteDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<ProviderSiteDraft | null>(null)

  const isCreateMode = params.id === 'new'
  const id = Number(params.id)
  const selectedSurface = searchParams.get('surface')

  const detailQuery = useTypedQuery<ProviderSite>({
    queryKey: ['provider-site', id],
    queryFn: () => apiRequest<ProviderSite>(`/admin/provider-sites/${id}`),
    enabled: !isCreateMode && Number.isFinite(id),
  })

  const capabilitiesQuery = useTypedQuery<SiteModelCapability[]>({
    queryKey: ['provider-site-capabilities', id],
    queryFn: () => apiRequest<SiteModelCapability[]>(`/admin/provider-sites/${id}/capabilities`),
    enabled: !isCreateMode && Number.isFinite(id),
  })

  const refreshMutation = useTypedMutation<ProviderSite, void>({
    mutationFn: () =>
      apiRequest<ProviderSite>(`/admin/provider-sites/${id}/refresh-capabilities`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['provider-site', id] })
      queryClient.invalidateQueries({ queryKey: ['provider-site-capabilities', id] })
      queryClient.invalidateQueries({ queryKey: ['provider-sites'] })
      queryClient.invalidateQueries({ queryKey: ['capability-matrix'] })
    },
  })

  const saveMutation = useTypedMutation<ProviderSite, void>({
    mutationFn: () =>
      apiRequest<ProviderSite>(isCreateMode ? '/admin/provider-sites' : `/admin/provider-sites/${id}`, {
        method: isCreateMode ? 'POST' : 'PUT',
        body: JSON.stringify(form),
      }),
    onSuccess: (result: ProviderSite) => {
      queryClient.invalidateQueries({ queryKey: ['provider-sites'] })
      queryClient.invalidateQueries({ queryKey: ['capability-matrix'] })
      queryClient.invalidateQueries({ queryKey: ['provider-site', result.id] })
      if (isCreateMode) {
        navigate(`/provider-sites/${result.id}`)
        return
      }
      queryClient.invalidateQueries({ queryKey: ['provider-site-capabilities', result.id] })
    },
  })

  const current = detailQuery.data
  const form = draft ?? (current ? toDraft(current) : DEFAULT_DRAFT)
  const filteredCapabilities = (capabilitiesQuery.data ?? []).filter((item: SiteModelCapability) => modelSupportsFeature(item, selectedSurface))
  const surfaceEntries = Object.entries(current?.surfaces ?? {})
  const debugRequestedModel = filteredCapabilities[0]?.modelKey ?? capabilitiesQuery.data?.[0]?.modelKey ?? ''
  const debugRequestPath = selectedSurface === 'response_create' ? '/v1/responses' : '/v1/chat/completions'

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!form.profileCode.trim() || !form.displayName.trim()) return
    saveMutation.mutate()
  }

  const handleSurfaceSelect = (surface: string) => {
    const next = new URLSearchParams(searchParams)
    if (next.get('surface') === surface) {
      next.delete('surface')
    } else {
      next.set('surface', surface)
    }
    setSearchParams(next)
  }

  const updateDraft = (patch: Partial<ProviderSiteDraft>) => {
    setDraft((currentDraft) => ({
      ...(currentDraft ?? form),
      ...patch,
    }))
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Site detail</p>
          <h2>{isCreateMode ? '新建站点档案' : current?.displayName ?? '站点档案'}</h2>
          <p className="empty-state">详情页同时承载编辑、feature resolution 和调试深链。</p>
        </div>
        <div className="inline-actions">
          <Link className="action-link" to="/provider-sites">返回列表</Link>
          {!isCreateMode ? (
            <>
              <button type="button" onClick={() => refreshMutation.mutate()} disabled={refreshMutation.isPending}>
                刷新能力快照
              </button>
              <Link
                className="action-link"
                to={`/translation-debug?protocol=openai&requestPath=${encodeURIComponent(debugRequestPath)}&requestedModel=${encodeURIComponent(debugRequestedModel)}`}
              >
                进入调试页
              </Link>
            </>
          ) : null}
        </div>
        <form className="stacked-form" onSubmit={handleSubmit}>
          <div className="form-grid">
            <label>
              <span>profileCode</span>
              <input value={form.profileCode} onChange={(event) => updateDraft({ profileCode: event.target.value })} />
            </label>
            <label>
              <span>displayName</span>
              <input value={form.displayName} onChange={(event) => updateDraft({ displayName: event.target.value })} />
            </label>
            <label>
              <span>siteKind</span>
              <select value={form.siteKind} onChange={(event) => updateDraft({ siteKind: event.target.value })}>
                {SITE_KIND_OPTIONS.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </label>
            <label>
              <span>baseUrlPattern</span>
              <input value={form.baseUrlPattern} onChange={(event) => updateDraft({ baseUrlPattern: event.target.value })} />
            </label>
            <label className="checkbox-line">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(event) => updateDraft({ active: event.target.checked })}
              />
              <span>active</span>
            </label>
          </div>
          <label>
            <span>description</span>
            <textarea value={form.description} onChange={(event) => updateDraft({ description: event.target.value })} rows={4} />
          </label>
          <div className="inline-actions">
            <button type="submit" disabled={saveMutation.isPending}>保存站点档案</button>
            {!isCreateMode && current ? (
              <button
                type="button"
                onClick={() => setDraft(null)}
              >
                重置表单
              </button>
            ) : null}
          </div>
        </form>
      </div>

      {!isCreateMode && current ? (
        <>
          <div className="panel">
            <div className="panel-head">
              <p className="panel-kicker">Site context</p>
              <h3>站点上下文</h3>
            </div>
            <div className="card-list">
              <div className="detail-card">
                <strong>{current.profileCode}</strong>
                <span>{current.providerFamily} / {current.siteKind}</span>
                <span>{current.authStrategy} / {current.pathStrategy}</span>
                <span>{current.modelAddressingStrategy} / {current.errorSchemaStrategy}</span>
                <span>surface: {current.compatibilitySurface}</span>
                <span>backend: {current.preferredBackend ?? '无'} / {(current.supportedBackends ?? []).join(', ') || '无'}</span>
                <span>credential: {current.credentialRequirements.join(', ') || '无'}</span>
                <span>transport: {current.streamTransport ?? '无'}</span>
                <span>fallback: {current.fallbackStrategy ?? '无'}</span>
                <span>cooldown: {current.cooldownCredentialCount} / {formatInstant(current.cooldownUntil)}</span>
                <span>refreshedAt: {formatInstant(current.refreshedAt)}</span>
                {current.blockedReason ? <span>{current.blockedReason}</span> : null}
              </div>
            </div>
          </div>

          <div className="panel panel-wide">
            <div className="panel-head">
              <p className="panel-kicker">Surface capability</p>
              <h3>Surface 能力</h3>
            </div>
            <div className="feature-list">
              {surfaceEntries.map(([surfaceKey, surface]) => (
                <button
                  key={surfaceKey}
                  type="button"
                  className={`feature-badge ${(surface.overallCapabilityLevel ?? 'NATIVE').toLowerCase()}${selectedSurface === surfaceKey ? ' active' : ''}`}
                  onClick={() => handleSurfaceSelect(surfaceKey)}
                >
                  {surface.operation}
                  <small>{surface.executionCapabilityLevel ?? '-'}/{surface.renderCapabilityLevel ?? '-'}/{surface.overallCapabilityLevel ?? '-'}</small>
                </button>
              ))}
            </div>
            <div className="card-list">
              {surfaceEntries.map(([surfaceKey, surface]) => (
                <div key={surfaceKey} className={`detail-card${selectedSurface === surfaceKey ? ' is-selected' : ''}`}>
                  <strong>{surface.operation}</strong>
                  <span>backend: {surface.preferredBackend ?? '-'} / {(surface.supportedBackends ?? []).join(', ') || '无'}</span>
                  <span>resource: {surface.resourceType}</span>
                  <span>execution: {surface.executionCapabilityLevel ?? '-'}</span>
                  <span>render: {surface.renderCapabilityLevel ?? '-'}</span>
                  <span>overall: {surface.overallCapabilityLevel ?? '-'}</span>
                  <span>requiredFeatures: {surface.requiredFeatures.join(', ') || '无'}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="panel">
            <div className="panel-head">
              <p className="panel-kicker">Model capabilities</p>
              <h3>{selectedSurface ? `模型能力 · ${selectedSurface}` : '模型能力'}</h3>
            </div>
            <div className="card-list">
              {filteredCapabilities.map((item: SiteModelCapability) => (
                <div key={item.id} className="detail-card">
                  <strong>{item.modelName}</strong>
                  <span>{item.modelKey}</span>
                  <span>{item.capabilityLevel}</span>
                  <span>backend: {item.preferredBackend ?? '-'} / {(item.supportedBackends ?? []).join(', ') || '无'}</span>
                  {Object.entries(item.surfaces).map(([surfaceKey, surface]) => (
                    <span key={surfaceKey}>{surface.operation}: {surface.executionCapabilityLevel ?? '-'}/{surface.renderCapabilityLevel ?? '-'}/{surface.overallCapabilityLevel ?? '-'}</span>
                  ))}
                </div>
              ))}
              {!filteredCapabilities.length ? <p className="empty-state">当前特征下暂无模型级细分记录。</p> : null}
            </div>
          </div>
        </>
      ) : null}
    </section>
  )
}

function toDraft(site: ProviderSite): ProviderSiteDraft {
  return {
    profileCode: site.profileCode,
    displayName: site.displayName,
    siteKind: site.siteKind,
    baseUrlPattern: site.baseUrlPattern ?? '',
    description: site.description ?? '',
    active: site.active,
  }
}
