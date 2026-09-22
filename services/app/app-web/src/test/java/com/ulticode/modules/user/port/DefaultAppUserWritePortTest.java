package com.ulticode.modules.user.port;

import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.ProfileMutationModule;
import com.ulticode.app.userprofile.ProfilePatch;
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
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAppUserWritePort profile writes")
class DefaultAppUserWritePortTest {

    @Mock
    private UuidGenerator uuidGenerator;
    @Mock
    private FileStoragePort fileStorage;
    @Mock
    private StorageCleanupOutbox storageCleanupOutbox;
    @Mock
    private ProfileMutationModule profileMutationModule;

    private DefaultAppUserWritePort port;

    @BeforeEach
    void setUp() {
        port = new DefaultAppUserWritePort(
                uuidGenerator, fileStorage, profileMutationModule, storageCleanupOutbox);
        ReflectionTestUtils.setField(port, "uploadSettleSeconds", 900);
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
        @DisplayName("delegates a new profile patch to the mutation module")
        void delegatesUpdate() {
            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setName("Alice");
            dto.setBio("Engineer");
            when(profileMutationModule.update(any(ProfilePatch.class)))
                    .thenReturn(result("u-001", "Alice", null, "Engineer"));

            UserVO result = port.updateProfile("u-001", dto);

            ArgumentCaptor<ProfilePatch> patch = ArgumentCaptor.forClass(ProfilePatch.class);
            verify(profileMutationModule).update(patch.capture());
            assertThat(patch.getValue().accountId()).isEqualTo("u-001");
            assertThat(patch.getValue().name()).isEqualTo("Alice");
            assertThat(patch.getValue().bio()).isEqualTo("Engineer");
            assertThat(result.getId()).isEqualTo("u-001");
            assertThat(result.getName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("passes all profile fields without changing the adapter contract")
        void passesAllFields() {
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
            when(profileMutationModule.update(any(ProfilePatch.class)))
                    .thenReturn(result("u-003", "N", "A", "B"));

            port.updateProfile("u-003", dto);

            ArgumentCaptor<ProfilePatch> patch = ArgumentCaptor.forClass(ProfilePatch.class);
            verify(profileMutationModule).update(patch.capture());
            assertThat(patch.getValue().name()).isEqualTo("N");
            assertThat(patch.getValue().avatar()).isEqualTo("A");
            assertThat(patch.getValue().bio()).isEqualTo("B");
            assertThat(patch.getValue().company()).isEqualTo("C");
            assertThat(patch.getValue().github()).isEqualTo("G");
            assertThat(patch.getValue().location()).isEqualTo("L");
            assertThat(patch.getValue().twitter()).isEqualTo("T");
            assertThat(patch.getValue().website()).isEqualTo("W");
            assertThat(patch.getValue().preferredLanguage()).isEqualTo("P");
        }

        @Test
        @DisplayName("delegates displaced avatar-key rejection to the mutation module")
        void delegatesOwnedKeyRejection() {
            UpdateUserDTO dto = new UpdateUserDTO();
            dto.setAvatar("app/avatars/u-012/displaced.png");
            when(profileMutationModule.update(any(ProfilePatch.class)))
                    .thenThrow(new BusinessException(BaseErrorCode.BAD_REQUEST,
                            "Avatar changes must use the avatar upload endpoint"));

            assertThatThrownBy(() -> port.updateProfile("u-012", dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("avatar upload endpoint");
            verify(profileMutationModule).update(any(ProfilePatch.class));
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
        @DisplayName("non-image content throws BAD_REQUEST")
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

            verify(fileStorage, never()).put(any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("oversized dimensions are rejected before decoding")
        void oversizedDimensionsAreRejectedBeforeDecoding() throws IOException {
            MultipartFile file = new MockMultipartFile(
                    "file", "huge.png", "image/png", pngWithDimensions(4097, 4097));

            assertThatThrownBy(() -> port.uploadAvatar("u-003", file))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Image dimensions exceed 4096x4096 pixel limit");

            verify(fileStorage, never()).put(any(), any(), anyLong(), any());
            verify(profileMutationModule, never()).replaceAvatar(anyString(), anyString());
        }

        @Test
        @DisplayName("valid avatar uploads before invoking the mutation module")
        void validAvatarWrites() {
            String userId = "u-004";
            when(uuidGenerator.newId()).thenReturn("uuid-1");
            when(profileMutationModule.replaceAvatar(userId, "app/avatars/u-004/uuid-1.png"))
                    .thenReturn(result(userId, null, "app/avatars/u-004/uuid-1.png", null));
            byte[] png = png();

            String url = port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "text/plain", png));

            assertThat(url).isEqualTo("/api/users/avatars/u-004/uuid-1.png");
            InOrder order = inOrder(fileStorage, profileMutationModule);
            order.verify(fileStorage).put(any(), any(), anyLong(), eq("image/png"));
            order.verify(profileMutationModule).replaceAvatar(userId, "app/avatars/u-004/uuid-1.png");
        }

        @Test
        @DisplayName("replacement delegates cleanup ownership to the mutation module")
        void replacementUsesMutationModule() {
            String userId = "u-005";
            when(uuidGenerator.newId()).thenReturn("uuid-2");
            when(profileMutationModule.replaceAvatar(userId, "app/avatars/u-005/uuid-2.png"))
                    .thenReturn(result(userId, null, "app/avatars/u-005/uuid-2.png", null));

            port.uploadAvatar(userId, new MockMultipartFile("file", "photo.png", "image/png", png()));

            verify(profileMutationModule).replaceAvatar(userId, "app/avatars/u-005/uuid-2.png");
            verify(fileStorage, never()).delete(anyString());
        }

        @Test
        @DisplayName("first avatar upload returns a display URL")
        void firstUploadReturnsDisplayUrl() {
            String userId = "u-005-async";
            when(uuidGenerator.newId()).thenReturn("uuid-2-async");
            when(profileMutationModule.replaceAvatar(userId,
                    "app/avatars/u-005-async/uuid-2-async.png"))
                    .thenReturn(result(userId, null,
                            "app/avatars/u-005-async/uuid-2-async.png", null));

            String url = port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png()));

            assertThat(url).isEqualTo("/api/users/avatars/u-005-async/uuid-2-async.png");
        }

        @Test
        @DisplayName("database failure queues the staged object for reconciled cleanup")
        void databaseFailureQueuesGraceCleanup() {
            String userId = "u-006";
            when(uuidGenerator.newId()).thenReturn("uuid-3");
            when(profileMutationModule.replaceAvatar(userId, "app/avatars/u-006/uuid-3.png"))
                    .thenThrow(new IllegalStateException("db failure"));

            assertThatThrownBy(() -> port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("db failure");

            verify(storageCleanupOutbox).enqueueAfterGrace("app/avatars/u-006/uuid-3.png", 900);
            verify(fileStorage, never()).delete(anyString());
        }

        @Test
        @DisplayName("mutation failure queues the staged object for reconciled cleanup")
        void mutationFailureQueuesGraceCleanup() {
            String userId = "u-009";
            when(uuidGenerator.newId()).thenReturn("uuid-6");
            when(profileMutationModule.replaceAvatar(userId, "app/avatars/u-009/uuid-6.png"))
                    .thenThrow(new IllegalStateException("search unavailable"));

            assertThatThrownBy(() -> port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("search unavailable");

            verify(storageCleanupOutbox).enqueueAfterGrace("app/avatars/u-009/uuid-6.png", 900);
            verify(fileStorage, never()).delete(anyString());
        }

        @Test
        @DisplayName("grace cleanup failure does not mask the mutation failure")
        void cleanupQueueFailureDoesNotMaskWriteFailure() {
            String userId = "u-010";
            when(uuidGenerator.newId()).thenReturn("uuid-7");
            when(profileMutationModule.replaceAvatar(userId, "app/avatars/u-010/uuid-7.png"))
                    .thenThrow(new IllegalStateException("profile write failed"));
            doThrow(new IllegalStateException("outbox unavailable"))
                    .when(storageCleanupOutbox).enqueueAfterGrace(anyString(), eq(900));

            assertThatThrownBy(() -> port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("profile write failed");
        }

        @Test
        @DisplayName("zero-row mutation failure queues staged object and fails")
        void zeroRowDatabaseWriteQueuesGraceCleanup() {
            String userId = "u-007";
            when(uuidGenerator.newId()).thenReturn("uuid-4");
            when(profileMutationModule.replaceAvatar(userId, "app/avatars/u-007/uuid-4.png"))
                    .thenThrow(new IllegalStateException("Profile write affected 0 rows"));

            assertThatThrownBy(() -> port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Profile write affected 0 rows");

            verify(storageCleanupOutbox).enqueueAfterGrace("app/avatars/u-007/uuid-4.png", 900);
            verify(fileStorage, never()).delete(anyString());
        }

        @Test
        @DisplayName("replacement never directly deletes the old object")
        void replacementNeverDeletesObjectDirectly() {
            String userId = "u-008";
            when(uuidGenerator.newId()).thenReturn("uuid-5");
            when(profileMutationModule.replaceAvatar(userId, "app/avatars/u-008/uuid-5.png"))
                    .thenReturn(result(userId, null, "app/avatars/u-008/uuid-5.png", null));

            port.uploadAvatar(userId,
                    new MockMultipartFile("file", "photo.png", "image/png", png()));

            verify(profileMutationModule).replaceAvatar(userId, "app/avatars/u-008/uuid-5.png");
            verify(fileStorage, never()).delete(anyString());
        }
    }

    private static ProfileWriteResult result(
            String accountId, String name, String avatar, String bio) {
        return new ProfileWriteResult(accountId, name, avatar, bio,
                null, null, null, null, null, null);
    }

    private static byte[] png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
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
