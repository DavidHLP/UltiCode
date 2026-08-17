package com.ulticode.notification.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.ulticode.notification.api",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BackendNotificationApiArchTest {

    private static final String API = "com.ulticode.notification.api..";

    @ArchTest
    static final ArchRule NOTIFICATION_API_MUST_NOT_DEPEND_ON_IMPLEMENTATION =
            noClasses().that().resideInAPackage(API)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ulticode.app..",
                            "com.ulticode.modules..",
                            "com.ulticode.notification.compat..",
                            "com.ulticode.notification.dubbo..",
                            "com.ulticode.submission..",
                            "com.ulticode.admin..",
                            "com.baomidou..",
                            "org.apache.ibatis..",
                            "org.mybatis..",
                            "org.springframework.stereotype..",
                            "org.springframework.beans.factory.annotation..",
                            "org.springframework.context.annotation..",
                            "org.springframework.security..")
                    .because("Notification API must remain implementation-free");

    @ArchTest
    static final ArchRule NOTIFICATION_API_DEPENDENCY_ALLOWLIST =
            classes().that().resideInAPackage(API)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            API,
                            "com.ulticode.common..",
                            "java..",
                            "javax..",
                            "jakarta..",
                            "lombok..",
                            "com.fasterxml.jackson.annotation..")
                    .because("Notification API may only expose pure contract dependencies");

    @ArchTest
    static final ArchRule NOTIFICATION_API_MUST_NOT_DEFINE_IMPLEMENTATIONS =
            noClasses().that().resideInAPackage(API)
                    .should().haveSimpleNameEndingWith("Entity")
                    .orShould().haveSimpleNameEndingWith("Mapper")
                    .orShould().haveSimpleNameEndingWith("ServiceImpl")
                    .orShould().haveSimpleNameEndingWith("Repository")
                    .because("Notification API contains contracts, not persistence or services");
}
