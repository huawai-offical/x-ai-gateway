export function formatNumber(value?: number | null) {
  if (value == null || Number.isNaN(value)) {
    return '--'
  }
  return value.toLocaleString('zh-CN')
}
