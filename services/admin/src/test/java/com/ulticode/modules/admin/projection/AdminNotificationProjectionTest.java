package com.ulticode.modules.admin.projection;

import com.ulticode.notification.api.dto.NotificationAdminDTO;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminNotificationProjection} &mdash; the
 * read-side deep module for the admin system-notification surface
 * (ADMIN-008: DTO-based over {@link NotificationAdminReadPort}).
 *
 * <p>Covers the projection rules:
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
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAdminNotificationProjection")
class AdminNotificationProjectionTest {

    @Mock private NotificationAdminReadPort notificationAdminReadPort;
    @Mock private AdminUserEnricher userEnricher;

    private DefaultAdminNotificationProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminNotificationProjection(notificationAdminReadPort, userEnricher);
    }

    private NotificationAdminDTO makeSystemNotification(String id, String announcementId, String creatorId) {
        return new NotificationAdminDTO(
                id,
                announcementId,
                "Title " + id,
                "Body " + id,
                "SYSTEM",
                "SYSTEM",
                LocalDateTime.of(2026, 7, 1, 0, 0),
                creatorId);
    }

    @Nested
    @DisplayName("getSystemNotifications()")
    class GetSystemNotifications {

        @Test
        @DisplayName("delegates to the read port with normalised pagination (default 10)")
        void delegatesWithNormalisedPagination() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setKeyword("test");
            query.setCategory("SECURITY");
            query.setPage(3);
            query.setLimit(25);

            when(notificationAdminReadPort.selectSystemNotifications(
                    eq(3), eq(25), eq("test"), any(), eq("SECURITY"), any(), any(), any()))
                    .thenReturn(PageResult.of(List.of(), 0L, 3, 25));

            PageResult<AdminNotificationVO> result = projection.getSystemNotifications(query);

            assertThat(result.getPage()).isEqualTo(3);
            assertThat(result.getPageSize()).isEqualTo(25);
            assertThat(result.getTotal()).isEqualTo(0);
            assertThat(result.getItems()).isEmpty();
        }

        @Test
        @DisplayName("page-size over 100 is capped via PaginationRequest (999 -> 100)")
        void pageSizeCappedAtHundred() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setPage(1);
            query.setLimit(999);

            when(notificationAdminReadPort.selectSystemNotifications(
                    eq(1), eq(100), any(), any(), any(), any(), any(), any()))
                    .thenReturn(PageResult.of(List.of(), 0L, 1, 100));

            PageResult<AdminNotificationVO> result = projection.getSystemNotifications(query);

            assertThat(result.getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("invalid sortBy is dropped to null (mapper-level default takes over)")
        void invalidSortByDroppedToNull() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setSortBy("DROP_TABLE");
            query.setSortOrder("desc");

            ArgumentCaptor<String> sortByCaptor = ArgumentCaptor.forClass(String.class);
            when(notificationAdminReadPort.selectSystemNotifications(
                    anyInt(), anyInt(), any(), any(), any(), any(),
                    sortByCaptor.capture(), any()))
                    .thenReturn(PageResult.of(List.of(), 0L, 1, 10));

            projection.getSystemNotifications(query);

            assertThat(sortByCaptor.getValue()).isNull();
        }

        @Test
        @DisplayName("valid sortBy and sortOrder pass through to the read port")
        void validSortByPassedThrough() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            query.setSortBy("announcementId");
            query.setSortOrder("asc");

            ArgumentCaptor<String> sortByCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> sortOrderCaptor = ArgumentCaptor.forClass(String.class);
            when(notificationAdminReadPort.selectSystemNotifications(
                    anyInt(), anyInt(), any(), any(), any(), any(),
                    sortByCaptor.capture(), sortOrderCaptor.capture()))
                    .thenReturn(PageResult.of(List.of(), 0L, 1, 10));

            projection.getSystemNotifications(query);

            assertThat(sortByCaptor.getValue()).isEqualTo("announcementId");
            assertThat(sortOrderCaptor.getValue()).isEqualTo("asc");
        }

        @Test
        @DisplayName("projection shape runs after page query with batch creator enrichment")
        void projectionShapeRunsAfterPageQuery() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();

            NotificationAdminDTO n1 = makeSystemNotification("n-1", "a-1", "u-1");
            NotificationAdminDTO n2 = makeSystemNotification("n-2", "a-2", "u-1");
            NotificationAdminDTO n3 = makeSystemNotification("n-3", "a-3", "u-2");

            when(notificationAdminReadPort.selectSystemNotifications(
                    anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(PageResult.of(List.of(n1, n2, n3), 3L, 1, 10));
            when(userEnricher.enrich(anySet())).thenReturn(Map.of(
                    "u-1", new AdminUserSummary("u-1", "alice", "role1", "Alice",
                            "https://example.com/avatar/u-1.png", "alice@example.com"),
                    "u-2", new AdminUserSummary("u-2", "bob", "role2", "Bob",
                            "https://example.com/avatar/u-2.png", "bob@example.com")));

            PageResult<AdminNotificationVO> result = projection.getSystemNotifications(query);

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
        @DisplayName("rows without creator ids skip enrichment (no N+1 lookups)")
        void noCreatorIdsSkipsEnrichment() {
            AdminNotificationQueryDTO query = new AdminNotificationQueryDTO();
            NotificationAdminDTO n = makeSystemNotification("n-1", "a-1", null);

            when(notificationAdminReadPort.selectSystemNotifications(
                    anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(PageResult.of(List.of(n), 1L, 1, 10));

            PageResult<AdminNotificationVO> result = projection.getSystemNotifications(query);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getCreator()).isNull();
            verify(userEnricher, never()).enrich(anySet());
        }
    }

    @Nested
    @DisplayName("toAdminVO()")
    class ToAdminVOSingle {

        @Test
        @DisplayName("returns null for a null DTO")
        void returnsNullForNull() {
            assertThat(projection.toAdminVO((NotificationAdminDTO) null)).isNull();
        }

        @Test
        @DisplayName("populates every VO field and enriches the creator")
        void populatesAllFields() {
            NotificationAdminDTO n = makeSystemNotification("n-1", "a-1", "u-1");
            when(userEnricher.enrich(anySet())).thenReturn(Map.of(
                    "u-1", new AdminUserSummary("u-1", "alice", "role1", "Alice",
                            "https://example.com/avatar/u-1.png", "alice@example.com")));

            AdminNotificationVO vo = projection.toAdminVO(n);

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
        @DisplayName("leaves creator null when the DTO carries no createdBy")
        void creatorNullWhenNoCreatedBy() {
            NotificationAdminDTO n = makeSystemNotification("n-1", "a-1", null);

            AdminNotificationVO vo = projection.toAdminVO(n);

            assertThat(vo.getCreator()).isNull();
            verify(userEnricher, never()).enrich(anySet());
        }

        @Test
        @DisplayName("leaves creator null when the creator id is not resolvable")
        void creatorNullWhenUserMissing() {
            NotificationAdminDTO n = makeSystemNotification("n-1", "a-1", "missing-user");
            when(userEnricher.enrich(anySet())).thenReturn(Collections.emptyMap());

            AdminNotificationVO vo = projection.toAdminVO(n);

            assertThat(vo.getCreator()).isNull();
        }
    }

    @Nested
    @DisplayName("buildAnnouncementVO() — all-opted-out broadcast intent")
    class BuildAnnouncementVO {

        @Test
        @DisplayName("carries only the announcement metadata")
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
