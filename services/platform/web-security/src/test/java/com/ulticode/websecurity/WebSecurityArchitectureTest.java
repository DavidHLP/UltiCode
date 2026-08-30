package com.ulticode.websecurity;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class WebSecurityArchitectureTest {

    private final JavaClasses moduleClasses = new ClassFileImporter()
            .importPackages("com.ulticode.websecurity", "com.ulticode.common.auth");

    @Test
    void moduleDoesNotDependOnServiceShells() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ulticode.auth..",
                        "com.ulticode.admin..",
                        "com.ulticode.app..",
                        "com.ulticode.modules..")
                .check(moduleClasses);
    }

}
