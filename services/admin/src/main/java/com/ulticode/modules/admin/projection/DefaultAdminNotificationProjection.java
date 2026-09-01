package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.notification.api.dto.NotificationAdminDTO;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminNotificationProjection}. Owns
 * every DTO-to-{@code AdminNotificationVO} projection rule, the sort-field
 * whitelist and the batch creator enrichment for the admin system
 * notification surface &mdash; see the interface javadoc for the deepening
 * rationale.
 *
 * <p>ADMIN-008: all notification entity/mapper imports replaced with
 * {@link NotificationAdminReadPort}. The paginated list read goes through
 * the App-owned read provider (deduplicated system announcements); creator
 * metadata is batch-loaded via {@code AdminUserEnricher.enrich} to stay
 * N+1-safe.
 *
 * <p>Mirrors the {@code DefaultAdminContestProjection} /
 * {@code DefaultAdminSubmissionProjection} shape exactly:
 * {@link org.springframework.stereotype.Service @Service} + Lombok's
 * {@link lombok.RequiredArgsConstructor} for constructor injection and
 * {@link lombok.extern.slf4j.Slf4j @Slf4j} for the SLF4J Logger, with a
 * small, focused surface that callers compose with the service's write
 * methods.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminNotificationProjection implements AdminNotificationProjection {

    /**
     * Whitelist of {@code sortBy} values accepted by the paginated list read.
     * Any value outside this set is silently dropped to {@code null} so the
     * provider-side {@code <otherwise>n.created_at</otherwise>} takes over
     * &mdash; matching the legacy behaviour carried by
     * {@code AdminNotificationServiceImpl}.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "title", "type", "category", "announcementId"
    );

    /** Hard-coded SYSTEM category used when the create request omits it. */
    private static final String SYSTEM_CATEGORY = "SYSTEM";

    private final NotificationAdminReadPort notificationAdminReadPort;
    private final AdminUserEnricher userEnricher;

    // ------------------------------------------------------------------
    // Paginated list read (query build + batch enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminNotificationVO> getSystemNotifications(AdminNotificationQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        String sortBy = query.getSortBy();
        if (sortBy != null && !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = null;
        }
        String sortOrder = query.getSortOrder();

        PageResult<NotificationAdminDTO> result;
        try {
            result = notificationAdminReadPort.selectSystemNotifications(
                    page,
                    limit,
                    query.getKeyword(),
                    query.getType(),
                    query.getCategory(),
                    query.getAnnouncementId(),
                    sortBy,
                    sortOrder);
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Notification", exception);
        }
        requirePage(result, "Notification");

        CreatorEnrichment creators = loadCreators(result.getItems());
        List<AdminNotificationVO> vos = toAdminVOList(result.getItems(), creators.users());
        DegradationStatus status = mergeStatus(result.getDegradationStatus(), creators.status());
        return PageResult.of(vos, result.getTotal(), pageRequest, status);
    }

    // ------------------------------------------------------------------
    // Projection helpers (DTO -> AdminNotificationVO)
    // ------------------------------------------------------------------

    @Override
    public AdminNotificationVO toAdminVO(NotificationAdminDTO notification) {
        if (notification == null) {
            return null;
        }
        CreatorEnrichment creators = loadCreators(Collections.singletonList(notification));
        return toAdminVOList(Collections.singletonList(notification), creators.users()).stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public AdminNotificationVO buildAnnouncementVO(CreateSystemNotificationRequest request,
                                                   String category,
                                                   String announcementId) {
        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setAnnouncementId(announcementId);
        vo.setTitle(request.getTitle());
        vo.setContent(request.getContent());
        vo.setType(request.getType());
        vo.setCategory(category != null ? category : SYSTEM_CATEGORY);
        return vo;
    }

    /**
     * Project a list of {@link NotificationAdminDTO} rows to the admin VO
     * shape with one batched creator enrichment lookup. N+1-safe: every
     * distinct {@code createdBy} is collapsed into a single
     * {@code AdminUserEnricher.enrich} call.
     */
    private CreatorEnrichment loadCreators(List<NotificationAdminDTO> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return new CreatorEnrichment(Collections.emptyMap(), DegradationStatus.OK);
        }

        Set<String> creatorIds = notifications.stream()
                .filter(java.util.Objects::nonNull)
                .map(NotificationAdminDTO::createdBy)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (creatorIds.isEmpty()) {
            return new CreatorEnrichment(Collections.emptyMap(), DegradationStatus.OK);
        }

        AdminUserEnricher.EnrichedUsers result;
        try {
            result = userEnricher.enrichWithStatus(creatorIds);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Auth/App user", exception);
        }
        if (result == null || result.status() == null
                || result.status() == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable("Auth/App user");
        }
        return new CreatorEnrichment(result.users(), result.status());
    }

    private List<AdminNotificationVO> toAdminVOList(
            List<NotificationAdminDTO> notifications, Map<String, AdminUserSummary> userMap) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyList();
        }
        return notifications.stream()
                .map(n -> toAdminVO(n, userMap))
                .collect(Collectors.toList());
    }

    private record CreatorEnrichment(
            Map<String, AdminUserSummary> users, DegradationStatus status) {
    }

    private static DegradationStatus mergeStatus(
            DegradationStatus pageStatus, DegradationStatus enrichmentStatus) {
        if (pageStatus == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable("Notification");
        }
        if (pageStatus == DegradationStatus.PARTIAL
                || enrichmentStatus == DegradationStatus.PARTIAL) {
            return DegradationStatus.PARTIAL;
        }
        return pageStatus == null ? DegradationStatus.OK : pageStatus;
    }

    private static void requirePage(PageResult<?> page, String owner) {
        if (page == null || page.getItems() == null
                || page.getItems().stream().anyMatch(java.util.Objects::isNull)
                || page.getTotal() == null
                || page.getTotal() < 0
                || page.getDegradationStatus() == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable(owner);
        }
    }

    /**
     * Pure shape rule for a single notification with a pre-loaded creator
     * map. When {@code notification} is {@code null} returns {@code null};
     * when the {@code createdBy} entry is absent the {@code creator} field
     * is left null (matching the legacy behaviour &mdash; only the
     * system-announcement branch sets createdBy metadata).
     */
    private AdminNotificationVO toAdminVO(NotificationAdminDTO notification, Map<String, AdminUserSummary> userMap) {
        if (notification == null) {
            return null;
        }

        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setId(notification.id());
        vo.setAnnouncementId(notification.announcementId());
        vo.setTitle(notification.title());
        vo.setContent(notification.body());
        vo.setType(notification.type());
        vo.setCategory(notification.category());
        vo.setCreatedAt(notification.createdAt());

        String creatorId = notification.createdBy();
        if (StringUtils.hasText(creatorId) && userMap.containsKey(creatorId)) {
            AdminUserSummary creator = userMap.get(creatorId);
            AdminNotificationVO.CreatorInfo creatorInfo = new AdminNotificationVO.CreatorInfo();
            creatorInfo.setId(creator.accountId());
            creatorInfo.setUsername(creator.username());
            creatorInfo.setAvatar(creator.avatar());
            vo.setCreator(creatorInfo);
        }

        return vo;
    }
}
