package com.ulticode.app.api.dto;

import com.ulticode.app.api.command.ForumPostModerationCommand;

import java.io.Serializable;

/** Entity-free result returned by the App-owned forum-post mutation seam. */
public record ForumPostModerationResultDTO(
        String postId,
        ForumPostModerationCommand.Action action,
        String authorUserId,
        Boolean previousState,
        String previousReason) implements Serializable {
    private static final long serialVersionUID = 1L;

}
