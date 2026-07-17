package com.ulticode.modules.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * The production bootstrap previously had no test. These lock its identity policy refusals (the
 * security-sensitive guards) and that it delegates account materialization to
 * {@link AdministratorProvisioner} only when every guard passes.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

  @Mock private UserMapper userMapper;
  @Mock private ConfigurableApplicationContext applicationContext;
  @Mock private AdministratorProvisioner provisioner;

  @Test
  void delegatesCreationWhenEnvironmentIsValidAndNoConflicts() {
    MockEnvironment environment = productionEnvironment();
    when(userMapper.selectCount(any())).thenReturn(0L);

    runner(environment).run(mock(ApplicationArguments.class));

    verify(provisioner)
        .createAdministrator(
            eq("root"),
            eq("root"),
            eq("root@example.com"),
            eq("Admin!234567890Ab"),
            eq("SUPER_ADMIN"));
    verify(applicationContext).close();
  }

  @Test
  void refusesWhenAnActiveAdministratorAlreadyExists() {
    MockEnvironment environment = productionEnvironment();
    when(userMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active administrator already exists");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
    verify(applicationContext, never()).close();
  }

  @Test
  void refusesWhenUsernameOrEmailAlreadyExists() {
    MockEnvironment environment = productionEnvironment();
    when(userMapper.selectCount(any())).thenReturn(0L, 1L);

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("username or email already exists");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
  }

  @Test
  void refusesOnInvalidEmail() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("APP_BOOTSTRAP_ADMIN_EMAIL", "not-an-email");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("valid email address");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
  }

  @Test
  void refusesOnWeakPassword() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("APP_BOOTSTRAP_ADMIN_PASSWORD", "short1");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
  }

  @Test
  void refusesWhenRequiredEnvVarMissing() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("APP_BOOTSTRAP_ADMIN_PASSWORD", "");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD is required");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
  }

  private AdminBootstrapRunner runner(MockEnvironment environment) {
    return new AdminBootstrapRunner(userMapper, environment, applicationContext, provisioner);
  }

  private MockEnvironment productionEnvironment() {
    return new MockEnvironment()
        .withProperty("APP_BOOTSTRAP_ADMIN_USERNAME", "root")
        .withProperty("APP_BOOTSTRAP_ADMIN_EMAIL", "root@example.com")
        .withProperty("APP_BOOTSTRAP_ADMIN_PASSWORD", "Admin!234567890Ab");
  }
}
