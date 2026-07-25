package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminCommentVO(
    String id,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String authorId,
    String parentId,
    String type,
    String parentEntityId,
    String parentTitle,
    AuthorInfo author,
    Boolean isFlagged,
    String flaggedReason,
    LocalDateTime flaggedAt,
    Boolean isDeleted,
    LocalDateTime deletedAt,
    String deletedBy
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthorInfo(String id, String username, String avatar) {}
}