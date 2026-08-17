package com.ulticode.submission.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.ulticode.submission.api",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BackendSubmissionApiArchTest {

    private static final String API = "com.ulticode.submission.api..";

    @ArchTest
    static final ArchRule SUBMISSION_API_MUST_NOT_DEPEND_ON_IMPLEMENTATION =
            noClasses().that().resideInAPackage(API)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ulticode.app..",
                            "com.ulticode.modules..",
                            "com.ulticode.submission.compat..",
                            "com.ulticode.submission.dubbo..",
                            "com.ulticode.notification..",
                            "com.ulticode.admin..",
                            "com.baomidou..",
                            "org.apache.ibatis..",
                            "org.mybatis..",
                            "org.springframework.stereotype..",
                            "org.springframework.beans.factory.annotation..",
                            "org.springframework.context.annotation..",
                            "org.springframework.security..")
                    .because("Submission API must remain implementation-free");

    @ArchTest
    static final ArchRule SUBMISSION_API_DEPENDENCY_ALLOWLIST =
            classes().that().resideInAPackage(API)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            API,
                            "com.ulticode.common..",
                            "com.ulticode.domain..",
                            "java..",
                            "javax..",
                            "jakarta..",
                            "lombok..",
                            "com.fasterxml.jackson.annotation..",
                            "com.fasterxml.jackson.core..",
                            "com.fasterxml.jackson.databind..",
                            "org.springframework.context..")
                    .because("Submission API may only expose pure contract dependencies");

    @ArchTest
    static final ArchRule SUBMISSION_API_MUST_NOT_DEFINE_IMPLEMENTATIONS =
            noClasses().that().resideInAPackage(API)
                    .should().haveSimpleNameEndingWith("Entity")
                    .orShould().haveSimpleNameEndingWith("Mapper")
                    .orShould().haveSimpleNameEndingWith("ServiceImpl")
                    .orShould().haveSimpleNameEndingWith("Repository")
                    .because("Submission API contains contracts, not persistence or services");
}
