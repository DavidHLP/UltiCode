package com.ulticode.auth.security;

import com.ulticode.auth.security.jwt.AuthAccessTokenVerifier;
import com.ulticode.auth.security.jwt.AuthJwtFilterConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthSecurityConfigContractTest.SecurityProbeController.class)
@Import({AuthSecurityConfig.class, AuthAuthenticationEntryPoint.class, AuthJwtFilterConfiguration.class,
        AuthSecurityConfigContractTest.SecurityProbeController.class})
class AuthSecurityConfigContractTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuthAccessTokenVerifier accessTokenVerifier;


    @Test
    void currentSessionEndpointsRequireAuthenticationAtFilterChain() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void savedAccountSwitchRequiresDoubleSubmitCsrfWithoutAnActiveAccessToken() throws Exception {
        var saved = new jakarta.servlet.http.Cookie("saved_account_YWxpY2U", "saved");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/accounts/switch")
                .cookie(saved)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/accounts/switch")
                .cookie(saved, new jakarta.servlet.http.Cookie("csrf_token", "csrf"))
                .header("X-CSRF-Token", "csrf")).andExpect(status().isOk());
    }

    @RestController
    static class SecurityProbeController {

        @GetMapping({"/auth/me", "/auth/permissions"})
        String probe() {
            return "reached-controller";
        }
        @org.springframework.web.bind.annotation.PostMapping("/auth/accounts/switch")
        String switchProbe() { return "reached-controller"; }
    }
}
