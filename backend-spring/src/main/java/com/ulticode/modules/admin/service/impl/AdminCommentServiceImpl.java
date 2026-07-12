package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.dto.BulkCommentActionRequest;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.service.AdminCommentService;
import com.ulticode.modules.admin.service.comment.CommentModerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Thin router for admin comment moderation. After the
 * {@link CommentModerator} seam extraction, this class no longer contains
 * any {@code if ("forum".equals(type)) ... else if ("solution".equals(type)) ...}
 * branching — each of the five moderated operations (list / get / flag /
 * unflag / delete) is delegated to a registered {@link CommentModerator}
 * keyed by {@link CommentModerator#getType()}.
 *
 * <p>Adding a third comment store (e.g. contest comments) is a one-bean
 * registration: drop in a new {@code @Component implements CommentModerator}
 * and Spring's component scan auto-wires it into {@link #moderators}; this
 * class picks it up via {@link #moderatorsByType} on next startup.
 *
 * <p>The router still owns two concerns that legitimately span moderators:
 * <ol>
 *   <li><b>Bulk actions</b> ({@link #bulkCommentAction}) — iterate the per-id
 *       work and aggregate per-item results, identical for every moderator.</li>
 *   <li><b>Type=null aggregation</b> ({@link #getComments} when no type is
 *       supplied) — query every moderator with a max-page-size page, merge,
 *       sort, paginate in memory. Each moderator's per-store query lives
 *       in that moderator; the cross-store merge is here because it has
 *       no single owner.</li>
 * </ol>
 *
 * <p>The {@link CurrentUserProvider} field is preserved from the prior
 * migration that replaced {@code SecurityUtil.getCurrentUserId()}. Each
 * moderator now owns its own {@link CurrentUserProvider} for the
 * {@code deleted_by} stamp; the field here is kept for parity with that
 * migration's intent and remains available for future cross-moderator
 * audit context needs.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentServiceImpl implements AdminCommentService {

    private final List<CommentModerator> moderators;
    private final AdminCommentReadPort commentReadPort;
    private final CurrentUserProvider currentUserProvider;
    private final AdminBulkExecutor bulkExecutor;

    /**
     * Type-keyed view of {@link #moderators}, built once at startup.
     * Mutating the underlying list is not supported.
     */
    private Map<String, CommentModerator> moderatorsByType;

    @PostConstruct
    public void indexModeratorsByType() {
        this.moderatorsByType = moderators.stream()
            .collect(Collectors.toUnmodifiableMap(
                CommentModerator::getType,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException(
                        "Duplicate CommentModerator for type " + a.getType()
                            + ": " + a + " vs " + b);
                }
            ));
        log.info("Registered {} comment moderators: {}",
            moderatorsByType.size(), moderatorsByType.keySet());
    }

    @Override
    public PageResult<AdminCommentVO> getComments(AdminCommentQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        String type = query.getType();
        if (!StringUtils.hasText(type)) {
            return getAllComments(query, page, limit);
        }
        CommentModerator moderator = moderatorFor(type);
        return moderator.listComments(query, page, limit);
    }

    @Override
    public AdminCommentVO getComment(String id, String type) {
        return moderatorFor(type).getComment(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.FLAG_COMMENT, entityType = AuditVocabulary.ENTITY_COMMENT)
    public AdminCommentVO flagComment(String id, String type, String reason) {
        moderatorFor(type).flagComment(id, reason);
        return getComment(id, type);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UNFLAG_COMMENT, entityType = AuditVocabulary.ENTITY_COMMENT)
    public AdminCommentVO unflagComment(String id, String type) {
        moderatorFor(type).unflagComment(id);
        return getComment(id, type);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.DELETE_COMMENT, entityType = AuditVocabulary.ENTITY_COMMENT)
    public void deleteComment(String id, String type) {
        moderatorFor(type).deleteComment(id);
    }

    @Override
    @Transactional
    public BulkActionResult bulkCommentAction(BulkCommentActionRequest request) {
        AdminBulkExecutor.Run run = bulkExecutor.run(
            request.getIds(),
            request.getAction(),
            id -> {
                switch (request.getAction()) {
                    case "delete" -> deleteComment(id, request.getType());
                    case "unflag" -> unflagComment(id, request.getType());
                    default -> throw new IllegalArgumentException("Unknown action: " + request.getAction());
                }
            },
            id -> true);

        BulkActionResult response = new BulkActionResult();
        response.setTotal(run.total());
        response.setSuccessful(run.successful());
        response.setFailed(run.failed());
        response.setResults(new ArrayList<>(run.items().size()));
        for (AdminBulkExecutor.ItemOutcome outcome : run.items()) {
            response.getResults().add(new BulkActionResult.BulkActionItem(
                outcome.id(), outcome.isSuccess(), outcome.errorOrNull()));
        }
        return response;
    }

    /**
     * Cross-moderator merge for the type-less {@link #getComments} path.
     *
     * <p>Each moderator is queried with a max-page-size page to fetch its
     * full result set; the union is sorted by {@code createdAt} desc and
     * sliced in memory. Preserves the original behavior of the
     * pre-refactor {@code getAllComments} method.
     */
    private PageResult<AdminCommentVO> getAllComments(AdminCommentQueryDTO query, int page, int limit) {
        List<AdminCommentVO> all = new ArrayList<>();
        for (CommentModerator moderator : moderators) {
            PageResult<AdminCommentVO> moderatorPage =
                moderator.listComments(query, 1, Integer.MAX_VALUE);
            if (moderatorPage != null && moderatorPage.getItems() != null) {
                all.addAll(moderatorPage.getItems());
            }
        }
        all.sort(Comparator.comparing(AdminCommentVO::createdAt).reversed());

        long total = all.size();
        int fromIndex = Math.min((page - 1) * limit, all.size());
        int toIndex = Math.min(fromIndex + limit, all.size());
        List<AdminCommentVO> paged = all.subList(fromIndex, toIndex);

        return PageResult.of(paged, total, page, limit);
    }

    /**
     * Resolve the moderator for a type tag. Throws
     * {@link BusinessException}({@link ErrorCode#BAD_REQUEST}) when the
     * type is not registered — the closed set is whatever the component
     * scan has provided at startup, so there is no static allow-list
     * to drift from the bean registrations.
     */
    private CommentModerator moderatorFor(String type) {
        CommentModerator moderator = moderatorsByType.get(type);
        if (moderator == null) {
            Set<String> valid = moderatorsByType.keySet();
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "Invalid comment type: '" + type + "'. Allowed: " + String.join(", ",
                    valid.stream().sorted().collect(Collectors.toList())));
        }
        return moderator;
    }
}
