import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'

type RecoveryCheckpoint = {
  id: number
  checkpointName: string
  changePlanId?: number | null
  status: string
  verificationStatus?: string | null
  verificationMessage?: string | null
  metadataSnapshotPath?: string | null
  runtimeSnapshotPath?: string | null
  dataSnapshotPath?: string | null
}

export function CheckpointsPage() {
  const queryClient = useQueryClient()
  const checkpointsQuery = useQuery({
    queryKey: ['operations', 'checkpoints'],
    queryFn: () => apiRequest<RecoveryCheckpoint[]>('/admin/operations/recovery-checkpoints'),
  })

  const verifyMutation = useMutation({
    mutationFn: (checkpointId: number) =>
      apiRequest<RecoveryCheckpoint>(`/admin/operations/recovery-checkpoints/${checkpointId}/verify`, {
        method: 'POST',
        body: JSON.stringify({ verifiedBy: 'console' }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['operations', 'checkpoints'] }),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="恢复检查点" title="恢复检查点" />

      <PageSection kicker="检查点清单" title="检查点列表">
        {checkpointsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : checkpointsQuery.error ? (
          <InlineError error={checkpointsQuery.error} title="检查点列表加载失败" />
        ) : checkpointsQuery.data?.length ? (
          <div className="grid gap-4">
            {checkpointsQuery.data.map((item: RecoveryCheckpoint) => (
              <Card key={item.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">{item.checkpointName}</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-4 p-5">
                  <div className="text-sm text-muted-foreground">来源计划：{item.changePlanId ?? '-'}</div>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge>{`状态：${item.status}`}</StatusBadge>
                    <StatusBadge tone={item.verificationStatus === 'VERIFIED' ? 'success' : 'warning'}>
                      {`校验：${item.verificationStatus ?? '-'}`}
                    </StatusBadge>
                  </div>
                  <div className="text-sm text-muted-foreground">{item.verificationMessage ?? '-'}</div>
                  <div className="grid gap-2 rounded-2xl border border-border/60 bg-muted/20 p-4 text-sm text-muted-foreground md:grid-cols-3">
                    <div>metadata：{item.metadataSnapshotPath ?? '-'}</div>
                    <div>runtime：{item.runtimeSnapshotPath ?? '-'}</div>
                    <div>data：{item.dataSnapshotPath ?? '-'}</div>
                  </div>
                  <div>
                    <Button type="button" variant="outline" size="sm" onClick={() => verifyMutation.mutate(item.id)}>
                      校验检查点
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有恢复检查点" />
        )}
      </PageSection>
    </div>
  )
}
