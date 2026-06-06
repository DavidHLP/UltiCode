package com.ulticode.modules.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DevUserBootstrapRunnerTest {

  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ConfigurableApplicationContext applicationContext;

  @Test
  void createsDocumentedDevelopmentAdministrator() {
    MockEnvironment environment = developmentEnvironment();
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(passwordEncoder.encode("admin123")).thenReturn("encoded-admin123");

    runner(environment).run(mock(ApplicationArguments.class));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userMapper).insert(captor.capture());
    User user = captor.getValue();
    assertThat(user.getUsername()).isEqualTo("admin");
    assertThat(user.getPassword()).isEqualTo("encoded-admin123");
    assertThat(user.getRole()).isEqualTo("ADMIN");
    assertThat(user.getIsActive()).isTrue();
    assertThat(user.getIsBanned()).isFalse();
    verify(applicationContext).close();
  }

  @Test
  void restoresAnExistingDisabledAdministrator() {
    MockEnvironment environment = developmentEnvironment();
    User existing = new User();
    existing.setId("admin-id");
    existing.setUsername("admin");
    existing.setIsActive(false);
    existing.setIsBanned(true);
    existing.setBannedReason("Disabled seed account");
    when(userMapper.selectOne(any())).thenReturn(existing);
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(passwordEncoder.encode("admin123")).thenReturn("encoded-admin123");

    runner(environment).run(mock(ApplicationArguments.class));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userMapper).updateById(captor.capture());
    assertThat(captor.getValue().getIsActive()).isTrue();
    assertThat(captor.getValue().getIsBanned()).isFalse();
    assertThat(captor.getValue().getBannedReason()).isNull();
    verify(userMapper, never()).insert(any(User.class));
  }

  @Test
  void refusesToRunOutsideDevelopmentProfile() {
    MockEnvironment environment = developmentEnvironment();
    environment.setActiveProfiles("prod");

    assertThatThrownBy(() -> runner(environment).run(mock(ApplicationArguments.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dev profile");

    verify(userMapper, never()).insert(any(User.class));
    verify(userMapper, never()).updateById(any(User.class));
  }

  private DevUserBootstrapRunner runner(MockEnvironment environment) {
    return new DevUserBootstrapRunner(
        userMapper, passwordEncoder, environment, applicationContext);
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
