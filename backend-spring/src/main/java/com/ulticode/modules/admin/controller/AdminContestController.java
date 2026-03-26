package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.service.AdminContestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for contest management.
 */
@Tag(name = "Admin - Contests", description = "Contest management endpoints for admin panel")
@RestController
@RequestMapping("/admin/contests")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminContestController {

    private final AdminContestService adminContestService;

    @Operation(summary = "Get contests", description = "Get paginated list of contests with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<AdminContestVO>> getContests(AdminContestQueryDTO query) {
        return Result.success(adminContestService.getContests(query));
    }

    @Operation(summary = "Get contest by ID", description = "Get detailed contest information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminContestVO> getContest(@PathVariable String id) {
        return Result.success(adminContestService.getContest(id));
    }
}
