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
 * Admin-owned MyBatis-Plus configuration (P7-LEGACY-ADMIN-CONFIG-OWN-001).
 *
 * <p>Replaces the beans the admin shell currently discovers from Legacy
 * {@code com.ulticode.common.config.MybatisPlusConfig} via the broad
 * {@code com.ulticode} scan:
 *
 * <ul>
 *   <li>pagination {@link MybatisPlusInterceptor} — 14 admin services and
 *       projections call {@code selectPage};</li>
 *   <li>autofill {@link MetaObjectHandler} — {@code SystemSetting},
 *       {@code AuditLog}, {@code AuditOutboxRecord} and {@code Backup} declare
 *       {@code @TableField(fill = ...)} on {@code createdAt}/{@code updatedAt}.
 *       Without this bean MyBatis-Plus sends {@code NULL} and DDL with
 *       {@code NOT NULL DEFAULT CURRENT_TIMESTAMP} rejects the insert.</li>
 * </ul>
 *
 * <p>Deliberately omits the dead {@code DbOwnerViolationInterceptor} (classified
 * DEAD by ADR-P7-DBPERM-CLASSIFICATION-20260803) and the app-owned SQL timing
 * interceptor. Mirrors {@code com.ulticode.app.config.MybatisPlusConfig}.
 */
@Configuration
public class AdminMybatisPlusConfig {

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

        @Override
        public void insertFill(MetaObject metaObject) {
            this.strictInsertFill(metaObject, FIELD_CREATED_AT, LocalDateTime::now, LocalDateTime.class);
            this.strictInsertFill(metaObject, FIELD_UPDATED_AT, LocalDateTime::now, LocalDateTime.class);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            this.setFieldValByName(FIELD_UPDATED_AT, LocalDateTime.now(), metaObject);
        }
    }
}
