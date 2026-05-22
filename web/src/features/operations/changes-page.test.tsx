// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ChangesPage } from './changes-page'

vi.mock('../../lib/api', () => ({
  apiRequest: vi.fn(async (url: string) => {
    if (url === '/admin/operations/change-plans') {
      return [
        {
          id: 1,
          planName: 'upgrade-main',
          planType: 'UPGRADE',
          executionClass: 'MANUAL',
          status: 'PENDING_APPROVAL',
          releaseArtifactId: 8,
          recoveryCheckpointId: 3,
          maintenanceWindowId: 2,
          requestedBy: 'ops',
          approvedBy: null,
          manualOverride: false,
          preflightChecks: [],
          approvals: [],
          rolloutStages: [],
          rollbackPlaybook: null,
        },
      ]
    }
    if (url === '/admin/operations/maintenance-windows') {
      return [{ id: 2, windowName: '夜间窗口', activeNow: true }]
    }
    if (url === '/admin/operations/recovery-checkpoints') {
      return [{ id: 3, checkpointName: 'cp-3', verificationStatus: 'VERIFIED' }]
    }
    if (url === '/admin/operations/release-artifacts') {
      return [{ id: 8, versionName: 'v2026.04.18', artifactRef: 'registry/app:v2026.04.18', active: true }]
    }
    if (url === '/admin/integrations/deliveries?entityType=CHANGE_PLAN') {
      return [
        {
          id: 51,
          eventId: 'evt-51',
          eventType: 'UPGRADE_STARTED',
          channelId: 3,
          entityType: 'CHANGE_PLAN',
          entityRef: 'upgrade-main',
          deliveryStatus: 'SUCCEEDED',
          attemptCount: 1,
          payloadJson: '{"summary":"upgrade started"}',
          occurredAt: '2026-04-18T01:00:00Z',
          responseSummary: 'accepted',
        },
      ]
    }
    return []
  }),
}))

describe('ChangesPage', () => {
  it('renders unified change plan workspace', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <ChangesPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('统一变更编排')).toBeInTheDocument()
    expect(await screen.findByText('变更计划列表')).toBeInTheDocument()
    expect(await screen.findByText('变更外发状态')).toBeInTheDocument()
    expect(await screen.findByText('upgrade-main')).toBeInTheDocument()
    expect(await screen.findByText('accepted')).toBeInTheDocument()
    expect(await screen.findByText('需要审批')).toBeInTheDocument()
  })
})
