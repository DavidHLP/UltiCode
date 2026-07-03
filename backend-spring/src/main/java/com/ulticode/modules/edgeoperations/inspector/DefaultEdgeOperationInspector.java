package com.ulticode.modules.edgeoperations.inspector;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.entity.enums.BookmarkType;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default adapter for {@link EdgeOperationInspector}. Side-effect free:
 * reads from {@link VoteService} and {@link BookmarkMapper} only.
 *
 * <p>This is the new home for the aggregated interaction stats that
 * {@code EdgeOperationsService} used to expose as
 * {@code getInteractions}. The service kept its write-path contract
 * (vote / toggle / favorite) and delegates every read to this module;
 * non-HTTP callers
 * ({@code DefaultProblemProjection#buildInteractions},
 * {@code EdgeOperationsServiceImpl#handleVoteOperation}) reuse the same
 * seam so the bookmark-count policy lives in one place.
 *
 * <p>The {@link VoteService} collaborator is injected directly rather
 * than routed through {@code EdgeOperationsService} so the inspector
 * can answer a "what are the votes for this target?" question without
 * pulling in the write-path bean graph. The bookmark mapper is the
 * single source of truth for favorite counts; the
 * {@link BookmarkType#leafTypeNames()} gate short-circuits the
 * database round-trip for non-leaf target types (POST / COMMENT /
 * PROBLEM_LIST) that the bookmark module never stores rows for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultEdgeOperationInspector implements EdgeOperationInspector {

    private final VoteService voteService;
    private final BookmarkMapper bookmarkMapper;

    @Override
    public EdgeOperationResponseVO getInteractions(String userId, String targetId,
                                                   EdgeOperationTargetType targetType) {
        // Read-side: vote counts + per-viewer vote state.
        VoteResultVO voteStatus = voteService.getVoteStatus(userId, targetId, targetType);

        // Read-side: favorites count from the bookmark module.
        long favorites = getFavoritesCount(targetId, targetType);

        return EdgeOperationResponseVO.builder()
                .likes(voteStatus.getLikes())
                .dislikes(voteStatus.getDislikes())
                .favorites(favorites)
                .viewer(EdgeOperationResponseVO.ViewerState.builder()
                        .vote(voteStatus.getUserVote())
                        .build())
                .build();
    }

    @Override
    public long getFavoritesCount(String targetId, EdgeOperationTargetType targetType) {
        if (!BookmarkType.leafTypeNames().contains(targetType.getValue())) {
            // Non-leaf types (POST / COMMENT / PROBLEM_LIST) are not stored in
            // the bookmarks table; skip the round-trip and return 0. The set is
            // sourced from BookmarkType.leafTypeNames() so adding a new leaf
            // type to the bookmark module is automatically picked up here.
            return 0L;
        }
        QueryWrapper<Bookmark> wrapper = new QueryWrapper<>();
        wrapper.eq("target_id", targetId)
                .eq("target_type", targetType.getValue());
        return bookmarkMapper.selectCount(wrapper);
    }
}
