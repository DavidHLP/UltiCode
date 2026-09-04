package com.ulticode.core;

import org.springframework.stereotype.Component;

import java.util.List;

/** Single source of truth for Core Owner Module assembly and ownership metadata. */
@Component
public final class CoreModuleRegistry {

    private final List<CoreModuleDefinition> modules;

    CoreModuleRegistry() {
        this(List.of(
                new CoreModuleDefinition(
                        "auth", "AUTH", CoreOwnerBootConfigurations.Auth.class,
                        "authTransactionManager"),
                new CoreModuleDefinition(
                        "admin", "ADMIN", CoreOwnerBootConfigurations.Admin.class,
                        "adminTransactionManager"),
                new CoreModuleDefinition(
                        "app", "APP", CoreOwnerBootConfigurations.App.class,
                        "appTransactionManager"),
                new CoreModuleDefinition(
                        "submission", "SUBMISSION", CoreOwnerBootConfigurations.Submission.class,
                        "submissionTransactionManager"),
                new CoreModuleDefinition(
                        "notification", "NOTIFICATION", CoreOwnerBootConfigurations.Notification.class,
                        "notificationTransactionManager"),
                new CoreModuleDefinition(
                        "search", "SEARCH", CoreOwnerBootConfigurations.Search.class,
                        null)));
    }

    CoreModuleRegistry(List<CoreModuleDefinition> modules) {
        this.modules = List.copyOf(modules);
    }

    public List<CoreModuleDefinition> modules() {
        return modules;
    }
}
