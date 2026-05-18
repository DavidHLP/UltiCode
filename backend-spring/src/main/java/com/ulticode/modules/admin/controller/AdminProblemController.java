package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for problem management
 */
@Tag(name = "Admin - Problems", description = "题目管理接口")
@RestController
@RequestMapping("/admin/problems")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminProblemController {

    private static final int MAX_EXPORT_SIZE = 10000;

    private final ProblemService problemService;
    private final AdminProblemService adminProblemService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Get problems list", description = "Get paginated list of problems with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProblemVO>> getProblems(ProblemQueryDTO query) {
        return Result.success(problemService.listProblems(query));
    }

    @Operation(summary = "Export problems", description = "Export problems as JSON or CSV file")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void exportProblems(ProblemQueryDTO query,
                               @RequestParam(defaultValue = "json") String format,
                               HttpServletResponse response) throws IOException {
        // Validate format first before any response headers are set
        String normalizedFormat = format != null ? format.trim().toLowerCase() : "json";
        if (!"csv".equals(normalizedFormat) && !"json".equals(normalizedFormat)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Unsupported format: \" + format + \". Use 'json' or 'csv'.\"}");
            return;
        }

        // Set response encoding first
        response.setCharacterEncoding("UTF-8");

        List<ProblemVO> problems = problemService.listAllProblems(query);

        // Enforce export size limit to prevent memory issues
        if (problems.size() > MAX_EXPORT_SIZE) {
            problems = problems.subList(0, MAX_EXPORT_SIZE);
        }

        String date = LocalDate.now().toString();

        if ("json".equals(normalizedFormat)) {
            response.setContentType("application/json");
            response.setHeader("Content-Disposition",
                "attachment; filename=problems-export-" + date + ".json");
            objectMapper.writeValue(response.getOutputStream(), problems);
        } else {
            response.setContentType("text/csv; charset=UTF-8");
            response.setHeader("Content-Disposition",
                "attachment; filename=problems-export-" + date + ".csv");
            PrintWriter writer = response.getWriter();
            writer.println("id,slug,title,difficulty,status,isPremium,isPublished,submissionCount,solutionCount,createdAt,updatedAt,tags");
            for (ProblemVO problem : problems) {
                String tags = problem.getTags() != null
                    ? problem.getTags().stream().map(ProblemVO.ProblemTagVO::getLabel).collect(Collectors.joining(";"))
                    : "";
                writer.println(String.join(",",
                    String.valueOf(problem.getId()),
                    escapeCsvField(problem.getSlug()),
                    escapeCsvField(problem.getTitle()),
                    escapeCsvField(problem.getDifficulty()),
                    escapeCsvField(problem.getStatus()),
                    String.valueOf(problem.getIsPremium()),
                    String.valueOf(problem.getIsPublished()),
                    String.valueOf(problem.getSubmissionCount()),
                    String.valueOf(problem.getSolutionCount()),
                    problem.getCreatedAt() != null ? problem.getCreatedAt().toString() : "",
                    problem.getUpdatedAt() != null ? problem.getUpdatedAt().toString() : "",
                    escapeCsvField(tags)
                ));
            }
            writer.flush();
        }
    }

    private static String escapeCsvField(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    @Operation(summary = "Get problem by ID", description = "Get detailed problem information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> getProblemById(@PathVariable Long id) {
        return Result.success(problemService.getProblemById(id));
    }

    @Operation(summary = "Create problem", description = "Create a new problem")
    @RateLimit(key = "admin:problem-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> createProblem(@Valid @RequestBody CreateProblemDTO createDTO) {
        return Result.success(problemService.createProblem(createDTO));
    }

    @Operation(summary = "Update problem", description = "Update an existing problem")
    @RateLimit(key = "admin:problem-update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> updateProblem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProblemDTO updateDTO) {
        return Result.success(problemService.updateProblem(id, updateDTO));
    }

    @Operation(summary = "Delete problem", description = "Delete a problem (soft delete)")
    @RateLimit(key = "admin:problem-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return Result.success();
    }

    @Operation(summary = "Publish problem", description = "Publish a problem")
    @RateLimit(key = "admin:problem-publish", limit = 30, period = 60)
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> publishProblem(@PathVariable Long id) {
        return Result.success(problemService.publishProblem(id));
    }

    @Operation(summary = "Unpublish problem", description = "Unpublish a problem")
    @RateLimit(key = "admin:problem-unpublish", limit = 30, period = 60)
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVO> unpublishProblem(@PathVariable Long id) {
        return Result.success(problemService.unpublishProblem(id));
    }

    @Operation(summary = "Bulk problem action", description = "Perform bulk action on multiple problems (publish, unpublish, delete, edit)")
    @RateLimit(key = "admin:problem-bulk", limit = 10, period = 60)
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<BulkProblemResultDTO>> bulkAction(@Valid @RequestBody BulkProblemRequestDTO request) {
        return Result.success(adminProblemService.bulkAction(request));
    }

    // ========== Tab-specific Endpoints ==========

    @Operation(summary = "Get problem header data", description = "Get header data for problem header tab")
    @GetMapping("/{id}/header")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<HeaderDataVO> getProblemHeader(@PathVariable Long id) {
        return Result.success(adminProblemService.getHeaderData(id));
    }

    @Operation(summary = "Get problem description data", description = "Get description data with details, examples, constraints, and tags")
    @GetMapping("/{id}/description")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<DescriptionDataVO> getProblemDescription(@PathVariable Long id) {
        return Result.success(adminProblemService.getDescriptionData(id));
    }

    @Operation(summary = "Get problem code data", description = "Get code data with language starter codes")
    @GetMapping("/{id}/code")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<CodeDataVO> getProblemCode(@PathVariable Long id) {
        return Result.success(adminProblemService.getCodeData(id));
    }

    @Operation(summary = "Get problem cases data", description = "Get cases data with examples, constraints, and hints")
    @GetMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<CasesDataVO> getProblemCases(@PathVariable Long id) {
        return Result.success(adminProblemService.getCasesData(id));
    }
}
