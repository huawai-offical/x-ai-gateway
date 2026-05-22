import { type FormEvent, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { InlineError } from '@/components/app/inline-error'
import { loginPortal, registerPortal } from './api'
import type { PortalSession } from './types'

type Mode = 'login' | 'register'

export function PortalLoginPage({ initialMode = 'login' }: { initialMode?: Mode }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<Mode>(initialMode)
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => {
      const normalizedEmail = email.trim().toLowerCase()
      if (!normalizedEmail) {
        throw new Error('邮箱不能为空。')
      }
      if (password.length < 8) {
        throw new Error('密码至少需要 8 个字符。')
      }
      if (mode === 'register') {
        return registerPortal({
          email: normalizedEmail,
          displayName: displayName.trim() || null,
          password,
        })
      }
      return loginPortal({ email: normalizedEmail, password })
    },
    onSuccess: (session: PortalSession) => {
      queryClient.clear()
      if (session.authenticated) {
        navigate('/portal', { replace: true })
      }
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      mutation.mutate()
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '提交失败。')
    }
  }

  return (
    <div className="min-h-svh bg-background px-4 py-10 text-foreground">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-8 lg:grid lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
        <section className="space-y-5">
          <div className="inline-flex rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-sm font-medium text-primary shadow-sm backdrop-blur">
            用户门户
          </div>
          <div className="space-y-3">
            <h1 className="max-w-2xl text-4xl font-semibold tracking-tight text-foreground md:text-5xl">
              订阅、Key 与公告
            </h1>
          </div>
          <Link className="text-sm font-medium text-primary hover:text-primary/80" to="/login">
            返回管理控制台登录
          </Link>
        </section>

        <Card className="border-border bg-card/95 shadow-2xl backdrop-blur">
          <CardHeader>
            <CardTitle>{mode === 'login' ? '登录用户门户' : '注册用户门户'}</CardTitle>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
              <Tabs value={mode} onValueChange={(value) => setMode(value as Mode)}>
                <TabsList className="grid w-full grid-cols-2">
                  <TabsTrigger value="login">登录</TabsTrigger>
                  <TabsTrigger value="register">注册</TabsTrigger>
                </TabsList>
                <TabsContent value="login" className="pt-4" />
                <TabsContent value="register" className="pt-4">
                  <label className="mb-4 flex flex-col gap-2">
                    <span className="text-sm font-medium text-foreground">显示名称</span>
                    <Input
                      value={displayName}
                      onChange={(event) => setDisplayName(event.target.value)}
                      placeholder="例如：Alice"
                    />
                  </label>
                </TabsContent>
              </Tabs>

              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">邮箱</span>
                <Input
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="user@example.com"
                  autoComplete="email"
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-foreground">密码</span>
                <Input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="至少 8 个字符"
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                />
              </label>

              {(formError || mutation.error) ? (
                <InlineError error={mutation.error ?? new Error(formError ?? '认证失败。')} title="门户认证失败" />
              ) : null}

              <Button type="submit" disabled={mutation.isPending}>
                {mode === 'login' ? '登录门户' : '创建账号并进入'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

export function PortalRegisterPage() {
  return <PortalLoginPage initialMode="register" />
}
