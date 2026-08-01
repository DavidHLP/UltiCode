package com.ulticode;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.app.api.service.BookmarkReadPort;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.app.i18n.service.I18nService;
import com.ulticode.modules.bookmark.projection.BookmarkProjection;
import com.ulticode.modules.bookmark.service.BookmarkService;
import com.ulticode.modules.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * P1-INFRA-005: verify the app service shell boots and exposes health.
 *
 * <p>P7-RELOCATE-I18N-001: @MockBean I18nService so the context loads when
 * MybatisAutoConfiguration is excluded (no real TranslationMapper bean).
 *
 * <p>P7-APP-SUBSCRIPTION-001: @MockBean SubscriptionService and
 * SubscriptionReadPort so the context loads without a datasource.
 *
 * <p>P7-APP-BOOKMARK-001: @MockBean BookmarkService, BookmarkReadPort,
 * and BookmarkProjection so the context loads without a datasource
 * (no real BookmarkMapper/BookmarkFolderMapper bean).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendAppApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @MockBean
    private I18nService i18nService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private SubscriptionReadPort subscriptionReadPort;

    @MockBean
    private BookmarkService bookmarkService;

    @MockBean
    private BookmarkReadPort bookmarkReadPort;

    @MockBean
    private BookmarkProjection bookmarkProjection;

    @Test
    @DisplayName("context loads and /actuator/health is UP")
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("placeholder /api/v1/app/health returns success")
    void placeholderReturnsOk() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/api/v1/app/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("backend-app shell up");
    }
}
