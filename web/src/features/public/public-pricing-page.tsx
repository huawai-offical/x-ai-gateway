import { Link } from 'react-router-dom'
import { ArrowRightIcon, CheckIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { StatusBadge } from '@/components/app/status-badge'
import { PublicBand, PublicFrame } from './public-shell'

const PLANS = [
  {
    name: '社区版',
    price: '¥0',
    quota: '测试额度 / 管理员发放',
    tone: 'info' as const,
    features: ['OpenAI-compatible smoke', 'Portal Key 自助管理', '公开文档与状态页'],
  },
  {
    name: '专业版',
    price: '¥19.9',
    quota: '500K token credits',
    tone: 'success' as const,
    features: ['Codex App API 接入', '用量与订单自助查看', '基础限流与额度保护'],
  },
  {
    name: '团队版',
    price: '按需',
    quota: '团队 Key / 独立策略',
    tone: 'warning' as const,
    features: ['多用户订阅关系', '访问组与公告', 'Provider 与路由策略托管'],
  },
]

export function PublicPricingPage() {
  return (
    <PublicFrame>
      <PublicBand className="border-t-0">
        <div className="max-w-3xl">
          <div className="text-sm font-medium uppercase tracking-[0.18em] text-primary">价格</div>
          <h1 className="mt-3 text-4xl font-semibold tracking-tight text-foreground">透明套餐</h1>
        </div>
      </PublicBand>

      <PublicBand className="bg-muted/30">
        <div className="grid gap-4 lg:grid-cols-3">
          {PLANS.map((plan) => (
            <div key={plan.name} className="flex flex-col rounded-lg border border-border bg-card p-6 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-xl font-semibold text-foreground">{plan.name}</div>
                  <div className="mt-2 text-sm text-muted-foreground">{plan.quota}</div>
                </div>
                <StatusBadge tone={plan.tone}>{plan.name}</StatusBadge>
              </div>
              <div className="mt-6 text-4xl font-semibold tracking-tight text-foreground">{plan.price}</div>
              <div className="mt-6 space-y-3">
                {plan.features.map((feature) => (
                  <div key={feature} className="flex items-start gap-2 text-sm text-muted-foreground">
                    <CheckIcon className="mt-0.5 size-4 shrink-0 text-primary" />
                    <span>{feature}</span>
                  </div>
                ))}
              </div>
              <Button className="mt-8" asChild>
                <Link to="/portal/register">
                  选择套餐
                  <ArrowRightIcon data-icon="inline-end" />
                </Link>
              </Button>
            </div>
          ))}
        </div>

        <div className="mt-6 rounded-lg border border-amber-500/30 bg-amber-500/10 p-4 text-sm leading-6 text-foreground">
          Provider 官方模型价格、汇率和 pass-through price 更新频繁，公开页不承诺实时官方价格。
        </div>
      </PublicBand>
    </PublicFrame>
  )
}
