# NestJS → Spring Boot 后端迁移修复设计文档

**日期**: 2026-03-22
**状态**: 待审核
**作者**: Claude Code

---

## 1. 概述

### 1.1 背景

将 NestJS 后端 (`backend/`) 迁移到 Spring Boot (`backend-spring/`) 后，存在多个功能差异和配置问题导致认证系统无法正常工作。

### 1.2 目标

- 确保功能对等：Spring Boot 后端与 NestJS 后端功能完全一致
- 保持前端兼容：Cookie 命名、API 响应格式与前端兼容
- 增强安全性：添加 CSRF 验证、Token 数据库存储、限流机制

### 1.3 修复策略

采用**渐进式分阶段修复**，共 4 个阶段：

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| Phase 1 | 核心：User 实体修复 + Cookie 配置 + `/auth/me` 端点 | P0 |
| Phase 2 | 认证增强：CSRF + Refresh Token 数据库存储 + 忘记密码 | P1 |
| Phase 3 | OAuth：GitHub/Google 完整实现 + 数据库迁移 | P2 |
| Phase 4 | 完善：Rate Limiting + 权限系统 + 测试覆盖 | P3 |

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                          │
│                    console:9002 / management:9003                │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP/WS
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend                           │
│                         :9001                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Security   │  │    Auth      │  │   Modules    │          │
│  │   JWT/CSRF   │  │   OAuth2     │  │   Business   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────┬───────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│    MySQL      │     │    Redis      │     │  MeiliSearch  │
│   :23306      │     │   :26379      │     │   (optional)  │
└───────────────┘     └───────────────┘     └───────────────┘
```

---

## 3. 数据库 Schema 修改

### 3.1 `users` 表修改

```sql
-- 添加逻辑删除字段
ALTER TABLE users
ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
ADD COLUMN deleted_at DATETIME NULL COMMENT '删除时间',
ADD COLUMN deleted_by VARCHAR(40) NULL COMMENT '删除人ID';

-- 添加索引优化查询
CREATE INDEX idx_users_is_deleted ON users(is_deleted);
```

### 3.2 `refresh_tokens` 表（新增）

```sql
CREATE TABLE refresh_tokens (
    id VARCHAR(40) PRIMARY KEY,
    user_id VARCHAR(40) NOT NULL,
    token_hash VARCHAR(255) NOT NULL COMMENT 'Token哈希值',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    revoked TINYINT(1) DEFAULT 0 COMMENT '是否已撤销',
    revoked_at DATETIME NULL COMMENT '撤销时间',

    INDEX idx_user_id (user_id),
    INDEX idx_token_hash (token_hash),
    INDEX idx_expires_at (expires_at),

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 3.3 `csrf_tokens` 表（新增）

```sql
CREATE TABLE csrf_tokens (
    id VARCHAR(40) PRIMARY KEY,
    user_id VARCHAR(40) NOT NULL,
    token VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,

    INDEX idx_user_id (user_id),
    INDEX idx_token (token),

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

---

## 4. User 实体重构

### 4.1 字段映射修正

**问题**: 当前 `User.java` 中 `createdAt` 和 `updatedAt` 字段映射错误。

```java
// 当前错误映射
@TableField(value = "joined_at", fill = FieldFill.INSERT)
private LocalDateTime createdAt;  // ❌ 错误

@TableField(value = "last_login_at")
private LocalDateTime updatedAt;   // ❌ 错误
```

**修复后**:

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
    private String preferredLanguage;
    private String role;
    private Boolean isActive;
    private Boolean isBanned;
    private LocalDateTime bannedUntil;
    private String bannedReason;
    private String createdBy;
    private String updatedBy;

    // 修正：正确映射数据库字段
    @TableField("joined_at")
    private LocalDateTime joinedAt;        // 注册时间

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;     // 最后登录时间

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

### 4.2 MyBatis-Plus 配置修正

```yaml
# application.yml
mybatis-plus:
  global-config:
    db-config:
      id-type: input          # 改为 input（UUID）
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

---

## 5. 认证系统重构

### 5.1 Cookie 配置（保持 NestJS 命名）

```yaml
# application.yml
jwt:
  cookie:
    access-token:
      name: access_token
      http-only: true
      secure: ${JWT_COOKIE_SECURE:true}
      same-site: strict
      path: /
      max-age: 900          # 15分钟
    refresh-token:
      name: refresh_token
      http-only: true
      secure: ${JWT_COOKIE_SECURE:true}
      same-site: strict
      path: /
      max-age: 604800       # 7天
```

### 5.2 API 端点对齐

| 端点 | NestJS | Spring Boot 状态 | 修复 |
|------|--------|-----------------|------|
| `POST /auth/login` | ✅ | ✅ 已有 | 对齐响应格式 |
| `POST /auth/register` | ✅ | ✅ 已有 | 对齐响应格式 |
| `POST /auth/logout` | ✅ | ✅ 已有 | - |
| `POST /auth/refresh` | ✅ | ✅ 已有 | 数据库存储 |
| `GET /auth/me` | ✅ | ❌ 缺失 | **新增** |
| `GET /auth/permissions` | ✅ | ❌ 缺失 | **新增** |
| `POST /auth/forgot-password` | ✅ | ❌ 缺失 | **新增** |
| `POST /auth/reset-password` | ✅ | ❌ 缺失 | **新增** |
| `GET /auth/github` | ✅ | ⚠️ 仅声明 | **完整实现** |
| `GET /auth/github/callback` | ✅ | ❌ 缺失 | **新增** |
| `GET /auth/google` | ✅ | ⚠️ 仅声明 | **完整实现** |
| `GET /auth/google/callback` | ✅ | ❌ 缺失 | **新增** |

### 5.3 登录响应格式对齐

```java
// LoginResponse.java
@Data
@Builder
public class LoginResponse {
    private String csrfToken;
    private UserVO user;
    // 移除 accessToken（通过 cookie 传递）
}
```

---

## 6. CSRF Token 服务

### 6.1 Redis 存储结构

```
Key: csrf:{userId}:{tokenId}
Value: {token}
TTL: 15分钟

Key: csrf:user:{userId}
Value: Set<{tokenId}>
TTL: 7天（与 refresh token 同步）
```

### 6.2 CsrfService 实现

```java
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

### 6.3 CSRF 验证拦截器

```java
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private final CsrfService csrfService;

    // 需要 CSRF 验证的方法
    private static final Set<String> CSRF_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    // 豁免 CSRF 的路径
    private static final Set<String> EXEMPT_PATHS = Set.of(
        "/auth/login", "/auth/register", "/auth/refresh", "/auth/logout"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {
        if (!CSRF_METHODS.contains(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (EXEMPT_PATHS.contains(path)) {
            return true;
        }

        // 从 Header 获取 CSRF Token
        String csrfToken = request.getHeader("X-CSRF-Token");
        if (csrfToken == null) {
            throw new BusinessException(ErrorCode.CSRF_TOKEN_MISSING);
        }

        // 从 SecurityContext 获取用户ID
        String userId = getCurrentUserId();
        if (userId == null) {
            return true; // 未认证请求由 Security 处理
        }

        if (!csrfService.validateToken(userId, csrfToken)) {
            throw new BusinessException(ErrorCode.CSRF_TOKEN_INVALID);
        }

        return true;
    }
}
```

---

## 7. Refresh Token 数据库存储

### 7.1 RefreshToken 实体

```java
@Data
@TableName("refresh_tokens")
public class RefreshToken {

    @TableId(type = IdType.INPUT)
    private String id;

    private String userId;

    private String tokenHash;      // 存储哈希值，不存明文

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private Boolean revoked;

    private LocalDateTime revokedAt;
}
```

### 7.2 RefreshTokenService

```java
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

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
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plus(REFRESH_TOKEN_TTL));
        refreshToken.setCreatedAt(LocalDateTime.now());
        refreshToken.setRevoked(false);

        refreshTokenMapper.insert(refreshToken);

        // 设置 Cookie
        setRefreshTokenCookie(response, token);

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
                .eq(RefreshToken::getRevoked, false)
        );

        if (storedToken == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        // 撤销旧 Token（Token 轮换）
        revokeToken(storedToken.getId());

        // 生成新 Token
        return createToken(storedToken.getUserId(), response);
    }

    /**
     * 撤销用户所有 Token（登出时）
     */
    public void revokeAllUserTokens(String userId) {
        refreshTokenMapper.update(null,
            new LambdaUpdateWrapper<RefreshToken>()
                .set(RefreshToken::getRevoked, true)
                .set(RefreshToken::getRevokedAt, LocalDateTime.now())
                .eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getRevoked, false)
        );
    }
}
```

---

## 8. OAuth 集成（GitHub/Google）

### 8.1 OAuth 配置

```yaml
# application.yml
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

### 8.2 OAuth 流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  用户    │     │  前端    │     │  后端    │     │  OAuth   │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ 点击登录       │                │                │
     │───────────────>│                │                │
     │                │ GET /auth/github              │
     │                │───────────────>│                │
     │                │                │ 302 重定向     │
     │                │<───────────────│                │
     │                │                │                │
     │                                  授权页面        │
     │─────────────────────────────────────────────────>│
     │                                  授权确认        │
     │<─────────────────────────────────────────────────│
     │                │                │                │
     │                │ GET /auth/github/callback?code= │
     │                │<────────────────────────────────│
     │                │                │                │
     │                │ GET /auth/github/callback?code= │
     │                │───────────────>│                │
     │                │                │ 获取 token     │
     │                │                │───────────────>│
     │                │                │ 获取用户信息   │
     │                │                │───────────────>│
     │                │                │<───────────────│
     │                │                │ 创建/更新用户  │
     │                │                │ 生成 JWT       │
     │                │ 302 重定向前端 + Set-Cookie     │
     │                │<───────────────│                │
     │                │                │                │
     │ 已登录         │                │                │
     │<───────────────│                │                │
```

### 8.3 OAuthController 端点

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    @GetMapping("/github")
    public void githubLogin(HttpServletResponse response) throws IOException {
        String authUrl = oauthService.getGithubAuthUrl();
        response.sendRedirect(authUrl);
    }

    @GetMapping("/github/callback")
    public void githubCallback(
            @RequestParam String code,
            HttpServletResponse response) throws IOException {
        LoginResponse loginResponse = oauthService.handleGithubCallback(code, response);
        // 重定向到前端
        response.sendRedirect("http://localhost:9002/?oauth=success");
    }

    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        String authUrl = oauthService.getGoogleAuthUrl();
        response.sendRedirect(authUrl);
    }

    @GetMapping("/google/callback")
    public void googleCallback(
            @RequestParam String code,
            HttpServletResponse response) throws IOException {
        LoginResponse loginResponse = oauthService.handleGoogleCallback(code, response);
        response.sendRedirect("http://localhost:9002/?oauth=success");
    }
}
```

### 8.4 SecurityConfig 更新

```java
private static final String[] PUBLIC_ENDPOINTS = {
    "/auth/login",
    "/auth/register",
    "/auth/refresh",
    "/auth/github",
    "/auth/github/callback",    // 新增
    "/auth/google",
    "/auth/google/callback",    // 新增
    "/auth/forgot-password",    // 新增
    "/auth/reset-password",     // 新增
    "/problems",
    "/problems/**",
    // ... 其他公开端点
};
```

---

## 9. Rate Limiting 限流

### 9.1 限流配置（三级限流）

```java
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter registry(RedisTemplate<String, String> redisTemplate) {
        return RateLimiter.builder()
            .addInterceptor(new SimpleRateLimiter(
                redisTemplate,
                Duration.ofSeconds(3),      // short: 3次/秒
                Duration.ofSeconds(1),
                "rate-limit:short:"
            ))
            .addInterceptor(new SimpleRateLimiter(
                redisTemplate,
                Duration.ofSeconds(20),     // medium: 20次/10秒
                Duration.ofSeconds(10),
                "rate-limit:medium:"
            ))
            .addInterceptor(new SimpleRateLimiter(
                redisTemplate,
                Duration.ofMinutes(1),       // long: 100次/分钟
                Duration.ofMinutes(1),
                "rate-limit:long:"
            ))
            .build();
    }
}
```

### 9.2 限流注解

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    RateLimitType value() default RateLimitType.NONE;
    int limit() default 100;
    int periodSeconds() default 60;
}

public enum RateLimitType {
    NONE,
    SHORT,    // 3/秒
    MEDIUM,   // 20/10秒
    LONG      // 100/分钟
}
```

### 9.3 限流拦截器

```java
@Aspect
@Component
public class RateLimitAspect {

    @Around("@annotation(com.ulticode.common.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String key = generateKey(joinPoint);

        // 执行限流检查
        // ... Redis INCR + EXPIRE 逻辑

        return joinPoint.proceed();
    }
}
```

---

## 10. 权限系统集成

### 10.1 权限表结构（已存在于数据库）

```sql
-- user_permissions 表已存在
-- 结构: user_id, resource, action, granted_at
```

### 10.2 UserPermission 实体

```java
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

### 10.3 PermissionService

```java
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
        // 尝试从缓存获取
        String cacheKey = PERM_CACHE_PREFIX + userId;
        // ...

        return userPermissionMapper.selectByUserId(userId);
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

### 10.4 权限注解

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permissionService.hasPermission(authentication.principal.id, #action, #resource)")
public @interface RequirePermission {
    String action();
    String resource();
}
```

### 10.5 `/auth/permissions` 端点

```java
@GetMapping("/permissions")
public Result<List<String>> getPermissions(@AuthenticationPrincipal User user) {
    List<UserPermission> permissions = permissionService.getUserPermissions(user.getId());
    List<String> result = permissions.stream()
        .map(p -> p.getAction() + ":" + p.getResource())
        .collect(Collectors.toList());
    return Result.success(result);
}
```

---

## 11. 忘记密码功能

### 11.1 密码重置流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  用户    │     │  前端    │     │  后端    │     │  邮件    │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ POST /auth/forgot-password     │                │
     │───────────────>│                │                │
     │                │───────────────>│                │
     │                │                │ 生成重置Token  │
     │                │                │ 存储到Redis    │
     │                │                │───────────────>│
     │                │                │   发送邮件     │
     │                │                │<───────────────│
     │                │  200 OK        │                │
     │                │<───────────────│                │
     │                │                │                │
     │  点击邮件链接  │                │                │
     │───────────────>│                │                │
     │                │ POST /auth/reset-password      │
     │                │───────────────>│                │
     │                │                │ 验证Token      │
     │                │                │ 更新密码       │
     │                │  200 OK        │                │
     │                │<───────────────│                │
```

### 11.2 DTO 定义

```java
@Data
public class ForgotPasswordDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}

@Data
public class ResetPasswordDTO {
    @NotBlank(message = "Token不能为空")
    private String token;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度6-20位")
    private String newPassword;
}
```

### 11.3 密码重置服务

```java
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserMapper userMapper;
    private final EmailService emailService;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;

    private static final String RESET_PREFIX = "password-reset:";
    private static final Duration RESET_TTL = Duration.ofHours(1);

    public void forgotPassword(String email) {
        User user = userMapper.selectByEmail(email);
        if (user == null) {
            // 不透露用户是否存在
            return;
        }

        String token = IdUtil.simpleUUID();
        String key = RESET_PREFIX + token;

        redisTemplate.opsForValue().set(key, user.getId(), RESET_TTL);

        String resetUrl = "http://localhost:9002/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(email, resetUrl);
    }

    public void resetPassword(String token, String newPassword) {
        String key = RESET_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 删除重置 Token
        redisTemplate.delete(key);
    }
}
```

---

## 12. 测试策略

### 12.1 测试覆盖目标

| 类型 | 覆盖率 | 重点 |
|------|--------|------|
| 单元测试 | 80%+ | Service 层业务逻辑 |
| 集成测试 | 70%+ | Controller 层 API 端点 |
| E2E 测试 | 关键流程 | 登录、注册、OAuth 流程 |

### 12.2 关键测试用例

```java
@SpringBootTest
class AuthServiceTest {

    @Test void login_success()           // 登录成功
    @Test void login_invalidPassword()   // 密码错误
    @Test void login_userNotFound()      // 用户不存在
    @Test void login_bannedUser()        // 被封禁用户

    @Test void register_success()        // 注册成功
    @Test void register_usernameTaken()  // 用户名已存在
    @Test void register_emailTaken()     // 邮箱已存在

    @Test void refresh_validToken()      // 刷新成功
    @Test void refresh_expiredToken()    // Token 过期
    @Test void refresh_revokedToken()    // Token 已撤销

    @Test void logout_clearsCookies()    // 登出清除 Cookie
    @Test void logout_revokesRefreshToken() // 登出撤销 Refresh Token
}

@SpringBootTest
class CsrfServiceTest {

    @Test void generateToken_storedInRedis()  // Token 存入 Redis
    @Test void validateToken_valid()           // 验证有效 Token
    @Test void validateToken_invalid()         // 验证无效 Token
    @Test void clearUserTokens_allRemoved()    // 清除用户所有 Token
}

@SpringBootTest
class OAuthServiceTest {

    @Test void githubAuthUrl_correctFormat()   // GitHub URL 正确
    @Test void githubCallback_createsUser()     // GitHub 回调创建用户
    @Test void githubCallback_existingUser()    // GitHub 回调已有用户

    @Test void googleAuthUrl_correctFormat()    // Google URL 正确
    @Test void googleCallback_createsUser()     // Google 回调创建用户
}
```

---

## 13. 实施阶段计划

### Phase 1: 核心修复（P0）

**目标**: 让基本登录功能可用

| 任务 | 文件 | 说明 |
|------|------|------|
| 1.1 | `User.java` | 修复字段映射，添加逻辑删除字段 |
| 1.2 | `application.yml` | 更新 Cookie 配置为 NestJS 命名 |
| 1.3 | `AuthController.java` | 添加 `GET /auth/me` 端点 |
| 1.4 | `AuthServiceImpl.java` | 对齐登录响应格式 |
| 1.5 | 数据库迁移 | 添加 `is_deleted`, `deleted_at`, `deleted_by` 字段 |
| 1.6 | 单元测试 | 核心认证测试 |

**验证标准**:
- ✅ 用户可以登录/注册
- ✅ `/auth/me` 返回当前用户信息
- ✅ Cookie 名称为 `access_token`, `refresh_token`

---

### Phase 2: 认证增强（P1）

**目标**: 完善认证安全机制

| 任务 | 文件 | 说明 |
|------|------|------|
| 2.1 | `RefreshToken.java` | 新增实体 |
| 2.2 | `RefreshTokenService.java` | Token 数据库存储与轮换 |
| 2.3 | `CsrfService.java` | CSRF Token 生成与验证 |
| 2.4 | `CsrfInterceptor.java` | CSRF 验证拦截器 |
| 2.5 | `AuthController.java` | 添加忘记密码/重置密码端点 |
| 2.6 | `PasswordResetService.java` | 密码重置逻辑 |
| 2.7 | 数据库迁移 | 创建 `refresh_tokens`, `csrf_tokens` 表 |
| 2.8 | 单元测试 | CSRF 和 Token 轮换测试 |

**验证标准**:
- ✅ Refresh Token 存储在数据库
- ✅ Token 轮换正常工作
- ✅ CSRF Token 验证生效
- ✅ 忘记密码邮件发送成功

---

### Phase 3: OAuth 集成（P2）

**目标**: 完整实现第三方登录

| 任务 | 文件 | 说明 |
|------|------|------|
| 3.1 | `OAuthConfig.java` | GitHub/Google OAuth 配置 |
| 3.2 | `OAuthService.java` | OAuth 核心逻辑 |
| 3.3 | `OAuthController.java` | OAuth 端点（已有，完善实现） |
| 3.4 | `SecurityConfig.java` | 添加回调端点到白名单 |
| 3.5 | `.env.example` | 添加 OAuth 环境变量模板 |
| 3.6 | 单元测试 | OAuth 流程测试 |

**验证标准**:
- ✅ GitHub OAuth 登录成功
- ✅ Google OAuth 登录成功
- ✅ 新用户自动创建
- ✅ 已有用户关联正确

---

### Phase 4: 完善功能（P3）

**目标**: 安全性和权限系统

| 任务 | 文件 | 说明 |
|------|------|------|
| 4.1 | `RateLimitAspect.java` | 限流切面 |
| 4.2 | `RateLimitConfig.java` | 三级限流配置 |
| 4.3 | `PermissionService.java` | 权限服务 |
| 4.4 | `AuthController.java` | 添加 `GET /auth/permissions` 端点 |
| 4.5 | `@RequirePermission` | 权限注解 |
| 4.6 | 集成测试 | API 端点测试 |
| 4.7 | E2E 测试 | 关键用户流程 |

**验证标准**:
- ✅ 限流生效（超出返回 429）
- ✅ `/auth/permissions` 返回权限列表
- ✅ 权限注解正确拦截

---

## 14. 文件修改清单

### 新增文件 (12个)

```
backend-spring/src/main/java/com/ulticode/
├── common/
│   ├── annotation/
│   │   └── RateLimit.java
│   └── aspect/
│       └── RateLimitAspect.java
├── security/
│   └── csrf/
│       ├── CsrfService.java
│       └── CsrfInterceptor.java
├── modules/
│   ├── auth/
│   │   ├── service/
│   │   │   ├── OAuthService.java
│   │   │   └── PasswordResetService.java
│   │   └── dto/
│   │       ├── ForgotPasswordDTO.java
│   │       └── ResetPasswordDTO.java
│   ├── refresh-token/
│   │   ├── entity/RefreshToken.java
│   │   ├── mapper/RefreshTokenMapper.java
│   │   └── service/RefreshTokenService.java
│   └── permission/
│       ├── entity/UserPermission.java
│       ├── mapper/UserPermissionMapper.java
│       └── service/PermissionService.java
```

### 修改文件 (15个)

```
backend-spring/
├── src/main/java/com/ulticode/
│   ├── common/config/
│   │   ├── SecurityConfig.java          # 添加公开端点
│   │   └── RateLimitConfig.java         # 新建限流配置
│   ├── modules/
│   │   ├── user/entity/User.java        # 修复字段映射
│   │   └── auth/
│   │       ├── controller/AuthController.java  # 添加端点
│   │       ├── service/impl/AuthServiceImpl.java # 对齐响应
│   │       └── dto/LoginResponse.java   # 调整结构
│   └── security/
│       └── jwt/JwtProperties.java       # 添加 Cookie 配置
├── src/main/resources/
│   └── application.yml                   # 更新配置
├── init-db/migrations/
│   ├── V006__add_user_soft_delete.sql   # 用户表逻辑删除
│   ├── V007__create_refresh_tokens.sql  # Refresh Token 表
│   └── V008__create_csrf_tokens.sql     # CSRF Token 表
└── .env.example                          # 添加 OAuth 变量
```

---

## 15. 环境变量

需要添加到 `.env` 文件的环境变量：

```bash
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
EMAIL_ENABLED=true
```

---

## 16. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 数据库迁移失败 | 服务不可用 | 先备份，使用事务迁移 |
| 现有用户密码不兼容 | 无法登录 | 保持 BCrypt 兼容，添加密码迁移脚本 |
| OAuth 配置错误 | 第三方登录失败 | 添加详细错误日志，提供配置检查端点 |
| Redis 连接失败 | CSRF/限流失效 | 添加降级策略，记录日志 |

---

## 17. 附录

### A. NestJS vs Spring Boot 功能对照表

| 功能 | NestJS 实现 | Spring Boot 实现 | 状态 |
|------|-------------|------------------|------|
| JWT 认证 | `@nestjs/jwt` | `jjwt` | ✅ 已完成 |
| 密码加密 | `bcrypt` | `BCryptPasswordEncoder` | ✅ 已完成 |
| Cookie | `cookie-parser` | Servlet Cookie | ⚠️ 需修复名称 |
| CSRF | 自定义 `CsrfService` | 待实现 | ❌ 缺失 |
| Refresh Token | 数据库 + Redis | 仅 Cookie | ❌ 需完善 |
| OAuth | `passport` | 待实现 | ❌ 缺失 |
| 限流 | 自定义装饰器 | 待实现 | ❌ 缺失 |
| 权限 | 自定义 Guard | 待实现 | ⚠️ 部分完成 |

### B. 参考文档

- [Spring Security 官方文档](https://docs.spring.io/spring-security/reference/)
- [NestJS 认证文档](https://docs.nestjs.com/security/authentication)
- [OAuth 2.0 规范](https://oauth.net/2/)
- [BCrypt 密码哈希](https://en.wikipedia.org/wiki/Bcrypt)
