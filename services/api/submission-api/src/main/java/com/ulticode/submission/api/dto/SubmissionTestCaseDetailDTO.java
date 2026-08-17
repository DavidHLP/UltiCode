package com.ulticode.submission.api.dto;

import com.ulticode.domain.submission.enums.CaseScope;

import java.io.Serializable;
import java.util.List;

/**
 * Entity-free projection of one {@code test_details} row entry for the
 * Admin submission detail read.
 *
 * <p>Mirrors the App submission module's entity-nested
 * {@code Submission.TestCaseDetail} shape so the Admin edge can rebuild
 * its wire VO (and JSON persistence contract) without importing the
 * entity. {@code caseId} / {@code caseScope} are null on legacy rows
 * written before P0-1.
 */
public record SubmissionTestCaseDetailDTO(
        String status,
        Integer time,
        Double memory,
        String detail,
        String output,
        String expectedOutput,
        List<InputParam> inputs,
        String caseId,
        CaseScope caseScope) implements Serializable {
    private static final long serialVersionUID = 1L;


    public SubmissionTestCaseDetailDTO {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    /** One input parameter of a test case. */
    public record InputParam(
            String id,
            String label,
            String name,
            String value) implements Serializable {
        private static final long serialVersionUID = 1L;

    }
}
