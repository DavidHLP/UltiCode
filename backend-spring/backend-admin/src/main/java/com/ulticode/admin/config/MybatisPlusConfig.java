package com.ulticode.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
 * <p>The pagination interceptor is owned here as well because the admin
 * Backup read projection calls {@code selectPage}; without it the service
 * shell would not enforce the public page/limit contract. Optimistic-locking
 * and cross-owner interceptors remain with their current owners until the
 * corresponding feature families migrate.
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

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
