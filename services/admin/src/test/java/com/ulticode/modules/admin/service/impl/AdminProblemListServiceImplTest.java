package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.admin.projection.AdminProblemListProjection;
import com.ulticode.modules.problemlist.service.ProblemListAdminService;
import com.ulticode.modules.problemlist.service.ProblemListService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminProblemListServiceImpl} after the seam
 * extraction (architecture-review candidate #1).
 *
 * <p>Verifies the architectural contract:
 * <ul>
 *   <li>Every mutation path delegates to {@link ProblemListAdminService};
 *       the admin service holds no direct *Mapper dependency for
 *       mutation.</li>
 *   <li>{@link AuditContext} receives both old and new value snapshots for
 *       every audited mutation so {@code AuditAspect} can persist them.</li>
 *   <li>Read paths (paginated list + single detail) keep using the mapper /
 *       projection directly, as documented in the service header.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminProblemListServiceImpl")
class AdminProblemListServiceImplTest {

    private static final String LIST_ID = "list-001";
    private static final String AUTHOR_ID = "user-author";
    private static final String ADMIN_USER_ID = "admin-001";

    @Mock private ProblemListService problemListService;
    @Mock private ProblemListAdminService problemListAdminService;
    @Mock private AdminProblemListProjection adminProblemListProjection;

    private AdminProblemListServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminProblemListServiceImpl(
                problemListService,
                problemListAdminService,
                adminProblemListProjection);
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
    }

    private ProblemList createList() {
        ProblemList list = new ProblemList();
        list.setId(LIST_ID);
        list.setName("Original Name");
        list.setDescription("Original Description");
        list.setAuthorId(AUTHOR_ID);
        list.setIsPublic(false);
        list.setIsFeatured(false);
        list.setBannerTag("original-tag");
        list.setBannerIcon("original-icon");
        list.setBannerTheme("original-theme");
        list.setBannerOrder(1);
        list.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        list.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        return list;
    }

    private ProblemListSummaryVO createSummary() {
        ProblemListSummaryVO vo = new ProblemListSummaryVO();
        vo.setId(LIST_ID);
        vo.setName("Updated Name");
        vo.setDescription("Updated Description");
        vo.setIsPublic(true);
        vo.setIsFeatured(true);
        vo.setBannerTag("updated-tag");
        vo.setBannerTheme("updated-theme");
        vo.setBannerOrder(2);
        return vo;
    }

    private ProblemListSummaryVO echoedSummary(ProblemList list) {
        ProblemListSummaryVO vo = new ProblemListSummaryVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setIsPublic(list.getIsPublic());
        vo.setIsFeatured(list.getIsFeatured());
        vo.setBannerTag(list.getBannerTag());
        vo.setBannerTheme(list.getBannerTheme());
        vo.setBannerOrder(list.getBannerOrder());
        return vo;
    }

    // ==================== getProblemLists (read path: mapper retained) ====================

    @Nested
    @DisplayName("getProblemLists()")
    class GetProblemListsTests {

        @Test
        @DisplayName("should delegate the page read to ProblemListProjection.findAdminLists")
        void delegatesToProjectionFindAdminLists() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            ProblemListSummaryVO vo = createSummary();
            PageResult<ProblemListSummaryVO> projectionResult =
                    PageResult.of(List.of(vo), 1L, 1, 10);
            when(adminProblemListProjection.findAdminLists(query)).thenReturn(projectionResult);

            var result = service.getProblemLists(query);

            // Thin pass-through after the candidate #3 rewire: the admin
            // service owns audit context, the projection owns page assembly
            // + entity→VO projection. The returned envelope is the
            // projection's, unchanged.
            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getItems()).containsExactly(vo);
            verify(adminProblemListProjection).findAdminLists(query);
        }

        @Test
        @DisplayName("should default sort to createdAt when sortBy is blank (regression: projection still receives the raw query)")
        void defaultsSortBy() {
            AdminProblemListQueryDTO query = new AdminProblemListQueryDTO();
            query.setPage(1);
            query.setLimit(10);
            query.setSortBy(null);
            query.setSortOrder(null);

            PageResult<ProblemListSummaryVO> empty =
                    PageResult.of(Collections.emptyList(), 0L, 1, 10);
            when(adminProblemListProjection.findAdminLists(query)).thenReturn(empty);

            var result = service.getProblemLists(query);

            assertThat(result.getItems()).isEmpty();
            verify(adminProblemListProjection).findAdminLists(query);
        }
    }

    // ==================== getProblemList (read path: mapper retained) ====================

    @Nested
    @DisplayName("getProblemList()")
    class GetProblemListTests {

        @Test
        @DisplayName("should delegate the detail read to ProblemListProjection.getAdminListDetail")
        void delegatesToProjectionGetAdminListDetail() {
            ProblemListDetailVO detail = new ProblemListDetailVO();
            when(adminProblemListProjection.getAdminListDetail(LIST_ID)).thenReturn(detail);

            ProblemListDetailVO result = service.getProblemList(LIST_ID);

            // Intent-level read after the candidate #3 rewire: the projection
            // owns the entity load (404 on missing) + admin-detail shaping.
            // The admin service no longer calls findEntityById or any
            // conversion helper.
            assertThat(result).isSameAs(detail);
            verify(adminProblemListProjection).getAdminListDetail(LIST_ID);
            verify(problemListAdminService, never()).findEntityById(any());
        }

        @Test
        @DisplayName("should surface PROBLEM_LIST_NOT_FOUND when the projection cannot find the list")
        void notFound() {
            when(adminProblemListProjection.getAdminListDetail(LIST_ID))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

            assertThatThrownBy(() -> service.getProblemList(LIST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    // ==================== createProblemList (delegates to user-owned seam) ====================

    @Nested
    @DisplayName("createProblemList()")
    class CreateProblemListTests {

        @Test
        @DisplayName("should delegate to ProblemListService.createList")
        void delegatesToCreateList() {
            CreateProblemListDTO dto = new CreateProblemListDTO();
            dto.setName("New");
            ProblemListSummaryVO vo = createSummary();
            when(problemListService.createList(ADMIN_USER_ID, dto)).thenReturn(vo);

            ProblemListSummaryVO result = service.createProblemList(dto, ADMIN_USER_ID);

            assertThat(result).isSameAs(vo);
            verify(problemListService).createList(ADMIN_USER_ID, dto);
        }
    }

    // ==================== updateProblemList (seam delegation + audit context) ====================

    @Nested
    @DisplayName("updateProblemList()")
    class UpdateProblemListTests {

        @Test
        @DisplayName("should read pre-state via service, delegate mutation, and capture audit snapshot")
        void delegatesMutationToService() {
            ProblemList existing = createList();
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);
            ProblemListSummaryVO vo = createSummary();
            when(problemListAdminService.adminUpdateProblemList(eq(LIST_ID), any(UpdateProblemListDTO.class)))
                    .thenReturn(vo);

            UpdateProblemListDTO dto = new UpdateProblemListDTO();
            dto.setName("Updated Name");
            ProblemListSummaryVO result = service.updateProblemList(LIST_ID, dto, ADMIN_USER_ID);

            assertThat(result).isSameAs(vo);
            verify(problemListAdminService).findEntityById(LIST_ID);
            verify(problemListAdminService).adminUpdateProblemList(LIST_ID, dto);

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("name", "Original Name");
            assertThat(oldValues).containsEntry("isPublic", false);
            assertThat(oldValues).containsEntry("isFeatured", false);

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("name", "Updated Name");
            assertThat(newValues).containsEntry("isPublic", true);
            assertThat(newValues).containsEntry("isFeatured", true);
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when service findEntityById rejects")
        void notFoundBubbles() {
            when(problemListAdminService.findEntityById(LIST_ID))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

            UpdateProblemListDTO dto = new UpdateProblemListDTO();
            dto.setName("X");

            assertThatThrownBy(() -> service.updateProblemList(LIST_ID, dto, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
            verify(problemListAdminService, never()).adminUpdateProblemList(any(), any());
        }
    }

    // ==================== deleteProblemList (seam delegation + audit context) ====================

    @Nested
    @DisplayName("deleteProblemList()")
    class DeleteProblemListTests {

        @Test
        @DisplayName("should read entity via service, capture audit snapshot, and delegate delete")
        void delegatesMutationToService() {
            ProblemList existing = createList();
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);

            service.deleteProblemList(LIST_ID, ADMIN_USER_ID);

            verify(problemListAdminService).findEntityById(LIST_ID);
            verify(problemListAdminService).adminDeleteProblemList(LIST_ID);

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("name", "Original Name");
            assertThat(oldValues).containsEntry("authorId", AUTHOR_ID);
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when entity missing")
        void notFoundBubbles() {
            when(problemListAdminService.findEntityById(LIST_ID))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

            assertThatThrownBy(() -> service.deleteProblemList(LIST_ID, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));
            verify(problemListAdminService, never()).adminDeleteProblemList(any());
        }
    }

    // ==================== updateListProblems (seam delegation + audit context) ====================

    @Nested
    @DisplayName("updateListProblems()")
    class UpdateListProblemsTests {

        @Test
        @DisplayName("should validate problems, delegate replace to service, capture count in audit context")
        void delegatesReplaceToService() {
            ProblemList existing = createList();
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);

            UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
            UpdateProblemListProblemsDTO.ProblemEntry e1 = new UpdateProblemListProblemsDTO.ProblemEntry();
            e1.setProblemId(1L);
            e1.setSortOrder(0);
            UpdateProblemListProblemsDTO.ProblemEntry e2 = new UpdateProblemListProblemsDTO.ProblemEntry();
            e2.setProblemId(2L);
            e2.setSortOrder(1);
            dto.setProblems(List.of(e1, e2));

            service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID);

            verify(problemListAdminService).findEntityById(LIST_ID);
            verify(problemListAdminService).adminReplaceListProblems(LIST_ID, dto);

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("updatedProblems", 2);
        }

        @Test
        @DisplayName("should reject null problems before delegating to the seam")
        void rejectsNullProblems() {
            ProblemList existing = createList();
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);

            UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
            dto.setProblems(null);

            assertThatThrownBy(() -> service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AdminErrorCode.VALIDATION_FAILED));
            verify(problemListAdminService, never()).adminReplaceListProblems(any(), any());
        }

        @Test
        @DisplayName("should delegate replace-problems via ProblemListAdminService without direct mapper mutation")
        void noDirectProblemMapperMutation() {
            ProblemList existing = createList();
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);

            UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
            dto.setProblems(Collections.emptyList());

            service.updateListProblems(LIST_ID, dto, ADMIN_USER_ID);

            // Confirm no direct interaction with the user-facing mappers — they are
            // not even injected into AdminProblemListServiceImpl after the seam
            // extraction; this assertion documents the seam contract.
            verify(problemListAdminService).adminReplaceListProblems(LIST_ID, dto);
        }
    }

    // ==================== updateBasicInfo (seam delegation + audit context) ====================

    @Nested
    @DisplayName("updateBasicInfo()")
    class UpdateBasicInfoTests {

        @Test
        @DisplayName("should capture pre-state, delegate mutation, capture new state in audit context")
        void delegatesAndAudits() {
            ProblemList existing = createList();
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);
            ProblemListSummaryVO vo = echoedSummary(existing);
            vo.setName("Admin Name");
            vo.setDescription("Admin Description");
            when(problemListAdminService.adminUpdateBasicInfo(eq(LIST_ID), any(UpdateBasicInfoDTO.class)))
                    .thenReturn(vo);

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
            dto.setName("Admin Name");
            dto.setDescription("Admin Description");

            ProblemListSummaryVO result = service.updateBasicInfo(LIST_ID, ADMIN_USER_ID, dto);

            assertThat(result).isSameAs(vo);
            verify(problemListAdminService).adminUpdateBasicInfo(LIST_ID, dto);

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("name", "Original Name");
            assertThat(oldValues).containsEntry("description", "Original Description");

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("name", "Admin Name");
            assertThat(newValues).containsEntry("description", "Admin Description");
        }
    }

    // ==================== updateVisibility (seam delegation + audit context) ====================

    @Nested
    @DisplayName("updateVisibility()")
    class UpdateVisibilityTests {

        @Test
        @DisplayName("should capture pre-state, delegate mutation, capture new state in audit context")
        void delegatesAndAudits() {
            ProblemList existing = createList();
            existing.setIsPublic(false);
            existing.setIsFeatured(false);
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);

            ProblemListSummaryVO vo = createSummary();
            vo.setIsPublic(true);
            vo.setIsFeatured(true);
            when(problemListAdminService.adminUpdateVisibility(eq(LIST_ID), any(UpdateVisibilityDTO.class)))
                    .thenReturn(vo);

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
            dto.setIsPublic(true);
            dto.setIsFeatured(true);

            ProblemListSummaryVO result = service.updateVisibility(LIST_ID, ADMIN_USER_ID, dto);

            assertThat(result).isSameAs(vo);
            verify(problemListAdminService).adminUpdateVisibility(LIST_ID, dto);

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("isPublic", false);
            assertThat(oldValues).containsEntry("isFeatured", false);

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("isPublic", true);
            assertThat(newValues).containsEntry("isFeatured", true);
        }
    }

    // ==================== updateBanner (seam delegation + audit context) ====================

    @Nested
    @DisplayName("updateBanner()")
    class UpdateBannerTests {

        @Test
        @DisplayName("should capture pre-state, delegate mutation, capture new state in audit context")
        void delegatesAndAudits() {
            ProblemList existing = createList();
            when(problemListAdminService.findEntityById(LIST_ID)).thenReturn(existing);
            ProblemListSummaryVO vo = createSummary();
            when(problemListAdminService.adminUpdateBanner(eq(LIST_ID), any(UpdateBannerDTO.class)))
                    .thenReturn(vo);

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("updated-tag");
            dto.setBannerTheme("updated-theme");
            dto.setBannerOrder(2);

            ProblemListSummaryVO result = service.updateBanner(LIST_ID, ADMIN_USER_ID, dto);

            assertThat(result).isSameAs(vo);
            verify(problemListAdminService).adminUpdateBanner(LIST_ID, dto);

            Map<String, Object> oldValues = AuditContext.getOldValues();
            assertThat(oldValues).containsEntry("bannerTag", "original-tag");
            assertThat(oldValues).containsEntry("bannerTheme", "original-theme");
            assertThat(oldValues).containsEntry("bannerOrder", 1);

            Map<String, Object> newValues = AuditContext.getNewValues();
            assertThat(newValues).containsEntry("bannerTag", "updated-tag");
            assertThat(newValues).containsEntry("bannerTheme", "updated-theme");
            assertThat(newValues).containsEntry("bannerOrder", 2);
        }
    }

    // ==================== Architectural invariant test ====================

    @Test
    @DisplayName("AdminProblemListServiceImpl must not hold a ProblemListProblemMapper dependency")
    void architecturalInvariant_noProblemMapperDependency() throws NoSuchMethodException {
        // The seam extraction removes ProblemListProblemMapper AND
        // ProblemListMapper from the admin service constructor entirely.
        // This test pins that contract: the admin side depends only on
        // ProblemListService (createList), ProblemListAdminService (mutation
        // bypass seam with audit snapshots), and AdminProblemListProjection
        // (read-side intent reads) — never the problem-relation mapper nor
        // the raw ProblemListMapper.
        java.lang.reflect.Constructor<?> ctor = AdminProblemListServiceImpl.class.getDeclaredConstructors()[0];
        Class<?>[] paramTypes = ctor.getParameterTypes();
        assertThat(paramTypes).containsOnly(
                ProblemListService.class,
                ProblemListAdminService.class,
                AdminProblemListProjection.class);
        assertThat(paramTypes).doesNotContain(
                com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper.class,
                com.ulticode.modules.problemlist.mapper.ProblemListMapper.class);
    }

    @Test
    @DisplayName("every audited admin mutation method is annotated with @Audited")
    void auditedAnnotationsPresent() throws NoSuchMethodException {
        String[] auditedMethods = {
                "updateProblemList",
                "deleteProblemList",
                "updateListProblems",
                "updateBasicInfo",
                "updateVisibility",
                "updateBanner"
        };
        for (String name : auditedMethods) {
            var method = AdminProblemListServiceImpl.class.getDeclaredMethod(name, parameterTypes(name));
            var audited = method.getAnnotation(com.ulticode.common.annotation.Audited.class);
            assertThat(audited)
                    .as("@Audited missing on AdminProblemListServiceImpl.%s", name)
                    .isNotNull();
            if (name.equals("deleteProblemList") || name.equals("updateListProblems")) {
                assertThat(audited.userIdFrom()).isEqualTo("userId");
                assertThat(audited.entityIdFrom()).isEqualTo("id");
            }
        }
    }

    private static Class<?>[] parameterTypes(String methodName) {
        return switch (methodName) {
            case "updateProblemList" -> new Class<?>[]{String.class, UpdateProblemListDTO.class, String.class};
            case "deleteProblemList" -> new Class<?>[]{String.class, String.class};
            case "updateListProblems" -> new Class<?>[]{String.class, UpdateProblemListProblemsDTO.class, String.class};
            case "updateBasicInfo" -> new Class<?>[]{String.class, String.class, UpdateBasicInfoDTO.class};
            case "updateVisibility" -> new Class<?>[]{String.class, String.class, UpdateVisibilityDTO.class};
            case "updateBanner" -> new Class<?>[]{String.class, String.class, UpdateBannerDTO.class};
            default -> throw new IllegalArgumentException("unknown method: " + methodName);
        };
    }
}
