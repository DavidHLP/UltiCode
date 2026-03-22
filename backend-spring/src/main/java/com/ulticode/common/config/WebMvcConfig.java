package com.ulticode.common.config;

import com.ulticode.security.csrf.CsrfInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrfInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrfInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/auth/login",
                "/auth/register",
                "/auth/refresh",
                "/auth/logout",
                "/auth/forgot-password",
                "/auth/reset-password",
                "/auth/github/**",
                "/auth/google/**",
                "/swagger-ui/**",
                "/api-docs/**",
                "/actuator/**",
                "/error"
            );
    }
}
