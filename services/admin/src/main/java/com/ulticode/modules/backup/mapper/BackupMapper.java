package com.ulticode.modules.backup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.backup.entity.Backup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface BackupMapper extends BaseMapper<Backup> {

    /**
     * Terminal FAILED transition that can never replace a durable COMPLETED
     * row: a completion write may commit while the client only observes a
     * connection error, so failure handling must not overwrite that success.
     * The object key is kept when the caller has none to record.
     */
    /**
     * Deletes only a row that is not a running backup owning a planned key, so
     * the decision cannot race the worker persisting that state.
     */
    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM backups
            WHERE id = #{id}
              AND NOT (object_key IS NOT NULL AND object_key <> ''
                       AND status IN ('PENDING', 'IN_PROGRESS'))
            """)
    int deleteIfNotRunning(@Param("id") String id);

    @Update("""
            UPDATE backups
            SET status = 'FAILED', completed_at = #{completedAt}, error = #{error},
                object_key = COALESCE(#{objectKey}, object_key)
            WHERE id = #{id} AND status <> 'COMPLETED'
            """)
    int failUnlessCompleted(@Param("id") String id,
                            @Param("completedAt") LocalDateTime completedAt,
                            @Param("error") String error,
                            @Param("objectKey") String objectKey);
}
