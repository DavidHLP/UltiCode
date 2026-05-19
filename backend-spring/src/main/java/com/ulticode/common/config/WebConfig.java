package com.ulticode.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> ulticodeCharacterEncodingFilter() {
        FilterRegistrationBean<CharacterEncodingFilter> filterReg = new FilterRegistrationBean<>();
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        filterReg.setFilter(filter);
        filterReg.addUrlPatterns("/*");
        filterReg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterReg;
    }
}
