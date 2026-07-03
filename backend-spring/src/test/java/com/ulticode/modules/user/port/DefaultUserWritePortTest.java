package com.ulticode.modules.user.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.user.dto.UpdateUserDTO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultUserWritePort}.
 *
 * <p>These tests cover the write-side mutating operations that the
 * deleted {@code UserService} facade used to inline. With the deep
 * module in place, every write is tested through the same seam and the
 * cross-module mapper dependencies stay out of the way.
 */
@ExtendWith(MockitoExtension.class)
class DefaultUserWritePortTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private DefaultUserWritePort userWritePort;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setEmail("test@example.com");
    }

    @Nested
    @DisplayName("updateCurrentUser")
    class UpdateCurrentUserTests {

        @Test
        @DisplayName("should update user profile successfully")
        void shouldUpdateUserProfileSuccessfully() {
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setName("Updated Name");
            updateDTO.setBio("Updated bio");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);
                when(userMapper.updateById(any(User.class))).thenReturn(1);

                UserVO result = userWritePort.updateCurrentUser(updateDTO);

                assertNotNull(result);
                assertEquals("Updated Name", result.getName());
                verify(userMapper).updateById(any(User.class));
            }
        }

        @Test
        @DisplayName("should throw AUTH_EMAIL_TAKEN when email already exists")
        void shouldThrowEmailTakenWhenEmailExists() {
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setEmail("new@example.com");

            User existingUser = new User();
            existingUser.setId("other-user-id");
            existingUser.setEmail("new@example.com");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);
                when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

                BusinessException ex = assertThrows(
                        BusinessException.class,
                        () -> userWritePort.updateCurrentUser(updateDTO));
                assertEquals(ErrorCode.AUTH_EMAIL_TAKEN, ex.getErrorCode());
            }
        }

        @Test
        @DisplayName("should allow updating to same email")
        void shouldAllowUpdatingToSameEmail() {
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setEmail("test@example.com"); // same as current
            updateDTO.setName("New Name");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn("test-user-id");
                when(userMapper.selectById("test-user-id")).thenReturn(testUser);
                when(userMapper.updateById(any(User.class))).thenReturn(1);

                UserVO result = userWritePort.updateCurrentUser(updateDTO);
                assertNotNull(result);
                assertEquals("New Name", result.getName());
            }
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when not authenticated")
        void shouldThrowUnauthorizedWhenNotAuthenticated() {
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setName("New Name");

            try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
                securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(null);

                BusinessException ex = assertThrows(
                        BusinessException.class,
                        () -> userWritePort.updateCurrentUser(updateDTO));
                assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
            }
        }
    }

    @Nested
    @DisplayName("updateLastLoginAt")
    class UpdateLastLoginAtTests {

        @Test
        @DisplayName("should update last login time")
        void shouldUpdateLastLoginTime() {
            when(userMapper.updateById(any(User.class))).thenReturn(1);
            userWritePort.updateLastLoginAt("test-user-id");
            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("should not update for null userId")
        void shouldNotUpdateForNullUserId() {
            userWritePort.updateLastLoginAt(null);
            verify(userMapper, never()).updateById(any(User.class));
        }

        @Test
        @DisplayName("should not update for blank userId")
        void shouldNotUpdateForBlankUserId() {
            userWritePort.updateLastLoginAt("   ");
            verify(userMapper, never()).updateById(any(User.class));
        }
    }
}