import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { PageSection } from '@/components/app/page-section'
import { StatusBadge } from '@/components/app/status-badge'

const ROLE_WORKBENCHES = [
  {
    role: '接入管理员',
    focus: '维护上游凭证、访问密钥与账号分组治理。',
    primary: '打开上游凭证',
    to: '/console/credentials',
    signal: '凭证与分组治理',
  },
  {
    role: '运营管理员',
    focus: '关注账号分组健康、热切换与批量预检。',
    primary: '查看账号分组',
    to: '/console/account-groups',
    signal: '1 个批量任务可重试',
  },
  {
    role: '排障管理员',
    focus: '处理失败请求、链路追踪与实时会话。',
    primary: '排查失败请求',
    to: '/console/request-logs',
    signal: '按请求 ID 定位',
  },
  {
    role: '财务/计费管理员',
    focus: '核对用量、套餐与用户权益。',
    primary: '查看套餐管理',
    to: '/console/plans',
    signal: '按用户与套餐核验',
  },
  {
    role: '系统管理员',
    focus: '维护控制台认证、系统参数与事件排查。',
    primary: '查看控制台认证',
    to: '/console/settings/admin-auth',
    signal: '系统工具',
  },
] as const

const BATCH_TRUST_STATES = [
  { label: '预检就绪', value: '24' },
  { label: '已跳过', value: '3' },
  { label: '已阻断', value: '1' },
  { label: '可重试失败', value: '2' },
] as const

export function DashboardPage() {
  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="智能运维总览补充视图"
        title="角色协同视图"
        actions={
          <Button asChild variant="outline">
            <Link to="/console/ops">返回智能运维总览</Link>
          </Button>
        }
      >
        <div className="grid gap-3 lg:grid-cols-5">
          {ROLE_WORKBENCHES.map((item) => (
            <Link
              key={item.role}
              to={item.to}
              className="flex min-h-40 flex-col justify-between gap-4 rounded-lg border border-border/60 bg-background p-4 transition-colors hover:bg-muted/50"
            >
              <div className="flex flex-col gap-2">
                <div className="text-sm font-semibold text-foreground">{item.role}</div>
                <div className="text-xs leading-5 text-muted-foreground">{item.focus}</div>
              </div>
              <div className="flex flex-col gap-2">
                <StatusBadge tone="info">{item.signal}</StatusBadge>
                <div className="text-sm font-medium text-primary">{item.primary}</div>
              </div>
            </Link>
          ))}
        </div>
      </PageSection>

      <PageSection kicker="批量可信" title="批量操作可信状态">
        <div className="grid gap-3 md:grid-cols-4">
          {BATCH_TRUST_STATES.map((item) => (
            <div key={item.label} className="rounded-lg border border-border/60 bg-background px-4 py-3">
              <div className="text-xs font-medium text-muted-foreground">{item.label}</div>
              <div className="mt-2 text-2xl font-semibold text-foreground">{item.value}</div>
            </div>
          ))}
        </div>
      </PageSection>
    </div>
  )
}
