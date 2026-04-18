import { type FormEvent, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedMutation, useTypedQuery } from '../../lib/typed-react-query'
import { SITE_KIND_OPTIONS, type ProviderSite, type ProviderSiteDraft } from './types'

const DEFAULT_DRAFT: ProviderSiteDraft = {
  profileCode: '',
  displayName: '',
  siteKind: 'OPENAI_DIRECT',
  baseUrlPattern: '',
  description: '',
  active: true,
}

export function ProviderSiteSettingsPage() {
  const params = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<ProviderSiteDraft | null>(null)

  const isCreateMode = params.id === 'new'
  const id = Number(params.id)

  const detailQuery = useTypedQuery<ProviderSite>({
    queryKey: ['provider-site', id],
    queryFn: () => apiRequest<ProviderSite>(`/admin/provider-sites/${id}`),
    enabled: !isCreateMode && Number.isFinite(id),
  })

  const refreshMutation = useTypedMutation<ProviderSite, void>({
    mutationFn: () =>
      apiRequest<ProviderSite>(`/admin/provider-sites/${id}/refresh-capabilities`, {
        method: 'POST',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['provider-site', id] })
      queryClient.invalidateQueries({ queryKey: ['provider-site-dossier', id] })
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
      queryClient.invalidateQueries({ queryKey: ['provider-site-dossier', result.id] })
      if (isCreateMode) {
        navigate(`/provider-sites/${result.id}`)
        return
      }
      navigate(`/provider-sites/${result.id}`)
    },
  })

  const current = detailQuery.data
  const form = draft ?? (current ? toDraft(current) : DEFAULT_DRAFT)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (!form.profileCode.trim() || !form.displayName.trim()) return
    saveMutation.mutate()
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
          <p className="panel-kicker">Provider site settings</p>
          <h2>{isCreateMode ? '新建站点设置' : current?.displayName ?? '站点设置'}</h2>
          <p className="empty-state">这里承接站点编辑、保存和能力刷新；运行档案单独放在 dossier 页面。</p>
        </div>
        <div className="inline-actions">
          <Link className="action-link" to={isCreateMode ? '/provider-sites' : `/provider-sites/${id}`}>返回运行档案</Link>
          {!isCreateMode ? (
            <button type="button" onClick={() => refreshMutation.mutate()} disabled={refreshMutation.isPending}>
              刷新能力快照
            </button>
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
            <button type="submit" disabled={saveMutation.isPending}>保存站点设置</button>
            {!isCreateMode && current ? (
              <button type="button" onClick={() => setDraft(null)}>重置表单</button>
            ) : null}
          </div>
        </form>
      </div>
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
