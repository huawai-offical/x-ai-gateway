// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AppLayout } from './layout'

function renderLayout(initialEntry = '/workbench') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/workbench" element={<div>workbench</div>} />
          <Route path="/keys" element={<div>keys</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppLayout', () => {
  it('collapses the desktop sidebar into an icon rail from the sidebar control', () => {
    const { container } = renderLayout()

    expect(container.querySelector('.app-shell')).not.toHaveClass('menu-collapsed')
    expect(container.querySelector('.sidebar-brand .sidebar-rail-toggle')).toBeNull()
    expect(container.querySelector('.sidebar-rail-dock .sidebar-rail-toggle')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '收起侧边栏' }))

    expect(container.querySelector('.app-shell')).toHaveClass('menu-collapsed')
    expect(screen.getByRole('button', { name: '展开侧边栏' })).toBeInTheDocument()
    expect(screen.getByTitle('站点真相 / Workbench')).toHaveClass('active')
  })
})
