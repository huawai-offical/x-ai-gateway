import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiRequest } from '../../lib/api'
import type { OutboundDelivery } from '../integrations/types'

type AlertRule = {
  id: number
  ruleName: string
  metricKey: string
  comparisonOperator: string
  thresholdValue: number
  severity: string
}

type AlertEvent = {
  id: number
  title: string
  severity: string
  status: string
  message: string
  entityType?: string | null
  entityRef?: string | null
}

type RouteGuardPolicy = {
  id: number
  policyName: string
  targetType: string
  providerType?: string | null
  siteProfileId?: number | null
  credentialId?: number | null
  accountId?: number | null
  proxyId?: number | null
  policyMode: string
  actionType: string
  ttlSeconds?: number | null
  effectiveUntil?: string | null
  priority: number
  enabled: boolean
  description?: string | null
}

type AutoActionRule = {
  id: number
  ruleName: string
  eventType: string
  severity?: string | null
  entityType?: string | null
  actionType: string
  ttlSeconds?: number | null
  recoveryMode: string
  enabled: boolean
  description?: string | null
}

type QuarantineRecord = {
  id: number
  targetType: string
  providerType?: string | null
  siteProfileId?: number | null
  credentialId?: number | null
  accountId?: number | null
  proxyId?: number | null
  sourceRuleId?: number | null
  sourceEventId?: number | null
  actionType: string
  recoveryMode: string
  reason: string
  status: string
  startedAt: string
  expiresAt?: string | null
  releasedAt?: string | null
  releaseReason?: string | null
}

type CredentialHealthScore = {
  credentialId: number
  credentialName: string
  providerType: string
  siteProfileId?: number | null
  proxyId?: number | null
  active: boolean
  score: number
  healthState: string
  reason?: string | null
  effectiveUntil?: string | null
}

type SiteHealthScore = {
  siteProfileId: number
  profileCode: string
  displayName: string
  providerFamily: string
  siteKind: string
  active: boolean
  score: number
  healthState: string
  reason?: string | null
  activeCredentialCount: number
  blockedCredentialCount: number
  effectiveUntil?: string | null
}

type GovernanceHealthScores = {
  sites: SiteHealthScore[]
  credentials: CredentialHealthScore[]
}

type AlertSilence = {
  id: number
  silenceName: string
  eventType?: string | null
  severity?: string | null
  entityType?: string | null
  entityRef?: string | null
  startsAt?: string | null
  endsAt?: string | null
  enabled: boolean
  reason?: string | null
}

const targetOptions = ['PROVIDER_TYPE', 'SITE_PROFILE', 'CREDENTIAL', 'ACCOUNT', 'PROXY']
const providerOptions = ['OPENAI_DIRECT', 'OPENAI_COMPATIBLE', 'GEMINI_DIRECT', 'ANTHROPIC_DIRECT', 'OLLAMA_DIRECT']
const policyModeOptions = ['ENFORCE', 'OVERRIDE_ALLOW', 'OVERRIDE_BLOCK']
const actionOptions = ['NONE', 'QUARANTINE', 'COOLDOWN', 'DRAIN']
const recoveryModeOptions = ['AUTO_RESUME', 'MANUAL_RESUME']
const entityTypeOptions = ['PROVIDER_TYPE', 'SITE_PROFILE', 'CREDENTIAL', 'ACCOUNT', 'PROXY']

function toNullableNumber(value: string) {
  return value.trim() ? Number(value) : null
}

function toNullableString(value: string) {
  return value.trim() ? value.trim() : null
}

function buildTargetDescription(item: RouteGuardPolicy | QuarantineRecord) {
  switch (item.targetType) {
    case 'PROVIDER_TYPE':
      return item.providerType ?? '-'
    case 'SITE_PROFILE':
      return `siteProfileId=${item.siteProfileId ?? '-'}`
    case 'CREDENTIAL':
      return `credentialId=${item.credentialId ?? '-'}`
    case 'ACCOUNT':
      return `accountId=${item.accountId ?? '-'}`
    case 'PROXY':
      return `proxyId=${item.proxyId ?? '-'}`
    default:
      return '-'
  }
}

export function OpsAlertsPage() {
  const queryClient = useQueryClient()
  const [ruleName, setRuleName] = useState('')
  const [metricKey, setMetricKey] = useState('qps')
  const [thresholdValue, setThresholdValue] = useState('1')

  const [policyName, setPolicyName] = useState('')
  const [targetType, setTargetType] = useState('CREDENTIAL')
  const [providerType, setProviderType] = useState('OPENAI_DIRECT')
  const [siteProfileId, setSiteProfileId] = useState('')
  const [credentialId, setCredentialId] = useState('')
  const [accountId, setAccountId] = useState('')
  const [proxyId, setProxyId] = useState('')
  const [policyMode, setPolicyMode] = useState('ENFORCE')
  const [policyActionType, setPolicyActionType] = useState('QUARANTINE')
  const [policyTtlSeconds, setPolicyTtlSeconds] = useState('300')
  const [policyPriority, setPolicyPriority] = useState('100')

  const [autoRuleName, setAutoRuleName] = useState('')
  const [autoEventType, setAutoEventType] = useState('REQUEST_ERROR_RATIO')
  const [autoSeverity, setAutoSeverity] = useState('HIGH')
  const [autoEntityType, setAutoEntityType] = useState('CREDENTIAL')
  const [autoActionType, setAutoActionType] = useState('QUARANTINE')
  const [autoTtlSeconds, setAutoTtlSeconds] = useState('300')
  const [autoRecoveryMode, setAutoRecoveryMode] = useState('AUTO_RESUME')
  const [silenceName, setSilenceName] = useState('')
  const [silenceEventType, setSilenceEventType] = useState('REQUEST_ERROR_RATIO')
  const [silenceSeverity, setSilenceSeverity] = useState('HIGH')
  const [silenceEntityType, setSilenceEntityType] = useState('CREDENTIAL')
  const [silenceEntityRef, setSilenceEntityRef] = useState('')
  const [silenceStartsAt, setSilenceStartsAt] = useState('')
  const [silenceEndsAt, setSilenceEndsAt] = useState('')
  const [silenceReason, setSilenceReason] = useState('')

  const rulesQuery = useQuery({
    queryKey: ['ops-alert-rules'],
    queryFn: () => apiRequest<AlertRule[]>('/admin/ops/alerts/rules'),
  })
  const alertsQuery = useQuery({
    queryKey: ['ops-alert-events'],
    queryFn: () => apiRequest<AlertEvent[]>('/admin/ops/alerts?status=OPEN'),
  })
  const routeGuardsQuery = useQuery({
    queryKey: ['ops-route-guards'],
    queryFn: () => apiRequest<RouteGuardPolicy[]>('/admin/ops/policies/route-guards'),
  })
  const autoActionsQuery = useQuery({
    queryKey: ['ops-auto-actions'],
    queryFn: () => apiRequest<AutoActionRule[]>('/admin/ops/policies/auto-actions'),
  })
  const quarantinesQuery = useQuery({
    queryKey: ['ops-quarantines'],
    queryFn: () => apiRequest<QuarantineRecord[]>('/admin/ops/quarantines'),
  })
  const healthScoresQuery = useQuery({
    queryKey: ['ops-health-scores'],
    queryFn: () => apiRequest<GovernanceHealthScores>('/admin/ops/health-scores'),
  })
  const silencesQuery = useQuery({
    queryKey: ['ops-alert-silences'],
    queryFn: () => apiRequest<AlertSilence[]>('/admin/ops/alerts/silences'),
  })
  const outboundDeliveriesQuery = useQuery({
    queryKey: ['ops-outbound-deliveries'],
    queryFn: () => apiRequest<OutboundDelivery[]>('/admin/integrations/deliveries'),
  })

  const createAlertRuleMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/ops/alerts/rules', {
        method: 'POST',
        body: JSON.stringify({
          ruleName,
          metricKey,
          comparisonOperator: '>',
          thresholdValue: Number(thresholdValue),
          severity: 'HIGH',
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ops-alert-rules'] })
      setRuleName('')
    },
  })

  const createRouteGuardMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/ops/policies/route-guards', {
        method: 'POST',
        body: JSON.stringify({
          policyName,
          targetType,
          providerType: targetType === 'PROVIDER_TYPE' ? providerType : null,
          siteProfileId: targetType === 'SITE_PROFILE' ? toNullableNumber(siteProfileId) : null,
          credentialId: targetType === 'CREDENTIAL' ? toNullableNumber(credentialId) : null,
          accountId: targetType === 'ACCOUNT' ? toNullableNumber(accountId) : null,
          proxyId: targetType === 'PROXY' ? toNullableNumber(proxyId) : null,
          policyMode,
          actionType: policyActionType,
          ttlSeconds: toNullableNumber(policyTtlSeconds),
          priority: Number(policyPriority),
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ops-route-guards'] })
      setPolicyName('')
      setSiteProfileId('')
      setCredentialId('')
      setAccountId('')
      setProxyId('')
    },
  })

  const createAutoActionMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/ops/policies/auto-actions', {
        method: 'POST',
        body: JSON.stringify({
          ruleName: autoRuleName,
          eventType: autoEventType,
          severity: autoSeverity || null,
          entityType: autoEntityType || null,
          actionType: autoActionType,
          ttlSeconds: toNullableNumber(autoTtlSeconds),
          recoveryMode: autoRecoveryMode,
          enabled: true,
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ops-auto-actions'] })
      setAutoRuleName('')
    },
  })

  const ackMutation = useMutation({
    mutationFn: (id: number) => apiRequest(`/admin/ops/alerts/${id}/ack`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-alert-events'] }),
  })
  const createSilenceMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/ops/alerts/silences', {
        method: 'POST',
        body: JSON.stringify({
          silenceName,
          eventType: toNullableString(silenceEventType),
          severity: toNullableString(silenceSeverity),
          entityType: toNullableString(silenceEntityType),
          entityRef: toNullableString(silenceEntityRef),
          startsAt: toNullableString(silenceStartsAt),
          endsAt: toNullableString(silenceEndsAt),
          enabled: true,
          reason: toNullableString(silenceReason),
        }),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ops-alert-silences'] })
      setSilenceName('')
      setSilenceEntityRef('')
      setSilenceStartsAt('')
      setSilenceEndsAt('')
      setSilenceReason('')
    },
  })

  const releaseMutation = useMutation({
    mutationFn: (id: number) =>
      apiRequest(`/admin/ops/quarantines/${id}/release`, {
        method: 'POST',
        body: JSON.stringify({ releaseReason: 'manual-release-from-ui' }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ops-quarantines'] }),
  })

  const handleAlertRuleSubmit = (event: FormEvent) => {
    event.preventDefault()
    createAlertRuleMutation.mutate()
  }

  const handleRouteGuardSubmit = (event: FormEvent) => {
    event.preventDefault()
    createRouteGuardMutation.mutate()
  }

  const handleAutoActionSubmit = (event: FormEvent) => {
    event.preventDefault()
    createAutoActionMutation.mutate()
  }

  const handleSilenceSubmit = (event: FormEvent) => {
    event.preventDefault()
    createSilenceMutation.mutate()
  }

  return (
    <section className="page-grid">
      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Health scores</p>
          <h2>治理健康分</h2>
          <p className="empty-state">把治理策略、自动隔离与冷却状态聚合成可解释的站点 / 凭证健康视图。</p>
        </div>
        <div className="card-list">
          {healthScoresQuery.data?.sites.map((site: SiteHealthScore) => (
            <div key={site.siteProfileId} className="detail-card">
              <strong>{site.displayName}</strong>
              <span>{site.siteKind} / score {site.score}</span>
              <span>{site.healthState} · active {site.activeCredentialCount} / blocked {site.blockedCredentialCount}</span>
              <span>{site.reason ?? '站点当前无治理阻断。'}</span>
            </div>
          ))}
          {healthScoresQuery.data?.credentials.map((item: CredentialHealthScore) => (
            <div key={item.credentialId} className="detail-card">
              <strong>{item.credentialName}</strong>
              <span>{item.providerType} / score {item.score}</span>
              <span>{item.healthState}{item.siteProfileId ? ` · siteProfileId=${item.siteProfileId}` : ''}</span>
              <span>{item.reason ?? '凭证当前可参与路由。'}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Alert rules</p>
          <h2>告警规则</h2>
          <p className="empty-state">负责发现异常，自动动作会基于这些事件继续触发治理闭环。</p>
        </div>
        <form className="inline-form" onSubmit={handleAlertRuleSubmit}>
          <input value={ruleName} onChange={(e) => setRuleName(e.target.value)} placeholder="规则名称" />
          <input value={metricKey} onChange={(e) => setMetricKey(e.target.value)} placeholder="metric key" />
          <input value={thresholdValue} onChange={(e) => setThresholdValue(e.target.value)} placeholder="阈值" />
          <button type="submit">创建告警规则</button>
        </form>
        <div className="card-list">
          {rulesQuery.data?.map((rule: AlertRule) => (
            <div key={rule.id} className="detail-card">
              <strong>{rule.ruleName}</strong>
              <span>{rule.metricKey}</span>
              <span>{rule.comparisonOperator} {rule.thresholdValue}</span>
              <span>{rule.severity}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Outbound delivery</p>
          <h2>外发投递状态</h2>
          <p className="empty-state">显示最近的外部通知投递结果，帮助判断告警与治理动作是否已经同步到外部协作链路。</p>
        </div>
        <div className="card-list">
          {(outboundDeliveriesQuery.data ?? []).slice(0, 5).map((delivery: OutboundDelivery) => (
            <div key={delivery.id} className="detail-card">
              <strong>{delivery.eventType}</strong>
              <span>{delivery.deliveryStatus} / attempt {delivery.attemptCount}</span>
              <span>{delivery.entityType ?? 'SYSTEM'} / {delivery.entityRef ?? '-'}</span>
              <span>{delivery.responseSummary ?? delivery.lastError ?? '等待投递结果'}</span>
            </div>
          ))}
          {!outboundDeliveriesQuery.data?.length ? <p className="empty-state">当前没有外发投递记录。</p> : null}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Route guards</p>
          <h2>路由守卫</h2>
          <p className="empty-state">手动治理入口，可按 provider/site/credential/account/proxy 直接阻断或临时隔离。</p>
        </div>
        <form className="inline-form" onSubmit={handleRouteGuardSubmit}>
          <input value={policyName} onChange={(event) => setPolicyName(event.target.value)} placeholder="策略名称" />
          <select value={targetType} onChange={(event) => setTargetType(event.target.value)}>
            {targetOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          {targetType === 'PROVIDER_TYPE' ? (
            <select value={providerType} onChange={(event) => setProviderType(event.target.value)}>
              {providerOptions.map((option) => <option key={option} value={option}>{option}</option>)}
            </select>
          ) : null}
          {targetType === 'SITE_PROFILE' ? (
            <input value={siteProfileId} onChange={(event) => setSiteProfileId(event.target.value)} placeholder="siteProfileId" />
          ) : null}
          {targetType === 'CREDENTIAL' ? (
            <input value={credentialId} onChange={(event) => setCredentialId(event.target.value)} placeholder="credentialId" />
          ) : null}
          {targetType === 'ACCOUNT' ? (
            <input value={accountId} onChange={(event) => setAccountId(event.target.value)} placeholder="accountId" />
          ) : null}
          {targetType === 'PROXY' ? (
            <input value={proxyId} onChange={(event) => setProxyId(event.target.value)} placeholder="proxyId" />
          ) : null}
          <select value={policyMode} onChange={(event) => setPolicyMode(event.target.value)}>
            {policyModeOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <select value={policyActionType} onChange={(event) => setPolicyActionType(event.target.value)}>
            {actionOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <input value={policyTtlSeconds} onChange={(event) => setPolicyTtlSeconds(event.target.value)} placeholder="ttl seconds" />
          <input value={policyPriority} onChange={(event) => setPolicyPriority(event.target.value)} placeholder="priority" />
          <button type="submit">创建路由守卫</button>
        </form>
        <div className="card-list">
          {routeGuardsQuery.data?.map((policy: RouteGuardPolicy) => (
            <div key={policy.id} className="detail-card">
              <strong>{policy.policyName}</strong>
              <span>{policy.targetType} · {buildTargetDescription(policy)}</span>
              <span>{policy.policyMode} / {policy.actionType}</span>
              <span>{policy.effectiveUntil ? `生效至 ${policy.effectiveUntil}` : '持续生效'}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Auto actions</p>
          <h2>自动动作</h2>
          <p className="empty-state">把告警事件映射为 quarantine/cooldown 动作，形成自动治理闭环。</p>
        </div>
        <form className="inline-form" onSubmit={handleAutoActionSubmit}>
          <input value={autoRuleName} onChange={(event) => setAutoRuleName(event.target.value)} placeholder="规则名称" />
          <input value={autoEventType} onChange={(event) => setAutoEventType(event.target.value)} placeholder="eventType" />
          <select value={autoSeverity} onChange={(event) => setAutoSeverity(event.target.value)}>
            <option value="">ANY_SEVERITY</option>
            {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <select value={autoEntityType} onChange={(event) => setAutoEntityType(event.target.value)}>
            <option value="">ANY_ENTITY</option>
            {entityTypeOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <select value={autoActionType} onChange={(event) => setAutoActionType(event.target.value)}>
            {actionOptions.filter((option) => option !== 'NONE').map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <input value={autoTtlSeconds} onChange={(event) => setAutoTtlSeconds(event.target.value)} placeholder="ttl seconds" />
          <select value={autoRecoveryMode} onChange={(event) => setAutoRecoveryMode(event.target.value)}>
            {recoveryModeOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <button type="submit">创建自动动作</button>
        </form>
        <div className="card-list">
          {autoActionsQuery.data?.map((rule: AutoActionRule) => (
            <div key={rule.id} className="detail-card">
              <strong>{rule.ruleName}</strong>
              <span>{rule.eventType} / {rule.entityType ?? 'ANY_ENTITY'}</span>
              <span>{rule.actionType} / {rule.recoveryMode}</span>
              <span>{rule.ttlSeconds ? `${rule.ttlSeconds}s` : '无 TTL'}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Alert silences</p>
          <h2>告警静默</h2>
          <p className="empty-state">在维护窗口或可接受噪音期内，显式压低告警可见性，避免误触发治理动作。</p>
        </div>
        <form className="inline-form" onSubmit={handleSilenceSubmit}>
          <input value={silenceName} onChange={(event) => setSilenceName(event.target.value)} placeholder="silence 名称" />
          <input value={silenceEventType} onChange={(event) => setSilenceEventType(event.target.value)} placeholder="eventType" />
          <select value={silenceSeverity} onChange={(event) => setSilenceSeverity(event.target.value)}>
            {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <select value={silenceEntityType} onChange={(event) => setSilenceEntityType(event.target.value)}>
            {entityTypeOptions.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
          <input value={silenceEntityRef} onChange={(event) => setSilenceEntityRef(event.target.value)} placeholder="entityRef" />
          <input value={silenceStartsAt} onChange={(event) => setSilenceStartsAt(event.target.value)} placeholder="startsAt (ISO8601)" />
          <input value={silenceEndsAt} onChange={(event) => setSilenceEndsAt(event.target.value)} placeholder="endsAt (ISO8601)" />
          <input value={silenceReason} onChange={(event) => setSilenceReason(event.target.value)} placeholder="reason" />
          <button type="submit">创建告警静默</button>
        </form>
        <div className="card-list">
          {silencesQuery.data?.map((silence: AlertSilence) => (
            <div key={silence.id} className="detail-card">
              <strong>{silence.silenceName}</strong>
              <span>{silence.eventType ?? 'ANY_EVENT'} / {silence.entityType ?? 'ANY_ENTITY'}</span>
              <span>{silence.entityRef ?? 'ANY_REF'} · {silence.severity ?? 'ANY_SEVERITY'}</span>
              <span>{silence.reason ?? '无额外说明'}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="panel">
        <div className="panel-head">
          <p className="panel-kicker">Alert events</p>
          <h2>打开中的告警</h2>
        </div>
        <div className="card-list">
          {alertsQuery.data?.map((alert: AlertEvent) => (
            <div key={alert.id} className="detail-card">
              <strong>{alert.title}</strong>
              <span>{alert.severity} / {alert.status}</span>
              <span>{alert.message}</span>
              <span>{alert.entityType ?? '-'} · {alert.entityRef ?? '-'}</span>
              <button type="button" onClick={() => ackMutation.mutate(alert.id)}>Ack</button>
            </div>
          ))}
        </div>
      </div>

      <div className="panel panel-wide">
        <div className="panel-head">
          <p className="panel-kicker">Quarantines</p>
          <h2>隔离记录</h2>
          <p className="empty-state">显示自动动作和手动守卫生效后的治理结果，可在这里手动 release。</p>
        </div>
        <div className="card-list">
          {quarantinesQuery.data?.map((item: QuarantineRecord) => (
            <div key={item.id} className="detail-card">
              <strong>{item.targetType} · {buildTargetDescription(item)}</strong>
              <span>{item.actionType} / {item.status}</span>
              <span>{item.reason}</span>
              <span>{item.expiresAt ? `到期时间 ${item.expiresAt}` : '无自动恢复时间'}</span>
              {item.status === 'ACTIVE' ? (
                <button type="button" onClick={() => releaseMutation.mutate(item.id)}>Release</button>
              ) : null}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
