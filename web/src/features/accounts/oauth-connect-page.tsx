import { useMutation } from '@tanstack/react-query'
import { useParams, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { InfoGrid } from '@/components/app/info-grid'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { StatusBadge } from '@/components/app/status-badge'
import { apiRequest } from '@/lib/api'

export function OauthConnectPage() {
  const { provider = 'openai_oauth' } = useParams()
  const [searchParams] = useSearchParams()
  const groupId = Number(searchParams.get('groupId') ?? 0)

  const startMutation = useMutation({
    mutationFn: () =>
      apiRequest<{ authorizationUrl: string }>(`/admin/oauth/${provider}/start`, {
        method: 'POST',
        body: JSON.stringify({ groupId }),
      }),
    onSuccess: (result: { authorizationUrl: string }) => {
      window.location.href = result.authorizationUrl
    },
  })

  return (
    <PageSection
      kicker="OAuth 连接"
      title={`发起 ${provider} 授权连接`}
      actions={
        <Button onClick={() => startMutation.mutate()} disabled={startMutation.isPending || !groupId}>
          开始授权
        </Button>
      }
    >
      <div className="flex flex-col gap-6">
        <InfoGrid
          items={[
            { key: 'provider', label: '提供方', value: provider },
            {
              key: 'group-id',
              label: '账号分组 ID',
              value: groupId || '未提供',
              hint: groupId ? '授权结果将写入指定账号分组。' : '缺少账号分组 ID，暂时无法发起授权。',
            },
            {
              key: 'status',
              label: '状态',
              value: <StatusBadge tone={groupId ? 'info' : 'warning'}>{groupId ? '就绪' : '缺少账号分组 ID'}</StatusBadge>,
            },
          ]}
          columnsClassName="md:grid-cols-3"
        />
        {startMutation.error ? <InlineError error={startMutation.error} title="发起授权失败" /> : null}
      </div>
    </PageSection>
  )
}
