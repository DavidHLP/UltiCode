package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Minimal published Problem projection for search indexing.
 *
 * @param id problem identifier in the search result representation
 * @param title display title
 * @param slug URL slug
 * @param difficulty optional difficulty label
 */
public record ProblemIndexDTO(String id, String title, String slug, String difficulty) implements Serializable {}
