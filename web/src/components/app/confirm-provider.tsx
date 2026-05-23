import { createContext, type PropsWithChildren, useCallback, useContext, useRef, useState } from 'react'
import { ConfirmDialog } from '@/components/app/confirm-dialog'

export type ConfirmOptions = {
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  destructive?: boolean
}

const ConfirmContext = createContext<((options: ConfirmOptions) => Promise<boolean>) | null>(null)

export function ConfirmProvider({ children }: PropsWithChildren) {
  const pendingResolveRef = useRef<((confirmed: boolean) => void) | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<ConfirmOptions | null>(null)

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => {
      pendingResolveRef.current?.(false)
      pendingResolveRef.current = resolve
      setPendingConfirm(options)
    })
  }, [])

  const settleConfirm = useCallback((confirmed: boolean) => {
    pendingResolveRef.current?.(confirmed)
    pendingResolveRef.current = null
    setPendingConfirm(null)
  }, [])

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <ConfirmDialog
        open={Boolean(pendingConfirm)}
        title={pendingConfirm?.title ?? ''}
        description={pendingConfirm?.description}
        confirmLabel={pendingConfirm?.confirmLabel}
        cancelLabel={pendingConfirm?.cancelLabel}
        destructive={pendingConfirm?.destructive}
        onOpenChange={(open) => {
          if (!open) {
            settleConfirm(false)
          }
        }}
        onConfirm={() => settleConfirm(true)}
      />
    </ConfirmContext.Provider>
  )
}

export function useConfirm() {
  const confirm = useContext(ConfirmContext)
  if (!confirm) {
    throw new Error('useConfirm must be used within ConfirmProvider.')
  }
  return confirm
}
