package com.ulticode.notification.dubbo.provider;

import com.ulticode.notification.api.dto.NotificationAdminDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.notification.port.adapter.DefaultNotificationAdminReadAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationAdminReadProviderTest {

    @Mock
    private DefaultNotificationAdminReadAdapter delegate;

    @Test
    void delegatesCategoryAwareNotificationList() {
        NotificationAdminReadProvider provider = new NotificationAdminReadProvider(delegate);
        PageResult<NotificationAdminDTO> expected = PageResult.of(List.of(), 0L, 1, 10);
        when(delegate.selectSystemNotifications(
                1, 10, "title", "SYSTEM", "SECURITY", "announcement-1", "createdAt", "asc"))
                .thenReturn(expected);

        PageResult<NotificationAdminDTO> result = provider.selectSystemNotifications(
                1, 10, "title", "SYSTEM", "SECURITY", "announcement-1", "createdAt", "asc");

        assertThat(result).isSameAs(expected);
        verify(delegate).selectSystemNotifications(
                1, 10, "title", "SYSTEM", "SECURITY", "announcement-1", "createdAt", "asc");
    }

    @Test
    void preservesLegacyNotificationListOverload() {
        NotificationAdminReadProvider provider = new NotificationAdminReadProvider(delegate);
        PageResult<NotificationAdminDTO> expected = PageResult.of(List.of(), 0L, 2, 25);
        when(delegate.selectSystemNotifications(
                2, 25, null, null, "announcement-2", null, null)).thenReturn(expected);

        PageResult<NotificationAdminDTO> result = provider.selectSystemNotifications(
                2, 25, null, null, "announcement-2", null, null);

        assertThat(result).isSameAs(expected);
        verify(delegate).selectSystemNotifications(
                2, 25, null, null, "announcement-2", null, null);
    }

    @Test
    void delegatesNotificationById() {
        NotificationAdminReadProvider provider = new NotificationAdminReadProvider(delegate);
        NotificationAdminDTO expected = new NotificationAdminDTO(
                "notification-1", "announcement-1", "Title", "Body", "SYSTEM", "SYSTEM", null, "admin-1");
        when(delegate.selectById("notification-1")).thenReturn(expected);

        NotificationAdminDTO result = provider.selectById("notification-1");

        assertThat(result).isSameAs(expected);
        verify(delegate).selectById("notification-1");
    }
}
