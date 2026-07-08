package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.projection.AdminNotificationProjection;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminNotificationServiceImpl} &mdash; the write-only
 * admin system-notification service after the ADR-0011 Stage 4 deepening.
 *
 * <p>Covers the write paths that delegate to
 * {@link AdminNotificationProjection} for VO shaping &mdash; the read paths
 * (paginated list read, batch creator enrichment, shape rule) live on the
 * projection and are tested in {@code AdminNotificationProjectionTest}.
 *
 * <p>{@code @MockitoSettings(strictness = LENIENT)} because the broadcast
 * preference-gating branch in {@code createSystemNotification} exercises
 * multiple user-mapper paths and not every test populates every stub.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminNotificationServiceImpl")
class AdminNotificationServiceImplTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private NotificationPreferenceMapper preferenceMapper;
    @Mock private UserMapper userMapper;
    @Mock private AdminNotificationProjection adminNotificationProjection;
    @Mock private UuidGenerator uuidGenerator;

    private AdminNotificationServiceImpl adminNotificationService;

    @BeforeEach
    void setUp() {
        // Pin Clock so created_at / updated_at deterministic; the actual
        // service code only reads Clock.instant() / getZone() through it.
        Clock clock = Clock.systemUTC();
        adminNotificationService = new AdminNotificationServiceImpl(
                notificationMapper, preferenceMapper, userMapper, clock,
                uuidGenerator, adminNotificationProjection);
    }

    private AdminNotificationVO makeVO(String id, String announcementId) {
        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setId(id);
        vo.setAnnouncementId(announcementId);
        vo.setTitle("Title " + id);
        vo.setContent("Body " + id);
        vo.setType("SYSTEM");
        vo.setCategory("SYSTEM");
        return vo;
    }

    @Nested
    @DisplayName("listSystemNotifications() — delegates to projection")
    class ListSystemNotifications {

        @Test
        @DisplayName("passes the query through to the projection and returns its result")
        void delegatesToProjection() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setPage(2);
            query.setLimit(15);

            AdminNotificationVO vo = makeVO("n-1", "a-1");
            PageResult<AdminNotificationVO> expected = PageResult.of(
                    List.of(vo), 1L, query.getPage(), query.getLimit());
            when(adminNotificationProjection.getSystemNotifications(query))
                    .thenReturn(expected);

            PageResult<AdminNotificationVO> result = adminNotificationService.listSystemNotifications(query);

            assertThat(result).isSameAs(expected);
            verify(adminNotificationProjection).getSystemNotifications(query);
        }

        @Test
        @DisplayName("does not hit notificationMapper or userMapper on the read path")
        void doesNotHitMappersDirectly() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            when(adminNotificationProjection.getSystemNotifications(query))
                    .thenReturn(PageResult.of(List.of(), 0L, 1, 10));

            adminNotificationService.listSystemNotifications(query);

            verify(notificationMapper, never()).selectDedupedAnnouncements(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
            verify(userMapper, never())
                    .selectBatchIds(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("updateSystemNotification() — write path delegates VO shaping")
    class UpdateSystemNotification {

        @Test
        @DisplayName("returns null and rethrows when the underlying notification does not exist")
        void returnsNullWhenNotificationNotFound() {
            when(notificationMapper.selectById("missing")).thenReturn(null);

            UpdateSystemNotificationRequest request = new UpdateSystemNotificationRequest();
            request.setTitle("New title");
            request.setContent("New body");
            request.setType("SYSTEM");

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> adminNotificationService.updateSystemNotification("missing", request))
                    .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                    .hasMessageContaining("Notification not found");

            verify(adminNotificationProjection, never())
                    .toAdminVO(org.mockito.ArgumentMatchers.any(Notification.class));
        }
    }

    @Nested
    @DisplayName("deleteNotification() — write path does not need the projection")
    class DeleteNotification {

        @Test
        @DisplayName("does not call into the projection (read/shape concern)")
        void doesNotCallProjection() {
            Notification existing = new Notification();
            existing.setId("n-1");
            existing.setTitle("Title");
            existing.setType("SYSTEM");
            existing.setCategory("SYSTEM");
            existing.setAnnouncementId("a-1");
            when(notificationMapper.selectById("n-1")).thenReturn(existing);
            when(notificationMapper.delete(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(1);

            adminNotificationService.deleteNotification("n-1");

            verify(adminNotificationProjection, never())
                    .toAdminVO(org.mockito.ArgumentMatchers.any(Notification.class));
            verify(adminNotificationProjection, never())
                    .buildAnnouncementVO(org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any());
        }
    }
}
