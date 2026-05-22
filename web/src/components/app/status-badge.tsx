import type { ComponentProps } from 'react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

type StatusBadgeTone =
  | 'neutral'
  | 'info'
  | 'success'
  | 'warning'
  | 'danger'

const toneClassName: Record<StatusBadgeTone, string> = {
  neutral: 'bg-slate-100 text-slate-700 hover:bg-slate-100',
  info: 'bg-cyan-100 text-cyan-800 hover:bg-cyan-100',
  success: 'bg-emerald-100 text-emerald-800 hover:bg-emerald-100',
  warning: 'bg-amber-100 text-amber-800 hover:bg-amber-100',
  danger: 'bg-rose-100 text-rose-800 hover:bg-rose-100',
}

export function StatusBadge({
  tone = 'neutral',
  className,
  children,
}: ComponentProps<'span'> & { tone?: StatusBadgeTone }) {
  return (
    <Badge
      variant="secondary"
      className={cn('rounded-full border-transparent px-2 py-0.5 text-xs font-medium', toneClassName[tone], className)}
    >
      {children}
    </Badge>
  )
}
