package com.ulticode.modules.forum.service.impl;

import com.ulticode.modules.forum.service.ForumVoteService;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of ForumVoteService.
 * Thin delegation to VoteService for forum post vote enrichment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumVoteServiceImpl implements ForumVoteService {

    private final VoteService voteService;

    @Override
    public VoteResultVO getPostVoteStatus(String userId, String postId) {
        log.debug("Getting vote status for post: {} user: {}", postId, userId);
        return voteService.getVoteStatus(userId, postId, EdgeOperationTargetType.FORUM_POST);
    }
}
