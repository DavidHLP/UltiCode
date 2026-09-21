package com.ulticode.app.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.storage.FileStoragePort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageCleanupDispatcherTest {

    private static final String OLD_KEY = "app/avatars/u-1/old.png";

    @Mock
    private StorageCleanupOutboxMapper outboxMapper;
    @Mock
    private FileStoragePort fileStorage;
    @Mock
    private UserProfileMapper userProfileMapper;

    private StorageCleanupDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new StorageCleanupDispatcher(outboxMapper, fileStorage, userProfileMapper);
    }

    private StorageCleanupOutboxRecord claimedRow() {
        StorageCleanupOutboxRecord record = new StorageCleanupOutboxRecord();
        record.setId("sc-1");
        record.setObjectKey(OLD_KEY);
        record.setState("CLAIMED");
        return record;
    }

    private void givenClaimed(StorageCleanupOutboxRecord record) {
        when(outboxMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(outboxMapper.selectClaimed(anyString())).thenReturn(List.of(record));
    }

    @Test
    void deletesReplacedObjectAndMarksDelivered() {
        givenClaimed(claimedRow());
        UserProfile profile = new UserProfile();
        profile.setAccountId("u-1");
        profile.setAvatar("app/avatars/u-1/new.png");
        when(userProfileMapper.selectById("u-1")).thenReturn(profile);
        when(outboxMapper.markDelivered(eq("sc-1"), anyString())).thenReturn(1);

        assertThat(dispatcher.dispatch()).isEqualTo(1);

        verify(fileStorage).delete(OLD_KEY);
        verify(outboxMapper).markDelivered(eq("sc-1"), anyString());
    }

    @Test
    void skipsDeleteWhileTheKeyIsStillTheCurrentAvatar() {
        givenClaimed(claimedRow());
        UserProfile profile = new UserProfile();
        profile.setAccountId("u-1");
        profile.setAvatar(OLD_KEY);
        when(userProfileMapper.selectById("u-1")).thenReturn(profile);
        when(outboxMapper.markDelivered(eq("sc-1"), anyString())).thenReturn(1);

        assertThat(dispatcher.dispatch()).isEqualTo(1);

        verify(fileStorage, never()).delete(any());
        verify(outboxMapper).markDelivered(eq("sc-1"), anyString());
    }

    @Test
    void deletesWhenTheProfileRowIsGone() {
        givenClaimed(claimedRow());
        when(userProfileMapper.selectById("u-1")).thenReturn(null);
        when(outboxMapper.markDelivered(eq("sc-1"), anyString())).thenReturn(1);

        assertThat(dispatcher.dispatch()).isEqualTo(1);

        verify(fileStorage).delete(OLD_KEY);
    }

    @Test
    void retriesWhenStorageDeleteFails() {
        givenClaimed(claimedRow());
        when(userProfileMapper.selectById("u-1")).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("storage unavailable"))
                .when(fileStorage).delete(OLD_KEY);

        assertThat(dispatcher.dispatch()).isZero();

        verify(outboxMapper).markRetry(eq("sc-1"), anyString(), eq("storage unavailable"), eq(5), eq(30));
        verify(outboxMapper, never()).markDelivered(anyString(), anyString());
    }
}
