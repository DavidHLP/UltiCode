package com.ulticode.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ulticode.common.dbperm.DbOwnerViolationInterceptor;
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

    @Bean
    public DbOwnerViolationInterceptor dbOwnerViolationInterceptor() {
        return new DbOwnerViolationInterceptor();
    }

    /**
     * Register the {@link SqlTimingInterceptor} and {@link DbOwnerViolationInterceptor} at the
     * MyBatis Executor level via {@code Configuration#addInterceptor}.
     *
     * @param sqlTimingInterceptor the SQL timing/counting interceptor
     * @param dbOwnerViolationInterceptor the cross-owner DB write violation interceptor
     * @return customizer that adds the interceptors to the MyBatis config
     */
    @Bean
    public ConfigurationCustomizer mybatisCustomizer(
            SqlTimingInterceptor sqlTimingInterceptor,
            DbOwnerViolationInterceptor dbOwnerViolationInterceptor) {
        return configuration -> {
            configuration.addInterceptor(sqlTimingInterceptor);
            configuration.addInterceptor(dbOwnerViolationInterceptor);
        };
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
