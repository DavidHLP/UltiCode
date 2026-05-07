package com.ulticode.modules.problemlist.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problemlist.service.impl.ProblemListServiceImpl;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}
