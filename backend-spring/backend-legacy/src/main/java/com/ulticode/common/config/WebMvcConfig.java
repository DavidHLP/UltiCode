package com.ulticode.common.config;

import com.ulticode.common.dbperm.DbOwnerWebHandlerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring WebMvc configuration for interceptors (P3-DBPERM-001).
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final DbOwnerWebHandlerInterceptor dbOwnerWebHandlerInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dbOwnerWebHandlerInterceptor)
                .addPathPatterns("/**");
    }
}
