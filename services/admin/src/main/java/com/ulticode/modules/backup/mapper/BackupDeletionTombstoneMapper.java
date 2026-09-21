package com.ulticode.modules.backup.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** Persists Admin-owned deletions while legacy backup rows remain for rollback. */
@Mapper
public interface BackupDeletionTombstoneMapper {

    /**
     * Records the deletion and the dump that still has to leave the object
     * store. Callers run this inside the deletion transaction, so the cleanup
     * intent survives a crash or a storage outage.
     */
    @Insert("""
            INSERT INTO backup_deletion_tombstones (backup_id, object_key)
            VALUES (#{backupId}, #{objectKey})
            ON DUPLICATE KEY UPDATE object_key = COALESCE(object_key, #{objectKey})
            """)
    int insert(@Param("backupId") String backupId, @Param("objectKey") String objectKey);

    @Select("""
            SELECT object_key FROM backup_deletion_tombstones
            WHERE object_key IS NOT NULL AND object_deleted_at IS NULL
            ORDER BY deleted_at, backup_id
            LIMIT #{limit}
            """)
    List<String> selectPendingObjectKeys(@Param("limit") int limit);

    /** Deletion is idempotent, so a cleared intent is final. */
    @Update("""
            UPDATE backup_deletion_tombstones
            SET object_deleted_at = NOW(3), cleanup_attempts = 0, cleanup_error = NULL
            WHERE object_key = #{objectKey} AND object_deleted_at IS NULL
            """)
    int markObjectDeleted(@Param("objectKey") String objectKey);

    @Update("""
            UPDATE backup_deletion_tombstones
            SET cleanup_attempts = cleanup_attempts + 1, cleanup_error = #{error}
            WHERE object_key = #{objectKey} AND object_deleted_at IS NULL
            """)
    int recordObjectDeleteFailure(@Param("objectKey") String objectKey, @Param("error") String error);
}
