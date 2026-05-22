import type { PropsWithChildren, ReactNode } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

type DataTableShellProps = PropsWithChildren<{
  title: string
  actions?: ReactNode
}>

export function DataTableShell({
  title,
  actions,
  children,
}: DataTableShellProps) {
  return (
    <Card className="border-border/60 bg-card/92 shadow-sm">
      <CardHeader className="flex flex-col gap-4 border-b border-border/60 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex flex-col gap-1">
          <CardTitle className="text-lg font-semibold">{title}</CardTitle>
        </div>
        {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
      </CardHeader>
      <CardContent className="p-5">{children}</CardContent>
    </Card>
  )
}
