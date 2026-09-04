package com.ulticode.core;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Objects;

/** Five explicit Owner persistence assemblies; Search remains database-free. */
@Configuration(proxyBeanMethods = false)
public class CoreOwnerDataSourceConfiguration {

    @Bean("authDataSourceProperties")
    @ConfigurationProperties("core.datasource.auth")
    DataSourceProperties authDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("adminDataSourceProperties")
    @ConfigurationProperties("core.datasource.admin")
    DataSourceProperties adminDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("appDataSourceProperties")
    @ConfigurationProperties("core.datasource.app")
    DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("submissionDataSourceProperties")
    @ConfigurationProperties("core.datasource.submission")
    DataSourceProperties submissionDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("notificationDataSourceProperties")
    @ConfigurationProperties("core.datasource.notification")
    DataSourceProperties notificationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("authDataSource")
    @ConfigurationProperties("core.datasource.auth.hikari")
    HikariDataSource authDataSource(
            @Qualifier("authDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("adminDataSource")
    @ConfigurationProperties("core.datasource.admin.hikari")
    HikariDataSource adminDataSource(
            @Qualifier("adminDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("appDataSource")
    @ConfigurationProperties("core.datasource.app.hikari")
    HikariDataSource appDataSource(
            @Qualifier("appDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("submissionDataSource")
    @ConfigurationProperties("core.datasource.submission.hikari")
    HikariDataSource submissionDataSource(
            @Qualifier("submissionDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("notificationDataSource")
    @ConfigurationProperties("core.datasource.notification.hikari")
    HikariDataSource notificationDataSource(
            @Qualifier("notificationDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("authTransactionManager")
    PlatformTransactionManager authTransactionManager(@Qualifier("authDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean("adminTransactionManager")
    PlatformTransactionManager adminTransactionManager(@Qualifier("adminDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean("appTransactionManager")
    PlatformTransactionManager appTransactionManager(@Qualifier("appDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean("submissionTransactionManager")
    PlatformTransactionManager submissionTransactionManager(@Qualifier("submissionDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean("notificationTransactionManager")
    PlatformTransactionManager notificationTransactionManager(@Qualifier("notificationDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean("authSqlSessionFactory")
    SqlSessionFactory authSqlSessionFactory(@Qualifier("authDataSource") DataSource dataSource)
            throws Exception {
        return sqlSessionFactory(dataSource);
    }

    @Bean("adminSqlSessionFactory")
    SqlSessionFactory adminSqlSessionFactory(@Qualifier("adminDataSource") DataSource dataSource)
            throws Exception {
        return sqlSessionFactory(dataSource);
    }

    @Bean("appSqlSessionFactory")
    SqlSessionFactory appSqlSessionFactory(@Qualifier("appDataSource") DataSource dataSource)
            throws Exception {
        return sqlSessionFactory(dataSource);
    }

    @Bean("submissionSqlSessionFactory")
    SqlSessionFactory submissionSqlSessionFactory(@Qualifier("submissionDataSource") DataSource dataSource)
            throws Exception {
        return sqlSessionFactory(dataSource);
    }

    @Bean("notificationSqlSessionFactory")
    SqlSessionFactory notificationSqlSessionFactory(
            @Qualifier("notificationDataSource") DataSource dataSource) throws Exception {
        return sqlSessionFactory(dataSource);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return Objects.requireNonNull(factory.getObject(), "SqlSessionFactory was not created");
    }

}
