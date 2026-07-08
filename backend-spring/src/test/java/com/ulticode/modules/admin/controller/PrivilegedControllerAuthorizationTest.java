package com.ulticode.modules.admin.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.projection.DashboardStatsProjection;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@SpringJUnitConfig(PrivilegedControllerAuthorizationTest.TestConfig.class)
class PrivilegedControllerAuthorizationTest {

  @jakarta.annotation.Resource private DashboardController dashboardController;
  @jakarta.annotation.Resource private DashboardStatsProjection dashboardService;

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
    when(dashboardService.loadStats()).thenReturn(new DashboardStatsVO());
    assertThatCode(dashboardController::getStats).doesNotThrowAnyException();
  }

  @Test
  @WithMockUser(roles = "SUPER_ADMIN")
  void superAdministratorCanReadAdminDashboard() {
    when(dashboardService.loadStats()).thenReturn(new DashboardStatsVO());
    assertThatCode(dashboardController::getStats).doesNotThrowAnyException();
  }

  @Test
  @WithMockUser(roles = "USER")
  void ordinaryUserCannotReadAdminChartStats() {
    assertThatThrownBy(() -> dashboardController.loadChartStats("users", "day", 30))
        .isInstanceOf(AccessDeniedException.class);
  }

  /** Covers every entry in {@link DashboardController#ALLOWED_METRICS} (L2 fix). */
  @ParameterizedTest
  @MethodSource("allAllowedMetrics")
  @WithMockUser(roles = "ADMIN")
  void administratorCanReadChartStatsWithAnyAllowedMetric(String metric) {
    when(dashboardService.loadChartStats(metric, "day", 7)).thenReturn(new ChartStatsVO());
    assertThatCode(() -> dashboardController.loadChartStats(metric, "day", 7))
        .doesNotThrowAnyException();
  }

  static Stream<String> allAllowedMetrics() {
    return Stream.of(DashboardController.ALLOWED_METRICS);
  }

  // ---------- M1 fix: tests that actually exercise the new validation ----------

  @ParameterizedTest
  @ValueSource(strings = {"hack", "USER", "users;DROP TABLE", "<script>"})
  @WithMockUser(roles = "ADMIN")
  void invalidMetricRejected(String metric) {
    assertThatThrownBy(() -> dashboardController.loadChartStats(metric, "day", 7))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "minute", "DAY", "week;"})
  @WithMockUser(roles = "ADMIN")
  void invalidPeriodRejected(String period) {
    assertThatThrownBy(() -> dashboardController.loadChartStats("users", period, 7))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -100, 366, 9999})
  @WithMockUser(roles = "ADMIN")
  void outOfRangeDaysRejected(int days) {
    assertThatThrownBy(() -> dashboardController.loadChartStats("users", "day", days))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void daysAtBoundariesAccepted() {
    when(dashboardService.loadChartStats("users", "day", 1)).thenReturn(new ChartStatsVO());
    when(dashboardService.loadChartStats("users", "day", 365)).thenReturn(new ChartStatsVO());
    assertThatCode(() -> dashboardController.loadChartStats("users", "day", 1))
        .doesNotThrowAnyException();
    assertThatCode(() -> dashboardController.loadChartStats("users", "day", 365))
        .doesNotThrowAnyException();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void nullDaysAccepted() {
    when(dashboardService.loadChartStats("users", "day", null)).thenReturn(new ChartStatsVO());
    assertThatCode(() -> dashboardController.loadChartStats("users", "day", null))
        .doesNotThrowAnyException();
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    com.ulticode.common.auth.CurrentUserProvider currentUserProvider() {
      return mock(com.ulticode.common.auth.CurrentUserProvider.class);
    }
    @Bean
    DashboardStatsProjection dashboardService() {
      return mock(DashboardStatsProjection.class);
    }

    @Bean
    DashboardController dashboardController(DashboardStatsProjection dashboardService) {
      return new DashboardController(dashboardService);
    }

    /** M1 fix: enables @Min/@Max/@Pattern enforcement on @RequestParam in test context. */
    @Bean
    static MethodValidationPostProcessor methodValidationPostProcessor() {
      return new MethodValidationPostProcessor();
    }
  }
}
