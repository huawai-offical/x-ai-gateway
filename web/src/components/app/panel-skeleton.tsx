import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function PanelSkeleton() {
  return (
    <Card className="border-border/55 bg-card/88 shadow-[0_1px_2px_rgba(15,23,42,0.06)] backdrop-blur">
      <CardHeader className="flex flex-col gap-2 border-b border-border/50">
        <Skeleton className="h-3 w-28" />
        <Skeleton className="h-6 w-56" />
        <Skeleton className="h-4 w-full max-w-2xl" />
      </CardHeader>
      <CardContent className="flex flex-col gap-4 p-5">
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-24 w-full" />
        <div className="grid gap-4 md:grid-cols-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      </CardContent>
    </Card>
  )
}
