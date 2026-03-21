package com.ulticode.modules.backup.dto;

import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Schema(description = "备份信息")
public class BackupVO {

    @Schema(description = "备份ID")
    private String id;

    @Schema(description = "文件名")
    private String filename;

    @Schema(description = "文件大小(字节)")
    private Long size;

    @Schema(description = "备份类型")
    private BackupType type;

    @Schema(description = "备份状态")
    private BackupStatus status;

    @Schema(description = "创建者ID")
    private String createdBy;

    @Schema(description = "创建者用户名")
    private String createdByName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    @Schema(description = "错误信息")
    private String error;

    @Schema(description = "元数据")
    private Map<String, Object> metadata;
}
