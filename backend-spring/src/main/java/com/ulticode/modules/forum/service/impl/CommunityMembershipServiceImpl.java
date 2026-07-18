package com.ulticode.modules.forum.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.entity.ForumCommunityMember;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.service.CommunityMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Community membership write implementation. Owns the join/leave invariants
 * previously embedded in the deleted {@code ForumWritePort} pass-through.
 *
 * <p><b>Idempotent join</b>: instead of a read-then-insert {@code isMember}
 * check (TOCTOU race under concurrent joins), the code inserts directly and
 * relies on the {@code forum_community_members_community_id_user_id_key}
 * unique constraint to reject duplicates. A duplicate-key collision means
 * another request already established the membership, so the counter is not
 * bumped twice and the call converges to success.
 *
 * <p><b>Counter-consistent leave</b>: the member counter is decremented only
 * when a membership row was actually deleted, so leaving as a non-member
 * cannot drift the counter below reality.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityMembershipServiceImpl implements CommunityMembershipService {

    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void joinCommunity(String communityId, String userId) {
        if (communityMapper.selectById(communityId) == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        }
        ForumCommunityMember membership = new ForumCommunityMember();
        membership.setCommunityId(communityId);
        membership.setUserId(userId);
        membership.setRole("MEMBER");
        membership.setJoinedAt(LocalDateTime.now(clock));
        try {
            memberMapper.insert(membership);
            communityMapper.incrementMembers(communityId);
        } catch (DuplicateKeyException ex) {
            // 并发:UNIQUE(community_id, user_id) 已被另一请求插入。
            // 收敛到「当前已是成员」的成功状态,不对 member 计数二次自增。
            log.warn("Converging on existing membership after duplicate-key collision: community={}, user={}",
                    communityId, userId);
        }
    }

    @Override
    @Transactional
    public void leaveCommunity(String communityId, String userId) {
        int affected = memberMapper.deleteByCommunityIdAndUserId(communityId, userId);
        if (affected > 0) {
            communityMapper.decrementMembers(communityId);
        }
    }
}
