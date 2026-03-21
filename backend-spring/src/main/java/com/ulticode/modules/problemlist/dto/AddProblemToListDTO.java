package com.ulticode.modules.problemlist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for adding a problem to a list.
 */
@Data
public class AddProblemToListDTO {
    @NotNull(message = "Problem ID is required")
    private Long problemId;
}
