package com.ulticode.modules.forum.projection;

import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default {@link ForumCommentProjection} implementation.
 *
 * <p>Owns the entity-to-VO shaping and nested-reply assembly previously
 * carried by {@code ForumCommentServiceImpl}. Behaviour is preserved verbatim
 * from the former service methods; only ownership moved so the write service
 * no longer exposes read-shaping on its interface and the read projection no
 * longer depends on the write service.
 *
 * @author ulticode
 */
@Component
public class DefaultForumCommentProjection implements ForumCommentProjection {

    @Override
    public ForumCommentVO toCommentVO(ForumComment comment, Map<String, User> authorMap) {
        ForumCommentVO vo = new ForumCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setAuthorId(comment.getAuthorId());

        User author = authorMap.get(comment.getAuthorId());
        if (author != null) {
            vo.setAuthorUsername(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        vo.setBody(comment.getBody());
        vo.setMarkdown(comment.getMarkdown());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setEditedAt(comment.getEditedAt());
        vo.setIsPinned(comment.getIsPinned());
        vo.setIsLocked(comment.getIsLocked());
        vo.setIsFlagged(comment.getIsFlagged());
        vo.setFlaggedReason(comment.getFlaggedReason());
        vo.setFlaggedAt(comment.getFlaggedAt());
        return vo;
    }

    @Override
    public List<ForumCommentVO> buildCommentTree(
            List<? extends ForumComment> comments,
            Map<String, User> authorMap) {

        List<? extends ForumComment> topLevel = comments.stream()
                .filter(c -> c.getParentId() == null)
                .collect(Collectors.toList());

        return topLevel.stream()
                .map(c -> {
                    ForumCommentVO vo = toCommentVO(c, authorMap);
                    List<ForumCommentVO> replies = findReplies(c.getId(), comments, authorMap);
                    if (!replies.isEmpty()) {
                        vo.setReplies(replies);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private List<ForumCommentVO> findReplies(
            String parentId,
            List<? extends ForumComment> allComments,
            Map<String, User> authorMap) {

        return allComments.stream()
                .filter(c -> parentId.equals(c.getParentId()))
                .map(c -> {
                    ForumCommentVO vo = toCommentVO(c, authorMap);
                    vo.setReplies(findReplies(c.getId(), allComments, authorMap));
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
