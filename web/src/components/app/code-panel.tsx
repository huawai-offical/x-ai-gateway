import type { ReactNode } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'

type CodePanelProps = {
  title?: string
  code?: string | null
  emptyText?: string
  footer?: ReactNode
  className?: string
}

export function CodePanel({
  title = '输出',
  code,
  emptyText = '暂无内容。',
  footer,
  className,
}: CodePanelProps) {
  return (
    <Card className={cn('border-border/55 bg-card/88 shadow-[0_1px_2px_rgba(15,23,42,0.06)] backdrop-blur', className)}>
      <CardHeader className="gap-2 border-b border-border/50">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 p-0">
        <pre className="scrollbar-subtle max-h-[28rem] overflow-auto px-5 py-5 text-xs leading-6 text-foreground">
          {code?.trim() ? code : emptyText}
        </pre>
        {footer ? <div className="border-t border-border/50 px-5 py-4">{footer}</div> : null}
      </CardContent>
    </Card>
  )
}
