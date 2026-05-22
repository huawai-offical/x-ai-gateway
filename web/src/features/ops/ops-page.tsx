import { startTransition, useDeferredValue, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  AlertTriangleIcon,
  ArrowUpRightIcon,
  BarChart3Icon,
  Clock3Icon,
  DatabaseZapIcon,
  GaugeIcon,
  TrendingUpIcon,
} from 'lucide-react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  XAxis,
  YAxis,
} from 'recharts'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { MetricCard } from '@/components/app/metric-card'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { formatInstant } from '@/lib/format'
import { useTypedQuery } from '@/lib/typed-react-query'
import { opsApi } from './api'
import type {
  AnalyticsBreakdownItem,
  CapacityPressureItem,
  DistributedKeyAnalyticsItem,
  OpsAnalyticsOverview,
  OpsCapacitySummary,
  OpsSloRisk,
  OpsSloSummary,
  OpsSummary,
} from './types'

const RANGE_OPTIONS = [
  { value: '1h', label: '最近 1 小时', hours: 1 },
  { value: '6h', label: '最近 6 小时', hours: 6 },
  { value: '24h', label: '最近 24 小时', hours: 24 },
] as const

const BUCKET_OPTIONS = [
  { value: '5', label: '5 分钟分桶' },
  { value: '15', label: '15 分钟分桶' },
  { value: '60', label: '60 分钟分桶' },
] as const

const qpsChartConfig = {
  qps: {
    label: 'QPS',
    color: 'var(--chart-1)',
  },
} satisfies ChartConfig

const cacheChartConfig = {
  cacheHitRatio: {
    label: '缓存命中率',
    color: 'var(--chart-2)',
  },
} satisfies ChartConfig

const tpmChartConfig = {
  tpm: {
    label: 'TPM',
    color: 'var(--chart-3)',
  },
} satisfies ChartConfig

const latencyChartConfig = {
  latencyP95: {
    label: '延迟 P95',
    color: 'var(--chart-5)',
  },
} satisfies ChartConfig

const failedChartConfig = {
  failedRequestCount: {
    label: '失败请求',
    color: 'var(--chart-4)',
  },
} satisfies ChartConfig

const cacheTokenChartConfig = {
  savedInputTokens: {
    label: '节省输入 Token',
    color: 'var(--chart-2)',
  },
  cacheWriteTokens: {
    label: '缓存写入 Token',
    color: 'var(--chart-5)',
  },
} satisfies ChartConfig

const keyBreakdownChartConfig = {
  totalTokens: {
    label: '总 Token',
    color: 'var(--chart-3)',
  },
  cacheHitRatioPercent: {
    label: '缓存命中率',
    color: 'var(--chart-2)',
  },
} satisfies ChartConfig

type RangeKey = (typeof RANGE_OPTIONS)[number]['value']
type OverviewEntry = {
  title: string
  description: string
  signal: string
  to: string
  cta: string
}

export function OpsPage() {
  const [rangeKey, setRangeKey] = useState<RangeKey>('6h')
  const [bucketMinutes, setBucketMinutes] = useState('15')

  const deferredRangeKey = useDeferredValue(rangeKey)
  const deferredBucketMinutes = useDeferredValue(bucketMinutes)

  const timeWindow = useMemo(
    () => buildTimeWindow(deferredRangeKey),
    [deferredRangeKey],
  )

  const summaryQuery = useTypedQuery<OpsSummary>({
    queryKey: ['ops-summary'],
    queryFn: opsApi.summary,
    refetchInterval: 10_000,
  })

  const overviewQuery = useTypedQuery<OpsAnalyticsOverview>({
    queryKey: ['ops-analytics-overview', timeWindow.from, timeWindow.to, deferredBucketMinutes],
    queryFn: () =>
      opsApi.analyticsOverview({
        from: timeWindow.from,
        to: timeWindow.to,
        bucketMinutes: Number(deferredBucketMinutes),
      }),
    refetchInterval: 30_000,
  })

  const sloQuery = useTypedQuery<OpsSloSummary>({
    queryKey: ['ops-slo'],
    queryFn: opsApi.sloSummary,
    refetchInterval: 30_000,
  })

  const capacityQuery = useTypedQuery<OpsCapacitySummary>({
    queryKey: ['ops-capacity'],
    queryFn: opsApi.capacitySummary,
    refetchInterval: 30_000,
  })

  const overview = overviewQuery.data
  const summary = summaryQuery.data

  const trendData = useMemo(() => {
    if (!overview) return []
    return overview.timeline.map((bucket) => ({
      bucketStart: bucket.bucketStart,
      bucketLabel: formatBucketLabel(bucket.bucketStart, overview.bucketMinutes),
      qps: bucket.routeDecisionCount / Math.max(1, overview.bucketMinutes * 60),
      cacheHitRatio:
        bucket.routeDecisionCount > 0
          ? (bucket.cacheHitCount / bucket.routeDecisionCount) * 100
          : 0,
      tpm: bucket.totalTokens / Math.max(1, overview.bucketMinutes),
      latencyP95: bucket.p95LatencyMs,
      failedRequestCount: bucket.failedRequestCount,
      cacheHitTokens: bucket.cacheHitTokens,
      cacheWriteTokens: bucket.cacheWriteTokens,
      savedInputTokens: bucket.savedInputTokens,
      usageRecordCount: bucket.usageRecordCount,
    }))
  }, [overview])

  const usageCompletenessData = useMemo(() => {
    if (!overview) return []
    const colorPalette = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)']
    return overview.usageCompletenessBreakdown.map((item, index) => ({
      ...item,
      fill: colorPalette[index % colorPalette.length],
    }))
  }, [overview])

  const totalTokens = useMemo(
    () => overview?.timeline.reduce((sum, item) => sum + item.totalTokens, 0) ?? 0,
    [overview],
  )

  const keyBreakdownData = useMemo<Array<DistributedKeyAnalyticsItem & { cacheHitRatioPercent: number }>>(() => {
    const rows = overview?.distributedKeyBreakdown ?? []
    return rows.map((item) => ({
      ...item,
      cacheHitRatioPercent: item.cacheHitRatio * 100,
    }))
  }, [overview?.distributedKeyBreakdown])

  const keyBreakdownChartData = useMemo(
    () =>
      keyBreakdownData.slice(0, 8).map((item) => ({
        keyLabel: item.keyPrefix || item.keyName,
        totalTokens: item.totalTokens,
        cacheHitRatioPercent: item.cacheHitRatioPercent,
      })),
    [keyBreakdownData],
  )

  const overviewMetrics = useMemo(() => {
    const sampledMinutes = overview ? sampledMinutesForOverview(overview) : 0
    const cacheHitRatio =
      overview && overview.sampledRouteDecisionCount > 0
        ? overview.sampledCacheHitCount / overview.sampledRouteDecisionCount
        : 0
    const sampleTpm = sampledMinutes > 0 ? totalTokens / sampledMinutes : 0
    const latestLatency = resolveLatestLatency(trendData, summary?.snapshot.p95LatencyMs ?? 0)

    return [
      {
        label: '实时 QPS',
        value: summary ? summary.snapshot.qps.toFixed(2) : '--',
        hint: summary ? `最近快照 ${formatInstant(summary.snapshot.observedAt)}` : '等待实时快照',
      },
      {
        label: '缓存命中率',
        value: formatPercent(cacheHitRatio),
        hint: overview ? `${overview.sampledCacheHitCount}/${overview.sampledRouteDecisionCount} 命中` : '等待分析窗口',
      },
      {
        label: 'TPM',
        value: formatCompactNumber(sampleTpm),
        hint: `${RANGE_OPTIONS.find((item) => item.value === rangeKey)?.label ?? '当前窗口'} 平均 Token 吞吐`,
      },
      {
        label: '延迟 P95',
        value: `${latestLatency.toFixed(0)} ms`,
        hint: '来自 request_log duration 分桶聚合',
      },
      {
        label: '活跃告警',
        value: summary?.snapshot.activeAlerts ?? 0,
        hint: summary?.snapshot.affectedEntities.length
          ? `${summary.snapshot.affectedEntities.length} 个受影响对象`
          : '当前无显式受影响对象',
      },
      {
        label: '上游失败',
        value: summary?.snapshot.providerFailures ?? 0,
        hint: summary ? `错误率 ${formatPercent(summary.snapshot.errorRate)}` : '等待实时快照',
      },
    ]
  }, [overview, rangeKey, summary, totalTokens, trendData])

  const overviewEntries = useMemo<OverviewEntry[]>(
    () => [
      {
        title: '角色协同视图',
        description: '按角色整理接入、运营、排障、计费与系统动作，并补充批量可信状态。',
        signal: capacityQuery.data
          ? `${capacityQuery.data.distributedKeys.length} 个热点访问密钥待关注`
          : '角色分工与批量可信补充视图',
        to: '/console/dashboard',
        cta: '打开角色协同',
      },
      {
        title: '事件处置视图',
        description: '聚焦打开中的风险事件、受影响对象、外发投递与事件时间线。',
        signal: summary
          ? `${summary.alerts.length} 个开放告警待处置`
          : '保留事件与处置补充视图',
        to: '/console/incidents',
        cta: '打开事件处置',
      },
      {
        title: '链路追踪',
        description: '按请求 ID 或网关资源键查看请求落点、缓存命中与异常链路。',
        signal: '请求级排障入口',
        to: '/console/traces',
        cta: '打开链路追踪',
      },
    ],
    [capacityQuery.data, summary],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="管理控制台"
        title="智能运维总览主面板"
      >
        <div className="flex flex-col gap-5">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-[14rem_14rem_minmax(0,1fr)]">
            <Select
              value={rangeKey}
              onValueChange={(nextValue) => {
                startTransition(() => {
                  setRangeKey(nextValue as RangeKey)
                })
              }}
            >
              <SelectTrigger className="w-full bg-background">
                <SelectValue placeholder="选择时间窗口" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {RANGE_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>

            <Select
              value={bucketMinutes}
              onValueChange={(nextValue) => {
                startTransition(() => {
                  setBucketMinutes(nextValue)
                })
              }}
            >
              <SelectTrigger className="w-full bg-background">
                <SelectValue placeholder="选择分桶" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {BUCKET_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>

            <div className="flex flex-wrap gap-2">
              <StatusBadge tone="info">
                分析窗口：{formatInstant(timeWindow.from)} - {formatInstant(timeWindow.to)}
              </StatusBadge>
              <StatusBadge tone="success">图表与实时事件</StatusBadge>
            </div>
          </div>

          {summaryQuery.isPending || overviewQuery.isPending ? (
            <PageSkeleton count={2} />
          ) : summaryQuery.error ? (
            <InlineError error={summaryQuery.error} title="实时摘要加载失败" />
          ) : overviewQuery.error ? (
            <InlineError error={overviewQuery.error} title="分析概览加载失败" />
          ) : (
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {overviewMetrics.map((metric) => (
                <MetricCard
                  key={metric.label}
                  label={metric.label}
                  value={metric.value}
                  hint={metric.hint}
                />
              ))}
            </div>
          )}
        </div>
      </PageSection>

      <PageSection kicker="协同入口" title="总览协同视图入口">
        <div className="grid gap-4 lg:grid-cols-3">
          {overviewEntries.map((entry) => (
            <Card key={entry.title} className="border-border/60 bg-card/92 shadow-sm">
              <CardHeader className="gap-2 border-b border-border/60">
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1">
                    <CardTitle className="text-base">{entry.title}</CardTitle>
                    <div className="text-sm leading-6 text-muted-foreground">{entry.description}</div>
                  </div>
                  <StatusBadge tone="info">{entry.signal}</StatusBadge>
                </div>
              </CardHeader>
              <CardContent className="p-5">
                <Button asChild variant="outline" size="sm">
                  <Link to={entry.to}>
                    {entry.cta}
                    <ArrowUpRightIcon data-icon="inline-end" />
                  </Link>
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      </PageSection>

      <PageSection
        kicker="核心信号"
        title="关键时间序列"
      >
        {overviewQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : overviewQuery.error ? (
          <InlineError error={overviewQuery.error} title="趋势图加载失败" />
        ) : trendData.length ? (
          <div className="grid gap-4 xl:grid-cols-3">
            <TrendChartCard title="QPS" icon={<GaugeIcon className="size-4" />}>
              <ChartContainer config={qpsChartConfig} className="h-72 w-full">
                <AreaChart data={trendData} margin={{ left: 12, right: 12, top: 12 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="bucketLabel" tickLine={false} axisLine={false} minTickGap={18} />
                  <YAxis tickLine={false} axisLine={false} width={48} />
                  <ChartTooltip
                    cursor={false}
                    content={<ChartTooltipContent indicator="line" />}
                  />
                  <Area
                    dataKey="qps"
                    type="monotone"
                    fill="var(--color-qps)"
                    fillOpacity={0.18}
                    stroke="var(--color-qps)"
                    strokeWidth={2}
                  />
                </AreaChart>
              </ChartContainer>
            </TrendChartCard>

            <TrendChartCard title="缓存命中率" icon={<DatabaseZapIcon className="size-4" />}>
              <ChartContainer config={cacheChartConfig} className="h-72 w-full">
                <LineChart data={trendData} margin={{ left: 12, right: 12, top: 12 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="bucketLabel" tickLine={false} axisLine={false} minTickGap={18} />
                  <YAxis tickLine={false} axisLine={false} width={52} unit="%" />
                  <ChartTooltip
                    cursor={false}
                    content={<ChartTooltipContent indicator="line" />}
                  />
                  <Line
                    dataKey="cacheHitRatio"
                    type="monotone"
                    stroke="var(--color-cacheHitRatio)"
                    strokeWidth={2.5}
                    dot={false}
                  />
                </LineChart>
              </ChartContainer>
            </TrendChartCard>

            <TrendChartCard title="TPM 使用量" icon={<BarChart3Icon className="size-4" />}>
              <ChartContainer config={tpmChartConfig} className="h-72 w-full">
                <AreaChart data={trendData} margin={{ left: 12, right: 12, top: 12 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="bucketLabel" tickLine={false} axisLine={false} minTickGap={18} />
                  <YAxis tickLine={false} axisLine={false} width={52} />
                  <ChartTooltip
                    cursor={false}
                    content={<ChartTooltipContent indicator="line" />}
                  />
                  <Area
                    dataKey="tpm"
                    type="monotone"
                    fill="var(--color-tpm)"
                    fillOpacity={0.18}
                    stroke="var(--color-tpm)"
                    strokeWidth={2}
                  />
                </AreaChart>
              </ChartContainer>
            </TrendChartCard>

            <TrendChartCard title="延迟 P95" icon={<Clock3Icon className="size-4" />}>
              <ChartContainer config={latencyChartConfig} className="h-72 w-full">
                <LineChart data={trendData} margin={{ left: 12, right: 12, top: 12 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="bucketLabel" tickLine={false} axisLine={false} minTickGap={18} />
                  <YAxis tickLine={false} axisLine={false} width={60} />
                  <ChartTooltip
                    cursor={false}
                    content={<ChartTooltipContent indicator="line" />}
                  />
                  <Line
                    dataKey="latencyP95"
                    type="monotone"
                    stroke="var(--color-latencyP95)"
                    strokeWidth={2.5}
                    dot={false}
                  />
                </LineChart>
              </ChartContainer>
            </TrendChartCard>

            <TrendChartCard title="失败请求趋势" icon={<AlertTriangleIcon className="size-4" />}>
              <ChartContainer config={failedChartConfig} className="h-72 w-full">
                <BarChart data={trendData} margin={{ left: 12, right: 12, top: 12 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="bucketLabel" tickLine={false} axisLine={false} minTickGap={18} />
                  <YAxis tickLine={false} axisLine={false} width={52} />
                  <ChartTooltip
                    cursor={false}
                    content={<ChartTooltipContent indicator="line" />}
                  />
                  <Bar dataKey="failedRequestCount" fill="var(--color-failedRequestCount)" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ChartContainer>
            </TrendChartCard>

            <TrendChartCard title="缓存 Token 收益" icon={<DatabaseZapIcon className="size-4" />}>
              <ChartContainer config={cacheTokenChartConfig} className="h-72 w-full">
                <LineChart data={trendData} margin={{ left: 12, right: 12, top: 12 }}>
                  <CartesianGrid vertical={false} />
                  <XAxis dataKey="bucketLabel" tickLine={false} axisLine={false} minTickGap={18} />
                  <YAxis tickLine={false} axisLine={false} width={52} />
                  <ChartTooltip
                    cursor={false}
                    content={<ChartTooltipContent indicator="line" />}
                  />
                  <Line dataKey="savedInputTokens" type="monotone" stroke="var(--color-savedInputTokens)" strokeWidth={2.5} dot={false} />
                  <Line dataKey="cacheWriteTokens" type="monotone" stroke="var(--color-cacheWriteTokens)" strokeWidth={2.5} dot={false} />
                </LineChart>
              </ChartContainer>
            </TrendChartCard>
          </div>
        ) : (
          <EmptyState
            title="当前窗口还没有可视化样本"
            icon={<TrendingUpIcon className="size-5" />}
          />
        )}
      </PageSection>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        <PageSection
        kicker="构成拆解"
          title="热点来源与缓存画像"
        >
          {overviewQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : overviewQuery.error ? (
            <InlineError error={overviewQuery.error} title="构成拆解加载失败" />
          ) : overview ? (
            <div className="grid gap-4 lg:grid-cols-4">
              <BreakdownCard title="上游来源" items={overview.providerBreakdown} />
              <BreakdownCard title="模型分组" items={overview.modelGroupBreakdown} />
              <BreakdownCard title="缓存来源" items={overview.cacheSourceBreakdown} />
              <Card className="border-border/60 bg-background/88 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <CardTitle className="text-base">用量完整性</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5">
                  {usageCompletenessData.length ? (
                    <ChartContainer
                      config={{
                        usage: { label: '记录数', color: 'var(--chart-1)' },
                      }}
                      className="h-52 w-full"
                    >
                      <PieChart>
                        <Pie
                          data={usageCompletenessData}
                          dataKey="count"
                          nameKey="key"
                          cx="50%"
                          cy="50%"
                          outerRadius={76}
                          label
                        >
                          {usageCompletenessData.map((entry) => (
                            <Cell key={entry.key} fill={entry.fill} />
                          ))}
                        </Pie>
                        <Legend />
                        <ChartTooltip
                          cursor={false}
                          content={<ChartTooltipContent indicator="line" />}
                        />
                      </PieChart>
                    </ChartContainer>
                  ) : (
                    <EmptyState title="暂无完整性数据" className="border-none bg-transparent shadow-none" />
                  )}
                </CardContent>
              </Card>
            </div>
          ) : (
            <EmptyState title="当前没有构成拆解数据" />
          )}
        </PageSection>

      </div>

      <PageSection
        kicker="访问密钥洞察"
        title="访问密钥用量与缓存明细"
      >
        {overviewQuery.isPending ? (
          <PageSkeleton count={2} />
        ) : overviewQuery.error ? (
          <InlineError error={overviewQuery.error} title="访问密钥维度数据加载失败" />
        ) : keyBreakdownData.length ? (
          <div className="grid gap-4">
            <Card className="border-border/60 bg-card/92 shadow-sm">
              <CardHeader className="gap-2 border-b border-border/60">
                <CardTitle className="text-base">Top 访问密钥 Token 与命中率</CardTitle>
              </CardHeader>
              <CardContent className="p-5">
                <ChartContainer config={keyBreakdownChartConfig} className="h-72 w-full">
                  <ComposedChart data={keyBreakdownChartData} margin={{ left: 12, right: 12, top: 12 }}>
                    <CartesianGrid vertical={false} />
                    <XAxis dataKey="keyLabel" tickLine={false} axisLine={false} minTickGap={12} />
                    <YAxis yAxisId="tokens" tickLine={false} axisLine={false} width={56} />
                    <YAxis yAxisId="ratio" orientation="right" tickLine={false} axisLine={false} width={56} unit="%" />
                    <ChartTooltip cursor={false} content={<ChartTooltipContent indicator="line" />} />
                    <Bar yAxisId="tokens" dataKey="totalTokens" fill="var(--color-totalTokens)" radius={[6, 6, 0, 0]} />
                    <Line yAxisId="ratio" dataKey="cacheHitRatioPercent" type="monotone" stroke="var(--color-cacheHitRatioPercent)" strokeWidth={2.5} dot={false} />
                  </ComposedChart>
                </ChartContainer>
              </CardContent>
            </Card>

            <PaginatedRows items={keyBreakdownData}>
              {({ pageItems }) => (
                <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                  <table className="w-full table-fixed text-sm">
                    <thead className="bg-muted/30">
                      <tr>
                        <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">访问密钥</th>
                        <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">请求</th>
                        <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">Token 总量</th>
                        <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">缓存命中</th>
                        <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">缓存收益</th>
                        <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">失败</th>
                        <th className="w-[8%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">P95</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pageItems.map((item) => (
                        <tr key={`${item.distributedKeyId ?? 'unknown'}-${item.keyPrefix}`} className="border-b border-border/40 align-top">
                          <td className="px-4 py-3">
                            <div className="truncate font-medium text-foreground">{item.keyName}</div>
                            <div className="truncate text-xs text-muted-foreground">{item.keyPrefix} · ID {item.distributedKeyId ?? '无'}</div>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{formatCompactNumber(item.routeDecisionCount)}</td>
                          <td className="truncate px-4 py-3 text-muted-foreground">{formatCompactNumber(item.totalTokens)}</td>
                          <td className="px-4 py-3">
                            <StatusBadge tone={item.cacheHitRatio >= 0.5 ? 'success' : item.cacheHitRatio >= 0.2 ? 'warning' : 'danger'}>
                              {formatCompactNumber(item.cacheHitCount)} / {formatPercent(item.cacheHitRatio)}
                            </StatusBadge>
                          </td>
                          <td className="truncate px-4 py-3 text-muted-foreground">
                            节省 {formatCompactNumber(item.savedInputTokens)} / 命中 {formatCompactNumber(item.cacheHitTokens)}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{formatCompactNumber(item.failedRequestCount)}</td>
                          <td className="truncate px-4 py-3 text-muted-foreground">{item.avgLatencyMs} ms</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </PaginatedRows>
          </div>
        ) : (
          <EmptyState title="当前窗口没有访问密钥维度样本" />
        )}
      </PageSection>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]">
        <PageSection
          kicker="SLO 风险"
          title="错误预算与风险"
        >
          {sloQuery.isPending ? (
            <PageSkeleton count={2} />
          ) : sloQuery.error ? (
            <InlineError error={sloQuery.error} title="错误预算加载失败" />
          ) : sloQuery.data ? (
            <div className="flex flex-col gap-4">
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <MetricCard label="风险等级" value={sloQuery.data.summary.riskLevel} hint="当前风险等级" />
                <MetricCard label="燃尽速率" value={sloQuery.data.summary.burnRate.toFixed(2)} hint="越高说明 error budget 消耗越快" />
                <MetricCard label="剩余错误预算" value={formatPercent(sloQuery.data.summary.errorBudgetRemainingRatio)} hint="剩余额度" />
                <MetricCard label="静默告警" value={sloQuery.data.summary.silencedAlertCount} hint="当前被静默的告警数" />
              </div>
              <div className="grid gap-4 lg:grid-cols-2">
                {sloQuery.data.risks.map((risk) => (
                  <RiskCard key={`${risk.policyName}-${risk.scopeType}-${risk.scopeRef ?? 'global'}`} risk={risk} />
                ))}
                {sloQuery.data.recommendedActions.map((action) => (
                  <RecommendationCard key={action} title="建议动作" content={action} />
                ))}
              </div>
            </div>
          ) : (
            <EmptyState title="当前没有错误预算数据" />
          )}
        </PageSection>

        <PageSection
          kicker="容量压力"
          title="预算与并发压力"
        >
          {capacityQuery.isPending ? (
            <PageSkeleton count={2} />
          ) : capacityQuery.error ? (
            <InlineError error={capacityQuery.error} title="预算压力加载失败" />
          ) : capacityQuery.data ? (
            <div className="flex flex-col gap-4">
              <div className="grid gap-4">
                {capacityQuery.data.distributedKeys.slice(0, 4).map((item) => (
                  <CapacityCard key={item.distributedKeyId} item={item} />
                ))}
              </div>
              {capacityQuery.data.recommendedActions.length ? (
                <div className="grid gap-3">
                  {capacityQuery.data.recommendedActions.map((action) => (
                    <RecommendationCard key={action} title="建议动作" content={action} compact />
                  ))}
                </div>
              ) : null}
            </div>
          ) : (
            <EmptyState title="当前没有容量数据" />
          )}
        </PageSection>
      </div>

      <PageSection
        kicker="告警聚焦"
        title="开放告警与高优先级入口"
      >
        {summaryQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : summaryQuery.error ? (
          <InlineError error={summaryQuery.error} title="开放告警加载失败" />
        ) : summary?.alerts.length ? (
          <div className="grid gap-4 xl:grid-cols-3">
            {summary.alerts.slice(0, 3).map((alert) => (
              <Card key={alert.id} className="border-border/60 bg-card/92 shadow-sm">
                <CardHeader className="gap-2 border-b border-border/60">
                  <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1">
                    <CardTitle className="text-base">{alert.title}</CardTitle>
                    <div className="text-sm text-muted-foreground">{alert.entityType ?? '系统'} / {alert.entityRef ?? '-'}</div>
                  </div>
                    <StatusBadge tone={alert.severity === 'HIGH' ? 'danger' : 'warning'}>
                      {alert.severity}
                    </StatusBadge>
                  </div>
                </CardHeader>
                <CardContent className="flex flex-col gap-3 p-5">
                  <div className="text-sm leading-6 text-foreground">{alert.message}</div>
                  <div className="flex flex-wrap gap-2">
                    <Button asChild size="sm" variant="outline">
                      <Link to="/ops/alerts">
                        确认 / 处置
                        <ArrowUpRightIcon data-icon="inline-end" />
                      </Link>
                    </Button>
                    <Button asChild size="sm" variant="outline">
                      <Link to="/traces">
                        查链路
                        <ArrowUpRightIcon data-icon="inline-end" />
                      </Link>
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <EmptyState
            title="当前没有开放告警"
            icon={<AlertTriangleIcon className="size-5" />}
          />
        )}
      </PageSection>
    </div>
  )
}

function TrendChartCard({
  title,
  icon,
  children,
}: {
  title: string
  icon: ReactNode
  children: ReactNode
}) {
  return (
    <Card className="border-border/60 bg-card/92 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <div className="flex items-center gap-2 text-muted-foreground">
          {icon}
          <span className="text-xs font-medium uppercase tracking-[0.16em]">{title}</span>
        </div>
      </CardHeader>
      <CardContent className="p-5">{children}</CardContent>
    </Card>
  )
}

function BreakdownCard({
  title,
  items,
}: {
  title: string
  items: AnalyticsBreakdownItem[]
}) {
  return (
    <Card className="border-border/60 bg-background/88 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 p-5">
        {items.length ? (
          items.slice(0, 5).map((item) => (
            <div
              key={item.key}
              className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 bg-background px-4 py-3"
            >
              <div className="min-w-0">
                <div className="truncate text-sm font-medium text-foreground">{item.key}</div>
                <div className="text-xs text-muted-foreground">
                  次数 {formatCompactNumber(item.count)} · 节省 {formatCompactNumber(item.savedInputTokens)}
                </div>
              </div>
              <StatusBadge tone="info">{formatCompactNumber(item.cacheHitTokens)}</StatusBadge>
            </div>
          ))
        ) : (
          <EmptyState title={`${title} 暂无数据`} className="border-none bg-transparent shadow-none" />
        )}
      </CardContent>
    </Card>
  )
}

function RiskCard({ risk }: { risk: OpsSloRisk }) {
  return (
    <Card className="border-border/60 bg-background/88 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1">
            <CardTitle className="text-base">{risk.policyName}</CardTitle>
            <div className="text-sm text-muted-foreground">{risk.scopeType}{risk.scopeRef ? ` / ${risk.scopeRef}` : ''}</div>
          </div>
          <StatusBadge tone={riskTone(risk.riskLevel)}>{risk.riskLevel}</StatusBadge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 p-5 text-sm">
        <div className="text-foreground">
          燃尽速率 {risk.burnRate.toFixed(2)} · 剩余预算 {formatPercent(risk.errorBudgetRemainingRatio)}
        </div>
        <div className="text-muted-foreground">{risk.suspectedCauses[0] ?? '当前没有额外风险说明。'}</div>
        {risk.suggestedActions.length ? (
          <div className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-muted-foreground">
            {risk.suggestedActions[0]}
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}

function CapacityCard({ item }: { item: CapacityPressureItem }) {
  return (
    <Card className="border-border/60 bg-background/88 shadow-sm">
      <CardHeader className="gap-2 border-b border-border/60">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1">
            <CardTitle className="text-base">{item.keyName}</CardTitle>
            <div className="text-sm text-muted-foreground">{item.maskedKey}</div>
          </div>
          <StatusBadge tone={pressureTone(item.pressureLevel)}>{item.pressureLevel}</StatusBadge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3 p-5 text-sm">
        <div className="grid gap-2 md:grid-cols-2">
          <MetricLine label="预算" value={`${formatCompactNumber(item.currentBudgetMicros ?? 0)} / ${formatCompactNumber(item.budgetLimitMicros ?? 0)}`} />
          <MetricLine label="RPM" value={`${formatCompactNumber(item.currentRpm ?? 0)} / ${formatCompactNumber(item.rpmLimit ?? 0)}`} />
          <MetricLine label="TPM" value={`${formatCompactNumber(item.currentTpm ?? 0)} / ${formatCompactNumber(item.tpmLimit ?? 0)}`} />
          <MetricLine label="并发" value={`${formatCompactNumber(item.currentConcurrency ?? 0)} / ${formatCompactNumber(item.concurrencyLimit ?? 0)}`} />
        </div>
        <div className="rounded-2xl border border-border/60 bg-background px-4 py-3 text-muted-foreground">
          {item.notes[0] ?? '当前窗口压力平稳。'}
        </div>
      </CardContent>
    </Card>
  )
}

function MetricLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-border/60 bg-background px-4 py-3">
      <div className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">{label}</div>
      <div className="mt-1 text-sm text-foreground">{value}</div>
    </div>
  )
}

function RecommendationCard({
  title,
  content,
  compact = false,
}: {
  title: string
  content: string
  compact?: boolean
}) {
  return (
    <Card className="border-border/60 bg-background/88 shadow-sm">
      <CardHeader className={compact ? 'gap-2 border-b border-border/60 pb-3' : 'gap-2 border-b border-border/60'}>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className={compact ? 'p-4 text-sm text-foreground' : 'p-5 text-sm text-foreground'}>
        {content}
      </CardContent>
    </Card>
  )
}

function buildTimeWindow(rangeKey: RangeKey) {
  const option = RANGE_OPTIONS.find((item) => item.value === rangeKey) ?? RANGE_OPTIONS[1]
  const to = new Date()
  const from = new Date(to.getTime() - option.hours * 60 * 60 * 1000)
  return {
    from: from.toISOString(),
    to: to.toISOString(),
  }
}

function sampledMinutesForOverview(overview: OpsAnalyticsOverview) {
  if (!overview.sampledFrom || !overview.sampledTo) return 0
  const from = new Date(overview.sampledFrom).getTime()
  const to = new Date(overview.sampledTo).getTime()
  const diffMs = Math.max(0, to - from)
  return diffMs / 60_000
}

function formatBucketLabel(bucketStart: string, bucketMinutes: number) {
  const date = new Date(bucketStart)
  return bucketMinutes >= 60
    ? date.toLocaleString('zh-CN', {
        month: 'numeric',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
        timeZone: 'Asia/Shanghai',
      })
    : date.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
        timeZone: 'Asia/Shanghai',
      })
}

function formatPercent(value: number) {
  return `${(value * 100).toFixed(1)}%`
}

function formatCompactNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: value >= 100 ? 0 : 1,
  }).format(value)
}

function riskTone(riskLevel: string) {
  switch (riskLevel) {
    case 'CRITICAL':
    case 'HIGH':
      return 'danger'
    case 'MEDIUM':
      return 'warning'
    default:
      return 'info'
  }
}

function pressureTone(pressureLevel: string) {
  switch (pressureLevel) {
    case 'HIGH':
      return 'danger'
    case 'MEDIUM':
      return 'warning'
    default:
      return 'success'
  }
}

function resolveLatestLatency(
  trendData: Array<{ latencyP95: number }>,
  fallback: number,
) {
  for (let index = trendData.length - 1; index >= 0; index -= 1) {
    if (trendData[index].latencyP95 > 0) {
      return trendData[index].latencyP95
    }
  }
  return fallback
}
