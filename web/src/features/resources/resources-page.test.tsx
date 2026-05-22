// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../lib/api'
import { ResourcesPage } from './resources-page'

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      get: vi.fn(),
    },
  }
})

const mockedApiGet = apiClient.get as unknown as ReturnType<typeof vi.fn>

beforeEach(() => {
  mockedApiGet.mockImplementation(async (url: string) => {
    if (url === '/admin/resources/async') {
      return [
        {
          resourceKey: 'ftjob_1',
          resourceType: 'TUNING',
          normalizedStatus: 'succeeded',
          terminal: true,
          objectMode: 'upstream_object_with_local_lineage',
          eventCount: 3,
          updatedAt: '2026-05-01T01:00:00Z',
        },
      ]
    }
    if (url === '/admin/resources/async/ftjob_1') {
      return {
        lifecycle: { normalizedStatus: 'succeeded', terminal: true },
        lineage: {
          summary: {
            resource_type: 'tuning',
            resource_id: 'ftjob_1',
            status: 'succeeded',
            node_count: 8,
            edge_count: 7,
          },
          nodes: [],
          edges: [],
        },
        artifacts: [],
      }
    }
    throw new Error(`unexpected get url: ${url}`)
  })
})

afterEach(() => {
  mockedApiGet.mockReset()
})

describe('ResourcesPage', () => {
  it('在详情弹窗结构化展示谱系摘要', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ResourcesPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('ftjob_1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '详情' }))

    expect(await screen.findByText('谱系节点 / 边')).toBeInTheDocument()
    expect(await screen.findByText('8 / 7')).toBeInTheDocument()
    expect(await screen.findByText('生命周期状态')).toBeInTheDocument()
  })
})
