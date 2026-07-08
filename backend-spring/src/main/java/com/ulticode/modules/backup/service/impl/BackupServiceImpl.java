package com.ulticode.modules.backup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import com.ulticode.modules.backup.service.BackupService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private final BackupMapper backupMapper;
    private final UserMapper userMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final BackupProcessPort backupProcessPort;

    @Value("${backup.dir:${BACKUP_DIR:/tmp/backups}}")
    private String backupDir;

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public BackupVO createBackup(String userId, CreateBackupDTO dto) {
        // Create backup directory if not exists
        ensureBackupDirectoryExists();

        // Generate filename
        String timestamp = LocalDateTime.now(clock).format(FILE_DATE_FORMAT);
        String filename = String.format("backup_%s_%s.sql", dto.getType().name().toLowerCase(), timestamp);

        // Create backup record with PENDING status
        Backup backup = new Backup();
        backup.setFilename(filename);
        backup.setSize(0L);
        backup.setType(dto.getType());
        backup.setStatus(BackupStatus.PENDING);
        backup.setCreatedBy(userId);

        backupMapper.insert(backup);
        log.info("Created backup record: {} by user: {}", backup.getId(), userId);

        // Execute backup asynchronously
        executeBackup(backup.getId());

        return toVO(backup);
    }

    @Override
    public PageResult<BackupVO> getBackups(BackupQueryDTO query) {
        LambdaQueryWrapper<Backup> wrapper = new LambdaQueryWrapper<>();

        if (query.getType() != null) {
            wrapper.eq(Backup::getType, query.getType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Backup::getStatus, query.getStatus());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(Backup::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(Backup::getCreatedAt, query.getEndDate());
        }

        wrapper.orderByDesc(Backup::getCreatedAt);

        Page<Backup> page = new Page<>(query.getPage(), query.getLimit());
        Page<Backup> result = backupMapper.selectPage(page, wrapper);

        // Collect user IDs to batch fetch
        List<String> userIds = result.getRecords().stream()
                .map(Backup::getCreatedBy)
                .distinct()
                .toList();

        // Create user map
        Map<String, User> userMap = userIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<BackupVO> voList = result.getRecords().stream()
                .map(backup -> toVO(backup, userMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public BackupVO getBackupById(String id) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }
        return toVO(backup);
    }

    @Override
    public File getBackupFile(String id) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }
        if (backup.getStatus() != BackupStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Backup is not completed yet");
        }

        Path filePath = validateBackupFilePath(backup.getFilename());
        File file = filePath.toFile();

        if (!file.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup file not found");
        }

        return file;
    }

    @Override
    public BackupVO restoreBackup(String id, String userId) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }
        if (backup.getStatus() != BackupStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot restore from a non-completed backup");
        }

        Path filePath = validateBackupFilePath(backup.getFilename());
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup file not found");
        }

        log.warn("Starting database restore from backup: {} by user: {}", id, userId);

        boolean success = backupProcessPort.restore(filePath);
        if (success) {
            log.info("Database restore completed successfully from backup: {}", id);
            Map<String, Object> metadata = backup.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put("lastRestoredAt", LocalDateTime.now(clock).toString());
            metadata.put("lastRestoredBy", userId);
            backup.setMetadata(metadata);
            backupMapper.updateById(backup);
            return toVO(backup);
        }
        throw new BusinessException(ErrorCode.UNKNOWN_ERROR,
                "Database restore failed. Check server logs for details.");
    }

    @Override
    public void deleteBackup(String id) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }

        // Delete the file from disk
        Path filePath = validateBackupFilePath(backup.getFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete backup file: {}", filePath, e);
        }

        // Delete the record from database
        backupMapper.deleteById(id);
        log.info("Deleted backup: {}", id);
    }

    @Override
    @Async
    public void executeBackup(String backupId) {
        Backup backup = backupMapper.selectById(backupId);
        if (backup == null) {
            log.error("Backup not found: {}", backupId);
            return;
        }

        try {
            // Update status to IN_PROGRESS
            backup.setStatus(BackupStatus.IN_PROGRESS);
            backupMapper.updateById(backup);

            // Ensure backup directory exists
            ensureBackupDirectoryExists();

            Path filePath = Paths.get(backupDir, backup.getFilename());

            // Delegate the mysqldump process I/O to the port — the
            // service no longer spawns the subprocess directly.
            boolean success = backupProcessPort.dump(filePath);

            if (success && Files.exists(filePath)) {
                long size = Files.size(filePath);
                backup.setSize(size);
                backup.setStatus(BackupStatus.COMPLETED);
                backup.setCompletedAt(LocalDateTime.now(clock));

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("databaseName", "see-port-adapter");
                metadata.put("backupType", backup.getType().name());
                backup.setMetadata(metadata);

                backupMapper.updateById(backup);
                log.info("Backup completed successfully: {}, size: {} bytes", backupId, size);
            } else {
                backup.setStatus(BackupStatus.FAILED);
                backup.setCompletedAt(LocalDateTime.now(clock));
                backup.setError("mysqldump failed — see server logs");
                backupMapper.updateById(backup);
                log.error("Backup failed: {}", backupId);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Backup execution failed for: {}", backupId, e);
            backup.setStatus(BackupStatus.FAILED);
            backup.setCompletedAt(LocalDateTime.now(clock));
            backup.setError(e.getMessage());
            backupMapper.updateById(backup);
        }
    }

    @Override
    public BackupVO toVO(Backup backup) {
        return toVO(backup, Collections.emptyMap());
    }

    private BackupVO toVO(Backup backup, Map<String, User> userMap) {
        BackupVO vo = new BackupVO();
        vo.setId(backup.getId());
        vo.setFilename(backup.getFilename());
        vo.setSize(backup.getSize());
        vo.setType(backup.getType());
        vo.setStatus(backup.getStatus());
        vo.setCreatedBy(backup.getCreatedBy());
        vo.setCreatedAt(backup.getCreatedAt());
        vo.setCompletedAt(backup.getCompletedAt());
        vo.setError(backup.getError());
        vo.setMetadata(backup.getMetadata());

        User user = userMap.get(backup.getCreatedBy());
        if (user != null) {
            vo.setCreatedByName(user.getUsername());
        }

        return vo;
    }

    private void ensureBackupDirectoryExists() {
        Path path = Paths.get(backupDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("Created backup directory: {}", backupDir);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to create backup directory: " + e.getMessage());
            }
        }
    }

    /**
     * Validate backup filename and ensure the resolved path stays within the backup directory.
     */
    private Path validateBackupFilePath(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid backup filename");
        }
        Path backupRoot = Paths.get(backupDir).normalize();
        Path filePath = Paths.get(backupDir, filename).normalize();
        if (!filePath.startsWith(backupRoot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Backup path traversal detected");
        }
        return filePath;
    }
}
