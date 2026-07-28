package com.ulticode.modules.user.port;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.account.AuthAccountPort;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultUserWritePortTest {

    @Mock
    private UserProfilePort userProfilePort;

    @Mock
    private AuthAccountPort authAccountPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private DefaultUserWritePort userWritePort;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setUsername("testuser");
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encoded-password");

        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("test-user-id");
    }

    @Nested
    @DisplayName("updateCurrentUser")
    class UpdateCurrentUserTests {

        @Test
        @DisplayName("should update user profile successfully via userProfilePort")
        void shouldUpdateUserProfileSuccessfully() {
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setName("Updated Name");

            UserVO expectedVO = new UserVO();
            expectedVO.setName("Updated Name");

            when(userProfilePort.updateProfile("test-user-id", updateDTO)).thenReturn(expectedVO);

            UserVO result = userWritePort.updateCurrentUser(updateDTO);

            assertNotNull(result);
            assertEquals("Updated Name", result.getName());
            verify(userProfilePort).updateProfile("test-user-id", updateDTO);
        }

        @Test
        @DisplayName("should throw UNAUTHORIZED when not authenticated")
        void shouldThrowUnauthorizedWhenNotAuthenticated() {
            when(currentUserProvider.getCurrentUserId()).thenReturn(null);
            UpdateUserDTO updateDTO = new UpdateUserDTO();
            updateDTO.setName("New Name");

            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> userWritePort.updateCurrentUser(updateDTO));
            assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("updateLastLoginAt")
    class UpdateLastLoginAtTests {

        @Test
        @DisplayName("should delegate to authAccountPort")
        void shouldUpdateLastLoginTime() {
            userWritePort.updateLastLoginAt("test-user-id");
            verify(authAccountPort).updateLastLoginAt("test-user-id");
        }

        @Test
        @DisplayName("should pass null userId to authAccountPort")
        void shouldNotUpdateForNullUserId() {
            userWritePort.updateLastLoginAt(null);
            verify(authAccountPort).updateLastLoginAt(null);
        }

        @Test
        @DisplayName("should pass blank userId to authAccountPort")
        void shouldNotUpdateForBlankUserId() {
            userWritePort.updateLastLoginAt("   ");
            verify(authAccountPort).updateLastLoginAt("   ");
        }
    }
}
