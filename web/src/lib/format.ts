export function formatInstant(value?: string | null) {
  if (!value) return '无'

  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    timeZone: 'Asia/Shanghai',
  })
}
