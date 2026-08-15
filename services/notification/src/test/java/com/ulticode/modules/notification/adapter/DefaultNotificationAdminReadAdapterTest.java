package com.ulticode.modules.notification.adapter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.port.adapter.DefaultNotificationAdminReadAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationAdminReadAdapterTest {

    @Mock
    private NotificationMapper notificationMapper;

    private DefaultNotificationAdminReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultNotificationAdminReadAdapter(notificationMapper);
    }

    @Test
    @DisplayName("passes an explicit category to the App-owned notification query")
    void passesExplicitCategory() {
        IPage<com.ulticode.modules.notification.entity.Notification> page = new Page<>(1, 20);
        when(notificationMapper.selectDedupedAnnouncements(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        adapter.selectSystemNotifications(1, 20, "title", "SYSTEM", "SECURITY",
                "announcement-1", "createdAt", "asc");

        ArgumentCaptor<String> category = ArgumentCaptor.forClass(String.class);
        verify(notificationMapper).selectDedupedAnnouncements(
                any(Page.class), category.capture(), any(), any(), any(), any(), any());
        assertThat(category.getValue()).isEqualTo("SECURITY");
    }

    @Test
    @DisplayName("defaults a missing category to SYSTEM")
    void defaultsCategoryToSystem() {
        IPage<com.ulticode.modules.notification.entity.Notification> page = new Page<>(1, 20);
        when(notificationMapper.selectDedupedAnnouncements(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        adapter.selectSystemNotifications(1, 20, null, null, " ", null, null, null);

        ArgumentCaptor<String> category = ArgumentCaptor.forClass(String.class);
        verify(notificationMapper).selectDedupedAnnouncements(
                any(Page.class), category.capture(), any(), any(), any(), any(), any());
        assertThat(category.getValue()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("legacy signature delegates with the SYSTEM category")
    void legacySignatureDefaultsToSystem() {
        IPage<com.ulticode.modules.notification.entity.Notification> page = new Page<>(1, 20);
        when(notificationMapper.selectDedupedAnnouncements(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(page);

        adapter.selectSystemNotifications(1, 20, null, null, null, null, null);

        ArgumentCaptor<String> category = ArgumentCaptor.forClass(String.class);
        verify(notificationMapper).selectDedupedAnnouncements(
                any(Page.class), category.capture(), any(), any(), any(), any(), any());
        assertThat(category.getValue()).isEqualTo("SYSTEM");
    }
}
