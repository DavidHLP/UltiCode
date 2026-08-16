package com.ulticode.submission.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration for the Submission owner shell.
 *
 * <p>SPLIT-004 slice-5: the admin read provider ({@code selectPage}) must
 * enforce the public page/limit contract. Without the pagination interceptor,
 * MyBatis-Plus runs the raw query and never issues the COUNT, so
 * {@code Page.getTotal()} stays 0 and the LIMIT is not applied. Mirrors the
 * App shell's {@code MybatisPlusConfig} for exactly this interceptor; other
 * interceptors (optimistic locking, SQL timing) stay with their current
 * owners until the corresponding feature families migrate.
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
