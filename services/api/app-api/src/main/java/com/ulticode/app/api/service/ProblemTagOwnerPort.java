package com.ulticode.app.api.service;

import java.time.LocalDateTime;

/**
 * Owner-only write surface for the {@code problem_tags} table.
 *
 * <p>The command is a complete row shape. The Problem provider owns the
 * implementation (insert/update/delete plus the relation-repoint + usage
 * recount of {@code mergeTags}), while administrative consumers depend only
 * on this entity-free contract. Conflict detection (name/slug uniqueness)
 * stays on the read side ({@link ProblemAdminReadPort#tagNameExists} /
 * {@link ProblemAdminReadPort#tagSlugExists}) so the Admin edge keeps its
 * own error semantics.
 */
public interface ProblemTagOwnerPort {

    /**
     * Insert one complete problem-tag row.
     */
    void createTag(TagWrite command);

    /**
     * Persist one complete problem-tag row update.
     */
    void updateTag(TagWrite command);

    /**
     * Delete one problem tag by primary key.
     */
    void deleteTag(String id);

    /**
     * Repoint every relation from {@code sourceId} to {@code targetTagId},
     * delete the source tag and recount the target's usage count in one
     * owner transaction.
     */
    void mergeTags(String sourceId, String targetTagId);

    /**
     * Complete problem-tag row command. Field order is part of the contract.
     */
    record TagWrite(String id, String label, String slug, String description,
                    String color, Integer usageCount,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
