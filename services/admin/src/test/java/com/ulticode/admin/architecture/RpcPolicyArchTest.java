package com.ulticode.admin.architecture;

import com.ulticode.common.rpc.RpcPolicy;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.apache.dubbo.config.annotation.DubboReference;

/**
 * Review 2026-08-25 P1: enforces the centralized RPC reliability policy.
 *
 * <p>{@link RpcPolicy} defines query = {@value RpcPolicy#QUERY_TIMEOUT_MS} ms /
 * {@value RpcPolicy#QUERY_RETRIES} retry and write = {@value RpcPolicy#WRITE_TIMEOUT_MS} ms /
 * {@value RpcPolicy#WRITE_RETRIES} retries. Every consumer {@code @DubboReference}
 * in this module must declare an explicit policy-compliant pair; bare
 * references or drifted literals (for example the historical
 * {@code timeout=3000, retries=2}) fail this test.</p>
 */
@AnalyzeClasses(packages = "com.ulticode", importOptions = ImportOption.DoNotIncludeTests.class)
class RpcPolicyArchTest {

    @ArchTest
    static final ArchRule dubboReferencesDeclareRpcPolicyCompliantTimeoutAndRetries =
            ArchRuleDefinition.fields()
                    .that().areAnnotatedWith(DubboReference.class)
                    .should(declareRpcPolicyCompliantTimeoutAndRetries())
                    .allowEmptyShould(true);

    private static ArchCondition<JavaField> declareRpcPolicyCompliantTimeoutAndRetries() {
        return new ArchCondition<>("declare timeout/retries from RpcPolicy "
                + "(query=" + RpcPolicy.QUERY_TIMEOUT_MS + "ms/" + RpcPolicy.QUERY_RETRIES
                + " retry, write=" + RpcPolicy.WRITE_TIMEOUT_MS + "ms/" + RpcPolicy.WRITE_RETRIES + " retries)") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                DubboReference reference = field.tryGetAnnotationOfType(DubboReference.class).orElse(null);
                if (reference == null) {
                    return;
                }
                int timeout = reference.timeout();
                int retries = reference.retries();
                boolean queryPolicy = timeout == RpcPolicy.QUERY_TIMEOUT_MS && retries == RpcPolicy.QUERY_RETRIES;
                boolean writePolicy = timeout == RpcPolicy.WRITE_TIMEOUT_MS && retries == RpcPolicy.WRITE_RETRIES;
                if (!queryPolicy && !writePolicy) {
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " declares timeout=" + timeout + ", retries=" + retries
                                    + "; use RpcPolicy.QUERY_TIMEOUT_MS/QUERY_RETRIES for reads or "
                                    + "RpcPolicy.WRITE_TIMEOUT_MS/WRITE_RETRIES for writes"));
                }
            }
        };
    }
}
