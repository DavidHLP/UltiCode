package com.ulticode.modules.admin.storage;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** Claim/retry state for Admin-uploaded objects whose owner row never landed. */
@Mapper
public interface AdminStorageCleanupOutboxMapper {

    @Insert("""
            INSERT INTO storage_cleanup_outbox (object_key)
            VALUES (#{objectKey})
            ON DUPLICATE KEY UPDATE deleted_at = NULL, kept_at = NULL, attempts = 0,
                                    last_error = NULL, verify_owner_reference = 0
            """)
    int enqueue(@Param("objectKey") String objectKey);

    /** Records a key whose RPC outcome is unknown; the sweep asks App before deleting. */
    @Insert("""
            INSERT INTO storage_cleanup_outbox (object_key, verify_owner_reference)
            VALUES (#{objectKey}, 1)
            ON DUPLICATE KEY UPDATE deleted_at = NULL, kept_at = NULL, attempts = 0,
                                    last_error = NULL, verify_owner_reference = 1
            """)
    int enqueueForOwnerCheck(@Param("objectKey") String objectKey);

    @Select("""
            SELECT object_key FROM storage_cleanup_outbox
            WHERE deleted_at IS NULL AND kept_at IS NULL AND verify_owner_reference = 0
            ORDER BY created_at, object_key
            LIMIT #{limit}
            """)
    List<String> selectPendingDeletions(@Param("limit") int limit);

    @Select("""
            SELECT object_key FROM storage_cleanup_outbox
            WHERE deleted_at IS NULL AND kept_at IS NULL AND verify_owner_reference = 1
            ORDER BY created_at, object_key
            LIMIT #{limit}
            """)
    List<String> selectPendingOwnerChecks(@Param("limit") int limit);

    @Update("""
            UPDATE storage_cleanup_outbox
            SET deleted_at = NOW(3), attempts = 0, last_error = NULL
            WHERE object_key = #{objectKey} AND deleted_at IS NULL AND kept_at IS NULL
            """)
    int markDeleted(@Param("objectKey") String objectKey);

    /** Closes an intent whose object App still references. */
    @Update("""
            UPDATE storage_cleanup_outbox
            SET kept_at = NOW(3), attempts = 0, last_error = NULL
            WHERE object_key = #{objectKey} AND deleted_at IS NULL AND kept_at IS NULL
            """)
    int markKept(@Param("objectKey") String objectKey);

    @Update("""
            UPDATE storage_cleanup_outbox
            SET attempts = attempts + 1, last_error = #{error}
            WHERE object_key = #{objectKey} AND deleted_at IS NULL AND kept_at IS NULL
            """)
    int recordFailure(@Param("objectKey") String objectKey, @Param("error") String error);
}
