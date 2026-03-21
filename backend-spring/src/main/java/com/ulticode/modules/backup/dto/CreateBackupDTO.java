package com.ulticode.modules.backup.dto;

import com.ulticode.modules.backup.entity.enums.BackupType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "创建备份请求")
public class CreateBackupDTO {

    @NotNull(message = "备份类型不能为空")
    @Schema(description = "备份类型", example = "FULL")
    private BackupType type;
}
