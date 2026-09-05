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
                        "authTransactionManager",
                        "backend-auth", true),
                new CoreModuleDefinition(
                        "admin", "ADMIN", CoreOwnerBootConfigurations.Admin.class,
                        "adminTransactionManager",
                        "backend-admin", true),
                new CoreModuleDefinition(
                        "app", "APP", CoreOwnerBootConfigurations.App.class,
                        "appTransactionManager",
                        "backend-app-web", false),
                new CoreModuleDefinition(
                        "submission", "SUBMISSION", CoreOwnerBootConfigurations.Submission.class,
                        "submissionTransactionManager",
                        "backend-submission", false),
                new CoreModuleDefinition(
                        "notification", "NOTIFICATION", CoreOwnerBootConfigurations.Notification.class,
                        "notificationTransactionManager",
                        "backend-notification", false),
                new CoreModuleDefinition(
                        "search", "SEARCH", CoreOwnerBootConfigurations.Search.class,
                        null,
                        "backend-search", false)));
    }

    CoreModuleRegistry(List<CoreModuleDefinition> modules) {
        this.modules = List.copyOf(modules);
    }

    public List<CoreModuleDefinition> modules() {
        return modules;
    }

    public List<CoreModuleDefinition> enabledModules() {
        return modules.stream()
                .filter(CoreModuleDefinition::enabled)
                .toList();
    }

    public List<CoreModuleDefinition> disabledModules() {
        return modules.stream()
                .filter(module -> !module.enabled())
                .toList();
    }
}
