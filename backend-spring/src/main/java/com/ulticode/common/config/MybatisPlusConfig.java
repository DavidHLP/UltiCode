package com.ulticode.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ulticode.common.metrics.SqlTimingInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    /**
     * Expose a {@link TransactionTemplate} built from the auto-configured
     * {@link PlatformTransactionManager} so services (e.g. the ADR-003 fenced
     * rejudge path) can run programmatic transactions without a separate
     * {@code @Transactional} proxy hop. Used to keep the flag-off legacy branch
     * non-transactional while the fenced branch runs in a single atomic txn.
     *
     * @param transactionManager the Spring-managed transaction manager
     * @return a reusable transaction template
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * Register the {@link SqlTimingInterceptor} at the MyBatis Executor
     * level via {@code Configuration#addInterceptor}. Wired outside the
     * MyBatis-Plus inner-interceptor chain because MP's InnerInterceptor
     * has no afterQuery hook, while this interceptor needs both before
     * and after timing around the actual SQL execution.
     *
     * @param sqlTimingInterceptor the SQL timing/counting interceptor
     * @return customizer that adds the interceptor to the MyBatis config
     */
    @Bean
    public ConfigurationCustomizer mybatisCustomizer(
            SqlTimingInterceptor sqlTimingInterceptor) {
        return configuration -> configuration.addInterceptor(sqlTimingInterceptor);
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AutoFillMetaObjectHandler();
    }

    public static class AutoFillMetaObjectHandler implements MetaObjectHandler {

        // Field names declared on @TableField(fill = FieldFill.INSERT) / INSERT_UPDATE.
        // Centralised so a rename only touches this block; entity annotations must
        // stay in sync.
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
            // 使用 setFieldValByName 而非 strictUpdateFill:后者在 entity 通过
            // selectById 加载后已有非 null 值时会 SKIP 填充,导致 updated_at 永不刷新。
            // setFieldValByName 无条件覆盖,符合「每次 UPDATE 都刷新审计时间」的直觉。
            // 仅当目标实体声明了 @TableField(fill = FieldFill.INSERT_UPDATE) 才会被填充。
            this.setFieldValByName(FIELD_UPDATED_AT, LocalDateTime.now(), metaObject);
        }
    }
}
