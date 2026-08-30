package com.ulticode.modules.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.modules.admin.port.UserProvisioningPort;
import com.ulticode.modules.admin.port.UserProvisioningPort.AdministratorSpec;
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
 * {@link UserProvisioningPort} only when every guard passes.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

  @Mock private UserProvisioningPort userProvisioningPort;
  @Mock private ConfigurableApplicationContext applicationContext;

  @Test
  void delegatesCreationWhenEnvironmentIsValidAndNoConflicts() {
    MockEnvironment environment = productionEnvironment();
    when(userProvisioningPort.countActiveAdministrators()).thenReturn(0L);
    when(userProvisioningPort.identityExists(eq("root"), eq("root@example.com"))).thenReturn(false);

    runner(environment).run(mock(ApplicationArguments.class));

    verify(userProvisioningPort)
        .createAdministrator(
            new AdministratorSpec("root", "root", "root@example.com", "Admin!234567890Ab", "SUPER_ADMIN"));
    verify(applicationContext).close();
  }

  @Test
  void abortsWhenAuthCannotVerifyAdministratorCount() {
    MockEnvironment environment = productionEnvironment();
    when(userProvisioningPort.countActiveAdministrators())
        .thenThrow(new IllegalStateException("AccountQueryService unavailable"));

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AccountQueryService unavailable");

    verify(userProvisioningPort, never()).identityExists(any(), any());
    verify(userProvisioningPort, never()).createAdministrator(any());
    verify(applicationContext, never()).close();
  }

  @Test
  void refusesWhenAnActiveAdministratorAlreadyExists() {
    MockEnvironment environment = productionEnvironment();
    when(userProvisioningPort.countActiveAdministrators()).thenReturn(1L);

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active administrator already exists");

    verify(userProvisioningPort, never()).createAdministrator(any());
    verify(applicationContext, never()).close();
  }

  @Test
  void refusesWhenUsernameOrEmailAlreadyExists() {
    MockEnvironment environment = productionEnvironment();
    when(userProvisioningPort.countActiveAdministrators()).thenReturn(0L);
    when(userProvisioningPort.identityExists(eq("root"), eq("root@example.com"))).thenReturn(true);

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("username or email already exists");

    verify(userProvisioningPort, never()).createAdministrator(any());
  }

  @Test
  void refusesOnInvalidEmail() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("APP_BOOTSTRAP_ADMIN_EMAIL", "not-an-email");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("valid email address");

    verify(userProvisioningPort, never()).createAdministrator(any());
  }

  @Test
  void refusesOnWeakPassword() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("APP_BOOTSTRAP_ADMIN_PASSWORD", "short1");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");

    verify(userProvisioningPort, never()).createAdministrator(any());
  }

  @Test
  void refusesWhenRequiredEnvVarMissing() {
    MockEnvironment environment = productionEnvironment();
    environment.setProperty("APP_BOOTSTRAP_ADMIN_PASSWORD", "");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD is required");

    verify(userProvisioningPort, never()).createAdministrator(any());
  }

  private AdminBootstrapRunner runner(MockEnvironment environment) {
    return new AdminBootstrapRunner(userProvisioningPort, environment, applicationContext);
  }

  private MockEnvironment productionEnvironment() {
    return new MockEnvironment()
        .withProperty("APP_BOOTSTRAP_ADMIN_USERNAME", "root")
        .withProperty("APP_BOOTSTRAP_ADMIN_EMAIL", "root@example.com")
        .withProperty("APP_BOOTSTRAP_ADMIN_PASSWORD", "Admin!234567890Ab")
        .withProperty("BOOTSTRAP_DELEGATION_PRIVATE_KEY", "test-private-key")
        .withProperty("BOOTSTRAP_DELEGATION_KEY_ID", "test-kid");
  }
}
