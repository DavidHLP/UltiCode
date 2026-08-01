package com.ulticode.app.api.dto;

/**
 * Lightweight DTO for forum post search index results.
 *
 * <p>Carries only the fields the search module needs — never exposes
 * the internal {@code ForumPost} entity.
 *
 * @param id        post ID
 * @param title     post title
 * @param excerpt   post excerpt
 * @param permalink post permalink
 */
public record ForumPostIndexDTO(
        String id,
        String title,
        String excerpt,
        String permalink
) {}
