package com.ulticode.modules.moderation.port;

import com.ulticode.app.api.dto.ContentLifecycleState;

/**
 * Port for moderation to apply content actions (delete/hide) across families.
 * Legacy-side adapter implements this via AdminForumService/AdminSolutionService.
 */
public interface ModerationContentActionPort {
    ContentLifecycleState deleteContent(String contentType, String contentId);
}
