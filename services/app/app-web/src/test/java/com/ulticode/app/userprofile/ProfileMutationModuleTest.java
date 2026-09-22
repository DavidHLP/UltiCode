package com.ulticode.app.userprofile;

import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.port.UserDirectoryRow;
import com.ulticode.modules.search.port.UserSearchRow;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileMutationModuleTest {

    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private StorageCleanupOutbox storageCleanupOutbox;
    @Mock
    private SearchDocumentChangedPublisher searchPublisher;
    @Mock
    private UserDirectoryQueryPort userDirectoryQueryPort;

    private ProfileMutationModule module;

    @BeforeEach
    void setUp() {
        module = new ProfileMutationModule(
                userProfileMapper, storageCleanupOutbox, userDirectoryQueryPort, searchPublisher);
    }

    @Test
    void insertsNewProfileAndReturnsPostMutationResult() {
        when(userProfileMapper.selectByIdForUpdate("user-1")).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);

        ProfileWriteResult result = module.update(patch("user-1", "Alice", null, "Engineer"));

        ArgumentCaptor<UserProfile> profile = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).insert(profile.capture());
        assertThat(profile.getValue().getAccountId()).isEqualTo("user-1");
        assertThat(profile.getValue().getName()).isEqualTo("Alice");
        assertThat(profile.getValue().getBio()).isEqualTo("Engineer");
        assertThat(result.accountId()).isEqualTo("user-1");
        assertThat(result.name()).isEqualTo("Alice");
        verify(userProfileMapper).selectByIdForUpdate("user-1");
    }

    @Test
    void updatesExistingProfileUsingLockedRow() {
        UserProfile existing = profile("user-2");
        existing.setName("Old");
        existing.setCompany("Acme");
        when(userProfileMapper.selectByIdForUpdate("user-2")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        ProfileWriteResult result = module.update(patch("user-2", "New", null, null));

        ArgumentCaptor<UserProfile> profile = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).updateById(profile.capture());
        assertThat(profile.getValue().getName()).isEqualTo("New");
        assertThat(profile.getValue().getCompany()).isEqualTo("Acme");
        assertThat(result.name()).isEqualTo("New");
    }

    @Test
    void skipsNullPatchFields() {
        UserProfile existing = profile("user-3");
        existing.setName("Name");
        existing.setAvatar("legacy-avatar");
        existing.setBio("Bio");
        existing.setCompany("Company");
        existing.setGithub("Github");
        existing.setLocation("Location");
        existing.setTwitter("Twitter");
        existing.setWebsite("Website");
        existing.setPreferredLanguage("en");
        when(userProfileMapper.selectByIdForUpdate("user-3")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        ProfileWriteResult result = module.update(new ProfilePatch(
                "user-3", null, null, null, null, null, null, null, null, null));

        assertThat(result.name()).isEqualTo("Name");
        assertThat(result.avatar()).isEqualTo("legacy-avatar");
        assertThat(result.bio()).isEqualTo("Bio");
        assertThat(result.company()).isEqualTo("Company");
        assertThat(result.github()).isEqualTo("Github");
        assertThat(result.location()).isEqualTo("Location");
        assertThat(result.twitter()).isEqualTo("Twitter");
        assertThat(result.website()).isEqualTo("Website");
        assertThat(result.preferredLanguage()).isEqualTo("en");
    }

    @Test
    void rejectsAffectedRowsOtherThanOne() {
        when(userProfileMapper.selectByIdForUpdate("user-4")).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(0);

        assertThatThrownBy(() -> module.update(patch("user-4", "Alice", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Profile write affected 0 rows");
        verify(storageCleanupOutbox, never()).enqueue(anyString());
        verify(searchPublisher, never()).publishUser(anyString(), any(), any(), any(), anyBoolean());
    }

    @Test
    void rejectsPatchThatReusesDisplacedOwnedAvatarKey() {
        UserProfile existing = profile("user-5");
        existing.setAvatar("app/avatars/user-5/current.png");
        when(userProfileMapper.selectByIdForUpdate("user-5")).thenReturn(existing);

        assertThatThrownBy(() -> module.update(new ProfilePatch(
                "user-5", null, "app/avatars/user-5/displaced.png",
                null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Avatar changes must use the avatar upload endpoint");
        verify(userProfileMapper, never()).updateById(any(UserProfile.class));
    }

    @Test
    void allowsUnchangedOwnedAvatarKey() {
        UserProfile existing = profile("user-6");
        existing.setAvatar("app/avatars/user-6/current.png");
        when(userProfileMapper.selectByIdForUpdate("user-6")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        ProfileWriteResult result = module.update(new ProfilePatch(
                "user-6", "Alice", "app/avatars/user-6/current.png",
                null, null, null, null, null, null, null));

        assertThat(result.avatar()).isEqualTo("app/avatars/user-6/current.png");
        verify(storageCleanupOutbox, never()).enqueue(anyString());
    }

    @Test
    void replaceAvatarSetsNewReference() {
        UserProfile existing = profile("user-7");
        existing.setAvatar("app/avatars/user-7/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-7")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        ProfileWriteResult result = module.replaceAvatar("user-7", "app/avatars/user-7/new.png");

        assertThat(result.avatar()).isEqualTo("app/avatars/user-7/new.png");
        verify(storageCleanupOutbox).enqueue("app/avatars/user-7/old.png");
    }

    @Test
    void cleansOnlyDisplacedAvatarOwnedBySameAccount() {
        UserProfile existing = profile("user-8");
        existing.setAvatar("app/avatars/other/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-8")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        module.replaceAvatar("user-8", "app/avatars/user-8/new.png");

        verify(storageCleanupOutbox, never()).enqueue(anyString());
    }

    @Test
    void writesRowThenCleanupThenPublishesSearch() {
        UserProfile existing = profile("user-9");
        existing.setAvatar("app/avatars/user-9/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-9")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        when(userDirectoryQueryPort.findById("user-9"))
                .thenReturn(directoryRow("user-9", "alice", "Alice", "new.png"));

        module.replaceAvatar("user-9", "app/avatars/user-9/new.png");

        InOrder order = inOrder(userProfileMapper, storageCleanupOutbox,
                userDirectoryQueryPort, searchPublisher);
        order.verify(userProfileMapper).updateById(any(UserProfile.class));
        order.verify(storageCleanupOutbox).enqueue("app/avatars/user-9/old.png");
        order.verify(userDirectoryQueryPort).findById("user-9");
        order.verify(searchPublisher).publishUser("user-9", "alice", "Alice", "new.png", true);
    }

    @Test
    void cleanupFailurePropagatesAndPreventsSearchPublication() {
        UserProfile existing = profile("user-10");
        existing.setAvatar("app/avatars/user-10/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-10")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        doThrow(new IllegalStateException("cleanup unavailable"))
                .when(storageCleanupOutbox).enqueue("app/avatars/user-10/old.png");

        assertThatThrownBy(() -> module.replaceAvatar("user-10", "app/avatars/user-10/new.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cleanup unavailable");
        verify(userDirectoryQueryPort, never()).findById(anyString());
        verify(searchPublisher, never()).publishUser(anyString(), any(), any(), any(), anyBoolean());
    }

    @Test
    void searchFailurePropagatesAfterProfileAndCleanup() {
        UserProfile existing = profile("user-11");
        existing.setAvatar("app/avatars/user-11/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-11")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        when(userDirectoryQueryPort.findById("user-11"))
                .thenReturn(directoryRow("user-11", "alice", "Alice", "new.png"));
        doThrow(new IllegalStateException("search unavailable"))
                .when(searchPublisher).publishUser(anyString(), any(), any(), any(), anyBoolean());

        assertThatThrownBy(() -> module.replaceAvatar("user-11", "app/avatars/user-11/new.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("search unavailable");
        verify(storageCleanupOutbox).enqueue("app/avatars/user-11/old.png");
    }

    @Test
    void directoryNullSkipsPublication() {
        when(userProfileMapper.selectByIdForUpdate("user-12")).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);
        when(userDirectoryQueryPort.findById("user-12")).thenReturn(null);

        module.update(patch("user-12", "Alice", null, null));

        verify(searchPublisher, never()).publishUser(anyString(), any(), any(), any(), anyBoolean());
    }

    @Test
    void directoryUnavailableFailsClosed() {
        when(userProfileMapper.selectByIdForUpdate("user-13")).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);
        when(userDirectoryQueryPort.findById("user-13"))
                .thenThrow(new IllegalStateException("directory unavailable"));

        assertThatThrownBy(() -> module.update(patch("user-13", "Alice", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("directory unavailable");
    }

    private static ProfilePatch patch(String accountId, String name, String avatar, String bio) {
        return new ProfilePatch(accountId, name, avatar, bio,
                null, null, null, null, null, null);
    }

    private static UserProfile profile(String accountId) {
        UserProfile profile = new UserProfile();
        profile.setAccountId(accountId);
        return profile;
    }

    private static UserDirectoryRow directoryRow(
            String id, String username, String name, String avatar) {
        UserSearchRow row = new UserSearchRow();
        row.setId(id);
        row.setUsername(username);
        row.setName(name);
        row.setAvatar(avatar);
        return UserDirectoryRow.from(row);
    }
}
