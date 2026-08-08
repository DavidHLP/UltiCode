package com.ulticode.modules.forum.service.impl;

import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumCommunityMember;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.app.error.ForumErrorCode;
import com.ulticode.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CommunityMembershipServiceImpl}.
 *
 * <p>Locks the two invariants the membership module owns: idempotent join
 * under a duplicate-key collision, and counter-consistent leave.
 *
 * <p>P7-RELOCATE-FORUM-001: uses {@link ForumErrorCode} instead of
 * legacy {@code ErrorCode}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityMembershipServiceImplTest {

    private static final String COMMUNITY_ID = "comm-1";
    private static final String USER_ID = "user-1";

    @Mock
    private ForumCommunityMapper communityMapper;

    @Mock
    private ForumCommunityMemberMapper memberMapper;

    @Mock
    private Clock clock;

    @InjectMocks
    private CommunityMembershipServiceImpl membershipService;

    private void initClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("join throws FORUM_COMMUNITY_NOT_FOUND when community does not exist")
    void joinThrowsWhenCommunityMissing() {
        when(communityMapper.selectById(COMMUNITY_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> membershipService.joinCommunity(COMMUNITY_ID, USER_ID));

        assertEquals(ForumErrorCode.FORUM_COMMUNITY_NOT_FOUND, ex.getErrorCode());
        verify(memberMapper, never()).insert(any(ForumCommunityMember.class));
    }

    @Test
    @DisplayName("join inserts a membership and increments the counter for a new member")
    void joinInsertsAndIncrementsForNewMember() {
        initClock();
        ForumCommunity community = new ForumCommunity();
        community.setId(COMMUNITY_ID);
        when(communityMapper.selectById(COMMUNITY_ID)).thenReturn(community);
        when(memberMapper.isMember(COMMUNITY_ID, USER_ID)).thenReturn(false);
        when(memberMapper.insert(any(ForumCommunityMember.class))).thenReturn(1);
        when(communityMapper.incrementMembers(COMMUNITY_ID)).thenReturn(1);

        membershipService.joinCommunity(COMMUNITY_ID, USER_ID);

        verify(memberMapper).insert(any(ForumCommunityMember.class));
        verify(communityMapper).incrementMembers(COMMUNITY_ID);
    }

    @Test
    @DisplayName("join converges to success without double increment on a duplicate-key collision")
    void joinIsIdempotentOnDuplicateKey() {
        initClock();
        ForumCommunity community = new ForumCommunity();
        community.setId(COMMUNITY_ID);
        when(communityMapper.selectById(COMMUNITY_ID)).thenReturn(community);
        when(memberMapper.isMember(COMMUNITY_ID, USER_ID)).thenReturn(false);
        when(memberMapper.insert(any(ForumCommunityMember.class)))
                .thenThrow(new DuplicateKeyException("duplicate entry"));

        // Must not throw — duplicate key means already a member
        assertDoesNotThrow(() -> membershipService.joinCommunity(COMMUNITY_ID, USER_ID));

        // No double increment since the insert failed
        verify(communityMapper, never()).incrementMembers(any());
    }

    @Test
    @DisplayName("leave deletes the membership and decrements the counter when the user is a member")
    void leaveDeletesAndDecrementsForMember() {
        when(memberMapper.deleteByCommunityIdAndUserId(COMMUNITY_ID, USER_ID)).thenReturn(1);
        when(communityMapper.decrementMembers(COMMUNITY_ID)).thenReturn(1);

        membershipService.leaveCommunity(COMMUNITY_ID, USER_ID);

        verify(memberMapper).deleteByCommunityIdAndUserId(COMMUNITY_ID, USER_ID);
        verify(communityMapper).decrementMembers(COMMUNITY_ID);
    }

    @Test
    @DisplayName("leave is a no-op (no counter drift) when the user is not a member")
    void leaveIsNoOpWhenNotMember() {
        when(memberMapper.deleteByCommunityIdAndUserId(COMMUNITY_ID, USER_ID)).thenReturn(0);

        membershipService.leaveCommunity(COMMUNITY_ID, USER_ID);

        verify(memberMapper).deleteByCommunityIdAndUserId(COMMUNITY_ID, USER_ID);
        verify(communityMapper, never()).decrementMembers(any());
    }
}
