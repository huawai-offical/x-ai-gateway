import { describe, expect, it } from 'vitest'
import { messageKeys, messages, translate, type Locale } from './index'

const locales = Object.keys(messages) as Locale[]

describe('i18n messages', () => {
  it('只保留 zh-CN 语言包', () => {
    expect(locales).toEqual(['zh-CN'])
  })

  it('covers navigation, common, and portal namespaces', () => {
    const keys = messageKeys('zh-CN')

    expect(keys.some((key) => key.startsWith('nav.'))).toBe(true)
    expect(keys.some((key) => key.startsWith('common.'))).toBe(true)
    expect(keys.some((key) => key.startsWith('portal.'))).toBe(true)
  })

  it('locale 省略时默认回落到 zh-CN', () => {
    expect(translate('common.actions.refresh')).toBe('刷新')
    expect(translate('common.actions.refresh', 'zh-CN')).toBe('刷新')
  })
})
