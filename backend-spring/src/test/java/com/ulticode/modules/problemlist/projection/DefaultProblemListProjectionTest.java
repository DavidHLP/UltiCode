package com.ulticode.modules.problemlist.projection;

import com.ulticode.modules.problemlist.dto.UserListsForProblemVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
}
