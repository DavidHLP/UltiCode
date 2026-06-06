package com.ulticode.modules.admin.bootstrap;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Explicit one-time administrator bootstrap command.
 *
 * <p>Run the application as a non-web process with APP_BOOTSTRAP_ADMIN_ENABLED=true. The command
 * refuses to run when any active administrator already exists and never logs the password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern STRONG_PASSWORD =
      Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{16,}$");

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final Environment environment;
  private final ConfigurableApplicationContext applicationContext;

  @Override
  public void run(ApplicationArguments args) {
    String username = required("APP_BOOTSTRAP_ADMIN_USERNAME");
    String email = required("APP_BOOTSTRAP_ADMIN_EMAIL");
    String password = required("APP_BOOTSTRAP_ADMIN_PASSWORD");

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_EMAIL must be a valid email address");
    }
    if (!STRONG_PASSWORD.matcher(password).matches()) {
      throw new IllegalStateException(
          "APP_BOOTSTRAP_ADMIN_PASSWORD must be at least 16 characters and include upper, lower, "
              + "digit, and symbol characters");
    }

    Long activeAdmins =
        userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .in(User::getRole, "ADMIN", "SUPER_ADMIN")
                .eq(User::getIsActive, true)
                .eq(User::getIsBanned, false));
    if (activeAdmins > 0) {
      throw new IllegalStateException("An active administrator already exists; bootstrap refused");
    }

    Long duplicateIdentity =
        userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .and(wrapper -> wrapper.eq(User::getUsername, username).or().eq(User::getEmail, email)));
    if (duplicateIdentity > 0) {
      throw new IllegalStateException("Bootstrap username or email already exists; overwrite refused");
    }

    User user = new User();
    user.setId(IdUtil.fastSimpleUUID());
    user.setUsername(username);
    user.setName(username);
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(password));
    user.setRole("SUPER_ADMIN");
    user.setIsActive(true);
    user.setIsBanned(false);
    user.setIsDeleted(0);
    user.setJoinedAt(LocalDateTime.now());
    userMapper.insert(user);

    log.info("Created bootstrap SUPER_ADMIN account: {}", username);
    applicationContext.close();
  }

  private String required(String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " is required");
    }
    return value;
  }
}
