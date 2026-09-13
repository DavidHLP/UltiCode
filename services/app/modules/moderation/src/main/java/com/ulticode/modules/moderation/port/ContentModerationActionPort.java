package com.ulticode.modules.moderation.port;

import com.ulticode.app.api.dto.ContentLifecycleState;

/**
 * App-owned content action seam used by moderation entry points.
 */
public interface ContentModerationActionPort {

    /**
     * Soft-delete content while preserving the acting moderator identity.
     *
     * @param contentType content-owner vocabulary key
     * @param contentId content identifier
     * @param actorId verified actor performing the action
     * @return the resulting content lifecycle state
     */
    ContentLifecycleState deleteContent(String contentType, String contentId, String actorId);
}
