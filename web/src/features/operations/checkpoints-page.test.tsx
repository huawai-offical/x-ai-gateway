// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { CheckpointsPage } from './checkpoints-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async () => [
    {
      id: 7,
      checkpointName: 'cp-7',
      changePlanId: 2,
      status: 'READY',
      verificationStatus: 'VERIFIED',
      verificationMessage: 'ok',
      metadataSnapshotPath: 'meta.json',
      runtimeSnapshotPath: 'runtime.json',
      dataSnapshotPath: 'data.json',
    },
  ]),
}))

describe('CheckpointsPage', () => {
  it('renders recovery checkpoints', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <CheckpointsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('恢复检查点')).toBeInTheDocument()
    expect(await screen.findByText('cp-7')).toBeInTheDocument()
    expect(await screen.findByText('校验：VERIFIED')).toBeInTheDocument()
  })
})
