import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
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
import { type CapabilityMatrixRow } from './types'

const FEATURE_COLUMNS: Array<{ key: keyof CapabilityMatrixRow; label: string }> = [
  { key: 'supportsResponses', label: 'Responses' },
  { key: 'supportsEmbeddings', label: 'Embeddings' },
  { key: 'supportsAudio', label: 'Audio' },
  { key: 'supportsImages', label: 'Images' },
  { key: 'supportsModeration', label: 'Moderation' },
  { key: 'supportsFiles', label: 'Files' },
  { key: 'supportsUploads', label: 'Uploads' },
]

export function CapabilityMatrixPage() {
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const matrixQuery = useQuery({
    queryKey: ['provider-sites', 'capability-matrix'],
    queryFn: () => apiRequest<CapabilityMatrixRow[]>('/admin/provider-sites/capability-matrix'),
  })

  const rows = useMemo(
    () => [...((matrixQuery.data ?? []) as CapabilityMatrixRow[])].sort((left, right) => left.displayName.localeCompare(right.displayName)),
    [matrixQuery.data],
  )
  const filteredRows = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase()
    return rows.filter((row) => {
      const matchesStatus = statusFilter === 'ALL' || row.healthState === statusFilter
      const text = [
        row.displayName,
        row.profileCode,
        row.providerFamily,
        row.siteKind,
        row.compatibilitySurface,
        row.healthState,
      ].join(' ').toLowerCase()
      return matchesStatus && (!normalizedKeyword || text.includes(normalizedKeyword))
    })
  }, [keyword, rows, statusFilter])
  const statusOptions = useMemo(
    () => Array.from(new Set(rows.map((row) => row.healthState).filter(Boolean))).sort((left, right) => left.localeCompare(right)),
    [rows],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="厂商管理" title="API 入口能力矩阵">
        {matrixQuery.error ? <InlineError error={matrixQuery.error} title="能力矩阵加载失败" /> : null}
        {matrixQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : (
          <>
            <InfoGrid
              items={[
                { key: 'sites', label: 'API 入口', value: rows.length.toLocaleString('zh-CN') },
                { key: 'ready', label: 'Ready', value: rows.filter((row) => row.healthState === 'READY').length.toLocaleString('zh-CN') },
                { key: 'models', label: '模型记录', value: rows.reduce((sum, row) => sum + row.modelCount, 0).toLocaleString('zh-CN') },
                { key: 'credentials', label: '绑定凭证', value: rows.reduce((sum, row) => sum + row.linkedCredentialCount, 0).toLocaleString('zh-CN') },
              ]}
            />
            <div className="mt-5 grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">关键字</span>
                <input
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder="入口 / 协议 / 状态"
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">健康状态</span>
                <select
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value)}
                >
                  <option value="ALL">全部状态</option>
                  {statusOptions.map((status) => (
                    <option key={status} value={status}>{status}</option>
                  ))}
                </select>
              </label>
            </div>
          </>
        )}
      </PageSection>

      <PageSection kicker="矩阵" title="入口能力">
        {matrixQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : filteredRows.length ? (
          <PaginatedRows items={filteredRows} itemLabel="个入口">
            {({ pageItems }) => (
              <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
                <table className="min-w-[72rem] w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">API 入口</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">协议</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模型/凭证</th>
                      {FEATURE_COLUMNS.map((feature) => (
                        <th key={feature.key} className="w-[7%] border-b border-border/60 px-3 py-3 text-left font-medium text-muted-foreground">
                          {feature.label}
                        </th>
                      ))}
                      <th className="w-[11%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">刷新</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((row) => (
                      <tr key={row.siteProfileId} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <Link className="font-medium text-primary hover:underline" to={`/console/provider-sites/${row.siteProfileId}`}>
                            {row.displayName}
                          </Link>
                          <div className="text-xs text-muted-foreground">{row.profileCode}</div>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>{formatEnum(row.siteKind)}</div>
                          <div className="text-xs">{row.providerFamily}</div>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={statusTone(row.healthState)}>{row.healthState}</StatusBadge>
                          {row.blockedReason ? <div className="mt-1 text-xs text-muted-foreground">{row.blockedReason}</div> : null}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <div>{row.modelCount} 模型</div>
                          <div className="text-xs">{row.linkedCredentialCount} 凭证</div>
                        </td>
                        {FEATURE_COLUMNS.map((feature) => (
                          <td key={feature.key} className="px-3 py-3">
                            <StatusBadge tone={row[feature.key] ? 'success' : 'neutral'}>
                              {row[feature.key] ? '支持' : '不支持'}
                            </StatusBadge>
                          </td>
                        ))}
                        <td className="px-4 py-3 text-muted-foreground">{formatInstant(row.refreshedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="没有匹配的能力矩阵记录" />
        )}
      </PageSection>

      <div className="flex justify-end">
        <Link to="/console/provider-sites">
          <Button type="button" variant="outline">返回厂商管理</Button>
        </Link>
      </div>
    </div>
  )
}

function statusTone(value?: string | null) {
  if (value === 'READY' || value === 'HEALTHY') return 'success'
  if (value === 'UNKNOWN') return 'neutral'
  if (value === 'DEGRADED' || value === 'COOLDOWN') return 'warning'
  return 'danger'
}

function formatEnum(value: string) {
  return value.replaceAll('_', ' ')
}
