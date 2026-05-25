package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.InvitationLeaderboardEntryResponse;
import com.prodigalgal.xaigateway.admin.api.InvitationTreeNodeResponse;
import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeUsageEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationRelationshipEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeUsageRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationRelationshipRepository;
import com.prodigalgal.xaigateway.portal.api.PortalInvitationLeaderboardEntryResponse;
import com.prodigalgal.xaigateway.portal.api.PortalInvitationSummaryResponse;
import com.prodigalgal.xaigateway.portal.api.PortalInvitationUserResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InvitationGrowthService {

    private final InvitationRelationshipRepository relationshipRepository;
    private final InvitationCodeUsageRepository usageRepository;
    private final GatewayUserRepository gatewayUserRepository;

    public InvitationGrowthService(
            InvitationRelationshipRepository relationshipRepository,
            InvitationCodeUsageRepository usageRepository,
            GatewayUserRepository gatewayUserRepository) {
        this.relationshipRepository = relationshipRepository;
        this.usageRepository = usageRepository;
        this.gatewayUserRepository = gatewayUserRepository;
    }

    public List<InvitationLeaderboardEntryResponse> leaderboard(int limit) {
        return leaderboardEntries(limit).stream()
                .map(entry -> new InvitationLeaderboardEntryResponse(
                        entry.user().getId(),
                        entry.user().getEmail(),
                        entry.user().getDisplayName(),
                        entry.directInviteCount(),
                        entry.totalInviteCount(),
                        entry.referrerRewardTokenCredits(),
                        entry.latestInviteAt()
                ))
                .toList();
    }

    public InvitationTreeNodeResponse tree(Long userId, int maxDepth) {
        GatewayUserEntity root = gatewayUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定用户。"));
        Map<Long, List<InvitationRelationshipEntity>> children = childrenByReferrer();
        return toTreeNode(root, null, 0, Math.max(1, Math.min(maxDepth, 10)), children, new HashSet<>());
    }

    public PortalInvitationSummaryResponse portalSummary(GatewayUserEntity user) {
        List<InvitationRelationshipEntity> direct = relationshipRepository.findAllByReferrerUser_IdOrderByCreatedAtDesc(user.getId());
        List<InvitationCodeUsageEntity> usages = usageRepository.findAllByReferrerUser_IdOrderByUsedAtDesc(user.getId());
        long referrerReward = usages.stream().mapToLong(InvitationCodeUsageEntity::getReferrerRewardTokenCredits).sum();
        long totalInvites = descendantUserIds(user.getId()).size();
        Instant latestInviteAt = direct.stream()
                .map(InvitationRelationshipEntity::getCreatedAt)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(null);
        List<PortalInvitationUserResponse> directInvites = direct.stream()
                .map(entity -> new PortalInvitationUserResponse(
                        entity.getInvitedUser().getId(),
                        entity.getInvitedUser().getEmail(),
                        entity.getInvitedUser().getDisplayName(),
                        entity.getCreatedAt()
                ))
                .toList();
        return new PortalInvitationSummaryResponse(
                user.getId(),
                direct.size(),
                totalInvites,
                referrerReward,
                latestInviteAt,
                directInvites,
                leaderboardEntries(10).stream()
                        .map(entry -> new PortalInvitationLeaderboardEntryResponse(
                                entry.user().getId(),
                                displayName(entry.user()),
                                entry.directInviteCount(),
                                entry.totalInviteCount(),
                                entry.referrerRewardTokenCredits(),
                                entry.latestInviteAt()
                        ))
                        .toList()
        );
    }

    private List<LeaderboardEntry> leaderboardEntries(int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 100));
        Map<Long, GatewayUserEntity> users = new LinkedHashMap<>();
        Map<Long, Long> directCounts = new HashMap<>();
        Map<Long, Instant> latestInvite = new HashMap<>();
        for (InvitationRelationshipEntity relationship : relationshipRepository.findAllByOrderByCreatedAtDesc()) {
            GatewayUserEntity referrer = relationship.getReferrerUser();
            if (referrer == null || referrer.getId() == null) {
                continue;
            }
            users.putIfAbsent(referrer.getId(), referrer);
            directCounts.merge(referrer.getId(), 1L, Long::sum);
            if (relationship.getCreatedAt() != null) {
                latestInvite.merge(referrer.getId(), relationship.getCreatedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        Map<Long, Long> referrerRewards = new HashMap<>();
        for (InvitationCodeUsageEntity usage : usageRepository.findAllByReferrerUserIsNotNullOrderByUsedAtDesc()) {
            GatewayUserEntity referrer = usage.getReferrerUser();
            if (referrer == null || referrer.getId() == null) {
                continue;
            }
            users.putIfAbsent(referrer.getId(), referrer);
            referrerRewards.merge(referrer.getId(), usage.getReferrerRewardTokenCredits(), Long::sum);
        }
        return users.values().stream()
                .map(user -> new LeaderboardEntry(
                        user,
                        directCounts.getOrDefault(user.getId(), 0L),
                        descendantUserIds(user.getId()).size(),
                        referrerRewards.getOrDefault(user.getId(), 0L),
                        latestInvite.get(user.getId())
                ))
                .sorted(Comparator
                        .comparingLong(LeaderboardEntry::totalInviteCount).reversed()
                        .thenComparing(Comparator.comparingLong(LeaderboardEntry::directInviteCount).reversed())
                        .thenComparing(Comparator.comparingLong(LeaderboardEntry::referrerRewardTokenCredits).reversed())
                        .thenComparing(entry -> entry.user().getId()))
                .limit(resolvedLimit)
                .toList();
    }

    private Set<Long> descendantUserIds(Long userId) {
        Map<Long, List<InvitationRelationshipEntity>> children = childrenByReferrer();
        Set<Long> visited = new HashSet<>();
        collectDescendants(userId, children, visited);
        return visited;
    }

    private void collectDescendants(Long userId, Map<Long, List<InvitationRelationshipEntity>> children, Set<Long> visited) {
        for (InvitationRelationshipEntity child : children.getOrDefault(userId, List.of())) {
            Long invitedUserId = child.getInvitedUser() == null ? null : child.getInvitedUser().getId();
            if (invitedUserId == null || !visited.add(invitedUserId)) {
                continue;
            }
            collectDescendants(invitedUserId, children, visited);
        }
    }

    private Map<Long, List<InvitationRelationshipEntity>> childrenByReferrer() {
        Map<Long, List<InvitationRelationshipEntity>> children = new HashMap<>();
        for (InvitationRelationshipEntity relationship : relationshipRepository.findAllByOrderByCreatedAtDesc()) {
            GatewayUserEntity referrer = relationship.getReferrerUser();
            if (referrer == null || referrer.getId() == null) {
                continue;
            }
            children.computeIfAbsent(referrer.getId(), ignored -> new ArrayList<>()).add(relationship);
        }
        return children;
    }

    private InvitationTreeNodeResponse toTreeNode(
            GatewayUserEntity user,
            Instant invitedAt,
            int depth,
            int maxDepth,
            Map<Long, List<InvitationRelationshipEntity>> children,
            Set<Long> visited) {
        if (user == null || user.getId() == null || !visited.add(user.getId())) {
            return null;
        }
        List<InvitationTreeNodeResponse> childNodes = depth >= maxDepth
                ? List.of()
                : children.getOrDefault(user.getId(), List.of()).stream()
                .map(child -> toTreeNode(child.getInvitedUser(), child.getCreatedAt(), depth + 1, maxDepth, children, visited))
                .filter(node -> node != null)
                .toList();
        return new InvitationTreeNodeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                depth,
                invitedAt,
                childNodes
        );
    }

    private String displayName(GatewayUserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getEmail();
    }

    private record LeaderboardEntry(
            GatewayUserEntity user,
            long directInviteCount,
            long totalInviteCount,
            long referrerRewardTokenCredits,
            Instant latestInviteAt
    ) {
    }
}
