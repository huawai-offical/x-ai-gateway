import { describe, expect, it } from 'vitest'
import { getRouteSurface, isConsoleRoute, isPortalRoute, matchRouteSurface, toConsolePath } from './route-surfaces'

describe('route surfaces', () => {
  it.each([
    ['/', 'public'],
    ['/docs', 'public'],
    ['/status', 'public'],
    ['/portal', 'portal'],
    ['/portal/keys', 'portal'],
    ['/portal/invitations', 'portal'],
    ['/portal/security', 'portal'],
    ['/portal/announcements/notice-1', 'portal'],
    ['/login', 'console'],
    ['/console', 'console'],
    ['/console/keys/key-1', 'console'],
    ['/keys/key-1', 'console'],
    ['/provider-reference-gap', 'console'],
    ['/operations/backups', 'console'],
    ['/integrations/webhooks', 'console'],
    ['/pricing', 'public'],
  ] as const)('maps %s to %s surface', (path, surface) => {
    expect(getRouteSurface(path)).toBe(surface)
  })

  it('keeps portal and console helpers mutually exclusive', () => {
    expect(isPortalRoute('/portal/redeem')).toBe(true)
    expect(isConsoleRoute('/portal/redeem')).toBe(false)
    expect(isConsoleRoute('/request-logs')).toBe(true)
    expect(isPortalRoute('/request-logs')).toBe(false)
  })

  it('exposes console canonical paths while marking legacy roots', () => {
    expect(matchRouteSurface('/workbench').legacy).toBe(true)
    expect(matchRouteSurface('/console/workbench').legacy).toBe(false)
    expect(toConsolePath('/workbench')).toBe('/console/workbench')
    expect(toConsolePath('/')).toBe('/console')
  })
})
