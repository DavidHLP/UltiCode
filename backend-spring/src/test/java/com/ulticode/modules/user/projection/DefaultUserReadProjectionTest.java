package com.ulticode.modules.user.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.follow.port.FollowCountPort;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for {@link DefaultUserReadProjection}.
 *
 * <p>These tests cover the read-side cross-table joins that the deleted
 * {@code UserService} facade used to scatter. With the deep module in
 * place, every read can be tested through the same seam regardless of
 * which underlying mapper it talks to.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultUserReadProjectionTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;
    @Mock
    private FollowCountPort followCountPort;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private DefaultUserReadProjection userReadProjection;

    private User testUser;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("test-user-id");
        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setUsername("testuser");
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setAvatar("https://example.com/avatar.png");
        testUser.setBio("Test bio");
        testUser.setJoinedAt(LocalDateTime.now().minusDays(30));
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            when(userMapper.selectById("test-user-id")).thenReturn(testUser);
            Optional<User> result = userReadProjection.findById("test-user-id");
            assertTrue(result.isPresent());
            assertEquals("test-user-id", result.get().getId());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(userMapper.selectById("non-existent")).thenReturn(null);
            Optional<User> result = userReadProjection.findById("non-existent");
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return empty for null id")
        void shouldReturnEmptyForNullId() {
            Optional<User> result = userReadProjection.findById(null);
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return empty for blank id")
        void shouldReturnEmptyForBlankId() {
            Optional<User> result = userReadProjection.findById("   ");
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsernameTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            Optional<User> result = userReadProjection.findByUsername("testuser");
            assertTrue(result.isPresent());
            assertEquals("testuser", result.get().getUsername());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            Optional<User> result = userReadProjection.findByUsername("nonexistent");
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmailTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            Optional<User> result = userReadProjection.findByEmail("test@example.com");
            assertTrue(result.isPresent());
            assertEquals("test@example.com", result.get().getEmail());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            Optional<User> result = userReadProjection.findByEmail("nonexistent@example.com");
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return current user when authenticated")
        void shouldReturnCurrentUserWhenAuthenticated() {
            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);

                UserVO result = userReadProjection.getCurrentUser();
                assertNotNull(result);
                assertEquals("test-user-id", result.getId());
            }
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when not authenticated")
        void shouldThrowUnauthorizedWhenNotAuthenticated() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);
            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(null);
                BusinessException ex = assertThrows(
                        BusinessException.class,
                        () -> userReadProjection.getCurrentUser());
                assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
            }
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user not found")
        void shouldThrowUserNotFoundWhenUserNotFound() {
            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(null);
                BusinessException ex = assertThrows(
                        BusinessException.class,
                        () -> userReadProjection.getCurrentUser());
                assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
            }
        }
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsersTests {

        @Test
        @DisplayName("should return paginated list of users")
        void shouldReturnPaginatedListOfUsers() {
            Page<User> userPage = new Page<>(1, 20);
            userPage.setRecords(List.of(testUser));
            userPage.setTotal(1);
            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(userPage);

            var result = userReadProjection.listUsers(1, 20);
            assertNotNull(result);
            assertEquals(1, result.getItems().size());
            assertEquals(1L, result.getTotal());
        }

        @Test
        @DisplayName("should limit page size to 100")
        void shouldLimitPageSizeTo100() {
            Page<User> userPage = new Page<>(1, 100);
            userPage.setRecords(List.of());
            userPage.setTotal(0);
            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(userPage);

            var result = userReadProjection.listUsers(1, 200);
            assertEquals(100, result.getPageSize());
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserByIdTests {

        @Test
        @DisplayName("should return user public profile")
        void shouldReturnUserPublicProfile() {
            when(userMapper.selectById("test-user-id")).thenReturn(testUser);
            UserVO result = userReadProjection.getUserById("test-user-id");
            assertNotNull(result);
            assertEquals("test-user-id", result.getId());
            // Email is not in public profile
            assertNull(result.getEmail());
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user not found")
        void shouldThrowUserNotFoundWhenUserNotFound() {
            when(userMapper.selectById("non-existent")).thenReturn(null);
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> userReadProjection.getUserById("non-existent"));
            assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("toVO")
    class ToVOTests {

        @Test
        @DisplayName("should convert user to VO")
        void shouldConvertUserToVO() {
            UserVO result = userReadProjection.toVO(testUser);
            assertNotNull(result);
            assertEquals("test-user-id", result.getId());
            assertEquals("test@example.com", result.getEmail());
        }

        @Test
        @DisplayName("should return null for null user")
        void shouldReturnNullForNullUser() {
            assertNull(userReadProjection.toVO(null));
        }
    }

    @Nested
    @DisplayName("getUserSkillsById")
    class GetUserSkillsByIdTests {

        @Test
        @DisplayName("should map MyBatis row maps to user skills")
        void getUserSkillsById_mapsRowMaps() {
            when(userMapper.selectById("user-123")).thenReturn(testUser);
            when(problemTagRelationMapper.findTagStatsByUserId("user-123")).thenReturn(List.of(
                    Map.of("tagName", "动态规划", "tagSlug", "dynamic-programming", "count", 4L),
                    Map.of("tagName", "数组", "tagSlug", "array", "count", 2)));
            when(submissionMapper.countAcceptedProblemsByUserId("user-123")).thenReturn(6L);

            UserSkillsDTO result = userReadProjection.getUserSkillsById("user-123");

            assertThat(result.getTotalSolved()).isEqualTo(6);
            assertThat(result.getSkills()).hasSize(2);
            assertThat(result.getSkills().get(0).getTagName()).isEqualTo("动态规划");
        }

        @Test
        @DisplayName("should return empty skills when no tag stats exist")
        void getUserSkillsById_handlesNoTagStats() {
            when(userMapper.selectById("user-123")).thenReturn(testUser);
            when(problemTagRelationMapper.findTagStatsByUserId("user-123")).thenReturn(null);
            when(submissionMapper.countAcceptedProblemsByUserId("user-123")).thenReturn(null);

            UserSkillsDTO result = userReadProjection.getUserSkillsById("user-123");

            assertThat(result.getTotalSolved()).isZero();
            assertThat(result.getSkills()).isEmpty();
        }
    }
}