import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeftIcon, ExternalLinkIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { CodePanel } from '@/components/app/code-panel'
import { EmptyState } from '@/components/app/empty-state'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'

type ExternalApp = {
  id: number
  appName: string
  slug: string
  iframeUrl: string
  allowedOrigin: string
  sandboxPermissions?: string | null
  signingSecretFingerprint?: string | null
  enabled: boolean
  navEnabled: boolean
  description?: string | null
}

type SignedContext = {
  slug: string
  origin: string
  context: string
  signature: string
  launchUrl: string
  expiresAt: string
}

type ExternalAppRuntime = {
  app: ExternalApp
  signedContext?: SignedContext | null
  runnable: boolean
  runtimeStatus: string
  runtimeMessage: string
  actualOrigin?: string | null
}

export function ExtensionRuntimePage() {
  const { slug = '' } = useParams()
  const runtimeQuery = useQuery({
    queryKey: ['integrations', 'external-apps', 'runtime', slug],
    enabled: Boolean(slug),
    queryFn: () =>
      apiRequest<ExternalAppRuntime>(`/admin/integrations/external-apps/runtime/${slug}`, {
        params: {
          actor: 'console-extension-runtime',
          ttlSeconds: 300,
        },
      }),
  })

  if (runtimeQuery.isPending) {
    return <PageSkeleton count={2} />
  }

  if (runtimeQuery.error) {
    return <InlineError error={runtimeQuery.error} title="扩展运行页加载失败" />
  }

  if (!runtimeQuery.data?.app) {
    return (
      <PageSection kicker="扩展运行页" title="未找到扩展应用">
        <EmptyState title={`没有找到 slug 为 ${slug || '-'} 的控制台扩展应用。`} />
        <div className="mt-4 flex justify-center">
          <Button asChild variant="outline">
            <Link to="/integrations/external-apps">返回扩展应用</Link>
          </Button>
        </div>
      </PageSection>
    )
  }

  const { app, signedContext, actualOrigin } = runtimeQuery.data
  const runtimeError = runtimeQuery.data.runnable ? null : runtimeQuery.data.runtimeMessage

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="扩展运行页"
        title={app.appName}
        actions={(
          <>
            <Button asChild variant="outline">
              <Link to="/integrations/external-apps">
                <ArrowLeftIcon />
                返回清单
              </Link>
            </Button>
            {signedContext ? (
              <Button asChild variant="outline">
                <a href={signedContext.launchUrl} target="_blank" rel="noreferrer">
                  <ExternalLinkIcon />
                  新窗口打开
                </a>
              </Button>
            ) : null}
          </>
        )}
      >
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <div className="flex flex-wrap gap-2">
            <StatusBadge tone={app.enabled ? 'success' : 'warning'}>{app.enabled ? '启用' : '停用'}</StatusBadge>
            <StatusBadge tone={app.navEnabled ? 'info' : 'warning'}>{app.navEnabled ? '导航可见' : '导航隐藏'}</StatusBadge>
            <StatusBadge tone={runtimeQuery.data.runtimeStatus === 'ORIGIN_MISMATCH' ? 'danger' : 'success'}>
              {runtimeQuery.data.runtimeStatus === 'ORIGIN_MISMATCH' ? 'Origin 异常' : 'Origin 已校验'}
            </StatusBadge>
          </div>
          <InfoGrid
            items={[
              { key: 'slug', label: 'Slug', value: app.slug },
              { key: 'origin', label: 'Allowed Origin', value: app.allowedOrigin },
              { key: 'actualOrigin', label: 'iframe Origin', value: actualOrigin ?? '-' },
              { key: 'sandbox', label: 'Sandbox', value: app.sandboxPermissions ?? defaultSandbox },
            ]}
            columnsClassName="md:grid-cols-1"
          />
        </div>
      </PageSection>

      {runtimeError ? (
        <InlineError error={new Error(runtimeError)} title="扩展应用暂不可运行" />
      ) : signedContext ? (
        <PageSection
          kicker="iframe runtime"
          title="安全挂载"
          contentClassName="space-y-4"
        >
          <div className="overflow-hidden rounded-2xl border border-border/70 bg-muted/20">
            <iframe
              title={`${app.appName} 扩展运行页`}
              src={signedContext.launchUrl}
              sandbox={app.sandboxPermissions ?? defaultSandbox}
              referrerPolicy="no-referrer"
              className="h-[68vh] min-h-[34rem] w-full bg-background"
              onLoad={(event) => {
                event.currentTarget.contentWindow?.postMessage(
                  {
                    type: 'x-ai-gateway:signed-context',
                    slug: signedContext.slug,
                    context: signedContext.context,
                    signature: signedContext.signature,
                    expiresAt: signedContext.expiresAt,
                  },
                  signedContext.origin,
                )
              }}
            />
          </div>
          <CodePanel title="Signed launch URL" code={signedContext.launchUrl} />
        </PageSection>
      ) : null}
    </div>
  )
}

const defaultSandbox = 'allow-scripts allow-forms allow-popups'
