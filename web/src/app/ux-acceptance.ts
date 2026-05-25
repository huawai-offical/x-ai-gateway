export type UxSurface = 'portal' | 'console'
export type UxViewportName = 'desktop' | 'mobile'
export type UxStateName = 'loading' | 'empty' | 'loaded' | 'error' | 'permissionDenied'

export type UxAcceptancePage = {
  id: string
  surface: UxSurface
  path: string
  ownerTask: string
  critical: boolean
  viewports: UxViewportName[]
  states: UxStateName[]
  emptyStateCta: string
  errorRecovery: string
  highRiskInputs: string[]
  destructiveActions: string[]
}

export type UxAcceptanceIssue = {
  pageId: string
  reason: string
}

export const uxViewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
] as const

export const requiredUxStates: UxStateName[] = ['loading', 'empty', 'loaded', 'error', 'permissionDenied']

export const highRiskInputRules = [
  {
    id: 'resource-picker',
    description: '账号分组、账号、用户、计划等资源必须优先使用 picker 或 combobox，避免用户手写裸 ID。',
  },
  {
    id: 'masked-secret',
    description: 'access token、refresh token、API key 和 webhook secret 必须默认掩码展示，并提供明确的 copy 或 reveal 语义。',
  },
  {
    id: 'field-array-validation',
    description: '模型、域名、规则条件和 allowlist 等多值输入必须有逐项校验和错误定位。',
  },
  {
    id: 'danger-confirm',
    description: '冻结、删除、停用、回滚和轮换等破坏性操作必须带确认语义、影响说明和禁用条件。',
  },
  {
    id: 'mobile-table-overflow',
    description: '宽表、日志和运行态列表在移动端必须提供横向滚动或列表化视图，操作列不能被挤压遮挡。',
  },
] as const

export const uxAcceptancePages: UxAcceptancePage[] = [
  {
    id: 'portal-home',
    surface: 'portal',
    path: '/portal',
    ownerTask: 'TASK-20260507-014',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '引导创建 Key、兑换额度或查看订阅。',
    errorRecovery: '说明门户会话或余额加载失败原因，并提供重新登录或重试。',
    highRiskInputs: ['masked-secret'],
    destructiveActions: [],
  },
  {
    id: 'portal-keys',
    surface: 'portal',
    path: '/portal/keys',
    ownerTask: 'TASK-20260507-014',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '无 Key 时提供创建入口。',
    errorRecovery: 'Key 列表失败时解释会话、权限或网络问题。',
    highRiskInputs: ['masked-secret', 'danger-confirm'],
    destructiveActions: ['rotate-key', 'disable-key'],
  },
  {
    id: 'console-account-groups',
    surface: 'console',
    path: '/console/account-groups',
    ownerTask: 'TASK-20260507-014',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '无账号分组时提供创建账号分组入口。',
    errorRecovery: '解释 provider、网络和权限导致的账号分组加载失败。',
    highRiskInputs: ['resource-picker', 'masked-secret', 'field-array-validation', 'danger-confirm'],
    destructiveActions: ['freeze-account', 'batch-import'],
  },
  {
    id: 'console-request-logs',
    surface: 'console',
    path: '/console/request-logs',
    ownerTask: 'TASK-20260507-014',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '无日志时说明筛选条件，并提供清空筛选。',
    errorRecovery: '日志查询失败时保留筛选条件并提供重试。',
    highRiskInputs: ['resource-picker', 'field-array-validation'],
    destructiveActions: [],
  },
  {
    id: 'console-ops-governance',
    surface: 'console',
    path: '/console/ops/governance',
    ownerTask: 'TASK-20260507-014',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '无治理规则时提供创建策略入口。',
    errorRecovery: '说明策略保存、审计或权限失败原因。',
    highRiskInputs: ['resource-picker', 'field-array-validation', 'danger-confirm'],
    destructiveActions: ['disable-rule', 'delete-rule'],
  },
  {
    id: 'console-codex-onboarding',
    surface: 'console',
    path: '/console/accounts/connect/codex',
    ownerTask: 'TASK-20260507-007',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '无 Codex 账号时引导导入 auth.json 或创建 Client Instance。',
    errorRecovery: '导入失败时说明字段缺失、凭证格式或权限原因。',
    highRiskInputs: ['resource-picker', 'masked-secret', 'field-array-validation', 'danger-confirm'],
    destructiveActions: ['revoke-session'],
  },
  {
    id: 'console-codex-observability',
    surface: 'console',
    path: '/console/request-logs',
    ownerTask: 'TASK-20260507-004',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '无请求日志时保留统一时间范围和通用筛选入口。',
    errorRecovery: '观测查询失败时保留筛选条件，并说明 request log、route decision 或 cache hit 哪一类失败。',
    highRiskInputs: ['resource-picker', 'field-array-validation', 'mobile-table-overflow'],
    destructiveActions: [],
  },
  {
    id: 'console-account-group-runtime',
    surface: 'console',
    path: '/console/account-groups/:id',
    ownerTask: 'TASK-20260507-017',
    critical: true,
    viewports: ['desktop', 'mobile'],
    states: ['loading', 'empty', 'loaded', 'error', 'permissionDenied'],
    emptyStateCta: '无 Codex 账号时引导导入 auth.json。',
    errorRecovery: '运行态失败时保留账号分组上下文，并说明 quota、smoke、冻结或绑定 Key 的失败原因。',
    highRiskInputs: ['resource-picker', 'masked-secret', 'danger-confirm', 'mobile-table-overflow'],
    destructiveActions: ['freeze-account', 'runtime-reset', 'batch-recovery-preflight'],
  },
]

export function validateUxAcceptanceMatrix(pages = uxAcceptancePages): UxAcceptanceIssue[] {
  const issues: UxAcceptanceIssue[] = []
  const requiredViewportNames = uxViewports.map((viewport) => viewport.name)
  for (const page of pages) {
    for (const viewport of requiredViewportNames) {
      if (!page.viewports.includes(viewport)) {
        issues.push({ pageId: page.id, reason: `缺少 ${viewport} viewport 验收。` })
      }
    }
    for (const state of requiredUxStates) {
      if (!page.states.includes(state)) {
        issues.push({ pageId: page.id, reason: `缺少 ${state} 状态验收。` })
      }
    }
    if (!page.emptyStateCta.trim()) {
      issues.push({ pageId: page.id, reason: '空态缺少下一步 CTA 或说明。' })
    }
    if (!page.errorRecovery.trim()) {
      issues.push({ pageId: page.id, reason: '错误态缺少业务原因或下一步动作。' })
    }
    if (page.surface === 'console' && !page.path.startsWith('/console')) {
      issues.push({ pageId: page.id, reason: 'Console 页面必须使用 /console/* 路由。' })
    }
    if (page.surface === 'portal' && !page.path.startsWith('/portal')) {
      issues.push({ pageId: page.id, reason: 'Portal 页面必须使用 /portal/* 路由。' })
    }
    if (page.destructiveActions.length && !page.highRiskInputs.includes('danger-confirm')) {
      issues.push({ pageId: page.id, reason: '破坏性操作缺少 danger-confirm 规则。' })
    }
    for (const highRiskInput of page.highRiskInputs) {
      if (!highRiskInputRules.some((rule) => rule.id === highRiskInput)) {
        issues.push({ pageId: page.id, reason: `未知高风险输入规则 ${highRiskInput}。` })
      }
    }
  }
  return issues
}
