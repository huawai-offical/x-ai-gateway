import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'

type ProxyItem = {
  id: number
  proxyName: string
  proxyUrl: string
  active: boolean
  description?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export function ProxyDetailPage() {
  const { id } = useParams()
  const proxiesQuery = useQuery({
    queryKey: ['network-proxies'],
    queryFn: () => apiRequest<ProxyItem[]>('/admin/network/proxies'),
  })

  const current = useMemo(
    () => proxiesQuery.data?.find((item: ProxyItem) => String(item.id) === id),
    [id, proxiesQuery.data],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="代理详情"
        title={current?.proxyName ?? '代理详情'}
      >
        {proxiesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : proxiesQuery.error ? (
          <InlineError error={proxiesQuery.error} title="代理详情加载失败" />
        ) : current ? (
          <div className="flex flex-col gap-6">
            <InfoGrid
              items={[
                { key: 'url', label: '代理 URL', value: current.proxyUrl },
                { key: 'active', label: '启用状态', value: <StatusBadge tone={current.active ? 'success' : 'warning'}>{current.active ? '启用' : '停用'}</StatusBadge> },
                { key: 'description', label: '说明', value: current.description ?? '无' },
              ]}
              columnsClassName="md:grid-cols-3"
            />
          </div>
        ) : (
          <EmptyState title="未找到代理节点" />
        )}
      </PageSection>
    </div>
  )
}
