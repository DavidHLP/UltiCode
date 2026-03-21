package com.ulticode.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setUsername("testuser");
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setAvatar("https://example.com/avatar.png");
        testUser.setBio("Test bio");
        testUser.setCompany("Test Company");
        testUser.setGithub("https://github.com/testuser");
        testUser.setJoinedAt(LocalDateTime.now().minusDays(30));
        testUser.setLocation("Test City");
        testUser.setTwitter("https://twitter.com/testuser");
        testUser.setWebsite("https://testuser.com");
        testUser.setPreferredLanguage("java");
        testUser.setRole("USER");
        testUser.setIsActive(true);
        testUser.setIsBanned(false);
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            // Arrange
            when(userMapper.selectById("test-user-id")).thenReturn(testUser);

            // Act
            Optional<User> result = userService.findById("test-user-id");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("test-user-id", result.get().getId());
            assertEquals("testuser", result.get().getUsername());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            // Arrange
            when(userMapper.selectById("non-existent")).thenReturn(null);

            // Act
            Optional<User> result = userService.findById("non-existent");

            // Assert
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return empty for null id")
        void shouldReturnEmptyForNullId() {
            // Act
            Optional<User> result = userService.findById(null);

            // Assert
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return empty for blank id")
        void shouldReturnEmptyForBlankId() {
            // Act
            Optional<User> result = userService.findById("   ");

            // Assert
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsernameTests {

        @Test
        @DisplayName("should return user when found by username")
        void shouldReturnUserWhenFoundByUsername() {
            // Arrange
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

            // Act
            Optional<User> result = userService.findByUsername("testuser");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("testuser", result.get().getUsername());
        }

        @Test
        @DisplayName("should return empty when username not found")
        void shouldReturnEmptyWhenUsernameNotFound() {
            // Arrange
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // Act
            Optional<User> result = userService.findByUsername("nonexistent");

            // Assert
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmailTests {

        @Test
        @DisplayName("should return user when found by email")
        void shouldReturnUserWhenFoundByEmail() {
            // Arrange
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

            // Act
            Optional<User> result = userService.findByEmail("test@example.com");

            // Assert
            assertTrue(result.isPresent());
            assertEquals("test@example.com", result.get().getEmail());
        }

        @Test
        @DisplayName("should return empty when email not found")
        void shouldReturnEmptyWhenEmailNotFound() {
            // Arrange
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // Act
            Optional<User> result = userService.findByEmail("nonexistent@example.com");

            // Assert
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return current user when authenticated")
        void shouldReturnCurrentUserWhenAuthenticated() {
            // Arrange
            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);

                // Act
                UserVO result = userService.getCurrentUser();

                // Assert
                assertNotNull(result);
                assertEquals("test-user-id", result.getId());
                assertEquals("testuser", result.getUsername());
                assertEquals("test@example.com", result.getEmail());
            }
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when not authenticated")
        void shouldThrowUnauthorizedWhenNotAuthenticated() {
            // Arrange
            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(null);

                // Act & Assert
                BusinessException exception = assertThrows(
                        BusinessException.class,
                        () -> userService.getCurrentUser()
                );
                assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
            }
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user not found")
        void shouldThrowUserNotFoundWhenUserNotFound() {
            // Arrange
            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(null);

                // Act & Assert
                BusinessException exception = assertThrows(
                        BusinessException.class,
                        () -> userService.getCurrentUser()
                );
                assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
            }
        }
    }

    @Nested
    @DisplayName("updateCurrentUser")
    class UpdateCurrentUserTests {

        @Test
        @DisplayName("should update user profile successfully")
        void shouldUpdateUserProfileSuccessfully() {
            // Arrange
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setName("Updated Name");
            updateDTO.setBio("Updated bio");
            updateDTO.setLocation("New City");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);
                when(userMapper.updateById(any(User.class))).thenReturn(1);

                // Act
                UserVO result = userService.updateCurrentUser(updateDTO);

                // Assert
                assertNotNull(result);
                assertEquals("Updated Name", result.getName());
                verify(userMapper).updateById(any(User.class));
            }
        }

        @Test
        @DisplayName("should throw AUTH_EMAIL_TAKEN when email already exists")
        void shouldThrowEmailTakenWhenEmailExists() {
            // Arrange
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setEmail("new@example.com");

            User existingUser = new User();
            existingUser.setId("other-user-id");
            existingUser.setEmail("new@example.com");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);
                when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

                // Act & Assert
                BusinessException exception = assertThrows(
                        BusinessException.class,
                        () -> userService.updateCurrentUser(updateDTO)
                );
                assertEquals(ErrorCode.AUTH_EMAIL_TAKEN, exception.getErrorCode());
            }
        }

        @Test
        @DisplayName("should allow updating to same email")
        void shouldAllowUpdatingToSameEmail() {
            // Arrange
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setEmail("test@example.com"); // Same as current email
            updateDTO.setName("New Name");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);
                when(userMapper.updateById(any(User.class))).thenReturn(1);

                // Act
                UserVO result = userService.updateCurrentUser(updateDTO);

                // Assert
                assertNotNull(result);
                assertEquals("New Name", result.getName());
                verify(userMapper).updateById(any(User.class));
            }
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when not authenticated")
        void shouldThrowUnauthorizedWhenNotAuthenticated() {
            // Arrange
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setName("New Name");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(null);

                // Act & Assert
                BusinessException exception = assertThrows(
                        BusinessException.class,
                        () -> userService.updateCurrentUser(updateDTO)
                );
                assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
            }
        }
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsersTests {

        @Test
        @DisplayName("should return paginated list of users")
        void shouldReturnPaginatedListOfUsers() {
            // Arrange
            Page<User> userPage = new Page<>(1, 20);
            userPage.setRecords(List.of(testUser));
            userPage.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(userPage);

            // Act
            PageResult<UserVO> result = userService.listUsers(1, 20);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getItems().size());
            assertEquals(1L, result.getTotal());
            assertEquals(1, result.getPage());
            assertEquals(20, result.getPageSize());
        }

        @Test
        @DisplayName("should use default values for null page and pageSize")
        void shouldUseDefaultValuesForNullPageAndPageSize() {
            // Arrange
            Page<User> userPage = new Page<>(1, 20);
            userPage.setRecords(List.of(testUser));
            userPage.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(userPage);

            // Act
            PageResult<UserVO> result = userService.listUsers(null, null);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getPage());
            assertEquals(20, result.getPageSize());
        }

        @Test
        @DisplayName("should limit page size to 100")
        void shouldLimitPageSizeTo100() {
            // Arrange
            Page<User> userPage = new Page<>(1, 100);
            userPage.setRecords(List.of(testUser));
            userPage.setTotal(1);

            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(userPage);

            // Act
            PageResult<UserVO> result = userService.listUsers(1, 200);

            // Assert
            assertNotNull(result);
            assertEquals(100, result.getPageSize());
        }

        @Test
        @DisplayName("should return empty list when no users")
        void shouldReturnEmptyListWhenNoUsers() {
            // Arrange
            Page<User> userPage = new Page<>(1, 20);
            userPage.setRecords(List.of());
            userPage.setTotal(0);

            when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(userPage);

            // Act
            PageResult<UserVO> result = userService.listUsers(1, 20);

            // Assert
            assertNotNull(result);
            assertTrue(result.getItems().isEmpty());
            assertEquals(0L, result.getTotal());
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserByIdTests {

        @Test
        @DisplayName("should return user public profile")
        void shouldReturnUserPublicProfile() {
            // Arrange
            when(userMapper.selectById("test-user-id")).thenReturn(testUser);

            // Act
            UserVO result = userService.getUserById("test-user-id");

            // Assert
            assertNotNull(result);
            assertEquals("test-user-id", result.getId());
            assertEquals("testuser", result.getUsername());
            // Email should not be in public profile
            assertNull(result.getEmail());
        }

        @Test
        @DisplayName("should throw USER_NOT_FOUND when user not found")
        void shouldThrowUserNotFoundWhenUserNotFound() {
            // Arrange
            when(userMapper.selectById("non-existent")).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> userService.getUserById("non-existent")
            );
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("toVO")
    class ToVOTests {

        @Test
        @DisplayName("should convert user to VO with all fields")
        void shouldConvertUserToVOWithAllFields() {
            // Act
            UserVO result = userService.toVO(testUser);

            // Assert
            assertNotNull(result);
            assertEquals("test-user-id", result.getId());
            assertEquals("testuser", result.getUsername());
            assertEquals("Test User", result.getName());
            assertEquals("test@example.com", result.getEmail());
            assertEquals("https://example.com/avatar.png", result.getAvatar());
            assertEquals("Test bio", result.getBio());
            assertEquals("Test Company", result.getCompany());
            assertEquals("https://github.com/testuser", result.getGithub());
            assertNotNull(result.getJoinedAt());
            assertEquals("Test City", result.getLocation());
            assertEquals("https://twitter.com/testuser", result.getTwitter());
            assertEquals("https://testuser.com", result.getWebsite());
            assertEquals("java", result.getPreferredLanguage());
            assertEquals("USER", result.getRole());
            assertTrue(result.getIsActive());
        }

        @Test
        @DisplayName("should return null for null user")
        void shouldReturnNullForNullUser() {
            // Act
            UserVO result = userService.toVO(null);

            // Assert
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("updateLastLoginAt")
    class UpdateLastLoginAtTests {

        @Test
        @DisplayName("should update last login time")
        void shouldUpdateLastLoginTime() {
            // Arrange
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            // Act
            userService.updateLastLoginAt("test-user-id");

            // Assert
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("should not update for null userId")
        void shouldNotUpdateForNullUserId() {
            // Act
            userService.updateLastLoginAt(null);

            // Assert
            verify(userMapper, never()).updateById(any(User.class));
        }

        @Test
        @DisplayName("should not update for blank userId")
        void shouldNotUpdateForBlankUserId() {
            // Act
            userService.updateLastLoginAt("   ");

            // Assert
            verify(userMapper, never()).updateById(any(User.class));
        }
    }
}
