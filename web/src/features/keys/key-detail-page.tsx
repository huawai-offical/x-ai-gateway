import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { apiRequest } from '../../lib/api'

type DistributedKey = {
  id: number
  keyName: string
  description?: string | null
  active: boolean
  allowedProtocols: string[]
  allowedModels: string[]
  allowedProviderTypes: string[]
  allowedClientFamilies: string[]
  requireClientFamilyMatch: boolean
  budgetLimitMicros?: number | null
  budgetWindowSeconds?: number | null
  rpmLimit?: number | null
  tpmLimit?: number | null
  concurrencyLimit?: number | null
}

export function KeyDetailPage() {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const keysQuery = useQuery({
    queryKey: ['distributed-keys'],
    queryFn: () => apiRequest<DistributedKey[]>('/admin/distributed-keys'),
  })

  const current = useMemo(
    () => keysQuery.data?.find((item: DistributedKey) => String(item.id) === id),
    [id, keysQuery.data],
  )

  const [draft, setDraft] = useState<DistributedKey | null>(null)

  if (current && draft?.id !== current.id) {
    setDraft(current)
  }

  const updateMutation = useMutation({
    mutationFn: async () => {
      if (!draft) return
      return apiRequest(`/admin/distributed-keys/${draft.id}`, {
        method: 'PUT',
        body: JSON.stringify(draft),
      })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['distributed-keys'] }),
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    updateMutation.mutate()
  }

  if (!draft) {
    return <section className="panel"><p className="empty-state">未找到指定 key。</p></section>
  }

  return (
    <section className="page-grid">
      <form className="panel panel-wide stacked-form" onSubmit={handleSubmit}>
        <div className="panel-head">
          <p className="panel-kicker">Key policy</p>
          <h2>{draft.keyName}</h2>
        </div>

        <label>
          <span>名称</span>
          <input value={draft.keyName} onChange={(e) => setDraft({ ...draft, keyName: e.target.value })} />
        </label>

        <label>
          <span>允许 provider</span>
          <input
            value={draft.allowedProviderTypes.join(',')}
            onChange={(e) => setDraft({ ...draft, allowedProviderTypes: splitCsv(e.target.value) })}
            placeholder="OPENAI_DIRECT,GEMINI_DIRECT"
          />
        </label>

        <div className="form-grid">
          <label>
            <span>预算 micros</span>
            <input
              type="number"
              value={draft.budgetLimitMicros ?? ''}
              onChange={(e) => setDraft({ ...draft, budgetLimitMicros: toNullableNumber(e.target.value) })}
            />
          </label>
          <label>
            <span>预算窗口秒</span>
            <input
              type="number"
              value={draft.budgetWindowSeconds ?? ''}
              onChange={(e) => setDraft({ ...draft, budgetWindowSeconds: toNullableNumber(e.target.value) })}
            />
          </label>
          <label>
            <span>RPM</span>
            <input type="number" value={draft.rpmLimit ?? ''} onChange={(e) => setDraft({ ...draft, rpmLimit: toNullableNumber(e.target.value) })} />
          </label>
          <label>
            <span>TPM</span>
            <input type="number" value={draft.tpmLimit ?? ''} onChange={(e) => setDraft({ ...draft, tpmLimit: toNullableNumber(e.target.value) })} />
          </label>
          <label>
            <span>并发</span>
            <input
              type="number"
              value={draft.concurrencyLimit ?? ''}
              onChange={(e) => setDraft({ ...draft, concurrencyLimit: toNullableNumber(e.target.value) })}
            />
          </label>
        </div>

        <label>
          <span>允许客户端家族</span>
          <input
            value={draft.allowedClientFamilies.join(',')}
            onChange={(e) => setDraft({ ...draft, allowedClientFamilies: splitCsv(e.target.value) })}
            placeholder="GENERIC_OPENAI,CODEX"
          />
        </label>

        <label className="checkbox-line">
          <input
            type="checkbox"
            checked={draft.requireClientFamilyMatch}
            onChange={(e) => setDraft({ ...draft, requireClientFamilyMatch: e.target.checked })}
          />
          <span>强制客户端家族匹配</span>
        </label>

        <button type="submit" disabled={updateMutation.isPending}>
          保存策略
        </button>
      </form>
    </section>
  )
}

function splitCsv(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function toNullableNumber(value: string) {
  if (!value.trim()) return null
  return Number(value)
}
