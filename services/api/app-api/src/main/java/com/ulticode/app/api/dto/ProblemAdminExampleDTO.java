package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Entity-free problem-example projection (statement sample input/output).
 *
 * <p>Carries the persisted {@code problem_examples} columns; the Admin edge
 * parses {@code inputs} JSON into its own VO shape, exactly as the legacy
 * entity mapping did.
 */
public record ProblemAdminExampleDTO(
        String id,
        Integer exampleOrder,
        String inputText,
        String outputText,
        String explanation,
        String inputs) implements Serializable {
    private static final long serialVersionUID = 1L;

}
