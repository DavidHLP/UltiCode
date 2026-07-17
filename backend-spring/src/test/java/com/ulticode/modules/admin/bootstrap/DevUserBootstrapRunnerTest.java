package com.ulticode.modules.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * The runner now owns only its development identity policy and delegates account materialization
 * to {@link AdministratorProvisioner}; these tests lock the delegation and the refusal paths. The
 * provisioner invariant itself is covered by {@link AdministratorProvisionerTest}.
 */
@ExtendWith(MockitoExtension.class)
class DevUserBootstrapRunnerTest {

  @Mock private UserMapper userMapper;
  @Mock private ConfigurableApplicationContext applicationContext;
  @Mock private AdministratorProvisioner provisioner;

  @Test
  void delegatesCreationWhenNoAccountExists() {
    MockEnvironment environment = developmentEnvironment();
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userMapper.selectCount(any())).thenReturn(0L);

    runner(environment).run(mock(ApplicationArguments.class));

    verify(provisioner)
        .createAdministrator(
            eq("admin"),
            eq("Development Administrator"),
            eq("admin@localhost.test"),
            eq("admin123"),
            eq("ADMIN"));
    verify(provisioner, never()).restoreAdministrator(any(), any(), any(), any(), any());
    verify(applicationContext).close();
  }

  @Test
  void delegatesRestorationWhenAccountExists() {
    MockEnvironment environment = developmentEnvironment();
    User existing = new User();
    existing.setId("admin-id");
    existing.setUsername("admin");
    when(userMapper.selectOne(any())).thenReturn(existing);
    when(userMapper.selectCount(any())).thenReturn(0L);

    runner(environment).run(mock(ApplicationArguments.class));

    verify(provisioner)
        .restoreAdministrator(
            eq(existing),
            eq("Development Administrator"),
            eq("admin@localhost.test"),
            eq("admin123"),
            eq("ADMIN"));
    verify(provisioner, never())
        .createAdministrator(any(), any(), any(), any(), any());
    verify(applicationContext).close();
  }

  @Test
  void refusesToRunOutsideDevelopmentProfile() {
    MockEnvironment environment = developmentEnvironment();
    environment.setActiveProfiles("prod");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dev profile");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
    verify(provisioner, never()).restoreAdministrator(any(), any(), any(), any(), any());
  }

  @Test
  void refusesWhenEmailBelongsToAnotherAccount() {
    MockEnvironment environment = developmentEnvironment();
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEV_SEED_ADMIN_EMAIL");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
    verify(provisioner, never()).restoreAdministrator(any(), any(), any(), any(), any());
  }

  @Test
  void refusesUnsupportedRole() {
    MockEnvironment environment = developmentEnvironment();
    environment.setProperty("DEV_SEED_ADMIN_ROLE", "GUEST");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEV_SEED_ADMIN_ROLE");

    verify(provisioner, never()).createAdministrator(any(), any(), any(), any(), any());
    verify(provisioner, never()).restoreAdministrator(any(), any(), any(), any(), any());
  }

  private DevUserBootstrapRunner runner(MockEnvironment environment) {
    return new DevUserBootstrapRunner(userMapper, environment, applicationContext, provisioner);
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
