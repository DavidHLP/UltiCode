package com.ulticode.modules.user.port;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link DefaultAppUserWritePort} profile write paths.
 *
 * <p>Each profile field must write to {@code user_profiles}
 * (App-owned canonical source).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAppUserWritePort profile writes")
class DefaultAppUserWritePortTest {

    @Mock private UserProfileMapper userProfileMapper;
    @Mock private UuidGenerator uuidGenerator;
    @Mock private com.ulticode.modules.search.port.UserDirectoryQueryPort userDirectoryQueryPort;
    @Mock private com.ulticode.modules.search.source.SearchDocumentChangedPublisher searchPublisher;
    private DefaultAppUserWritePort port;

    @BeforeEach
    void setUp() {
        port = new DefaultAppUserWritePort(userProfileMapper, uuidGenerator,
                userDirectoryQueryPort, searchPublisher);
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
        @DisplayName("new profile: inserts into user_profiles with non-null fields")
        void newProfileInserts() {
            String userId = "u-001";
            when(userProfileMapper.selectById(userId)).thenReturn(null);

            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("Alice");
            dto.setBio("Engineer");

            UserVO result = port.updateProfile(userId, dto);

            ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
            verify(userProfileMapper).insert(captor.capture());
            assertThat(captor.getValue().getAccountId()).isEqualTo(userId);
            assertThat(captor.getValue().getName()).isEqualTo("Alice");
            assertThat(captor.getValue().getBio()).isEqualTo("Engineer");

            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("existing profile: updates user_profiles with changed fields")
        void existingProfileUpdates() {
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
        }

        @Test
        @DisplayName("profile update publishes a complete user document (SEARCH-001)")
        void profileUpdatePublishesUserDocument() {
            String userId = "u-002";
            com.ulticode.modules.search.port.UserSearchRow row =
                    new com.ulticode.modules.search.port.UserSearchRow();
            row.setId(userId);
            row.setUsername("alice");
            row.setName("NewName");
            row.setAvatar("/a.png");
            when(userDirectoryQueryPort.findById(userId))
                    .thenReturn(com.ulticode.modules.search.port.UserDirectoryRow.from(row));
            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("NewName");

            port.updateProfile(userId, dto);

            verify(searchPublisher).publishUser(userId, "alice", "NewName", "/a.png", true);
        }

        @Test
        @DisplayName("all nine fields written when all non-null in DTO")
        void allFieldsWritten() {
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

            ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
            verify(userProfileMapper).insert(captor.capture());
            UserProfile inserted = captor.getValue();
            assertThat(inserted.getName()).isEqualTo("N");
            assertThat(inserted.getAvatar()).isEqualTo("A");
            assertThat(inserted.getBio()).isEqualTo("B");
            assertThat(inserted.getCompany()).isEqualTo("C");
            assertThat(inserted.getGithub()).isEqualTo("G");
            assertThat(inserted.getLocation()).isEqualTo("L");
            assertThat(inserted.getTwitter()).isEqualTo("T");
            assertThat(inserted.getWebsite()).isEqualTo("W");
            assertThat(inserted.getPreferredLanguage()).isEqualTo("P");
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
        @DisplayName("valid avatar: writes to user_profiles, returns URL")
        void validAvatarWrites() {
            String userId = "u-004";
            when(uuidGenerator.newId()).thenReturn("uuid-1");
            when(userProfileMapper.selectById(userId)).thenReturn(null);
            MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png",
                    new byte[]{1, 2, 3, 4, 5});

            String url = port.uploadAvatar(userId, file);

            assertThat(url).startsWith("/uploads/avatars/");
            verify(userProfileMapper).insert(any(UserProfile.class));
        }
    }
}
