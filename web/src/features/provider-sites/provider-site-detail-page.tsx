import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'
import { useTypedQuery } from '../../lib/typed-react-query'
import {
  formatInstant,
  type ProviderSiteDossierResponse,
  type SiteModelCapability,
  type SurfaceDossierItemResponse,
} from './types'

const DOSSIER_TABS = ['summary', 'surfaces', 'models', 'trace-links'] as const
type DossierTab = (typeof DOSSIER_TABS)[number]

export function ProviderSiteDetailPage() {
  const params = useParams()
  const [searchParams] = useSearchParams()
  const [activeTab, setActiveTab] = useState<DossierTab>('summary')
  const id = Number(params.id)
  const selectedSurface = searchParams.get('surface')

  const dossierQuery = useTypedQuery<ProviderSiteDossierResponse>({
    queryKey: ['provider-site-dossier', id],
    queryFn: () => apiRequest<ProviderSiteDossierResponse>(`/admin/provider-sites/${id}/dossier`),
    enabled: Number.isFinite(id),
  })

  const dossier = dossierQuery.data
  const site = dossier?.site
  const filteredCapabilities = (dossier?.capabilities ?? []).filter((item) => matchesSurface(item, selectedSurface))

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Site dossier</p>
          <h2>{site?.displayName ?? '站点运行档案'}</h2>
          <p className="empty-state">详情页先给运行结论、blocker 和建议动作，编辑行为拆到独立 settings 页面。</p>
        </div>
        <div className="inline-actions">
          <Link className="action-link" to="/provider-sites">返回站点列表</Link>
          {site ? <Link className="action-link" to={`/provider-sites/${site.id}/settings`}>编辑站点设置</Link> : null}
          {site ? (
            <Link
              className="action-link"
              to={`/workbench?protocol=openai&requestPath=${encodeURIComponent(selectedSurfacePath(dossier, selectedSurface) ?? '/v1/chat/completions')}&requestedModel=${encodeURIComponent(filteredCapabilities[0]?.modelKey ?? '')}`}
            >
              打开 Workbench
            </Link>
          ) : null}
        </div>
        <div className="inline-actions">
          {DOSSIER_TABS.map((tab) => (
            <button
              key={tab}
              type="button"
              className={`secondary-button${activeTab === tab ? ' active' : ''}`}
              onClick={() => setActiveTab(tab)}
            >
              {tabLabel(tab)}
            </button>
          ))}
        </div>
      </div>

      {activeTab === 'summary' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Summary</p>
            <h3>运行摘要</h3>
          </div>
          {site ? (
            <>
              <div className="detail-grid">
                <div className="detail-card">
                  <strong>provider</strong>
                  <span>{site.providerFamily} / {site.siteKind}</span>
                </div>
                <div className="detail-card">
                  <strong>health</strong>
                  <span>{site.healthState}</span>
                </div>
                <div className="detail-card">
                  <strong>compatibilitySurface</strong>
                  <span>{site.compatibilitySurface}</span>
                </div>
                <div className="detail-card">
                  <strong>cooldown</strong>
                  <span>{site.cooldownCredentialCount} / {formatInstant(site.cooldownUntil)}</span>
                </div>
              </div>
              {site.blockedReason ? (
                <div className="detail-card">
                  <strong>site blocker</strong>
                  <span>{site.blockedReason}</span>
                </div>
              ) : null}
              <div className="card-list">
                {dossier?.recommendedActions.map((action) => (
                  <div key={action} className="detail-card">
                    <strong>Recommended action</strong>
                    <span>{action}</span>
                  </div>
                ))}
              </div>
              <div className="detail-grid">
                <div className="detail-card">
                  <strong>Blocked surfaces</strong>
                  <span>{dossier?.blockedSurfaces.length ?? 0}</span>
                </div>
                <div className="detail-card">
                  <strong>Degraded surfaces</strong>
                  <span>{dossier?.degradedSurfaces.length ?? 0}</span>
                </div>
                <div className="detail-card">
                  <strong>Accepted exceptions</strong>
                  <span>{dossier?.acceptedExceptions.length ?? 0}</span>
                </div>
                <div className="detail-card">
                  <strong>Models</strong>
                  <span>{dossier?.capabilities.length ?? 0}</span>
                </div>
              </div>
            </>
          ) : (
            <p className="empty-state">正在加载 dossier…</p>
          )}
        </div>
      ) : null}

      {activeTab === 'surfaces' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Surfaces</p>
            <h3>Surface 限制与能力</h3>
          </div>
          <div className="card-list">
            <SurfaceSection title="Blocked" items={dossier?.blockedSurfaces ?? []} siteId={site?.id} />
            <SurfaceSection title="Degraded" items={dossier?.degradedSurfaces ?? []} siteId={site?.id} />
            <SurfaceSection title="Accepted exceptions" items={dossier?.acceptedExceptions ?? []} siteId={site?.id} />
          </div>
        </div>
      ) : null}

      {activeTab === 'models' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Models</p>
            <h3>模型档案</h3>
          </div>
          <div className="card-list">
            {filteredCapabilities.map((item) => (
              <div key={item.id} className="detail-card">
                <strong>{item.modelName}</strong>
                <span>{item.modelKey}</span>
                <span>{item.capabilityLevel}</span>
                <span>backend: {item.preferredBackend ?? '-'} / {(item.supportedBackends ?? []).join(', ') || '无'}</span>
              </div>
            ))}
            {!filteredCapabilities.length ? <p className="empty-state">当前筛选下没有模型记录。</p> : null}
          </div>
        </div>
      ) : null}

      {activeTab === 'trace-links' ? (
        <div className="panel panel-wide">
          <div className="panel-head">
            <p className="panel-kicker">Trace links</p>
            <h3>下一步入口</h3>
          </div>
          <div className="card-list">
            <div className="detail-card">
              <strong>Capability matrix</strong>
              <span>从全局 blocker / degraded / accepted exception 视角继续定位。</span>
              <div className="inline-actions">
                <Link className="action-link" to="/capability-matrix">打开矩阵</Link>
              </div>
            </div>
            {site ? (
              <>
                <div className="detail-card">
                  <strong>Trace workbench</strong>
                  <span>按 providerType/requestPath 查看最近请求的 route、cache 和 async 资源线索。</span>
                  <div className="inline-actions">
                    <Link
                      className="action-link"
                      to={`/traces?providerType=${encodeURIComponent(site.siteKind)}&requestPath=${encodeURIComponent(selectedSurfacePath(dossier, selectedSurface) ?? '/v1/chat/completions')}`}
                    >
                      打开 Traces
                    </Link>
                  </div>
                </div>
                <div className="detail-card">
                  <strong>Incident command center</strong>
                  <span>从事件维度看受影响实体与建议动作。</span>
                  <div className="inline-actions">
                    <Link className="action-link" to={`/incidents?entityType=SITE_PROFILE&entityRef=${encodeURIComponent(String(site.id))}`}>
                      打开 Incidents
                    </Link>
                  </div>
                </div>
              </>
            ) : null}
          </div>
        </div>
      ) : null}
    </section>
  )
}

function SurfaceSection({ title, items, siteId }: { title: string; items: SurfaceDossierItemResponse[]; siteId?: number }) {
  return (
    <div className="detail-card">
      <strong>{title}</strong>
      <div className="card-list">
        {items.map((item) => (
          <div key={`${title}-${item.surfaceKey}`} className="detail-card">
            <strong>{item.operation}</strong>
            <span>{item.supportStatus ?? '-'} / {item.degradationLevel ?? '-'}</span>
            <span>{item.normalizedPath ?? '无 normalizedPath'}</span>
            {item.blockerReasons.length ? <span>{item.blockerReasons.join('；')}</span> : null}
            {item.lossReasons.length ? <span>{item.lossReasons.join('；')}</span> : null}
            {siteId ? (
              <div className="inline-actions">
                <Link className="action-link" to={`/provider-sites/${siteId}/settings`}>设置</Link>
                <Link className="action-link" to={`/workbench?protocol=openai&requestPath=${encodeURIComponent(item.normalizedPath ?? '/v1/chat/completions')}`}>Workbench</Link>
              </div>
            ) : null}
          </div>
        ))}
        {!items.length ? <p className="empty-state">当前没有记录。</p> : null}
      </div>
    </div>
  )
}

function matchesSurface(model: SiteModelCapability, surface?: string | null) {
  if (!surface) return true
  return Boolean(model.surfaces[surface])
}

function selectedSurfacePath(dossier?: ProviderSiteDossierResponse, surfaceKey?: string | null) {
  if (!surfaceKey || !dossier) return null
  const surface =
    dossier.blockedSurfaces.find((item) => item.surfaceKey === surfaceKey)
    ?? dossier.degradedSurfaces.find((item) => item.surfaceKey === surfaceKey)
    ?? dossier.acceptedExceptions.find((item) => item.surfaceKey === surfaceKey)
    ?? dossier.capabilities.find((item) => item.surfaces[surfaceKey])?.surfaces[surfaceKey]
  return surface?.normalizedPath ?? null
}

function tabLabel(tab: DossierTab) {
  switch (tab) {
    case 'summary':
      return 'Summary'
    case 'surfaces':
      return 'Surfaces'
    case 'models':
      return 'Models'
    case 'trace-links':
      return 'Trace links'
  }
}
