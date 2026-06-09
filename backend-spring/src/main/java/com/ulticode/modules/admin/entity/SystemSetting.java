package com.ulticode.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * System setting row.
 *
 * <p>Maps the existing {@code system_settings} key-value table:
 * <pre>
 *   key         varchar(50)   PK
 *   value       text          (JSON-serialized VO)
 *   description varchar(255)  (optional human label)
 *   updated_at  datetime(3)
 * </pre>
 *
 * <p>One row is stored per settings category (e.g. {@code general},
 * {@code email}, {@code rate-limits}, {@code uploads}, {@code features}).
 * The category-specific VO is JSON-serialized into the {@code value}
 * column by the service layer.
 */
@Data
@TableName("system_settings")
public class SystemSetting {

    /**
     * Category key, e.g. {@code "general"}, {@code "email"}.
     *
     * <p>The column is named {@code key} in the existing DDL, which is a
     * MySQL reserved word; the backticks in {@code @TableId} force MyBatis-Plus
     * to emit {@code `key`} (not {@code key}) in generated SQL.
     */
    @TableId("`key`")
    private String key;

    /** JSON-serialized settings payload. */
    private String value;

    /** Optional human-readable description. */
    private String description;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
