import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/app/empty-state'
import { InlineError } from '@/components/app/inline-error'
import { PageSkeleton } from '@/components/app/page-skeleton'
import { StatusBadge } from '@/components/app/status-badge'
import { formatInstant } from '@/lib/format'
import { getPortalSession, listPortalAnnouncements, markPortalAnnouncementRead } from './api'
import { PortalFrame } from './portal-shell'
import type { PortalAnnouncement } from './types'

export function PortalAnnouncementDetailPage() {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const sessionQuery = useQuery({ queryKey: ['portal', 'session'], queryFn: getPortalSession })
  const announcementsQuery = useQuery({
    queryKey: ['portal', 'announcements'],
    queryFn: listPortalAnnouncements,
    enabled: Boolean(sessionQuery.data?.authenticated),
  })
  const markReadMutation = useMutation({
    mutationFn: markPortalAnnouncementRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['portal', 'announcements'] }),
  })

  if (sessionQuery.isPending) {
    return <PortalFrame><PageSkeleton count={2} /></PortalFrame>
  }
  if (sessionQuery.error) {
    return <PortalFrame><InlineError error={sessionQuery.error} title="门户会话加载失败" /></PortalFrame>
  }
  if (!sessionQuery.data?.authenticated) {
    return <Navigate to="/portal/login" replace />
  }

  const announcements = (announcementsQuery.data ?? []) as PortalAnnouncement[]
  const announcement = announcements.find((item) => item.id === id)

  return (
    <PortalFrame>
      <Card className="border-border bg-card/95 shadow-lg">
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium text-primary">公告</p>
              <CardTitle className="mt-2 text-3xl">公告详情</CardTitle>
            </div>
            <Button type="button" variant="outline" asChild>
              <Link to="/portal">返回概览</Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {announcementsQuery.isPending ? (
            <PageSkeleton count={1} />
          ) : announcementsQuery.error ? (
            <InlineError error={announcementsQuery.error} title="公告加载失败" />
          ) : announcement ? (
            <article className="space-y-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h1 className="text-2xl font-semibold tracking-tight text-foreground">{announcement.title}</h1>
                  <p className="mt-2 text-sm text-muted-foreground">{formatInstant(announcement.publishedAt)}</p>
                </div>
                <StatusBadge tone={announcement.read ? 'info' : 'warning'}>{announcement.read ? '已读' : '未读'}</StatusBadge>
              </div>
              <p className="rounded-2xl border border-primary/20 bg-primary/10 px-4 py-3 text-sm text-foreground">{announcement.summary}</p>
              <div className="whitespace-pre-wrap rounded-2xl border border-border/60 bg-card px-5 py-4 text-sm leading-7 text-foreground">
                {announcement.body || '暂无正文。'}
              </div>
              {!announcement.read ? (
                <Button type="button" onClick={() => markReadMutation.mutate(announcement.id)} disabled={markReadMutation.isPending}>
                  标记已读
                </Button>
              ) : null}
              {markReadMutation.error ? <InlineError error={markReadMutation.error} title="标记已读失败" /> : null}
            </article>
          ) : (
            <EmptyState title="未找到该公告，或者当前用户无权查看。" />
          )}
        </CardContent>
      </Card>
    </PortalFrame>
  )
}
