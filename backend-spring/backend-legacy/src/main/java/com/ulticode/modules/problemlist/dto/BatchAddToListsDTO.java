package com.ulticode.modules.problemlist.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * DTO for batch adding/removing a problem to/from multiple lists.
 */
@Data
public class BatchAddToListsDTO {
    @NotEmpty(message = "List IDs are required")
    private List<String> listIds;
}
