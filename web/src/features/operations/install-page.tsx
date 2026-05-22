import { useMutation, useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { CodePanel } from '@/components/app/code-panel'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { apiRequest } from '@/lib/api'

type InstallationState = {
  id: number
  status: string
  bootstrapCompleted: boolean
  metadataJson?: string | null
}

export function InstallPage() {
  const stateQuery = useQuery({
    queryKey: ['install-state'],
    queryFn: () => apiRequest<InstallationState>('/admin/install/state'),
  })
  const bootstrapMutation = useMutation({
    mutationFn: () =>
      apiRequest('/admin/install/bootstrap', {
        method: 'POST',
        body: JSON.stringify({ adminEmail: 'admin@example.com', environmentName: 'local' }),
      }),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="安装"
        title="安装向导与状态"
        actions={
          <Button type="button" onClick={() => bootstrapMutation.mutate()} disabled={bootstrapMutation.isPending}>
            执行初始化
          </Button>
        }
      >
        {stateQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : stateQuery.error || bootstrapMutation.error ? (
          <InlineError error={stateQuery.error ?? bootstrapMutation.error} title="安装初始化操作失败" />
        ) : null}
      </PageSection>

      <CodePanel title="安装状态" code={JSON.stringify(stateQuery.data, null, 2)} />
    </div>
  )
}
