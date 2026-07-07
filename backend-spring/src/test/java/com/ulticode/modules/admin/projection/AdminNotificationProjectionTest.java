package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminNotificationProjection} &mdash; the
 * read-side deep module lifted out of {@code AdminNotificationServiceImpl}
 * per ADR-0011 Stage 4.
 *
 * <p>Covers the projection rules that previously lived inside
 * {@code AdminNotificationServiceImpl}:
 * <ul>
 *   <li>{@code getSystemNotifications} &mdash; pagination defaults,
 *       sort-field whitelist validation, sortOrder passthrough.</li>
 *   <li>{@code toAdminVO} (single + list) &mdash; shape rule, N+1-safe
 *       batch creator enrichment, null-safety.</li>
 *   <li>{@code buildAnnouncementVO} &mdash; the lightweight VO used when
 *       every recipient opted out of a broadcast.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminNotificationProjection")
class AdminNotificationProjectionTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private UserMapper userMapper;

    private DefaultAdminNotificationProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminNotificationProjection(notificationMapper, userMapper);
    }

    private Notification makeSystemNotification(String id, String announcementId, String creatorId) {
        Notification n = new Notification();
        n.setId(id);
        n.setAnnouncementId(announcementId);
        n.setTitle("Title " + id);
        n.setBody("Body " + id);
        n.setType("SYSTEM");
        n.setCategory("SYSTEM");
        n.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("createdBy", creatorId);
        metadata.put("isSystemAnnouncement", true);
        n.setMetadata(metadata);
        return n;
    }

    private User makeUser(String id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setAvatar("https://example.com/avatar/" + id + ".png");
        return u;
    }

    @Nested
    @DisplayName("getSystemNotifications()")
    class GetSystemNotifications {

        @Test
        @DisplayName("delegates to notificationMapper.selectDedupedAnnouncements with normalised pagination (default 10)")
        void delegatesWithNormalisedPagination() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setKeyword("test");
            query.setPage(3);
            query.setLimit(25);

            IPage<Notification> empty = new Page<>(3, 25, 0);
            when(notificationMapper.selectDedupedAnnouncements(
                    any(Page.class), eq("SYSTEM"), eq("test"),
                    any(), any(), any(), any()))
                    .thenReturn((Page<Notification>) empty);

            var result = projection.getSystemNotifications(query);

            assertThat(result.getPage()).isEqualTo(3);
            assertThat(result.getPageSize()).isEqualTo(25);
            assertThat(result.getTotal()).isEqualTo(0);
            assertThat(result.getItems()).isEmpty();
        }

        @Test
        @DisplayName("page-size cap is enforced by PaginationRequest (limit 999 -> 100)")
        void pageSizeCapIsEnforced() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setPage(1);
            query.setLimit(999);

            IPage<Notification> empty = new Page<>(1, 100, 0);
            when(notificationMapper.selectDedupedAnnouncements(
                    any(Page.class), eq("SYSTEM"), any(),
                    any(), any(), any(), any()))
                    .thenReturn((Page<Notification>) empty);

            var result = projection.getSystemNotifications(query);

            assertThat(result.getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("invalid sortBy is silently dropped to null (mapper-level default takes over)")
        void invalidSortByIsSilentlyDropped() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setSortBy("DROP_TABLE");
            query.setSortOrder("desc");

            ArgumentCaptor<String> sortByCaptor = ArgumentCaptor.forClass(String.class);

            IPage<Notification> empty = new Page<>(1, 10, 0);
            when(notificationMapper.selectDedupedAnnouncements(
                    any(Page.class), eq("SYSTEM"), any(),
                    any(), any(), sortByCaptor.capture(), any()))
                    .thenReturn((Page<Notification>) empty);

            projection.getSystemNotifications(query);

            assertThat(sortByCaptor.getValue()).isNull();
        }

        @Test
        @DisplayName("valid sortBy values pass through to the mapper verbatim")
        void validSortByPassesThrough() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setSortBy("announcementId");
            query.setSortOrder("asc");

            ArgumentCaptor<String> sortByCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> sortOrderCaptor = ArgumentCaptor.forClass(String.class);

            IPage<Notification> empty = new Page<>(1, 10, 0);
            when(notificationMapper.selectDedupedAnnouncements(
                    any(Page.class), eq("SYSTEM"), any(),
                    any(), any(), sortByCaptor.capture(), sortOrderCaptor.capture()))
                    .thenReturn((Page<Notification>) empty);

            projection.getSystemNotifications(query);

            assertThat(sortByCaptor.getValue()).isEqualTo("announcementId");
            assertThat(sortOrderCaptor.getValue()).isEqualTo("asc");
        }

        @Test
        @DisplayName("projection shape + creator enrichment runs after the page query")
        void projectionShapeRunsAfterPageQuery() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();

            Notification n1 = makeSystemNotification("n-1", "a-1", "u-1");
            Notification n2 = makeSystemNotification("n-2", "a-2", "u-1");
            Notification n3 = makeSystemNotification("n-3", "a-3", "u-2");
            IPage<Notification> page = new Page<>(1, 10, 3);
            page.setRecords(List.of(n1, n2, n3));

            when(notificationMapper.selectDedupedAnnouncements(
                    any(Page.class), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);
            when(userMapper.selectBatchIds(any()))
                    .thenReturn(List.of(
                            makeUser("u-1", "alice"),
                            makeUser("u-2", "bob")));

            var result = projection.getSystemNotifications(query);

            assertThat(result.getItems()).hasSize(3);
            assertThat(result.getItems()).extracting(AdminNotificationVO::getId)
                    .containsExactly("n-1", "n-2", "n-3");
            assertThat(result.getItems()).extracting(AdminNotificationVO::getTitle)
                    .containsExactly("Title n-1", "Title n-2", "Title n-3");
            assertThat(result.getItems()).extracting(AdminNotificationVO::getContent)
                    .containsExactly("Body n-1", "Body n-2", "Body n-3");
            assertThat(result.getItems()).extracting(v -> v.getCreator().getUsername())
                    .containsExactly("alice", "alice", "bob");
            assertThat(result.getItems()).extracting(v -> v.getCreator().getAvatar())
                    .containsExactly("https://example.com/avatar/u-1.png",
                                     "https://example.com/avatar/u-1.png",
                                     "https://example.com/avatar/u-2.png");
            assertThat(result.getTotal()).isEqualTo(3);
        }

        @Test
        @DisplayName("no creator IDs in metadata -> userMapper is not queried (N+1 safe)")
        void noCreatorIdsSkipsUserLookup() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();

            Notification n = new Notification();
            n.setId("n-1");
            n.setTitle("Title");
            n.setBody("Body");
            n.setType("SYSTEM");
            n.setCategory("SYSTEM");
            // metadata intentionally null
            IPage<Notification> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(n));

            when(notificationMapper.selectDedupedAnnouncements(
                    any(Page.class), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);

            var result = projection.getSystemNotifications(query);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getCreator()).isNull();
            verify(userMapper, never()).selectBatchIds(any());
        }
    }

    @Nested
    @DisplayName("toAdminVO() — single entity shortcut")
    class ToAdminVOSingle {

        @Test
        @DisplayName("returns null when the input is null")
        void returnsNullForNull() {
            assertThat(projection.toAdminVO((Notification) null)).isNull();
        }

        @Test
        @DisplayName("populates every scalar field from the entity")
        void populatesScalars() {
            Notification n = makeSystemNotification("n-1", "a-1", "u-1");
            when(userMapper.selectBatchIds(any()))
                    .thenReturn(List.of(makeUser("u-1", "alice")));

            AdminNotificationVO vo = projection.toAdminVO(n);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo("n-1");
            assertThat(vo.getAnnouncementId()).isEqualTo("a-1");
            assertThat(vo.getTitle()).isEqualTo("Title n-1");
            assertThat(vo.getContent()).isEqualTo("Body n-1");
            assertThat(vo.getType()).isEqualTo("SYSTEM");
            assertThat(vo.getCategory()).isEqualTo("SYSTEM");
            assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
            assertThat(vo.getCreator()).isNotNull();
            assertThat(vo.getCreator().getId()).isEqualTo("u-1");
            assertThat(vo.getCreator().getUsername()).isEqualTo("alice");
            assertThat(vo.getCreator().getAvatar()).isEqualTo("https://example.com/avatar/u-1.png");
        }

        @Test
        @DisplayName("creator stays null when metadata.createdBy is missing")
        void nullMetadataCreator() {
            Notification n = new Notification();
            n.setId("n-1");
            n.setTitle("Title");
            n.setBody("Body");
            n.setType("SYSTEM");
            n.setCategory("SYSTEM");
            n.setMetadata(null);

            AdminNotificationVO vo = projection.toAdminVO(n);

            assertThat(vo).isNotNull();
            assertThat(vo.getCreator()).isNull();
            verify(userMapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("creator stays null when the user is not found in the batch lookup")
        void creatorNullWhenUserNotFound() {
            Notification n = makeSystemNotification("n-1", "a-1", "missing-user");
            when(userMapper.selectBatchIds(any())).thenReturn(List.of());

            AdminNotificationVO vo = projection.toAdminVO(n);

            assertThat(vo).isNotNull();
            assertThat(vo.getCreator()).isNull();
        }
    }

    @Nested
    @DisplayName("buildAnnouncementVO() — all-opted-out broadcast")
    class BuildAnnouncementVO {

        @Test
        @DisplayName("carries announcementId, title, content, type and category")
        void carriesAnnouncementMetadata() {
            CreateSystemNotificationRequest request = new CreateSystemNotificationRequest();
            request.setTitle("Heads-up");
            request.setContent("Maintenance window");
            request.setType("SYSTEM");

            AdminNotificationVO vo = projection.buildAnnouncementVO(request, "MARKETING", "ann-id-1");

            assertThat(vo.getAnnouncementId()).isEqualTo("ann-id-1");
            assertThat(vo.getTitle()).isEqualTo("Heads-up");
            assertThat(vo.getContent()).isEqualTo("Maintenance window");
            assertThat(vo.getType()).isEqualTo("SYSTEM");
            assertThat(vo.getCategory()).isEqualTo("MARKETING");
            assertThat(vo.getCreator()).isNull();
            assertThat(vo.getId()).isNull();
            assertThat(vo.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("null category falls back to SYSTEM")
        void nullCategoryFallsBackToSystem() {
            CreateSystemNotificationRequest request = new CreateSystemNotificationRequest();
            request.setTitle("T");
            request.setContent("C");
            request.setType("SYSTEM");

            AdminNotificationVO vo = projection.buildAnnouncementVO(request, null, "ann-id-2");

            assertThat(vo.getCategory()).isEqualTo("SYSTEM");
        }
    }
}
