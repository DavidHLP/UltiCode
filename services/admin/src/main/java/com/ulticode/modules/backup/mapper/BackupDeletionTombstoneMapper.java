package com.ulticode.modules.backup.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persists Admin-owned deletions while legacy backup rows remain for rollback. */
@Mapper
public interface BackupDeletionTombstoneMapper {

    @Insert("""
            INSERT INTO backup_deletion_tombstones (backup_id)
            VALUES (#{backupId})
            ON DUPLICATE KEY UPDATE backup_id = backup_id
            """)
    int insert(@Param("backupId") String backupId);
}
