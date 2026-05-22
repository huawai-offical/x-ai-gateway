import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type MaintenanceRun = {
  id: number
  runType: string
  status: string
  dryRun: boolean
  confirmRequired: boolean
  confirmed: boolean
  artifactPath?: string | null
  artifactChecksum?: string | null
  actor?: string | null
  durationMs: number
  detailJson?: string | null
  errorMessage?: string | null
  createdAt?: string | null
}

export function MaintenanceRunsPage() {
  const queryClient = useQueryClient()
  const [detail, setDetail] = useState<MaintenanceRun | null>(null)
  const runsQuery = useQuery({
    queryKey: ['operations', 'maintenance-runs'],
    queryFn: () => apiRequest<MaintenanceRun[]>('/admin/operations/maintenance-runs'),
  })
  const runMutation = useMutation({
    mutationFn: ({ runType, dryRun, confirm }: { runType: string; dryRun: boolean; confirm?: boolean }) =>
      apiRequest<MaintenanceRun>('/admin/operations/maintenance-runs', {
        method: 'POST',
        body: JSON.stringify({ runType, dryRun, confirm, actor: 'console' }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['operations', 'maintenance-runs'] }),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageSection kicker="维护控制面" title="统一维护运行记录">
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" onClick={() => runMutation.mutate({ runType: 'PRECHECK', dryRun: true })}>执行预检</Button>
          <Button type="button" variant="outline" onClick={() => runMutation.mutate({ runType: 'BACKUP', dryRun: true })}>备份演练</Button>
          <Button type="button" onClick={() => runMutation.mutate({ runType: 'BACKUP', dryRun: false, confirm: true })}>创建备份工件</Button>
          <Button type="button" variant="outline" onClick={() => runMutation.mutate({ runType: 'RESTORE_DRY_RUN', dryRun: true })}>恢复演练</Button>
          <Button type="button" variant="outline" onClick={() => runMutation.mutate({ runType: 'UPGRADE_CHECK', dryRun: true })}>升级检查</Button>
          <Button type="button" variant="outline" onClick={() => runMutation.mutate({ runType: 'ROLLBACK_PLAN', dryRun: true })}>回滚计划</Button>
        </div>
        {runMutation.error ? <InlineError error={runMutation.error} title="维护动作执行失败" /> : null}
      </PageSection>

      <PageSection kicker="运行记录" title="维护运行记录">
        {runsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : runsQuery.error ? (
          <InlineError error={runsQuery.error} title="维护运行记录加载失败" />
        ) : runsQuery.data?.length ? (
          <PaginatedRows items={(runsQuery.data ?? []) as MaintenanceRun[]}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">类型</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">模式</th>
                      <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">校验和</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">创建时间</th>
                      <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((run) => (
                      <tr key={run.id} className="border-b border-border/40">
                        <td className="px-4 py-3 font-medium text-foreground">{run.runType}</td>
                        <td className="px-4 py-3"><StatusBadge tone={run.status === 'COMPLETED' ? 'success' : 'danger'}>{run.status}</StatusBadge></td>
                        <td className="px-4 py-3 text-muted-foreground">{run.dryRun ? '演练' : '已确认'}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{run.artifactChecksum ?? '-'}</td>
                        <td className="px-4 py-3 text-muted-foreground">{formatInstant(run.createdAt)}</td>
                        <td className="px-4 py-3"><Button type="button" size="sm" variant="outline" onClick={() => setDetail(run)}>详情</Button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="还没有维护运行记录" />
        )}
      </PageSection>

      <Dialog open={detail != null} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>维护运行详情</DialogTitle>
            <DialogDescription />
          </DialogHeader>
          {detail ? (
            <div className="space-y-4">
              <InfoGrid
                items={[
                  { key: 'id', label: '运行 ID', value: detail.id },
                  { key: 'type', label: '类型', value: detail.runType },
                  { key: 'actor', label: '执行人', value: detail.actor ?? '-' },
                  { key: 'duration', label: '耗时', value: `${detail.durationMs} ms` },
                  { key: 'path', label: '工件路径', value: detail.artifactPath ?? '-' },
                  { key: 'checksum', label: '校验和', value: detail.artifactChecksum ?? '-' },
                ]}
                columnsClassName="md:grid-cols-2"
              />
              <CodePanel title="详情 JSON" code={detail.detailJson ?? detail.errorMessage ?? '{}'} />
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}
