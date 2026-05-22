import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import {
  CpuIcon,
  LoaderCircleIcon,
  LogInIcon,
  RefreshCcwIcon,
  ShieldCheckIcon,
} from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { StatusBadge } from '@/components/app/status-badge'
import { formatInstant } from '@/lib/format'
import { isApiError } from '@/lib/api'
import { createAdminChallenge, loginAdminConsole } from './api'
import {
  consumeAuthLoginNotice,
  readRememberedAdminUsername,
  useAdminAuth,
} from './auth-provider'
import { solvePowChallenge } from './pow'
import type { AdminAuthChallenge, PowSolveState } from './types'

const DEFAULT_REDIRECT = '/incidents'
const LOGIN_TOAST_DURATION_MS = 5000

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { completeLogin, isAuthenticated, session } = useAdminAuth()

  const redirectTo = useMemo(
    () => normalizeRedirect(searchParams.get('redirect')),
    [searchParams],
  )

  const [username, setUsername] = useState(() => readRememberedAdminUsername())
  const [password, setPassword] = useState('')
  const [mathAnswer, setMathAnswer] = useState('')
  const [challenge, setChallenge] = useState<AdminAuthChallenge | null>(null)
  const [challengeState, setChallengeState] = useState<'loading' | 'ready' | 'refreshing' | 'error'>('loading')
  const [challengeError, setChallengeError] = useState<string | null>(null)
  const [powState, setPowState] = useState<PowSolveState>({
    status: 'idle',
    nonce: '',
    attempts: 0,
  })
  const [submitting, setSubmitting] = useState(false)

  const refreshChallenge = useCallback(async (options?: { mode?: 'initial' | 'refresh' }) => {
    setChallengeState(options?.mode === 'initial' ? 'loading' : 'refreshing')
    setChallengeError(null)
    setMathAnswer('')

    try {
      const nextChallenge = await createAdminChallenge()
      setChallenge(nextChallenge)
      setChallengeState('ready')
    } catch (error) {
      const message = extractErrorMessage(error, '获取登录挑战失败，请稍后重试。')
      setChallenge(null)
      setChallengeState('error')
      setChallengeError(message)
      toast.error(message, { duration: LOGIN_TOAST_DURATION_MS })
    }
  }, [])

  const clearReasonParam = useCallback(() => {
    if (!searchParams.has('reason')) {
      return
    }

    const nextParams = new URLSearchParams(searchParams)
    nextParams.delete('reason')
    setSearchParams(nextParams, { replace: true })
  }, [searchParams, setSearchParams])

  useEffect(() => {
    const reason = searchParams.get('reason')
    if (reason === 'logged-out') {
      toast.info('你已安全退出控制台，如需继续操作请重新登录。', {
        duration: LOGIN_TOAST_DURATION_MS,
      })
      clearReasonParam()
      return
    }
    if (reason === 'credentials-updated') {
      const usernameHint = readRememberedAdminUsername()
      toast.success(
        usernameHint
          ? `控制台凭证已更新，请使用新账号重新登录。当前推荐用户名：${usernameHint}。`
          : '控制台凭证已更新，请使用新账号与新密码重新登录。',
        { duration: LOGIN_TOAST_DURATION_MS },
      )
      clearReasonParam()
      return
    }

    const persistedNotice = consumeAuthLoginNotice()
    if (persistedNotice === 'expired') {
      toast.warning('控制台会话已失效或被清除，请重新完成登录校验。', {
        duration: LOGIN_TOAST_DURATION_MS,
      })
      return
    }
  }, [clearReasonParam, searchParams])

  useEffect(() => {
    if (!isAuthenticated) {
      void refreshChallenge({ mode: 'initial' })
    }
  }, [isAuthenticated, refreshChallenge])

  useEffect(() => {
    if (!challenge) {
      setPowState({
        status: 'idle',
        nonce: '',
        attempts: 0,
      })
      return undefined
    }

    const controller = new AbortController()
    setPowState({
      status: 'solving',
      nonce: '',
      attempts: 0,
    })

    void solvePowChallenge(
      challenge.challengeId,
      challenge.powSalt,
      challenge.powDifficulty,
      {
        signal: controller.signal,
        onProgress: (attempts) => {
          setPowState((current) => ({
            ...current,
            status: current.status === 'solved' ? current.status : 'solving',
            attempts,
          }))
        },
      },
    )
      .then((nonce) => {
        setPowState((current) => ({
          status: 'solved',
          nonce,
          attempts: current.attempts,
        }))
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }

        setPowState((current) => ({
          status: 'failed',
          nonce: '',
          attempts: current.attempts,
          message: error instanceof Error ? error.message : 'POW 计算失败。',
        }))
      })

    return () => {
      controller.abort()
    }
  }, [challenge])

  if (isAuthenticated && session?.authenticated) {
    return <Navigate to={redirectTo} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!challenge || powState.status !== 'solved') {
      toast.error('POW 仍在计算中，请等待计算完成后再登录。', {
        duration: LOGIN_TOAST_DURATION_MS,
      })
      return
    }

    const parsedMathAnswer = Number(mathAnswer)
    if (!Number.isInteger(parsedMathAnswer)) {
      toast.error('请输入正确的数学验证码结果。', {
        duration: LOGIN_TOAST_DURATION_MS,
      })
      return
    }

    setSubmitting(true)

    try {
      const nextSession = await loginAdminConsole({
        username: username.trim(),
        password,
        challengeId: challenge.challengeId,
        mathAnswer: parsedMathAnswer,
        powNonce: powState.nonce,
      })
      completeLogin(nextSession)
      navigate(redirectTo, { replace: true })
    } catch (error) {
      toast.error(extractErrorMessage(error, '登录失败，请刷新挑战后重试。'), {
        duration: LOGIN_TOAST_DURATION_MS,
      })
      await refreshChallenge({ mode: 'refresh' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-svh bg-[radial-gradient(circle_at_top_left,_rgba(8,145,178,0.14),_transparent_26%),radial-gradient(circle_at_bottom_right,_rgba(59,130,246,0.16),_transparent_26%),linear-gradient(180deg,_rgba(248,250,252,1),_rgba(241,245,249,1))] px-4 py-8 lg:px-8 lg:py-10">
      <div className="mx-auto flex w-full max-w-2xl items-center justify-center">
        <Card className="w-full border-border/70 bg-background/96 shadow-xl backdrop-blur">
          <CardHeader className="border-b border-border/60">
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge tone="info">管理控制台</StatusBadge>
              <StatusBadge tone="success">POW + 数学验证码</StatusBadge>
            </div>
            <div className="mt-3 flex items-center gap-2">
              <ShieldCheckIcon className="text-primary" />
              <CardTitle>登录控制台</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="flex flex-col gap-5 pt-6">
            <Alert>
              {stageIcon(challengeState, powState, submitting)}
              <AlertTitle>{stageTitle(challengeState, powState, submitting)}</AlertTitle>
              <AlertDescription>{stageDescription(challengeState, powState, submitting, challengeError)}</AlertDescription>
            </Alert>

            <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-foreground" htmlFor="login-username">
                  用户名
                </label>
                <Input
                  id="login-username"
                  autoComplete="username"
                  placeholder="请输入控制台用户名"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  disabled={submitting}
                />
              </div>

              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-foreground" htmlFor="login-password">
                  密码
                </label>
                <Input
                  id="login-password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="输入控制台密码"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  disabled={submitting}
                />
              </div>

              <div className="rounded-3xl border border-border/70 bg-muted/40 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <StatusBadge tone="warning">数学验证码</StatusBadge>
                      <StatusBadge tone={powTone(powState.status)}>{powLabel(powState.status)}</StatusBadge>
                    </div>
                    <div className="text-sm font-medium text-foreground">
                      {challenge?.mathPrompt ?? '正在生成登录挑战...'}
                    </div>
                    <div className="text-xs leading-6 text-muted-foreground">
                      {challenge
                        ? `挑战 ${challenge.challengeId.slice(0, 8)} · POW ${challenge.powAlgorithm} · 难度 ${challenge.powDifficulty} · 过期时间 ${formatInstant(challenge.expiresAt)}`
                        : '系统会自动刷新一次性验证码与 POW 挑战。'}
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => void refreshChallenge({ mode: 'refresh' })}
                    disabled={submitting || challengeState === 'loading' || challengeState === 'refreshing'}
                  >
                    {challengeState === 'loading' || challengeState === 'refreshing' ? (
                      <LoaderCircleIcon data-icon="inline-start" className="animate-spin" />
                    ) : (
                      <RefreshCcwIcon data-icon="inline-start" />
                    )}
                    {challengeState === 'refreshing' ? '刷新中...' : challengeState === 'loading' ? '准备中...' : '刷新挑战'}
                  </Button>
                </div>

                <div className="mt-4 flex flex-col gap-4">
                  <div className="flex flex-col gap-2">
                    <label className="text-sm font-medium text-foreground" htmlFor="math-answer">
                      数学验证码结果
                    </label>
                    <Input
                      id="math-answer"
                      inputMode="numeric"
                      placeholder="输入上方算式结果"
                      value={mathAnswer}
                      onChange={(event) => setMathAnswer(event.target.value)}
                      disabled={!challenge || submitting}
                    />
                  </div>

                  <div className="rounded-2xl border border-border/70 bg-background/90 p-4">
                    <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                      <CpuIcon className={powState.status === 'solving' ? 'size-4 animate-pulse text-primary' : 'size-4 text-primary'} />
                      客户端 POW 计算状态
                    </div>
                    <div className="mt-2 text-sm leading-6 text-muted-foreground">
                      {powDescription(powState)}
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <StatusBadge tone={powTone(powState.status)}>{powLabel(powState.status)}</StatusBadge>
                      <StatusBadge tone="info">尝试次数 {powState.attempts}</StatusBadge>
                      {powState.status === 'solved' ? (
                        <StatusBadge tone="success">nonce {powState.nonce}</StatusBadge>
                      ) : null}
                    </div>
                  </div>
                </div>
              </div>

              <Button
                type="submit"
                size="lg"
                disabled={
                  submitting ||
                  challengeState === 'loading' ||
                  challengeState === 'refreshing' ||
                  !challenge ||
                  powState.status !== 'solved'
                }
              >
                <LogInIcon data-icon="inline-start" />
                {submitting ? '正在校验并登录...' : '登录并进入控制台'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function normalizeRedirect(value: string | null) {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return DEFAULT_REDIRECT
  }
  return value
}

function extractErrorMessage(error: unknown, fallback: string) {
  if (isApiError(error)) {
    return error.message
  }
  return error instanceof Error ? error.message : fallback
}

function powTone(status: PowSolveState['status']) {
  switch (status) {
    case 'solved':
      return 'success'
    case 'failed':
      return 'danger'
    case 'solving':
      return 'warning'
    default:
      return 'neutral'
  }
}

function powLabel(status: PowSolveState['status']) {
  switch (status) {
    case 'solved':
      return 'POW 已就绪'
    case 'failed':
      return 'POW 失败'
    case 'solving':
      return 'POW 计算中'
    default:
      return '等待挑战'
  }
}

function powDescription(state: PowSolveState) {
  switch (state.status) {
    case 'solving':
      return '浏览器正在自动求解满足难度要求的 nonce，完成后会自动允许提交登录。这个过程会随着难度提升而变慢。'
    case 'solved':
      return 'POW 已求解完成，本次登录挑战可以直接提交。'
    case 'failed':
      return state.message
    default:
      return '新挑战下发后会立即开始自动计算。'
  }
}

function stageTitle(
  challengeState: 'loading' | 'ready' | 'refreshing' | 'error',
  powState: PowSolveState,
  submitting: boolean,
) {
  if (submitting) {
    return '正在校验登录信息'
  }
  if (challengeState === 'loading') {
    return '正在准备登录挑战'
  }
  if (challengeState === 'refreshing') {
    return '正在刷新挑战'
  }
  if (challengeState === 'error') {
    return '挑战获取失败'
  }
  if (powState.status === 'solving') {
    return 'POW 计算中'
  }
  if (powState.status === 'solved') {
    return '登录校验已就绪'
  }
  if (powState.status === 'failed') {
    return 'POW 计算失败'
  }
  return '等待登录校验'
}

function stageDescription(
  challengeState: 'loading' | 'ready' | 'refreshing' | 'error',
  powState: PowSolveState,
  submitting: boolean,
  challengeError: string | null,
) {
  if (submitting) {
    return '系统正在同时校验账号密码、数学验证码答案和 POW nonce，请稍候。'
  }
  if (challengeState === 'loading') {
    return '正在从服务端获取一次性挑战，并准备数学验证码与 POW 参数。'
  }
  if (challengeState === 'refreshing') {
    return '旧挑战会被替换为新的题目和 POW 参数，提交状态也会随之更新。'
  }
  if (challengeState === 'error') {
    return challengeError ?? '当前挑战没有准备好，请检查网络或稍后刷新后重试。'
  }
  if (powState.status === 'solving') {
    return '数学验证码已经展示，浏览器正在后台自动计算满足难度要求的 POW。'
  }
  if (powState.status === 'solved') {
    return '现在只需要填写账号、密码和数学验证码，就可以提交登录。'
  }
  if (powState.status === 'failed') {
    return '当前挑战的 POW 没有算出来，请刷新挑战后再次尝试。'
  }
  return '系统正在等待挑战和 POW 状态稳定。'
}

function stageIcon(
  challengeState: 'loading' | 'ready' | 'refreshing' | 'error',
  powState: PowSolveState,
  submitting: boolean,
) {
  if (submitting || challengeState === 'loading' || challengeState === 'refreshing' || powState.status === 'solving') {
    return <LoaderCircleIcon className="animate-spin" />
  }
  return <ShieldCheckIcon />
}
