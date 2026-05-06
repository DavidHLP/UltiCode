package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.service.AdminContestService;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.dto.CreateAnnouncementDTO;
import com.ulticode.modules.contest.dto.UpdateAnnouncementDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "Create contest")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminContestVO> createContest(@Valid @RequestBody CreateContestDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(adminContestService.createContest(dto, userId));
    }

    @Operation(summary = "Update contest")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminContestVO> updateContest(@PathVariable String id, @Valid @RequestBody UpdateContestDTO dto) {
        return Result.success(adminContestService.updateContest(id, dto));
    }

    @Operation(summary = "Delete contest")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteContest(@PathVariable String id) {
        adminContestService.deleteContest(id);
        return Result.success(null);
    }

    @Operation(summary = "Start contest")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminContestVO> startContest(@PathVariable String id) {
        return Result.success(adminContestService.startContest(id));
    }

    @Operation(summary = "End contest")
    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<AdminContestVO> endContest(@PathVariable String id) {
        return Result.success(adminContestService.endContest(id));
    }

    // Announcement CRUD

    @Operation(summary = "Get contest announcements")
    @GetMapping("/{id}/announcements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<ContestAnnouncement>> getAnnouncements(@PathVariable String id) {
        return Result.success(adminContestService.getAnnouncements(id));
    }

    @Operation(summary = "Create contest announcement")
    @PostMapping("/{id}/announcements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ContestAnnouncement> createAnnouncement(
            @PathVariable String id,
            @Valid @RequestBody CreateAnnouncementDTO dto) {
        return Result.success(adminContestService.createAnnouncement(id, dto.getTitle(), dto.getContent(), dto.getIsPinned()));
    }

    @Operation(summary = "Update contest announcement")
    @PatchMapping("/{contestId}/announcements/{announcementId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ContestAnnouncement> updateAnnouncement(
            @PathVariable String contestId,
            @PathVariable String announcementId,
            @Valid @RequestBody UpdateAnnouncementDTO dto) {
        return Result.success(adminContestService.updateAnnouncement(contestId, announcementId, dto.getTitle(), dto.getContent(), dto.getIsPinned()));
    }

    @Operation(summary = "Delete contest announcement")
    @DeleteMapping("/{contestId}/announcements/{announcementId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteAnnouncement(
            @PathVariable String contestId,
            @PathVariable String announcementId) {
        adminContestService.deleteAnnouncement(contestId, announcementId);
        return Result.success(null);
    }

    @Operation(summary = "Get contest rankings", description = "Get live rankings for a contest in admin panel")
    @GetMapping("/{id}/rankings")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<com.ulticode.modules.contest.dto.ContestRankingVO>> getRankings(@PathVariable String id) {
        return Result.success(adminContestService.getRankings(id));
    }
}
