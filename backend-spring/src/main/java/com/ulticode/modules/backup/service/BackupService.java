package com.ulticode.modules.backup.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;

import java.io.File;

/**
 * Service interface for backup operations
 */
public interface BackupService {

    /**
     * Create a new backup
     *
     * @param userId the user creating the backup
     * @param dto    the backup creation request
     * @return the created backup
     */
    BackupVO createBackup(String userId, CreateBackupDTO dto);

    /**
     * Get a paginated list of backups
     *
     * @param query the query parameters
     * @return paginated backup list
     */
    PageResult<BackupVO> getBackups(BackupQueryDTO query);

    /**
     * Get a backup by ID
     *
     * @param id the backup ID
     * @return the backup
     */
    BackupVO getBackupById(String id);

    /**
     * Get the backup file for download
     *
     * @param id the backup ID
     * @return the backup file
     */
    File getBackupFile(String id);

    /**
     * Restore database from a backup
     *
     * @param id     the backup ID
     * @param userId the user performing the restore
     * @return the backup that was restored
     */
    BackupVO restoreBackup(String id, String userId);

    /**
     * Delete a backup
     *
     * @param id the backup ID
     */
    void deleteBackup(String id);

    /**
     * Execute the backup process asynchronously
     *
     * @param backupId the backup ID to execute
     */
    void executeBackup(String backupId);

    /**
     * Convert Backup entity to BackupVO
     *
     * @param backup the backup entity
     * @return the backup VO
     */
    BackupVO toVO(Backup backup);
}
