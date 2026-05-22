import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const DEFAULT_PAGE_SIZE = 50
const DEFAULT_PAGE_SIZE_OPTIONS = [25, 50, 100, 200]

type TablePaginationState<T> = {
  pageItems: T[]
  page: number
  pageCount: number
  pageSize: number
  total: number
  start: number
  end: number
  setPage: (page: number) => void
  setPageSize: (pageSize: number) => void
}

type PaginatedRowsProps<T> = {
  items: T[]
  children: (state: TablePaginationState<T>) => ReactNode
  defaultPageSize?: number
  pageSizeOptions?: number[]
  itemLabel?: string
}

export function PaginatedRows<T>({
  items,
  children,
  defaultPageSize = DEFAULT_PAGE_SIZE,
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  itemLabel = '条',
}: PaginatedRowsProps<T>) {
  const pagination = useTablePagination(items, {
    defaultPageSize,
    pageSizeOptions,
  })

  return (
    <div className="flex flex-col gap-3">
      {children(pagination)}
      <TablePagination state={pagination} pageSizeOptions={pageSizeOptions} itemLabel={itemLabel} />
    </div>
  )
}

export function useTablePagination<T>(
  items: T[],
  options: {
    defaultPageSize?: number
    pageSizeOptions?: number[]
  } = {},
): TablePaginationState<T> {
  const initialPageSize = options.defaultPageSize ?? DEFAULT_PAGE_SIZE
  const [pageSize, setPageSizeState] = useState(initialPageSize)
  const [page, setPageState] = useState(1)
  const total = items.length
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const safePage = Math.min(page, pageCount)
  const startIndex = (safePage - 1) * pageSize
  const endIndex = Math.min(total, startIndex + pageSize)
  const pageItems = useMemo(
    () => items.slice(startIndex, endIndex),
    [endIndex, items, startIndex],
  )

  useEffect(() => {
    if (page !== safePage) {
      setPageState(safePage)
    }
  }, [page, safePage])

  const setPage = (nextPage: number) => {
    setPageState(Math.min(Math.max(1, nextPage), pageCount))
  }

  const setPageSize = (nextPageSize: number) => {
    if (!Number.isFinite(nextPageSize) || nextPageSize <= 0) {
      return
    }
    setPageSizeState(nextPageSize)
    setPageState(1)
  }

  return {
    pageItems,
    page: safePage,
    pageCount,
    pageSize,
    total,
    start: total === 0 ? 0 : startIndex + 1,
    end: endIndex,
    setPage,
    setPageSize,
  }
}

function TablePagination<T>({
  state,
  pageSizeOptions,
  itemLabel,
}: {
  state: TablePaginationState<T>
  pageSizeOptions: number[]
  itemLabel: string
}) {
  const options = useMemo(
    () => Array.from(new Set([...pageSizeOptions, state.pageSize])).sort((a, b) => a - b),
    [pageSizeOptions, state.pageSize],
  )

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border/60 bg-muted/20 px-3 py-2 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-wrap items-center gap-2">
        <span>
          共 {state.total} {itemLabel}
          {state.total > 0 ? `，当前 ${state.start}-${state.end}` : ''}
        </span>
        <span className="text-muted-foreground/70">第 {state.page} / {state.pageCount} 页</span>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <span>每页</span>
        <Select value={String(state.pageSize)} onValueChange={(value) => state.setPageSize(Number(value))}>
          <SelectTrigger size="sm" className="w-24">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {options.map((option) => (
              <SelectItem key={option} value={String(option)}>
                {option} 条
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => state.setPage(state.page - 1)}
          disabled={state.page <= 1}
        >
          <ChevronLeftIcon data-icon="inline-start" />
          上一页
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => state.setPage(state.page + 1)}
          disabled={state.page >= state.pageCount}
        >
          下一页
          <ChevronRightIcon data-icon="inline-end" />
        </Button>
      </div>
    </div>
  )
}
