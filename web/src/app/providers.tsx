import type { PropsWithChildren } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from 'next-themes'
import { ConfirmProvider } from '@/components/app/confirm-provider'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { queryClient } from './query-client'
import { AdminAuthProvider } from '@/features/auth/auth-provider'

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="dark"
      enableSystem={false}
      storageKey="x-ai-gateway:theme"
      disableTransitionOnChange
    >
      <QueryClientProvider client={queryClient}>
        <AdminAuthProvider>
          <TooltipProvider delayDuration={120}>
            <ConfirmProvider>{children}</ConfirmProvider>
            <Toaster />
          </TooltipProvider>
        </AdminAuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
