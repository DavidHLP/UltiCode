# NestJS 到 Spring Boot 迁移 - Phase 1: 基础设施层

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 Spring Boot 项目的完整基础设施，包括项目结构、配置、认证、缓存、API 文档等，为后续业务模块开发提供基础。

**Architecture:** 采用 Spring Boot 3.5 + MyBatis-Plus + Spring Security 架构，保持与 NestJS 的 API 响应格式和错误码完全兼容。

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus 3.5.5, Spring Security, jjwt 0.12.5, Redisson 3.27.0, SpringDoc 2.3.0, Hutool 5.8.26, MapStruct 1.5.5

---

## 文件结构规划

```
backend-spring/
├── pom.xml
├── src/main/java/com/ulticode/
│   ├── UltiCodeApplication.java           # 主启动类
│   ├── common/
│   │   ├── config/                        # 配置类
│   │   ├── exception/                     # 异常处理
│   │   ├── response/                      # 统一响应
│   │   ├── annotation/                    # 自定义注解
│   │   └── util/                          # 工具类
│   ├── security/                          # 安全模块
│   │   └── jwt/
│   └── infrastructure/                    # 基础设施
│       ├── redis/
│       └── queue/
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    └── application-prod.yml
```

---

## Task 1: 项目初始化和依赖配置

**Files:**
- Modify: `backend-spring/pom.xml`
- Create: `backend-spring/src/main/java/com/ulticode/UltiCodeApplication.java`
- Create: `backend-spring/src/main/resources/application.yml`
- Create: `backend-spring/src/main/resources/application-dev.yml`

### Step 1.1: 更新 pom.xml 添加所有依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.12</version>
        <relativePath/>
    </parent>

    <groupId>com.ulticode</groupId>
    <artifactId>backend-spring</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>UltiCode Backend</name>
    <description>UltiCode Online Programming Platform - Spring Boot Backend</description>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <jjwt.version>0.12.5</jjwt.version>
        <redisson.version>3.27.0</redisson.version>
        <springdoc.version>2.3.0</springdoc.version>
        <hutool.version>5.8.26</hutool.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
        <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
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

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok-mapstruct-binding</artifactId>
                            <version>${lombok-mapstruct-binding.version}</version>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 1.2: 创建主启动类**

```java
// src/main/java/com/ulticode/UltiCodeApplication.java
package com.ulticode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UltiCodeApplication {
    public static void main(String[] args) {
        SpringApplication.run(UltiCodeApplication.class, args);
    }
}
```

- [ ] **Step 1.3: 创建 application.yml**

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: ulticode-backend
  profiles:
    active: dev

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:ulticode}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DB:0}
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

server:
  port: ${SERVER_PORT:3000}

jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-must-be-at-least-256-bits-long}
  access-token-expiration: 900000    # 15 minutes
  refresh-token-expiration: 604800000 # 7 days
  cookie:
    name: access_token
    http-only: true
    secure: false
    same-site: strict
    max-age: 86400

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.ulticode.modules.*.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: assign_id
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true

logging:
  level:
    com.ulticode: DEBUG
    org.springframework.security: DEBUG
```

- [ ] **Step 1.4: 创建 application-dev.yml**

```yaml
# src/main/resources/application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ulticode?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password:

jwt:
  cookie:
    secure: false

logging:
  level:
    com.baomidou.mybatisplus: DEBUG

springdoc:
  swagger-ui:
    enabled: true
```

- [ ] **Step 1.5: 验证项目可编译**

```bash
cd backend-spring && ./mvnw clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 1.6: Commit**

```bash
git add backend-spring/pom.xml backend-spring/src/
git commit -m "feat: 初始化 Spring Boot 项目结构和依赖配置"
```

---

## Task 2: 统一响应格式和错误码

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/common/response/Result.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/response/PageResult.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/exception/ErrorCode.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/exception/BusinessException.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/exception/GlobalExceptionHandler.java`
- Test: `backend-spring/src/test/java/com/ulticode/common/response/ResultTest.java`

### Step 2.1: 创建 Result 统一响应类

```java
// src/main/java/com/ulticode/common/response/Result.java
package com.ulticode.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private String traceId;

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

    public static <T> Result<T> error(Integer code, String message, String traceId) {
        return Result.<T>builder()
            .code(code)
            .message(message)
            .traceId(traceId)
            .build();
    }

    private static String generateTraceId() {
        return "t-" + System.currentTimeMillis();
    }
}
```

- [ ] **Step 2.2: 创建 PageResult 分页响应类**

```java
// src/main/java/com/ulticode/common/response/PageResult.java
package com.ulticode.common.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PageResult<T> {
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;

    public static <T> PageResult<T> of(List<T> items, Long total, Integer page, Integer pageSize) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
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

- [ ] **Step 2.3: 创建 ErrorCode 错误码枚举**

```java
// src/main/java/com/ulticode/common/exception/ErrorCode.java
package com.ulticode.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

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

    public HttpStatus getHttpStatus() {
        return switch (this) {
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
            case CONTEST_ONLY_REGISTER_UPCOMING, CONTEST_NOT_REGISTERED, CONTEST_REGISTRATION_CLOSED,
                 CONTEST_FULL, CONTEST_NOT_STARTED, CONTEST_ENDED -> HttpStatus.BAD_REQUEST;

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

- [ ] **Step 2.4: 创建 BusinessException 业务异常类**

```java
// src/main/java/com/ulticode/common/exception/BusinessException.java
package com.ulticode.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String traceId;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.traceId = generateTraceId();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.traceId = generateTraceId();
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.traceId = generateTraceId();
    }

    private static String generateTraceId() {
        return "t-" + System.currentTimeMillis();
    }
}
```

- [ ] **Step 2.5: 创建 GlobalExceptionHandler 全局异常处理**

```java
// src/main/java/com/ulticode/common/exception/GlobalExceptionHandler.java
package com.ulticode.common.exception;

import com.ulticode.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        Result<?> result = Result.error(e.getErrorCode().getCode(), e.getMessage(), e.getTraceId());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message);
        Result<?> result = Result.error(ErrorCode.BAD_REQUEST.getCode(), message);
        return ResponseEntity.badRequest().body(result);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        Result<?> result = Result.error(ErrorCode.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<?>> handleAuthenticationException(AuthenticationException e) {
        log.warn("Authentication failed: {}", e.getMessage());
        Result<?> result = Result.error(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("Unexpected error", e);
        Result<?> result = Result.error(ErrorCode.UNKNOWN_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
```

- [ ] **Step 2.6: 编写单元测试**

```java
// src/test/java/com/ulticode/common/response/ResultTest.java
package com.ulticode.common.response;

import com.ulticode.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void success_withData_shouldReturnCorrectResult() {
        String data = "test data";
        Result<String> result = Result.success(data);

        assertEquals(0, result.getCode());
        assertEquals("ok", result.getMessage());
        assertEquals(data, result.getData());
        assertNotNull(result.getTraceId());
        assertTrue(result.getTraceId().startsWith("t-"));
    }

    @Test
    void success_withoutData_shouldReturnNullData() {
        Result<Void> result = Result.success();

        assertEquals(0, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void error_withCodeAndMessage_shouldReturnCorrectResult() {
        Result<Void> result = Result.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());

        assertEquals(20001, result.getCode());
        assertEquals("User not found", result.getMessage());
        assertNull(result.getData());
        assertNotNull(result.getTraceId());
    }
}
```

- [ ] **Step 2.7: 运行测试**

```bash
cd backend-spring && ./mvnw test -Dtest=ResultTest
```

Expected: Tests run: 3, Failures: 0

- [ ] **Step 2.8: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/common/
git add backend-spring/src/test/java/com/ulticode/common/
git commit -m "feat: 添加统一响应格式和错误码定义"
```

---

## Task 3: Spring Security 和 JWT 认证配置

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/security/jwt/JwtProperties.java`
- Create: `backend-spring/src/main/java/com/ulticode/security/jwt/JwtTokenProvider.java`
- Create: `backend-spring/src/main/java/com/ulticode/security/jwt/JwtAuthenticationFilter.java`
- Create: `backend-spring/src/main/java/com/ulticode/security/UserDetailsServiceImpl.java`
- Create: `backend-spring/src/main/java/com/ulticode/security/AuthenticationEntryPointImpl.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/config/WebConfig.java`

### Step 3.1: 创建 JwtProperties 配置类

```java
// src/main/java/com/ulticode/security/jwt/JwtProperties.java
package com.ulticode.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private Long accessTokenExpiration;
    private Long refreshTokenExpiration;
    private CookieConfig cookie = new CookieConfig();

    @Data
    public static class CookieConfig {
        private String name = "access_token";
        private Boolean httpOnly = true;
        private Boolean secure = true;
        private String sameSite = "strict";
        private Integer maxAge = 86400;
    }
}
```

- [ ] **Step 3.2: 创建 JwtTokenProvider**

```java
// src/main/java/com/ulticode/security/jwt/JwtTokenProvider.java
package com.ulticode.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey getSecretKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String userId, String username, String role) {
        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiration()))
            .signWith(getSecretKey())
            .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiration()))
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

    public String getUserIdFromToken(String token) {
        return parseToken(token).getSubject();
    }

    public String getUsernameFromToken(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }
}
```

- [ ] **Step 3.3: 创建 JwtAuthenticationFilter**

```java
// src/main/java/com/ulticode/security/jwt/JwtAuthenticationFilter.java
package com.ulticode.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            String username = jwtTokenProvider.getUsernameFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                );

            authentication.setDetails(username);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Set authentication for user: {}", username);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Try to get from cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (jwtProperties.getCookie().getName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // 2. Try to get from Authorization header
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
```

- [ ] **Step 3.4: 创建 UserDetailsServiceImpl**

```java
// src/main/java/com/ulticode/security/UserDetailsServiceImpl.java
package com.ulticode.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // TODO: Implement actual user loading from database
        // For now, return a placeholder user
        return new User(
            username,
            "",
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
```

- [ ] **Step 3.5: 创建 AuthenticationEntryPointImpl**

```java
// src/main/java/com/ulticode/security/AuthenticationEntryPointImpl.java
package com.ulticode.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Result<?> result = Result.error(ErrorCode.UNAUTHORIZED);
        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
```

- [ ] **Step 3.6: 创建 SecurityConfig**

```java
// src/main/java/com/ulticode/common/config/SecurityConfig.java
package com.ulticode.common.config;

import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh",
                    "/api/problems",
                    "/api/problems/**"
                ).permitAll()
                // Swagger endpoints
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).permitAll()
                // Health check
                .requestMatchers("/actuator/**").permitAll()
                // WebSocket
                .requestMatchers("/ws/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 3.7: 创建 WebConfig (CORS 配置)**

```java
// src/main/java/com/ulticode/common/config/WebConfig.java
package com.ulticode.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "http://localhost:9002",
            "http://localhost:9003",
            "http://127.0.0.1:9002",
            "http://127.0.0.1:9003"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

- [ ] **Step 3.8: 验证配置可编译**

```bash
cd backend-spring && ./mvnw clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3.9: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/security/
git add backend-spring/src/main/java/com/ulticode/common/config/
git commit -m "feat: 添加 Spring Security 和 JWT 认证配置"
```

---

## Task 4: MyBatis-Plus 配置

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/common/config/MybatisPlusConfig.java`

### Step 4.1: 创建 MybatisPlusConfig

```java
// src/main/java/com/ulticode/common/config/MybatisPlusConfig.java
package com.ulticode.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
@MapperScan("com.ulticode.modules.*.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AutoFillMetaObjectHandler();
    }

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

- [ ] **Step 4.2: 验证编译**

```bash
cd backend-spring && ./mvnw clean compile
```

- [ ] **Step 4.3: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/common/config/MybatisPlusConfig.java
git commit -m "feat: 添加 MyBatis-Plus 配置"
```

---

## Task 5: Redis 和 Redisson 配置

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/common/config/RedisConfig.java`
- Create: `backend-spring/src/main/java/com/ulticode/infrastructure/redis/RedisService.java`
- Create: `backend-spring/src/main/java/com/ulticode/infrastructure/redis/CacheConstants.java`

### Step 5.1: 创建 RedisConfig

```java
// src/main/java/com/ulticode/common/config/RedisConfig.java
package com.ulticode.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

- [ ] **Step 5.2: 创建 CacheConstants**

```java
// src/main/java/com/ulticode/infrastructure/redis/CacheConstants.java
package com.ulticode.infrastructure.redis;

public final class CacheConstants {
    private CacheConstants() {}

    // Refresh token cache (userId -> refreshToken)
    public static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    // User cache
    public static final String USER_CACHE_PREFIX = "user:";
    public static final long USER_CACHE_TTL = 3600; // 1 hour

    // Problem cache
    public static final String PROBLEM_CACHE_PREFIX = "problem:";
    public static final long PROBLEM_CACHE_TTL = 7200; // 2 hours

    // Submission rate limit
    public static final String SUBMISSION_RATE_LIMIT_PREFIX = "submission_rate:";
    public static final long SUBMISSION_RATE_LIMIT_TTL = 60; // 1 minute

    // Global ranking cache
    public static final String GLOBAL_RANKING_CACHE = "global_ranking";
    public static final long GLOBAL_RANKING_CACHE_TTL = 300; // 5 minutes
}
```

- [ ] **Step 5.3: 创建 RedisService**

```java
// src/main/java/com/ulticode/infrastructure/redis/RedisService.java
package com.ulticode.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null && clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return null;
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    // Hash operations
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public void hDelete(String key, Object... fields) {
        redisTemplate.opsForHash().delete(key, fields);
    }
}
```

- [ ] **Step 5.4: 验证编译**

```bash
cd backend-spring && ./mvnw clean compile
```

- [ ] **Step 5.5: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/common/config/RedisConfig.java
git add backend-spring/src/main/java/com/ulticode/infrastructure/redis/
git commit -m "feat: 添加 Redis 配置和 RedisService"
```

---

## Task 6: SpringDoc API 文档配置

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/common/config/SwaggerConfig.java`

### Step 6.1: 创建 SwaggerConfig

```java
// src/main/java/com/ulticode/common/config/SwaggerConfig.java
package com.ulticode.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("UltiCode API")
                .description("UltiCode 在线编程练习与个性化推荐平台 API 文档")
                .version("1.0.0")
                .contact(new Contact()
                    .name("UltiCode Team")
                    .email("support@ulticode.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer"))
            .components(new Components()
                .addSecuritySchemes("Bearer", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT 认证，格式: Bearer {token}")));
    }
}
```

- [ ] **Step 6.2: 验证编译**

```bash
cd backend-spring && ./mvnw clean compile
```

- [ ] **Step 6.3: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/common/config/SwaggerConfig.java
git commit -m "feat: 添加 SpringDoc API 文档配置"
```

---

## Task 7: 基础工具类

**Files:**
- Create: `backend-spring/src/main/java/com/ulticode/common/util/SecurityUtil.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/annotation/CurrentUser.java`
- Create: `backend-spring/src/main/java/com/ulticode/common/annotation/RequireRole.java`

### Step 7.1: 创建 SecurityUtil

```java
// src/main/java/com/ulticode/common/util/SecurityUtil.java
package com.ulticode.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {
    private SecurityUtil() {}

    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getDetails() != null) {
            return authentication.getDetails().toString();
        }
        return null;
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getPrincipal());
    }

    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role));
    }
}
```

- [ ] **Step 7.2: 创建 CurrentUser 注解**

```java
// src/main/java/com/ulticode/common/annotation/CurrentUser.java
package com.ulticode.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
```

- [ ] **Step 7.3: 创建 RequireRole 注解**

```java
// src/main/java/com/ulticode/common/annotation/RequireRole.java
package com.ulticode.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String[] value() default {};
}
```

- [ ] **Step 7.4: 验证编译**

```bash
cd backend-spring && ./mvnw clean compile
```

- [ ] **Step 7.5: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/common/util/
git add backend-spring/src/main/java/com/ulticode/common/annotation/
git commit -m "feat: 添加基础工具类和自定义注解"
```

---

## Task 8: 验证和启动测试

### Step 8.1: 完整编译测试

```bash
cd backend-spring && ./mvnw clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 8.2: 运行所有测试**

```bash
cd backend-spring && ./mvnw test
```

Expected: Tests run: X, Failures: 0, Errors: 0

- [ ] **Step 8.3: 启动应用**

```bash
cd backend-spring && ./mvnw spring-boot:run
```

Expected: Application starts successfully on port 3000

- [ ] **Step 8.4: 验证 Swagger UI**

访问: http://localhost:3000/swagger-ui.html

Expected: Swagger UI 页面正常显示

- [ ] **Step 8.5: Final Commit**

```bash
git add -A
git commit -m "feat: 完成 Phase 1 基础设施层搭建

- 项目结构和依赖配置
- 统一响应格式和错误码
- Spring Security 和 JWT 认证
- MyBatis-Plus 配置
- Redis 和 Redisson 配置
- SpringDoc API 文档
- 基础工具类和注解"
```

---

## 验收检查清单

- [ ] 项目可通过 `./mvnw clean compile` 编译
- [ ] 项目可通过 `./mvnw test` 通过所有测试
- [ ] 项目可通过 `./mvnw spring-boot:run` 启动
- [ ] 访问 `/swagger-ui.html` 显示 API 文档
- [ ] 访问 `/api-docs` 返回 OpenAPI JSON
- [ ] 统一响应格式包含 `code`, `message`, `data`, `traceId`
- [ ] 错误码与 NestJS 完全一致

---

## 下一阶段

完成 Phase 1 后，继续执行:
- Phase 2: 核心业务模块 (User, Auth, Problem, Submission, Solution)
- 参考: `docs/superpowers/plans/2026-03-21-phase2-core-modules.md`
