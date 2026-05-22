// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PaginatedRows } from './table-pagination'

describe('PaginatedRows', () => {
  it('defaults to 50 rows per page and supports page size changes', async () => {
    const rows = Array.from({ length: 75 }, (_, index) => `row-${index + 1}`)

    render(
      <PaginatedRows items={rows}>
        {({ pageItems }) => (
          <table>
            <tbody>
              {pageItems.map((row) => (
                <tr key={row}>
                  <td>{row}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </PaginatedRows>,
    )

    expect(screen.getByText('共 75 条，当前 1-50')).toBeInTheDocument()
    expect(screen.getByText('第 1 / 2 页')).toBeInTheDocument()
    expect(screen.getByText('row-50')).toBeInTheDocument()
    expect(screen.queryByText('row-51')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /下一页/ }))
    expect(screen.getByText('共 75 条，当前 51-75')).toBeInTheDocument()
    expect(screen.getByText('row-75')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('combobox'))
    const option = await screen.findByRole('option', { name: '100 条' })
    fireEvent.click(option)

    expect(screen.getByText('共 75 条，当前 1-75')).toBeInTheDocument()
    expect(screen.getByText('第 1 / 1 页')).toBeInTheDocument()
    expect(screen.getByText('row-75')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(75)
  })
})
