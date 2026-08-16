package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * DTO for problem example data.
 *
 * @param id            the example ID (as String)
 * @param exampleOrder  the example order
 * @param inputText     the input text
 * @param outputText    the output text
 * @param inputs        the inputs string (for JudgingCaseInputs.parse)
 */
public record ProblemExampleDTO(
    String id,
    Integer exampleOrder,
    String inputText,
    String outputText,
    String inputs
) implements Serializable {
    private static final long serialVersionUID = 1L;
}