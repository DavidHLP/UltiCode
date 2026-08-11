package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.NotificationAdminDTO;
import com.ulticode.common.response.PageResult;

/**
 * Entity-free notification read port consumed by backend-admin
 * projections/services after the notification family stays App-owned
 * (ADMIN-008).
 *
 * <p>Method set is the exact minimum derived from the verified admin
 * consumer call sites: the paginated deduplicated system-announcement list
 * (admin notification panel) and single-row read-back for the write
 * response shape. The provider lives in {@code backend-app} and owns the
 * {@code notifications} table; backend-admin never imports notification
 * entities or mappers.
 *
 * @author ulticode
 */
public interface NotificationAdminReadPort {

    /**
     * Paginated list of deduplicated system announcements without a category filter.
     *
     * @param page           1-based page
     * @param size           page size
     * @param keyword        optional title keyword (nullable)
     * @param type           optional type filter (nullable)
     * @param announcementId optional announcement id filter (nullable)
     * @param sortBy         whitelisted sort field (nullable)
     * @param sortOrder      sort direction (nullable)
     * @return paginated notification DTOs
     */
    PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type,
            String announcementId, String sortBy, String sortOrder);

    /**
     * Paginated list of deduplicated system announcements with an optional
     * category filter.
     *
     * <p>The default delegates to the category-less method so existing
     * implementors remain source-compatible; the App owner adapter overrides
     * this method to apply the category predicate.
     *
     * @param page           1-based page
     * @param size           page size
     * @param keyword        optional title keyword (nullable)
     * @param type           optional type filter (nullable)
     * @param category       optional category filter (nullable; defaults to SYSTEM)
     * @param announcementId optional announcement id filter (nullable)
     * @param sortBy         whitelisted sort field: createdAt/title/type/category/announcementId (nullable; falls back to createdAt)
     * @param sortOrder      sort direction: asc/desc (nullable; falls back to desc)
     * @return paginated notification DTOs
     */
    default PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type, String category,
            String announcementId, String sortBy, String sortOrder) {
        return selectSystemNotifications(page, size, keyword, type,
                announcementId, sortBy, sortOrder);
    }

    /**
     * Fetch a single notification by id.
     *
     * @param id notification id
     * @return notification DTO, or {@code null} when not found
     */
    NotificationAdminDTO selectById(String id);
}
