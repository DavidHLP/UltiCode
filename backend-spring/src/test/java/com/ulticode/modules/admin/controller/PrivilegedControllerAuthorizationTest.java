package com.ulticode.modules.admin.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(PrivilegedControllerAuthorizationTest.TestConfig.class)
class PrivilegedControllerAuthorizationTest {

  @jakarta.annotation.Resource private DashboardController dashboardController;
  @jakarta.annotation.Resource private DashboardService dashboardService;

  @Test
  @WithMockUser(roles = "USER")
  void ordinaryUserCannotReadAdminDashboard() {
    assertThatThrownBy(dashboardController::getStats).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "MODERATOR")
  void moderatorCannotReadAdminDashboard() {
    assertThatThrownBy(dashboardController::getStats).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void administratorCanReadAdminDashboard() {
    when(dashboardService.getStats()).thenReturn(new DashboardStatsVO());
    assertThatCode(dashboardController::getStats).doesNotThrowAnyException();
  }

  @Test
  @WithMockUser(roles = "SUPER_ADMIN")
  void superAdministratorCanReadAdminDashboard() {
    when(dashboardService.getStats()).thenReturn(new DashboardStatsVO());
    assertThatCode(dashboardController::getStats).doesNotThrowAnyException();
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    DashboardService dashboardService() {
      return mock(DashboardService.class);
    }

    @Bean
    DashboardController dashboardController(DashboardService dashboardService) {
      return new DashboardController(dashboardService);
    }
  }
}
