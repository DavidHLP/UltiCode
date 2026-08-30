package com.ulticode.notification.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ulticode.websecurity.jwt.AccessTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = NotificationRouteAuthorizationContractTest.ProbeController.class)
@ContextConfiguration(classes = {NotificationRouteAuthorizationContractTest.ProbeController.class,
        NotificationSecurityConfig.class})
class NotificationRouteAuthorizationContractTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AccessTokenVerifier accessTokenVerifier;

    @Test
    void anonymousCanReachExplicitHealthRoute() throws Exception {
        mockMvc.perform(get("/api/v1/notification/health"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotReadNotificationBusinessRoute() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanReadNotificationBusinessRoute() throws Exception {
        mockMvc.perform(get("/notifications").with(user("user-1").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotReachUnmatchedRoute() throws Exception {
        mockMvc.perform(get("/unknown"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/v1/notification/health", "/notifications", "/unknown"})
        String probe() {
            return "ok";
        }
    }
}
