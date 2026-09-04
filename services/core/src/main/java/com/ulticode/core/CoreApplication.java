package com.ulticode.core;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootConfiguration
@EnableAutoConfiguration(
        excludeName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                RedissonAutoConfigurationV2.class,
                RedissonAutoConfigurationV4.class
        })
@ComponentScan(
        basePackages = "com.ulticode.core",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.ulticode\\.core\\..*\\$.*"))
@EnableScheduling
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CoreApplication.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }
}
