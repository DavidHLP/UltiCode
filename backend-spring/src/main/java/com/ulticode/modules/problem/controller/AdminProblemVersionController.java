package com.ulticode.modules.problem.controller;

import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.problem.service.ProblemVersionService;
import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import com.ulticode.modules.problem.vo.VersionWithDiffVO;
import com.ulticode.modules.problem.vo.VersionsResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin - Problem Versions", description = "题目版本历史管理接口")
@RestController
@RequestMapping("/admin/problems")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminProblemVersionController {

    private final ProblemVersionService problemVersionService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "List problem versions", description = "Get paginated version history for a problem")
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<VersionsResponseVO> listVersions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(problemVersionService.listVersions(id, page, limit));
    }

    @Operation(summary = "Get version detail", description = "Get full detail of a specific problem version")
    @GetMapping("/{id}/versions/{versionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ProblemVersionDetailVO> getVersionDetail(
            @PathVariable Long id,
            @PathVariable String versionId) {
        return Result.success(problemVersionService.getVersionDetail(id, versionId));
    }

    @Operation(summary = "Compare versions", description = "Compare two problem versions and return their differences")
    @GetMapping("/{id}/versions/{fromVersionId}/diff/{toVersionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<VersionWithDiffVO> compareVersions(
            @PathVariable Long id,
            @PathVariable String fromVersionId,
            @PathVariable String toVersionId) {
        return Result.success(problemVersionService.compareVersions(id, fromVersionId, toVersionId));
    }

    @Operation(summary = "Rollback to version", description = "Rollback a problem to a specific version")
    @PostMapping("/{id}/versions/{versionId}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> rollbackToVersion(
            @PathVariable Long id,
            @PathVariable String versionId,
            @RequestBody(required = false) Map<String, String> body) {
        String userId = currentUserProvider.getCurrentUserId();
        String reason = body != null ? body.get("reason") : null;
        problemVersionService.rollbackToVersion(id, versionId, reason, userId);
        return Result.success(Map.of("success", true, "message", "Rollback successful"));
    }

    @Operation(summary = "Create initial version", description = "Create the initial version for a problem")
    @PostMapping("/{id}/versions/create-initial")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> createInitialVersion(@PathVariable Long id) {
        String userId = currentUserProvider.getCurrentUserId();
        problemVersionService.createInitialVersion(id, userId);
        return Result.success(Map.of("success", true, "message", "Initial version created"));
    }
}
