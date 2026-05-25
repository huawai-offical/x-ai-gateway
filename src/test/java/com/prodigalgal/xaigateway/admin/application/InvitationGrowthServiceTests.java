package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.infra.persistence.entity.GatewayUserEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationCodeUsageEntity;
import com.prodigalgal.xaigateway.infra.persistence.entity.InvitationRelationshipEntity;
import com.prodigalgal.xaigateway.infra.persistence.repository.GatewayUserRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationCodeUsageRepository;
import com.prodigalgal.xaigateway.infra.persistence.repository.InvitationRelationshipRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvitationGrowthServiceTests {

    @Test
    void shouldAggregateLeaderboardAndPortalSummary() {
        InvitationRelationshipRepository relationshipRepository = Mockito.mock(InvitationRelationshipRepository.class);
        InvitationCodeUsageRepository usageRepository = Mockito.mock(InvitationCodeUsageRepository.class);
        GatewayUserRepository userRepository = Mockito.mock(GatewayUserRepository.class);
        InvitationGrowthService service = new InvitationGrowthService(relationshipRepository, usageRepository, userRepository);
        GatewayUserEntity root = user(1L, "root@example.com", "Root");
        GatewayUserEntity child = user(2L, "child@example.com", "Child");
        GatewayUserEntity grandChild = user(3L, "grand@example.com", "Grand");
        InvitationRelationshipEntity direct = relationship(root, child, "2026-05-24T01:00:00Z");
        InvitationRelationshipEntity nested = relationship(child, grandChild, "2026-05-24T02:00:00Z");
        InvitationCodeUsageEntity rootReward = usage(root, 600L);
        InvitationCodeUsageEntity childReward = usage(child, 300L);
        Mockito.when(relationshipRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(nested, direct));
        Mockito.when(relationshipRepository.findAllByReferrerUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(direct));
        Mockito.when(usageRepository.findAllByReferrerUserIsNotNullOrderByUsedAtDesc()).thenReturn(List.of(rootReward, childReward));
        Mockito.when(usageRepository.findAllByReferrerUser_IdOrderByUsedAtDesc(1L)).thenReturn(List.of(rootReward));
        Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(root));

        var leaderboard = service.leaderboard(10);
        var summary = service.portalSummary(root);
        var tree = service.tree(1L, 5);

        assertEquals(1L, leaderboard.getFirst().userId());
        assertEquals(1L, leaderboard.getFirst().directInviteCount());
        assertEquals(2L, leaderboard.getFirst().totalInviteCount());
        assertEquals(600L, leaderboard.getFirst().referrerRewardTokenCredits());
        assertEquals(1L, summary.directInviteCount());
        assertEquals(2L, summary.totalInviteCount());
        assertEquals(600L, summary.referrerRewardTokenCredits());
        assertEquals(1L, tree.userId());
        assertEquals(1, tree.children().size());
    }

    private GatewayUserEntity user(Long id, String email, String displayName) {
        GatewayUserEntity user = new GatewayUserEntity();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setActive(true);
        return user;
    }

    private InvitationRelationshipEntity relationship(GatewayUserEntity referrer, GatewayUserEntity invited, String createdAt) {
        InvitationRelationshipEntity relationship = new InvitationRelationshipEntity();
        relationship.setReferrerUser(referrer);
        relationship.setInvitedUser(invited);
        relationship.setDepth(1);
        relationship.setPath(referrer.getId() + "/" + invited.getId());
        ReflectionTestUtils.setField(relationship, "createdAt", Instant.parse(createdAt));
        return relationship;
    }

    private InvitationCodeUsageEntity usage(GatewayUserEntity referrer, long reward) {
        InvitationCodeUsageEntity usage = new InvitationCodeUsageEntity();
        usage.setReferrerUser(referrer);
        usage.setReferrerRewardTokenCredits(reward);
        return usage;
    }
}
