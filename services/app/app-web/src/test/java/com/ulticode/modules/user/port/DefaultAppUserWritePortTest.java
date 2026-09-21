package com.ulticode.modules.user.port;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    @Mock private FileStoragePort fileStorage;
    @Mock private com.ulticode.app.storage.StorageCleanupOutbox storageCleanupOutbox;
    @Mock private com.ulticode.modules.search.port.UserDirectoryQueryPort userDirectoryQueryPort;
    @Mock private com.ulticode.modules.search.source.SearchDocumentChangedPublisher searchPublisher;

    private DefaultAppUserWritePort port;
    private AvatarProfileMutationService avatarProfileMutationService;

    @BeforeEach
    void setUp() {
        avatarProfileMutationService = new AvatarProfileMutationService(
                userProfileMapper, storageCleanupOutbox, userDirectoryQueryPort, searchPublisher);
        port = new DefaultAppUserWritePort(userProfileMapper, uuidGenerator,
                fileStorage, userDirectoryQueryPort, searchPublisher, avatarProfileMutationService);
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

            UserVO result = port.updateProfile(userId, dto);

            verify(userProfileMapper).updateById(any(UserProfile.class));
            assertThat(result.getCompany()).isEqualTo("Acme");
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
        @DisplayName("WebP frame headers without compressed payload are rejected")
        void webpHeaderWithoutFramePayloadIsRejected() {
            MultipartFile file = new MockMultipartFile(
                    "file", "header-only.webp", "image/webp", webpHeaderOnly());

            assertThatThrownBy(() -> port.uploadAvatar("u-webp", file))
                    .isInstanceOf(BusinessException.class);

            verify(fileStorage, never()).put(any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
        }
        @Test
        @DisplayName("oversized dimensions are rejected before decoding")
        void oversizedDimensionsAreRejectedBeforeDecoding() throws IOException {
            MultipartFile file = new MockMultipartFile(
                    "file", "huge.png", "image/png", pngWithDimensions(4097, 4097));

            assertThatThrownBy(() -> port.uploadAvatar("u-003", file))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Image dimensions exceed 4096x4096 pixel limit");

            verify(fileStorage, never()).put(any(), any(), org.mockito.ArgumentMatchers.anyLong(), any());
            verify(userProfileMapper, never()).selectById("u-003");
        }

        @Test
        @DisplayName("existing profile without an avatar is updated instead of inserted")
        void existingProfileWithoutAvatarUpdates() {
            String userId = "u-004-existing";
            UserProfile existing = new UserProfile();
            existing.setAccountId(userId);
            when(userProfileMapper.selectById(userId)).thenReturn(existing);
            when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
            when(uuidGenerator.newId()).thenReturn("uuid-existing");
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

            port.uploadAvatar(userId, new MockMultipartFile("file", "photo.png", "image/png", png));

            verify(userProfileMapper).updateById(any(UserProfile.class));
            verify(userProfileMapper, never()).insert(any(UserProfile.class));
        }


        @Test
        @DisplayName("valid avatar: uploads before inserting the object key")
        void validAvatarWrites() {
            String userId = "u-004";
            when(uuidGenerator.newId()).thenReturn("uuid-1");
            when(userProfileMapper.selectById(userId)).thenReturn(null);
            when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
            MultipartFile file = new MockMultipartFile("file", "photo.png", "text/plain", png);

            String url = port.uploadAvatar(userId, file);

            assertThat(url).isEqualTo("/api/users/avatars/u-004/uuid-1.png");
            InOrder order = inOrder(fileStorage, userProfileMapper);
            order.verify(fileStorage).put(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.eq("image/png"));
            ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
            order.verify(userProfileMapper).insert(captor.capture());
            assertThat(captor.getValue().getAvatar()).isEqualTo("app/avatars/u-004/uuid-1.png");
        }

        @Test
        @DisplayName("replacing an avatar queues durable cleanup after the database update")
        void replacementQueuesCleanupAfterDatabaseUpdate() {
            String userId = "u-005";
            UserProfile existing = new UserProfile();
            existing.setAccountId(userId);
            existing.setAvatar("app/avatars/u-005/old.png");
            when(userProfileMapper.selectById(userId)).thenReturn(existing);
            when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
            when(uuidGenerator.newId()).thenReturn("uuid-2");
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

            port.uploadAvatar(userId, new MockMultipartFile("file", "photo.png", "image/png", png));

            InOrder order = inOrder(fileStorage, userProfileMapper, storageCleanupOutbox);
            order.verify(fileStorage).put(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.eq("image/png"));
            order.verify(userProfileMapper).selectById(userId);
            order.verify(userProfileMapper).updateById(any(UserProfile.class));
            order.verify(storageCleanupOutbox).enqueue("app/avatars/u-005/old.png");
            verify(fileStorage, never()).delete("app/avatars/u-005/old.png");
        }

        @Test
        @DisplayName("first avatar upload queues no cleanup")
        void firstUploadQueuesNoCleanup() {
            String userId = "u-005-async";
            when(userProfileMapper.selectById(userId)).thenReturn(null);
            when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);
            when(uuidGenerator.newId()).thenReturn("uuid-2-async");
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

            String url = port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png));

            assertThat(url).isEqualTo("/api/users/avatars/u-005-async/uuid-2-async.png");
            verify(storageCleanupOutbox, never()).enqueue(any());
        }

        @Test
        @DisplayName("database failure removes the newly uploaded object")
        void databaseFailureRemovesUploadedObject() {
            String userId = "u-006";
            when(userProfileMapper.selectById(userId)).thenReturn(null);
            when(uuidGenerator.newId()).thenReturn("uuid-3");
            doThrow(new IllegalStateException("db failure"))
                    .when(userProfileMapper).insert(any(UserProfile.class));
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

            assertThatThrownBy(() -> port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("db failure");

            verify(fileStorage).delete("app/avatars/u-006/uuid-3.png");
        }

        @Test
        @DisplayName("search publication failure removes the staged object")
        void searchPublicationFailureRemovesStagedObject() {
            String userId = "u-009";
            when(userProfileMapper.selectById(userId)).thenReturn(null);
            when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);
            when(uuidGenerator.newId()).thenReturn("uuid-6");
            com.ulticode.modules.search.port.UserSearchRow row =
                    new com.ulticode.modules.search.port.UserSearchRow();
            row.setId(userId);
            row.setUsername("alice");
            row.setName("Alice");
            row.setAvatar("app/avatars/u-009/uuid-6.png");
            when(userDirectoryQueryPort.findById(userId))
                    .thenReturn(com.ulticode.modules.search.port.UserDirectoryRow.from(row));
            doThrow(new IllegalStateException("search unavailable"))
                    .when(searchPublisher).publishUser(any(), any(), any(), any(), anyBoolean());
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

            assertThatThrownBy(() -> port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("search unavailable");

            verify(fileStorage).delete("app/avatars/u-009/uuid-6.png");
        }

        @Test
        @DisplayName("zero-row database write removes uploaded object and fails")
        void zeroRowDatabaseWriteRemovesUploadedObject() {
            String userId = "u-007";
            when(userProfileMapper.selectById(userId)).thenReturn(null);
            when(uuidGenerator.newId()).thenReturn("uuid-4");
            when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(0);
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

            assertThatThrownBy(() -> port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Avatar profile update affected 0 rows");

            verify(fileStorage).delete("app/avatars/u-007/uuid-4.png");
        }

        @Test
        @DisplayName("replacement queues the cleanup intent inside the mutation, not as a direct delete")
        void cleanupIntentCouplesWithTheTransaction() {
            String userId = "u-008";
            UserProfile existing = new UserProfile();
            existing.setAccountId(userId);
            existing.setAvatar("app/avatars/u-008/old.png");
            when(userProfileMapper.selectById(userId)).thenReturn(existing);
            when(uuidGenerator.newId()).thenReturn("uuid-5");
            when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
            byte[] png = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

            port.uploadAvatar(userId, new MockMultipartFile("file", "photo.png", "image/png", png));

            // The intent is a row in the same transaction, so a rolled-back
            // update leaves both the profile row and the previous object intact;
            // the dispatcher owns the actual delete.
            verify(storageCleanupOutbox).enqueue("app/avatars/u-008/old.png");
            verify(fileStorage, never()).delete("app/avatars/u-008/old.png");
        }
    }
    private static byte[] webpHeaderOnly() {
        return new byte[]{
                'R', 'I', 'F', 'F', 22, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', ' ', 10, 0, 0, 0,
                0, 0, 0, (byte) 0x9d, 0x01, 0x2a, 1, 0, 1, 0
        };
    }

    private static byte[] pngWithDimensions(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        byte[] content = output.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(content);
        buffer.putInt(16, width);
        buffer.putInt(20, height);
        CRC32 crc = new CRC32();
        crc.update(content, 12, 17);
        buffer.putInt(29, (int) crc.getValue());
        return content;
    }

}
