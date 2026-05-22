import { ClockIcon, ShieldCheckIcon } from 'lucide-react'
import { StatusBadge } from '@/components/app/status-badge'
import { PublicBand, PublicFrame } from './public-shell'

const SERVICES = [
  { name: '公开文档', status: '正常', detail: '公开文档与 OpenAPI 可访问。' },
  { name: '用户门户', status: '正常', detail: '客户登录、Key、用量和订单入口正常。' },
  { name: '网关 API', status: '正常', detail: 'OpenAI-compatible 主入口正常。' },
  { name: 'Provider 路由', status: '受保护降级', detail: 'Provider 异常时会进入路由治理。' },
]

const EVENTS = [
  { title: 'Provider 价格校验刷新', time: '2026-05-13 00:00', summary: '后台补齐价格来源、同步状态与校验记录口径。' },
  { title: '用户门户页面', time: '2026-05-13 00:00', summary: '客户用量、订单、状态和安全中心入口补齐。' },
]

export function PublicStatusPage() {
  return (
    <PublicFrame>
      <PublicBand className="border-t-0">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <div className="text-sm font-medium uppercase tracking-[0.18em] text-primary">状态</div>
            <h1 className="mt-3 text-4xl font-semibold tracking-tight text-foreground">公开服务状态</h1>
          </div>
          <StatusBadge tone="success">公开页面可访问</StatusBadge>
        </div>
      </PublicBand>

      <PublicBand className="bg-muted/30">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {SERVICES.map((service) => (
            <div key={service.name} className="rounded-lg border border-border bg-card p-5 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <ShieldCheckIcon className="size-5 text-primary" />
                <StatusBadge tone={service.status === '正常' ? 'success' : 'warning'}>{service.status}</StatusBadge>
              </div>
              <div className="mt-4 font-semibold text-foreground">{service.name}</div>
              <div className="mt-2 text-sm leading-6 text-muted-foreground">{service.detail}</div>
            </div>
          ))}
        </div>
        <div className="mt-8 rounded-lg border border-border bg-card p-5 shadow-sm">
          <div className="flex items-center gap-2 font-semibold text-foreground">
            <ClockIcon className="size-5 text-primary" />
            最近更新
          </div>
          <div className="mt-4 space-y-3">
            {EVENTS.map((event) => (
              <div key={event.title} className="rounded-lg border border-border p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="font-medium text-foreground">{event.title}</div>
                  <span className="text-xs text-muted-foreground">{event.time}</span>
                </div>
                <div className="mt-2 text-sm text-muted-foreground">{event.summary}</div>
              </div>
            ))}
          </div>
        </div>
      </PublicBand>
    </PublicFrame>
  )
}
