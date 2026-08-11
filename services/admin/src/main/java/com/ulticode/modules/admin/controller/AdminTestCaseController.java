package com.ulticode.modules.admin.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.testcase.BulkImportResponse;
import com.ulticode.modules.admin.dto.testcase.BulkImportTestCasesDTO;
import com.ulticode.modules.admin.dto.testcase.CreateTestCaseDTO;
import com.ulticode.modules.admin.dto.testcase.UpdateTestCaseDTO;
import com.ulticode.modules.admin.service.AdminTestCaseService;
import com.ulticode.app.api.dto.ProblemAdminTestCaseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for test case management.
 */
@Tag(name = "Admin - Test Cases", description = "Test case management")
@Validated
@RestController
@RequestMapping("/admin/problems/{problemId}/test-cases")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminTestCaseController {

    private final AdminTestCaseService adminTestCaseService;

    @Operation(summary = "List test cases", description = "Get paginated list of test cases for a problem")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProblemAdminTestCaseDTO>> listTestCases(
            @PathVariable Long problemId,
            @RequestParam(required = false) Boolean isSample,
            @RequestParam(required = false) Boolean isHidden,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(adminTestCaseService.listTestCases(problemId, isSample, isHidden, page, limit));
    }

    @Operation(summary = "Get test case", description = "Get a single test case by ID")
    @GetMapping("/{testCaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemAdminTestCaseDTO> getTestCase(@PathVariable Long problemId, @PathVariable String testCaseId) {
        return Result.success(adminTestCaseService.getTestCase(problemId, testCaseId));
    }

    @Operation(summary = "Create test case", description = "Create a new test case for a problem")
    @RateLimit(key = "admin:testcase-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemAdminTestCaseDTO> createTestCase(
            @PathVariable Long problemId,
            @Valid @RequestBody CreateTestCaseDTO dto) {
        return Result.success(adminTestCaseService.createTestCase(problemId, dto));
    }

    @Operation(summary = "Update test case", description = "Update an existing test case")
    @RateLimit(key = "admin:testcase-update", limit = 30, period = 60)
    @PutMapping("/{testCaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemAdminTestCaseDTO> updateTestCase(
            @PathVariable Long problemId,
            @PathVariable String testCaseId,
            @Valid @RequestBody UpdateTestCaseDTO dto) {
        return Result.success(adminTestCaseService.updateTestCase(problemId, testCaseId, dto));
    }

    @Operation(summary = "Delete test case", description = "Delete a test case")
    @RateLimit(key = "admin:testcase-delete", limit = 30, period = 60)
    @DeleteMapping("/{testCaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteTestCase(@PathVariable Long problemId, @PathVariable String testCaseId) {
        adminTestCaseService.deleteTestCase(problemId, testCaseId);
        return Result.success();
    }

    @Operation(summary = "Bulk import test cases",
            description = "Create multiple test cases at once; when replaceExisting is true the existing cases are deleted first")
    @RateLimit(key = "admin:testcase-bulk", limit = 10, period = 60)
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BulkImportResponse> bulkImportTestCases(
            @PathVariable Long problemId,
            @Valid @RequestBody BulkImportTestCasesDTO dto) {
        return Result.success(adminTestCaseService.bulkImportTestCases(problemId, dto));
    }

    @Operation(summary = "Reorder test cases", description = "Update test case order by ID list")
    @RateLimit(key = "admin:testcase-reorder", limit = 30, period = 60)
    @PutMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> reorderTestCases(
            @PathVariable Long problemId,
            @Valid @RequestBody @Size(min = 1, max = 1000, message = "List must contain 1-1000 items") List<String> testCaseIds) {
        adminTestCaseService.reorderTestCases(problemId, testCaseIds);
        return Result.success();
    }

    @Operation(summary = "Export test cases", description = "Download all test cases for a problem as a JSON file")
    @RateLimit(key = "admin:testcase-export", limit = 30, period = 60)
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportTestCases(@PathVariable Long problemId) {
        String json = adminTestCaseService.exportTestCasesAsJson(problemId);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"test-cases-" + problemId + ".json\"")
                .contentLength(body.length)
                .body(body);
    }
}
