package com.ulticode.modules.problemlist.projection;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problemlist.dto.CategorySummaryVO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UserListsForProblemVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
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
    @Mock private ProblemMapper problemMapper;
    @Mock private UserMapper userMapper;

    private static final String OWNER_ID = "user-001";

    private DefaultProblemListProjection projection;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        projection = new DefaultProblemListProjection(
                problemListMapper, problemListProblemMapper, problemListCategoryMapper,
                problemListBookmarkMapper, problemMapper, userMapper);
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

    // ==================== findAdminLists (admin intent read, candidate #3) ====================

    @Nested
    @DisplayName("findAdminLists()")
    class FindAdminListsTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("happy path: selectPage records + total flow through as a paged summary result")
        void findAdminLists_returnsPagedResultWithRecordsAndTotal() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            query.setPage(1);
            query.setLimit(10);
            query.setSearch("algo");
            query.setIsPublic(true);

            ProblemList entity = adminListEntity("list-1", "Algo 101");
            Page<ProblemList> mapperPage = new Page<>(1, 10);
            mapperPage.setRecords(List.of(entity));
            mapperPage.setTotal(7L);
            when(problemListMapper.selectPage(any(Page.class), any()))
                    .thenReturn(mapperPage);
            // toSummaryVO enrichment collaborators
            when(problemListProblemMapper.countByListId("list-1")).thenReturn(3L);
            User author = new User();
            author.setName("Alice");
            author.setUsername("alice");
            when(userMapper.selectById(OWNER_ID)).thenReturn(author);

            PageResult<ProblemListSummaryVO> result = projection.findAdminLists(query);

            assertThat(result.getTotal()).isEqualTo(7L);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getItems()).hasSize(1);
            ProblemListSummaryVO vo = result.getItems().get(0);
            assertThat(vo.getId()).isEqualTo("list-1");
            assertThat(vo.getName()).isEqualTo("Algo 101");
            assertThat(vo.getProblemCount()).isEqualTo(3);
            assertThat(vo.getAuthorName()).isEqualTo("Alice");
            // The wrapper-build → selectPage path was exercised (the wrapper
            // itself is opaque; the regression guard is the call count).
            verify(problemListMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("empty page: result items empty, total zero")
        void findAdminLists_emptyResult() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            query.setPage(2);
            query.setLimit(5);

            Page<ProblemList> empty = new Page<>(2, 5);
            empty.setRecords(Collections.emptyList());
            empty.setTotal(0L);
            when(problemListMapper.selectPage(any(Page.class), any())).thenReturn(empty);

            PageResult<ProblemListSummaryVO> result = projection.findAdminLists(query);

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getPageSize()).isEqualTo(5);
        }

        private ProblemList adminListEntity(String id, String name) {
            ProblemList list = new ProblemList();
            list.setId(id);
            list.setName(name);
            list.setAuthorId(OWNER_ID);
            list.setIsPublic(true);
            list.setIsFeatured(false);
            return list;
        }
    }

    // ==================== getAdminListDetail (admin intent read, candidate #3) ====================

    @Nested
    @DisplayName("getAdminListDetail()")
    class GetAdminListDetailTests {

        private static final String LIST_ID = "list-detail-1";

        @Test
        @DisplayName("happy path: loads entity + shapes admin detail (no viewer, no categories)")
        void getAdminListDetail_loadsAndProjects() {
            ProblemList list = adminListEntity(LIST_ID, "Detail List");
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            // No relations → empty problems + zero-stats short-circuit
            when(problemListProblemMapper.findByListId(LIST_ID))
                    .thenReturn(Collections.emptyList());
            User author = new User();
            author.setName("Bob");
            author.setUsername("bob");
            when(userMapper.selectById(OWNER_ID)).thenReturn(author);

            ProblemListDetailVO vo = projection.getAdminListDetail(LIST_ID);

            assertThat(vo.getId()).isEqualTo(LIST_ID);
            assertThat(vo.getName()).isEqualTo("Detail List");
            // Admin-view contract: never owner, never saved, no viewer state.
            assertThat(vo.getIsOwner()).isFalse();
            assertThat(vo.getIsSaved()).isFalse();
            assertThat(vo.getViewer()).isNull();
            assertThat(vo.getCategories()).isEmpty();
            // Empty relations → empty problems + zero stats.
            assertThat(vo.getProblems()).isEmpty();
            assertThat(vo.getStats().getTotalCount()).isEqualTo(0);
            assertThat(vo.getStats().getSolvedCount()).isEqualTo(0);
            // Author enrichment came through.
            assertThat(vo.getAuthorName()).isEqualTo("Bob");
            // The projection owns the load now (admin service no longer does).
            verify(problemListMapper).findById(LIST_ID);
        }

        @Test
        @DisplayName("not found: throws BusinessException(PROBLEM_LIST_NOT_FOUND) when findById is empty")
        void getAdminListDetail_throwsNotFoundWhenMissing() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projection.getAdminListDetail(LIST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_LIST_NOT_FOUND));
        }

        private ProblemList adminListEntity(String id, String name) {
            ProblemList list = new ProblemList();
            list.setId(id);
            list.setName(name);
            list.setAuthorId(OWNER_ID);
            list.setIsPublic(true);
            list.setIsFeatured(false);
            return list;
        }
    }
}
