package com.ulticode.app.api.dto;

/**
 * Raw Problem-owned test-case projection for the judge queue.
 *
 * <p>The queue owns JSON input parsing and construction of its judge-ready
 * case type. This record deliberately carries the {@code test_cases} storage
 * values only and does not import queue entities or parsing implementations.
 */
public record ProblemJudgingCaseDTO(
        String id,
        Integer testOrder,
        String inputText,
        String outputText,
        String inputs,
        Boolean isHidden,
        Boolean isSample) {}
