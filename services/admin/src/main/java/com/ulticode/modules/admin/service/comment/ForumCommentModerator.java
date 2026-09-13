package com.ulticode.modules.admin.service.comment;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.dto.ForumCommentModerationResultDTO;
import com.ulticode.app.api.service.ForumCommentAdministrationService;
import com.ulticode.app.api.service.ForumCommentReadPort;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentPage;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentRow;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Forum-branch implementation of {@link CommentModerator}.
 *
 * <p>Owns the entire {@code "forum"} arm of the five moderated operations.
 * Was previously the {@code if ("forum".equals(type)) ...} block inside
 * {@code AdminCommentServiceImpl}; extracted to keep the service layer a
 * thin router and to make the cross-mapper enrichment contract local to
 * carrying full command / idempotency / actor / trace metadata. Author
 * profiles and post titles are enriched by {@link AdminCommentReadPort}.
 * {@code RpcResult} failures are mapped explicitly onto
 * {@link AdminErrorCode}, and the local audit diff (old/new values +
 * {@code type}) is preserved verbatim.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumCommentModerator implements CommentModerator {

    static final String TYPE = "forum";

    private final ForumCommentReadPort forumCommentReadPort;
    private final ForumCommentAdministrationService forumCommentAdministrationService;
    private final AdminCommentReadPort commentReadPort;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PageResult<AdminCommentVO> listComments(AdminCommentQueryDTO query, int page, int limit) {
        ForumCommentPage commentPage = forumCommentReadPort.page(
                query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder(), page, limit);

        Set<String> authorIds = commentPage.rows().stream()
                .map(ForumCommentRow::authorId).collect(Collectors.toSet());
        Set<String> postIds = commentPage.rows().stream()
                .map(ForumCommentRow::postId).collect(Collectors.toSet());

        Map<String, AdminCommentReadPort.AuthorSummary> authorMap =
                commentReadPort.findAuthorSummariesByIds(authorIds);
        Map<String, String> postTitleMap = commentReadPort.findForumPostTitlesByIds(postIds);

        List<AdminCommentVO> vos = commentPage.rows().stream()
                .map(c -> toAdminVO(c, authorMap.get(c.authorId()), postTitleMap.get(c.postId())))
                .toList();

        return PageResult.of(vos, commentPage.total(), page, limit);
    }

    @Override
    public AdminCommentVO getComment(String commentId) {
        ForumCommentRow comment = forumCommentReadPort.getById(commentId);
        if (comment == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AdminCommentReadPort.AuthorSummary author = commentReadPort
                .findAuthorSummariesByIds(Set.of(comment.authorId()))
                .get(comment.authorId());
        String postTitle = commentReadPort
                .findForumPostTitlesByIds(Set.of(comment.postId()))
                .get(comment.postId());
        return toAdminVO(comment, author, postTitle);
    }

    @Override
    public void flagComment(String commentId, String reason) {
        RpcResult<ForumCommentModerationResultDTO> result = forumCommentAdministrationService.moderate(
                command(commentId, ForumCommentModerationCommand.Action.FLAG, reason, null));
        if (result == null || !result.success() || result.data() == null) {
            throw AdminOwnerErrorMapper.mapOwnerError(
                    AdminOwnerErrorMapper.Owner.FORUM_COMMENT, result);
        }
        ForumCommentModerationResultDTO res = result.data();
        AuditContext.setUserId(res.authorUserId());
        AuditContext.setOldValues(oldFlagValues(res));
        AuditContext.setNewValues(Map.of(
            "isFlagged", true,
            "flaggedReason", reason != null ? reason : "",
            "type", TYPE
        ));
        log.info("Forum comment flagged: {}", commentId);
    }

    @Override
    public void unflagComment(String commentId) {
        RpcResult<ForumCommentModerationResultDTO> result = forumCommentAdministrationService.moderate(
                command(commentId, ForumCommentModerationCommand.Action.UNFLAG, null, null));
        if (result == null || !result.success() || result.data() == null) {
            throw AdminOwnerErrorMapper.mapOwnerError(
                    AdminOwnerErrorMapper.Owner.FORUM_COMMENT, result);
        }
        ForumCommentModerationResultDTO res = result.data();
        AuditContext.setUserId(res.authorUserId());
        AuditContext.setOldValues(oldFlagValues(res));
        AuditContext.setNewValues(Map.of(
            "isFlagged", false,
            "flaggedReason", "",
            "type", TYPE
        ));
        log.info("Forum comment unflagged: {}", commentId);
    }

    @Override
    public void deleteComment(String commentId) {
        String deletedBy = currentUserProvider.getCurrentUserId();
        RpcResult<ForumCommentModerationResultDTO> result = forumCommentAdministrationService.moderate(
                command(commentId, ForumCommentModerationCommand.Action.DELETE, null, deletedBy));
        if (result == null || !result.success() || result.data() == null) {
            throw AdminOwnerErrorMapper.mapOwnerError(
                    AdminOwnerErrorMapper.Owner.FORUM_COMMENT, result);
        }
        ForumCommentModerationResultDTO res = result.data();
        AuditContext.setUserId(res.authorUserId());
        AuditContext.setOldValues(oldDeleteValues(res));
        AuditContext.setNewValues(Map.of("isDeleted", true, "type", TYPE));
        log.info("Forum comment deleted: {} by {}", commentId, deletedBy);
    }
    private ForumCommentModerationCommand command(String commentId, ForumCommentModerationCommand.Action action,
                                                  String reason, String deletedBy) {
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "forum-comment-moderation", null, actorId, currentUserProvider,
                "forum comment moderation");
        return new ForumCommentModerationCommand(
                envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                commentId, action, reason, deletedBy);
    }


    private static Map<String, Object> oldFlagValues(ForumCommentModerationResultDTO res) {
        Map<String, Object> map = new HashMap<>();
        map.put("isFlagged", res.previousIsFlagged());
        map.put("flaggedReason", res.previousFlaggedReason());
        map.put("type", TYPE);
        return map;
    }

    private static Map<String, Object> oldDeleteValues(ForumCommentModerationResultDTO res) {
        Map<String, Object> map = new HashMap<>();
        map.put("isDeleted", res.previousIsDeleted());
        map.put("type", TYPE);
        return map;
    }
    static AdminCommentVO toAdminVO(ForumCommentRow comment,
                                     AdminCommentReadPort.AuthorSummary author,
                                     String postTitle) {
        return new AdminCommentVO(
            comment.id(),
            comment.body(),
            comment.createdAt(),
            comment.editedAt() != null ? comment.editedAt() : comment.createdAt(),
            comment.authorId(),
            comment.parentId(),
            TYPE,
            comment.postId(),
            postTitle,
            author != null ? new AdminCommentVO.AuthorInfo(author.id(), author.username(), author.avatar()) : null,
            comment.isFlagged(),
            comment.flaggedReason(),
            comment.flaggedAt(),
            comment.isDeleted(),
            comment.deletedAt(),
            comment.deletedBy()
        );
    }
}
