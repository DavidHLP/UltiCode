package com.ulticode.modules.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Admin request DTO for fully replacing the problem set of a problem
 * list.
 */
@Data
public class UpdateProblemsRequest {

    @Valid
    private List<ProblemEntry> problems;

    @Data
    public static class ProblemEntry {

        @NotNull(message = "Problem ID is required")
        private Long problemId;

        @NotNull(message = "Sort order is required")
        private Integer sortOrder;
    }
}
