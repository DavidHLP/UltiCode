package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Request to import problems")
public class ImportProblemsRequestDTO {

    public static final int MAX_IMPORT_SIZE = 500;

    @NotEmpty(message = "Problems list cannot be empty")
    @Size(max = MAX_IMPORT_SIZE, message = "Cannot import more than " + MAX_IMPORT_SIZE + " problems at once")
    @Schema(description = "List of problems to import (max " + MAX_IMPORT_SIZE + ")")
    private List<ImportProblemItemDTO> problems;

    @Schema(description = "Conflict resolution strategy", allowableValues = {"skip", "update", "create_new"})
    private String onConflict = "skip";
}
