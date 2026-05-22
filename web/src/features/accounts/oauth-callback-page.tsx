import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ArrowUpRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { CodePanel } from '@/components/app/code-panel'
import { InfoGrid } from '@/components/app/info-grid'
import { PageSection } from '@/components/app/page-section'
import { StatusBadge } from '@/components/app/status-badge'

export function OauthCallbackPage() {
  const { provider } = useParams()
  const [searchParams] = useSearchParams()
  const status = searchParams.get('status')
  const accountId = searchParams.get('accountId')
  const groupId = searchParams.get('groupId')
  const sessionKey = searchParams.get('sessionKey') ?? searchParams.get('state')
  const error = searchParams.get('error')
  const errorDescription = searchParams.get('error_description') ?? searchParams.get('message')
  const isSuccess = status === 'success' && accountId
  const returnPath = groupId ? `/console/account-groups/${encodeURIComponent(groupId)}` : '/console/account-groups'

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="OAuth 回调"
        title={isSuccess ? '账号认证导入成功' : '授权回调需要处理'}
        actions={(
          <div className="flex flex-wrap gap-2">
            <Button asChild type="button" variant={isSuccess ? 'default' : 'outline'}>
              <Link to={returnPath}>
                返回账号分组
                <ArrowUpRightIcon data-icon="inline-end" />
              </Link>
            </Button>
            {accountId && groupId ? (
              <Button asChild type="button" variant="outline">
                <Link to={`/console/account-groups/${encodeURIComponent(groupId)}`}>
                  打开目标分组
                  <ArrowUpRightIcon data-icon="inline-end" />
                </Link>
              </Button>
            ) : null}
          </div>
        )}
      >
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <StatusBadge tone={isSuccess ? 'success' : error ? 'danger' : 'warning'}>
            {isSuccess ? '成功' : error ? '失败' : '处理中'}
          </StatusBadge>
          <span className="text-sm text-muted-foreground">
            {isSuccess ? 'OAuth 授权已完成，结果已写入账号分组。' : '请检查回调参数，或重新发起授权。'}
          </span>
        </div>
        <InfoGrid
          items={[
            { key: 'provider', label: '提供方', value: provider ?? '未知' },
            { key: 'status', label: '状态', value: formatOAuthCallbackStatus(status, error) },
            { key: 'accountId', label: '账号 ID', value: accountId ?? '未生成' },
            { key: 'groupId', label: '账号分组 ID', value: groupId ?? '未指定' },
            { key: 'sessionKey', label: '会话标识', value: sessionKey ?? '无' },
            { key: 'error', label: '错误码', value: error ?? '无' },
            { key: 'errorDescription', label: '错误说明', value: errorDescription ?? '无' },
          ]}
          columnsClassName="md:grid-cols-2"
        />
      </PageSection>

      <CodePanel title="原始回调参数" code={JSON.stringify(Object.fromEntries(searchParams.entries()), null, 2)} />
    </div>
  )
}

function formatOAuthCallbackStatus(status: string | null, error: string | null) {
  if (status === 'success') return '成功'
  if (status === 'pending') return '处理中'
  if (status === 'failed' || error) return '失败'
  return '未知'
}
