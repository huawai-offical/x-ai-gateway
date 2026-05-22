import { Children, type PropsWithChildren, type ReactNode } from 'react'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { cn } from '@/lib/utils'

type PageSectionProps = PropsWithChildren<{
  kicker?: string
  title: string
  actions?: ReactNode
  className?: string
  contentClassName?: string
}>

export function PageSection({
  kicker,
  title,
  actions,
  className,
  contentClassName,
  children,
}: PageSectionProps) {
  const hasContent = Children.count(children) > 0

  return (
    <Card className={cn('border-border/60 bg-card/92 shadow-sm', className)}>
      <CardHeader className="gap-4 border-b border-border/60 pb-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex flex-col gap-2">
            {kicker ? (
              <div className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                {kicker}
              </div>
            ) : null}
            <h2 className="text-xl font-semibold tracking-tight text-foreground">{title}</h2>
          </div>
          {actions ? <div className="flex flex-wrap items-center gap-2.5">{actions}</div> : null}
        </div>
      </CardHeader>
      {hasContent ? <CardContent className={cn('p-5', contentClassName)}>{children}</CardContent> : null}
    </Card>
  )
}
