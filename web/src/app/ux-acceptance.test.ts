import { describe, expect, it } from 'vitest'
import { highRiskInputRules, uxAcceptancePages, uxViewports, validateUxAcceptanceMatrix } from './ux-acceptance'

describe('ux acceptance matrix', () => {
  it('covers the core Portal and Console pages across desktop and mobile', () => {
    const criticalPages = uxAcceptancePages.filter((page) => page.critical)

    expect(criticalPages.length).toBeGreaterThanOrEqual(5)
    expect(uxViewports.map((viewport) => viewport.name)).toEqual(['desktop', 'mobile'])
    expect(criticalPages.some((page) => page.surface === 'portal')).toBe(true)
    expect(criticalPages.some((page) => page.surface === 'console')).toBe(true)
    expect(validateUxAcceptanceMatrix()).toEqual([])
  })

  it('keeps high-risk inputs away from bare id-only workflows', () => {
    const ruleIds = highRiskInputRules.map((rule) => rule.id)

    expect(ruleIds).toContain('resource-picker')
    expect(ruleIds).toContain('masked-secret')
    expect(ruleIds).toContain('field-array-validation')
    expect(ruleIds).toContain('danger-confirm')
    expect(ruleIds).toContain('mobile-table-overflow')
    expect(uxAcceptancePages.find((page) => page.id === 'console-codex-onboarding')?.highRiskInputs).toEqual(
      expect.arrayContaining(['resource-picker', 'masked-secret']),
    )
    expect(uxAcceptancePages.find((page) => page.id === 'console-codex-onboarding')?.path).toBe('/console/accounts/connect/codex')
    expect(uxAcceptancePages.find((page) => page.id === 'console-codex-observability')?.ownerTask).toBe('TASK-20260507-004')
    expect(uxAcceptancePages.find((page) => page.id === 'console-account-group-runtime')?.ownerTask).toBe('TASK-20260507-017')
  })
})
