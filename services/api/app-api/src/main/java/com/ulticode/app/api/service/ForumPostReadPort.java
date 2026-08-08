package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ForumPostIndexDTO;

import java.util.List;

/**
 * Read-side port for forum post queries owned by the App service.
 *
 * <p>Consumed by legacy modules (search) that previously imported
 * {@code ForumPostMapper} directly. This port returns primitive types or
 * DTOs — never the internal {@code ForumPost} entity.
 *
 * <p>P7-RELOCATE-FORUM-001: extracted when the forum family relocated
 * from backend-legacy to backend-app.
 */
public interface ForumPostReadPort {

    /**
     * Search published, non-deleted forum posts by title or excerpt LIKE match.
     *
     * @param query search keyword
     * @param limit maximum results
     * @return list of matching posts as index DTOs
     */
    List<ForumPostIndexDTO> searchForIndex(String query, int limit);
}
