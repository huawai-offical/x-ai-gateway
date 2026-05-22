import { useIsFetching, useIsMutating } from '@tanstack/react-query'

export function GlobalActivityBar() {
  const isFetching = useIsFetching()
  const isMutating = useIsMutating()
  const active = isFetching + isMutating > 0

  return (
    <div className="absolute inset-x-0 top-0 h-0.5 overflow-hidden rounded-full">
      {active ? (
        <div className="h-full w-full origin-left animate-pulse bg-gradient-to-r from-cyan-500 via-blue-500 to-slate-200" />
      ) : null}
    </div>
  )
}
