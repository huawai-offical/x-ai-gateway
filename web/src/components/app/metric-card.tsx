import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

type MetricCardProps = {
  label: string
  value: string | number
  hint?: string
  className?: string
}

export function MetricCard({ label, value, hint, className }: MetricCardProps) {
  return (
    <Card className={cn('border-border/60 bg-card/90 shadow-sm', className)}>
      <CardContent className="flex min-h-24 flex-col justify-between gap-3 p-4">
        <div className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
          {label}
        </div>
        <div className="text-2xl font-semibold text-foreground">{value}</div>
        {hint ? <div className="text-sm text-muted-foreground">{hint}</div> : null}
      </CardContent>
    </Card>
  )
}
