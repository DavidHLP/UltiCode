package com.ulticode.modules.forum.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumCommunityMember;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CommunityMembershipServiceImpl}.
 *
 * <p>Locks the two invariants the membership module owns: idempotent join
 * under a duplicate-key collision, and counter-consistent leave.
 */
@ExtendWith(MockitoExtension.class)
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

    @BeforeEach
    void setUp() {
        // Stub the Clock with a fixed instant + UTC zone so the joinedAt
        // timestamp asserted below is deterministic across CI machines
        // (ZoneId.systemDefault() / Instant.now() would drift on every run).
        Instant fixedInstant = Instant.parse("2026-07-19T00:00:00Z");
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        // LocalDateTime.now(clock) is implemented as
        // LocalDateTime.ofInstant(clock.instant(), clock.getZone()), so the two
        // stubs above are sufficient — no third stub is needed.
    }

    @Test
    @DisplayName("join throws FORUM_COMMUNITY_NOT_FOUND when community does not exist")
    void joinThrowsWhenCommunityMissing() {
        when(communityMapper.selectById(COMMUNITY_ID)).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> membershipService.joinCommunity(COMMUNITY_ID, USER_ID)
        );
        assertEquals(ErrorCode.FORUM_COMMUNITY_NOT_FOUND, ex.getErrorCode());
        verify(memberMapper, never()).insert(any(ForumCommunityMember.class));
        verify(communityMapper, never()).incrementMembers(any());
    }

    @Test
    @DisplayName("join inserts a membership and increments the counter for a new member")
    void joinInsertsAndIncrementsForNewMember() {
        when(communityMapper.selectById(COMMUNITY_ID)).thenReturn(new ForumCommunity());

        membershipService.joinCommunity(COMMUNITY_ID, USER_ID);

        // Fixed Clock above means joinedAt is deterministic (UTC 2026-07-19).
        LocalDateTime expectedJoinedAt = LocalDateTime.of(2026, 7, 19, 0, 0);
        verify(memberMapper).insert(org.mockito.ArgumentMatchers.argThat((ForumCommunityMember m) -> {
            assertEquals(COMMUNITY_ID, m.getCommunityId());
            assertEquals(USER_ID, m.getUserId());
            assertEquals("MEMBER", m.getRole());
            assertEquals(expectedJoinedAt, m.getJoinedAt());
            return true;
        }));
        verify(communityMapper).incrementMembers(COMMUNITY_ID);
    }

    @Test
    @DisplayName("join converges to success without double increment on a duplicate-key collision")
    void joinIsIdempotentOnDuplicateKey() {
        // Concurrent join: another request inserted the same (community, user) row
        // between our existence check and insert, so the unique constraint fires.
        when(communityMapper.selectById(COMMUNITY_ID)).thenReturn(new ForumCommunity());
        when(memberMapper.insert(org.mockito.ArgumentMatchers.argThat((ForumCommunityMember m) -> true)))
                .thenThrow(new DuplicateKeyException("simulated unique violation"));

        // Converges to success — the caller is already a member.
        assertDoesNotThrow(() -> membershipService.joinCommunity(COMMUNITY_ID, USER_ID));

        // The counter must not be bumped twice for the same membership.
        verify(communityMapper, never()).incrementMembers(any());
    }

    @Test
    @DisplayName("leave deletes the membership and decrements the counter when the user is a member")
    void leaveDeletesAndDecrementsForMember() {
        when(memberMapper.deleteByCommunityIdAndUserId(COMMUNITY_ID, USER_ID)).thenReturn(1);

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
