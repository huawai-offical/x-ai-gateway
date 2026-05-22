import { useQuery } from '@tanstack/react-query'
import { ExternalLinkIcon, TerminalSquareIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { PublicBand, PublicFrame } from './public-shell'

type PublicDocsBundle = {
  docsVersion: string
  title: string
  openApiUrl: string
  sdkTargets: string[]
  quickStart: string[]
  compatibility: Array<{
    protocol: string
    basePath: string
    clients: string[]
    capabilities: string[]
    notes: string
  }>
  providerPresets: Array<{
    code: string
    displayName: string
    compatibilitySurface: string
    supportStrategy: string
    capabilityTags: string[]
  }>
  examples: Array<{
    id: string
    protocol: string
    language: string
    title: string
    code: string
  }>
  errorCodes: Array<{
    code: string
    httpStatus: number
    description: string
    recoveryHint: string
  }>
}

const FALLBACK_DOCS: PublicDocsBundle = {
  docsVersion: 'fallback',
  title: 'x-ai-gateway 公开兼容文档',
  openApiUrl: '/public/docs/openapi.json',
  sdkTargets: ['curl', 'OpenAI SDK', 'Claude Code', 'Gemini CLI', 'Codex CLI'],
  quickStart: [
    '创建或获取 Distributed Key。',
    '把 OpenAI-compatible client 的 base URL 设置为网关 /v1。',
    '先执行 Chat Completions smoke，再启用 Claude、Gemini 或 Codex 入口。',
  ],
  compatibility: [
    {
      protocol: 'openai',
      basePath: '/v1',
      clients: ['OpenAI SDK', 'Codex CLI', 'curl'],
      capabilities: ['chat.completions', 'responses', 'embeddings'],
      notes: 'OpenAI-compatible 客户端使用 /v1 作为 base path。',
    },
    {
      protocol: 'claude',
      basePath: '/v1',
      clients: ['Claude Code'],
      capabilities: ['messages'],
      notes: '当 Provider 能力允许时，网关会翻译 Claude native 语义。',
    },
    {
      protocol: 'gemini',
      basePath: '/v1',
      clients: ['Gemini CLI'],
      capabilities: ['generateContent', 'files', 'batches'],
      notes: '需要时会通过 gateway 资源链路暴露 Gemini native object。',
    },
  ],
  providerPresets: [],
  examples: [
    {
      id: 'curl',
      protocol: 'openai',
      language: 'shell',
      title: 'OpenAI-compatible 对话冒烟验证',
      code: 'curl https://gateway.example.com/v1/chat/completions -H "Authorization: Bearer $X_AI_GATEWAY_API_KEY"',
    },
  ],
  errorCodes: [],
}

export function PublicDocsPage() {
  const docsQuery = useQuery({
    queryKey: ['public-docs', 'zh-CN'],
    queryFn: () => apiRequest<PublicDocsBundle>('/public/docs/compatibility', {
      params: { locale: 'zh-CN' },
    }),
    retry: false,
  })

  const docs = docsQuery.data ?? FALLBACK_DOCS
  const openApiUrl = docsQuery.data?.openApiUrl

  return (
    <PublicFrame>
      <PublicBand className="border-t-0">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <div className="text-sm font-medium uppercase tracking-[0.18em] text-primary">文档</div>
            <h1 className="mt-3 text-4xl font-semibold tracking-tight text-foreground">{docs.title}</h1>
          </div>
          {openApiUrl ? (
            <Button asChild variant="outline">
              <a href={openApiUrl}>
                OpenAPI 文档
                <ExternalLinkIcon data-icon="inline-end" />
              </a>
            </Button>
          ) : (
            <Button type="button" variant="outline" disabled>
              OpenAPI 文档暂不可用
            </Button>
          )}
        </div>
      </PublicBand>

      <PublicBand className="bg-muted/30">
        {docsQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : (
          <div className="grid gap-6 xl:grid-cols-[0.75fr_1.25fr]">
            <div className="space-y-4">
              {docsQuery.error ? <InlineError error={docsQuery.error} title="公开文档暂时不可用，已展示本地内容" /> : null}
              <InfoPanel title="快速开始" items={docs.quickStart} />
              <InfoPanel title="SDK 支持" items={docs.sdkTargets} />
            </div>
            <div className="space-y-6">
              <CompatibilityTable docs={docs} />
              <ProviderPresetTable docs={docs} />
              <Examples docs={docs} />
            </div>
          </div>
        )}
      </PublicBand>
    </PublicFrame>
  )
}

function CompatibilityTable({ docs }: { docs: PublicDocsBundle }) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <div className="border-b border-border px-4 py-3 font-semibold text-foreground">协议兼容</div>
      <PaginatedRows items={docs.compatibility}>
        {({ pageItems }) => (
          <table className="w-full table-fixed text-sm">
            <thead className="bg-muted/40">
              <tr>
                <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">协议</th>
                <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">基础路径</th>
                <th className="w-[28%] px-4 py-3 text-left font-medium text-muted-foreground">客户端</th>
                <th className="w-[38%] px-4 py-3 text-left font-medium text-muted-foreground">能力边界</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((item) => (
                <tr key={item.protocol} className="border-t border-border align-top">
                  <td className="px-4 py-3 font-medium text-foreground">{item.protocol}</td>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{item.basePath}</td>
                  <td className="px-4 py-3 text-muted-foreground">{item.clients.join('、')}</td>
                  <td className="px-4 py-3 text-muted-foreground">
                    <div>{item.capabilities.join('、')}</div>
                    <div className="mt-2 text-xs">{item.notes}</div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </PaginatedRows>
    </div>
  )
}

function ProviderPresetTable({ docs }: { docs: PublicDocsBundle }) {
  const rows = docs.providerPresets.slice(0, 8)
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <div className="border-b border-border px-4 py-3 font-semibold text-foreground">Provider 方案预设</div>
      <div className="grid gap-3 p-4 md:grid-cols-2">
        {rows.length ? rows.map((preset) => (
          <div key={preset.code} className="rounded-lg border border-border p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="font-medium text-foreground">{preset.displayName}</div>
                <div className="mt-1 font-mono text-xs text-muted-foreground">{preset.code}</div>
              </div>
              <StatusBadge tone="info">{preset.compatibilitySurface}</StatusBadge>
            </div>
            <div className="mt-3 text-sm text-muted-foreground">{preset.supportStrategy}</div>
            <div className="mt-2 text-xs text-muted-foreground">{preset.capabilityTags.join('、')}</div>
          </div>
        )) : (
          <div className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">当前本地文档未包含 Provider 预设。</div>
        )}
      </div>
    </div>
  )
}

function Examples({ docs }: { docs: PublicDocsBundle }) {
  return (
    <div className="grid gap-3 md:grid-cols-2">
      {docs.examples.slice(0, 4).map((example) => (
        <div key={example.id} className="rounded-lg border border-border bg-card p-4 shadow-sm">
          <div className="flex items-center gap-2 font-medium text-foreground">
            <TerminalSquareIcon className="size-4 text-primary" />
            {example.title}
          </div>
          <pre className="mt-3 overflow-auto rounded-lg bg-muted/60 p-3 text-xs leading-6 text-foreground">{example.code}</pre>
        </div>
      ))}
    </div>
  )
}

function InfoPanel({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="rounded-lg border border-border bg-card p-5 shadow-sm">
      <div className="font-semibold text-foreground">{title}</div>
      <div className="mt-4 space-y-3 text-sm leading-6 text-muted-foreground">
        {items.map((item) => <div key={item}>{item}</div>)}
      </div>
    </div>
  )
}
