import type { PropsWithChildren, ReactNode } from 'react'
import { cn } from '@/lib/utils'

type PageColumnsProps = PropsWithChildren<{
  rail?: ReactNode
  className?: string
  contentClassName?: string
  railClassName?: string
  railSticky?: boolean
  railScrollable?: boolean
}>

export function PageColumns({
  rail,
  className,
  contentClassName,
  railClassName,
  railSticky = true,
  railScrollable = true,
  children,
}: PageColumnsProps) {
  return (
    <div
      className={cn(
        'grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem] xl:items-start',
        className,
      )}
    >
      <div className={cn('min-w-0 flex flex-col gap-6', contentClassName)}>{children}</div>
      {rail ? (
        <aside
          className={cn(
            'min-w-0 flex flex-col gap-6',
            railSticky && 'xl:sticky xl:top-24',
            railScrollable && 'xl:max-h-[calc(100svh-7.5rem)] xl:overflow-y-auto xl:pr-1',
            railClassName,
          )}
        >
          {rail}
        </aside>
      ) : null}
    </div>
  )
}
