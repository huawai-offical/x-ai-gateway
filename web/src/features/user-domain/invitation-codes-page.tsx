import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SearchIcon, TriangleAlertIcon, WaypointsIcon } from 'lucide-react'
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
import { apiRequest, isApiError } from '@/lib/api'
import { formatInstant } from '@/lib/format'

type InvitationCode = {
  id: number
  code: string
  active: boolean
  maxUses: number
  usedCount: number
  expiresAt?: string | null
  ownerUserId?: number | null
  ownerEmail?: string | null
  ownerDisplayName?: string | null
  rewardTokenCredits: number
  referrerRewardTokenCredits: number
  rewardPlanId?: number | null
  rewardPlanName?: string | null
  rewardPlanDurationDays?: number | null
  rewardAccessGroupId?: number | null
  rewardAccessGroupName?: string | null
  rewardAccessGroupDurationDays?: number | null
  notes?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

type InvitationCodeUsage = {
  id: number
  invitationCodeId: number
  code: string
  userId: number
  registrationEmail: string
  registrationChannel: string
  requestSource?: string | null
  referrerUserId?: number | null
  referrerEmail?: string | null
  rewardTokenCredits: number
  referrerRewardTokenCredits: number
  rewardPlanId?: number | null
  rewardPlanName?: string | null
  rewardSubscriptionId?: number | null
  rewardAccessGroupId?: number | null
  rewardAccessGroupName?: string | null
  rewardAccessGroupGrantId?: number | null
  usedAt?: string | null
  createdAt?: string | null
}

type InvitationLeaderboardEntry = {
  userId: number
  email: string
  displayName?: string | null
  directInviteCount: number
  totalInviteCount: number
  referrerRewardTokenCredits: number
  latestInviteAt?: string | null
}

type InvitationTreeNode = {
  userId: number
  email: string
  displayName?: string | null
  depth: number
  invitedAt?: string | null
  children: InvitationTreeNode[]
}

type PlanOption = {
  id: number
  planName: string
  defaultDurationDays?: number | null
}

type AccessGroupOption = {
  id: number
  groupName: string
}

const SELECT_CLASS = 'h-10 rounded-md border border-input bg-background px-3 text-sm'

export function InvitationCodesPage() {
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [editingCode, setEditingCode] = useState<InvitationCode | null>(null)
  const [usageCode, setUsageCode] = useState<InvitationCode | null>(null)
  const [codesText, setCodesText] = useState('')
  const [generateCount, setGenerateCount] = useState('0')
  const [maxUses, setMaxUses] = useState('1')
  const [ownerUserId, setOwnerUserId] = useState('')
  const [rewardTokenCredits, setRewardTokenCredits] = useState('0')
  const [referrerRewardTokenCredits, setReferrerRewardTokenCredits] = useState('0')
  const [rewardPlanId, setRewardPlanId] = useState('')
  const [rewardPlanDurationDays, setRewardPlanDurationDays] = useState('')
  const [rewardAccessGroupId, setRewardAccessGroupId] = useState('')
  const [rewardAccessGroupDurationDays, setRewardAccessGroupDurationDays] = useState('')
  const [expiresAtLocal, setExpiresAtLocal] = useState('')
  const [active, setActive] = useState(true)
  const [notes, setNotes] = useState('')
  const [keyword, setKeyword] = useState('')
  const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'disabled'>('all')
  const [formError, setFormError] = useState<string | null>(null)
  const [treeRootUserId, setTreeRootUserId] = useState('')
  const [treeMaxDepth, setTreeMaxDepth] = useState('5')
  const [submittedTreeRootUserId, setSubmittedTreeRootUserId] = useState('')
  const [submittedTreeMaxDepth, setSubmittedTreeMaxDepth] = useState(5)
  const [treeFormError, setTreeFormError] = useState<string | null>(null)

  const codesQuery = useQuery({
    queryKey: ['user-domain', 'invitation-codes', keyword.trim(), activeFilter],
    queryFn: () => apiRequest<InvitationCode[]>(invitationCodeListPath(keyword, activeFilter)),
  })
  const usagesQuery = useQuery({
    queryKey: ['user-domain', 'invitation-code-usages', usageCode?.id],
    queryFn: () => apiRequest<InvitationCodeUsage[]>(`/admin/invitation-codes/${usageCode?.id}/usages`),
    enabled: usageCode != null,
  })
  const plansQuery = useQuery({
    queryKey: ['user-domain', 'plans', 'invitation-code-options'],
    queryFn: () => apiRequest<PlanOption[]>('/admin/plans?active=true'),
  })
  const accessGroupsQuery = useQuery({
    queryKey: ['user-domain', 'access-groups', 'invitation-code-options'],
    queryFn: () => apiRequest<AccessGroupOption[]>('/admin/access-groups?active=true'),
  })
  const leaderboardQuery = useQuery({
    queryKey: ['user-domain', 'invitation-codes', 'leaderboard'],
    queryFn: () => apiRequest<InvitationLeaderboardEntry[]>('/admin/invitation-codes/leaderboard?limit=20'),
  })
  const invitationTreeQuery = useQuery({
    queryKey: ['user-domain', 'invitation-codes', 'tree', submittedTreeRootUserId, submittedTreeMaxDepth],
    queryFn: () => apiRequest<InvitationTreeNode>(`/admin/invitation-codes/tree/${submittedTreeRootUserId}?maxDepth=${submittedTreeMaxDepth}`),
    enabled: submittedTreeRootUserId.trim().length > 0,
  })
  const createMutation = useMutation({
    mutationFn: () => apiRequest<InvitationCode[]>('/admin/invitation-codes', {
      method: 'POST',
      body: buildCreatePayload(),
    }),
    onSuccess: () => {
      setCreateOpen(false)
      resetForm()
      invalidateInvitationQueries(queryClient)
    },
  })
  const updateMutation = useMutation({
    mutationFn: () => {
      if (editingCode == null) throw new Error('请选择邀请码。')
      return apiRequest<InvitationCode>(`/admin/invitation-codes/${editingCode.id}`, {
        method: 'PUT',
        body: buildUpdatePayload(),
      })
    },
    onSuccess: () => {
      setEditingCode(null)
      resetForm()
      invalidateInvitationQueries(queryClient)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (codeId: number) => apiRequest<void>(`/admin/invitation-codes/${codeId}`, { method: 'DELETE' }),
    onSuccess: () => invalidateInvitationQueries(queryClient),
  })

  const codes = (codesQuery.data ?? []) as InvitationCode[]
  const usages = (usagesQuery.data ?? []) as InvitationCodeUsage[]
  const plans = (plansQuery.data ?? []) as PlanOption[]
  const accessGroups = (accessGroupsQuery.data ?? []) as AccessGroupOption[]
  const leaderboard = (leaderboardQuery.data ?? []) as InvitationLeaderboardEntry[]
  const invitationTree = invitationTreeQuery.data
  const mergedError = codesQuery.error ?? usagesQuery.error ?? createMutation.error ?? updateMutation.error ?? deleteMutation.error
  const optionError = plansQuery.error ?? accessGroupsQuery.error

  const openCreate = () => {
    resetForm()
    setCreateOpen(true)
  }

  const openEdit = (code: InvitationCode) => {
    setEditingCode(code)
    setActive(code.active)
    setMaxUses(String(code.maxUses))
    setOwnerUserId(code.ownerUserId == null ? '' : String(code.ownerUserId))
    setRewardTokenCredits(String(code.rewardTokenCredits ?? 0))
    setReferrerRewardTokenCredits(String(code.referrerRewardTokenCredits ?? 0))
    setRewardPlanId(code.rewardPlanId == null ? '' : String(code.rewardPlanId))
    setRewardPlanDurationDays(code.rewardPlanDurationDays == null ? '' : String(code.rewardPlanDurationDays))
    setRewardAccessGroupId(code.rewardAccessGroupId == null ? '' : String(code.rewardAccessGroupId))
    setRewardAccessGroupDurationDays(code.rewardAccessGroupDurationDays == null ? '' : String(code.rewardAccessGroupDurationDays))
    setExpiresAtLocal(toLocalDateTime(code.expiresAt))
    setNotes(code.notes ?? '')
  }

  const submitCreate = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      createMutation.mutate()
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '创建失败。')
    }
  }

  const submitUpdate = (event: FormEvent) => {
    event.preventDefault()
    try {
      setFormError(null)
      updateMutation.mutate()
    } catch (error) {
      setFormError(error instanceof Error ? error.message : '保存失败。')
    }
  }

  const submitTreeSearch = (event: FormEvent) => {
    event.preventDefault()
    searchTree(treeRootUserId, treeMaxDepth)
  }

  const openTreeFromLeaderboard = (item: InvitationLeaderboardEntry) => {
    searchTree(String(item.userId), treeMaxDepth)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageSection
        kicker="用户域"
        title="邀请码"
        actions={<Button type="button" onClick={openCreate}>创建邀请码</Button>}
      >
        {mergedError ? <InlineError error={mergedError} title="邀请码操作失败" /> : null}
        {optionError ? <InlineError error={optionError} title="奖励选项加载失败" /> : null}
        <div className="grid gap-3 pb-2 md:grid-cols-[1fr_180px]">
          <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索邀请码或备注" />
          <select
            className={SELECT_CLASS}
            value={activeFilter}
            onChange={(event) => setActiveFilter(event.target.value as 'all' | 'active' | 'disabled')}
          >
            <option value="all">全部状态</option>
            <option value="active">仅启用</option>
            <option value="disabled">仅停用</option>
          </select>
        </div>
        {codesQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : codes.length ? (
          <PaginatedRows items={codes}>
            {({ pageItems }) => (
              <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
                <table className="w-full min-w-[1080px] table-fixed text-sm">
                  <thead className="bg-muted/30">
                    <tr>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">邀请码</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">状态</th>
                      <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">使用</th>
                      <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">归属</th>
                      <th className="w-[24%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">奖励与赠送</th>
                      <th className="w-[12%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">过期</th>
                      <th className="w-[16%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((item) => (
                      <tr key={item.id} className="border-b border-border/40 align-top">
                        <td className="px-4 py-3">
                          <div className="font-mono text-xs text-foreground">{item.code}</div>
                          {item.notes ? <div className="mt-1 truncate text-xs text-muted-foreground">{item.notes}</div> : null}
                        </td>
                        <td className="px-4 py-3"><StatusBadge tone={item.active ? 'success' : 'warning'}>{item.active ? '启用' : '停用'}</StatusBadge></td>
                        <td className="px-4 py-3 text-muted-foreground">{item.usedCount}/{item.maxUses}</td>
                        <td className="truncate px-4 py-3 text-muted-foreground">{ownerLabel(item)}</td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <RewardSummary items={formatCodeRewardItems(item)} />
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.expiresAt) || '长期'}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button type="button" variant="outline" size="sm" onClick={() => openEdit(item)}>编辑</Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => setUsageCode(item)}>记录</Button>
                            <Button type="button" variant="outline" size="sm" onClick={() => deleteMutation.mutate(item.id)}>删除</Button>
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
          <EmptyState title="还没有邀请码" />
        )}
      </PageSection>

      <PageSection kicker="增长" title="邀请排行榜">
        {leaderboardQuery.isPending ? (
          <PageSkeleton count={1} />
        ) : leaderboardQuery.error ? (
          <InlineError error={leaderboardQuery.error} title="邀请排行榜加载失败" />
        ) : leaderboard.length ? (
          <div className="overflow-x-auto rounded-2xl border border-border/60 bg-card/92">
            <table className="w-full min-w-[860px] table-fixed text-sm">
              <thead className="bg-muted/30">
                <tr>
                  <th className="w-[26%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">用户</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">直接邀请</th>
                  <th className="w-[14%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">累计邀请</th>
                  <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">返佣 Token</th>
                  <th className="w-[18%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">最近邀请</th>
                  <th className="w-[10%] border-b border-border/60 px-4 py-3 text-left font-medium text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody>
                {leaderboard.map((item) => (
                  <tr key={item.userId} className="border-b border-border/40 align-top">
                    <td className="px-4 py-3">
                      <div className="font-medium text-foreground">{item.displayName || item.email || `#${item.userId}`}</div>
                      <div className="truncate text-xs text-muted-foreground">{item.email || `#${item.userId}`}</div>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.directInviteCount)}</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatNumber(item.totalInviteCount)}</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatTokenCredits(item.referrerRewardTokenCredits)}</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.latestInviteAt) || '暂无'}</td>
                    <td className="px-4 py-3">
                      <Button type="button" variant="outline" size="sm" onClick={() => openTreeFromLeaderboard(item)}>
                        <WaypointsIcon data-icon="inline-start" />
                        邀请树
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="还没有邀请排行数据" />
        )}
      </PageSection>

      <PageSection
        kicker="层级"
        title="邀请树"
      >
        <form className="grid gap-4 lg:grid-cols-[1fr_180px_auto]" onSubmit={submitTreeSearch}>
          <label className="grid gap-2 text-sm">
            <span className="font-medium text-foreground">根用户 ID</span>
            <Input
              inputMode="numeric"
              value={treeRootUserId}
              onChange={(event) => setTreeRootUserId(event.target.value)}
              placeholder="输入邀请人用户 ID"
            />
          </label>
          <label className="grid gap-2 text-sm">
            <span className="font-medium text-foreground">最大深度</span>
            <select className={SELECT_CLASS} value={treeMaxDepth} onChange={(event) => setTreeMaxDepth(event.target.value)}>
              <option value="1">1 层</option>
              <option value="2">2 层</option>
              <option value="3">3 层</option>
              <option value="5">5 层</option>
              <option value="10">10 层</option>
            </select>
          </label>
          <div className="flex items-end">
            <Button type="submit" className="w-full lg:w-auto">
              <SearchIcon data-icon="inline-start" />
              查询
            </Button>
          </div>
        </form>
        <div className="pt-4">
          {treeFormError ? (
            <InvitationTreeError title="邀请树查询失败" error={new Error(treeFormError)} />
          ) : !submittedTreeRootUserId ? (
            <EmptyState title="输入用户 ID 查看邀请层级" icon={<WaypointsIcon className="size-5" />} />
          ) : invitationTreeQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : invitationTreeQuery.error ? (
            <InvitationTreeError error={invitationTreeQuery.error} title="邀请树加载失败" />
          ) : invitationTree ? (
            <div className="rounded-2xl border border-border/60 bg-card/92 p-4">
              <InvitationTreeView node={invitationTree} maxDepth={submittedTreeMaxDepth} />
            </div>
          ) : (
            <EmptyState title="未找到邀请树" />
          )}
        </div>
      </PageSection>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-4xl">
          <DialogHeader><DialogTitle>创建邀请码</DialogTitle></DialogHeader>
          <form className="grid gap-4" onSubmit={submitCreate}>
            <Textarea rows={5} value={codesText} onChange={(event) => setCodesText(event.target.value)} placeholder="粘贴邀请码，每行一个" />
            <div className="grid gap-4 md:grid-cols-3">
              <LabeledInput label="自动生成数量" value={generateCount} onChange={setGenerateCount} inputMode="numeric" />
              <LabeledInput label="最大使用次数" value={maxUses} onChange={setMaxUses} inputMode="numeric" />
              <label className="grid gap-2 text-sm">
                <span className="font-medium text-foreground">过期时间</span>
                <Input type="datetime-local" value={expiresAtLocal} onChange={(event) => setExpiresAtLocal(event.target.value)} />
              </label>
            </div>
            <RewardFields
              ownerUserId={ownerUserId}
              setOwnerUserId={setOwnerUserId}
              rewardTokenCredits={rewardTokenCredits}
              setRewardTokenCredits={setRewardTokenCredits}
              referrerRewardTokenCredits={referrerRewardTokenCredits}
              setReferrerRewardTokenCredits={setReferrerRewardTokenCredits}
              rewardPlanId={rewardPlanId}
              setRewardPlanId={setRewardPlanId}
              rewardPlanDurationDays={rewardPlanDurationDays}
              setRewardPlanDurationDays={setRewardPlanDurationDays}
              rewardAccessGroupId={rewardAccessGroupId}
              setRewardAccessGroupId={setRewardAccessGroupId}
              rewardAccessGroupDurationDays={rewardAccessGroupDurationDays}
              setRewardAccessGroupDurationDays={setRewardAccessGroupDurationDays}
              plans={plans}
              accessGroups={accessGroups}
            />
            <Input value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="备注" />
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} />
              立即启用
            </label>
            {formError || createMutation.error ? <InlineError error={createMutation.error ?? new Error(formError ?? '创建失败')} title="邀请码创建失败" /> : null}
            <DialogFooter><Button type="submit" disabled={createMutation.isPending}>创建</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={editingCode != null} onOpenChange={(open) => !open && setEditingCode(null)}>
        <DialogContent className="max-w-4xl">
          <DialogHeader><DialogTitle>编辑邀请码</DialogTitle></DialogHeader>
          <form className="grid gap-4" onSubmit={submitUpdate}>
            <div className="rounded-xl bg-muted/30 px-3 py-2 font-mono text-xs">{editingCode?.code}</div>
            <div className="grid gap-4 md:grid-cols-2">
              <LabeledInput label="最大使用次数" value={maxUses} onChange={setMaxUses} inputMode="numeric" />
              <label className="grid gap-2 text-sm">
                <span className="font-medium text-foreground">过期时间</span>
                <Input type="datetime-local" value={expiresAtLocal} onChange={(event) => setExpiresAtLocal(event.target.value)} />
              </label>
            </div>
            <RewardFields
              ownerUserId={ownerUserId}
              setOwnerUserId={setOwnerUserId}
              rewardTokenCredits={rewardTokenCredits}
              setRewardTokenCredits={setRewardTokenCredits}
              referrerRewardTokenCredits={referrerRewardTokenCredits}
              setReferrerRewardTokenCredits={setReferrerRewardTokenCredits}
              rewardPlanId={rewardPlanId}
              setRewardPlanId={setRewardPlanId}
              rewardPlanDurationDays={rewardPlanDurationDays}
              setRewardPlanDurationDays={setRewardPlanDurationDays}
              rewardAccessGroupId={rewardAccessGroupId}
              setRewardAccessGroupId={setRewardAccessGroupId}
              rewardAccessGroupDurationDays={rewardAccessGroupDurationDays}
              setRewardAccessGroupDurationDays={setRewardAccessGroupDurationDays}
              plans={plans}
              accessGroups={accessGroups}
            />
            <Input value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="备注" />
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} />
              启用邀请码
            </label>
            {formError || updateMutation.error ? <InlineError error={updateMutation.error ?? new Error(formError ?? '保存失败')} title="邀请码保存失败" /> : null}
            <DialogFooter><Button type="submit" disabled={updateMutation.isPending}>保存</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={usageCode != null} onOpenChange={(open) => !open && setUsageCode(null)}>
        <DialogContent className="max-w-5xl">
          <DialogHeader><DialogTitle>使用记录：{usageCode?.code}</DialogTitle></DialogHeader>
          {usagesQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : usages.length ? (
            <div className="max-h-[420px] overflow-auto rounded-2xl border border-border/60">
              <table className="w-full min-w-[960px] table-fixed text-sm">
                <thead className="bg-muted/30">
                  <tr>
                    <th className="w-[24%] px-4 py-3 text-left font-medium text-muted-foreground">注册用户</th>
                    <th className="w-[18%] px-4 py-3 text-left font-medium text-muted-foreground">邀请人</th>
                    <th className="w-[26%] px-4 py-3 text-left font-medium text-muted-foreground">发放结果</th>
                    <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">渠道</th>
                    <th className="w-[16%] px-4 py-3 text-left font-medium text-muted-foreground">时间</th>
                  </tr>
                </thead>
                <tbody>
                  {usages.map((item: InvitationCodeUsage) => (
                    <tr key={item.id} className="border-t border-border/50 align-top">
                      <td className="px-4 py-3">
                        <div className="font-medium text-foreground">{item.registrationEmail}</div>
                        <div className="text-xs text-muted-foreground">#{item.userId}</div>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{usageReferrerLabel(item)}</td>
                      <td className="px-4 py-3 text-muted-foreground">
                        <RewardSummary items={formatUsageRewardItems(item)} />
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {item.registrationChannel}
                        {item.requestSource ? <div className="text-xs">{item.requestSource}</div> : null}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{formatInstant(item.usedAt) || '未知'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="还没有使用记录" />
          )}
        </DialogContent>
      </Dialog>
    </div>
  )

  function buildCreatePayload() {
    return {
      codes: codesText.split(/\r?\n/).map((line) => line.trim()).filter(Boolean),
      rawText: codesText,
      generateCount: parseOptionalNonNegativeInteger(generateCount, '自动生成数量') ?? 0,
      prefix: 'INV',
      ...buildUpdatePayload(),
    }
  }

  function buildUpdatePayload() {
    return {
      maxUses: parsePositiveInteger(maxUses, '最大使用次数'),
      active,
      expiresAt: toIsoFromLocal(expiresAtLocal),
      ownerUserId: parseOptionalPositiveInteger(ownerUserId, '归属用户 ID'),
      rewardTokenCredits: parseOptionalNonNegativeInteger(rewardTokenCredits, '注册人奖励 Token') ?? 0,
      referrerRewardTokenCredits: parseOptionalNonNegativeInteger(referrerRewardTokenCredits, '邀请人返佣 Token') ?? 0,
      rewardPlanId: parseSelectId(rewardPlanId),
      rewardPlanDurationDays: parseOptionalPositiveInteger(rewardPlanDurationDays, '赠送套餐天数'),
      rewardAccessGroupId: parseSelectId(rewardAccessGroupId),
      rewardAccessGroupDurationDays: parseOptionalPositiveInteger(rewardAccessGroupDurationDays, '赠送权益/授权组天数'),
      notes: notes.trim() || null,
    }
  }

  function resetForm() {
    setCodesText('')
    setGenerateCount('0')
    setMaxUses('1')
    setOwnerUserId('')
    setRewardTokenCredits('0')
    setReferrerRewardTokenCredits('0')
    setRewardPlanId('')
    setRewardPlanDurationDays('')
    setRewardAccessGroupId('')
    setRewardAccessGroupDurationDays('')
    setExpiresAtLocal('')
    setActive(true)
    setNotes('')
    setFormError(null)
  }

  function searchTree(rootUserIdValue: string, maxDepthValue: string) {
    try {
      const parsedRootUserId = parsePositiveInteger(rootUserIdValue, '根用户 ID')
      const parsedMaxDepth = parsePositiveInteger(maxDepthValue, '最大深度')
      setTreeFormError(null)
      setTreeRootUserId(String(parsedRootUserId))
      setTreeMaxDepth(String(Math.min(parsedMaxDepth, 10)))
      setSubmittedTreeRootUserId(String(parsedRootUserId))
      setSubmittedTreeMaxDepth(Math.min(parsedMaxDepth, 10))
    } catch (error) {
      setTreeFormError(error instanceof Error ? error.message : '邀请树查询条件不合法。')
    }
  }
}

function RewardFields({
  ownerUserId,
  setOwnerUserId,
  rewardTokenCredits,
  setRewardTokenCredits,
  referrerRewardTokenCredits,
  setReferrerRewardTokenCredits,
  rewardPlanId,
  setRewardPlanId,
  rewardPlanDurationDays,
  setRewardPlanDurationDays,
  rewardAccessGroupId,
  setRewardAccessGroupId,
  rewardAccessGroupDurationDays,
  setRewardAccessGroupDurationDays,
  plans,
  accessGroups,
}: {
  ownerUserId: string
  setOwnerUserId: (value: string) => void
  rewardTokenCredits: string
  setRewardTokenCredits: (value: string) => void
  referrerRewardTokenCredits: string
  setReferrerRewardTokenCredits: (value: string) => void
  rewardPlanId: string
  setRewardPlanId: (value: string) => void
  rewardPlanDurationDays: string
  setRewardPlanDurationDays: (value: string) => void
  rewardAccessGroupId: string
  setRewardAccessGroupId: (value: string) => void
  rewardAccessGroupDurationDays: string
  setRewardAccessGroupDurationDays: (value: string) => void
  plans: PlanOption[]
  accessGroups: AccessGroupOption[]
}) {
  return (
    <div className="grid gap-4 rounded-2xl border border-border/60 bg-muted/10 p-4">
      <div className="grid gap-4 md:grid-cols-3">
        <LabeledInput label="归属用户 ID" value={ownerUserId} onChange={setOwnerUserId} inputMode="numeric" />
        <LabeledInput label="注册人奖励 Token" value={rewardTokenCredits} onChange={setRewardTokenCredits} inputMode="numeric" />
        <LabeledInput label="邀请人返佣 Token" value={referrerRewardTokenCredits} onChange={setReferrerRewardTokenCredits} inputMode="numeric" />
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <label className="grid gap-2 text-sm">
          <span className="font-medium text-foreground">赠送套餐</span>
          <select className={SELECT_CLASS} value={rewardPlanId} onChange={(event) => setRewardPlanId(event.target.value)}>
            <option value="">不赠送套餐</option>
            {plans.map((plan) => <option key={plan.id} value={String(plan.id)}>{plan.planName}</option>)}
          </select>
        </label>
        <LabeledInput label="套餐天数" value={rewardPlanDurationDays} onChange={setRewardPlanDurationDays} inputMode="numeric" />
        <label className="grid gap-2 text-sm">
          <span className="font-medium text-foreground">赠送权益/授权组</span>
          <select className={SELECT_CLASS} value={rewardAccessGroupId} onChange={(event) => setRewardAccessGroupId(event.target.value)}>
            <option value="">不赠送权益/授权组</option>
            {accessGroups.map((group) => <option key={group.id} value={String(group.id)}>{group.groupName}</option>)}
          </select>
        </label>
        <LabeledInput label="权益/授权组天数" value={rewardAccessGroupDurationDays} onChange={setRewardAccessGroupDurationDays} inputMode="numeric" />
      </div>
    </div>
  )
}

function LabeledInput({
  label,
  value,
  onChange,
  inputMode,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  inputMode?: 'numeric'
}) {
  return (
    <label className="grid gap-2 text-sm">
      <span className="font-medium text-foreground">{label}</span>
      <Input inputMode={inputMode} value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  )
}

function RewardSummary({ items }: { items: string[] }) {
  return (
    <div className="grid gap-1">
      {items.map((item, index) => (
        <div key={`${item}-${index}`} className={item === '无' ? '' : 'text-foreground'}>{item}</div>
      ))}
    </div>
  )
}

function InvitationTreeView({ node, maxDepth }: { node: InvitationTreeNode; maxDepth: number }) {
  const totalDescendants = countTreeDescendants(node)
  return (
    <div className="grid gap-3">
      <div className="grid gap-1 border-b border-border/50 pb-3">
        <div className="text-sm font-medium text-foreground">
          {treeNodeLabel(node)}
        </div>
        <div className="text-xs text-muted-foreground">
          累计下级 {formatNumber(totalDescendants)} 人，当前查询深度 {formatNumber(maxDepth)} 层
        </div>
      </div>
      {node.children.length ? (
        <div className="grid gap-2">
          <TreeChildren nodes={node.children} />
        </div>
      ) : (
        <EmptyState title="该用户暂无下级邀请" />
      )}
    </div>
  )
}

function InvitationTreeError({ title, error }: { title: string; error: unknown }) {
  const detail = errorDetail(error)
  return (
    <div role="alert" className="grid gap-2 rounded-2xl border border-destructive/30 bg-destructive/10 p-4 text-sm">
      <InlineError error={error} title={title} />
      <div className="flex items-start gap-3">
        <TriangleAlertIcon className="mt-0.5 size-5 shrink-0 text-destructive" />
        <div className="grid gap-1">
          <div className="font-medium text-foreground">{title}</div>
          <div className="text-muted-foreground">{detail.message}</div>
          {detail.traceId ? <div className="font-mono text-xs text-muted-foreground">traceId: {detail.traceId}</div> : null}
        </div>
      </div>
    </div>
  )
}

function TreeChildren({ nodes }: { nodes: InvitationTreeNode[] }) {
  return (
    <div className="grid gap-2">
      {nodes.map((node) => (
        <TreeNodeCard key={node.userId} node={node} />
      ))}
    </div>
  )
}

function TreeNodeCard({ node }: { node: InvitationTreeNode }) {
  return (
    <div className="grid gap-2 rounded-xl border border-border/55 bg-muted/10 p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="break-words text-sm font-medium text-foreground">{treeNodeLabel(node)}</div>
          <div className="mt-1 text-xs text-muted-foreground">
            第 {formatNumber(node.depth)} 层 · 邀请时间 {formatInstant(node.invitedAt) || '未知'}
          </div>
        </div>
        <StatusBadge tone={node.children.length ? 'info' : 'neutral'}>
          下级 {formatNumber(node.children.length)}
        </StatusBadge>
      </div>
      {node.children.length ? (
        <div className="ml-4 grid gap-2 border-l border-border/60 pl-3">
          <TreeChildren nodes={node.children} />
        </div>
      ) : null}
    </div>
  )
}

function ownerLabel(item: InvitationCode) {
  if (item.ownerEmail) return item.ownerDisplayName ? `${item.ownerDisplayName} / ${item.ownerEmail}` : item.ownerEmail
  if (item.ownerUserId != null) return `#${item.ownerUserId}`
  return '无'
}

function treeNodeLabel(node: InvitationTreeNode) {
  const name = node.displayName || node.email || `#${node.userId}`
  return node.email && node.displayName ? `${name} / ${node.email} / #${node.userId}` : `${name} / #${node.userId}`
}

function countTreeDescendants(node: InvitationTreeNode): number {
  return node.children.reduce((total, child) => total + 1 + countTreeDescendants(child), 0)
}

function errorDetail(error: unknown) {
  if (isApiError(error)) {
    return { message: error.message, traceId: error.traceId ?? null }
  }
  if (error instanceof Error && error.message.trim()) {
    return { message: error.message, traceId: errorTraceId(error) }
  }
  return { message: '发生未知错误。', traceId: null }
}

function errorTraceId(error: Error) {
  if (!('traceId' in error)) {
    return null
  }
  const traceId = error.traceId
  return typeof traceId === 'string' && traceId.trim() ? traceId : null
}

function usageReferrerLabel(item: InvitationCodeUsage) {
  if (item.referrerEmail) return item.referrerUserId == null ? item.referrerEmail : `${item.referrerEmail} / #${item.referrerUserId}`
  if (item.referrerUserId != null) return `#${item.referrerUserId}`
  return '无'
}

function formatCodeRewardItems(item: InvitationCode) {
  const rewards = [
    item.rewardTokenCredits > 0 ? `注册奖励 ${formatTokenCredits(item.rewardTokenCredits)}` : null,
    item.referrerRewardTokenCredits > 0 ? `邀请人返佣 ${formatTokenCredits(item.referrerRewardTokenCredits)}` : null,
    item.rewardPlanId != null ? `套餐 ${item.rewardPlanName ?? `#${item.rewardPlanId}`}${formatDurationSuffix(item.rewardPlanDurationDays)}` : null,
    item.rewardAccessGroupId != null ? `权益/授权组 ${item.rewardAccessGroupName ?? `#${item.rewardAccessGroupId}`}${formatDurationSuffix(item.rewardAccessGroupDurationDays)}` : null,
  ].filter((value): value is string => Boolean(value))
  return rewards.length ? rewards : ['无']
}

function formatUsageRewardItems(item: InvitationCodeUsage) {
  const rewards = [
    item.rewardTokenCredits > 0 ? `注册奖励 ${formatTokenCredits(item.rewardTokenCredits)}` : null,
    item.referrerRewardTokenCredits > 0 ? `邀请人返佣 ${formatTokenCredits(item.referrerRewardTokenCredits)}` : null,
    item.rewardPlanId != null ? `套餐 ${item.rewardPlanName ?? `#${item.rewardPlanId}`}${item.rewardSubscriptionId != null ? ` / 订阅 #${item.rewardSubscriptionId}` : ''}` : null,
    item.rewardAccessGroupId != null ? `权益/授权组 ${item.rewardAccessGroupName ?? `#${item.rewardAccessGroupId}`}${item.rewardAccessGroupGrantId != null ? ` / 授权 #${item.rewardAccessGroupGrantId}` : ''}` : null,
  ].filter((value): value is string => Boolean(value))
  return rewards.length ? rewards : ['无']
}

function formatTokenCredits(value?: number | null) {
  return value && value > 0 ? value.toLocaleString('zh-CN') : '无'
}

function formatNumber(value?: number | null) {
  return value == null ? '0' : Math.max(0, value).toLocaleString('zh-CN')
}

function formatDurationSuffix(value?: number | null) {
  return value && value > 0 ? ` / ${value.toLocaleString('zh-CN')} 天` : ''
}

function parseSelectId(value: string) {
  return value.trim() ? parseOptionalPositiveInteger(value, '下拉选项') : null
}

function parsePositiveInteger(value: string, fieldName: string) {
  const parsed = parseOptionalPositiveInteger(value, fieldName)
  if (parsed == null) {
    throw new Error(`${fieldName}不能为空。`)
  }
  return parsed
}

function parseOptionalPositiveInteger(value: string, fieldName: string) {
  if (!value.trim()) return null
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${fieldName}必须是正整数。`)
  }
  return Math.trunc(parsed)
}

function parseOptionalNonNegativeInteger(value: string, fieldName: string) {
  if (!value.trim()) return null
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${fieldName}必须是非负整数。`)
  }
  return Math.trunc(parsed)
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

function invitationCodeListPath(keyword: string, activeFilter: 'all' | 'active' | 'disabled') {
  const params = new URLSearchParams()
  if (keyword.trim()) params.set('keyword', keyword.trim())
  if (activeFilter === 'active') params.set('active', 'true')
  if (activeFilter === 'disabled') params.set('active', 'false')
  const query = params.toString()
  return query ? `/admin/invitation-codes?${query}` : '/admin/invitation-codes'
}

function invalidateInvitationQueries(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['user-domain', 'invitation-codes'] })
  queryClient.invalidateQueries({ queryKey: ['user-domain', 'invitation-code-usages'] })
}
