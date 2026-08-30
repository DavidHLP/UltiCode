package com.ulticode.admin.security;

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

@WebMvcTest(controllers = AdminRouteAuthorizationContractTest.ProbeController.class)
@ContextConfiguration(classes = {AdminRouteAuthorizationContractTest.ProbeController.class,
        AdminSecurityConfig.class})
class AdminRouteAuthorizationContractTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AccessTokenVerifier accessTokenVerifier;

    @Test
    void anonymousCanReachOnlyExplicitHealthRoute() throws Exception {
        mockMvc.perform(get("/api/v1/admin/health"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotReachAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/probe"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUserCannotReachAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/probe").with(user("user-1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReachAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/probe").with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void unmatchedRouteIsDeniedEvenToAdministrator() throws Exception {
        mockMvc.perform(get("/unknown").with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/v1/admin/health", "/admin/probe", "/unknown"})
        String probe() {
            return "ok";
        }
    }
}
