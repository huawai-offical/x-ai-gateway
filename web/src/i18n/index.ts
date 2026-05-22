import { defaultLocale, messages, type Locale, type MessageKey } from './messages'

export function isSupportedLocale(value: string | null | undefined): value is Locale {
  return value === 'zh-CN'
}

export function translate(key: MessageKey, locale: Locale = defaultLocale): string {
  return messages[locale][key] ?? messages[defaultLocale][key] ?? key
}

export function messageKeys(locale: Locale = defaultLocale): MessageKey[] {
  return Object.keys(messages[locale]).sort() as MessageKey[]
}

export { defaultLocale, messages }
export type { Locale, MessageKey }
