package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.app.api.service.ProblemListSearchReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminProblemListProjection} after the
 * ADMIN-005 rewiring &mdash; the admin-side deep module backed by the
 * entity-free app-api read ports ({@link ProblemListSearchReadPort} /
 * {@link ProblemListChainReadPort}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminProblemListProjection")
class DefaultAdminProblemListProjectionTest {

    @Mock private ProblemListSearchReadPort problemListSearchReadPort;
    @Mock private ProblemListChainReadPort problemListChainReadPort;
    @Mock private AdminUserEnricher userEnricher;

    private static final String OWNER_ID = "user-001";

    private DefaultAdminProblemListProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminProblemListProjection(
                problemListSearchReadPort, problemListChainReadPort, userEnricher);
    }

    @Nested
    @DisplayName("findAdminLists()")
    class FindAdminListsTests {

        @Test
        @DisplayName("happy path: remote paged result flows through with author enrichment")
        void findAdminLists_returnsPagedResultWithEnrichment() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            query.setPage(1);
            query.setLimit(10);
            query.setSearch("algo");
            query.setIsPublic(true);

            ProblemListSummaryDTO dto = summary("list-1", "Algo 101");
            when(problemListSearchReadPort.searchAdminLists(
                    eq("algo"), eq(null), eq(true), eq("createdAt"), eq("desc"), eq(1), eq(10)))
                    .thenReturn(PageResult.of(List.of(dto), 7L, 1, 10));
            when(userEnricher.enrichOne(OWNER_ID)).thenReturn(
                    new AdminUserSummary(OWNER_ID, "alice", "role1", "Alice", "avatar1", "alice@example.com"));

            PageResult<ProblemListSummaryDTO> result = projection.findAdminLists(query);

            assertThat(result.getTotal()).isEqualTo(7L);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getItems()).hasSize(1);
            ProblemListSummaryDTO vo = result.getItems().get(0);
            assertThat(vo.getId()).isEqualTo("list-1");
            assertThat(vo.getName()).isEqualTo("Algo 101");
            assertThat(vo.getAuthorName()).isEqualTo("Alice");
            assertThat(vo.getAuthorUsername()).isEqualTo("alice");
            verify(problemListSearchReadPort).searchAdminLists(
                    eq("algo"), eq(null), eq(true), eq("createdAt"), eq("desc"), eq(1), eq(10));
        }

        @Test
        @DisplayName("empty page: result items empty, total zero")
        void findAdminLists_emptyResult() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            query.setPage(2);
            query.setLimit(5);

            when(problemListSearchReadPort.searchAdminLists(
                    any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(PageResult.of(Collections.emptyList(), 0L, 2, 5));

            PageResult<ProblemListSummaryDTO> result = projection.findAdminLists(query);

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
        @DisplayName("happy path: shapes admin detail (no viewer, no categories, stats computed)")
        void getAdminListDetail_loadsAndShapes() {
            ProblemListDetailDTO remote = detail(LIST_ID, "Detail List");
            remote.setProblems(List.of(
                    new ProblemListDetailDTO.ProblemInListDTO(
                            1L, "two-sum", "Two Sum", "easy", "solved",
                            0, null, new BigDecimal("85.0"), false, true, List.of()),
                    new ProblemListDetailDTO.ProblemInListDTO(
                            2L, "add-two", "Add Two", "medium", "attempted",
                            1, null, new BigDecimal("50.0"), false, false, List.of())));
            when(problemListChainReadPort.findAdminDetail(LIST_ID)).thenReturn(remote);
            when(userEnricher.enrichOne(OWNER_ID)).thenReturn(
                    new AdminUserSummary(OWNER_ID, "bob", "role2", "Bob", "avatar2", "bob@example.com"));

            ProblemListDetailDTO vo = projection.getAdminListDetail(LIST_ID);

            assertThat(vo.getId()).isEqualTo(LIST_ID);
            assertThat(vo.getName()).isEqualTo("Detail List");
            assertThat(vo.getIsOwner()).isFalse();
            assertThat(vo.getIsSaved()).isFalse();
            assertThat(vo.getViewer()).isNull();
            assertThat(vo.getCategories()).isEmpty();
            assertThat(vo.getAuthorName()).isEqualTo("Bob");
            assertThat(vo.getStats().getTotalCount()).isEqualTo(2);
            assertThat(vo.getStats().getSolvedCount()).isEqualTo(1);
            assertThat(vo.getStats().getAttemptedCount()).isEqualTo(1);
            assertThat(vo.getStats().getTodoCount()).isEqualTo(0);
            assertThat(vo.getStats().getProgress()).isEqualTo(50.0);
            verify(problemListChainReadPort).findAdminDetail(LIST_ID);
        }

        @Test
        @DisplayName("not found: throws BusinessException(PROBLEM_LIST_NOT_FOUND) when the remote read is null")
        void getAdminListDetail_throwsNotFoundWhenMissing() {
            when(problemListChainReadPort.findAdminDetail(LIST_ID)).thenReturn(null);

            assertThatThrownBy(() -> projection.getAdminListDetail(LIST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    private ProblemListSummaryDTO summary(String id, String name) {
        ProblemListSummaryDTO dto = new ProblemListSummaryDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setAuthorId(OWNER_ID);
        dto.setIsPublic(true);
        dto.setIsFeatured(false);
        dto.setProblemCount(3);
        return dto;
    }

    private ProblemListDetailDTO detail(String id, String name) {
        ProblemListDetailDTO dto = new ProblemListDetailDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setAuthorId(OWNER_ID);
        dto.setIsPublic(true);
        dto.setIsFeatured(false);
        return dto;
    }
}
