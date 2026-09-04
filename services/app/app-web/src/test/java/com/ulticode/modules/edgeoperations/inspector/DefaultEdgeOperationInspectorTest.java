package com.ulticode.modules.edgeoperations.inspector;

import com.ulticode.modules.bookmark.port.BookmarkReadPort;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultEdgeOperationInspector}, the new home
 * for aggregated edge-operation interaction stats.
 *
 * <p>The read module is exercised here in isolation; the
 * write-module tests in {@code EdgeOperationsServiceTest} only
 * verify the write paths (vote / toggle) and stub the inspector
 * with {@code when(...).thenReturn(...)} when the response shape
 * needs to match.
 *
 * <p>Test surface: a single constructor call with two collaborators
 * ({@code VoteService}, {@code BookmarkMapper}). No Spring context
 * is required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultEdgeOperationInspector — interaction read deep module")
class DefaultEdgeOperationInspectorTest {

    @Mock
    private VoteService voteService;

    @Mock
    private BookmarkReadPort bookmarkReadPort;

    private DefaultEdgeOperationInspector inspector;

    private static final String USER_ID = "test-user-id";
    private static final String TARGET_ID = "test-target-id";
    private static final EdgeOperationTargetType TARGET_TYPE = EdgeOperationTargetType.PROBLEM;

    @BeforeEach
    void setUp() {
        inspector = new DefaultEdgeOperationInspector(voteService, bookmarkReadPort);
    }

    // ------------------------------------------------------------------
    // getInteractions
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getInteractions")
    class GetInteractionsTests {

        @Test
        @DisplayName("returns aggregated stats for a leaf target type")
        void getInteractions_leafType_returnsAggregatedStats() {
            VoteResultVO voteResult = new VoteResultVO(
                    TARGET_ID, TARGET_TYPE.getValue(), 20L, 5L, 1);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(8L);

            EdgeOperationResponseVO result = inspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE);

            assertThat(result).isNotNull();
            assertThat(result.getLikes()).isEqualTo(20L);
            assertThat(result.getDislikes()).isEqualTo(5L);
            assertThat(result.getFavorites()).isEqualTo(8L);
            assertThat(result.getViewer().getVote()).isEqualTo(1);

            verify(bookmarkReadPort).countFavoritesByTarget(anyString(), anyString());
        }

        @Test
        @DisplayName("returns 0 favorites for non-leaf target types (port filters internally)")
        void getInteractions_nonLeafType_returnsZeroFavoritesAndSkipsBookmark() {
            EdgeOperationTargetType postType = EdgeOperationTargetType.POST;
            VoteResultVO voteResult = new VoteResultVO(
                    TARGET_ID, postType.getValue(), 10L, 2L, -1);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, postType)).thenReturn(voteResult);
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(0L);

            EdgeOperationResponseVO result = inspector.getInteractions(USER_ID, TARGET_ID, postType);

            assertThat(result.getFavorites()).isZero();
            assertThat(result.getViewer().getVote()).isEqualTo(-1);
        }

        @Test
        @DisplayName("queries the bookmark mapper for leaf types like FORUM_POST")
        void getInteractions_forumPostType_propagatesBookmarkCount() {
            EdgeOperationTargetType forumPostType = EdgeOperationTargetType.FORUM_POST;
            VoteResultVO voteResult = new VoteResultVO(
                    TARGET_ID, forumPostType.getValue(), 3L, 0L, 0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, forumPostType)).thenReturn(voteResult);
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(7L);

            EdgeOperationResponseVO result = inspector.getInteractions(USER_ID, TARGET_ID, forumPostType);

            assertThat(result.getFavorites()).isEqualTo(7L);
            verify(bookmarkReadPort).countFavoritesByTarget(anyString(), anyString());
        }

        @Test
        @DisplayName("works for anonymous users (null userId)")
        void getInteractions_anonymousUser_returnsStats() {
            VoteResultVO voteResult = new VoteResultVO(
                    TARGET_ID, TARGET_TYPE.getValue(), 20L, 5L, 0);
            when(voteService.getVoteStatus(null, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(3L);

            EdgeOperationResponseVO result = inspector.getInteractions(null, TARGET_ID, TARGET_TYPE);

            assertThat(result).isNotNull();
            assertThat(result.getFavorites()).isEqualTo(3L);
            assertThat(result.getViewer().getVote()).isZero();
        }

        @Test
        @DisplayName("returns zero user vote when the user has not voted")
        void getInteractions_userHasNotVoted_returnsZeroVote() {
            VoteResultVO voteResult = new VoteResultVO(
                    TARGET_ID, TARGET_TYPE.getValue(), 20L, 5L, 0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(0L);

            EdgeOperationResponseVO result = inspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE);

            assertThat(result.getViewer().getVote()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // getFavoritesCount
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getFavoritesCount")
    class GetFavoritesCountTests {

        @Test
        @DisplayName("returns the bookmark mapper count for leaf target types")
        void getFavoritesCount_leafType_returnsBookmarkCount() {
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(42L);

            long count = inspector.getFavoritesCount(TARGET_ID, TARGET_TYPE);

            assertThat(count).isEqualTo(42L);
            verify(bookmarkReadPort).countFavoritesByTarget(anyString(), anyString());
        }

        @Test
        @DisplayName("returns 0 for non-leaf target types (port filters internally)")
        void getFavoritesCount_nonLeafType_returnsZeroAndSkipsMapper() {
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(0L);

            long count = inspector.getFavoritesCount(TARGET_ID, EdgeOperationTargetType.POST);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("returns 0 for COMMENT target type (port filters internally)")
        void getFavoritesCount_commentType_returnsZero() {
            when(bookmarkReadPort.countFavoritesByTarget(anyString(), anyString())).thenReturn(0L);

            long count = inspector.getFavoritesCount(TARGET_ID, EdgeOperationTargetType.COMMENT);

            assertThat(count).isZero();
        }

    }
}
