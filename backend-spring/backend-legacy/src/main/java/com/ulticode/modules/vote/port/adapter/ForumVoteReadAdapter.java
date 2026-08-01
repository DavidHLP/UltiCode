package com.ulticode.modules.vote.port.adapter;

import com.ulticode.app.api.dto.VoteStatusDTO;
import com.ulticode.app.api.service.ForumVoteReadPort;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default {@link ForumVoteReadPort} adapter backed by {@link VoteService}.
 *
 * <p>Located in {@code backend-legacy} (vote module) so it can use
 * {@code VoteService} and {@code EdgeOperationTargetType} directly.
 * Spring component scan makes it visible to {@code backend-app} which
 * declares the {@link ForumVoteReadPort} interface.
 *
 * <p>Translates the legacy {@link com.ulticode.modules.vote.dto.VoteResultVO}
 * into the app-API {@link VoteStatusDTO} record.
 *
 * <p>P7-RELOCATE-FORUM-001
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumVoteReadAdapter implements ForumVoteReadPort {

    private final VoteService voteService;

    @Override
    public VoteStatusDTO getVoteStatus(String userId, String targetId, String targetType) {
        EdgeOperationTargetType type = parseTargetType(targetType);
        var result = voteService.getVoteStatus(userId, targetId, type);
        return new VoteStatusDTO(
                result.getTargetId(),
                result.getTargetType(),
                result.getUserVote(),
                result.getLikes(),
                result.getDislikes()
        );
    }

    private static EdgeOperationTargetType parseTargetType(String targetType) {
        if (targetType == null) {
            return EdgeOperationTargetType.FORUM_POST;
        }
        return switch (targetType.toUpperCase()) {
            case "FORUM_POST" -> EdgeOperationTargetType.FORUM_POST;
            case "FORUM_COMMENT" -> EdgeOperationTargetType.FORUM_COMMENT;
            case "SOLUTION" -> EdgeOperationTargetType.SOLUTION;
            case "COMMENT" -> EdgeOperationTargetType.COMMENT;
            case "POST" -> EdgeOperationTargetType.POST;
            default -> EdgeOperationTargetType.FORUM_POST;
        };
    }
}
