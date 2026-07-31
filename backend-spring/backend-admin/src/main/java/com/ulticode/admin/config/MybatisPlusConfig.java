package com.ulticode.admin.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration for the backend-admin service shell.
 *
 * <p>Owns the {@link MetaObjectHandler} bean that auto-fills
 * {@code createdAt} / {@code updatedAt} / {@code addedAt} on entities that
 * declare {@code @TableField(fill = FieldFill.INSERT)} /
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)}. Without this bean
 * (and the same fields the legacy shell exposes) MyBatis-Plus sends
 * {@code NULL} for those columns, and any DDL with
 * {@code NOT NULL DEFAULT CURRENT_TIMESTAMP(...)} rejects the insert
 * (e.g. {@code backups.created_at}).
 *
 * <p>Kept narrow: the admin service shell currently only needs the
 * auto-fill parity. Pagination / optimistic-locking / cross-owner
 * interceptors are intentionally registered in
 * {@code backend-legacy}'s {@code MybatisPlusConfig} until phase 7 retires
 * that module; ports of those concerns will land here as their owning
 * feature families migrate.
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AutoFillMetaObjectHandler();
    }

    public static class AutoFillMetaObjectHandler implements MetaObjectHandler {

        private static final String FIELD_CREATED_AT = "createdAt";
        private static final String FIELD_UPDATED_AT = "updatedAt";
        private static final String FIELD_ADDED_AT = "addedAt";

        @Override
        public void insertFill(MetaObject metaObject) {
            this.strictInsertFill(metaObject, FIELD_CREATED_AT, LocalDateTime::now, LocalDateTime.class);
            this.strictInsertFill(metaObject, FIELD_UPDATED_AT, LocalDateTime::now, LocalDateTime.class);
            this.strictInsertFill(metaObject, FIELD_ADDED_AT, LocalDateTime::now, LocalDateTime.class);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            this.setFieldValByName(FIELD_UPDATED_AT, LocalDateTime.now(), metaObject);
        }
    }
}
