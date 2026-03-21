# NestJS 到 Spring Boot 迁移设计文档

> 创建日期: 2026-03-21
> 状态: 待实施
> 目标: 将 `/backend` (NestJS) 的架构完整迁移到 `/backend-spring` (Spring Boot)

---

## 1. 项目概述

### 1.1 迁移目标

- **完全替换**: 用 Spring Boot 完全替换 NestJS 后端，最终废弃 NestJS
- **保持兼容**: 与现有前端 (Vue 3) 保持 API 兼容性
- **功能对等**: 所有 NestJS 功能在 Spring Boot 中实现

### 1.2 当前状态

| 维度 | NestJS Backend | Spring Boot Backend-Spring |
|------|---------------|---------------------------|
| 模块数量 | 25+ 个业务模块 | 空项目 |
| 数据模型 | Prisma + MySQL (70+ 模型) | 待配置 |
| 认证 | JWT + Cookie | 待实现 |
| 缓存 | Redis + Cache Manager | 待配置 |
| 任务队列 | BullMQ | 待实现 |
| 实时通信 | WebSocket (Socket.io) | 待实现 |
| API 文档 | Swagger | 待实现 |

### 1.3 源项目模块列表

```
NestJS 模块 (backend/src/):
├── achievement/      # 成就系统
├── admin/            # 管理后台
├── auth/             # 认证授权
├── backup/           # 备份系统
├── bookmark/         # 收藏夹
├── cache/            # 缓存配置
├── common/           # 公共模块
├── config/           # 配置
├── contest/          # 竞赛系统
├── edge-operations/  # 边缘操作 (点赞等)
├── email/            # 邮件服务
├── forum/            # 论坛系统
├── i18n/             # 国际化
├── moderation/       # 内容审核
├── monitoring/       # 监控
├── notification/     # 通知系统
├── problem/          # 题目管理
├── problem-list/     # 题单管理
├── recommendation/   # 推荐系统
├── search/           # 搜索服务
├── solution/         # 题解系统
├── submission/       # 提交系统
├── subscription/     # 订阅系统
├── test-case/        # 测试用例
├── user/             # 用户管理
├── view/             # 浏览记录
└── vote/             # 投票系统
```

---

## 1.1 重要变更说明 (Breaking Changes)

### 1.1.1 WebSocket 协议变更

**影响**: 前端需要从 Socket.io 客户端迁移到 STOMP 客户端

**NestJS 当前使用**:
- Socket.io 协议
- 事件: `subscribe:community`, `subscribe:contest`, `ping`
- 服务端推送: `SUBMISSION_RESULT`, `CONTEST_UPDATE`, `BADGE_EARNED`

**Spring Boot 将使用**:
- STOMP over WebSocket
- 订阅地址: `/topic/community/{id}`, `/topic/contest/{id}`
- 消息格式保持兼容

**前端迁移指南** (需在迁移时提供):
```typescript
// Socket.io (旧)
socket.emit('subscribe:community', communityId);

// STOMP (新)
stompClient.subscribe(`/topic/community/${communityId}`, callback);
```

### 1.1.2 OAuth 支持

NestJS 支持 GitHub 和 Google OAuth 登录。Spring Boot 将继续支持这些提供商：
- Spring Security OAuth2 Client
- 需要配置 GitHub/Google OAuth2 凭据

---

## 2. 技术选型

### 2.1 技术栈对照表

| 功能 | NestJS | Spring Boot |
|------|--------|-------------|
| **框架核心** | @nestjs/core | spring-boot-starter-web |
| **ORM** | Prisma | MyBatis-Plus 3.5.5 |
| **数据库** | MySQL | MySQL (复用现有) |
| **认证** | @nestjs/jwt | Spring Security + jjwt 0.12.5 |
| **缓存** | cache-manager-redis-store | Spring Data Redis |
| **任务队列** | BullMQ | Redisson + Spring Integration |
| **WebSocket** | Socket.io | Spring WebSocket + STOMP (**注意**: 前端需要迁移) |
| **限流** | @nestjs/throttler | Bucket4j / Resilience4j |
| **API文档** | @nestjs/swagger | SpringDoc OpenAPI 3 |
| **定时任务** | @nestjs/schedule | Spring Boot Quartz |
| **验证** | class-validator | Jakarta Validation |
| **配置** | @nestjs/config | Spring Boot Configuration |
| **工具类** | - | Hutool 5.8.26 |
| **对象映射** | - | MapStruct 1.5.5 |

### 2.2 核心依赖

```xml
<properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <jjwt.version>0.12.5</jjwt.version>
    <redisson.version>3.27.0</redisson.version>
    <springdoc.version>2.3.0</springdoc.version>
    <hutool.version>5.8.26</hutool.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
</properties>

<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>${mybatis-plus.version}</version>
    </dependency>

    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Redisson -->
    <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson-spring-boot-starter</artifactId>
        <version>${redisson.version}</version>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>

    <!-- SpringDoc OpenAPI -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Hutool -->
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
        <version>${hutool.version}</version>
    </dependency>

    <!-- MapStruct -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
</dependencies>
```

---

## 3. 项目结构

### 3.1 目录结构

```
backend-spring/
├── src/main/java/com/ulticode/
│   ├── UltiCodeApplication.java
│   │
│   ├── common/                          # 公共模块
│   │   ├── config/                      # 配置类
│   │   │   ├── MybatisPlusConfig.java   # MyBatis-Plus 配置
│   │   │   ├── RedisConfig.java         # Redis 配置
│   │   │   ├── RedissonConfig.java      # Redisson 配置
│   │   │   ├── SecurityConfig.java      # Spring Security 配置
│   │   │   ├── SwaggerConfig.java       # API 文档配置
│   │   │   ├── WebConfig.java           # Web 配置 (CORS等)
│   │   │   └── WebSocketConfig.java     # WebSocket 配置
│   │   │
│   │   ├── exception/                   # 异常处理
│   │   │   ├── BusinessException.java   # 业务异常
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ErrorCode.java           # 错误码枚举
│   │   │
│   │   ├── response/                    # 统一响应
│   │   │   ├── Result.java              # 统一响应包装
│   │   │   └── PageResult.java          # 分页响应
│   │   │
│   │   ├── annotation/                  # 自定义注解
│   │   │   ├── RequireRole.java         # 角色验证
│   │   │   └── CurrentUser.java         # 当前用户
│   │   │
│   │   └── util/                        # 工具类
│   │       ├── SecurityUtil.java        # 安全工具
│   │       └── JsonUtil.java            # JSON 工具
│   │
│   ├── security/                        # 安全模块
│   │   ├── jwt/
│   │   │   ├── JwtTokenProvider.java    # JWT 生成/验证
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── JwtProperties.java       # JWT 配置属性
│   │   ├── UserDetailsServiceImpl.java
│   │   └── AuthenticationEntryPointImpl.java
│   │
│   ├── infrastructure/                  # 基础设施
│   │   ├── redis/                       # Redis 操作
│   │   │   ├── RedisService.java
│   │   │   └── CacheConstants.java
│   │   ├── queue/                       # 任务队列
│   │   │   ├── QueueConfig.java
│   │   │   └── QueueProcessor.java
│   │   └── websocket/                   # WebSocket
│   │       ├── WebSocketHandler.java
│   │       └── WebSocketInterceptor.java
│   │
│   └── modules/                         # 业务模块
│       ├── user/
│       │   ├── controller/
│       │   │   └── UserController.java
│       │   ├── service/
│       │   │   ├── UserService.java
│       │   │   └── impl/UserServiceImpl.java
│       │   ├── mapper/
│       │   │   └── UserMapper.java
│       │   ├── entity/
│       │   │   └── User.java
│       │   └── dto/
│       │       ├── UserDTO.java
│       │       └── UserVO.java
│       ├── auth/
│       ├── problem/
│       ├── solution/
│       ├── contest/
│       ├── forum/
│       ├── submission/
│       ├── notification/
│       ├── bookmark/
│       ├── moderation/
│       └── ...
│
├── src/main/resources/
│   ├── application.yml                  # 主配置
│   ├── application-dev.yml              # 开发环境
│   ├── application-prod.yml             # 生产环境
│   └── mapper/                          # MyBatis XML
│       └── UserMapper.xml
│
├── src/test/java/com/ulticode/          # 测试
│   └── modules/
│
└── pom.xml
```

### 3.2 包命名规范

| 层级 | 包名 | 职责 |
|------|------|------|
| Controller | `controller` | REST API 入口，参数验证 |
| Service | `service` / `service.impl` | 业务逻辑 |
| Mapper | `mapper` | 数据访问 (MyBatis-Plus) |
| Entity | `entity` | 数据库实体 (对应 Prisma model) |
| DTO | `dto` | 数据传输对象 (请求/响应) |
| VO | `dto` | 视图对象 (响应数据) |

---

## 4. 核心设计

### 4.1 统一响应格式

**保持与 NestJS 前端兼容的响应格式**:

```java
// Result.java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Result<T> {
    private Integer code;       // 0=成功, 其他=失败
    private String message;     // 消息
    private T data;             // 数据
    private String traceId;     // 追踪ID (与 NestJS 兼容)

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
            .code(0)
            .message("ok")
            .data(data)
            .traceId(generateTraceId())
            .build();
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return Result.<T>builder()
            .code(code)
            .message(message)
            .traceId(generateTraceId())
            .build();
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }

    private static String generateTraceId() {
        return "t-" + System.currentTimeMillis();
    }
}

// PageResult.java
@Data
@Builder
public class PageResult<T> {
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;

    public static <T> PageResult<T> of(List<T> items, Long total, Integer page, Integer pageSize) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return PageResult.<T>builder()
            .items(items)
            .total(total)
            .page(page)
            .pageSize(pageSize)
            .totalPages(totalPages)
            .build();
    }
}
```

### 4.2 错误码定义

**严格复制自 NestJS `common/error-codes.ts`** (保持前端兼容性):

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Generic errors (0xxxx)
    SUCCESS(0, "Success"),
    UNKNOWN_ERROR(50000, "Unknown error"),
    BAD_REQUEST(40000, "Bad request"),
    UNAUTHORIZED(40100, "Unauthorized"),
    FORBIDDEN(40300, "Forbidden"),
    NOT_FOUND(40400, "Not found"),
    CONFLICT(40900, "Conflict"),

    // Auth module (1xxxx)
    AUTH_INVALID_CREDENTIALS(10001, "Invalid credentials"),
    AUTH_NO_PASSWORD(10002, "No password set"),
    AUTH_USERNAME_TAKEN(10003, "Username already taken"),
    AUTH_EMAIL_TAKEN(10004, "Email already taken"),
    AUTH_USER_NOT_FOUND(10005, "User not found"),
    AUTH_TOKEN_EXPIRED(10006, "Token expired"),
    AUTH_INVALID_RESET_TOKEN(10007, "Invalid reset token"),
    AUTH_RESET_TOKEN_ALREADY_USED(10008, "Reset token already used"),
    AUTH_RESET_TOKEN_EXPIRED(10009, "Reset token expired"),

    // User module (2xxxx)
    USER_NOT_FOUND(20001, "User not found"),
    USER_CANNOT_EDIT_OTHERS(20002, "Cannot edit other users"),

    // Problem module (3xxxx)
    PROBLEM_NOT_FOUND(30001, "Problem not found"),
    PROBLEM_LOCKED(30002, "Problem locked"),
    PROBLEM_PREMIUM_REQUIRED(30003, "Premium required"),

    // Submission module (4xxxx)
    SUBMISSION_NOT_FOUND(40001, "Submission not found"),
    SUBMISSION_USER_ID_REQUIRED(40002, "User ID required"),
    SUBMISSION_RATE_LIMITED(40003, "Rate limited"),
    SUBMISSION_CODE_EMPTY(40004, "Code cannot be empty"),
    SUBMISSION_LANGUAGE_UNSUPPORTED(40005, "Language not supported"),

    // Solution module (5xxxx)
    SOLUTION_NOT_FOUND(50001, "Solution not found"),
    SOLUTION_CANNOT_DELETE_OTHERS(50002, "Cannot delete others' solutions"),
    SOLUTION_CANNOT_UPDATE_OTHERS(50003, "Cannot update others' solutions"),
    SOLUTION_COMMENT_NOT_FOUND(50004, "Comment not found"),
    SOLUTION_NEED_ACCEPTED_SUBMISSION(50007, "Need accepted submission"),
    SOLUTION_ALREADY_EXISTS(50008, "Solution already exists"),

    // Forum module (6xxxx)
    FORUM_POST_NOT_FOUND(60001, "Post not found"),
    FORUM_COMMUNITY_NOT_FOUND(60002, "Community not found"),
    FORUM_COMMUNITY_RESTRICTED(60003, "Community restricted"),
    FORUM_CANNOT_EDIT_POST(60004, "Cannot edit post"),
    FORUM_CANNOT_DELETE_POST(60005, "Cannot delete post"),
    FORUM_COMMENT_NOT_FOUND(60006, "Comment not found"),
    FORUM_POST_LOCKED(60007, "Post locked"),

    // Contest module (7xxxx)
    CONTEST_NOT_FOUND(70001, "Contest not found"),
    CONTEST_ONLY_REGISTER_UPCOMING(70002, "Can only register for upcoming contests"),
    CONTEST_ALREADY_REGISTERED(70003, "Already registered"),
    CONTEST_NOT_REGISTERED(70004, "Not registered"),
    CONTEST_REGISTRATION_CLOSED(70005, "Registration closed"),
    CONTEST_FULL(70006, "Contest full"),
    CONTEST_NO_PERMISSION(70007, "No permission"),
    CONTEST_NOT_STARTED(70008, "Contest not started"),
    CONTEST_ENDED(70009, "Contest ended"),

    // Bookmark module (8xxxx)
    BOOKMARK_FOLDER_NOT_FOUND(80001, "Folder not found"),
    BOOKMARK_CANNOT_DELETE_DEFAULT(80002, "Cannot delete default folder"),
    BOOKMARK_FOLDER_NAME_EXISTS(80003, "Folder name exists"),

    // Problem list module (9xxxx)
    PROBLEM_LIST_NOT_FOUND(90001, "Problem list not found"),
    PROBLEM_LIST_CANNOT_EDIT(90002, "Cannot edit problem list"),
    PROBLEM_LIST_PRIVATE(90003, "Problem list is private"),
    ;

    private final Integer code;
    private final String message;
}
```

**HTTP 状态码映射** (与 NestJS 保持一致):

```java
public enum ErrorCode {
    // ... 错误码定义 ...

    public HttpStatus getHttpStatus() {
        return switch (this) {
            // Generic
            case SUCCESS -> HttpStatus.OK;
            case UNKNOWN_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;

            // Auth
            case AUTH_INVALID_CREDENTIALS, AUTH_NO_PASSWORD, AUTH_TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case AUTH_USERNAME_TAKEN, AUTH_EMAIL_TAKEN -> HttpStatus.CONFLICT;
            case AUTH_USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case AUTH_INVALID_RESET_TOKEN, AUTH_RESET_TOKEN_ALREADY_USED, AUTH_RESET_TOKEN_EXPIRED -> HttpStatus.BAD_REQUEST;

            // User
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_CANNOT_EDIT_OTHERS -> HttpStatus.FORBIDDEN;

            // Problem
            case PROBLEM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PROBLEM_LOCKED, PROBLEM_PREMIUM_REQUIRED -> HttpStatus.FORBIDDEN;

            // Submission
            case SUBMISSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SUBMISSION_USER_ID_REQUIRED, SUBMISSION_CODE_EMPTY, SUBMISSION_LANGUAGE_UNSUPPORTED -> HttpStatus.BAD_REQUEST;
            case SUBMISSION_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;

            // Solution
            case SOLUTION_NOT_FOUND, SOLUTION_COMMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SOLUTION_CANNOT_DELETE_OTHERS, SOLUTION_CANNOT_UPDATE_OTHERS, SOLUTION_NEED_ACCEPTED_SUBMISSION -> HttpStatus.FORBIDDEN;
            case SOLUTION_ALREADY_EXISTS -> HttpStatus.CONFLICT;

            // Forum
            case FORUM_POST_NOT_FOUND, FORUM_COMMUNITY_NOT_FOUND, FORUM_COMMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORUM_COMMUNITY_RESTRICTED, FORUM_CANNOT_EDIT_POST, FORUM_CANNOT_DELETE_POST, FORUM_POST_LOCKED -> HttpStatus.FORBIDDEN;

            // Contest
            case CONTEST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONTEST_NO_PERMISSION -> HttpStatus.FORBIDDEN;
            case CONTEST_ALREADY_REGISTERED -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;

            // Bookmark
            case BOOKMARK_FOLDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BOOKMARK_CANNOT_DELETE_DEFAULT -> HttpStatus.BAD_REQUEST;
            case BOOKMARK_FOLDER_NAME_EXISTS -> HttpStatus.CONFLICT;

            // Problem list
            case PROBLEM_LIST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PROBLEM_LIST_CANNOT_EDIT, PROBLEM_LIST_PRIVATE -> HttpStatus.FORBIDDEN;
        };
    }
}
```

### 4.3 CSRF Token 支持

**注意**: NestJS 在登录/注册响应中返回 `csrf_token`。Spring Boot 需要保持兼容。

```java
// LoginResponse.java
@Data
@Builder
public class LoginResponse {
    private String csrfToken;  // 与 NestJS 兼容
    private UserVO user;
    private String accessToken;
}

// SecurityConfig.java - 启用 CSRF
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ... 其他配置 ...
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            );
        return http.build();
    }
}
```

### 4.4 全局异常处理

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return Result.error(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return Result.error(ErrorCode.VALIDATION_ERROR.getCode(), message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException e) {
        return Result.error(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public Result<?> handleAuthenticationException(AuthenticationCredentialsNotFoundException e) {
        return Result.error(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Unexpected error", e);
        return Result.error(ErrorCode.UNKNOWN_ERROR);
    }
}
```

### 4.4 JWT 认证设计

**认证流程**:

```
1. 登录: POST /api/auth/login
   - 验证 username/password
   - 生成 accessToken (15分钟) + refreshToken (7天)
   - accessToken 放入 Cookie (httpOnly)
   - refreshToken 存入 Redis
   - 返回用户信息

2. Token 刷新: POST /api/auth/refresh
   - 验证 refreshToken
   - 生成新 accessToken
   - 可选: 轮换 refreshToken

3. 退出: POST /api/auth/logout
   - 清除 Cookie
   - 删除 Redis 中的 refreshToken
```

**JWT 配置**:

```yaml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key}
  access-token-expiration: 900000    # 15分钟
  refresh-token-expiration: 604800000 # 7天
  cookie:
    name: access_token
    http-only: true
    secure: true
    same-site: strict
    max-age: 86400
```

**Token 生成**:

```java
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId, String username, UserRole role) {
        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("role", role.name())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(getSecretKey())
            .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
            .signWith(getSecretKey())
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSecretKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

### 4.5 权限控制

**自定义注解**:

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole(@securityUtil.getRequiredRoles(#annotation))")
public @interface RequireRole {
    UserRole[] value() default {};
}

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
```

**使用示例**:

```java
@RestController
@RequestMapping("/api/admin")
@RequireRole(UserRole.ADMIN)
public class AdminController {

    @GetMapping("/users")
    public Result<PageResult<UserVO>> listUsers(...) {
        // 只有 ADMIN 及以上角色可访问
    }

    @PostMapping("/problems")
    @RequireRole({UserRole.ADMIN, UserRole.MODERATOR})
    public Result<ProblemVO> createProblem(...) {
        // ADMIN 和 MODERATOR 可访问
    }
}
```

### 4.6 MyBatis-Plus 配置

```java
@Configuration
@MapperScan("com.ulticode.modules.*.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AutoFillMetaObjectHandler();
    }

    // 自动填充 created_at, updated_at
    public static class AutoFillMetaObjectHandler implements MetaObjectHandler {
        @Override
        public void insertFill(MetaObject metaObject) {
            this.strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
            this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        }
    }
}
```

---

## 5. 迁移计划

### 5.1 阶段划分

#### Phase 1: 基础设施层 (预计 8 天)

| 任务 | 预计时间 | 产出 |
|------|----------|------|
| 1.1 项目结构初始化 | 0.5天 | 目录结构、pom.xml |
| 1.2 依赖配置 | 0.5天 | 完整 pom.xml |
| 1.3 Entity 生成 | 2天 | 70+ Entity 类 |
| 1.4 统一响应/异常 | 1天 | Result, PageResult, ErrorCode, GlobalExceptionHandler |
| 1.5 JWT 认证 | 2天 | SecurityConfig, JwtTokenProvider, Filters |
| 1.6 Redis/Redisson 配置 | 1天 | RedisConfig, RedissonConfig, RedisService |
| 1.7 SpringDoc 配置 | 0.5天 | SwaggerConfig |
| 1.8 基础工具类 | 0.5天 | SecurityUtil, JsonUtil 等 |

#### Phase 2: 核心业务模块 (预计 12 天)

| 模块 | 预计时间 | 功能 |
|------|----------|------|
| 2.1 User | 2天 | 用户 CRUD、个人中心、头像上传 |
| 2.2 Auth | 2天 | 登录、注册、Token 刷新、密码重置 |
| 2.3 Problem | 3天 | 题目 CRUD、标签、测试用例、版本管理 |
| 2.4 Submission | 3天 | 代码提交、执行、结果查询 |
| 2.5 Solution | 2天 | 题解 CRUD、评论、投票 |

#### Phase 3: 高级功能 (预计 10 天)

| 模块 | 预计时间 | 功能 |
|------|----------|------|
| 3.1 Contest | 3天 | 竞赛创建、报名、排名、计分 |
| 3.2 Forum | 3天 | 社区、帖子、评论、标签 |
| 3.3 WebSocket | 2天 | 实时通知、竞赛状态推送 |
| 3.4 任务队列 | 2天 | 代码执行队列、邮件发送队列 |

#### Phase 4: 管理功能 (预计 7 天)

| 模块 | 预计时间 | 功能 |
|------|----------|------|
| 4.1 Admin | 2天 | 用户管理、系统配置、审计日志 |
| 4.2 Moderation | 2天 | 举报处理、内容审核、封禁管理 |
| 4.3 Notification | 1天 | 通知创建、已读状态、推送 |
| 4.4 其他模块 | 2天 | Bookmark, Achievement, Subscription 等 |

#### Phase 5: 辅助模块 (预计 5 天)

| 模块 | 预计时间 | 功能 |
|------|----------|------|
| 5.1 Recommendation | 2天 | 每日推荐、个性化推荐 |
| 5.2 Search | 1天 | 全文搜索、过滤 |
| 5.3 EdgeOperations | 1天 | 点赞、投票操作 |
| 5.4 其他 | 1天 | Backup, Monitoring, I18n |

**总计预估: 8-10 周** (考虑缓冲时间)

### 5.2 Entity 映射规范

Prisma 类型到 Java 类型映射:

| Prisma 类型 | Java 类型 | 备注 |
|-------------|-----------|------|
| String | String | - |
| BigInt | Long | - |
| Int | Integer | - |
| Boolean | Boolean | - |
| DateTime | LocalDateTime | - |
| Decimal | BigDecimal | - |
| Json | String + @TableField(typeHandler = JacksonTypeHandler.class) | JSON 字段 |
| Enum | Java Enum | 需定义对应枚举 |

### 5.3 API 路径映射

保持与 NestJS 一致的 API 路径:

```
NestJS                              Spring Boot
/api/users                          /api/users
/api/auth/login                     /api/auth/login
/api/problems                       /api/problems
/api/problems/:id                   /api/problems/{id}
/api/submissions                    /api/submissions
/api/contests                       /api/contests
...
```

---

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Entity 生成工作量大 | 高 | 使用 MyBatis-Plus 代码生成器 |
| API 不兼容导致前端修改 | 高 | 严格保持响应格式一致，使用相同的错误码 |
| WebSocket 协议不兼容 | 高 | 前端需要从 Socket.io 迁移到 STOMP |
| 任务队列迁移复杂 | 中 | 保持队列命名和 Job 数据格式兼容 |
| 性能差异 | 低 | 进行压力测试对比 |
| 时间估算过于乐观 | 中 | 增加 2 周缓冲时间，总计 8-10 周 |
| OAuth 迁移复杂 | 中 | 使用 Spring Security OAuth2 Client |
| CSRF Token 兼容性 | 中 | 保持登录响应中返回 csrf_token |

---

## 7. 验收标准

### 7.1 Phase 1 验收

- [ ] 项目可启动，访问 /swagger-ui.html 显示 API 文档
- [ ] 统一响应格式正常工作
- [ ] JWT 认证流程完整 (登录/刷新/退出)
- [ ] Redis 缓存读写正常

### 7.2 Phase 2 验收

- [ ] 用户注册/登录/信息修改正常
- [ ] 题目 CRUD 完整
- [ ] 代码提交和执行正常
- [ ] 题解功能完整

### 7.3 Phase 3 验收

- [ ] 竞赛创建和报名流程正常
- [ ] 论坛帖子/评论功能完整
- [ ] WebSocket 连接和消息推送正常
- [ ] 异步任务执行正常

### 7.4 Phase 4 验收

- [ ] 管理后台功能完整
- [ ] 内容审核流程正常
- [ ] 通知推送正常
- [ ] 所有前端功能可正常使用

---

## 8. 附录

### 8.1 参考资源

- Spring Boot 文档: https://spring.io/projects/spring-boot
- MyBatis-Plus 文档: https://baomidou.com/
- SpringDoc 文档: https://springdoc.org/
- Redisson 文档: https://redisson.org/

### 8.3 任务队列配置

**队列命名约定** (必须与 NestJS BullMQ 保持一致):

| 队列名称 | 用途 | Job 数据结构 |
|----------|------|-------------|
| `judge_queue` | 代码判题 | `{ submissionId: string }` |
| `contest_queue` | 竞赛定时任务 | `{ contestId: string, action: string }` |
| `notification_queue` | 通知发送 | `{ userId: string, type: string, data: object }` |

**Redisson 配置**:

```java
@Configuration
public class RedissonQueueConfig {

    @Bean
    public RQueue<JudgeJob> judgeQueue(RedissonClient redisson) {
        return redisson.getQueue("judge_queue");
    }

    @Bean
    public RDelayedQueue<JudgeJob> delayedJudgeQueue(RedissonClient redisson) {
        RQueue<JudgeJob> queue = redisson.getQueue("judge_queue");
        return redisson.getDelayedQueue(queue);
    }
}

// Job 数据结构 (JSON 序列化兼容)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JudgeJob {
    private String submissionId;
}
```

**重试策略**:
- 最大重试次数: 3
- 重试间隔: 指数退避 (1s, 2s, 4s)
- 死信队列: `judge_queue_dlq`

### 8.4 命名约定

| 类型 | 约定 | 示例 |
|------|------|------|
| Entity | 与 Prisma model 一致 | User, Problem |
| DTO (请求) | XxxDTO | CreateUserDTO, UpdateProblemDTO |
| VO (响应) | XxxVO | UserVO, ProblemDetailVO |
| Service | XxxService | UserService |
| Controller | XxxController | UserController |
| Mapper | XxxMapper | UserMapper |
