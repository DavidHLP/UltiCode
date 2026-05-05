package com.ulticode.modules.admin.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.tag.*;
import com.ulticode.modules.admin.service.AdminTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Tags", description = "标签管理接口")
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminTagController {

    private final AdminTagService adminTagService;

    @Operation(summary = "Get tags list", description = "Get paginated list of tags with filters")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<TagListResponse> getTags(TagQueryDTO query) {
        return Result.success(adminTagService.getTags(query));
    }

    @Operation(summary = "Get tag by ID", description = "Get detailed tag information")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<TagVO> getTag(@PathVariable String id, @RequestParam String type) {
        return Result.success(adminTagService.getTag(id, type));
    }

    @Operation(summary = "Create tag", description = "Create a new tag")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<TagVO> createTag(@Valid @RequestBody CreateTagDTO dto) {
        return Result.success(adminTagService.createTag(dto));
    }

    @Operation(summary = "Update tag", description = "Update an existing tag")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<TagVO> updateTag(@PathVariable String id, @Valid @RequestBody UpdateTagDTO dto) {
        return Result.success(adminTagService.updateTag(id, dto));
    }

    @Operation(summary = "Delete tag", description = "Delete a tag")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteTag(@PathVariable String id, @RequestParam String type) {
        adminTagService.deleteTag(id, type);
        return Result.success(null);
    }

    @Operation(summary = "Merge tags", description = "Merge source tag into target tag")
    @PostMapping("/merge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> mergeTag(@Valid @RequestBody MergeTagDTO dto) {
        adminTagService.mergeTag(dto);
        return Result.success(null);
    }
}
