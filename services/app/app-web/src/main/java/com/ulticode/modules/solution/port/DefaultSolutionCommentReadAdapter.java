package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.service.SolutionCommentReadPort;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provider implementing {@link SolutionCommentReadPort} for the admin
 * service (ADMIN-006).
 *
 * <p>Delegates to the mapper's logical-delete-ignoring pair
 * ({@code selectPageIgnoreDeleted}/{@code selectByIdIgnoreDeleted}) so admin
 * comment moderation keeps the same audit-visible semantics it had when it
 * reached for {@code SolutionCommentMapper} directly. Rows cross the seam as
 * entity-free {@link SolutionCommentReadPort.SolutionCommentRow} values.
 *
 * @author ulticode
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultSolutionCommentReadAdapter implements SolutionCommentReadPort {

    private final SolutionCommentMapper solutionCommentMapper;

    @Override
    public SolutionCommentPage page(Boolean isFlagged, Boolean isDeleted, String search,
                                    String solutionId, String sortBy, String sortOrder,
                                    int page, int limit) {
        Page<SolutionComment> pageResult = new Page<>(page, limit);
        List<SolutionComment> records = solutionCommentMapper.selectPageIgnoreDeleted(
                pageResult, isFlagged, isDeleted, search,
                solutionId, sortBy, sortOrder);
        pageResult.setRecords(records);

        List<SolutionCommentRow> rows = records.stream().map(this::toRow).toList();
        return new SolutionCommentPage(rows, pageResult.getTotal());
    }

    @Override
    public SolutionCommentRow getById(String commentId) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        return comment != null ? toRow(comment) : null;
    }

    private SolutionCommentRow toRow(SolutionComment c) {
        return new SolutionCommentRow(
                c.getId(),
                c.getContent(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getUserId(),
                c.getParentId(),
                c.getSolutionId(),
                c.getIsFlagged(),
                c.getFlaggedReason(),
                c.getFlaggedAt(),
                c.getIsDeleted(),
                c.getDeletedAt(),
                c.getDeletedBy());
    }
}
