import { useState, type ReactNode } from 'react'
import {
  BrainCircuitIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  LoaderCircleIcon,
  PlayIcon,
} from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { MetricCard } from '@/components/app/metric-card'
import { StatusBadge } from '@/components/app/status-badge'
import {
  type AdminChatExecuteResponse,
  type AdminResourceExecuteResponse,
  type RouteExecutionAttempt,
} from './types'
import { isChatLikePath } from './utils'

export function WorkbenchStage({
  title,
  kicker,
  icon,
  children,
}: {
  title: string
  kicker: string
  icon: ReactNode
  children: ReactNode
}) {
  return (
    <Card className="border-border/60 bg-card/96 shadow-sm">
      <CardHeader className="gap-4 border-b border-border/60 pb-5">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-2">
            <div className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">{kicker}</div>
            <div className="flex items-center gap-2">
              <div className="flex size-9 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                {icon}
              </div>
              <CardTitle className="text-base">{title}</CardTitle>
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-6 p-6">{children}</CardContent>
    </Card>
  )
}

export function WorkbenchPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="rounded-3xl border border-border/60 bg-background/92 p-5 shadow-sm">
      <div className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">{title}</div>
      <div className="mt-4">{children}</div>
    </div>
  )
}

export function PlanNarrativeCard({ title, items }: { title: string; items: string[] }) {
  return (
    <Card className="border-border/60 bg-background/90 shadow-none">
      <CardHeader className="gap-2 border-b border-border/60 pb-4">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 p-5">
        {items.map((item) => (
          <div key={item} className="rounded-2xl border border-border/60 bg-card px-4 py-3.5 text-sm leading-6 text-foreground">
            {item}
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

export function WorkbenchDisclosure({
  title,
  children,
  defaultOpen = false,
}: {
  title: string
  children: ReactNode
  defaultOpen?: boolean
}) {
  return (
    <details open={defaultOpen} className="rounded-3xl border border-border/60 bg-card/96 shadow-sm">
      <summary className="cursor-pointer list-none px-6 py-5 text-sm font-medium text-foreground [&::-webkit-details-marker]:hidden">
        {title}
      </summary>
      <div className="border-t border-border/60 px-6 py-6">{children}</div>
    </details>
  )
}

export function StageLoading({ text, compact = false }: { text: string; compact?: boolean }) {
  return (
    <div className={compact
      ? 'flex items-center gap-3 rounded-2xl border border-border/60 bg-background/90 px-4 py-3'
      : 'flex items-center gap-3 rounded-2xl border border-border/60 bg-background/90 px-4 py-5'}
    >
      <LoaderCircleIcon className="animate-spin text-primary" />
      <span className="text-sm text-muted-foreground">{text}</span>
    </div>
  )
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-2.5">
      <span className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">{label}</span>
      {children}
    </label>
  )
}

export function DraftStatus({ label, summary, valid }: { label: string; summary: string; valid: boolean }) {
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border/60 bg-background/90 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="text-sm text-foreground">{label}</div>
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-muted-foreground">{summary}</span>
        <StatusBadge tone={valid ? 'success' : 'danger'}>{valid ? '有效' : '无效'}</StatusBadge>
      </div>
    </div>
  )
}

export function AttemptCard({ attempt }: { attempt: RouteExecutionAttempt }) {
  return (
    <Card className="border-border/60 bg-card/92 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">尝试 #{attempt.attempt}</CardTitle>
        <div className="text-sm text-muted-foreground">{attempt.providerType ?? '-'}</div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 p-5">
        <div className="flex flex-wrap gap-2">
          <StatusBadge tone={attempt.outcome?.includes('FAILED') ? 'danger' : 'success'}>
            {attempt.outcome ?? '-'}
          </StatusBadge>
          {attempt.credentialId ? <StatusBadge>凭证 {attempt.credentialId}</StatusBadge> : null}
        </div>
        <div className="text-sm leading-6 text-muted-foreground">{attempt.detail ?? '无额外详情'}</div>
      </CardContent>
    </Card>
  )
}

export function TraceTimeline({ items }: { items: Array<{ title: string; meta: Array<[string, string]> }> }) {
  return (
    <div className="flex flex-col gap-4">
      {items.map((item) => (
        <Card key={item.title} className="border-border/60 bg-card/92 shadow-sm">
          <CardHeader className="gap-2 border-b border-border/60">
            <CardTitle className="text-base">{item.title}</CardTitle>
          </CardHeader>
          <CardContent className="p-5">
            <KeyValueGrid items={item.meta} />
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

export function KeyValueGrid({ items }: { items: Array<[string, string]> }) {
  return (
    <div className="grid gap-3 md:grid-cols-2">
      {items.map(([label, value]) => (
        <div key={label} className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 bg-background/90 px-4 py-3">
          <span className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{label}</span>
          <span className="max-w-[18rem] break-all text-right text-sm text-foreground">{value}</span>
        </div>
      ))}
    </div>
  )
}

export function ListBlock({ title, items, emptyText }: { title: string; items: string[]; emptyText: string }) {
  return (
    <div className="flex flex-col gap-2">
      <div className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{title}</div>
      {items.length ? (
        items.map((item) => (
          <div key={item} className="rounded-2xl border border-border/60 bg-background/90 px-4 py-3 text-sm leading-6 text-foreground">
            {item}
          </div>
        ))
      ) : (
        <div className="rounded-2xl border border-dashed border-border/60 bg-background/60 px-4 py-3 text-sm text-muted-foreground">
          {emptyText}
        </div>
      )}
    </div>
  )
}

export function JsonBlock({ title, value }: { title: string; value: unknown }) {
  return (
    <Card className="border-border/60 bg-card shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base text-foreground">{title}</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <pre className="overflow-x-auto rounded-b-xl bg-muted/60 p-5 text-xs leading-6 text-foreground">{JSON.stringify(value, null, 2)}</pre>
      </CardContent>
    </Card>
  )
}

function NormalizedTextResult({ text }: { text: string }) {
  const [isExpanded, setIsExpanded] = useState(true)

  if (!text) return null

  const thinkRegex = /<think>([\s\S]*?)<\/think>/i
  const match = text.match(thinkRegex)

  let reasoning: string | null = null
  let content = text

  if (match) {
    reasoning = match[1].trim()
    content = text.replace(thinkRegex, '').trim()
  }

  return (
    <div className="flex flex-col gap-3">
      {reasoning ? (
        <div className="overflow-hidden rounded-2xl border border-primary/20 bg-primary/5 backdrop-blur-md transition-all duration-300 dark:bg-primary/10">
          <button
            type="button"
            onClick={() => setIsExpanded(!isExpanded)}
            className="flex w-full items-center justify-between bg-primary/10 px-4 py-3 text-xs font-semibold text-primary transition-colors hover:bg-primary/20"
          >
            <span className="flex items-center gap-2">
              <BrainCircuitIcon className="size-4 animate-pulse text-primary" />
              深度思考推理链
            </span>
            <div className="flex items-center gap-1.5">
              <span className="mr-1 font-mono text-[10px] text-primary/60">{reasoning.length} 字符</span>
              {isExpanded ? <ChevronUpIcon className="size-3.5" /> : <ChevronDownIcon className="size-3.5" />}
            </div>
          </button>

          {isExpanded ? (
            <div className="max-h-96 overflow-y-auto whitespace-pre-wrap break-words border-t border-primary/10 bg-primary/5 px-4 py-3 text-xs leading-relaxed text-muted-foreground">
              {reasoning}
            </div>
          ) : null}
        </div>
      ) : null}

      {content ? (
        <div className="rounded-2xl border border-border/60 bg-card px-4 py-4 text-sm leading-6 text-foreground shadow-sm">
          {content}
        </div>
      ) : null}
    </div>
  )
}

export function ExecutionResultCard({
  requestPath,
  executeResult,
  resourceExecuteResult,
  usageSummary,
}: {
  requestPath: string
  executeResult?: AdminChatExecuteResponse
  resourceExecuteResult?: AdminResourceExecuteResponse
  usageSummary: Array<{ label: string; value: string | number; hint: string }>
}) {
  if (!executeResult && !resourceExecuteResult) {
    return (
      <EmptyState
        title="还没有真实执行结果"
        icon={<PlayIcon className="size-5" />}
      />
    )
  }

  return (
    <Card className="border-border/60 bg-background/90 shadow-none">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">真实规范化结果</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 p-4">
        {usageSummary.length ? (
          <div className="grid gap-3 md:grid-cols-2">
            {usageSummary.map((item) => (
              <MetricCard key={item.label} label={item.label} value={item.value} hint={item.hint} />
            ))}
          </div>
        ) : null}

        {isChatLikePath(requestPath) && executeResult ? (
          <>
            <KeyValueGrid
              items={[
                ['请求 ID', executeResult.requestId],
                ['执行后端', executeResult.executionBackend ?? '-'],
              ]}
            />
            {executeResult.text ? <NormalizedTextResult text={executeResult.text} /> : null}
          </>
        ) : null}

        {!isChatLikePath(requestPath) && resourceExecuteResult ? (
          <>
            <KeyValueGrid
              items={[
                ['请求 ID', resourceExecuteResult.requestId ?? '-'],
                ['网关资源键', resourceExecuteResult.gatewayResourceKey ?? '-'],
                ['状态码', String(resourceExecuteResult.statusCode)],
                ['内容类型', resourceExecuteResult.contentType ?? '-'],
              ]}
            />
            {resourceExecuteResult.responseText ? <NormalizedTextResult text={resourceExecuteResult.responseText} /> : null}
            {resourceExecuteResult.canonicalResponse ? (
              <JsonBlock title="Canonical Resource Response" value={resourceExecuteResult.canonicalResponse} />
            ) : null}
          </>
        ) : null}
      </CardContent>
    </Card>
  )
}
