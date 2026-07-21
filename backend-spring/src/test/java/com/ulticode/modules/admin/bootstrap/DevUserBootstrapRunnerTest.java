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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * The runner now owns only its development identity policy and delegates account materialization
 * to {@link UserProvisioningPort}; these tests lock the delegation and the refusal paths. The
 * provisioning invariant itself is covered by {@code user.port.UserProvisioningAdapterTest}.
 */
@ExtendWith(MockitoExtension.class)
class DevUserBootstrapRunnerTest {

  @Mock private UserProvisioningPort userProvisioningPort;
  @Mock private ConfigurableApplicationContext applicationContext;

  @Test
  void delegatesCreationWhenNoAccountExists() {
    MockEnvironment environment = developmentEnvironment();
    when(userProvisioningPort.findIdByUsername("admin")).thenReturn(Optional.empty());
    when(userProvisioningPort.emailConflicts(eq("admin@localhost.test"), any())).thenReturn(false);

    runner(environment).run(mock(ApplicationArguments.class));

    verify(userProvisioningPort)
        .createAdministrator(
            new AdministratorSpec(
                "admin", "Development Administrator", "admin@localhost.test", "admin123", "ADMIN"));
    verify(userProvisioningPort, never()).restoreAdministrator(any(), any());
    verify(applicationContext).close();
  }

  @Test
  void delegatesRestorationWhenAccountExists() {
    MockEnvironment environment = developmentEnvironment();
    when(userProvisioningPort.findIdByUsername("admin")).thenReturn(Optional.of("admin-id"));
    when(userProvisioningPort.emailConflicts(eq("admin@localhost.test"), any())).thenReturn(false);

    runner(environment).run(mock(ApplicationArguments.class));

    verify(userProvisioningPort)
        .restoreAdministrator(
            eq("admin-id"),
            eq(new AdministratorSpec(
                "admin", "Development Administrator", "admin@localhost.test", "admin123", "ADMIN")));
    verify(userProvisioningPort, never()).createAdministrator(any());
    verify(applicationContext).close();
  }

  @Test
  void refusesToRunOutsideDevelopmentProfile() {
    MockEnvironment environment = developmentEnvironment();
    environment.setActiveProfiles("prod");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dev profile");

    verify(userProvisioningPort, never()).createAdministrator(any());
    verify(userProvisioningPort, never()).restoreAdministrator(any(), any());
  }

  @Test
  void refusesWhenEmailBelongsToAnotherAccount() {
    MockEnvironment environment = developmentEnvironment();
    when(userProvisioningPort.findIdByUsername("admin")).thenReturn(Optional.empty());
    when(userProvisioningPort.emailConflicts(eq("admin@localhost.test"), any())).thenReturn(true);

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEV_SEED_ADMIN_EMAIL");

    verify(userProvisioningPort, never()).createAdministrator(any());
    verify(userProvisioningPort, never()).restoreAdministrator(any(), any());
  }

  @Test
  void refusesUnsupportedRole() {
    MockEnvironment environment = developmentEnvironment();
    environment.setProperty("DEV_SEED_ADMIN_ROLE", "GUEST");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEV_SEED_ADMIN_ROLE");

    verify(userProvisioningPort, never()).createAdministrator(any());
    verify(userProvisioningPort, never()).restoreAdministrator(any(), any());
  }

  private DevUserBootstrapRunner runner(MockEnvironment environment) {
    return new DevUserBootstrapRunner(userProvisioningPort, environment, applicationContext);
  }

  private MockEnvironment developmentEnvironment() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("DEV_SEED_ADMIN_USERNAME", "admin")
            .withProperty("DEV_SEED_ADMIN_EMAIL", "admin@localhost.test")
            .withProperty("DEV_SEED_ADMIN_PASSWORD", "admin123")
            .withProperty("DEV_SEED_ADMIN_ROLE", "ADMIN");
    environment.setActiveProfiles("dev");
    return environment;
  }
}
