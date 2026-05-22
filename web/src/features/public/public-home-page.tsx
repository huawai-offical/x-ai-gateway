import { Link } from 'react-router-dom'
import { ArrowRightIcon, GaugeIcon, KeyRoundIcon, LayersIcon, RadioTowerIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { StatusBadge } from '@/components/app/status-badge'
import heroImage from '@/assets/hero.png'
import { PublicBand, PublicFrame } from './public-shell'

const CAPABILITY_CARDS = [
  { title: 'OpenAI 兼容', value: '/v1/chat/completions / responses', icon: LayersIcon },
  { title: 'Anthropic / Gemini', value: 'Messages 与 generateContent', icon: RadioTowerIcon },
  { title: 'Codex App API', value: '账号接入与长测', icon: KeyRoundIcon },
]

export function PublicHomePage() {
  return (
    <PublicFrame>
      <section className="relative min-h-[calc(100svh-7rem)] overflow-hidden bg-gradient-to-br from-background via-background to-muted/40 text-foreground">
        <img
          src={heroImage}
          alt=""
          className="pointer-events-none absolute right-[-3rem] top-8 h-[70%] max-h-[26rem] w-auto opacity-20 md:right-12 md:top-16 md:opacity-35"
        />
        <div className="relative mx-auto flex min-h-[calc(100svh-7rem)] w-full max-w-7xl flex-col justify-center gap-8 px-4 py-16">
          <div className="max-w-3xl">
            <img src="/logo.svg" alt="" className="mb-5 size-16" />
            <StatusBadge tone="info" className="bg-primary/10 text-primary">公开入口</StatusBadge>
            <h1 className="mt-5 text-4xl font-semibold tracking-tight text-foreground md:text-6xl">
              x-ai-gateway
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-muted-foreground">
              面向社区用户和内部管理员的 AI API 网关，统一承接 OpenAI、Anthropic、Gemini、Codex 等入口。
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <Link to="/portal/register">
                  创建客户账号
                  <ArrowRightIcon data-icon="inline-end" />
                </Link>
              </Button>
              <Button asChild size="lg" variant="outline" className="border-border bg-card/70 text-foreground hover:bg-accent hover:text-foreground">
                <Link to="/console">进入控制台</Link>
              </Button>
            </div>
          </div>
          <div className="grid max-w-5xl gap-3 md:grid-cols-3">
            {CAPABILITY_CARDS.map((item) => {
              const Icon = item.icon
              return (
                <div key={item.title} className="rounded-lg border border-border bg-card/70 p-4 shadow-sm backdrop-blur">
                  <Icon className="size-5 text-primary" />
                  <div className="mt-3 font-medium text-foreground">{item.title}</div>
                  <div className="mt-1 text-sm leading-6 text-muted-foreground">{item.value}</div>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      <PublicBand className="bg-muted/30">
        <div className="grid gap-4 md:grid-cols-4">
          <Metric icon={GaugeIcon} label="Provider Catalog" value="18+" />
          <Metric icon={LayersIcon} label="协议入口" value="OpenAI / Claude / Gemini" />
          <Metric icon={RadioTowerIcon} label="非 Chat 能力" value="Audio / Images / Files / Batches" />
          <Metric icon={KeyRoundIcon} label="Codex 接入" value="账号接入 + 长测" />
        </div>
      </PublicBand>
    </PublicFrame>
  )
}

function Metric({ icon: Icon, label, value }: { icon: typeof GaugeIcon; label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-card p-5 shadow-sm">
      <Icon className="size-5 text-primary" />
      <div className="mt-4 text-sm text-muted-foreground">{label}</div>
      <div className="mt-2 text-xl font-semibold text-foreground">{value}</div>
    </div>
  )
}
