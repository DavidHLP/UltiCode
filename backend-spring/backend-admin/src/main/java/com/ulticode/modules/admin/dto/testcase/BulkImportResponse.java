package com.ulticode.modules.admin.dto.testcase;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response for bulk test-case import reporting the number of cases created.
 */
@Getter
@AllArgsConstructor
public class BulkImportResponse {
    private final int count;
}
