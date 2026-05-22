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
    <Card className={cn('border-border/60 bg-card/92 shadow-sm', className)}>
      <CardHeader className="gap-2 border-b border-border/60">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 p-0">
        <pre className="max-h-[28rem] overflow-auto px-5 py-5 text-xs leading-6 text-foreground">
          {code?.trim() ? code : emptyText}
        </pre>
        {footer ? <div className="border-t border-border/60 px-5 py-4">{footer}</div> : null}
      </CardContent>
    </Card>
  )
}
