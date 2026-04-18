import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { apiRequest } from '../../lib/api'
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
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Change Plan</p>
          <h2>统一变更编排</h2>
        </div>
        <p className="panel-copy">
          所有快照、升级、恢复和回滚都统一通过 ChangePlan 进入申请、审批、执行、核验与审计闭环。
        </p>
        <div className="inline-form">
          <input value={planName} onChange={(event) => setPlanName(event.target.value)} placeholder="plan name" />
          <select value={planType} onChange={(event) => setPlanType(event.target.value)} aria-label="plan type">
            <option value="SNAPSHOT">SNAPSHOT</option>
            <option value="UPGRADE">UPGRADE</option>
            <option value="RESTORE">RESTORE</option>
            <option value="ROLLBACK">ROLLBACK</option>
          </select>
          <select value={executionClass} onChange={(event) => setExecutionClass(event.target.value)} aria-label="execution class">
            <option value="DRY_RUN">DRY_RUN</option>
            <option value="MANUAL">MANUAL</option>
            <option value="AUTO_TRIGGERED">AUTO_TRIGGERED</option>
          </select>
          <input value={requestedBy} onChange={(event) => setRequestedBy(event.target.value)} placeholder="requested by" />
        </div>
        <div className="inline-form">
          <input
            value={releaseArtifactId}
            onChange={(event) => setReleaseArtifactId(event.target.value)}
            placeholder="release artifact id"
            disabled={planType !== 'UPGRADE' && planType !== 'ROLLBACK'}
          />
          <input
            value={recoveryCheckpointId}
            onChange={(event) => setRecoveryCheckpointId(event.target.value)}
            placeholder="checkpoint id"
            disabled={planType !== 'RESTORE' && planType !== 'ROLLBACK'}
          />
          <input
            value={maintenanceWindowId}
            onChange={(event) => setMaintenanceWindowId(event.target.value)}
            placeholder="maintenance window id"
            disabled={planType !== 'UPGRADE'}
          />
        </div>
        <div className="inline-form">
          <input
            value={emergencyReason}
            onChange={(event) => setEmergencyReason(event.target.value)}
            placeholder="emergency reason"
            disabled={planType !== 'RESTORE' && planType !== 'ROLLBACK'}
          />
          <input value={overrideReason} onChange={(event) => setOverrideReason(event.target.value)} placeholder="override reason" />
          <button type="button" onClick={() => createMutation.mutate()} disabled={!planName}>
            创建变更计划
          </button>
        </div>
        <div className="detail-card-grid">
          <div className="detail-card">
            <strong>维护窗口</strong>
            <span>{windowsQuery.data?.length ?? 0} 个</span>
          </div>
          <div className="detail-card">
            <strong>Checkpoint</strong>
            <span>{checkpointsQuery.data?.length ?? 0} 个</span>
          </div>
          <div className="detail-card">
            <strong>Release Artifacts</strong>
            <span>{releaseArtifactsQuery.data?.length ?? 0} 个</span>
          </div>
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Plans</p>
          <h2>变更计划列表</h2>
        </div>
        <div className="card-list">
          {changePlansQuery.data?.map((plan: ChangePlan) => {
            const window = plan.maintenanceWindowId ? windowsById.get(plan.maintenanceWindowId) : null
            const checkpoint = plan.recoveryCheckpointId ? checkpointsById.get(plan.recoveryCheckpointId) : null
            const release = plan.releaseArtifactId ? releasesById.get(plan.releaseArtifactId) : null
            const needsApproval = plan.executionClass === 'MANUAL' && ['UPGRADE', 'RESTORE', 'ROLLBACK'].includes(plan.planType)

            return (
              <div key={plan.id} className="detail-card">
                <strong>{plan.planName}</strong>
                <span>{plan.planType} / {plan.executionClass}</span>
                <span>{STATUS_TEXT[plan.status] ?? plan.status}</span>
                <span>风险等级：{plan.riskLevel ?? '-'}</span>
                <span>当前阶段：{plan.currentStage ?? '-'}</span>
                <span>当前说明：{plan.currentMessage ?? '-'}</span>
                <span>审批要求：{needsApproval ? '需要审批' : '无需审批'}</span>
                <span>维护窗口：{window ? `${window.windowName}${window.activeNow ? '（命中）' : '（未命中）'}` : '-'}</span>
                <span>Checkpoint：{checkpoint ? `${checkpoint.checkpointName} / ${checkpoint.verificationStatus ?? '-'}` : '-'}</span>
                <span>Release：{release ? release.versionName : '-'}</span>
                <div className="inline-actions">
                  {plan.status === 'PENDING_APPROVAL' ? (
                    <button type="button" onClick={() => approveMutation.mutate(plan.id)}>审批通过</button>
                  ) : null}
                  {['READY', 'APPROVED', 'FAILED'].includes(plan.status) ? (
                    <button type="button" onClick={() => executeMutation.mutate(plan.id)}>执行计划</button>
                  ) : null}
                  {!['COMPLETED', 'CANCELED', 'ROLLED_BACK'].includes(plan.status) ? (
                    <button type="button" onClick={() => cancelMutation.mutate(plan.id)}>取消计划</button>
                  ) : null}
                </div>
                <details>
                  <summary>策略与阶段明细</summary>
                  <ul className="compact-list">
                    {plan.preflightChecks.map((item: PreflightCheck) => (
                      <li key={`${plan.id}-${item.checkName}`}>{item.checkName}: {item.status} / {item.message}</li>
                    ))}
                    {plan.rolloutStages.map((item: RolloutStage) => (
                      <li key={item.id}>{item.stage}: {item.status}{item.message ? ` / ${item.message}` : ''}</li>
                    ))}
                    {plan.approvals.map((item: ApprovalRecord) => (
                      <li key={item.id}>审批记录：{item.decision} / {item.actor}{item.reason ? ` / ${item.reason}` : ''}</li>
                    ))}
                    {plan.rollbackPlaybook ? (
                      <li>
                        rollback playbook #{plan.rollbackPlaybook.id} / {plan.rollbackPlaybook.status}
                        {plan.rollbackPlaybook.latestRollbackPlanId ? ` / rollback #${plan.rollbackPlaybook.latestRollbackPlanId}` : ''}
                      </li>
                    ) : null}
                  </ul>
                </details>
              </div>
            )
          })}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Outbound delivery</p>
          <h2>变更外发状态</h2>
        </div>
        <div className="card-list">
          {(outboundDeliveriesQuery.data ?? []).slice(0, 5).map((delivery: OutboundDelivery) => (
            <div key={delivery.id} className="detail-card">
              <strong>{delivery.eventType}</strong>
              <span>{delivery.deliveryStatus} / attempt {delivery.attemptCount}</span>
              <span>{delivery.entityRef ?? 'CHANGE_PLAN'} / channel #{delivery.channelId}</span>
              <span>{delivery.responseSummary ?? delivery.lastError ?? '等待投递结果'}</span>
            </div>
          ))}
          {!outboundDeliveriesQuery.data?.length ? <p className="empty-state">当前没有变更外发记录。</p> : null}
        </div>
      </div>
    </section>
  )
}
