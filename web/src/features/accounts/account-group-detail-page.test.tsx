// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AccountGroupDetailPage } from './account-group-detail-page'

const { apiRequestMock } = vi.hoisted(() => ({
  apiRequestMock: vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/admin/account-groups/1' && init?.method === 'PUT') {
      return {
        id: 1,
        groupName: 'Codex Group',
        providerType: 'CODEX_OAUTH',
        supportedModels: ['gpt-5.4@low', 'gpt-5.3-codex'],
        supportedProtocols: ['openai', 'responses'],
        allowedClientFamilies: ['CODEX', 'GENERIC_OPENAI'],
        description: 'updated',
        defaultGroup: false,
        active: true,
      }
    }
    if (url === '/admin/account-groups/1/status?active=false' && init?.method === 'POST') {
      return {
        id: 1,
        groupName: 'Codex Group',
        providerType: 'CODEX_OAUTH',
        supportedModels: ['gpt-5.4@low'],
        supportedProtocols: ['openai', 'responses'],
        allowedClientFamilies: ['CODEX'],
        active: false,
      }
    }
    if (url === '/admin/account-groups/1' && init?.method === 'DELETE') {
      return null
    }
    if (url === '/admin/account-groups/1') {
      return {
        id: 1,
        groupName: 'Codex Group',
        providerType: 'CODEX_OAUTH',
        supportedModels: ['gpt-5.4@low'],
        supportedProtocols: ['openai', 'responses'],
        allowedClientFamilies: ['CODEX'],
        description: 'Codex runtime group',
        defaultGroup: false,
        active: true,
        createdAt: '2026-05-20T01:00:00Z',
        updatedAt: '2026-05-21T01:00:00Z',
      }
    }
    if (url === '/admin/account-groups/2') {
      return {
        id: 2,
        groupName: 'OpenAI OAuth Group',
        providerType: 'OPENAI_OAUTH',
        supportedModels: ['gpt-4o'],
        supportedProtocols: ['openai'],
        allowedClientFamilies: ['GENERIC_OPENAI'],
      }
    }
    if (url === '/admin/accounts/group/1') {
      return [
        {
          id: 7,
          accountName: 'codex-real-test',
          providerType: 'CODEX_OAUTH',
          supportedModels: ['gpt-5.4@low'],
          healthy: true,
          frozen: false,
          refreshStatus: 'READY',
          refreshFailureCount: 0,
          quotaRemainingTokens: 120000,
          quotaRemainingRequests: 120,
          totalRequestCount: 20,
          requestSuccessRate: 0.95,
          cacheHitRate: 0.35,
          lastRefreshAt: '2026-05-07T02:00:00Z',
        },
        {
          id: 8,
          accountName: 'codex-cooldown',
          providerType: 'CODEX_OAUTH',
          supportedModels: ['gpt-5.4@low'],
          healthy: false,
          frozen: true,
          refreshStatus: 'FAILED',
          refreshFailureCount: 2,
          cooldownUntil: '2026-05-07T03:00:00Z',
          lastErrorMessage: 'upstream timeout after quota refresh',
        },
        {
          id: 9,
          accountName: 'codex-policy-blocked',
          providerType: 'CODEX_OAUTH',
          supportedModels: ['gpt-5.4@low'],
          healthy: false,
          frozen: false,
          refreshStatus: 'FAILED',
          refreshFailureCount: 1,
          lastErrorMessage: 'permission denied Bearer abcdefghijklmnopqrstuvwxyz',
        },
      ]
    }
    if (url === '/admin/accounts/group/2') {
      return []
    }
    if (url === '/admin/accounts/7') {
      return {
        id: 7,
        groupId: 1,
        accountName: 'codex-real-test',
        providerType: 'CODEX_OAUTH',
        externalAccountId: 'codex:user-7',
        active: true,
        frozen: false,
        healthy: true,
        lastRefreshAt: '2026-05-07T02:00:00Z',
      }
    }
    if (url === '/admin/credentials/group/1') {
      return [
        {
          id: 21,
          credentialName: 'MiMo OpenAI-compatible key',
          providerType: 'OPENAI_COMPATIBLE',
          active: true,
        },
      ]
    }
    if (url === '/admin/credentials/group/2') {
      return []
    }
    if (url === '/admin/distributed-keys') {
      return [
        {
          id: 5,
          keyName: 'Codex access key',
          keyPrefix: 'xag_codex',
          maskedKey: 'xag_codex_****',
          active: true,
          allowedProtocolSuites: ['xiaomi_mimo.openai_compatible'],
          allowedProviderTypes: ['OPENAI_COMPATIBLE'],
          allowedClientFamilies: ['CODEX'],
        },
      ]
    }
    if (typeof url === 'string' && url.startsWith('/admin/account-groups/model-catalog')) {
      return ['gpt-5.3-codex', 'gpt-5.4@low']
    }
    if (url === '/admin/accounts/import-auth-json' && init?.method === 'POST') {
      return { id: 3, accountName: 'imported-openai', healthy: true, frozen: false }
    }
    if (url === '/admin/accounts/official/import' && init?.method === 'POST') {
      return {
        accountId: 7,
        accountName: 'codex-real-test',
        externalAccountId: 'codex:email:abc123',
        quotaStatus: 'READY',
        refreshStatus: 'QUOTA_READY',
        routeEligible: true,
        lastRefreshResultJson: '{"status":"refreshed","trigger":"import"}',
      }
    }
    if (url === '/admin/accounts/7/freeze?frozen=true' && init?.method === 'POST') {
      return { id: 7, accountName: 'codex-real-test', healthy: true, frozen: true }
    }
    if (url === '/admin/accounts/7/runtime-reset' && init?.method === 'POST') {
      return { id: 7, accountName: 'codex-real-test', healthy: true, frozen: false, refreshStatus: 'READY' }
    }
    if (url === '/admin/accounts/7/refresh-models' && init?.method === 'POST') {
      return { accountId: 7, modelCount: 2, sampleModels: ['gpt-5.4@low', 'gpt-5.3-codex'] }
    }
    if (url === '/admin/accounts/7/official/quota-refresh' && init?.method === 'POST') {
      return { accountId: 7, quotaStatus: 'READY', quotaRemainingTokens: 120000 }
    }
    if (url === '/admin/accounts/7/official/codex/responses-smoke' && init?.method === 'POST') {
      return { accountId: 7, status: 'OK', model: 'gpt-5.4@low', dryRun: true, routeEligible: true }
    }
    if (url === '/admin/account-groups/1/codex-runtime/batch-recovery-preflight' && init?.method === 'POST') {
      return {
        operation: 'codex-runtime-recovery',
        generatedAt: '2026-05-08T01:00:00Z',
        dryRunOnly: true,
        executed: false,
        refreshQuota: false,
        totals: { total: 3, safe: 1, blocked: 1, alreadyReady: 1, executed: 0, failed: 0, skipped: 0 },
        items: [
          {
            accountId: 8,
            accountName: 'codex-cooldown',
            category: 'safe',
            status: '已隔离',
            reason: '账号已隔离；健康状态异常',
            recommendedAction: '可按批量恢复策略重置运行态。',
            errorSummary: 'upstream timeout after quota refresh',
            executionStatus: 'PREFLIGHT',
          },
          {
            accountId: 9,
            accountName: 'codex-policy-blocked',
            category: 'blocked',
            status: '异常',
            reason: '最近错误包含权限、策略、安全或禁用语义，批量恢复前需要人工复核。',
            recommendedAction: '人工核验账号授权、组织策略和 auth.json 来源后再单独处理。',
            errorSummary: 'permission denied Bearer ***',
            executionStatus: 'PREFLIGHT',
          },
        ],
        auditEventId: 91,
        auditEventTitle: 'Codex Runtime 批量恢复预检',
      }
    }
    if (url === '/admin/account-groups/1/codex-runtime/batch-recovery' && init?.method === 'POST') {
      return {
        operation: 'codex-runtime-recovery',
        generatedAt: '2026-05-08T01:01:00Z',
        dryRunOnly: false,
        executed: true,
        refreshQuota: false,
        totals: { total: 3, safe: 1, blocked: 1, alreadyReady: 1, executed: 1, failed: 0, skipped: 2 },
        items: [
          {
            accountId: 8,
            accountName: 'codex-cooldown',
            category: 'safe',
            status: '可路由',
            reason: '账号已隔离；健康状态异常',
            recommendedAction: '已重置运行态。',
            errorSummary: 'upstream timeout after quota refresh',
            executionStatus: 'EXECUTED',
          },
          {
            accountId: 9,
            accountName: 'codex-policy-blocked',
            category: 'blocked',
            status: '异常',
            reason: '最近错误包含权限、策略、安全或禁用语义，批量恢复前需要人工复核。',
            recommendedAction: '人工核验账号授权、组织策略和 auth.json 来源后再单独处理。',
            errorSummary: 'permission denied Bearer ***',
            executionStatus: 'SKIPPED',
          },
        ],
        auditEventId: 92,
        auditEventTitle: 'Codex Runtime 批量恢复执行',
      }
    }
    return []
  }),
}))

vi.mock('../../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../lib/api')>()
  return {
    ...actual,
    apiRequest: apiRequestMock,
  }
})

afterEach(() => {
  cleanup()
  apiRequestMock.mockClear()
})

describe('AccountGroupDetailPage', () => {
  it('renders group detail heading', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/1']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Codex Group')).toBeInTheDocument()
    expect(await screen.findByText('支持协议')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '编辑账号分组' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '停用账号分组' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '删除账号分组' })).toBeInTheDocument()
  })

  it('supports editing group on the unified detail page', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/1']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Codex Group')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '编辑账号分组' }))
    const editDialog = await screen.findByRole('dialog', { name: '编辑账号分组' })

    fireEvent.click(within(editDialog).getByRole('button', { name: /允许客户端/ }))
    fireEvent.click(within(editDialog).getByRole('checkbox', { name: 'GENERIC_OPENAI' }))

    fireEvent.click(within(editDialog).getByRole('button', { name: '选择模型' }))
    const modelDialog = await screen.findByRole('dialog', { name: '支持模型选择' })
    fireEvent.change(within(modelDialog).getByPlaceholderText('输入关键字，例如 gpt / gemini / claude'), { target: { value: '5.3' } })
    fireEvent.click(within(modelDialog).getByRole('checkbox', { name: 'gpt-5.3-codex' }))
    fireEvent.click(within(modelDialog).getByRole('button', { name: '完成' }))

    fireEvent.click(within(editDialog).getByRole('button', { name: '保存' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/account-groups/1',
        expect.objectContaining({ method: 'PUT', body: expect.any(String) }),
      )
    })
    const updateCall = apiRequestMock.mock.calls.find(
      ([url, init]) => url === '/admin/account-groups/1' && init?.method === 'PUT',
    )
    const payload = JSON.parse(String(updateCall?.[1]?.body))
    expect(payload.allowedClientFamilies).toContain('GENERIC_OPENAI')
    expect(payload.supportedModels).toContain('gpt-5.3-codex')
  })

  it('supports status toggle and delete from the unified detail page', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/1']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
            <Route path="/console/account-groups" element={<div>账号分组列表页</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Codex Group')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '停用账号分组' }))
    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/account-groups/1/status?active=false',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    fireEvent.click(screen.getByRole('button', { name: '删除账号分组' }))
    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/account-groups/1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
    expect(await screen.findByText('账号分组列表页')).toBeInTheDocument()
    confirmSpy.mockRestore()
  })

  it('imports Codex auth.json through the official account API and shows a sanitized result', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/1']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Codex Group')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '导入 auth.json 接入' }))

    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    fireEvent.change(screen.getByRole('textbox', { name: '账号名称' }), { target: { value: 'imported-openai' } })
    fireEvent.change(screen.getByRole('textbox', { name: '访问令牌' }), { target: { value: 'sk-import-token' } })
    fireEvent.click(screen.getByRole('button', { name: '导入账号' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/accounts/official/import',
        expect.objectContaining({
          method: 'POST',
        }),
      )
    })
    const officialCall = apiRequestMock.mock.calls.find(([url]) => url === '/admin/accounts/official/import')
    expect(JSON.parse(String(officialCall?.[1]?.body))).toEqual(expect.objectContaining({
      accountType: 'CODEX',
      groupId: 1,
      refreshQuotaAfterImport: true,
    }))
    expect(await screen.findByText('auth.json 导入结果已由后端脱敏保存')).toBeInTheDocument()
    expect(screen.getByText('codex:email:abc123')).toBeInTheDocument()
    expect(screen.getByText('refreshed / import')).toBeInTheDocument()
  })

  it('keeps non-Codex auth.json imports on the generic endpoint', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/2']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('OpenAI OAuth Group')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '导入 auth.json 接入' }))
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    fireEvent.change(screen.getByRole('textbox', { name: '账号名称' }), { target: { value: 'imported-openai' } })
    fireEvent.change(screen.getByRole('textbox', { name: '访问令牌' }), { target: { value: 'sk-import-token' } })
    fireEvent.click(screen.getByRole('button', { name: '导入账号' }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/accounts/import-auth-json',
        expect.objectContaining({
          method: 'POST',
        }),
      )
    })
  })

  it('binds account groups with a distributed key picker', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/1']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Codex Group')).toBeInTheDocument()
    expect(await screen.findByRole('combobox', { name: '分布式 Key' })).toHaveTextContent('Codex access key')
    expect(screen.getByRole('combobox', { name: '运行时 provider' })).toHaveDisplayValue('OPENAI_COMPATIBLE')
    fireEvent.change(screen.getByRole('combobox', { name: '分布式 Key' }), { target: { value: '5' } })
    fireEvent.submit(screen.getByRole('button', { name: '绑定到访问密钥' }).closest('form')!)
    await waitFor(() => {
      const bindCall = apiRequestMock.mock.calls.find(([url, init]) =>
        url === '/admin/account-groups/1/bindings' && init?.method === 'POST')
      expect(JSON.parse(String(bindCall?.[1]?.body))).toMatchObject({
        distributedKeyId: 5,
        providerType: 'OPENAI_COMPATIBLE',
      })
    })
    expect(screen.queryByText(/避免手写裸 ID 绑定错误/)).not.toBeInTheDocument()
  })

  it('exposes Codex runtime recovery actions', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/1']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('热切换、负载均衡与失败恢复')).toBeInTheDocument()
    expect((await screen.findAllByText('codex-real-test')).length).toBeGreaterThan(0)

    fireEvent.click(screen.getAllByRole('button', { name: '查看' })[0])
    const detailDialog = await screen.findByRole('dialog', { name: 'OAuth 账号详情' })
    fireEvent.click(await screen.findByRole('button', { name: /隔离账号/ }))
    fireEvent.click(await screen.findByRole('button', { name: /重置运行态/ }))
    fireEvent.click(await screen.findByRole('button', { name: /刷新模型/ }))
    fireEvent.click(await screen.findByRole('button', { name: /刷新 quota/ }))
    fireEvent.click(await screen.findByRole('button', { name: /dry-run 验证/ }))
    expect(detailDialog).toBeInTheDocument()

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/accounts/7/freeze?frozen=true', expect.objectContaining({ method: 'POST' }))
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/accounts/7/runtime-reset', expect.objectContaining({ method: 'POST' }))
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/accounts/7/refresh-models', expect.objectContaining({ method: 'POST' }))
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/accounts/7/official/quota-refresh', expect.objectContaining({ method: 'POST' }))
      expect(apiRequestMock).toHaveBeenCalledWith('/admin/accounts/7/official/codex/responses-smoke', expect.objectContaining({ method: 'POST' }))
    })
  })

  it('generates a dry-run batch recovery preflight with blocked candidates', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/account-groups/1']}>
          <Routes>
            <Route path="/account-groups/:id" element={<AccountGroupDetailPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('热切换、负载均衡与失败恢复')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /批量恢复预检/ }))

    expect(screen.getByText('Codex 批量恢复预检')).toBeInTheDocument()
    expect(await screen.findByText('可恢复候选')).toBeInTheDocument()
    expect(screen.getByText('阻断候选')).toBeInTheDocument()
    expect(screen.getAllByText('codex-policy-blocked').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/权限、策略、安全或禁用语义/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Bearer \*\*\*/).length).toBeGreaterThan(0)
    expect(screen.getByText(/dryRunOnly/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /查看审计事件/ })).toHaveAttribute(
      'href',
      '/console/ops/system-events?eventType=CODEX_RUNTIME_BATCH_RECOVERY&entityRef=account-group%3A1',
    )

    fireEvent.click(screen.getByRole('button', { name: /执行批量恢复/ }))

    await waitFor(() => {
      expect(apiRequestMock).toHaveBeenCalledWith(
        '/admin/account-groups/1/codex-runtime/batch-recovery',
        expect.objectContaining({ method: 'POST' }),
      )
    })
    expect(await screen.findByText('Codex 批量恢复结果')).toBeInTheDocument()
    expect(screen.getByText('EXECUTED')).toBeInTheDocument()
  })
})
