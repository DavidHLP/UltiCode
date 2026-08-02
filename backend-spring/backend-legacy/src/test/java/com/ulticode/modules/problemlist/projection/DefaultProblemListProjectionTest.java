package com.ulticode.modules.problemlist.projection;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problemlist.dto.CategorySummaryVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UserListsForProblemVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;
import com.ulticode.modules.problemlist.dto.UserProblemListsVO;
import com.ulticode.modules.problemlist.entity.ProblemListBookmark;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.app.api.service.ProblemListReadPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for {@link DefaultProblemListProjection} — the read-side deep
 * module lifted out of ProblemListServiceImpl. Currently covers the
 * getUserListsForProblem read with its N+1-fix regression guard (batch-loaded
 * hasProblem + problemCount). These cases previously lived on
 * ProblemListServiceTest and were migrated verbatim when the read cluster
 * moved behind the projection seam.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultProblemListProjection")
class DefaultProblemListProjectionTest {

    @Mock private ProblemListMapper problemListMapper;
    @Mock private ProblemListProblemMapper problemListProblemMapper;
    @Mock private ProblemListCategoryMapper problemListCategoryMapper;
    @Mock private ProblemListBookmarkMapper problemListBookmarkMapper;
    @Mock private ProblemListReadPort problemListReadPort;
    @Mock private UserMapper userMapper;

    private static final String OWNER_ID = "user-001";

    private DefaultProblemListProjection projection;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        projection = new DefaultProblemListProjection(
                problemListMapper, problemListProblemMapper, problemListCategoryMapper,
                problemListBookmarkMapper, problemListReadPort, userMapper);
    }

    @Nested
    @DisplayName("getUserListsForProblem()")
    class GetUserListsForProblemTests {

        private final Long PROBLEM_ID_LONG = 7L;

        @Test
        @DisplayName("should batch-load hasProblem + count via 2 queries, never N+1")
        void getUserListsForProblem_BatchesInsteadOfNPlusOne() {
            ProblemList l1 = createList("list-a", "List A");
            ProblemList l2 = createList("list-b", "List B");
            ProblemList l3 = createList("list-c", "List C");
            when(problemListMapper.findByAuthorId(OWNER_ID))
                    .thenReturn(Arrays.asList(l1, l2, l3));

            when(problemListProblemMapper.findListIdsContainingProblem(
                    anyList(), anyLong()))
                    .thenReturn(Arrays.asList("list-a", "list-c")); // l1 + l3 contain problem 7

            Map<String, Object> row1 = new HashMap<>();
            row1.put("list_id", "list-a"); row1.put("cnt", 3L);
            Map<String, Object> row2 = new HashMap<>();
            row2.put("list_id", "list-b"); row2.put("cnt", 1L);
            Map<String, Object> row3 = new HashMap<>();
            row3.put("list_id", "list-c"); row3.put("cnt", 5L);
            when(problemListProblemMapper.countByListIds(anyList()))
                    .thenReturn(Arrays.asList(row1, row2, row3));

            UserListsForProblemVO result =
                    projection.getUserListsForProblem(OWNER_ID, PROBLEM_ID_LONG);

            assertThat(result.getProblemId()).isEqualTo(PROBLEM_ID_LONG);
            assertThat(result.getLists()).hasSize(3);
            assertThat(result.getLists())
                    .filteredOn(s -> s.getId().equals("list-a"))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.getHasProblem()).isTrue();
                        assertThat(s.getProblemCount()).isEqualTo(3);
                        assertThat(s.getCanEdit()).isTrue();
                    });
            assertThat(result.getLists())
                    .filteredOn(s -> s.getId().equals("list-b"))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.getHasProblem()).isFalse();
                        assertThat(s.getProblemCount()).isEqualTo(1);
                    });
            assertThat(result.getLists())
                    .filteredOn(s -> s.getId().equals("list-c"))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.getHasProblem()).isTrue();
                        assertThat(s.getProblemCount()).isEqualTo(5);
                    });

            // Verify N+1 fix: 2 batched calls (not 2*3 = 6)
            verify(problemListProblemMapper).findListIdsContainingProblem(anyList(), anyLong());
            verify(problemListProblemMapper).countByListIds(anyList());
            // Old per-list methods must NEVER be invoked
            verify(problemListProblemMapper, never())
                    .findByListIdAndProblemId(anyString(), anyLong());
            verify(problemListProblemMapper, never()).countByListId(anyString());
        }

        @Test
        @DisplayName("should default problemCount to 0 for lists with no entries")
        void getUserListsForProblem_DefaultsZeroCount() {
            ProblemList l1 = createList("list-x", "X");
            when(problemListMapper.findByAuthorId(OWNER_ID)).thenReturn(Arrays.asList(l1));
            when(problemListProblemMapper.findListIdsContainingProblem(anyList(), anyLong()))
                    .thenReturn(Arrays.asList());
            // empty countByListIds → list-x not in map → defaults to 0
            when(problemListProblemMapper.countByListIds(anyList())).thenReturn(Arrays.asList());

            UserListsForProblemVO result =
                    projection.getUserListsForProblem(OWNER_ID, PROBLEM_ID_LONG);

            assertThat(result.getLists()).hasSize(1);
            assertThat(result.getLists().get(0).getProblemCount()).isEqualTo(0);
            assertThat(result.getLists().get(0).getHasProblem()).isFalse();
        }

        @Test
        @DisplayName("should return empty lists when user has no lists (short-circuit)")
        void getUserListsForProblem_EmptyWhenNoLists() {
            when(problemListMapper.findByAuthorId(OWNER_ID)).thenReturn(Arrays.asList());

            UserListsForProblemVO result =
                    projection.getUserListsForProblem(OWNER_ID, PROBLEM_ID_LONG);

            assertThat(result.getLists()).isEmpty();
            // Verify we short-circuited (no batch calls)
            verify(problemListProblemMapper, never())
                    .findListIdsContainingProblem(anyList(), anyLong());
            verify(problemListProblemMapper, never()).countByListIds(anyList());
        }

        private ProblemList createList(String id, String name) {
            ProblemList list = new ProblemList();
            list.setId(id);
            list.setName(name);
            list.setAuthorId(OWNER_ID);
            list.setIsPublic(false);
            list.setIsFeatured(false);
            return list;
        }
    }

    // ==================== findAll (Phase B: pre-existing coverage gap) ====================

    @Nested
    @DisplayName("findAll()")
    class FindAllTests {

        @Test
        @DisplayName("happy path: featured + public lists, empty saved/categories for unauthenticated view")
        void findAll_populatesFeaturedAndPublic() {
            ProblemList featured = listEntity("list-f", "Featured");
            featured.setIsFeatured(true);
            ProblemList publicList = listEntity("list-p", "Public");
            when(problemListMapper.findFeatured()).thenReturn(List.of(featured));
            when(problemListMapper.findAllPublic()).thenReturn(List.of(publicList));
            // toSummaryVO enrichment collaborators
            when(problemListProblemMapper.countByListId(anyString())).thenReturn(0L);

            UserProblemListsVO result = projection.findAll("en");

            assertThat(result.getFeaturedLists()).hasSize(1);
            assertThat(result.getFeaturedLists().get(0).getId()).isEqualTo("list-f");
            assertThat(result.getOwnLists()).hasSize(1);
            assertThat(result.getOwnLists().get(0).getId()).isEqualTo("list-p");
            // Unauthenticated view contract: no saved lists, no categories.
            assertThat(result.getSavedLists()).isEmpty();
            assertThat(result.getCategories()).isEmpty();
        }
    }

    // ==================== getUserProblemLists (Phase B) ====================

    @Nested
    @DisplayName("getUserProblemLists()")
    class GetUserProblemListsTests {

        @Test
        @DisplayName("happy path: own + saved + featured + categories all populated")
        void getUserProblemLists_populatesAllFourSections() {
            ProblemList own = listEntity("list-own", "My List");
            own.setAuthorId(OWNER_ID);
            ProblemList featured = listEntity("list-feat", "Featured");
            featured.setIsFeatured(true);
            ProblemList saved = listEntity("list-saved", "Saved By Me");
            saved.setIsPublic(true);

            when(problemListMapper.findByAuthorId(OWNER_ID)).thenReturn(List.of(own));
            when(problemListMapper.findFeatured()).thenReturn(List.of(featured));
            ProblemListBookmark bookmark = new ProblemListBookmark();
            bookmark.setListId("list-saved");
            bookmark.setUserId(OWNER_ID);
            when(problemListBookmarkMapper.findByUserId(OWNER_ID)).thenReturn(List.of(bookmark));
            when(problemListMapper.findById("list-saved")).thenReturn(Optional.of(saved));
            ProblemListCategory cat = new ProblemListCategory();
            cat.setId("cat-1");
            cat.setUserId(OWNER_ID);
            cat.setName("Favorites");
            when(problemListCategoryMapper.findByUserId(OWNER_ID)).thenReturn(List.of(cat));
            // Conversion-helper enrichment collaborators
            when(problemListProblemMapper.countByListId(anyString())).thenReturn(0L);
            when(problemListBookmarkMapper.findByCategoryId("cat-1")).thenReturn(Collections.emptyList());
            when(problemListBookmarkMapper.existsByUserIdAndListId(eq(OWNER_ID), anyString())).thenReturn(false);

            UserProblemListsVO result = projection.getUserProblemLists(OWNER_ID);

            assertThat(result.getOwnLists()).hasSize(1);
            assertThat(result.getOwnLists().get(0).getId()).isEqualTo("list-own");
            assertThat(result.getSavedLists()).hasSize(1);
            assertThat(result.getSavedLists().get(0).getId()).isEqualTo("list-saved");
            // Bookmark sets isSaved=true on the saved path explicitly.
            assertThat(result.getSavedLists().get(0).getIsSaved()).isTrue();
            assertThat(result.getFeaturedLists()).hasSize(1);
            assertThat(result.getFeaturedLists().get(0).getId()).isEqualTo("list-feat");
            assertThat(result.getCategories()).hasSize(1);
            assertThat(result.getCategories().get(0).getId()).isEqualTo("cat-1");
        }
    }

    // ==================== getListOverview (Phase B: access-control smoke tests) ====================

    @Nested
    @DisplayName("getListOverview()")
    class GetListOverviewTests {

        private static final String LIST_ID = "list-overview-1";

        @Test
        @DisplayName("not found: throws BusinessException(PROBLEM_LIST_NOT_FOUND) when findById is empty")
        void getListOverview_throwsNotFoundWhenListMissing() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projection.getListOverview(LIST_ID, OWNER_ID, "en"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_LIST_NOT_FOUND));
        }

        @Test
        @DisplayName("private + viewer not owner: throws BusinessException(PROBLEM_LIST_PRIVATE)")
        void getListOverview_throwsPrivateWhenListNotPublicAndViewerNotOwner() {
            ProblemList list = listEntity(LIST_ID, "Private List");
            list.setIsPublic(false);
            list.setAuthorId("someone-else");
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));

            assertThatThrownBy(() -> projection.getListOverview(LIST_ID, OWNER_ID, "en"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_LIST_PRIVATE));
        }
    }

    // ==================== Conversion helpers (Phase B) ====================

    @Nested
    @DisplayName("Conversion helpers — toSummaryVO / toSummaryVOWithSavedStatus / toCategorySummaryVO")
    class ConversionHelperTests {

        @Test
        @DisplayName("toSummaryVO: enriches with problem count + author info")
        void toSummaryVO_enrichesWithProblemCountAndAuthor() {
            ProblemList list = listEntity("list-1", "Summary List");
            list.setAuthorId(OWNER_ID);
            when(problemListProblemMapper.countByListId("list-1")).thenReturn(7L);
            User author = new User();
            author.setName("Alice");
            author.setUsername("alice");
            when(userMapper.selectById(OWNER_ID)).thenReturn(author);

            ProblemListSummaryVO vo = projection.toSummaryVO(list);

            assertThat(vo.getId()).isEqualTo("list-1");
            assertThat(vo.getName()).isEqualTo("Summary List");
            assertThat(vo.getProblemCount()).isEqualTo(7);
            assertThat(vo.getAuthorName()).isEqualTo("Alice");
            assertThat(vo.getAuthorUsername()).isEqualTo("alice");
            verify(problemListProblemMapper).countByListId("list-1");
            verify(userMapper).selectById(OWNER_ID);
        }

        @Test
        @DisplayName("toSummaryVOWithSavedStatus: sets isSaved=true when bookmark exists")
        void toSummaryVOWithSavedStatus_marksSavedWhenBookmarkExists() {
            ProblemList list = listEntity("list-1", "Saved List");
            list.setAuthorId(OWNER_ID);
            when(problemListProblemMapper.countByListId("list-1")).thenReturn(0L);
            when(problemListBookmarkMapper.existsByUserIdAndListId(OWNER_ID, "list-1"))
                    .thenReturn(true);

            ProblemListSummaryVO vo = projection.toSummaryVOWithSavedStatus(list, OWNER_ID);

            assertThat(vo.getIsSaved()).isTrue();
            verify(problemListBookmarkMapper).existsByUserIdAndListId(OWNER_ID, "list-1");
        }

        @Test
        @DisplayName("toSummaryVOWithSavedStatus: sets isSaved=false when userId is null (unauthenticated)")
        void toSummaryVOWithSavedStatus_unauthenticatedSetsFalse() {
            ProblemList list = listEntity("list-1", "Anon List");
            when(problemListProblemMapper.countByListId("list-1")).thenReturn(0L);

            ProblemListSummaryVO vo = projection.toSummaryVOWithSavedStatus(list, null);

            assertThat(vo.getIsSaved()).isFalse();
            // No bookmark lookup when userId is null.
            verify(problemListBookmarkMapper, never())
                    .existsByUserIdAndListId(any(), any());
        }

        @Test
        @DisplayName("toCategorySummaryVO: enriches with list count from bookmark mapper")
        void toCategorySummaryVO_enrichesWithListCount() {
            ProblemListCategory cat = new ProblemListCategory();
            cat.setId("cat-1");
            cat.setUserId(OWNER_ID);
            cat.setName("Favorites");
            cat.setIcon("star");
            cat.setColor("blue");
            cat.setSortOrder(1);
            // Two bookmarks attached to this category → listCount = 2.
            ProblemListBookmark b1 = new ProblemListBookmark();
            b1.setCategoryId("cat-1");
            ProblemListBookmark b2 = new ProblemListBookmark();
            b2.setCategoryId("cat-1");
            when(problemListBookmarkMapper.findByCategoryId("cat-1"))
                    .thenReturn(Arrays.asList(b1, b2));

            CategorySummaryVO vo = projection.toCategorySummaryVO(cat);

            assertThat(vo.getId()).isEqualTo("cat-1");
            assertThat(vo.getName()).isEqualTo("Favorites");
            assertThat(vo.getIcon()).isEqualTo("star");
            assertThat(vo.getColor()).isEqualTo("blue");
            assertThat(vo.getSortOrder()).isEqualTo(1);
            assertThat(vo.getListCount()).isEqualTo(2);
            verify(problemListBookmarkMapper).findByCategoryId("cat-1");
        }
    }

    // ==================== Shared helper ====================

    private ProblemList listEntity(String id, String name) {
        ProblemList list = new ProblemList();
        list.setId(id);
        list.setName(name);
        list.setAuthorId(OWNER_ID);
        list.setIsPublic(true);
        list.setIsFeatured(false);
        list.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        list.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        return list;
    }
}
