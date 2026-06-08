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

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

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
        @Override
        public void insertFill(MetaObject metaObject) {
            this.strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
            this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
            this.strictInsertFill(metaObject, "addedAt", LocalDateTime::now, LocalDateTime.class);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        }
    }
}
