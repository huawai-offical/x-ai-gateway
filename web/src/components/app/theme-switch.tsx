import { useEffect, useState } from 'react'
import { MoonStarIcon, SunIcon } from 'lucide-react'
import { useTheme } from 'next-themes'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export function ThemeSwitch({ className }: { className?: string }) {
  const { resolvedTheme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  const activeTheme = mounted && resolvedTheme === 'light' ? 'light' : 'dark'

  return (
    <div
      className={cn(
        'inline-flex items-center gap-1 rounded-2xl border border-border/70 bg-background/85 p-1 shadow-sm backdrop-blur',
        className,
      )}
    >
      <Button
        type="button"
        variant={activeTheme === 'dark' ? 'secondary' : 'ghost'}
        size="sm"
        className="h-8 gap-1.5 rounded-xl px-3"
        aria-label="切换到深色主题"
        onClick={() => setTheme('dark')}
      >
        <MoonStarIcon data-icon="inline-start" />
        深色
      </Button>
      <Button
        type="button"
        variant={activeTheme === 'light' ? 'secondary' : 'ghost'}
        size="sm"
        className="h-8 gap-1.5 rounded-xl px-3"
        aria-label="切换到浅色主题"
        onClick={() => setTheme('light')}
      >
        <SunIcon data-icon="inline-start" />
        浅色
      </Button>
    </div>
  )
}
