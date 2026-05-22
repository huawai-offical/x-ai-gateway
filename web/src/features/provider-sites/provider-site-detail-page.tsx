import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeftIcon, RefreshCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'
import {
  type CapabilityResolution,
  type ProviderSite,
  type SiteModelCapability,
  type SurfaceCapability,
} from './types'

export function ProviderSiteDetailPage() {
  const params = useParams()
  const siteId = Number(params.id)
  const queryClient = useQueryClient()

  const siteQuery = useQuery({
    queryKey: ['provider-sites', 'detail', siteId],
    queryFn: () => apiRequest<ProviderSite>(`/admin/provider-sites/${siteId}`),
    enabled: Number.isFinite(siteId),
  })
  const capabilitiesQuery = useQuery({
    queryKey: ['provider-sites', 'capabilities', siteId],
    queryFn: () => apiRequest<SiteModelCapability[]>(`/admin/provider-sites/${siteId}/capabilities`),
    enabled: Number.isFinite(siteId),
  })
  const refreshMutation = useMutation({
    mutationFn: () => apiRequest<ProviderSite>(`/admin/provider-sites/${siteId}/refresh`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['provider-sites', 'detail', siteId] })
      queryClient.invalidateQueries({ queryKey: ['provider-sites', 'capabilities', siteId] })
      queryClient.invalidateQueries({ queryKey: ['provider-sites', 'list'] })
    },
  })

  const site = siteQuery.data
  const capabilities = useMemo(
    () => [...((capabilitiesQuery.data ?? []) as SiteModelCapability[])].sort((left, right) => left.modelName.localeCompare(right.modelName)),
    [capabilitiesQuery.data],
  )
  const surfaces = useMemo<Array<[string, SurfaceCapability]>>(
    () => Object.entries((site?.surfaces ?? {}) as Record<string, SurfaceCapability>)
      .sort(([left], [right]) => left.localeCompare(right)),
    [site?.surfaces],
  )
  const features = useMemo<Array<[string, CapabilityResolution]>>(
    () => Object.entries((site?.features ?? {}) as Record<string, CapabilityResolution>)
      .sort(([left], [right]) => left.localeCompare(right)),
    [site?.features],
  )
  const firstError = siteQuery.error ?? capabilitiesQuery.error ?? refreshMutation.error

  if (siteQuery.isPending) {
    return <PageSkeleton count={2} />
  }

  if (!site || firstError) {
    return <InlineError error={firstError ?? new Error('API 入口不存在。')} title="API 入口加载失败" />
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="厂商管理"
        title={site.displayName}
        actions={(
          <>
            <Link to="/console/provider-sites">
              <Button type="button" variant="outline">
                <ArrowLeftIcon data-icon="inline-start" />
                返回厂商管理
              </Button>
            </Link>
            <Button type="button" variant="outline" onClick={() => refreshMutation.mutate()} disabled={refreshMutation.isPending}>
              <RefreshCwIcon data-icon="inline-start" />
              刷新能力
            </Button>
          </>
        )}
      >
        {firstError ? <InlineError error={firstError} title="API 入口操作失败" /> : null}
        <InfoGrid
          items={[
            { key: 'vendor', label: '厂商', value: site.vendorName || site.vendorCode || site.providerFamily, hint: site.vendorCode ?? site.providerFamily },
            { key: 'kind', label: '协议入口', value: formatEnum(site.siteKind), hint: `${site.providerFamily} / ${site.compatibilitySurface}` },
            { key: 'status', label: '状态', value: <StatusBadge tone={site.active ? 'success' : 'warning'}>{site.active ? '启用' : '停用'}</StatusBadge>, hint: site.healthState },
            { key: 'counts', label: '模型与凭证', value: `${site.modelCount} 模型 / ${site.linkedCredentialCount} 凭证`, hint: `刷新 ${formatInstant(site.refreshedAt)}` },
          ]}
        />
      </PageSection>

      <PageSection kicker="入口策略" title="调用与兼容画像">
        <InfoGrid
          columnsClassName="md:grid-cols-2 xl:grid-cols-4"
          items={[
            { key: 'auth', label: '鉴权策略', value: site.authStrategy },
            { key: 'path', label: '路径策略', value: site.pathStrategy },
            { key: 'modelAddressing', label: '模型寻址', value: site.modelAddressingStrategy },
            { key: 'errorSchema', label: '错误结构', value: site.errorSchemaStrategy },
            { key: 'baseUrl', label: 'Base URL 匹配', value: site.baseUrlPattern ?? '未配置', className: 'xl:col-span-2' },
            { key: 'requirements', label: '凭证要求', value: summarizeList(site.credentialRequirements, '无', 4) },
            { key: 'protocols', label: '支持协议', value: summarizeList(site.supportedProtocols, '无', 4) },
          ]}
        />
        <div className="mt-4 rounded-2xl border border-border/60 bg-muted/20 p-4">
          <div className="mb-2 text-sm font-medium text-foreground">conversation profile</div>
          <pre className="max-h-80 overflow-auto whitespace-pre-wrap break-words rounded-xl bg-background p-3 text-xs text-muted-foreground">
            {JSON.stringify(site.conversationProfile ?? {}, null, 2)}
          </pre>
        </div>
      </PageSection>

      <PageSection kicker="模型能力" title="模型能力清单">
        {capabilitiesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : capabilities.length ? (
          <PaginatedRows items={capabilities} itemLabel="个模型">
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议</th>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">能力</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">执行后端</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">刷新时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((model) => (
                      <tr key={model.id} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-medium text-foreground">{model.modelName}</div>
                          <div className="text-xs text-muted-foreground">{model.modelKey}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{summarizeList(model.supportedProtocols, '无', 3)}</td>
                        <td className="px-4 py-3 text-muted-foreground">{summarizeModelTags(model).join(' / ') || '无'}</td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>{model.preferredBackend ?? '未指定'}</div>
                          <div className="text-xs">{model.capabilityLevel}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{formatInstant(model.sourceRefreshedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="暂无模型能力记录" />
        )}
      </PageSection>

      <PageSection kicker="Surface" title="入口能力矩阵">
        {surfaces.length ? (
          <PaginatedRows items={surfaces} itemLabel="个 surface">
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Surface</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">路径</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">能力等级</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">阻断原因</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map(([key, surface]) => (
                      <tr key={key} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-medium text-foreground">{key}</div>
                          <div className="text-xs text-muted-foreground">{surface.resourceType} / {surface.operation}</div>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={supportTone(surface.supportStatus)}>{surface.supportStatus ?? 'unknown'}</StatusBadge>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{surface.normalizedPath ?? '无'}</td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>执行：{surface.executionCapabilityLevel ?? '-'}</div>
                          <div>渲染：{surface.renderCapabilityLevel ?? '-'}</div>
                          <div>综合：{surface.overallCapabilityLevel ?? '-'}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{summarizeList(surface.blockerReasons, '无', 2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="暂无 surface 能力记录" />
        )}
      </PageSection>

      <PageSection kicker="Feature" title="特性解析">
        {features.length ? (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {features.map(([key, resolution]) => (
              <div key={key} className="rounded-2xl border border-border/60 bg-muted/20 p-4">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <div className="font-medium text-foreground">{key}</div>
                  <StatusBadge tone={supportTone(resolution.supportStatus)}>{resolution.supportStatus ?? resolution.effectiveLevel ?? 'unknown'}</StatusBadge>
                </div>
                <div className="text-xs leading-5 text-muted-foreground">
                  <div>声明：{resolution.declaredLevel ?? '-'}</div>
                  <div>实现：{resolution.implementedLevel ?? '-'}</div>
                  <div>有效：{resolution.effectiveLevel ?? '-'}</div>
                  <div>阻断：{summarizeList(resolution.blockedReasons, '无', 2)}</div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState title="暂无特性解析记录" />
        )}
      </PageSection>
    </div>
  )
}

function summarizeModelTags(model: SiteModelCapability) {
  const tags: string[] = []
  if (model.supportsChat) tags.push('Chat')
  if (model.supportsTools) tags.push('Tools')
  if (model.supportsImageInput) tags.push('Image')
  if (model.supportsEmbeddings) tags.push('Embeddings')
  if (model.supportsCache) tags.push('Cache')
  if (model.supportsThinking) tags.push('Thinking')
  if (model.supportsVisibleReasoning) tags.push('VisibleReasoning')
  if (model.supportsReasoningReuse) tags.push('ReasoningReuse')
  return tags
}

function summarizeList(items: string[], fallback: string, maxItems = 2) {
  const normalized = items.map((item) => item.trim()).filter(Boolean)
  if (!normalized.length) {
    return fallback
  }
  if (normalized.length <= maxItems) {
    return normalized.join(', ')
  }
  return `${normalized.slice(0, maxItems).join(', ')} +${normalized.length - maxItems}`
}

function formatEnum(value: string) {
  return value.replaceAll('_', ' ')
}

function supportTone(value?: string | null) {
  const normalized = (value ?? '').toLowerCase()
  if (normalized === 'native' || normalized === 'ready' || normalized === 'healthy') return 'success'
  if (normalized === 'emulated' || normalized === 'degraded') return 'warning'
  if (normalized === 'blocked' || normalized === 'unsupported' || normalized === 'failed') return 'danger'
  return 'neutral'
}
