package com.ulticode.modules.backup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.service.BackupService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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

    @Value("${backup.dir:${BACKUP_DIR:/tmp/backups}}")
    private String backupDir;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

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

        try {
            // Parse database connection info from datasource URL
            DatabaseConnectionInfo dbInfo = parseDatasourceUrl(datasourceUrl);

            // Build mysql restore command — use MYSQL_PWD env var to avoid credential exposure in process args
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysql",
                    "--host=" + dbInfo.host,
                    "--port=" + dbInfo.port,
                    "--user=" + datasourceUsername,
                    dbInfo.database
            );
            processBuilder.environment().put("MYSQL_PWD", datasourcePassword);

            processBuilder.redirectInput(filePath.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Read output for logging
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Database restore completed successfully from backup: {}", id);

                // Add metadata about restore
                Map<String, Object> metadata = backup.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                }
                metadata.put("lastRestoredAt", LocalDateTime.now().toString());
                metadata.put("lastRestoredBy", userId);
                backup.setMetadata(metadata);
                backupMapper.updateById(backup);

                return toVO(backup);
            } else {
                log.error("Database restore failed with exit code: {}. Output: {}", exitCode, output);
                throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Database restore failed. Check server logs for details.");
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to restore database from backup: {}", id, e);
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to restore database. Check server logs for details.");
        }
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

            // Parse database connection info from datasource URL
            DatabaseConnectionInfo dbInfo = parseDatasourceUrl(datasourceUrl);

            // Ensure backup directory exists
            ensureBackupDirectoryExists();

            Path filePath = Paths.get(backupDir, backup.getFilename());

            // Build mysqldump command — use MYSQL_PWD env var to avoid credential exposure
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "mysqldump",
                    "--host=" + dbInfo.host,
                    "--port=" + dbInfo.port,
                    "--user=" + datasourceUsername,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    "--add-drop-table",
                    dbInfo.database
            );
            processBuilder.environment().put("MYSQL_PWD", datasourcePassword);

            processBuilder.redirectOutput(filePath.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Read output for logging (errors will be mixed with stdout)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && Files.exists(filePath)) {
                // Get file size
                long size = Files.size(filePath);

                // Update backup record
                backup.setSize(size);
                backup.setStatus(BackupStatus.COMPLETED);
                backup.setCompletedAt(LocalDateTime.now());

                // Add metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("databaseName", dbInfo.database);
                metadata.put("backupType", backup.getType().name());
                backup.setMetadata(metadata);

                backupMapper.updateById(backup);
                log.info("Backup completed successfully: {}, size: {} bytes", backupId, size);
            } else {
                // Backup failed
                backup.setStatus(BackupStatus.FAILED);
                backup.setCompletedAt(LocalDateTime.now());
                backup.setError("mysqldump failed with exit code " + exitCode + ": " + output);
                backupMapper.updateById(backup);
                log.error("Backup failed: {}, exit code: {}, output: {}", backupId, exitCode, output);
            }
        // broad catch: backup execution involves process I/O, file I/O, and DB updates
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Backup execution failed for: {}", backupId, e);
            backup.setStatus(BackupStatus.FAILED);
            backup.setCompletedAt(LocalDateTime.now());
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

    private DatabaseConnectionInfo parseDatasourceUrl(String url) {
        // Parse JDBC URL: jdbc:mysql://host:port/database
        DatabaseConnectionInfo info = new DatabaseConnectionInfo();
        info.port = 3306; // default port

        try {
            // Remove jdbc:mysql:// prefix
            String connectionPart = url.substring("jdbc:mysql://".length());

            // Split by / to get host:port and database
            int slashIndex = connectionPart.indexOf('/');
            if (slashIndex > 0) {
                String hostPort = connectionPart.substring(0, slashIndex);
                String databasePart = connectionPart.substring(slashIndex + 1);

                // Remove query parameters from database name
                int queryIndex = databasePart.indexOf('?');
                if (queryIndex > 0) {
                    info.database = databasePart.substring(0, queryIndex);
                } else {
                    info.database = databasePart;
                }

                // Parse host and port
                int colonIndex = hostPort.indexOf(':');
                if (colonIndex > 0) {
                    info.host = hostPort.substring(0, colonIndex);
                    info.port = Integer.parseInt(hostPort.substring(colonIndex + 1));
                } else {
                    info.host = hostPort;
                }
            }
        // broad catch: URL string parsing may throw NumberFormatException or StringIndexOutOfBoundsException
        } catch (Exception e) {
            log.error("Failed to parse datasource URL: {}", url, e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to parse database connection configuration");
        }

        return info;
    }

    private static class DatabaseConnectionInfo {
        String host;
        int port;
        String database;
    }
}
