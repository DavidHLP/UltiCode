package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminProblemListProjection} &mdash; the
 * admin-side deep module lifted out per architecture-review 2026-07-19
 * candidate #3. Mirrors the {@code DefaultAdminContestProjection} /
 * {@code DefaultAdminSubmissionProjection} / {@code DefaultAdminUserProjection}
 * shape: admin &rarr; feature dependency direction (mapper + entity only),
 * paginated read owns its own {@code LambdaQueryWrapper}, single-detail
 * read owns the entity load.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminProblemListProjection")
class DefaultAdminProblemListProjectionTest {

    @Mock private ProblemListMapper problemListMapper;
    @Mock private ProblemListProblemMapper problemListProblemMapper;
    @Mock private ProblemMapper problemMapper;
    @Mock private UserMapper userMapper;

    private static final String OWNER_ID = "user-001";

    private DefaultAdminProblemListProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminProblemListProjection(
                problemListMapper, problemListProblemMapper, problemMapper, userMapper);
    }

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
    }

    @Nested
    @DisplayName("getAdminListDetail()")
    class GetAdminListDetailTests {

        private static final String LIST_ID = "list-detail-1";

        @Test
        @DisplayName("happy path: loads entity + shapes admin detail (no viewer, no categories)")
        void getAdminListDetail_loadsAndProjects() {
            ProblemList list = adminListEntity(LIST_ID, "Detail List");
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            when(problemListProblemMapper.findByListId(LIST_ID))
                    .thenReturn(Collections.emptyList());
            User author = new User();
            author.setName("Bob");
            author.setUsername("bob");
            when(userMapper.selectById(OWNER_ID)).thenReturn(author);

            ProblemListDetailVO vo = projection.getAdminListDetail(LIST_ID);

            assertThat(vo.getId()).isEqualTo(LIST_ID);
            assertThat(vo.getName()).isEqualTo("Detail List");
            assertThat(vo.getIsOwner()).isFalse();
            assertThat(vo.getIsSaved()).isFalse();
            assertThat(vo.getViewer()).isNull();
            assertThat(vo.getCategories()).isEmpty();
            assertThat(vo.getProblems()).isEmpty();
            assertThat(vo.getStats().getTotalCount()).isEqualTo(0);
            assertThat(vo.getStats().getSolvedCount()).isEqualTo(0);
            assertThat(vo.getAuthorName()).isEqualTo("Bob");
            verify(problemListMapper).findById(LIST_ID);
        }

        @Test
        @DisplayName("not found: throws BusinessException(PROBLEM_LIST_NOT_FOUND) when findById is empty")
        void getAdminListDetail_throwsNotFoundWhenMissing() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projection.getAdminListDetail(LIST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
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
