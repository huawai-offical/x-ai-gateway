import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

type InfoGridItem = {
  key: string
  label: string
  value: ReactNode
  hint?: ReactNode
  className?: string
}

type InfoGridProps = {
  items: InfoGridItem[]
  className?: string
  columnsClassName?: string
}

export function InfoGrid({
  items,
  className,
  columnsClassName,
}: InfoGridProps) {
  if (!items.length) {
    return null
  }

  return (
    <div className={cn('grid gap-4 md:grid-cols-2 xl:grid-cols-4', columnsClassName, className)}>
      {items.map((item) => (
        <div
          key={item.key}
          className={cn(
            'flex min-w-0 flex-col gap-2 rounded-xl border border-border/45 bg-muted/18 p-5 shadow-[inset_0_1px_0_rgba(255,255,255,0.035)]',
            item.className,
          )}
        >
          <div className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">
            {item.label}
          </div>
          <div className="break-words text-sm font-medium text-foreground">
            {item.value}
          </div>
          {item.hint ? <div className="text-xs leading-5 text-muted-foreground">{item.hint}</div> : null}
        </div>
      ))}
    </div>
  )
}
