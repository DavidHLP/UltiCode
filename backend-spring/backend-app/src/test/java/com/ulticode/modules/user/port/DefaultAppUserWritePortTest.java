package com.ulticode.modules.user.port;

import com.ulticode.app.user.port.UserProfileWriteMapper;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P7-RELOCATE-USER-REMAINDER-001: focused tests for
 * {@link DefaultAppUserWritePort} dual-write paths.
 *
 * <p>Each profile field must write to <em>both</em> tables:
 * {@code user_profiles} (App-owned canonical) and {@code users} profile
 * columns (via {@link UserProfileWriteMapper}, Q-write for read
 * consistency during the P5-USERPROFILE-001 dual-write window).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAppUserWritePort dual-write")
class DefaultAppUserWritePortTest {

    @Mock private UserProfileMapper userProfileMapper;
    @Mock private UserProfileWriteMapper userProfileWriteMapper;
    @Mock private UuidGenerator uuidGenerator;

    private DefaultAppUserWritePort port;

    @BeforeEach
    void setUp() {
        port = new DefaultAppUserWritePort(userProfileMapper, userProfileWriteMapper, uuidGenerator);
    }

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfile {

        @Test
        @DisplayName("null userId throws UNAUTHORIZED")
        void nullUserIdThrows() {
            assertThatThrownBy(() -> port.updateProfile(null, new UpdateUserDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(BaseErrorCode.UNAUTHORIZED.message());
        }

        @Test
        @DisplayName("new profile: inserts into user_profiles + dual-writes non-null fields to users")
        void newProfileInsertsAndDualWrites() {
            String userId = "u-001";
            when(userProfileMapper.selectById(userId)).thenReturn(null);

            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("Alice");
            dto.setBio("Engineer");

            UserVO result = port.updateProfile(userId, dto);

            // user_profiles insert
            ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
            verify(userProfileMapper).insert(captor.capture());
            assertThat(captor.getValue().getAccountId()).isEqualTo(userId);
            assertThat(captor.getValue().getName()).isEqualTo("Alice");

            // dual-write to users table
            verify(userProfileWriteMapper).updateName(userId, "Alice");
            verify(userProfileWriteMapper).updateBio(userId, "Engineer");
            // fields NOT in DTO should NOT be dual-written
            verify(userProfileWriteMapper, never()).updateAvatar(eq(userId), any());
            verify(userProfileWriteMapper, never()).updateGithub(eq(userId), any());

            // VO returned with updated fields
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("existing profile: updates user_profiles + dual-writes only changed fields")
        void existingProfileUpdatesAndDualWrites() {
            String userId = "u-002";
            UserProfile existing = new UserProfile();
            existing.setAccountId(userId);
            existing.setName("OldName");
            when(userProfileMapper.selectById(userId)).thenReturn(existing);

            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("NewName");
            dto.setCompany("Acme");

            port.updateProfile(userId, dto);

            verify(userProfileMapper).updateById(any(UserProfile.class));
            verify(userProfileWriteMapper).updateName(userId, "NewName");
            verify(userProfileWriteMapper).updateCompany(userId, "Acme");
            // unchanged fields not written
            verify(userProfileWriteMapper, never()).updateBio(eq(userId), any());
        }

        @Test
        @DisplayName("all nine fields dual-write when all non-null in DTO")
        void allFieldsDualWrite() {
            String userId = "u-003";
            when(userProfileMapper.selectById(userId)).thenReturn(null);

            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("N");
            dto.setAvatar("A");
            dto.setBio("B");
            dto.setCompany("C");
            dto.setGithub("G");
            dto.setLocation("L");
            dto.setTwitter("T");
            dto.setWebsite("W");
            dto.setPreferredLanguage("P");

            port.updateProfile(userId, dto);

            verify(userProfileWriteMapper).updateName(userId, "N");
            verify(userProfileWriteMapper).updateAvatar(userId, "A");
            verify(userProfileWriteMapper).updateBio(userId, "B");
            verify(userProfileWriteMapper).updateCompany(userId, "C");
            verify(userProfileWriteMapper).updateGithub(userId, "G");
            verify(userProfileWriteMapper).updateLocation(userId, "L");
            verify(userProfileWriteMapper).updateTwitter(userId, "T");
            verify(userProfileWriteMapper).updateWebsite(userId, "W");
            verify(userProfileWriteMapper).updatePreferredLanguage(userId, "P");
        }
    }

    @Nested
    @DisplayName("uploadAvatar()")
    class UploadAvatar {

        @Test
        @DisplayName("null userId throws UNAUTHORIZED")
        void nullUserIdThrows() {
            MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1});
            assertThatThrownBy(() -> port.uploadAvatar(null, file))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("empty file throws BAD_REQUEST")
        void emptyFileThrows() {
            MultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
            assertThatThrownBy(() -> port.uploadAvatar("u-001", file))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("non-image content type throws BAD_REQUEST")
        void nonImageThrows() {
            MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{1, 2, 3});
            assertThatThrownBy(() -> port.uploadAvatar("u-001", file))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("valid avatar: dual-writes to user_profiles + users, returns URL")
        void validAvatarDualWrites() {
            String userId = "u-004";
            when(uuidGenerator.newId()).thenReturn("uuid-1");
            when(userProfileMapper.selectById(userId)).thenReturn(null);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png",
                    new byte[]{1, 2, 3, 4, 5});

            String url = port.uploadAvatar(userId, file);

            assertThat(url).startsWith("/uploads/avatars/");
            // user_profiles insert
            verify(userProfileMapper).insert(any(UserProfile.class));
            // dual-write avatar to users
            verify(userProfileWriteMapper).updateAvatar(eq(userId), eq(url));
        }
    }
}
