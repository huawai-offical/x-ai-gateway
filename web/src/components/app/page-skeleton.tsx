import { PanelSkeleton } from './panel-skeleton'

export function PageSkeleton({ count = 2 }: { count?: number }) {
  return (
    <div className="space-y-6">
      {Array.from({ length: count }).map((_, index) => (
        <PanelSkeleton key={index} />
      ))}
    </div>
  )
}
