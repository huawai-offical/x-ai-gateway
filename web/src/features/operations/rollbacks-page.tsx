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

type RollbackJob = {
  id: number
  status: string
  message: string
}

export function RollbacksPage() {
  const queryClient = useQueryClient()
  const [upgradeJobId, setUpgradeJobId] = useState('')
  const [releaseArtifactId, setReleaseArtifactId] = useState('')
  const [backupJobId, setBackupJobId] = useState('')
  const rollbacksQuery = useQuery({
    queryKey: ['rollbacks'],
    queryFn: () => apiRequest<RollbackJob[]>('/admin/rollbacks'),
  })
  const rollbackMutation = useMutation({
    mutationFn: () => apiRequest(`/admin/rollbacks?upgradeJobId=${upgradeJobId}&releaseArtifactId=${releaseArtifactId}&backupJobId=${backupJobId}`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['rollbacks'] }),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="回滚" title="回滚任务">
        <div className="grid gap-4 md:grid-cols-3">
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">升级任务 ID</span>
            <Input value={upgradeJobId} onChange={(event) => setUpgradeJobId(event.target.value)} placeholder="升级任务 ID" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">发布制品 ID</span>
            <Input value={releaseArtifactId} onChange={(event) => setReleaseArtifactId(event.target.value)} placeholder="发布制品 ID" />
          </label>
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-foreground">备份任务 ID</span>
            <Input value={backupJobId} onChange={(event) => setBackupJobId(event.target.value)} placeholder="备份任务 ID" />
          </label>
        </div>
        <Button type="button" onClick={() => rollbackMutation.mutate()}>执行回滚</Button>
        {rollbackMutation.error ? <InlineError error={rollbackMutation.error} title="执行回滚失败" /> : null}
      </PageSection>

      <PageSection kicker="回滚任务" title="回滚记录">
        {rollbacksQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : rollbacksQuery.error ? (
          <InlineError error={rollbacksQuery.error} title="回滚记录加载失败" />
        ) : rollbacksQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {rollbacksQuery.data.map((item: RollbackJob) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">回滚任务 #{item.id}</CardTitle>
                </CardHeader>
                <CardContent className="p-5">
                  <div className="mb-3 text-sm text-muted-foreground">{item.message}</div>
                  <StatusBadge tone={item.status === 'COMPLETED' ? 'success' : 'warning'}>{item.status}</StatusBadge>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有回滚任务" />
        )}
      </PageSection>
    </div>
  )
}
