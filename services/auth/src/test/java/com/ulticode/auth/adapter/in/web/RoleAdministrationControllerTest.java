package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.permission.service.RoleAdministrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleAdministrationControllerTest {

    private RoleAdministrationService roleAdministrationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        roleAdministrationService = mock(RoleAdministrationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RoleAdministrationController(roleAdministrationService))
                .build();
    }

    @Test
    void changeRolePreservesExistingRouteAndDelegatesActor() throws Exception {
        when(roleAdministrationService.changeRole("user-1", "ADMIN", "admin-1"))
                .thenReturn("ADMIN");

        mockMvc.perform(post("/auth/admin/users/user-1/role")
                        .principal(() -> "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        verify(roleAdministrationService).changeRole("user-1", "ADMIN", "admin-1");
    }

    @Test
    void grantPermissionReturnsTransportSafeValueObject() throws Exception {
        RoleAdministrationService.PermissionGrant grant = new RoleAdministrationService.PermissionGrant(
                "grant-1", "user-1", "PROBLEM", "READ", "admin-1",
                LocalDateTime.of(2026, 8, 6, 12, 0), null);
        when(roleAdministrationService.grantPermission(
                "user-1", "READ", "PROBLEM", null, "admin-1"))
                .thenReturn(grant);

        mockMvc.perform(post("/auth/admin/users/user-1/permissions")
                        .principal(() -> "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"READ\",\"resource\":\"PROBLEM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("grant-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.action").value("READ"))
                .andExpect(jsonPath("$.data.resource").value("PROBLEM"));

        verify(roleAdministrationService).grantPermission(
                "user-1", "READ", "PROBLEM", null, "admin-1");
    }

    @Test
    void revokePermissionPreservesExistingRouteAndResult() throws Exception {
        when(roleAdministrationService.revokePermission(
                "user-1", "READ", "PROBLEM", "admin-1"))
                .thenReturn(true);

        mockMvc.perform(delete("/auth/admin/users/user-1/permissions")
                        .principal(() -> "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"READ\",\"resource\":\"PROBLEM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.removed").value(true));

        verify(roleAdministrationService).revokePermission(
                "user-1", "READ", "PROBLEM", "admin-1");
    }
}
