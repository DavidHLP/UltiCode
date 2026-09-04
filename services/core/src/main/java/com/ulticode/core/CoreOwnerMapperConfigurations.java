package com.ulticode.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Top-level assemblies avoid duplicate scanner registration in one context. */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {
        "com.ulticode.auth.account.mapper",
        "com.ulticode.auth.refreshtoken.mapper",
        "com.ulticode.auth.idempotency.mapper",
        "com.ulticode.auth.permission.mapper",
        "com.ulticode.auth.search",
        "com.ulticode.auth.security.oauth.mapper",
        "com.ulticode.auth.audit",
        "com.ulticode.auth.reconciliation"
}, sqlSessionFactoryRef = "authSqlSessionFactory")
class CoreAuthMapperConfiguration {
}

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {
        "com.ulticode.modules.admin.mapper",
        "com.ulticode.modules.admin.outbox.mapper",
        "com.ulticode.modules.backup.mapper",
        "com.ulticode.modules.lease",
}, sqlSessionFactoryRef = "adminSqlSessionFactory")
class CoreAdminMapperConfiguration {
}

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {
        "com.ulticode.modules.follow.mapper",
        "com.ulticode.modules.bookmark.mapper",
        "com.ulticode.modules.solution.mapper",
        "com.ulticode.modules.forum.mapper",
        "com.ulticode.modules.problem.mapper",
        "com.ulticode.modules.contest.mapper",
        "com.ulticode.modules.vote.mapper",
        "com.ulticode.modules.moderation.mapper",
        "com.ulticode.modules.achievement.mapper",
        "com.ulticode.modules.event.outbox",
        "com.ulticode.app.userprofile.mapper",
        "com.ulticode.app.audit",
        "com.ulticode.app.user.port",
        "com.ulticode.app.i18n.mapper",
        "com.ulticode.app.idempotency.mapper",
        "com.ulticode.modules.problemlist.mapper",
        "com.ulticode.modules.reconciliation.port",
        "com.ulticode.modules.dashboard.mapper",
        "com.ulticode.modules.subscription.mapper"
}, sqlSessionFactoryRef = "appSqlSessionFactory")
class CoreAppMapperConfiguration {
}

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {
        "com.ulticode.modules.submission.mapper",
        "com.ulticode.submission.idempotency.mapper",
        "com.ulticode.modules.submission.outbox.mapper",
        "com.ulticode.modules.submission.result",
        "com.ulticode.modules.submission.created"
}, sqlSessionFactoryRef = "submissionSqlSessionFactory")
class CoreSubmissionMapperConfiguration {
}

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {
        "com.ulticode.modules.notification.mapper",
        "com.ulticode.modules.notification.ledger.mapper",
        "com.ulticode.modules.email.mapper",
        "com.ulticode.notification.idempotency.mapper"
}, sqlSessionFactoryRef = "notificationSqlSessionFactory")
class CoreNotificationMapperConfiguration {
}
