# NestJS 到 Spring Boot 迁移 - Phase 2: 核心业务模块

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现核心业务模块，包括 User、Auth、Problem、Submission、Solution，使前端可以进行基本的功能测试。

**Architecture:** 采用标准的 Controller → Service → Mapper 三层架构，使用 MyBatis-Plus 进行数据访问。

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus 3.5.5, Spring Security, jjwt 0.12.5

---

## 前置条件

- Phase 1 基础设施层已完成
- 数据库已存在 (与 NestJS 共享)
- Redis 已配置

---

## 模块概览

| 模块 | 功能 | API 端点数量 |
|------|------|-------------|
| User | 用户 CRUD、个人中心 | 8 |
| Auth | 登录、注册、Token 刷新 | 5 |
| Problem | 题目 CRUD、标签、测试用例 | 10 |
| Submission | 代码提交、结果查询 | 4 |
| Solution | 题解 CRUD、评论 | 8 |

---

## Task 1: User 模块

**Files:**
- Create: `src/main/java/com/ulticode/modules/user/entity/User.java`
- Create: `src/main/java/com/ulticode/modules/user/mapper/UserMapper.java`
- Create: `src/main/java/com/ulticode/modules/user/service/UserService.java`
- Create: `src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/user/controller/UserController.java`
- Create: `src/main/java/com/ulticode/modules/user/dto/UserDTO.java`
- Create: `src/main/java/com/ulticode/modules/user/dto/UserVO.java`
- Create: `src/main/java/com/ulticode/modules/user/dto/UpdateUserDTO.java`
- Test: `src/test/java/com/ulticode/modules/user/service/UserServiceTest.java`

### Step 1.1: 创建 User Entity

参照 NestJS Prisma schema 中的 User 模型:

```java
package com.ulticode.modules.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.ASSIGN_UUID)  // 使用 UUID 与 NestJS 兼容
    private String id;

    private String username;
    private String name;
    private String email;
    private String avatar;
    private String password;
    private String bio;
    private String company;
    private String github;
    private LocalDateTime joinedAt;
    private String location;
    private String twitter;
    private String website;
    private String preferredLanguage;
    private String role; // USER, MODERATOR, ADMIN, SUPER_ADMIN
    private Boolean isActive;
    private Boolean isBanned;
    private LocalDateTime bannedUntil;
    private String bannedReason;
    private LocalDateTime lastLoginAt;
    private String createdBy;
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean isDeleted;
}
```

### Step 1.2: 创建 UserMapper

```java
package com.ulticode.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

### Step 1.3: 创建 DTO 和 VO

```java
// UserVO.java
package com.ulticode.modules.user.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private String id;
    private String username;
    private String name;
    private String email;
    private String avatar;
    private String bio;
    private String company;
    private String github;
    private LocalDateTime joinedAt;
    private String location;
    private String twitter;
    private String website;
    private String preferredLanguage;
    private String role;
}

// UpdateUserDTO.java
package com.ulticode.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserDTO {
    @Size(max = 120)
    private String name;

    @Email
    private String email;

    private String avatar;
    private String bio;
    private String company;
    private String github;
    private String location;
    private String twitter;
    private String website;
    private String preferredLanguage;
}
```

### Step 1.4: 创建 UserService

```java
package com.ulticode.modules.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;

public interface UserService {
    User findById(String id);
    User findByUsername(String username);
    User findByEmail(String email);
    UserVO getCurrentUser(String userId);
    UserVO updateCurrentUser(String userId, UpdateUserDTO dto);
    Page<UserVO> listUsers(int page, int pageSize);
    void updateLastLoginAt(String userId);
}
```

### Step 1.5: 创建 UserServiceImpl

```java
package com.ulticode.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public User findById(String id) {
        return userMapper.selectById(id);
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
    }

    @Override
    public User findByEmail(String email) {
        return userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
    }

    @Override
    public UserVO getCurrentUser(String userId) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO updateCurrentUser(String userId, UpdateUserDTO dto) {
        User user = findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (dto.getName() != null) user.setName(dto.getName());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getBio() != null) user.setBio(dto.getBio());
        if (dto.getCompany() != null) user.setCompany(dto.getCompany());
        if (dto.getGithub() != null) user.setGithub(dto.getGithub());
        if (dto.getLocation() != null) user.setLocation(dto.getLocation());
        if (dto.getTwitter() != null) user.setTwitter(dto.getTwitter());
        if (dto.getWebsite() != null) user.setWebsite(dto.getWebsite());
        if (dto.getPreferredLanguage() != null) user.setPreferredLanguage(dto.getPreferredLanguage());

        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    public Page<UserVO> listUsers(int page, int pageSize) {
        Page<User> userPage = userMapper.selectPage(
            new Page<>(page, pageSize),
            new LambdaQueryWrapper<User>()
                .eq(User::getIsActive, true)
                .orderByDesc(User::getJoinedAt)
        );

        Page<UserVO> voPage = new Page<>(page, pageSize, userPage.getTotal());
        List<UserVO> voList = userPage.getRecords().stream()
            .map(this::toVO)
            .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional
    public void updateLastLoginAt(String userId) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setCompany(user.getCompany());
        vo.setGithub(user.getGithub());
        vo.setJoinedAt(user.getJoinedAt());
        vo.setLocation(user.getLocation());
        vo.setTwitter(user.getTwitter());
        vo.setWebsite(user.getWebsite());
        vo.setPreferredLanguage(user.getPreferredLanguage());
        vo.setRole(user.getRole());
        return vo;
    }
}
```

### Step 1.6: 创建 UserController

```java
package com.ulticode.modules.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "用户管理接口")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前用户信息", security = @SecurityRequirement(name = "Bearer"))
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        String userId = SecurityUtil.getCurrentUserId();
        UserVO user = userService.getCurrentUser(userId);
        return Result.success(user);
    }

    @Operation(summary = "更新当前用户信息", security = @SecurityRequirement(name = "Bearer"))
    @PatchMapping("/me")
    public Result<UserVO> updateCurrentUser(@Valid @RequestBody UpdateUserDTO dto) {
        String userId = SecurityUtil.getCurrentUserId();
        UserVO user = userService.updateCurrentUser(userId, dto);
        return Result.success(user);
    }

    @Operation(summary = "获取用户列表")
    @GetMapping
    public Result<PageResult<UserVO>> listUsers(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        Page<UserVO> userPage = userService.listUsers(page, pageSize);
        PageResult<UserVO> result = PageResult.of(
            userPage.getRecords(),
            userPage.getTotal(),
            (int) userPage.getCurrent(),
            (int) userPage.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable String id) {
        UserVO user = userService.getCurrentUser(id);
        return Result.success(user);
    }
}
```

- [ ] **Step 1.7: 编写单元测试**
- [ ] **Step 1.8: 运行测试验证**
- [ ] **Step 1.9: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/user/
git add backend-spring/src/test/java/com/ulticode/modules/user/
git commit -m "feat(user): 添加 User 模块"
```

---

## Task 2: Auth 模块

**Files:**
- Create: `src/main/java/com/ulticode/modules/auth/controller/AuthController.java`
- Create: `src/main/java/com/ulticode/modules/auth/service/AuthService.java`
- Create: `src/main/java/com/ulticode/modules/auth/service/impl/AuthServiceImpl.java`
- Create: `src/main/java/com/ulticode/modules/auth/dto/LoginDTO.java`
- Create: `src/main/java/com/ulticode/modules/auth/dto/RegisterDTO.java`
- Create: `src/main/java/com/ulticode/modules/auth/dto/LoginResponse.java`

### Step 2.1: 创建 Auth DTOs

```java
// LoginDTO.java
package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}

// RegisterDTO.java
package com.ulticode.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank
    @Size(min = 3, max = 120)
    private String username;

    @NotBlank
    @Size(min = 6, max = 255)
    private String password;

    @Email
    private String email;
}

// LoginResponse.java
package com.ulticode.modules.auth.dto;

import com.ulticode.modules.user.dto.UserVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String csrfToken;
    private UserVO user;
}
```

### Step 2.2: 创建 AuthService

```java
package com.ulticode.modules.auth.service;

import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    LoginResponse login(LoginDTO dto, HttpServletResponse response);
    LoginResponse register(RegisterDTO dto, HttpServletResponse response);
    LoginResponse refresh(String refreshToken, HttpServletResponse response);
    void logout(String userId, HttpServletResponse response);
}
```

### Step 2.3: 实现 AuthServiceImpl

```java
package com.ulticode.modules.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.*;
import com.ulticode.modules.auth.service.AuthService;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public LoginResponse login(LoginDTO dto, HttpServletResponse response) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );

        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!user.getIsActive() || user.getIsBanned()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
            user.getId(), user.getUsername(), user.getRole()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Set cookie
        setAccessTokenCookie(response, accessToken);

        // Update last login
        userService.updateLastLoginAt(user.getId());

        // Build response
        UserVO userVO = userService.getCurrentUser(user.getId());
        return LoginResponse.builder()
            .csrfToken(IdUtil.simpleUUID())
            .user(userVO)
            .build();
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterDTO dto, HttpServletResponse response) {
        // Check username
        if (userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        ) > 0) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_TAKEN);
        }

        // Check email
        if (dto.getEmail() != null && userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail())
        ) > 0) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
        }

        // Create user
        User user = new User();
        user.setId(IdUtil.fastSimpleUUID());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now());

        userMapper.insert(user);

        return login(new LoginDTO(dto.getUsername(), dto.getPassword()), response);
    }

    @Override
    public LoginResponse refresh(String refreshToken, HttpServletResponse response) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userService.findById(userId);

        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(
            user.getId(), user.getUsername(), user.getRole()
        );

        setAccessTokenCookie(response, accessToken);

        UserVO userVO = userService.getCurrentUser(user.getId());
        return LoginResponse.builder()
            .csrfToken(IdUtil.simpleUUID())
            .user(userVO)
            .build();
    }

    @Override
    public void logout(String userId, HttpServletResponse response) {
        // Clear cookie
        Cookie cookie = new Cookie(jwtProperties.getCookie().getName(), null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(jwtProperties.getCookie().getName(), token);
        cookie.setPath("/");
        cookie.setHttpOnly(jwtProperties.getCookie().getHttpOnly());
        cookie.setSecure(jwtProperties.getCookie().getSecure());
        cookie.setMaxAge(jwtProperties.getCookie().getMaxAge());
        response.addCookie(cookie);
    }
}
```

### Step 2.4: 创建 AuthController

```java
package com.ulticode.modules.auth.controller;

import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import com.ulticode.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "认证接口")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(
        @Valid @RequestBody LoginDTO dto,
        HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.login(dto, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginResponse> register(
        @Valid @RequestBody RegisterDTO dto,
        HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.register(dto, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(
        @CookieValue(value = "refresh_token", required = false) String refreshToken,
        HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.refresh(refreshToken, response);
        return Result.success(loginResponse);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        String userId = SecurityUtil.getCurrentUserId();
        authService.logout(userId, response);
        return Result.success();
    }
}
```

- [ ] **Step 2.5: 编写单元测试**
- [ ] **Step 2.6: 运行测试验证**
- [ ] **Step 2.7: Commit**

```bash
git add backend-spring/src/main/java/com/ulticode/modules/auth/
git commit -m "feat(auth): 添加 Auth 认证模块"
```

---

## Task 3-5: Problem, Submission, Solution 模块

(详细步骤与 User/Auth 模块类似，参照 NestJS 实现逐个迁移)

每个模块遵循相同的模式:
1. 创建 Entity (参照 Prisma schema)
2. 创建 Mapper
3. 创建 DTO 和 VO
4. 创建 Service 接口和实现
5. 创建 Controller
6. 编写测试
7. Commit

---

## 验收检查清单

- [ ] User 模块: 用户 CRUD 正常
- [ ] Auth 模块: 登录/注册/退出正常
- [ ] Problem 模块: 题目 CRUD 正常
- [ ] Submission 模块: 代码提交正常
- [ ] Solution 模块: 题解 CRUD 正常
- [ ] 所有 API 返回格式与 NestJS 兼容
- [ ] 所有错误码与 NestJS 一致
