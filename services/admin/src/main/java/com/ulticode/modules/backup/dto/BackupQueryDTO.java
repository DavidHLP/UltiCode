package com.ulticode.modules.backup.dto;

import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "备份查询参数")
public class BackupQueryDTO {

    @Schema(description = "备份类型")
    private BackupType type;

    @Schema(description = "备份状态")
    private BackupStatus status;

    @Schema(description = "开始时间")
    private LocalDateTime startDate;

    @Schema(description = "结束时间")
    private LocalDateTime endDate;

    @Min(value = 1, message = "page must be >= 1")
    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "limit must be >= 1")
    @Max(value = 100, message = "limit must be <= 100")
    @Schema(description = "每页数量", example = "20")
    private Integer limit = 20;
}
