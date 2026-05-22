import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'

type BackupJob = {
  id: number
  status: string
  snapshotPath?: string | null
}

export function BackupsPage() {
  const queryClient = useQueryClient()
  const backupsQuery = useQuery({
    queryKey: ['backups'],
    queryFn: () => apiRequest<BackupJob[]>('/admin/backups'),
  })
  const createMutation = useMutation({
    mutationFn: (dryRun: boolean) => apiRequest('/admin/backups', { method: 'POST', body: JSON.stringify({ dryRun }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backups'] }),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="备份" title="备份与恢复">
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" onClick={() => createMutation.mutate(true)}>演练</Button>
          <Button type="button" onClick={() => createMutation.mutate(false)}>创建备份</Button>
        </div>
        {createMutation.error ? <InlineError error={createMutation.error} title="创建备份失败" /> : null}
      </PageSection>

      <PageSection kicker="备份任务" title="备份记录">
        {backupsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : backupsQuery.error ? (
          <InlineError error={backupsQuery.error} title="备份记录加载失败" />
        ) : backupsQuery.data?.length ? (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {backupsQuery.data.map((item: BackupJob) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">备份任务 #{item.id}</CardTitle>
                </CardHeader>
                <CardContent className="p-5">
                  <div className="mb-3 text-sm text-muted-foreground">{item.snapshotPath ?? '尚未生成快照路径'}</div>
                  <StatusBadge tone={item.status === 'COMPLETED' ? 'success' : 'warning'}>{item.status}</StatusBadge>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有备份任务" />
        )}
      </PageSection>
    </div>
  )
}
