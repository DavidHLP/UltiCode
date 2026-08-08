package com.ulticode.modules.problemlist.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ProblemListErrorCode;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.app.api.service.ProblemExistencePort;
import com.ulticode.modules.problemlist.projection.ProblemListProjection;
import com.ulticode.modules.problemlist.service.impl.ProblemListServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link ProblemListService} write state machine.
 *
 * <p>The read-cluster cases (getUserListsForProblem) moved to
 * {@link com.ulticode.modules.problemlist.projection.DefaultProblemListProjectionTest}
 * when the read paths were lifted into {@link ProblemListProjection}. What
 * remains here is the list mutation logic: updateBasicInfo / updateVisibility /
 * updateBanner / forkList / addProblem. The mocked projection echoes the list
 * entity into the summary VO so the write-path assertions still observe the
 * entity built by the service without depending on projection internals.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemListService")
class ProblemListServiceTest {

    private static final String LIST_ID = "list-001";
    private static final String OWNER_ID = "user-001";
    private static final String OTHER_USER_ID = "user-002";

    @Mock private ProblemListMapper problemListMapper;
    @Mock private ProblemListProblemMapper problemListProblemMapper;
    @Mock private ProblemListCategoryMapper problemListCategoryMapper;
    @Mock private ProblemListBookmarkMapper problemListBookmarkMapper;
    @Mock private ProblemExistencePort problemExistencePort;
    @Mock private ProblemListProjection problemListProjection;

    private ProblemListServiceImpl problemListService;

    @BeforeEach
    void setUp() {
        problemListService = new ProblemListServiceImpl(
                problemListMapper,
                problemListProblemMapper,
                problemListCategoryMapper,
                problemListBookmarkMapper,
                problemExistencePort,
                problemListProjection
        );

        // Echo the list entity into the summary VO so write-path assertions on
        // the returned VO observe the entity built by the service. Lenient
        // because not every test triggers a projection.toXxx return (the
        // guard-rejection and void-return paths do not).
        lenient().when(problemListProjection.toSummaryVO(any())).thenAnswer(inv -> {
            ProblemList l = inv.getArgument(0);
            return echoSummary(l);
        });
        lenient().when(problemListProjection.toSummaryVOWithSavedStatus(any(), any())).thenAnswer(inv -> {
            ProblemList l = inv.getArgument(0);
            return echoSummary(l);
        });
    }

    private ProblemListSummaryVO echoSummary(ProblemList l) {
        ProblemListSummaryVO vo = new ProblemListSummaryVO();
        vo.setId(l.getId());
        vo.setName(l.getName());
        vo.setDescription(l.getDescription());
        vo.setAuthorId(l.getAuthorId());
        vo.setIsPublic(l.getIsPublic());
        vo.setIsFeatured(l.getIsFeatured());
        vo.setBannerTag(l.getBannerTag());
        vo.setBannerIcon(l.getBannerIcon());
        vo.setBannerTheme(l.getBannerTheme());
        vo.setBannerOrder(l.getBannerOrder());
        return vo;
    }

    private ProblemList createProblemList() {
        ProblemList list = new ProblemList();
        list.setId(LIST_ID);
        list.setName("Original Name");
        list.setDescription("Original Description");
        list.setAuthorId(OWNER_ID);
        list.setIsPublic(false);
        list.setIsFeatured(false);
        list.setBannerTag("original-tag");
        list.setBannerIcon("original-icon");
        list.setBannerTheme("original-theme");
        list.setBannerOrder(1);
        list.setVersion(1);
        list.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        list.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        return list;
    }

    // ==================== updateBasicInfo Tests ====================

    @Nested
    @DisplayName("updateBasicInfo()")
    class UpdateBasicInfoTests {

        @Test
        @DisplayName("should update name and description successfully")
        void updateBasicInfo_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
            dto.setName("Updated Name");
            dto.setDescription("Updated Description");

            ProblemListSummaryVO result = problemListService.updateBasicInfo(LIST_ID, OWNER_ID, dto);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Updated Name");
            assertThat(result.getDescription()).isEqualTo("Updated Description");

            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            ProblemList updated = captor.getValue();
            assertThat(updated.getName()).isEqualTo("Updated Name");
            assertThat(updated.getDescription()).isEqualTo("Updated Description");
            assertThat(updated.getAuthorId()).isEqualTo(OWNER_ID);
        }

        @Test
        @DisplayName("should throw exception when user is not the owner")
        void updateBasicInfo_NotOwner() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
            dto.setName("Updated Name");
            dto.setDescription("Updated Description");

            assertThatThrownBy(() -> problemListService.updateBasicInfo(LIST_ID, OTHER_USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_CANNOT_EDIT));

            verify(problemListMapper, never()).updateById(any(ProblemList.class));
        }

        @Test
        @DisplayName("should throw exception when list not found")
        void updateBasicInfo_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
            dto.setName("Updated Name");
            dto.setDescription("Updated Description");

            assertThatThrownBy(() -> problemListService.updateBasicInfo(LIST_ID, OWNER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));

            verify(problemListMapper, never()).updateById(any(ProblemList.class));
        }
    }

    // ==================== updateVisibility Tests ====================

    @Nested
    @DisplayName("updateVisibility()")
    class UpdateVisibilityTests {

        @Test
        @DisplayName("should update isPublic and isFeatured successfully")
        void updateVisibility_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
            dto.setIsPublic(true);
            dto.setIsFeatured(true);

            ProblemListSummaryVO result = problemListService.updateVisibility(LIST_ID, OWNER_ID, dto);

            assertThat(result).isNotNull();
            assertThat(result.getIsPublic()).isTrue();
            assertThat(result.getIsFeatured()).isTrue();

            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            ProblemList updated = captor.getValue();
            assertThat(updated.getIsPublic()).isTrue();
            assertThat(updated.getIsFeatured()).isTrue();
            assertThat(updated.getName()).isEqualTo("Original Name"); // unchanged
        }

        @Test
        @DisplayName("should update only isPublic when isFeatured is null")
        void updateVisibility_OnlyPublic() {
            ProblemList existing = createProblemList();
            existing.setIsFeatured(true);
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
            dto.setIsPublic(true);
            dto.setIsFeatured(null);

            ProblemListSummaryVO result = problemListService.updateVisibility(LIST_ID, OWNER_ID, dto);

            assertThat(result.getIsPublic()).isTrue();
            assertThat(result.getIsFeatured()).isTrue(); // unchanged

            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            assertThat(captor.getValue().getIsFeatured()).isTrue(); // unchanged
        }

        @Test
        @DisplayName("should throw exception when user is not the owner")
        void updateVisibility_NotOwner() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
            dto.setIsPublic(true);

            assertThatThrownBy(() -> problemListService.updateVisibility(LIST_ID, OTHER_USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_CANNOT_EDIT));

            verify(problemListMapper, never()).updateById(any(ProblemList.class));
        }
    }

    // ==================== updateBanner Tests ====================

    @Nested
    @DisplayName("updateBanner()")
    class UpdateBannerTests {

        @Test
        @DisplayName("should update banner fields successfully")
        void updateBanner_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("new-tag");
            dto.setBannerTheme("new-theme");
            dto.setBannerOrder(5);

            ProblemListSummaryVO result = problemListService.updateBanner(LIST_ID, OWNER_ID, dto);

            assertThat(result).isNotNull();
            assertThat(result.getBannerTag()).isEqualTo("new-tag");
            assertThat(result.getBannerTheme()).isEqualTo("new-theme");
            assertThat(result.getBannerOrder()).isEqualTo(5);

            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            ProblemList updated = captor.getValue();
            assertThat(updated.getBannerTag()).isEqualTo("new-tag");
            assertThat(updated.getBannerTheme()).isEqualTo("new-theme");
            assertThat(updated.getBannerOrder()).isEqualTo(5);
        }

        @Test
        @DisplayName("should update only provided banner fields")
        void updateBanner_PartialUpdate() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("new-tag");
            dto.setBannerTheme(null);
            dto.setBannerOrder(null);

            ProblemListSummaryVO result = problemListService.updateBanner(LIST_ID, OWNER_ID, dto);

            assertThat(result.getBannerTag()).isEqualTo("new-tag");

            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            ProblemList updated = captor.getValue();
            assertThat(updated.getBannerTag()).isEqualTo("new-tag");
            assertThat(updated.getBannerTheme()).isEqualTo("original-theme"); // unchanged
            assertThat(updated.getBannerOrder()).isEqualTo(1); // unchanged
        }

        @Test
        @DisplayName("should throw exception when user is not the owner")
        void updateBanner_NotOwner() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("new-tag");

            assertThatThrownBy(() -> problemListService.updateBanner(LIST_ID, OTHER_USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_CANNOT_EDIT));

            verify(problemListMapper, never()).updateById(any(ProblemList.class));
        }
    }

    // ==================== forkList Tests (Task 1 P1 + exception matrix) ====================

    @Nested
    @DisplayName("forkList()")
    class ForkListTests {

        @Test
        @DisplayName("should return full ProblemListSummaryVO (aligned with createList)")
        void forkList_ReturnsFullVO() {
            ProblemList original = createProblemList();
            original.setIsPublic(true);
            ProblemListProblemRelation existingProblemRel = new ProblemListProblemRelation();
            existingProblemRel.setListId(LIST_ID);
            existingProblemRel.setProblemId(7L);
            existingProblemRel.setSortOrder(0);

            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(original));
            when(problemListProblemMapper.findByListId(LIST_ID))
                    .thenReturn(Arrays.asList(existingProblemRel));

            ProblemListSummaryVO result = problemListService.forkList(LIST_ID, OWNER_ID);

            // We do not assert on result.getId() because the mock mapper does not
            // simulate MyBatis-Plus' auto-id assignment. The id contract is verified
            // by the controller integration test (fork flow end-to-end).
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Original Name (Fork)");
            assertThat(result.getAuthorId()).isEqualTo(OWNER_ID);
            assertThat(result.getIsPublic()).isFalse();   // fork always starts private
            assertThat(result.getIsFeatured()).isFalse(); // fork never inherits featured
            assertThat(result.getDescription()).isEqualTo("Original Description");
            // 1 source problem → 1 problem-relation copied
            ArgumentCaptor<ProblemListProblemRelation> captor =
                    ArgumentCaptor.forClass(ProblemListProblemRelation.class);
            verify(problemListProblemMapper).insert(captor.capture());
            assertThat(captor.getValue().getListId()).isEqualTo(result.getId());
            assertThat(captor.getValue().getProblemId()).isEqualTo(7L);
            assertThat(captor.getValue().getSortOrder()).isEqualTo(0);
        }

        @Test
        @DisplayName("should throw when source list not found")
        void forkList_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemListService.forkList(LIST_ID, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should throw when source list is private")
        void forkList_PrivateForbidden() {
            ProblemList original = createProblemList(); // isPublic = false by default
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(original));

            assertThatThrownBy(() -> problemListService.forkList(LIST_ID, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_PRIVATE));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should copy no problem-relations when source list is empty")
        void forkList_EmptySource_PassesThrough() {
            ProblemList original = createProblemList();
            original.setIsPublic(true);
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(original));
            when(problemListProblemMapper.findByListId(LIST_ID)).thenReturn(java.util.Collections.emptyList());

            ProblemListSummaryVO result = problemListService.forkList(LIST_ID, OWNER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Original Name (Fork)");
            assertThat(result.getAuthorId()).isEqualTo(OWNER_ID);
            assertThat(result.getIsPublic()).isFalse();
            // 0 source problems → 0 problem-relation inserts
            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should allow owner to fork own public list")
        void forkList_OwnerForksOwnPublicList_OK() {
            ProblemList original = createProblemList();
            original.setIsPublic(true);
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(original));
            when(problemListProblemMapper.findByListId(LIST_ID)).thenReturn(java.util.Collections.emptyList());

            // OWNER_ID is the author of `original` (via createProblemList()); call as OWNER_ID.
            ProblemListSummaryVO result = problemListService.forkList(LIST_ID, OWNER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getAuthorId()).isEqualTo(OWNER_ID);
            // fork is private + non-featured regardless of source visibility / featured flag
            assertThat(result.getIsPublic()).isFalse();
            assertThat(result.getIsFeatured()).isFalse();
            verify(problemListMapper).insert(any(ProblemList.class));
        }
    }

    // ==================== addProblem Tests (Task 5 P1 duplicate detection) ====================

    @Nested
    @DisplayName("addProblem()")
    class AddProblemTests {

        private final Long PROBLEM_ID = 7L;

        @Test
        @DisplayName("should throw 409 PROBLEM_LIST_PROBLEM_DUPLICATE on duplicate add")
        void addProblem_DuplicateThrows() {
            ProblemList list = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            // Problem exists (else service throws PROBLEM_NOT_FOUND before duplicate check)
            when(problemExistencePort.exists(PROBLEM_ID)).thenReturn(true);
            when(problemListProblemMapper.findByListIdAndProblemId(LIST_ID, PROBLEM_ID))
                    .thenReturn(Optional.of(new ProblemListProblemRelation()));

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should throw 404 when list not found")
        void addProblem_ListNotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
        }

        @Test
        @DisplayName("should throw 403 when user is not owner")
        void addProblem_NotOwner() {
            ProblemList list = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OTHER_USER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_CANNOT_EDIT));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should throw 404 when problem does not exist")
        void addProblem_ProblemNotFound() {
            ProblemList list = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            // Service checks problem existence BEFORE duplicate check, so this
            // stub triggers PROBLEM_NOT_FOUND without needing the duplicate mock.
            when(problemExistencePort.exists(PROBLEM_ID)).thenReturn(false);

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemErrorCode.PROBLEM_NOT_FOUND));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should insert relation on successful add")
        void addProblem_Success() {
            ProblemList list = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            when(problemListProblemMapper.findByListIdAndProblemId(LIST_ID, PROBLEM_ID))
                    .thenReturn(Optional.empty());
            when(problemExistencePort.exists(PROBLEM_ID)).thenReturn(true);
            when(problemListProblemMapper.getMaxSortOrder(LIST_ID)).thenReturn(2);

            problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID);

            ArgumentCaptor<ProblemListProblemRelation> captor =
                    ArgumentCaptor.forClass(ProblemListProblemRelation.class);
            verify(problemListProblemMapper).insert(captor.capture());
            ProblemListProblemRelation inserted = captor.getValue();
            assertThat(inserted.getListId()).isEqualTo(LIST_ID);
            assertThat(inserted.getProblemId()).isEqualTo(PROBLEM_ID);
            assertThat(inserted.getSortOrder()).isEqualTo(3); // maxOrder + 1
        }

        @Test
        @DisplayName("should throw 409 when insert loses duplicate race (DB PK conflict)")
        void addProblem_DuplicateRace_ThrowsBusinessException() {
            ProblemList list = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            // Fast-path check says not present, but DB PK says duplicate (concurrent insert won)
            when(problemListProblemMapper.findByListIdAndProblemId(LIST_ID, PROBLEM_ID))
                    .thenReturn(Optional.empty());
            when(problemExistencePort.exists(PROBLEM_ID)).thenReturn(true);
            when(problemListProblemMapper.getMaxSortOrder(LIST_ID)).thenReturn(0);
            doThrow(new DuplicateKeyException("PK conflict (problem_id, list_id)"))
                    .when(problemListProblemMapper).insert(any(ProblemListProblemRelation.class));

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE));
        }
    }

    // ==================== Admin-bypass mutation tests ====================

    @Nested
    @DisplayName("findEntityById()")
    class FindEntityByIdTests {

        @Test
        @DisplayName("should return the entity when found")
        void findEntityById_Found() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            ProblemList result = problemListService.findEntityById(LIST_ID);

            assertThat(result).isSameAs(existing);
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when missing")
        void findEntityById_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemListService.findEntityById(LIST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("adminUpdateProblemList()")
    class AdminUpdateProblemListTests {

        @Test
        @DisplayName("should apply all provided fields without ownership check")
        void adminUpdateProblemList_AppliesAllFields() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateProblemListDTO dto = new UpdateProblemListDTO();
            dto.setName("Admin Name");
            dto.setDescription("Admin Description");
            dto.setIsPublic(true);
            dto.setIsFeatured(true);
            dto.setBannerTag("admin-tag");
            dto.setBannerIcon("admin-icon");
            dto.setBannerTheme("admin-theme");
            dto.setBannerOrder(9);

            ProblemListSummaryVO result = problemListService.adminUpdateProblemList(LIST_ID, dto);

            assertThat(result.getName()).isEqualTo("Admin Name");
            assertThat(result.getDescription()).isEqualTo("Admin Description");
            assertThat(result.getIsPublic()).isTrue();
            assertThat(result.getIsFeatured()).isTrue();
            assertThat(result.getBannerTag()).isEqualTo("admin-tag");
            assertThat(result.getBannerIcon()).isEqualTo("admin-icon");
            assertThat(result.getBannerTheme()).isEqualTo("admin-theme");
            assertThat(result.getBannerOrder()).isEqualTo(9);

            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            assertThat(captor.getValue().getAuthorId()).isEqualTo(OWNER_ID);
        }

        @Test
        @DisplayName("should bypass ownership: admin can edit any list")
        void adminUpdateProblemList_BypassesOwnership() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateProblemListDTO dto = new UpdateProblemListDTO();
            dto.setName("Renamed By Admin");

            ProblemListSummaryVO result = problemListService.adminUpdateProblemList(LIST_ID, dto);

            assertThat(result.getName()).isEqualTo("Renamed By Admin");
            verify(problemListMapper).updateById(any(ProblemList.class));
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when missing")
        void adminUpdateProblemList_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            UpdateProblemListDTO dto = new UpdateProblemListDTO();
            dto.setName("X");

            assertThatThrownBy(() -> problemListService.adminUpdateProblemList(LIST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
            verify(problemListMapper, never()).updateById(any(ProblemList.class));
        }

        @Test
        @DisplayName("should skip null fields in partial PATCH")
        void adminUpdateProblemList_PartialPatch() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateProblemListDTO dto = new UpdateProblemListDTO();
            dto.setName("Only Name");

            ProblemListSummaryVO result = problemListService.adminUpdateProblemList(LIST_ID, dto);

            assertThat(result.getName()).isEqualTo("Only Name");
            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            ProblemList updated = captor.getValue();
            assertThat(updated.getDescription()).isEqualTo("Original Description");
            assertThat(updated.getBannerTag()).isEqualTo("original-tag");
            assertThat(updated.getIsPublic()).isFalse();
            assertThat(updated.getIsFeatured()).isFalse();
        }
    }

    @Nested
    @DisplayName("adminUpdateBasicInfo()")
    class AdminUpdateBasicInfoTests {

        @Test
        @DisplayName("should apply name and description without ownership check")
        void adminUpdateBasicInfo_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
            dto.setName("Admin Name");
            dto.setDescription("Admin Description");

            ProblemListSummaryVO result = problemListService.adminUpdateBasicInfo(LIST_ID, dto);

            assertThat(result.getName()).isEqualTo("Admin Name");
            assertThat(result.getDescription()).isEqualTo("Admin Description");
            verify(problemListMapper).updateById(any(ProblemList.class));
        }

        @Test
        @DisplayName("should bypass ownership: admin can edit any list")
        void adminUpdateBasicInfo_BypassesOwnership() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
            dto.setName("X");
            dto.setDescription("Y");

            problemListService.adminUpdateBasicInfo(LIST_ID, dto);

            verify(problemListMapper).updateById(any(ProblemList.class));
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when missing")
        void adminUpdateBasicInfo_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
            dto.setName("X");
            dto.setDescription("Y");

            assertThatThrownBy(() -> problemListService.adminUpdateBasicInfo(LIST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("adminUpdateVisibility()")
    class AdminUpdateVisibilityTests {

        @Test
        @DisplayName("should apply isPublic and isFeatured without ownership check")
        void adminUpdateVisibility_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
            dto.setIsPublic(true);
            dto.setIsFeatured(true);

            ProblemListSummaryVO result = problemListService.adminUpdateVisibility(LIST_ID, dto);

            assertThat(result.getIsPublic()).isTrue();
            assertThat(result.getIsFeatured()).isTrue();
            verify(problemListMapper).updateById(any(ProblemList.class));
        }

        @Test
        @DisplayName("should skip null visibility fields")
        void adminUpdateVisibility_Partial() {
            ProblemList existing = createProblemList();
            existing.setIsFeatured(true);
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
            dto.setIsPublic(true);
            dto.setIsFeatured(null);

            ProblemListSummaryVO result = problemListService.adminUpdateVisibility(LIST_ID, dto);

            assertThat(result.getIsPublic()).isTrue();
            assertThat(result.getIsFeatured()).isTrue();

            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            assertThat(captor.getValue().getIsFeatured()).isTrue();
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when missing")
        void adminUpdateVisibility_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
            dto.setIsPublic(true);

            assertThatThrownBy(() -> problemListService.adminUpdateVisibility(LIST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("adminUpdateBanner()")
    class AdminUpdateBannerTests {

        @Test
        @DisplayName("should apply banner fields without ownership check")
        void adminUpdateBanner_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("admin-tag");
            dto.setBannerTheme("admin-theme");
            dto.setBannerOrder(7);

            ProblemListSummaryVO result = problemListService.adminUpdateBanner(LIST_ID, dto);

            assertThat(result.getBannerTag()).isEqualTo("admin-tag");
            assertThat(result.getBannerTheme()).isEqualTo("admin-theme");
            assertThat(result.getBannerOrder()).isEqualTo(7);
            verify(problemListMapper).updateById(any(ProblemList.class));
        }

        @Test
        @DisplayName("should skip null banner fields")
        void adminUpdateBanner_Partial() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("only-tag");
            dto.setBannerTheme(null);
            dto.setBannerOrder(null);

            ProblemListSummaryVO result = problemListService.adminUpdateBanner(LIST_ID, dto);

            assertThat(result.getBannerTag()).isEqualTo("only-tag");
            ArgumentCaptor<ProblemList> captor = ArgumentCaptor.forClass(ProblemList.class);
            verify(problemListMapper).updateById(captor.capture());
            ProblemList updated = captor.getValue();
            assertThat(updated.getBannerTag()).isEqualTo("only-tag");
            assertThat(updated.getBannerTheme()).isEqualTo("original-theme");
            assertThat(updated.getBannerOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when missing")
        void adminUpdateBanner_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("tag");

            assertThatThrownBy(() -> problemListService.adminUpdateBanner(LIST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("adminReplaceListProblems()")
    class AdminReplaceListProblemsTests {

        @Test
        @DisplayName("should delete existing relations and insert all new ones")
        void adminReplaceListProblems_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
            UpdateProblemListProblemsDTO.ProblemEntry e1 = new UpdateProblemListProblemsDTO.ProblemEntry();
            e1.setProblemId(10L);
            e1.setSortOrder(0);
            UpdateProblemListProblemsDTO.ProblemEntry e2 = new UpdateProblemListProblemsDTO.ProblemEntry();
            e2.setProblemId(11L);
            e2.setSortOrder(1);
            dto.setProblems(Arrays.asList(e1, e2));

            problemListService.adminReplaceListProblems(LIST_ID, dto);

            verify(problemListProblemMapper).deleteByListId(LIST_ID);
            ArgumentCaptor<ProblemListProblemRelation> captor =
                    ArgumentCaptor.forClass(ProblemListProblemRelation.class);
            verify(problemListProblemMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
            List<ProblemListProblemRelation> inserted = captor.getAllValues();
            assertThat(inserted).hasSize(2);
            assertThat(inserted.get(0).getListId()).isEqualTo(LIST_ID);
            assertThat(inserted.get(0).getProblemId()).isEqualTo(10L);
            assertThat(inserted.get(0).getSortOrder()).isEqualTo(0);
            assertThat(inserted.get(1).getProblemId()).isEqualTo(11L);
            assertThat(inserted.get(1).getSortOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("should clear relations when problems list is empty (full replacement)")
        void adminReplaceListProblems_EmptyList() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
            dto.setProblems(java.util.Collections.emptyList());

            problemListService.adminReplaceListProblems(LIST_ID, dto);

            verify(problemListProblemMapper).deleteByListId(LIST_ID);
            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should throw VALIDATION_FAILED when problems is null")
        void adminReplaceListProblems_NullProblems() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
            dto.setProblems(null);

            assertThatThrownBy(() -> problemListService.adminReplaceListProblems(LIST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.VALIDATION_FAILED));
            verify(problemListProblemMapper).deleteByListId(LIST_ID);
            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when missing")
        void adminReplaceListProblems_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            UpdateProblemListProblemsDTO dto = new UpdateProblemListProblemsDTO();
            dto.setProblems(java.util.Collections.emptyList());

            assertThatThrownBy(() -> problemListService.adminReplaceListProblems(LIST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
            verify(problemListProblemMapper, never()).deleteByListId(any());
        }
    }

    @Nested
    @DisplayName("adminDeleteProblemList()")
    class AdminDeleteProblemListTests {

        @Test
        @DisplayName("should delete problem relations and the list row without ownership check")
        void adminDeleteProblemList_Success() {
            ProblemList existing = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(existing));

            problemListService.adminDeleteProblemList(LIST_ID);

            verify(problemListProblemMapper).deleteByListId(LIST_ID);
            verify(problemListMapper).deleteById(LIST_ID);
        }

        @Test
        @DisplayName("should throw PROBLEM_LIST_NOT_FOUND when missing")
        void adminDeleteProblemList_NotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemListService.adminDeleteProblemList(LIST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemListErrorCode.PROBLEM_LIST_NOT_FOUND));
            verify(problemListProblemMapper, never()).deleteByListId(any());
            verify(problemListMapper, never()).deleteById(anyString());
        }
    }
}
