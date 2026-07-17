package com.ulticode.modules.admin.dto.testcase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request body for bulk test-case import.
 *
 * <p>Carries the cases to create plus the {@code replaceExisting} policy so the
 * backend honors the import dialog's "replace existing" toggle in one atomic
 * request rather than asking the client to orchestrate delete-then-insert.
 */
@Data
public class BulkImportTestCasesDTO {

    @Valid
    @NotEmpty(message = "testCases must contain at least one item")
    @Size(min = 1, max = 500, message = "List must contain 1-500 items")
    private List<CreateTestCaseDTO> testCases;

    /**
     * When {@code true}, all existing test cases for the problem are deleted
     * within the same transaction before the new batch is inserted. {@code null}
     * is treated as {@code false} (append-only import).
     */
    private Boolean replaceExisting;
}
