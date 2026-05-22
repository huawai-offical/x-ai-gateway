export type ErrorRule = {
  id: number
  enabled: boolean
  priority: number
  providerType?: string | null
  protocol?: string | null
  modelPattern?: string | null
  requestPath?: string | null
  httpStatus?: number | null
  errorCode?: string | null
  matchScope?: string | null
  action: string
  rewriteStatus?: number | null
  rewriteCode?: string | null
  rewriteMessage?: string | null
  downgradePolicy?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type ErrorRuleDraft = {
  id?: number
  enabled: boolean
  priority: string
  providerType: string
  protocol: string
  modelPattern: string
  requestPath: string
  httpStatus: string
  errorCode: string
  matchScope: string
  action: string
  rewriteStatus: string
  rewriteCode: string
  rewriteMessage: string
  downgradePolicy: string
}

export type ErrorRulePreviewRequest = {
  providerType?: string | null
  protocol?: string | null
  model?: string | null
  requestPath?: string | null
  httpStatus?: number | null
  errorCode?: string | null
  matchScope?: string | null
  message?: string | null
}

export type ErrorRulePreviewResponse = {
  matchedRules: ErrorRule[]
}

export function createDefaultErrorRuleDraft(): ErrorRuleDraft {
  return {
    enabled: true,
    priority: '100',
    providerType: '',
    protocol: 'openai',
    modelPattern: '',
    requestPath: '/v1/chat/completions',
    httpStatus: '500',
    errorCode: 'UPSTREAM_ERROR',
    matchScope: 'UPSTREAM',
    action: 'REWRITE',
    rewriteStatus: '502',
    rewriteCode: 'REWRITTEN_ERROR',
    rewriteMessage: '规则命中后的错误输出',
    downgradePolicy: '',
  }
}

export function errorRuleToDraft(rule: ErrorRule): ErrorRuleDraft {
  return {
    id: rule.id,
    enabled: rule.enabled,
    priority: String(rule.priority),
    providerType: rule.providerType ?? '',
    protocol: rule.protocol ?? 'openai',
    modelPattern: rule.modelPattern ?? '',
    requestPath: rule.requestPath ?? '',
    httpStatus: rule.httpStatus == null ? '' : String(rule.httpStatus),
    errorCode: rule.errorCode ?? '',
    matchScope: rule.matchScope ?? 'UPSTREAM',
    action: rule.action,
    rewriteStatus: rule.rewriteStatus == null ? '' : String(rule.rewriteStatus),
    rewriteCode: rule.rewriteCode ?? '',
    rewriteMessage: rule.rewriteMessage ?? '',
    downgradePolicy: rule.downgradePolicy ?? '',
  }
}
