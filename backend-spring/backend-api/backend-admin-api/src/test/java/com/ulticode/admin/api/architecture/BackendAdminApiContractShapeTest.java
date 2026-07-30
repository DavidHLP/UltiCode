package com.ulticode.admin.api.architecture;

import com.ulticode.admin.api.dto.AdminNotificationQuery;
import com.ulticode.admin.api.dto.AdminNotificationVO;
import com.ulticode.admin.api.dto.CreateSystemNotificationRequest;
import com.ulticode.admin.api.dto.UpdateSystemNotificationRequest;
import com.ulticode.admin.api.service.AdminNotificationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract-shape assertions for backend-admin-api (P7-RELOCATE-ADMIN-001).
 *
 * <p>Locks the following invariants for the admin contract module:
 * <ol>
 *   <li>Service interfaces are pure Java (no Spring/MyBatis/Dubbo annotations
 *       on methods or parameters);</li>
 *   <li>DTOs use String UUIDs for identifiers (not Long/BigInteger);</li>
 *   <li>Query DTOs have defensive defaults (page/size bounds);</li>
 *   <li>VO records have String id field (UUID convention).</li>
 * </ol>
 */
class BackendAdminApiContractShapeTest {

    @Test
    void adminNotificationServiceHasNoSpringAnnotations() {
        for (Method method : AdminNotificationService.class.getDeclaredMethods()) {
            assertThat(method.getAnnotations())
                    .as("AdminNotificationService.%s should not carry Spring annotations", method.getName())
                    .noneMatch(a -> a.annotationType().getName().startsWith("org.springframework"))
                    .noneMatch(a -> a.annotationType().getName().startsWith("com.baomidou"))
                    .noneMatch(a -> a.annotationType().getName().startsWith("org.apache.dubbo"));

            for (Parameter param : method.getParameters()) {
                assertThat(param.getAnnotations())
                        .as("AdminNotificationService.%s parameter %s should not carry Spring annotations",
                                method.getName(), param.getName())
                        .noneMatch(a -> a.annotationType().getName().startsWith("org.springframework"));
            }
        }
    }

    @Test
    void adminNotificationQueryHasDefensiveDefaults() {
        AdminNotificationQuery q = new AdminNotificationQuery(null, null, null, 0, 0);
        assertThat(q.page()).isEqualTo(1);
        assertThat(q.size()).isEqualTo(20);
    }

    @Test
    void adminNotificationQueryRejectsExcessiveSize() {
        AdminNotificationQuery q = new AdminNotificationQuery(null, null, null, 1, 5000);
        assertThat(q.size()).isEqualTo(20);
    }

    @Test
    void adminNotificationVoHasStringId() {
        assertThat(AdminNotificationVO.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .contains("id");
        assertThat(AdminNotificationVO.class.getRecordComponents())
                .filteredOn(rc -> rc.getName().equals("id"))
                .extracting(rc -> rc.getType().getName())
                .containsExactly("java.lang.String");
    }

    @Test
    void createRequestHasNoIdField() {
        assertThat(CreateSystemNotificationRequest.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("id");
    }

    @Test
    void updateRequestHasNoIdField() {
        assertThat(UpdateSystemNotificationRequest.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("id");
    }
}
