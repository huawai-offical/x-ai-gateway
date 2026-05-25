import { describe, expect, it } from 'vitest'
import { navigationGroups, resolveRouteMeta } from './navigation'

describe('navigation route meta', () => {
  it('keeps Codex import flow under account groups after runtime page retirement', () => {
    const codexConnect = resolveRouteMeta('/console/accounts/connect/codex')

    expect(codexConnect.groupLabel).toBe('接入与模型')
    expect(codexConnect.navTo).toBe('/console/account-groups')
    expect(codexConnect.navLabel).toBe('上游账号组/凭证池')
  })

  it('maps retained console routes to the updated information architecture', () => {
    expect(resolveRouteMeta('/console/request-logs').groupLabel).toBe('观测记录')
    expect(resolveRouteMeta('/console/models').groupLabel).toBe('接入与模型')
    expect(resolveRouteMeta('/console/ops/governance').groupLabel).toBe('路由治理')
    expect(resolveRouteMeta('/console/error-rules').navTo).toBe('/console/ops')
    expect(resolveRouteMeta('/console/operations/windows').groupLabel).toBe('智能运维')
    expect(resolveRouteMeta('/console/users').groupLabel).toBe('用户与计费')
  })

  it('does not expose retired proxy probe navigation entries', () => {
    const labels = navigationGroups.flatMap((group) => group.items.map((item) => item.label))
    const targets = navigationGroups.flatMap((group) => group.items.map((item) => item.to))

    expect(labels).not.toContain('拨测记录')
    expect(labels).not.toContain('网络拨测')
    expect(targets).not.toContain('/console/ops/probes')
    expect(targets).not.toContain('/console/network/probes')
  })
})
