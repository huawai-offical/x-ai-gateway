import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'
import type { OutboundDelivery } from '../integrations/types'

type MaintenanceWindow = {
  id: number
  windowName: string
  activeNow: boolean
}

type RecoveryCheckpoint = {
  id: number
  checkpointName: string
  verificationStatus?: string | null
}

type ReleaseArtifact = {
  id: number
  versionName: string
  artifactRef: string
  active: boolean
}

type PreflightCheck = {
  checkName: string
  status: string
  blocking: boolean
  message: string
}

type ApprovalRecord = {
  id: number
  decision: string
  actor: string
  reason?: string | null
}

type RolloutStage = {
  id: number
  stage: string
  status: string
  message?: string | null
}

type RollbackPlaybook = {
  id: number
  status: string
  recoveryCheckpointId?: number | null
  rollbackReleaseArtifactId?: number | null
  latestRollbackPlanId?: number | null
}

type ChangePlan = {
  id: number
  planName: string
  planType: string
  executionClass: string
  status: string
  releaseArtifactId?: number | null
  recoveryCheckpointId?: number | null
  maintenanceWindowId?: number | null
  requestedBy?: string | null
  approvedBy?: string | null
  manualOverride: boolean
  overrideReason?: string | null
  emergencyReason?: string | null
  riskLevel?: string | null
  currentStage?: string | null
  currentMessage?: string | null
  preflightChecks: PreflightCheck[]
  approvals: ApprovalRecord[]
  rolloutStages: RolloutStage[]
  rollbackPlaybook?: RollbackPlaybook | null
}

const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审批',
  READY: '可执行',
  RUNNING: '执行中',
  FAILED: '失败',
  ROLLING_BACK: '回滚中',
  ROLLED_BACK: '已回滚',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  CANCELED: '已取消',
}

export function ChangesPage() {
  const queryClient = useQueryClient()
  const [planName, setPlanName] = useState('')
  const [planType, setPlanType] = useState('SNAPSHOT')
  const [executionClass, setExecutionClass] = useState('DRY_RUN')
  const [requestedBy, setRequestedBy] = useState('console')
  const [releaseArtifactId, setReleaseArtifactId] = useState('')
  const [recoveryCheckpointId, setRecoveryCheckpointId] = useState('')
  const [maintenanceWindowId, setMaintenanceWindowId] = useState('')
  const [emergencyReason, setEmergencyReason] = useState('')
  const [overrideReason, setOverrideReason] = useState('')

  const changePlansQuery = useQuery({
    queryKey: ['operations', 'change-plans'],
    queryFn: () => apiRequest<ChangePlan[]>('/admin/operations/change-plans'),
  })
  const windowsQuery = useQuery({
    queryKey: ['operations', 'maintenance-windows'],
    queryFn: () => apiRequest<MaintenanceWindow[]>('/admin/operations/maintenance-windows'),
  })
  const checkpointsQuery = useQuery({
    queryKey: ['operations', 'checkpoints'],
    queryFn: () => apiRequest<RecoveryCheckpoint[]>('/admin/operations/recovery-checkpoints'),
  })
  const releaseArtifactsQuery = useQuery({
    queryKey: ['operations', 'release-artifacts'],
    queryFn: () => apiRequest<ReleaseArtifact[]>('/admin/operations/release-artifacts'),
  })
  const outboundDeliveriesQuery = useQuery({
    queryKey: ['operations', 'outbound-deliveries'],
    queryFn: () => apiRequest<OutboundDelivery[]>('/admin/integrations/deliveries?entityType=CHANGE_PLAN'),
  })

  const refreshPlans = () => queryClient.invalidateQueries({ queryKey: ['operations', 'change-plans'] })
  const refreshCheckpoints = () => queryClient.invalidateQueries({ queryKey: ['operations', 'checkpoints'] })

  const createMutation = useMutation({
    mutationFn: () =>
      apiRequest<ChangePlan>('/admin/operations/change-plans', {
        method: 'POST',
        body: JSON.stringify({
          planName,
          planType,
          executionClass,
          releaseArtifactId: releaseArtifactId ? Number(releaseArtifactId) : null,
          recoveryCheckpointId: recoveryCheckpointId ? Number(recoveryCheckpointId) : null,
          maintenanceWindowId: maintenanceWindowId ? Number(maintenanceWindowId) : null,
          requestedBy,
          manualOverride: Boolean(overrideReason),
          overrideReason: overrideReason || null,
          emergencyReason: emergencyReason || null,
        }),
      }),
    onSuccess: () => {
      refreshPlans()
      refreshCheckpoints()
      setPlanName('')
      setReleaseArtifactId('')
      setRecoveryCheckpointId('')
      setMaintenanceWindowId('')
      setEmergencyReason('')
      setOverrideReason('')
    },
  })

  const approveMutation = useMutation({
    mutationFn: (planId: number) =>
      apiRequest<ChangePlan>(`/admin/operations/change-plans/${planId}/approve`, {
        method: 'POST',
        body: JSON.stringify({ approvedBy: requestedBy || 'console', reason: '控制台审批通过' }),
      }),
    onSuccess: refreshPlans,
  })

  const executeMutation = useMutation({
    mutationFn: (planId: number) =>
      apiRequest<ChangePlan>(`/admin/operations/change-plans/${planId}/execute`, {
        method: 'POST',
        body: JSON.stringify({ actor: requestedBy || 'console' }),
      }),
    onSuccess: () => {
      refreshPlans()
      refreshCheckpoints()
    },
  })

  const cancelMutation = useMutation({
    mutationFn: (planId: number) =>
      apiRequest<ChangePlan>(`/admin/operations/change-plans/${planId}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ actor: requestedBy || 'console', reason: '控制台取消' }),
      }),
    onSuccess: refreshPlans,
  })

  const windowsById = useMemo(
    () => new Map<number, MaintenanceWindow>((windowsQuery.data ?? []).map((window: MaintenanceWindow) => [window.id, window])),
    [windowsQuery.data],
  )
  const checkpointsById = useMemo(
    () => new Map<number, RecoveryCheckpoint>((checkpointsQuery.data ?? []).map((checkpoint: RecoveryCheckpoint) => [checkpoint.id, checkpoint])),
    [checkpointsQuery.data],
  )
  const releasesById = useMemo(
    () => new Map<number, ReleaseArtifact>((releaseArtifactsQuery.data ?? []).map((release: ReleaseArtifact) => [release.id, release])),
    [releaseArtifactsQuery.data],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="变更编排"
        title="统一变更编排"
      >
        <InfoGrid
          items={[
            { key: 'windows', label: '维护窗口', value: `${windowsQuery.data?.length ?? 0} 个` },
            { key: 'checkpoints', label: '恢复快照', value: `${checkpointsQuery.data?.length ?? 0} 个` },
            { key: 'releases', label: '发布制品', value: `${releaseArtifactsQuery.data?.length ?? 0} 个` },
          ]}
          columnsClassName="md:grid-cols-3"
        />

        <div className="grid gap-5 xl:grid-cols-[minmax(0,1.22fr)_minmax(19rem,0.78fr)]">
          <div className="grid gap-5">
            <div className="rounded-3xl border border-border/60 bg-background/90 p-6 shadow-sm">
              <div className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">基础参数</div>
              <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">计划名称</span>
                  <Input value={planName} onChange={(event) => setPlanName(event.target.value)} placeholder="例如：主站升级窗口" />
                </label>
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">计划类型</span>
                  <select className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={planType} onChange={(event) => setPlanType(event.target.value)} aria-label="计划类型">
                    <option value="SNAPSHOT">{planTypeLabel('SNAPSHOT')}</option>
                    <option value="UPGRADE">{planTypeLabel('UPGRADE')}</option>
                    <option value="RESTORE">{planTypeLabel('RESTORE')}</option>
                    <option value="ROLLBACK">{planTypeLabel('ROLLBACK')}</option>
                  </select>
                </label>
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">执行类别</span>
                  <select className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={executionClass} onChange={(event) => setExecutionClass(event.target.value)} aria-label="执行类别">
                    <option value="DRY_RUN">{executionClassLabel('DRY_RUN')}</option>
                    <option value="MANUAL">{executionClassLabel('MANUAL')}</option>
                    <option value="AUTO_TRIGGERED">{executionClassLabel('AUTO_TRIGGERED')}</option>
                  </select>
                </label>
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">发起人</span>
                  <Input value={requestedBy} onChange={(event) => setRequestedBy(event.target.value)} placeholder="例如：console" />
                </label>
              </div>
            </div>

            <div className="rounded-3xl border border-border/60 bg-muted/20 p-6">
              <div className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">发布约束与恢复参数</div>
              <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">发布制品 ID</span>
                  <Input value={releaseArtifactId} onChange={(event) => setReleaseArtifactId(event.target.value)} placeholder="仅升级/回滚使用" disabled={planType !== 'UPGRADE' && planType !== 'ROLLBACK'} />
                </label>
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">恢复快照 ID</span>
                  <Input value={recoveryCheckpointId} onChange={(event) => setRecoveryCheckpointId(event.target.value)} placeholder="仅恢复/回滚使用" disabled={planType !== 'RESTORE' && planType !== 'ROLLBACK'} />
                </label>
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">维护窗口 ID</span>
                  <Input value={maintenanceWindowId} onChange={(event) => setMaintenanceWindowId(event.target.value)} placeholder="仅升级使用" disabled={planType !== 'UPGRADE'} />
                </label>
                <label className="flex flex-col gap-2">
                  <span className="text-sm font-medium text-foreground">应急原因</span>
                  <Input value={emergencyReason} onChange={(event) => setEmergencyReason(event.target.value)} placeholder="仅恢复/回滚使用" disabled={planType !== 'RESTORE' && planType !== 'ROLLBACK'} />
                </label>
                <label className="flex flex-col gap-2 md:col-span-2 xl:col-span-4">
                  <span className="text-sm font-medium text-foreground">人工覆写原因</span>
                  <Input value={overrideReason} onChange={(event) => setOverrideReason(event.target.value)} placeholder="如需人工覆写，请记录原因" />
                </label>
              </div>
            </div>
          </div>

          <div className="rounded-3xl border border-border/60 bg-card/92 p-6 shadow-sm">
            <div className="flex flex-col gap-5">
              <div className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">创建动作</div>
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
                <CompactFact label="计划类型" value={planTypeLabel(planType)} />
                <CompactFact label="执行类别" value={executionClassLabel(executionClass)} />
                <CompactFact label="维护窗口" value={maintenanceWindowId || '未指定'} />
                <CompactFact label="人工覆写" value={overrideReason ? '已填写' : '未填写'} />
              </div>
              <div className="border-t border-border/60 pt-4">
                <Button type="button" className="w-full" onClick={() => createMutation.mutate()} disabled={!planName}>
                  创建变更计划
                </Button>
              </div>
            </div>
          </div>
        </div>

        {(createMutation.error || changePlansQuery.error || windowsQuery.error || checkpointsQuery.error || releaseArtifactsQuery.error) ? (
          <InlineError
            error={createMutation.error ?? changePlansQuery.error ?? windowsQuery.error ?? checkpointsQuery.error ?? releaseArtifactsQuery.error}
            title="变更编排操作失败"
          />
        ) : null}
      </PageSection>

      <PageSection kicker="计划列表" title="变更计划列表">
        {changePlansQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : changePlansQuery.data?.length ? (
          <div className="grid gap-5">
            {changePlansQuery.data.map((plan: ChangePlan) => {
              const window = plan.maintenanceWindowId ? windowsById.get(plan.maintenanceWindowId) : null
              const checkpoint = plan.recoveryCheckpointId ? checkpointsById.get(plan.recoveryCheckpointId) : null
              const release = plan.releaseArtifactId ? releasesById.get(plan.releaseArtifactId) : null
              const needsApproval = plan.executionClass === 'MANUAL' && ['UPGRADE', 'RESTORE', 'ROLLBACK'].includes(plan.planType)

              return (
                <Card key={plan.id} className="border-border/60 bg-card/92 shadow-sm">
                  <CardHeader className="gap-2 border-b border-border/60">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0">
                        <CardTitle className="text-base">{plan.planName}</CardTitle>
                        <div className="text-sm text-muted-foreground">{planTypeLabel(plan.planType)} / {executionClassLabel(plan.executionClass)}</div>
                      </div>
                      <StatusBadge tone={plan.status === 'COMPLETED' ? 'success' : plan.status === 'FAILED' ? 'danger' : 'warning'}>
                        {STATUS_TEXT[plan.status] ?? plan.status}
                      </StatusBadge>
                    </div>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-5 p-6 text-sm text-muted-foreground">
                    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
                      <CompactFact label="风险等级" value={plan.riskLevel ?? '-'} />
                      <CompactFact label="当前阶段" value={plan.currentStage ?? '-'} />
                      <CompactFact label="审批要求" value={needsApproval ? '需要审批' : '无需审批'} />
                      <CompactFact label="发起人" value={plan.requestedBy ?? '-'} />
                      <CompactFact label="维护窗口" value={window ? `${window.windowName}${window.activeNow ? '（命中）' : '（未命中）'}` : '-'} />
                      <CompactFact label="恢复快照" value={checkpoint ? `${checkpoint.checkpointName} / ${checkpoint.verificationStatus ?? '-'}` : '-'} />
                      <CompactFact label="发布制品" value={release ? release.versionName : '-'} />
                      <CompactFact label="人工覆写" value={plan.manualOverride ? '是' : '否'} />
                    </div>
                    <div className="rounded-2xl border border-border/60 bg-background/90 px-4 py-3 text-sm text-foreground">
                      当前说明：{plan.currentMessage ?? '-'}
                    </div>
                    <div className="flex flex-wrap gap-2 border-t border-border/60 pt-4">
                      {plan.status === 'PENDING_APPROVAL' ? (
                        <Button type="button" variant="outline" size="sm" onClick={() => approveMutation.mutate(plan.id)}>审批通过</Button>
                      ) : null}
                      {['READY', 'APPROVED', 'FAILED'].includes(plan.status) ? (
                        <Button type="button" variant="outline" size="sm" onClick={() => executeMutation.mutate(plan.id)}>执行计划</Button>
                      ) : null}
                      {!['COMPLETED', 'CANCELED', 'ROLLED_BACK'].includes(plan.status) ? (
                        <Button type="button" variant="outline" size="sm" onClick={() => cancelMutation.mutate(plan.id)}>取消计划</Button>
                      ) : null}
                    </div>
                    <details className="rounded-2xl border border-border/60 bg-muted/20 px-5 py-4">
                      <summary className="cursor-pointer font-medium text-foreground">展开阶段与策略明细</summary>
                      <div className="mt-4 flex flex-col gap-3">
                        {plan.preflightChecks.map((item: PreflightCheck) => (
                          <div key={`${plan.id}-${item.checkName}`}>{item.checkName}: {item.status} / {item.message}</div>
                        ))}
                        {plan.rolloutStages.map((item: RolloutStage) => (
                          <div key={item.id}>{item.stage}: {item.status}{item.message ? ` / ${item.message}` : ''}</div>
                        ))}
                        {plan.approvals.map((item: ApprovalRecord) => (
                          <div key={item.id}>审批记录：{item.decision} / {item.actor}{item.reason ? ` / ${item.reason}` : ''}</div>
                        ))}
                        {plan.rollbackPlaybook ? (
                          <div>
                            回滚预案 #{plan.rollbackPlaybook.id} / {plan.rollbackPlaybook.status}
                            {plan.rollbackPlaybook.latestRollbackPlanId ? ` / 回滚 #${plan.rollbackPlaybook.latestRollbackPlanId}` : ''}
                          </div>
                        ) : null}
                      </div>
                    </details>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        ) : (
          <EmptyState title="当前没有变更计划" />
        )}
      </PageSection>

      <PageSection kicker="外发状态" title="变更外发状态">
        {outboundDeliveriesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : outboundDeliveriesQuery.error ? (
          <InlineError error={outboundDeliveriesQuery.error} title="变更外发状态加载失败" />
        ) : (outboundDeliveriesQuery.data ?? []).length ? (
          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {(outboundDeliveriesQuery.data ?? []).slice(0, 5).map((delivery: OutboundDelivery) => (
              <Card key={delivery.id} className="border-border/60 bg-card/92 shadow-sm">
                  <CardHeader className="gap-2 border-b border-border/60">
                    <CardTitle className="text-base">{delivery.eventType}</CardTitle>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-4 p-5 text-sm text-muted-foreground">
                    <div className="text-foreground">{delivery.entityRef ?? 'CHANGE_PLAN'} / 通道 #{delivery.channelId}</div>
                    <StatusBadge tone={delivery.deliveryStatus === 'SUCCEEDED' ? 'success' : 'warning'}>
                      {delivery.deliveryStatus} / 尝试 {delivery.attemptCount}
                    </StatusBadge>
                    <div className="text-foreground">{delivery.responseSummary ?? delivery.lastError ?? '等待投递结果'}</div>
                  </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState title="当前没有变更外发记录" />
        )}
      </PageSection>
    </div>
  )
}

function CompactFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-border/60 bg-background/90 px-4 py-3.5">
      <div className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{label}</div>
      <div className="mt-1 text-sm text-foreground">{value}</div>
    </div>
  )
}

function planTypeLabel(type: string) {
  switch (type) {
    case 'SNAPSHOT':
      return '拍快照'
    case 'UPGRADE':
      return '升级'
    case 'RESTORE':
      return '恢复'
    case 'ROLLBACK':
      return '回滚'
    default:
      return type
  }
}

function executionClassLabel(value: string) {
  switch (value) {
    case 'DRY_RUN':
      return '演练'
    case 'MANUAL':
      return '人工执行'
    case 'AUTO_TRIGGERED':
      return '自动触发'
    default:
      return value
  }
}
