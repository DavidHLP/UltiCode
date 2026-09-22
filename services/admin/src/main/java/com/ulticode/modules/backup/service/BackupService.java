package com.ulticode.modules.backup.service;

import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;

import java.io.InputStream;

/**
 * Service interface for backup write operations. Read paths (paginated list,
 * detail by id) intentionally live behind
 * {@link com.ulticode.modules.backup.projection.BackupReadProjection} so the
 * controller depends on that projection directly for reads &mdash; see
 * {@code BackupController} and the deep-module note on the projection.
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
     * Open a completed backup object for streaming download.
     *
     * @param id the backup ID
     * @return the object stream and the validated display filename
     */
    BackupDownload getBackupFile(String id);

    record BackupDownload(String filename, InputStream content, long contentLength, String contentType) {
    }

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
     * Convert Backup entity to BackupVO. Thin delegate to
     * {@link com.ulticode.modules.backup.projection.BackupReadProjection#toVO(Backup)}
     * so write paths return the same view shape the controller's read path
     * serves without re-implementing the projection rules.
     *
     * @param backup the backup entity
     * @return the backup VO
     */
    BackupVO toVO(Backup backup);
}
