package com.ulticode.modules.backup.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Backup entity for database backups
 */
@Data
@TableName(value = "backups", autoResultMap = true)
public class Backup {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String filename;

    private Long size;

    private BackupType type;

    private BackupStatus status;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    private String error;
}
