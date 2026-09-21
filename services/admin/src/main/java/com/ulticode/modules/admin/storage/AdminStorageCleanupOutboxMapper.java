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
            ON DUPLICATE KEY UPDATE deleted_at = NULL, attempts = 0, last_error = NULL
            """)
    int enqueue(@Param("objectKey") String objectKey);

    @Select("""
            SELECT object_key FROM storage_cleanup_outbox
            WHERE deleted_at IS NULL
            ORDER BY created_at, object_key
            LIMIT #{limit}
            """)
    List<String> selectPendingObjectKeys(@Param("limit") int limit);

    @Update("""
            UPDATE storage_cleanup_outbox
            SET deleted_at = NOW(3), attempts = 0, last_error = NULL
            WHERE object_key = #{objectKey} AND deleted_at IS NULL
            """)
    int markDeleted(@Param("objectKey") String objectKey);

    @Update("""
            UPDATE storage_cleanup_outbox
            SET attempts = attempts + 1, last_error = #{error}
            WHERE object_key = #{objectKey} AND deleted_at IS NULL
            """)
    int recordFailure(@Param("objectKey") String objectKey, @Param("error") String error);
}
