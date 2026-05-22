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
        'flex min-h-28 flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-border bg-muted/35 px-5 py-8 text-center',
        className,
      )}
    >
      <div className="flex size-11 items-center justify-center rounded-2xl bg-background text-muted-foreground shadow-sm">
        {icon ?? <InboxIcon className="size-5" />}
      </div>
      <div className="flex flex-col gap-0.5">
        <div className="font-medium text-foreground">{title}</div>
      </div>
    </div>
  )
}
