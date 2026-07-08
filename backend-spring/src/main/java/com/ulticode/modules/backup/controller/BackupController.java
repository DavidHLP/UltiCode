package com.ulticode.modules.backup.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "Admin - Backup", description = "备份管理接口")
@RestController
@RequestMapping("/admin/backups")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class BackupController {

    private final BackupService backupService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "创建备份")
    @RateLimit(key = "admin:backup-create", limit = 30, period = 60)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BackupVO> createBackup(@Valid @RequestBody CreateBackupDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        return Result.success(backupService.createBackup(userId, dto));
    }

    @Operation(summary = "获取备份列表")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<BackupVO>> getBackups(BackupQueryDTO query) {
        return Result.success(backupService.getBackups(query));
    }

    @Operation(summary = "获取备份详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BackupVO> getBackupById(@PathVariable String id) {
        return Result.success(backupService.getBackupById(id));
    }

    @Operation(summary = "下载备份文件")
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String id) {
        File file = backupService.getBackupFile(id);

        Resource resource = new FileSystemResource(file);
        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    @Operation(summary = "从备份恢复")
    @RateLimit(key = "admin:backup-restore", limit = 30, period = 60)
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<BackupVO> restoreBackup(@PathVariable String id) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        return Result.success(backupService.restoreBackup(id, userId));
    }

    @Operation(summary = "删除备份")
    @RateLimit(key = "admin:backup-delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteBackup(@PathVariable String id) {
        backupService.deleteBackup(id);
        return Result.success();
    }
}
