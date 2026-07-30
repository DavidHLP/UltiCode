package com.ulticode.modules.problem.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.problem.dto.SaveProblemNoteDTO;
import com.ulticode.modules.problem.service.ProblemNoteService;
import com.ulticode.modules.problem.vo.ProblemNoteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for per-user private problem notes.
 * Exposes GET/POST /problems/{problemId}/note.
 *
 * @author Claude
 * @since 2026-06-11
 */
@Tag(name = "Problem Note", description = "Per-user private problem notes")
@RestController
@RequestMapping("/problems/{problemId}/note")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class ProblemNoteController {

    private final ProblemNoteService problemNoteService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Get the current user's note for a problem")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<ProblemNoteVO> getNote(@PathVariable Long problemId) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(problemNoteService.getNote(userId, problemId));
    }

    @Operation(summary = "Save (upsert) the current user's note for a problem")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimit(key = "problem-note:upsert", limit = 30, period = 60)
    public Result<ProblemNoteVO> saveNote(
            @PathVariable Long problemId,
            @Valid @RequestBody SaveProblemNoteDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(problemNoteService.upsertNote(userId, problemId, dto.getContent()));
    }
}
