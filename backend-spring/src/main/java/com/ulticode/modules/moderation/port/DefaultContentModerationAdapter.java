package com.ulticode.modules.moderation.port;

import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultContentModerationAdapter implements ContentModerationPort {

    private final ForumPostMapper forumPostMapper;
    private final ForumCommentMapper forumCommentMapper;
    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final ProblemMapper problemMapper;

    @Override
    public String resolveAuthorId(String entityType, String entityId) {
        if (entityType == null || entityId == null) {
            return null;
        }
        switch (entityType) {
            case "forum_post":
                var post = forumPostMapper.selectById(entityId);
                return post != null ? post.getUserId() : null;
            case "forum_comment":
                var comment = forumCommentMapper.selectById(entityId);
                return comment != null ? comment.getAuthorId() : null;
            case "solution":
                var solution = solutionMapper.selectById(entityId);
                return solution != null ? solution.getUserId() : null;
            case "solution_comment":
                var solComment = solutionCommentMapper.selectById(entityId);
                return solComment != null ? solComment.getUserId() : null;
            case "problem":
                var problem = problemMapper.selectById(entityId);
                return problem != null ? problem.getPublishedBy() : null;
            default:
                return null;
        }
    }

    @Override
    public void updateFlagStatus(String entityType, String entityId, boolean isFlagged, String reason) {
        if (entityType == null || entityId == null) {
            return;
        }
        switch (entityType) {
            case "forum_post":
                forumPostMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "forum_comment":
                forumCommentMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "solution":
                solutionMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "solution_comment":
                solutionCommentMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "problem":
                problemMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            default:
                break;
        }
    }
}
