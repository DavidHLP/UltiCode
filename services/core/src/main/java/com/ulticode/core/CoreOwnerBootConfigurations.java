package com.ulticode.core;

import com.ulticode.app.config.MapperScanConfig;
import com.ulticode.modules.notification.port.adapter.DefaultNotificationAdminReadAdapter;
import com.ulticode.modules.submission.port.DefaultSubmissionFencePort;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Explicit child-context assemblies for six registered modules; Auth and Admin are enabled by default. */
final class CoreOwnerBootConfigurations {

    private CoreOwnerBootConfigurations() {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan({"com.ulticode.auth", "com.ulticode.common", "com.ulticode.websecurity"})
    @MapperScan({
            "com.ulticode.auth.account.mapper",
            "com.ulticode.auth.audit",
            "com.ulticode.auth.idempotency.mapper",
            "com.ulticode.auth.permission.mapper",
            "com.ulticode.auth.reconciliation",
            "com.ulticode.auth.refreshtoken.mapper",
            "com.ulticode.auth.search",
            "com.ulticode.auth.security.oauth.mapper"
    })
    @EnableScheduling
    static class Auth {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan({
            "com.ulticode.admin",
            "com.ulticode.modules.admin",
            "com.ulticode.modules.event.inbox",
            "com.ulticode.modules.backup",
            "com.ulticode.modules.lease",
            "com.ulticode.common"
    })
    @MapperScan({
            "com.ulticode.modules.admin.mapper",
            "com.ulticode.modules.admin.outbox.mapper",
            "com.ulticode.modules.event.inbox",
            "com.ulticode.modules.backup.mapper",
            "com.ulticode.modules.lease"
    })
    @EnableAsync
    @EnableScheduling
    static class Admin {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan({
            "com.ulticode.app",
            "com.ulticode.audit",
            "com.ulticode.common",
            "com.ulticode.modules.achievement",
            "com.ulticode.modules.bookmark",
            "com.ulticode.modules.contest",
            "com.ulticode.modules.dashboard",
            "com.ulticode.modules.edgeoperations",
            "com.ulticode.modules.event.outbox",
            "com.ulticode.modules.event.inbox",
            "com.ulticode.modules.event.replay",
            "com.ulticode.modules.follow",
            "com.ulticode.modules.forum",
            "com.ulticode.modules.moderation",
            "com.ulticode.modules.notification.event",
            "com.ulticode.modules.notification.intent",
            "com.ulticode.modules.notification.port",
            "com.ulticode.modules.problem",
            "com.ulticode.modules.problemlist",
            "com.ulticode.modules.reconciliation.port",
            "com.ulticode.modules.search",
            "com.ulticode.modules.solution",
            "com.ulticode.modules.submission.controller",
            "com.ulticode.modules.submission.event",
            "com.ulticode.modules.submission.port",
            "com.ulticode.modules.subscription",
            "com.ulticode.modules.user",
            "com.ulticode.modules.vote",
            "com.ulticode.modules.websocket",
            "com.ulticode.websecurity"
    })
    @Import(MapperScanConfig.class)
    @EnableScheduling
    static class App {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan({
            "com.ulticode.submission",
            "com.ulticode.modules.submission.mapper",
            "com.ulticode.modules.submission.projection",
            "com.ulticode.modules.submission.reaper",
            "com.ulticode.modules.submission.result",
            "com.ulticode.modules.submission.stats",
            "com.ulticode.modules.submission.outbox",
            "com.ulticode.modules.submission.entity",
            "com.ulticode.modules.submission.created",
            "com.ulticode.modules.queue",
            "com.ulticode.websecurity"
    })
    @Import({DefaultSubmissionFencePort.class, DefaultSubmissionWritePort.class})
    @MapperScan({
            "com.ulticode.modules.submission.mapper",
            "com.ulticode.submission.idempotency.mapper",
            "com.ulticode.modules.submission.outbox.mapper",
            "com.ulticode.modules.submission.result",
            "com.ulticode.modules.submission.created"
    })
    @EnableScheduling
    static class Submission {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan({
            "com.ulticode.notification",
            "com.ulticode.modules.notification.channel",
            "com.ulticode.modules.notification.dispatcher",
            "com.ulticode.modules.notification.service",
            "com.ulticode.modules.notification.ledger",
            "com.ulticode.modules.notification.consumer",
            "com.ulticode.modules.notification.controller",
            "com.ulticode.modules.notification.adapter",
            "com.ulticode.modules.email"
    })
    @Import(DefaultNotificationAdminReadAdapter.class)
    @MapperScan({
            "com.ulticode.modules.notification.mapper",
            "com.ulticode.modules.notification.ledger.mapper",
            "com.ulticode.modules.email.mapper",
            "com.ulticode.notification.idempotency.mapper",
            "com.ulticode.modules.event.inbox"
    })
    @EnableScheduling
    static class Notification {
    }

    @Configuration(proxyBeanMethods = false)
    // Search stays database-free while the Core classpath still carries the
    // JDBC/MyBatis starters; without these exclusions the child would try to
    // auto-configure a DataSource from `spring.datasource.*` that the manager
    // deliberately does not pass for Search.
    @EnableAutoConfiguration(
            excludeName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
            exclude = {
                    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
                    com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
            })
    @ComponentScan({"com.ulticode.search", "com.ulticode.common"})
    @EnableScheduling
    static class Search {
    }
}
