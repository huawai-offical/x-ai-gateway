import { AlertTriangleIcon, HomeIcon, RefreshCwIcon, ShieldIcon } from 'lucide-react'
import { Link, useRouteError } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { NormalizedRouteError } from './route-error-normalizer'
import { normalizeRouteError } from './route-error-normalizer'

export function RouteErrorBoundary() {
  const error = useRouteError()
  const normalized = normalizeRouteError(error)

  return <RouteErrorView error={normalized} />
}

export function RouteNotFoundPage() {
  return (
    <RouteErrorView
      error={{
        title: '页面不存在',
        message: '当前地址没有匹配的页面，请返回控制台或首页继续操作。',
        status: 404,
      }}
    />
  )
}

function RouteErrorView({ error }: { error: NormalizedRouteError }) {
  return (
    <main className="relative min-h-svh bg-background px-4 py-6 text-foreground sm:px-6 lg:px-8">
      <section className="mx-auto flex min-h-[calc(100svh-3rem)] w-full max-w-3xl items-center">
        <div className="w-full rounded-xl border border-border bg-card p-5 shadow-sm sm:p-6">
          <div className="flex flex-col gap-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
              <div className="flex size-11 shrink-0 items-center justify-center rounded-lg bg-destructive/10 text-destructive">
                <AlertTriangleIcon className="size-5" />
              </div>
              <div className="min-w-0 flex-1 space-y-2">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="text-xs font-medium text-muted-foreground">页面错误</p>
                  {error.status ? (
                    <span className="rounded-md border border-border bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      HTTP {error.status}
                    </span>
                  ) : null}
                </div>
                <h1 className="text-xl font-semibold tracking-normal text-foreground sm:text-2xl">
                  {error.title}
                </h1>
                <p className="max-w-2xl text-sm leading-6 text-muted-foreground">{error.message}</p>
              </div>
            </div>

            {error.detail ? (
              <div className="rounded-lg border border-border bg-muted/45 p-3">
                <p className="mb-1 text-xs font-medium text-muted-foreground">技术细节</p>
                <pre className="max-h-36 overflow-auto whitespace-pre-wrap break-words text-xs leading-5 text-foreground">
                  {error.detail}
                </pre>
              </div>
            ) : null}

            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={() => window.location.reload()}>
                <RefreshCwIcon data-icon="inline-start" />
                刷新当前页面
              </Button>
              <Button asChild type="button" variant="outline">
                <Link to="/console/ops">
                  <ShieldIcon data-icon="inline-start" />
                  返回控制台
                </Link>
              </Button>
              <Button asChild type="button" variant="outline">
                <Link to="/">
                  <HomeIcon data-icon="inline-start" />
                  返回首页
                </Link>
              </Button>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}
