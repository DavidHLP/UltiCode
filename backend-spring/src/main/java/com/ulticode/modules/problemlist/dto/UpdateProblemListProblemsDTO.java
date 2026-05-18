package com.ulticode.modules.problemlist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * DTO for updating the problems in a problem list.
 */
@Data
public class UpdateProblemListProblemsDTO {

    @Valid
    private List<ProblemEntry> problems;

    /**
     * Entry representing a problem in the list with its sort order.
     */
    @Data
    public static class ProblemEntry {
        @NotNull(message = "Problem ID is required")
        private Long problemId;

        @NotNull(message = "Sort order is required")
        private Integer sortOrder;
    }
}
