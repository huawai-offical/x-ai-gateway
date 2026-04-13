import { type FormEvent, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedMutation, useTypedQuery } from '../../lib/typed-react-query'
import {
  type CapabilityResolution,
  featureLabel,
  formatInstant,
  modelSupportsFeature,
  resolutionTone,
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
  const selectedFeature = searchParams.get('feature')

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
  const filteredCapabilities = (capabilitiesQuery.data ?? []).filter((item: SiteModelCapability) => modelSupportsFeature(item, selectedFeature))
  const featureEntries = Object.entries(current?.features ?? {}) as Array<[string, CapabilityResolution]>
  const debugRequestedModel = filteredCapabilities[0]?.modelKey ?? capabilitiesQuery.data?.[0]?.modelKey ?? ''
  const debugRequestPath = selectedFeature === 'response_object' ? '/v1/responses' : '/v1/chat/completions'

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!form.profileCode.trim() || !form.displayName.trim()) return
    saveMutation.mutate()
  }

  const handleFeatureSelect = (feature: string) => {
    const next = new URLSearchParams(searchParams)
    if (next.get('feature') === feature) {
      next.delete('feature')
    } else {
      next.set('feature', feature)
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
              <p className="panel-kicker">Feature resolution</p>
              <h3>特征解析</h3>
            </div>
            <div className="feature-list">
              {featureEntries.map(([feature, resolution]) => (
                <button
                  key={feature}
                  type="button"
                  className={`feature-badge ${resolutionTone(resolution)}${selectedFeature === feature ? ' active' : ''}`}
                  onClick={() => handleFeatureSelect(feature)}
                >
                  {featureLabel(feature)}
                  <small>{resolution.declaredLevel ?? '-'}/{resolution.implementedLevel ?? '-'}/{resolution.effectiveLevel ?? '-'}</small>
                </button>
              ))}
            </div>
            <div className="card-list">
              {featureEntries.map(([feature, resolution]) => (
                <div key={feature} className={`detail-card${selectedFeature === feature ? ' is-selected' : ''}`}>
                  <strong>{featureLabel(feature)}</strong>
                  <span>declared: {resolution.declaredLevel ?? '-'}</span>
                  <span>implemented: {resolution.implementedLevel ?? '-'}</span>
                  <span>effective: {resolution.effectiveLevel ?? '-'}</span>
                  {resolution.blockedReasons.length ? <span>{resolution.blockedReasons.join('；')}</span> : null}
                  {resolution.lossReasons.length ? <span>{resolution.lossReasons.join('；')}</span> : null}
                </div>
              ))}
            </div>
          </div>

          <div className="panel">
            <div className="panel-head">
              <p className="panel-kicker">Model capabilities</p>
              <h3>{selectedFeature ? `模型能力 · ${featureLabel(selectedFeature)}` : '模型能力'}</h3>
            </div>
            <div className="card-list">
              {filteredCapabilities.map((item: SiteModelCapability) => (
                <div key={item.id} className="detail-card">
                  <strong>{item.modelName}</strong>
                  <span>{item.modelKey}</span>
                  <span>{item.capabilityLevel}</span>
                  <span>chat: {String(item.supportsChat)}</span>
                  <span>tools: {String(item.supportsTools)}</span>
                  <span>image_input: {String(item.supportsImageInput)}</span>
                  <span>embeddings: {String(item.supportsEmbeddings)}</span>
                  <span>thinking: {String(item.supportsThinking)}</span>
                  <span>transport: {item.reasoningTransport ?? '-'}</span>
                  <span>{item.supportedProtocols.join(', ')}</span>
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
