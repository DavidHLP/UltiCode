package com.ulticode.modules.user.projection;

import com.ulticode.app.error.UserErrorCode;
import com.ulticode.app.user.port.UserReadMapper;
import com.ulticode.app.user.port.UserSummaryView;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.api.service.FollowCountPort;
import com.ulticode.app.api.service.SubmissionUserStatsPort;
import com.ulticode.app.api.service.SubmissionStreakPort;
import com.ulticode.app.api.service.ProblemDifficultyReadPort;
import com.ulticode.app.api.service.ProblemTagStatsReadPort;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
    private UserReadMapper userReadMapper;
    @Mock
    private SubmissionStreakPort submissionStreakCalculator;
    @Mock
    private SubmissionUserStatsPort submissionUserStats;
    @Mock
    private ProblemDifficultyReadPort problemDifficultyReadPort;
    @Mock
    private ProblemTagStatsReadPort problemTagStatsReadPort;
    @Mock
    private FollowCountPort followCountPort;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private DefaultUserReadProjection userReadProjection;

    private UserSummaryView testUser;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("test-user-id");
        testUser = new UserSummaryView(
                "test-user-id",
                "testuser",
                "Test User",
                "test@example.com",
                "https://example.com/avatar.png",
                "Test bio",
                "Test Company",
                "testgithub",
                LocalDateTime.now().minusDays(30),
                "Test Location",
                "@testtwitter",
                "https://test.com",
                "en",
                "USER",
                true,
                false,
                LocalDateTime.now().minusDays(5));
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            when(userReadMapper.selectById("test-user-id")).thenReturn(testUser);
            Optional<UserSummaryView> result = userReadProjection.findById("test-user-id");
            assertTrue(result.isPresent());
            assertEquals("test-user-id", result.get().id());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(userReadMapper.selectById("non-existent")).thenReturn(null);
            Optional<UserSummaryView> result = userReadProjection.findById("non-existent");
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return empty for null id")
        void shouldReturnEmptyForNullId() {
            Optional<UserSummaryView> result = userReadProjection.findById(null);
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return empty for blank id")
        void shouldReturnEmptyForBlankId() {
            Optional<UserSummaryView> result = userReadProjection.findById("   ");
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsernameTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            when(userReadMapper.selectByUsername("testuser")).thenReturn(testUser);
            Optional<UserSummaryView> result = userReadProjection.findByUsername("testuser");
            assertTrue(result.isPresent());
            assertEquals("testuser", result.get().username());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(userReadMapper.selectByUsername("nonexistent")).thenReturn(null);
            Optional<UserSummaryView> result = userReadProjection.findByUsername("nonexistent");
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmailTests {

        @Test
        @DisplayName("should throw UnsupportedOperationException — email lookup removed from app read port")
        @Disabled("P7: email lookup removed from app read port")
        void shouldThrowUnsupportedOperationException() {
            assertThrows(UnsupportedOperationException.class,
                    () -> userReadProjection.findByEmail("test@example.com"));
        }
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return current user when authenticated")
        void shouldReturnCurrentUserWhenAuthenticated() {
            when(userReadMapper.selectById("test-user-id")).thenReturn(testUser);
            UserVO result = userReadProjection.getCurrentUser();
            assertNotNull(result);
            assertEquals("test-user-id", result.getId());
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when not authenticated")
        void shouldThrowUnauthorizedWhenNotAuthenticated() {
            when(currentUserProvider.getCurrentUserId()).thenReturn(null);
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> userReadProjection.getCurrentUser());
            assertEquals(BaseErrorCode.UNAUTHORIZED, ex.getErrorCode());
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user not found")
        void shouldThrowUserNotFoundWhenUserNotFound() {
            when(userReadMapper.selectById("test-user-id")).thenReturn(null);
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> userReadProjection.getCurrentUser());
            assertEquals(UserErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsersTests {

        @Test
        @DisplayName("should return paginated list of users")
        void shouldReturnPaginatedListOfUsers() {
            when(userReadMapper.selectActiveUsers(20, 0)).thenReturn(List.of(testUser));
            when(userReadMapper.countActiveUsers()).thenReturn(1L);

            var result = userReadProjection.listUsers(1, 20);
            assertNotNull(result);
            assertEquals(1, result.getItems().size());
            assertEquals(1L, result.getTotal());
        }

        @Test
        @DisplayName("should limit page size to 100")
        void shouldLimitPageSizeTo100() {
            when(userReadMapper.selectActiveUsers(100, 0)).thenReturn(List.of());
            when(userReadMapper.countActiveUsers()).thenReturn(0L);

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
            when(userReadMapper.selectById("test-user-id")).thenReturn(testUser);
            UserVO result = userReadProjection.getUserById("test-user-id");
            assertNotNull(result);
            assertEquals("test-user-id", result.getId());
            // Email is not in public profile
            assertNull(result.getEmail());
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user not found")
        void shouldThrowUserNotFoundWhenUserNotFound() {
            when(userReadMapper.selectById("non-existent")).thenReturn(null);
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> userReadProjection.getUserById("non-existent"));
            assertEquals(UserErrorCode.USER_NOT_FOUND, ex.getErrorCode());
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
            when(userReadMapper.selectById("user-123")).thenReturn(testUser);
            when(problemTagStatsReadPort.findTagStatsByUserId("user-123")).thenReturn(List.of(
                    Map.of("tagName", "动态规划", "tagSlug", "dynamic-programming", "count", 4L),
                    Map.of("tagName", "数组", "tagSlug", "array", "count", 2)));
            when(submissionUserStats.countAcceptedProblemsByUserId("user-123")).thenReturn(6L);

            UserSkillsDTO result = userReadProjection.getUserSkillsById("user-123");

            assertThat(result.getTotalSolved()).isEqualTo(6);
            assertThat(result.getSkills()).hasSize(2);
            assertThat(result.getSkills().get(0).getTagName()).isEqualTo("动态规划");
        }

        @Test
        @DisplayName("should return empty skills when no tag stats exist")
        void getUserSkillsById_handlesNoTagStats() {
            when(userReadMapper.selectById("user-123")).thenReturn(testUser);
            when(problemTagStatsReadPort.findTagStatsByUserId("user-123")).thenReturn(null);
            when(submissionUserStats.countAcceptedProblemsByUserId("user-123")).thenReturn(null);

            UserSkillsDTO result = userReadProjection.getUserSkillsById("user-123");

            assertThat(result.getTotalSolved()).isZero();
            assertThat(result.getSkills()).isEmpty();
        }
    }
}
