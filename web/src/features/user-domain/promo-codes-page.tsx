import { type FormEvent, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSection } from '@/components/app/page-section'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { PaginatedRows } from '@/components/app/table-pagination'
import { apiRequest } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type Campaign = {
  id: number
  campaignName: string
  description?: string | null
  active: boolean
  rewardTokenCredits: number
  maxRedemptionsPerUser: number
  startsAt?: string | null
  expiresAt?: string | null
  updatedAt?: string | null
}

type RedeemCode = {
  id: number
  campaignId: number
  campaignName: string
  code: string
  active: boolean
  maxUses: number
  usedCount: number
  expiresAt?: string | null
}

type CampaignForm = {
  campaignName: string
  description: string
  active: boolean
  rewardTokenCredits: string
  maxRedemptionsPerUser: string
  expiresAtLocal: string
}

export function PromoCodesPage() {
  const queryClient = useQueryClient()
  const [campaignOpen, setCampaignOpen] = useState(false)
  const [codeOpen, setCodeOpen] = useState(false)
  const [selectedCampaignId, setSelectedCampaignId] = useState<number | null>(null)
  const [editingCampaignId, setEditingCampaignId] = useState<number | null>(null)
  const [campaignForm, setCampaignForm] = useState<CampaignForm>(emptyCampaignForm())
  const [codesText, setCodesText] = useState('')
  const [generateCount, setGenerateCount] = useState('0')
  const [codeMaxUses, setCodeMaxUses] = useState('1')
  const [codeExpiresAtLocal, setCodeExpiresAtLocal] = useState('')
  const [codeActive, setCodeActive] = useState(true)
  const [editingCode, setEditingCode] = useState<RedeemCode | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const campaignsQuery = useQuery({
    queryKey: ['user-domain', 'promo-campaigns'],
    queryFn: () => apiRequest<Campaign[]>('/admin/promo-codes'),
  })
  const codesQuery = useQuery({
    queryKey: ['user-domain', 'promo-codes', selectedCampaignId],
    queryFn: () => apiRequest<RedeemCode[]>(`/admin/promo-codes/${selectedCampaignId}/codes`),
    enabled: selectedCampaignId != null,
  })
  const saveCampaignMutation = useMutation({
    mutationFn: () => {
      const payload = buildCampaignPayload(campaignForm)
      if (editingCampaignId == null) {
        return apiRequest<Campaign>('/admin/promo-codes', { method: 'POST', body: payload })
      }
      return apiRequest<Campaign>(`/admin/promo-codes/${editingCampaignId}`, { method: 'PUT', body: payload })
    },
    onSuccess: () => {
      setCampaignOpen(false)
      setEditingCampaignId(null)
      setCampaignForm(emptyCampaignForm())
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'promo-campaigns'] })
    },
  })
  const deleteCampaignMutation = useMutation({
    mutationFn: (campaignId: number) => apiRequest<void>(`/admin/promo-codes/${campaignId}`, { method: 'DELETE' }),
    onSuccess: () => {
      setSelectedCampaignId(null)
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'promo-campaigns'] })
    },
  })
  const createCodesMutation = useMutation({
    mutationFn: () => {
      if (selectedCampaignId == null) throw new Error('请先选择活动。')
      return apiRequest<RedeemCode[]>(`/admin/promo-codes/${selectedCampaignId}/codes`, {
        method: 'POST',
        body: {
          codes: codesText.split(/\r?\n/).map((line) => line.trim()).filter(Boolean),
          rawText: codesText,
          generateCount: Number(generateCount) || 0,
          prefix: 'XAG',
          maxUses: Number(codeMaxUses) || 1,
          active: codeActive,
          expiresAt: toIsoFromLocal(codeExpiresAtLocal),
        },
      })
    },
    onSuccess: () => {
      setCodeOpen(false)
      resetCodeForm()
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'promo-codes'] })
    },
  })
  const updateCodeMutation = useMutation({
    mutationFn: () => {
      if (selectedCampaignId == null || editingCode == null) throw new Error('请选择兑换码。')
      return apiRequest<RedeemCode>(`/admin/promo-codes/${selectedCampaignId}/codes/${editingCode.id}`, {
        method: 'PUT',
        body: {
          active: codeActive,
          maxUses: Number(codeMaxUses) || 1,
          expiresAt: toIsoFromLocal(codeExpiresAtLocal),
        },
      })
    },
    onSuccess: () => {
      setEditingCode(null)
      resetCodeForm()
      queryClient.invalidateQueries({ queryKey: ['user-domain', 'promo-codes'] })
    },
  })
  const deleteCodeMutation = useMutation({
    mutationFn: (codeId: number) => {
      if (selectedCampaignId == null) throw new Error('请先选择活动。')
      return apiRequest<void>(`/admin/promo-codes/${selectedCampaignId}/codes/${codeId}`, { method: 'DELETE' })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user-domain', 'promo-codes'] }),
  })

  const campaigns = useMemo<Campaign[]>(() => campaignsQuery.data ?? [], [campaignsQuery.data])
  const codes = (codesQuery.data ?? []) as RedeemCode[]
  const selectedCampaign = campaigns.find((item) => item.id === selectedCampaignId) ?? campaigns[0]

  const openCodes = (campaign: Campaign) => {
    setSelectedCampaignId(campaign.id)
    setCodeOpen(true)
  }

  const openCampaignCreate = () => {
    setEditingCampaignId(null)
    setCampaignForm(emptyCampaignForm())
    setCampaignOpen(true)
  }

  const openCampaignEdit = (campaign: Campaign) => {
    setEditingCampaignId(campaign.id)
    setCampaignForm({
      campaignName: campaign.campaignName,
      description: campaign.description ?? '',
      active: campaign.active,
      rewardTokenCredits: String(campaign.rewardTokenCredits),
      maxRedemptionsPerUser: String(campaign.maxRedemptionsPerUser),
      expiresAtLocal: toLocalDateTime(campaign.expiresAt),
    })
    setCampaignOpen(true)
  }

  const openEditCode = (code: RedeemCode) => {
    setEditingCode(code)
    setCodeActive(code.active)
    setCodeMaxUses(String(code.maxUses))
    setCodeExpiresAtLocal(toLocalDateTime(code.expiresAt))
  }

  const saveCampaign = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      saveCampaignMutation.mutate()
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '保存失败。')
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="用户域"
        title="兑换码活动"
        actions={<Button type="button" onClick={openCampaignCreate}>创建活动</Button>}
      >
        {saveCampaignMutation.error || deleteCampaignMutation.error || createCodesMutation.error || updateCodeMutation.error || deleteCodeMutation.error ? (
          <InlineError error={saveCampaignMutation.error ?? deleteCampaignMutation.error ?? createCodesMutation.error ?? updateCodeMutation.error ?? deleteCodeMutation.error} title="兑换码操作失败" />
        ) : null}

        {campaignsQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : campaignsQuery.error ? (
          <InlineError error={campaignsQuery.error} title="活动列表加载失败" />
        ) : campaigns.length ? (
          <PaginatedRows items={campaigns}>
            {({ pageItems }) => (
              <div className="overflow-hidden rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">活动</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                  <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">奖励</th>
                  <th className="w-[20%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">有效期</th>
                  <th className="w-[22%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageItems.map((item) => (
                  <tr key={item.id} className="border-b border-border/40 align-top">
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{item.campaignName}</div>
                      <div className="truncate text-xs text-muted-foreground">{item.description ?? '无描述'}</div>
                    </td>
                    <td className="px-4 py-3"><StatusBadge tone={item.active ? 'success' : 'warning'}>{item.active ? '启用' : '停用'}</StatusBadge></td>
                    <td className="px-4 py-3 text-muted-foreground">{item.rewardTokenCredits.toLocaleString('zh-CN')} Token</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.expiresAt) || '长期'}</td>
                    <td className="px-4 py-3">
                      <Button type="button" variant="outline" size="sm" onClick={() => openCodes(item)}>管理兑换码</Button>
                      <Button type="button" variant="outline" size="sm" onClick={() => openCampaignEdit(item)}>编辑</Button>
                      <Button type="button" variant="outline" size="sm" onClick={() => deleteCampaignMutation.mutate(item.id)}>删除</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
            )}
          </PaginatedRows>
        ) : (
          <EmptyState title="还没有兑换活动" />
        )}
      </PageSection>

      <Dialog open={campaignOpen} onOpenChange={setCampaignOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader><DialogTitle>{editingCampaignId == null ? '创建兑换活动' : '编辑兑换活动'}</DialogTitle></DialogHeader>
          <form className="grid gap-4" onSubmit={saveCampaign}>
            <Input value={campaignForm.campaignName} onChange={(event) => setCampaignForm((current) => ({ ...current, campaignName: event.target.value }))} placeholder="活动名称" />
            <Textarea rows={3} value={campaignForm.description} onChange={(event) => setCampaignForm((current) => ({ ...current, description: event.target.value }))} placeholder="活动描述" />
            <div className="grid gap-4 md:grid-cols-3">
              <Input value={campaignForm.rewardTokenCredits} onChange={(event) => setCampaignForm((current) => ({ ...current, rewardTokenCredits: event.target.value }))} placeholder="奖励 Token" />
              <Input value={campaignForm.maxRedemptionsPerUser} onChange={(event) => setCampaignForm((current) => ({ ...current, maxRedemptionsPerUser: event.target.value }))} placeholder="每用户上限" />
              <Input type="datetime-local" value={campaignForm.expiresAtLocal} onChange={(event) => setCampaignForm((current) => ({ ...current, expiresAtLocal: event.target.value }))} />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={campaignForm.active} onChange={(event) => setCampaignForm((current) => ({ ...current, active: event.target.checked }))} />
              立即启用
            </label>
            {formError || saveCampaignMutation.error ? <InlineError error={saveCampaignMutation.error ?? new Error(formError ?? '保存失败')} title="活动保存失败" /> : null}
            <DialogFooter><Button type="submit">{editingCampaignId == null ? '创建活动' : '保存活动'}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={codeOpen} onOpenChange={setCodeOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader><DialogTitle>管理兑换码：{selectedCampaign?.campaignName ?? '未选择活动'}</DialogTitle></DialogHeader>
          <div className="grid gap-4 md:grid-cols-[1fr_120px_120px_180px_auto]">
            <Textarea rows={4} value={codesText} onChange={(event) => setCodesText(event.target.value)} placeholder="粘贴兑换码，每行一个" />
            <Input value={generateCount} onChange={(event) => setGenerateCount(event.target.value)} placeholder="自动生成数量" />
            <Input value={codeMaxUses} onChange={(event) => setCodeMaxUses(event.target.value)} placeholder="可用次数" />
            <Input type="datetime-local" value={codeExpiresAtLocal} onChange={(event) => setCodeExpiresAtLocal(event.target.value)} />
            <Button type="button" onClick={() => createCodesMutation.mutate()} disabled={createCodesMutation.isPending}>导入/生成</Button>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={codeActive} onChange={(event) => setCodeActive(event.target.checked)} />
            新兑换码默认启用
          </label>
          {codesQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : codes.length ? (
            <PaginatedRows items={codes}>
              {({ pageItems }) => (
                <div className="max-h-[360px] overflow-auto rounded-2xl border border-border/60">
                  <table className="w-full table-fixed text-sm">
                    <thead className="bg-muted/30">
                      <tr>
                        <th className="w-[34%] px-4 py-3 text-left font-medium text-muted-foreground">兑换码</th>
                        <th className="w-[14%] px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                        <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">使用</th>
                        <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">过期</th>
                        <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pageItems.map((item) => (
                        <tr key={item.id} className="border-t border-border/50">
                          <td className="px-4 py-3 font-mono text-xs">{item.code}</td>
                          <td className="px-4 py-3"><StatusBadge tone={item.active ? 'success' : 'warning'}>{item.active ? '启用' : '停用'}</StatusBadge></td>
                          <td className="px-4 py-3 text-muted-foreground">{item.usedCount}/{item.maxUses}</td>
                          <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.expiresAt) || '长期'}</td>
                          <td className="px-4 py-3">
                            <div className="flex flex-wrap gap-2">
                              <Button type="button" variant="outline" size="sm" onClick={() => openEditCode(item)}>编辑</Button>
                              <Button type="button" variant="outline" size="sm" onClick={() => deleteCodeMutation.mutate(item.id)}>删除</Button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </PaginatedRows>
          ) : (
            <EmptyState title="还没有兑换码" />
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={editingCode != null} onOpenChange={(open) => !open && setEditingCode(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader><DialogTitle>编辑兑换码</DialogTitle></DialogHeader>
          <div className="grid gap-4">
            <div className="rounded-xl bg-muted/30 px-3 py-2 font-mono text-xs">{editingCode?.code}</div>
            <Input value={codeMaxUses} onChange={(event) => setCodeMaxUses(event.target.value)} placeholder="最大使用次数" />
            <Input type="datetime-local" value={codeExpiresAtLocal} onChange={(event) => setCodeExpiresAtLocal(event.target.value)} />
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={codeActive} onChange={(event) => setCodeActive(event.target.checked)} />
              启用兑换码
            </label>
          </div>
          <DialogFooter>
            <Button type="button" onClick={() => updateCodeMutation.mutate()} disabled={updateCodeMutation.isPending}>保存兑换码</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )

  function resetCodeForm() {
    setCodesText('')
    setGenerateCount('0')
    setCodeMaxUses('1')
    setCodeExpiresAtLocal('')
    setCodeActive(true)
  }
}

function emptyCampaignForm(): CampaignForm {
  return {
    campaignName: '',
    description: '',
    active: true,
    rewardTokenCredits: '100000',
    maxRedemptionsPerUser: '1',
    expiresAtLocal: '',
  }
}

function buildCampaignPayload(form: CampaignForm) {
  if (!form.campaignName.trim()) throw new Error('活动名称不能为空。')
  return {
    campaignName: form.campaignName.trim(),
    description: form.description.trim() || null,
    active: form.active,
    rewardTokenCredits: Number(form.rewardTokenCredits) || 0,
    maxRedemptionsPerUser: Number(form.maxRedemptionsPerUser) || 1,
    expiresAt: toIsoFromLocal(form.expiresAtLocal),
  }
}

function toIsoFromLocal(value: string) {
  if (!value.trim()) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) throw new Error('时间格式不合法。')
  return date.toISOString()
}

function toLocalDateTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const offsetMs = date.getTimezoneOffset() * 60 * 1000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}
