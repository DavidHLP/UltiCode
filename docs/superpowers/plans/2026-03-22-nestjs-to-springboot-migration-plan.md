# NestJS → Spring Boot 后端迁移修复 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Spring Boot 后端与 NestJS 后端的功能对等问题，确保认证系统完整可用。

**Architecture:** 渐进式 4 阶段修复（P0→P1→P2→P3），每阶段可独立验证部署。核心修复→认证增强→OAuth集成→完善功能。

**Tech Stack:** Spring Boot 3.5, Spring Security 6, MyBatis-Plus, Redis, MySQL, JWT (jjwt)

**Spec:** `docs/superpowers/specs/2026-03-22-nestjs-to-springboot-migration-design.md`

---

## 文件结构

### 新增文件 (12个)

```
backend-spring/src/main/java/com/ulticode/
├── common/
│   ├── annotation/
│   │   └── RateLimit.java              # 限流注解
│   └── aspect/
│       └── RateLimitAspect.java        # 限流切面
├── security/
│   └── csrf/
│       ├── CsrfService.java            # CSRF Token 服务
│       └── CsrfInterceptor.java        # CSRF 拦截器
├── modules/
│   ├── auth/
│   │   ├── service/
│   │   │   ├── OAuthService.java       # OAuth 服务
│   │   │   └── PasswordResetService.java # 密码重置服务
│   │   └── dto/
│   │       ├── ForgotPasswordDTO.java  # 忘记密码 DTO
│   │       └── ResetPasswordDTO.java   # 重置密码 DTO
│   ├── refresh-token/
│   │   ├── entity/RefreshToken.java    # Refresh Token 实体
│   │   ├── mapper/RefreshTokenMapper.java
│   │   └── service/RefreshTokenService.java
│   └── permission/
│       ├── entity/UserPermission.java  # 用户权限实体
│       ├── mapper/UserPermissionMapper.java
│       └── service/PermissionService.java
```

### 修改文件 (15个)

```
backend-spring/
├── src/main/java/com/ulticode/
│   ├── common/config/
│   │   ├── SecurityConfig.java
│   │   └── WebMvcConfig.java           # 新增：注册拦截器
│   ├── modules/
│   │   ├── user/entity/User.java
│   │   └── auth/
│   │       ├── controller/AuthController.java
│   │       ├── service/AuthService.java
│   │       ├── service/impl/AuthServiceImpl.java
│   │       └── dto/LoginResponse.java
│   └── security/
│       └── jwt/JwtProperties.java
├── src/main/resources/
│   └── application.yml
├── init-db/migrations/
│   ├── V009__add_user_soft_delete.sql
│   └── V010__add_refresh_token_hash.sql
└── .env.example
```

---

# Phase 1: 核心修复 (P0)

**目标:** 让基本登录功能可用

---

## Task 1.1: 修复 User 实体字段映射

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java`

- [ ] **Step 1: 读取当前 User.java 文件**

Run: 查看 `backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java`

- [ ] **Step 2: 修复字段映射**

移除错误的 `createdAt` 和 `updatedAt` 字段映射，保留正确的 `joinedAt` 和 `lastLoginAt`：

```java
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.INPUT)
    private String id;

    private String username;
    private String name;
    private String email;
    private String avatar;
    private String password;
    private String bio;
    private String company;
    private String github;
    private String location;
    private String twitter;
    private String website;

    @TableField("preferred_language")
    private String preferredLanguage;

    private String role;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("is_banned")
    private Boolean isBanned;

    @TableField("banned_until")
    private LocalDateTime bannedUntil;

    @TableField("banned_reason")
    private String bannedReason;

    @TableField("joined_at")
    private LocalDateTime joinedAt;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    // 新增：逻辑删除字段
    @TableField("is_deleted")
    @TableLogic
    private Integer isDeleted;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("deleted_by")
    private String deletedBy;
}
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/user/entity/User.java
git commit -m "fix: correct User entity field mapping and add soft delete fields"
```

---

## Task 1.2: 更新 MyBatis-Plus 配置

**Files:**
- Modify: `backend-spring/src/main/resources/application.yml`

- [ ] **Step 1: 读取当前 application.yml**

Run: 查看 `backend-spring/src/main/resources/application.yml`

- [ ] **Step 2: 更新 MyBatis-Plus 配置**

将 `id-type` 改为 `input`，`logic-delete-field` 改为 `isDeleted`：

```yaml
mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  type-aliases-package: com.ulticode.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: input                    # 改为 input（UUID）
      logic-delete-field: isDeleted     # 与实体字段一致
      logic-delete-value: 1
      logic-not-delete-value: 0
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/resources/application.yml
git commit -m "fix: update MyBatis-Plus config for UUID id-type and soft delete"
```

---

## Task 1.3: 更新 JWT Cookie 配置

**Files:**
- Modify: `backend-spring/src/main/resources/application.yml`
- Modify: `backend-spring/src/main/java/com/ulticode/security/jwt/JwtProperties.java`

- [ ] **Step 1: 更新 application.yml Cookie 配置**

将 Cookie 名称改为 NestJS 兼容的 `access_token` 和 `refresh_token`：

```yaml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-here-must-be-at-least-32-characters-long}
  access-token:
    expiration: 900000  # 15 minutes
  refresh-token:
    expiration: 604800000  # 7 days
  cookie:
    access-token:
      name: access_token
      http-only: true
      secure: ${JWT_COOKIE_SECURE:true}
      same-site: strict
      path: /
      max-age: 900
    refresh-token:
      name: refresh_token
      http-only: true
      secure: ${JWT_COOKIE_SECURE:true}
      same-site: strict
      path: /
      max-age: 604800
```

- [ ] **Step 2: 更新 JwtProperties.java**

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private AccessTokenConfig accessToken = new AccessTokenConfig();
    private RefreshTokenConfig refreshToken = new RefreshTokenConfig();
    private CookieConfig cookie = new CookieConfig();

    @Data
    public static class AccessTokenConfig {
        private Long expiration = 900000L;
    }

    @Data
    public static class RefreshTokenConfig {
        private Long expiration = 604800000L;
    }

    @Data
    public static class CookieConfig {
        private AccessTokenCookie accessToken = new AccessTokenCookie();
        private RefreshTokenCookie refreshToken = new RefreshTokenCookie();
    }

    @Data
    public static class AccessTokenCookie {
        private String name = "access_token";
        private boolean httpOnly = true;
        private boolean secure = true;
        private String sameSite = "strict";
        private String path = "/";
        private int maxAge = 900;
    }

    @Data
    public static class RefreshTokenCookie {
        private String name = "refresh_token";
        private boolean httpOnly = true;
        private boolean secure = true;
        private String sameSite = "strict";
        private String path = "/";
        private int maxAge = 604800;
    }
}
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/resources/application.yml src/main/java/com/ulticode/security/jwt/JwtProperties.java
git commit -m "fix: update JWT cookie names to match NestJS (access_token, refresh_token)"
```

---

## Task 1.4: 修复 extractRefreshToken 方法 Bug

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`

- [ ] **Step 1: 读取 AuthController.java**

Run: 查看 `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`

- [ ] **Step 2: 修复 extractRefreshToken 方法**

将 `ACCESS_TOKEN_COOKIE` 改为 `REFRESH_TOKEN_COOKIE`：

```java
private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

/**
 * Extract refresh token from cookies.
 */
private String extractRefreshToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
    }
    return null;
}
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/controller/AuthController.java
git commit -m "fix: correct extractRefreshToken to read from refresh_token cookie"
```

---

## Task 1.5: 添加 /auth/me 端点

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`

- [ ] **Step 1: 添加 /auth/me 端点**

```java
@Operation(summary = "Get current user", description = "Get the authenticated user profile with CSRF token")
@GetMapping("/me")
public Result<UserWithCsrfVO> getCurrentUser(@AuthenticationPrincipal User user) {
    String csrfToken = csrfService.generateToken(user.getId());
    UserVO userVO = userService.toVO(user);

    UserWithCsrfVO response = new UserWithCsrfVO();
    response.setUser(userVO);
    response.setCsrfToken(csrfToken);

    return Result.success(response);
}
```

- [ ] **Step 2: 创建 UserWithCsrfVO DTO**

Create: `backend-spring/src/main/java/com/ulticode/modules/auth/dto/UserWithCsrfVO.java`

```java
package com.ulticode.modules.auth.dto;

import com.ulticode.modules.user.dto.UserVO;
import lombok.Data;

@Data
public class UserWithCsrfVO {
    private UserVO user;
    private String csrfToken;
}
```

- [ ] **Step 3: 暂时创建 CsrfService Stub**

Create: `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfService.java`

```java
package com.ulticode.security.csrf;

import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Service;

/**
 * CSRF Token 服务（临时实现，Phase 2 完善）
 */
@Service
public class CsrfService {

    /**
     * 生成 CSRF Token（临时实现）
     */
    public String generateToken(String userId) {
        // Phase 2 将使用 Redis 存储
        return IdUtil.simpleUUID();
    }

    /**
     * 验证 CSRF Token（临时实现）
     */
    public boolean validateToken(String userId, String token) {
        // Phase 2 将实现完整验证
        return token != null && !token.isEmpty();
    }
}
```

- [ ] **Step 4: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/controller/AuthController.java src/main/java/com/ulticode/modules/auth/dto/UserWithCsrfVO.java src/main/java/com/ulticode/security/csrf/CsrfService.java
git commit -m "feat: add /auth/me endpoint with CSRF token"
```

---

## Task 1.6: 更新 AuthServiceImpl 使用新的 Cookie 配置

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/modules/auth/service/impl/AuthServiceImpl.java`

- [ ] **Step 1: 读取 AuthServiceImpl.java**

Run: 查看 `backend-spring/src/main/java/com/ulticode/modules/auth/service/impl/AuthServiceImpl.java`

- [ ] **Step 2: 更新 Cookie 设置方法**

使用 JwtProperties 中的新配置：

```java
private void setAuthCookie(HttpServletResponse response, String accessToken) {
    JwtProperties.AccessTokenCookie cookieConfig = jwtProperties.getCookie().getAccessToken();

    String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
            cookieConfig.getName(),
            accessToken,
            cookieConfig.getPath(),
            cookieConfig.getMaxAge(),
            cookieConfig.isSecure() ? "; Secure" : "",
            cookieConfig.getSameSite()
    );
    response.addHeader("Set-Cookie", headerValue);
}

private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
    JwtProperties.RefreshTokenCookie cookieConfig = jwtProperties.getCookie().getRefreshToken();

    String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
            cookieConfig.getName(),
            refreshToken,
            cookieConfig.getPath(),
            cookieConfig.getMaxAge(),
            cookieConfig.isSecure() ? "; Secure" : "",
            cookieConfig.getSameSite()
    );
    response.addHeader("Set-Cookie", headerValue);
}

private void clearAuthCookies(HttpServletResponse response) {
    JwtProperties.CookieConfig cookieConfig = jwtProperties.getCookie();

    // 清除 access_token
    String accessHeader = String.format("%s=; Path=%s; Max-Age=0; HttpOnly%s; SameSite=%s",
            cookieConfig.getAccessToken().getName(),
            cookieConfig.getAccessToken().getPath(),
            cookieConfig.getAccessToken().isSecure() ? "; Secure" : "",
            cookieConfig.getAccessToken().getSameSite()
    );
    response.addHeader("Set-Cookie", accessHeader);

    // 清除 refresh_token
    String refreshHeader = String.format("%s=; Path=%s; Max-Age=0; HttpOnly%s; SameSite=%s",
            cookieConfig.getRefreshToken().getName(),
            cookieConfig.getRefreshToken().getPath(),
            cookieConfig.getRefreshToken().isSecure() ? "; Secure" : "",
            cookieConfig.getRefreshToken().getSameSite()
    );
    response.addHeader("Set-Cookie", refreshHeader);
}
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/service/impl/AuthServiceImpl.java
git commit -m "fix: update cookie handling to use new JWT properties config"
```

---

## Task 1.7: 添加数据库迁移脚本

**Files:**
- Create: `init-db/migrations/V009__add_user_soft_delete.sql`

- [ ] **Step 1: 创建迁移脚本**

```sql
-- 添加用户表逻辑删除字段
ALTER TABLE users
ADD COLUMN IF NOT EXISTS is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL COMMENT '删除时间',
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(40) NULL COMMENT '删除人ID';

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_users_is_deleted ON users(is_deleted);
```

- [ ] **Step 2: 提交修改**

```bash
git add init-db/migrations/V009__add_user_soft_delete.sql
git commit -m "db: add soft delete columns to users table"
```

---

## Task 1.8: Phase 1 验证测试

- [ ] **Step 1: 编译项目**

```bash
cd backend-spring && ./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行现有测试**

```bash
cd backend-spring && ./mvnw test
```

Expected: Tests run successfully

- [ ] **Step 3: 启动服务**

```bash
cd backend-spring && ./mvnw spring-boot:run
```

- [ ] **Step 4: 测试登录端点**

```bash
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}' \
  -c cookies.txt -v
```

Expected: 200 OK, Set-Cookie header contains `access_token` and `refresh_token`

- [ ] **Step 5: 测试 /auth/me 端点**

```bash
curl -X GET http://localhost:9001/auth/me \
  -b cookies.txt -v
```

Expected: 200 OK, 返回用户信息和 csrfToken

- [ ] **Step 6: 提交 Phase 1 完成**

```bash
git tag phase1-core-fix-complete
git push origin phase1-core-fix-complete
```

---

# Phase 2: 认证增强 (P1)

**目标:** 完善认证安全机制

---

## Task 2.1: 完善 CsrfService 实现

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfService.java`

- [ ] **Step 1: 完善 CsrfService 使用 Redis**

```java
package com.ulticode.security.csrf;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CsrfService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String CSRF_PREFIX = "csrf:";
    private static final Duration CSRF_TTL = Duration.ofMinutes(15);

    /**
     * 生成 CSRF Token
     */
    public String generateToken(String userId) {
        String tokenId = IdUtil.fastSimpleUUID();
        String token = IdUtil.simpleUUID();
        String key = CSRF_PREFIX + userId + ":" + tokenId;

        redisTemplate.opsForValue().set(key, token, CSRF_TTL);

        // 记录用户的 token 集合
        redisTemplate.opsForSet().add(CSRF_PREFIX + "user:" + userId, tokenId);
        redisTemplate.expire(CSRF_PREFIX + "user:" + userId, Duration.ofDays(7));

        return token;
    }

    /**
     * 验证 CSRF Token
     */
    public boolean validateToken(String userId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String userKey = CSRF_PREFIX + "user:" + userId;
        Set<String> tokenIds = redisTemplate.opsForSet().members(userKey);

        if (tokenIds == null || tokenIds.isEmpty()) {
            return false;
        }

        for (String tokenId : tokenIds) {
            String key = CSRF_PREFIX + userId + ":" + tokenId;
            String storedToken = redisTemplate.opsForValue().get(key);
            if (token.equals(storedToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 用户登出时清理所有 CSRF Token
     */
    public void clearUserTokens(String userId) {
        String userKey = CSRF_PREFIX + "user:" + userId;
        Set<String> tokenIds = redisTemplate.opsForSet().members(userKey);

        if (tokenIds != null) {
            for (String tokenId : tokenIds) {
                redisTemplate.delete(CSRF_PREFIX + userId + ":" + tokenId);
            }
        }
        redisTemplate.delete(userKey);
    }
}
```

- [ ] **Step 2: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/security/csrf/CsrfService.java
git commit -m "feat: implement CSRF token service with Redis storage"
```

---

## Task 2.2: 创建 CsrfInterceptor

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfInterceptor.java`
- Modify: `backend-spring/src/main/java/com/ulticode/common/config/WebMvcConfig.java`

- [ ] **Step 1: 创建 CsrfInterceptor**

```java
package com.ulticode.security.csrf;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class CsrfInterceptor implements HandlerInterceptor {

    private final CsrfService csrfService;
    private final JwtTokenProvider jwtTokenProvider;

    private static final Set<String> CSRF_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> EXEMPT_PATHS = Set.of(
        "/auth/login", "/auth/register", "/auth/refresh", "/auth/logout",
        "/auth/forgot-password", "/auth/reset-password"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!CSRF_METHODS.contains(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (EXEMPT_PATHS.contains(path)) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true; // 未认证请求由 Security 处理
        }

        String csrfToken = request.getHeader("X-CSRF-Token");
        if (csrfToken == null || csrfToken.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "CSRF token is missing");
        }

        String userId = authentication.getName();
        if (!csrfService.validateToken(userId, csrfToken)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Invalid CSRF token");
        }

        return true;
    }
}
```

- [ ] **Step 2: 创建 WebMvcConfig 注册拦截器**

```java
package com.ulticode.common.config;

import com.ulticode.security.csrf.CsrfInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrfInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrfInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/auth/login",
                "/auth/register",
                "/auth/refresh",
                "/auth/logout",
                "/auth/forgot-password",
                "/auth/reset-password",
                "/auth/github/**",
                "/auth/google/**",
                "/swagger-ui/**",
                "/api-docs/**",
                "/actuator/**"
            );
    }
}
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/security/csrf/CsrfInterceptor.java src/main/java/com/ulticode/common/config/WebMvcConfig.java
git commit -m "feat: add CSRF interceptor for state-changing requests"
```

---

## Task 2.3: 创建 RefreshToken 实体和服务

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/modules/refreshtoken/entity/RefreshToken.java`
- Create: `backend-spring/src/main/java/com/ulticode/modules/refreshtoken/mapper/RefreshTokenMapper.java`
- Create: `backend-spring/src/main/java/com/ulticode/modules/refreshtoken/service/RefreshTokenService.java`

- [ ] **Step 1: 创建 RefreshToken 实体**

```java
package com.ulticode.modules.refreshtoken.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("refresh_tokens")
public class RefreshToken {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;

    private String token;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime rotatedAt;

    private Boolean isRevoked;
}
```

- [ ] **Step 2: 创建 RefreshTokenMapper**

```java
package com.ulticode.modules.refreshtoken.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.refreshtoken.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
```

- [ ] **Step 3: 创建 RefreshTokenService**

```java
package com.ulticode.modules.refreshtoken.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.refreshtoken.entity.RefreshToken;
import com.ulticode.modules.refreshtoken.mapper.RefreshTokenMapper;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.DigestUtils;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * 创建 Refresh Token
     */
    public String createToken(String userId, HttpServletResponse response) {
        String tokenId = IdUtil.fastSimpleUUID();
        String token = jwtTokenProvider.generateRefreshToken(userId);
        String tokenHash = DigestUtils.sha256Hex(token);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(tokenId);
        refreshToken.setUserId(userId);
        refreshToken.setToken(token);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setCreatedAt(LocalDateTime.now());
        refreshToken.setIsRevoked(false);

        refreshTokenMapper.insert(refreshToken);

        return token;
    }

    /**
     * 验证并轮换 Refresh Token
     */
    public String validateAndRotate(String token, HttpServletResponse response) {
        String tokenHash = DigestUtils.sha256Hex(token);

        RefreshToken storedToken = refreshTokenMapper.selectOne(
            new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash)
                .eq(RefreshToken::getIsRevoked, false)
        );

        if (storedToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token has expired");
        }

        // 撤销旧 Token
        revokeToken(storedToken.getId());

        // 生成新 Token
        return createToken(storedToken.getUserId(), response);
    }

    /**
     * 撤销单个 Token
     */
    public void revokeToken(String tokenId) {
        refreshTokenMapper.update(null,
            new LambdaUpdateWrapper<RefreshToken>()
                .set(RefreshToken::getIsRevoked, true)
                .set(RefreshToken::getRotatedAt, LocalDateTime.now())
                .eq(RefreshToken::getId, tokenId)
        );
    }

    /**
     * 撤销用户所有 Token
     */
    public void revokeAllUserTokens(String userId) {
        refreshTokenMapper.update(null,
            new LambdaUpdateWrapper<RefreshToken>()
                .set(RefreshToken::getIsRevoked, true)
                .set(RefreshToken::getRotatedAt, LocalDateTime.now())
                .eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getIsRevoked, false)
        );
    }
}
```

- [ ] **Step 4: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/refreshtoken/
git commit -m "feat: add RefreshToken entity and service with token rotation"
```

---

## Task 2.4: 添加数据库迁移脚本

**Files:**
- Create: `init-db/migrations/V010__add_refresh_token_hash.sql`

- [ ] **Step 1: 创建迁移脚本**

```sql
-- 添加 token_hash 列到 refresh_tokens 表
ALTER TABLE refresh_tokens
ADD COLUMN IF NOT EXISTS token_hash VARCHAR(255) NULL COMMENT 'Token哈希值' AFTER token;

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
```

- [ ] **Step 2: 提交修改**

```bash
git add init-db/migrations/V010__add_refresh_token_hash.sql
git commit -m "db: add token_hash column to refresh_tokens table"
```

---

## Task 2.5: 添加忘记密码端点

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/modules/auth/dto/ForgotPasswordDTO.java`
- Create: `backend-spring/src/main/java/com/ulticode/modules/auth/dto/ResetPasswordDTO.java`
- Create: `backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java`
- Modify: `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`
- Modify: `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java`

- [ ] **Step 1: 创建 ForgotPasswordDTO**

```java
package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

- [ ] **Step 2: 创建 ResetPasswordDTO**

```java
package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDTO {
    @NotBlank(message = "Token不能为空")
    private String token;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String newPassword;
}
```

- [ ] **Step 3: 创建 PasswordResetService**

```java
package com.ulticode.modules.auth.service;

import cn.hutool.core.util.IdUtil;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.frontend-url:http://localhost:9002}")
    private String frontendUrl;

    private static final String RESET_PREFIX = "password-reset:";
    private static final Duration RESET_TTL = Duration.ofHours(1);

    /**
     * 忘记密码 - 发送重置邮件
     */
    public void forgotPassword(String email) {
        User user = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        );

        if (user == null) {
            // 不透露用户是否存在
            return;
        }

        String token = IdUtil.simpleUUID();
        String key = RESET_PREFIX + token;

        redisTemplate.opsForValue().set(key, user.getId(), RESET_TTL);

        // TODO: 发送邮件 (Phase 3 完整实现)
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        // emailService.sendPasswordResetEmail(email, resetUrl);
        System.out.println("Password reset URL: " + resetUrl); // 临时日志
    }

    /**
     * 重置密码
     */
    public void resetPassword(String token, String newPassword) {
        String key = RESET_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid or expired reset token");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "User not found");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 删除重置 Token
        redisTemplate.delete(key);

        // 撤销用户所有会话
        refreshTokenService.revokeAllUserTokens(userId);
    }
}
```

- [ ] **Step 4: 添加端点到 AuthController**

```java
@Operation(summary = "Forgot password", description = "Send password reset email")
@PostMapping("/forgot-password")
public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordDTO dto) {
    passwordResetService.forgotPassword(dto.getEmail());
    return Result.success();
}

@Operation(summary = "Reset password", description = "Reset password using token from email")
@PostMapping("/reset-password")
public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
    passwordResetService.resetPassword(dto.getToken(), dto.getNewPassword());
    return Result.success();
}
```

- [ ] **Step 5: 更新 SecurityConfig 添加公开端点**

```java
private static final String[] PUBLIC_ENDPOINTS = {
    "/auth/login",
    "/auth/register",
    "/auth/refresh",
    "/auth/forgot-password",    // 新增
    "/auth/reset-password",     // 新增
    "/auth/github",
    "/auth/github/callback",
    "/auth/google",
    "/auth/google/callback",
    "/problems",
    "/problems/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/api-docs/**",
    "/v3/api-docs/**",
    "/ws/**",
    "/actuator/health"
};
```

- [ ] **Step 6: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/dto/ForgotPasswordDTO.java src/main/java/com/ulticode/modules/auth/dto/ResetPasswordDTO.java src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java src/main/java/com/ulticode/modules/auth/controller/AuthController.java src/main/java/com/ulticode/common/config/SecurityConfig.java
git commit -m "feat: add forgot-password and reset-password endpoints"
```

---

## Task 2.6: Phase 2 验证测试

- [ ] **Step 1: 编译项目**

```bash
cd backend-spring && ./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行测试**

```bash
cd backend-spring && ./mvnw test
```

Expected: Tests pass

- [ ] **Step 3: 启动服务并测试**

```bash
cd backend-spring && ./mvnw spring-boot:run
```

- [ ] **Step 4: 测试 CSRF 验证**

```bash
# 登录获取 Cookie
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}' \
  -c cookies.txt

# 不带 CSRF Token 的 POST 请求应该失败
curl -X POST http://localhost:9001/some-protected-endpoint \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{}'
```

Expected: 403 Forbidden (CSRF token missing)

- [ ] **Step 5: 测试忘记密码**

```bash
curl -X POST http://localhost:9001/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'
```

Expected: 200 OK

- [ ] **Step 6: 提交 Phase 2 完成**

```bash
git tag phase2-auth-enhancement-complete
git push origin phase2-auth-enhancement-complete
```

---

# Phase 3: OAuth 集成 (P2)

**目标:** 完整实现第三方登录

---

## Task 3.1: 创建 OAuth 配置

**Files:**
- Modify: `backend-spring/src/main/resources/application.yml`

- [ ] **Step 1: 添加 OAuth 配置**

```yaml
# 前端 URL（用于重定向）
app:
  frontend-url: ${FRONTEND_URL:http://localhost:9002}

# OAuth Configuration
oauth:
  github:
    client-id: ${GITHUB_CLIENT_ID:}
    client-secret: ${GITHUB_CLIENT_SECRET:}
    redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:9001/auth/github/callback}
    authorize-url: https://github.com/login/oauth/authorize
    token-url: https://github.com/login/oauth/access_token
    user-url: https://api.github.com/user
    scopes: user:email

  google:
    client-id: ${GOOGLE_CLIENT_ID:}
    client-secret: ${GOOGLE_CLIENT_SECRET:}
    redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:9001/auth/google/callback}
    authorize-url: https://accounts.google.com/o/oauth2/v2/auth
    token-url: https://oauth2.googleapis.com/token
    user-url: https://www.googleapis.com/oauth2/v2/userinfo
    scopes: email,profile
```

- [ ] **Step 2: 创建 OAuthProperties**

Create: `backend-spring/src/main/java/com/ulticode/security/oauth/OAuthProperties.java`

```java
package com.ulticode.security.oauth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private OAuthProvider github = new OAuthProvider();
    private OAuthProvider google = new OAuthProvider();

    @Data
    public static class OAuthProvider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String authorizeUrl;
        private String tokenUrl;
        private String userUrl;
        private String scopes;
    }
}
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/resources/application.yml src/main/java/com/ulticode/security/oauth/OAuthProperties.java
git commit -m "feat: add OAuth configuration for GitHub and Google"
```

---

## Task 3.2: 创建 OAuthService

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/modules/auth/service/OAuthService.java`

- [ ] **Step 1: 创建 OAuthService**

```java
package com.ulticode.modules.auth.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.service.RefreshTokenService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtTokenProvider;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.oauth.OAuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oauthProperties;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final CsrfService csrfService;
    private final ObjectMapper objectMapper;

    // ==================== GitHub OAuth ====================

    public String getGithubAuthUrl() {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();
        String state = IdUtil.simpleUUID();

        return UriComponentsBuilder.fromHttpUrl(github.getAuthorizeUrl())
            .queryParam("client_id", github.getClientId())
            .queryParam("redirect_uri", github.getRedirectUri())
            .queryParam("scope", github.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    public LoginResponse handleGithubCallback(String code, HttpServletResponse response) {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();

        // 获取 access token
        String tokenResponse = HttpUtil.createPost(github.getTokenUrl())
            .header("Accept", "application/json")
            .body("client_id=" + github.getClientId() +
                  "&client_secret=" + github.getClientSecret() +
                  "&code=" + code +
                  "&redirect_uri=" + URLEncoder.encode(github.getRedirectUri(), StandardCharsets.UTF_8))
            .execute()
            .body();

        String accessToken;
        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            accessToken = tokenNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("Failed to parse GitHub token response", e);
            throw new RuntimeException("GitHub OAuth failed");
        }

        // 获取用户信息
        String userResponse = HttpUtil.createGet(github.getUserUrl())
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .execute()
            .body();

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String githubId = userNode.get("id").asText();
            String login = userNode.get("login").asText();
            String email = userNode.has("email") && !userNode.get("email").isNull()
                ? userNode.get("email").asText()
                : login + "@github";
            String avatar = userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null;

            return createOrUpdateUser(githubId, login, email, avatar, "github", response);
        } catch (Exception e) {
            log.error("Failed to parse GitHub user response", e);
            throw new RuntimeException("Failed to get GitHub user info");
        }
    }

    // ==================== Google OAuth ====================

    public String getGoogleAuthUrl() {
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();
        String state = IdUtil.simpleUUID();

        return UriComponentsBuilder.fromHttpUrl(google.getAuthorizeUrl())
            .queryParam("client_id", google.getClientId())
            .queryParam("redirect_uri", google.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", google.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    public LoginResponse handleGoogleCallback(String code, HttpServletResponse response) {
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();

        // 获取 access token
        String tokenResponse = HttpUtil.createPost(google.getTokenUrl())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("client_id=" + google.getClientId() +
                  "&client_secret=" + google.getClientSecret() +
                  "&code=" + code +
                  "&redirect_uri=" + URLEncoder.encode(google.getRedirectUri(), StandardCharsets.UTF_8) +
                  "&grant_type=authorization_code")
            .execute()
            .body();

        String accessToken;
        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            accessToken = tokenNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("Failed to parse Google token response", e);
            throw new RuntimeException("Google OAuth failed");
        }

        // 获取用户信息
        String userResponse = HttpUtil.createGet(google.getUserUrl())
            .header("Authorization", "Bearer " + accessToken)
            .execute()
            .body();

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String googleId = userNode.get("id").asText();
            String email = userNode.get("email").asText();
            String name = userNode.has("name") ? userNode.get("name").asText() : email.split("@")[0];
            String avatar = userNode.has("picture") ? userNode.get("picture").asText() : null;

            return createOrUpdateUser(googleId, name, email, avatar, "google", response);
        } catch (Exception e) {
            log.error("Failed to parse Google user response", e);
            throw new RuntimeException("Failed to get Google user info");
        }
    }

    // ==================== 用户创建/更新 ====================

    private LoginResponse createOrUpdateUser(String oauthId, String name, String email,
                                              String avatar, String provider, HttpServletResponse response) {
        // 查找现有用户（通过 email）
        User user = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        );

        if (user == null) {
            // 创建新用户
            user = new User();
            user.setId(IdUtil.fastSimpleUUID());
            user.setUsername(provider + "_" + oauthId);
            user.setName(name);
            user.setEmail(email);
            user.setAvatar(avatar);
            user.setRole("USER");
            user.setIsActive(true);
            user.setIsBanned(false);
            user.setJoinedAt(LocalDateTime.now());
            userMapper.insert(user);
        } else {
            // 更新头像（如果需要）
            if (avatar != null && !avatar.equals(user.getAvatar())) {
                user.setAvatar(avatar);
                userMapper.updateById(user);
            }
        }

        // 生成 JWT
        String jwtToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = refreshTokenService.createToken(user.getId(), response);

        // 设置 Cookie
        setAuthCookie(response, jwtToken);
        setRefreshTokenCookie(response, refreshToken);

        // 生成 CSRF Token
        String csrfToken = csrfService.generateToken(user.getId());

        return LoginResponse.builder()
            .csrfToken(csrfToken)
            .user(toUserVO(user))
            .build();
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        JwtProperties.AccessTokenCookie config = jwtProperties.getCookie().getAccessToken();
        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
            config.getName(), token, config.getPath(), config.getMaxAge(),
            config.isSecure() ? "; Secure" : "", config.getSameSite());
        response.addHeader("Set-Cookie", headerValue);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        JwtProperties.RefreshTokenCookie config = jwtProperties.getCookie().getRefreshToken();
        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
            config.getName(), token, config.getPath(), config.getMaxAge(),
            config.isSecure() ? "; Secure" : "", config.getSameSite());
        response.addHeader("Set-Cookie", headerValue);
    }

    private Object toUserVO(User user) {
        // 简化实现，实际应使用 UserVO
        return user;
    }
}
```

- [ ] **Step 2: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/service/OAuthService.java
git commit -m "feat: implement OAuth service for GitHub and Google"
```

---

## Task 3.3: 添加 OAuth 端点

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`

- [ ] **Step 1: 添加 OAuth 端点**

```java
@Value("${app.frontend-url:http://localhost:9002}")
private String frontendUrl;

@GetMapping("/github")
public void githubLogin(HttpServletResponse response) throws IOException {
    String authUrl = oauthService.getGithubAuthUrl();
    response.sendRedirect(authUrl);
}

@GetMapping("/github/callback")
public void githubCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
    oauthService.handleGithubCallback(code, response);
    response.sendRedirect(frontendUrl + "/?oauth=success");
}

@GetMapping("/google")
public void googleLogin(HttpServletResponse response) throws IOException {
    String authUrl = oauthService.getGoogleAuthUrl();
    response.sendRedirect(authUrl);
}

@GetMapping("/google/callback")
public void googleCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
    oauthService.handleGoogleCallback(code, response);
    response.sendRedirect(frontendUrl + "/?oauth=success");
}
```

- [ ] **Step 2: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/controller/AuthController.java
git commit -m "feat: add OAuth endpoints for GitHub and Google login"
```

---

## Task 3.4: 更新 .env.example

**Files:**
- Modify: `backend-spring/.env.example`

- [ ] **Step 1: 添加 OAuth 环境变量**

```bash
# 前端 URL
FRONTEND_URL=http://localhost:9002

# OAuth - GitHub
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
GITHUB_REDIRECT_URI=http://localhost:9001/auth/github/callback

# OAuth - Google
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
GOOGLE_REDIRECT_URI=http://localhost:9001/auth/google/callback

# Email (用于密码重置)
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USER=your_smtp_user
SMTP_PASSWORD=your_smtp_password
EMAIL_ENABLED=false
```

- [ ] **Step 2: 提交修改**

```bash
cd backend-spring && git add .env.example
git commit -m "docs: add OAuth environment variables to .env.example"
```

---

## Task 3.5: Phase 3 验证测试

- [ ] **Step 1: 编译项目**

```bash
cd backend-spring && ./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 测试 OAuth 端点**

```bash
# 测试 GitHub OAuth 重定向
curl -v http://localhost:9001/auth/github
```

Expected: 302 Redirect to GitHub

- [ ] **Step 3: 提交 Phase 3 完成**

```bash
git tag phase3-oauth-complete
git push origin phase3-oauth-complete
```

---

# Phase 4: 完善功能 (P3)

**目标:** 安全性和权限系统

---

## Task 4.1: 创建限流注解

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/common/annotation/RateLimit.java`

- [ ] **Step 1: 创建 RateLimit 注解**

```java
package com.ulticode.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /**
     * 限流 key 前缀
     */
    String key() default "";

    /**
     * 限流次数
     */
    int limit() default 100;

    /**
     * 限流时间窗口（秒）
     */
    int period() default 60;
}
```

- [ ] **Step 2: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/common/annotation/RateLimit.java
git commit -m "feat: add RateLimit annotation"
```

---

## Task 4.2: 创建限流切面

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/common/aspect/RateLimitAspect.java`

- [ ] **Step 1: 创建 RateLimitAspect**

```java
package com.ulticode.common.aspect;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, String> redisTemplate;

    @Around("@annotation(com.ulticode.common.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = generateKey(rateLimit, joinPoint);
        String redisKey = "rate-limit:" + key;

        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, rateLimit.period(), TimeUnit.SECONDS);
        }

        if (count != null && count > rateLimit.limit()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Please try again later.");
        }

        return joinPoint.proceed();
    }

    private String generateKey(RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        String key = rateLimit.key();

        if (key.isEmpty()) {
            // 使用类名 + 方法名 + IP 作为默认 key
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();
            String ip = getClientIp();
            key = className + ":" + methodName + ":" + ip;
        }

        return key;
    }

    private String getClientIp() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
```

- [ ] **Step 2: 添加 TOO_MANY_REQUESTS 错误码**

在 ErrorCode 中添加：

```java
TOO_MANY_REQUESTS(42900, "请求过于频繁");
```

- [ ] **Step 3: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/common/aspect/RateLimitAspect.java src/main/java/com/ulticode/common/exception/ErrorCode.java
git commit -m "feat: implement rate limiting aspect with Redis"
```

---

## Task 4.3: 创建权限服务

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/modules/permission/entity/UserPermission.java`
- Create: `backend-spring/src/main/java/com/ulticode/modules/permission/mapper/UserPermissionMapper.java`
- Create: `backend-spring/src/main/java/com/ulticode/modules/permission/service/PermissionService.java`

- [ ] **Step 1: 创建 UserPermission 实体**

```java
package com.ulticode.modules.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_permissions")
public class UserPermission {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;

    private String resource;

    private String action;

    private LocalDateTime grantedAt;
}
```

- [ ] **Step 2: 创建 UserPermissionMapper**

```java
package com.ulticode.modules.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.permission.entity.UserPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPermissionMapper extends BaseMapper<UserPermission> {
}
```

- [ ] **Step 3: 创建 PermissionService**

```java
package com.ulticode.modules.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserPermissionMapper userPermissionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PERM_CACHE_PREFIX = "user:perms:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 获取用户所有权限
     */
    public List<UserPermission> getUserPermissions(String userId) {
        return userPermissionMapper.selectList(
            new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, userId)
        );
    }

    /**
     * 获取用户权限列表（格式：action:resource）
     */
    public List<String> getUserPermissionStrings(String userId) {
        List<UserPermission> permissions = getUserPermissions(userId);
        return permissions.stream()
            .map(p -> p.getAction() + ":" + p.getResource())
            .collect(Collectors.toList());
    }

    /**
     * 检查用户是否有特定权限
     */
    public boolean hasPermission(String userId, String action, String resource) {
        List<UserPermission> permissions = getUserPermissions(userId);
        return permissions.stream()
            .anyMatch(p ->
                (p.getAction().equals("*") || p.getAction().equals(action)) &&
                (p.getResource().equals("*") || p.getResource().equals(resource))
            );
    }

    /**
     * 清除用户权限缓存
     */
    public void invalidateCache(String userId) {
        redisTemplate.delete(PERM_CACHE_PREFIX + userId);
    }
}
```

- [ ] **Step 4: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/permission/
git commit -m "feat: add permission service and entities"
```

---

## Task 4.4: 添加 /auth/permissions 端点

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`

- [ ] **Step 1: 添加权限端点**

```java
@GetMapping("/permissions")
@Operation(summary = "Get user permissions", description = "Get all permissions for the authenticated user")
public Result<List<String>> getPermissions(@AuthenticationPrincipal User user) {
    List<String> permissions = permissionService.getUserPermissionStrings(user.getId());
    return Result.success(permissions);
}
```

- [ ] **Step 2: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/controller/AuthController.java
git commit -m "feat: add /auth/permissions endpoint"
```

---

## Task 4.5: 添加密码重置限流

**Files:**
- Modify: `backend-spring/src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java`

- [ ] **Step 1: 添加限流注解**

```java
@RateLimit(key = "'forgot-password:' + #email", limit = 3, period = 3600)
public void forgotPassword(String email) {
    // ... 现有实现
}
```

- [ ] **Step 2: 提交修改**

```bash
cd backend-spring && git add src/main/java/com/ulticode/modules/auth/service/PasswordResetService.java
git commit -m "feat: add rate limiting to forgot-password endpoint"
```

---

## Task 4.6: Phase 4 验证测试

- [ ] **Step 1: 编译项目**

```bash
cd backend-spring && ./mvnw compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行所有测试**

```bash
cd backend-spring && ./mvnw test
```

Expected: All tests pass

- [ ] **Step 3: 测试限流功能**

```bash
# 连续请求 4 次（超过 3 次限制）
for i in {1..4}; do
  curl -X POST http://localhost:9001/auth/forgot-password \
    -H "Content-Type: application/json" \
    -d '{"email":"test@example.com"}'
  echo ""
done
```

Expected: 第 4 次返回 429 Too Many Requests

- [ ] **Step 4: 测试权限端点**

```bash
curl -X GET http://localhost:9001/auth/permissions \
  -b cookies.txt
```

Expected: 200 OK, 返回权限列表

- [ ] **Step 5: 提交 Phase 4 完成**

```bash
git tag phase4-complete-features-complete
git push origin phase4-complete-features-complete
```

---

# 最终验证

## Task 5.1: 完整功能验证

- [ ] **Step 1: 完整登录流程测试**

```bash
# 1. 注册
curl -X POST http://localhost:9001/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test123","email":"test@example.com"}' \
  -c cookies.txt

# 2. 获取当前用户
curl -X GET http://localhost:9001/auth/me -b cookies.txt

# 3. 获取权限
curl -X GET http://localhost:9001/auth/permissions -b cookies.txt

# 4. 登出
curl -X POST http://localhost:9001/auth/logout -b cookies.txt
```

- [ ] **Step 2: 构建生产包**

```bash
cd backend-spring && ./mvnw clean package -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 创建最终标签**

```bash
git tag migration-complete
git push origin migration-complete
```

---

## 文件清单汇总

| Phase | 新增文件 | 修改文件 |
|-------|---------|---------|
| 1 | 1 (CsrfService.java) | 4 |
| 2 | 6 | 3 |
| 3 | 2 | 2 |
| 4 | 4 | 2 |
| **Total** | **13** | **11** |

---

## 注意事项

1. **Cookie Secure 属性**: 生产环境必须设置 `JWT_COOKIE_SECURE=true`
2. **OAuth 凭证**: 需要在 GitHub/Google 开发者控制台配置
3. **Redis 连接**: 确保 Redis 服务可用
4. **数据库迁移**: 按顺序执行 V009, V010 迁移脚本
5. **测试覆盖**: 每个 Phase 完成后进行验证测试
