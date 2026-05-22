import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'

type ReleaseArtifact = {
  id: number
  versionName: string
  artifactRef: string
}

type UpgradeJob = {
  id: number
  status: string
  message: string
}

export function UpgradesPage() {
  const queryClient = useQueryClient()
  const [versionName, setVersionName] = useState('')
  const [artifactRef, setArtifactRef] = useState('')
  const [targetReleaseArtifactId, setTargetReleaseArtifactId] = useState('')
  const releasesQuery = useQuery({
    queryKey: ['release-artifacts'],
    queryFn: () => apiRequest<ReleaseArtifact[]>('/admin/upgrades/releases'),
  })
  const upgradesQuery = useQuery({
    queryKey: ['upgrades'],
    queryFn: () => apiRequest<UpgradeJob[]>('/admin/upgrades'),
  })
  const releaseMutation = useMutation({
    mutationFn: () => apiRequest('/admin/upgrades/releases', { method: 'POST', body: JSON.stringify({ versionName, artifactRef }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['release-artifacts'] }),
  })
  const upgradeMutation = useMutation({
    mutationFn: () => apiRequest('/admin/upgrades', { method: 'POST', body: JSON.stringify({ targetReleaseArtifactId: Number(targetReleaseArtifactId), confirm: true }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['upgrades'] }),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="发布制品" title="发布制品">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)_auto]">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">版本</span>
            <Input value={versionName} onChange={(event) => setVersionName(event.target.value)} placeholder="版本号" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">制品引用</span>
            <Input value={artifactRef} onChange={(event) => setArtifactRef(event.target.value)} placeholder="制品引用" />
          </label>
          <div className="flex items-end">
            <Button type="button" onClick={() => releaseMutation.mutate()}>登记制品</Button>
          </div>
        </div>
        {releaseMutation.error ? <InlineError error={releaseMutation.error} title="登记发布制品失败" /> : null}

        {releasesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : releasesQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {releasesQuery.data.map((item: ReleaseArtifact) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.versionName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-2 p-5 text-sm text-muted-foreground">
                  <div>{item.artifactRef}</div>
                  <div>发布制品 #{item.id}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="还没有发布制品" />
        )}
      </PageSection>

      <PageSection kicker="升级" title="升级任务">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_auto]">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">目标发布 ID</span>
            <Input value={targetReleaseArtifactId} onChange={(event) => setTargetReleaseArtifactId(event.target.value)} placeholder="目标发布 ID" />
          </label>
          <div className="flex items-end">
            <Button type="button" onClick={() => upgradeMutation.mutate()}>执行升级</Button>
          </div>
        </div>
        {upgradeMutation.error ? <InlineError error={upgradeMutation.error} title="执行升级失败" /> : null}

        {upgradesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : upgradesQuery.error ? (
          <InlineError error={upgradesQuery.error} title="升级任务加载失败" />
        ) : upgradesQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {upgradesQuery.data.map((item: UpgradeJob) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">升级任务 #{item.id}</CardTitle>
                </CardHeader>
                <CardContent className="p-5">
                  <div className="mb-3 text-sm text-muted-foreground">{item.message}</div>
                  <StatusBadge tone={item.status === 'COMPLETED' ? 'success' : 'warning'}>{item.status}</StatusBadge>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有升级任务" />
        )}
      </PageSection>
    </div>
  )
}
