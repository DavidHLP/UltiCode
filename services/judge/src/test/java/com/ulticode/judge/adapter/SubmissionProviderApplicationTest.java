package com.ulticode.judge.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.util.AnnotationUtils;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit owner mapping lets Judge subscribe before Submission has registered. */
class SubmissionProviderApplicationTest {
    private static final String PROPERTY = "ulticode.rpc.providers.submission";
    private static final String DECLARATION = "${" + PROPERTY + ":backend-submission}";

    @Test
    void bothSubmissionReferencesDeclareOwnerAndPreserveWritePolicies() throws Exception {
        for (Field field : referenceFields()) {
            DubboReference reference = field.getAnnotation(DubboReference.class);
            assertThat(reference).as(field.toString()).isNotNull();
            assertThat(reference.providedBy()).as(field.toString()).containsExactly(DECLARATION);
            assertThat(reference.group()).isEqualTo("backend-submission");
            assertThat(reference.version()).isEqualTo(
                    field.getDeclaringClass() == RemoteSubmissionFencePort.class ? "1.1.0" : "1.0.0");
            assertThat(reference.timeout()).isEqualTo(RpcPolicy.WRITE_TIMEOUT_MS);
            assertThat(reference.retries()).isEqualTo(RpcPolicy.WRITE_RETRIES).isZero();
            assertThat(reference.check()).isFalse();
        }
    }

    @Test
    void actualDubboAnnotationResolutionUsesDefaultProviderName() throws Exception {
        assertMapping(cleanEnvironment(), "backend-submission");
    }

    @Test
    void configuredProviderNameResolvesThroughDubboAnnotationAndMapping() throws Exception {
        StandardEnvironment environment = cleanEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("override", Map.of(
                PROPERTY, "custom-submission", "DUBBO_APPLICATION_NAME", "different-judge")));
        assertMapping(environment, "custom-submission");
    }

    @Test
    void environmentAliasResolvesWithoutUsingConsumersOwnApplicationName() throws Exception {
        StandardEnvironment environment = cleanEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("fixture", Map.of(
                "ULTICODE_RPC_PROVIDERS_SUBMISSION", "renamed-submission",
                "DUBBO_APPLICATION_NAME", "renamed-judge")));
        assertMapping(environment, "renamed-submission");
    }

    @Test
    void consumerApplicationNameAloneDoesNotRetargetDefaultOwner() throws Exception {
        StandardEnvironment environment = cleanEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("fixture", Map.of(
                "DUBBO_APPLICATION_NAME", "renamed-judge")));
        assertMapping(environment, "backend-submission");
    }

    private static void assertMapping(StandardEnvironment environment, String application) throws Exception {
        for (Field field : referenceFields()) {
            DubboReference reference = field.getAnnotation(DubboReference.class);
            String[] providedBy = (String[]) AnnotationUtils.getAttributes(reference, environment, true)
                    .get("providedBy");
            assertThat(providedBy).as(field.toString()).containsExactly(application);
            URL consumer = URL.valueOf("consumer://localhost/" + field.getType().getName())
                    .addParameter("provided-by", String.join(",", providedBy));
            assertThat(ServiceNameMapping.getMappingByUrl(consumer)).containsExactly(application);
        }
    }

    private static StandardEnvironment cleanEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return environment;
    }

    private static List<Field> referenceFields() throws NoSuchFieldException {
        return List.of(
                RemoteSubmissionVerdictWritePort.class.getDeclaredField("submissionVerdict"),
                RemoteSubmissionFencePort.class.getDeclaredField("submissionFencePort"));
    }
}
