package com.ulticode.modules.problemlist.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.dto.UserListsForProblemVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problemlist.service.impl.ProblemListServiceImpl;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProblemListService update methods.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemListService")
class ProblemListServiceTest {

    private static final String LIST_ID = "list-001";
    private static final String OWNER_ID = "user-001";
    private static final String OTHER_USER_ID = "user-002";

    @Mock
    private ProblemListMapper problemListMapper;

    @Mock
    private ProblemListProblemMapper problemListProblemMapper;

    @Mock
    private ProblemListCategoryMapper problemListCategoryMapper;

    @Mock
    private ProblemListBookmarkMapper problemListBookmarkMapper;

    @Mock
    private ProblemMapper problemMapper;

    @Mock
    private UserMapper userMapper;

    private ProblemListService problemListService;

    @BeforeEach
    void setUp() {
        problemListService = new ProblemListServiceImpl(
                problemListMapper,
                problemListProblemMapper,
                problemListCategoryMapper,
                problemListBookmarkMapper,
                problemMapper,
                userMapper
        );
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

    private void mockOwnerUser() {
        User user = new User();
        user.setId(OWNER_ID);
        user.setName("Test User");
        user.setUsername("testuser");
        when(userMapper.selectById(OWNER_ID)).thenReturn(user);
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
            when(problemListProblemMapper.countByListId(LIST_ID)).thenReturn(0L);
            mockOwnerUser();
            when(problemListBookmarkMapper.existsByUserIdAndListId(OWNER_ID, LIST_ID)).thenReturn(false);

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
                            .isEqualTo(ErrorCode.PROBLEM_LIST_CANNOT_EDIT));

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
                            .isEqualTo(ErrorCode.PROBLEM_LIST_NOT_FOUND));

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
            when(problemListProblemMapper.countByListId(LIST_ID)).thenReturn(0L);
            mockOwnerUser();
            when(problemListBookmarkMapper.existsByUserIdAndListId(OWNER_ID, LIST_ID)).thenReturn(false);

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
            when(problemListProblemMapper.countByListId(LIST_ID)).thenReturn(0L);
            mockOwnerUser();
            when(problemListBookmarkMapper.existsByUserIdAndListId(OWNER_ID, LIST_ID)).thenReturn(false);

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
                            .isEqualTo(ErrorCode.PROBLEM_LIST_CANNOT_EDIT));

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
            when(problemListProblemMapper.countByListId(LIST_ID)).thenReturn(0L);
            mockOwnerUser();
            when(problemListBookmarkMapper.existsByUserIdAndListId(OWNER_ID, LIST_ID)).thenReturn(false);

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
            when(problemListProblemMapper.countByListId(LIST_ID)).thenReturn(0L);
            mockOwnerUser();
            when(problemListBookmarkMapper.existsByUserIdAndListId(OWNER_ID, LIST_ID)).thenReturn(false);

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
                            .isEqualTo(ErrorCode.PROBLEM_LIST_CANNOT_EDIT));

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
                            .isEqualTo(ErrorCode.PROBLEM_LIST_NOT_FOUND));

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
                            .isEqualTo(ErrorCode.PROBLEM_LIST_PRIVATE));

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

    // ==================== getUserListsForProblem Tests (Task 3 P1 N+1 verification) ====================

    @Nested
    @DisplayName("getUserListsForProblem()")
    class GetUserListsForProblemTests {

        private final String PROBLEM_ID = "7";
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
                    problemListService.getUserListsForProblem(OWNER_ID, PROBLEM_ID_LONG);

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
                    problemListService.getUserListsForProblem(OWNER_ID, PROBLEM_ID_LONG);

            assertThat(result.getLists()).hasSize(1);
            assertThat(result.getLists().get(0).getProblemCount()).isEqualTo(0);
            assertThat(result.getLists().get(0).getHasProblem()).isFalse();
        }

        @Test
        @DisplayName("should return empty lists when user has no lists (short-circuit)")
        void getUserListsForProblem_EmptyWhenNoLists() {
            when(problemListMapper.findByAuthorId(OWNER_ID)).thenReturn(Arrays.asList());

            UserListsForProblemVO result =
                    problemListService.getUserListsForProblem(OWNER_ID, PROBLEM_ID_LONG);

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

    // ==================== addProblem Tests (Task 5 P1 duplicate detection) ====================

    @Nested
    @DisplayName("addProblem()")
    class AddProblemTests {

        private final Long PROBLEM_ID = 7L;

        @Test
        @DisplayName("should throw 409 PROBLEM_LIST_PROBLEM_DUPLICATE on duplicate add")
        void addProblem_DuplicateThrows() {
            ProblemList list = createProblemList();
            Problem problem = new Problem();
            problem.setId(PROBLEM_ID);
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            // Problem exists (else service throws PROBLEM_NOT_FOUND before duplicate check)
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(problem);
            when(problemListProblemMapper.findByListIdAndProblemId(LIST_ID, PROBLEM_ID))
                    .thenReturn(Optional.of(new ProblemListProblemRelation()));

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should throw 404 when list not found")
        void addProblem_ListNotFound() {
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_LIST_NOT_FOUND));
        }

        @Test
        @DisplayName("should throw 403 when user is not owner")
        void addProblem_NotOwner() {
            ProblemList list = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OTHER_USER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_LIST_CANNOT_EDIT));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should throw 404 when problem does not exist")
        void addProblem_ProblemNotFound() {
            ProblemList list = createProblemList();
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            // Service checks problem existence BEFORE duplicate check, so this
            // stub triggers PROBLEM_NOT_FOUND without needing the duplicate mock.
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));

            verify(problemListProblemMapper, never()).insert(any(ProblemListProblemRelation.class));
        }

        @Test
        @DisplayName("should insert relation on successful add")
        void addProblem_Success() {
            ProblemList list = createProblemList();
            Problem problem = new Problem();
            problem.setId(PROBLEM_ID);
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            when(problemListProblemMapper.findByListIdAndProblemId(LIST_ID, PROBLEM_ID))
                    .thenReturn(Optional.empty());
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(problem);
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
            Problem problem = new Problem();
            problem.setId(PROBLEM_ID);
            when(problemListMapper.findById(LIST_ID)).thenReturn(Optional.of(list));
            // Fast-path check says not present, but DB PK says duplicate (concurrent insert won)
            when(problemListProblemMapper.findByListIdAndProblemId(LIST_ID, PROBLEM_ID))
                    .thenReturn(Optional.empty());
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(problem);
            when(problemListProblemMapper.getMaxSortOrder(LIST_ID)).thenReturn(0);
            doThrow(new DuplicateKeyException("PK conflict (problem_id, list_id)"))
                    .when(problemListProblemMapper).insert(any(ProblemListProblemRelation.class));

            assertThatThrownBy(() -> problemListService.addProblem(LIST_ID, OWNER_ID, PROBLEM_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE));
        }
    }
}
