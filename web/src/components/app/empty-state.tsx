import type { ReactNode } from 'react'
import { InboxIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

type EmptyStateProps = {
  title: string
  icon?: ReactNode
  className?: string
}

export function EmptyState({
  title,
  icon,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex min-h-28 flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-border/55 bg-muted/24 px-5 py-8 text-center',
        className,
      )}
    >
      <div className="flex size-10 items-center justify-center rounded-lg bg-background/75 text-muted-foreground ring-1 ring-border/50">
        {icon ?? <InboxIcon className="size-5" />}
      </div>
      <div className="flex flex-col gap-0.5">
        <div className="font-medium text-foreground">{title}</div>
      </div>
    </div>
  )
}
