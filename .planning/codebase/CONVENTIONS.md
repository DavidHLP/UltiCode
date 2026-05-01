# UltiCode 代码规范 (CONVENTIONS)

本文档定义 UltiCode 项目的代码规范，确保前后端代码保持一致的风格。

## 1. 技术栈概览

| Layer | Language | Framework/Tool |
|-------|----------|----------------|
| Backend | Java 17 | Spring Boot 3.2.5, MyBatis-Plus |
| Recommendation | Java 17 | Dubbo3, Spark |
| Console Frontend | TypeScript | Vue 3, Vite, Tailwind CSS v4 |
| Management Frontend | TypeScript | Vue 3, Vite, Tailwind CSS v4 |

---

## 2. 后端规范 (Java/Spring Boot)

### 2.1 项目结构

```
backend-spring/src/main/java/com/ulticode/
├── common/           # 共享工具、配置、异常
│   ├── response/     # Result 封装、PageResult
│   ├── exception/    # GlobalExceptionHandler、BusinessException
│   ├── config/       # SecurityConfig、Web配置、RedisConfig
│   ├── annotation/   # @CurrentUser、@RequireRole、@RateLimit
│   └── dto/          # 通用 DTO
├── security/         # JWT 过滤器、CSRF 服务
├── modules/          # 功能模块 (26个)
│   ├── auth/         # 认证、登录、OAuth
│   ├── user/         # 用户 CRUD、profile
│   ├── problem/      # 题目、测试用例、示例
│   ├── submission/   # 代码提交、评判
│   ├── contest/      # 竞赛、排名
│   └── ...           # 其他领域模块
└── websocket/        # 实时通信
```

### 2.2 模块标准结构

每个业务模块遵循以下结构:

```
module/
├── controller/          # REST 控制器
│   └── XxxController.java
├── dto/                 # 数据传输对象
│   ├── XxxCreateDTO.java
│   ├── XxxUpdateDTO.java
│   └── XxxVO.java
├── entity/             # 数据库实体
│   └── Xxx.java
├── mapper/              # MyBatis Mapper
│   └── XxxMapper.java
└── service/             # 业务逻辑
    ├── XxxService.java
    └── impl/
        └── XxxServiceImpl.java
```

### 2.3 类命名模式

| 类型 | 命名规则 | 示例 |
|------|----------|------|
| Entity | `PascalCase` | `User`, `Problem`, `Submission` |
| DTO (请求) | `{Name}DTO` | `LoginDTO`, `CreateSubmissionDTO` |
| DTO (响应) | `{Name}VO` | `UserVO`, `ProblemVO` |
| Controller | `{Name}Controller` | `AuthController`, `ProblemController` |
| Service | `{Name}Service` | `AuthService`, `ProblemService` |
| ServiceImpl | `{Name}ServiceImpl` | `AuthServiceImpl` |
| Mapper | `{Name}Mapper` | `UserMapper`, `ProblemMapper` |
| Config | `{Name}Config` | `SecurityConfig`, `RedisConfig` |
| Properties | `{Name}Properties` | `JwtProperties` |

### 2.4 Entity 规范

- 使用 `@Data` (Lombok)
- 使用 `@TableName` 指定表名
- 主键使用 `@TableId(type = IdType.INPUT)`
- 审计字段: `createdBy`, `updatedBy`, `createdAt`, `updatedAt`
- 软删除使用 `@TableLogic`
- 数据库列名使用蛇形命名，Java 字段使用驼峰命名

```java
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("joined_at")
    private LocalDateTime joinedAt;

    @TableField("is_deleted")
    @TableLogic
    private Integer isDeleted;
}
```

### 2.5 DTO 规范

**请求 DTO (DTO)**:
- 使用 `@Data` (Lombok)
- 使用 `@NotBlank`、`@NotNull` 等 Jakarta Validation 注解
- 字段注释使用 Javadoc `/** ... */`

```java
@Data
public class LoginDTO {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

**响应 DTO (VO)**:
- 使用 `@Data` + `@JsonInclude(JsonInclude.Include.NON_NULL)`
- 排除敏感字段（如 password）

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserVO {
    private String id;
    private String username;
    private String email;  // 仅对本人或管理员显示
}
```

### 2.6 Controller 规范

- 使用 `@Tag` 定义 Swagger 标签
- 使用 `@Operation` 定义接口描述
- 使用 `@ApiResponse` 定义响应
- 返回类型统一使用 `Result<T>`
- 使用 `@Valid` 验证请求体

```java
@Tag(name = "Auth", description = "Authentication endpoints")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    @Operation(summary = "Login", description = "Authenticate user")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }
}
```

### 2.7 API 路径规范

- 资源命名: 复数名词 (如 `/problems`, `/users`)
- 嵌套资源: `/admin/problems/{id}/versions`
- 动名词: `/problems/{id}/publish`
- 版本控制: 当前无版本前缀

### 2.8 响应格式

所有 API 响应使用 `Result<T>` 封装:

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1234567890"
}
```

- `code: 0` = 成功
- `code: 非0` = 错误

分页响应使用 `PageResult<T>`:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "totalPages": 5
  }
}
```

---

## 3. 前端规范 (Vue 3/TypeScript)

### 3.1 项目结构

```
console/src/          # 用户控制台 (端口 9002)
management/src/       # 管理后台 (端口 9003)
├── api/              # API 客户端
├── components/       # Vue 组件
├── composables/      # 组合式函数
├── stores/           # Pinia 状态管理
├── types/            # TypeScript 类型定义
├── utils/            # 工具函数
└── views/            # 页面组件
```

### 3.2 ESLint 配置

- 使用 `eslint.config.ts` (Flat Config 格式)
- 使用 `@vue/eslint-config-typescript` + `eslint-plugin-vue`
- TypeScript: `vue-tsconfig/recommended`
- Prettier: `@vue/eslint-config-prettier/skip-formatting`

**版本差异**:

| 包 | Console | Management |
|----|---------|------------|
| ESLint | 9.x | 10.x |
| eslint-plugin-vue | ^9.30.0 | ~10.8.0 |

**Vue 组件命名规则**:

| 项目 | 规则 |
|------|------|
| Console | `vue/multi-word-component-names` 有组件白名单 (Alert, Avatar, Badge, Button, Card, Dialog, Input, Table 等 50+ 组件) |
| Management | `vue/multi-word-component-names: 'off'` (禁用) |

### 3.3 代码格式化

格式化由 Prettier 处理，通过 `@vue/eslint-config-prettier/skip-formatting` 跳过 ESLint 格式化。

Prettier 配置 (隐式默认):
```json
{
  "semi": false,
  "singleQuote": true,
  "printWidth": 100
}
```

### 3.4 TypeScript 类型命名

| 类型 | 命名规则 | 示例 |
|------|----------|------|
| API 类型 | `{Name}DTO`, `{Name}VO` | `LoginRequest`, `UserResponse` |
| Store 类型 | `use{Name}Store` | `useAuthStore` |
| 组件 Props | `{Name}Props` | `ButtonProps` |
| API 响应 | `PageResult<T>` | `PageResult<Problem>` |

### 3.5 API 客户端规范

使用统一的 `apiGet` / `apiPost` / `apiPatch` / `apiDelete` 方法:

```typescript
// console/src/api/auth.ts
export const authApi = {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    return apiPost<LoginResponse>("/auth/login", credentials);
  },

  async getCurrentUser(): Promise<User> {
    const response = await apiGet<UserWithCsrfResponse>("/auth/me");
    return response.user;
  },
};
```

**请求工具特性**:
- 自动解封 `Result<T>` 响应
- 自动附加 CSRF Token (POST/PUT/PATCH/DELETE)
- 自动处理 `X-Request-ID` 请求追踪
- 请求去重 (非认证关键接口)
- 自动重试 (5xx 错误)

### 3.6 组件规范

- 使用 `<script setup lang="ts">`
- Props 使用 TypeScript 类型定义
- 事件使用 `defineEmits`
- 样式使用 Tailwind CSS v4

### 3.7 路径别名

```typescript
// vite.config.ts
resolve: {
  alias: {
    '@': fileURLToPath(new URL('./src', import.meta.url)),
  },
}
```

### 3.8 CSS 设计规范

- **颜色空间**: 仅使用 OKLCH，禁止 hex/HSL
- **主题切换**: 根元素添加 `.dark` 类
- **CSS**: Tailwind CSS v4 使用 `@theme inline`
- **UI 组件**: shadcn-vue (new-york style) + Radix Vue + Lucide icons
- **圆角**: `--radius: 0` (全直角)
- **图表颜色**: light/dark 不变，仅 grid/tooltip 在主题间变化

---

## 4. Git 提交规范

### 4.1 提交信息格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 4.2 Type 列表

| Type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `style` | 代码格式 (不影响功能) |
| `refactor` | 重构 (非新功能非修复) |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具相关 |
| `ci` | CI 配置 |

### 4.3 Scope 范围

| Scope | 说明 |
|-------|------|
| `console` | 用户控制台 |
| `management` | 管理后台 |
| `backend` | Spring Boot 后端 |
| `auth` | 认证模块 |
| `problem` | 题目模块 |
| `submission` | 提交模块 |
| `contest` | 竞赛模块 |
| `solution` | 题解模块 |
| `(planning)` | 规划文档 |

### 4.4 提交示例

```
feat(auth): add password reset functionality

fix(submission): handle null pointer in GROUP BY query

docs(planning): update codebase docs from gsd-map-codebase

refactor(management): migrate router guard to Vue Router 4 style
```

---

## 5. 注释和文档规范

### 5.1 Java 注释

- 类和公共方法使用 Javadoc:
```java
/**
 * User View Object for API responses.
 * Excludes sensitive data like password.
 */
@Data
public class UserVO { }
```

- 字段注释:
```java
/**
 * User unique identifier (UUID)
 */
private String id;
```

### 5.2 TypeScript 注释

- 函数/方法使用块注释:
```typescript
/**
 * Authentication API methods
 */
export const authApi = { }

/**
 * Login with username and password
 * POST /auth/login → Result<LoginResponse>
 * Returns: { csrfToken: string, user: User }
 */
async login(credentials: LoginRequest): Promise<LoginResponse> { }
```

### 5.3 行内注释

- 复杂逻辑添加说明
- 避免显而易见的注释

---

## 6. 数据库规范

- **ORM**: MyBatis-Plus
- **迁移**: Flyway (通过 `db-manager` Python CLI 管理)
- **命名**: MySQL 列使用蛇形命名，Java 字段使用驼峰命名
- **迁移位置**: `db-manager/migrations/`

### 迁移文件命名

```
V{version}__{description}.sql
```

示例: `V1__users_and_submissions.sql`

---

## 7. 测试规范

### 7.1 测试文件位置

| 层级 | 位置 |
|------|------|
| Backend | `backend-spring/src/test/java/com/ulticode/` |
| Frontend Console | `console/src/**/*.spec.ts` |
| Frontend Management | `management/src/**/*.spec.ts` |

### 7.2 测试命名

- Java: `XxxTest.java`, `XxxImplTest.java`, `XxxIT.java` (集成测试)
- TypeScript: `Xxx.spec.ts`

### 7.3 后端测试模式

- 使用 `@ExtendWith(MockitoExtension.class)`
- 使用 `@Nested` + `@DisplayName` 组织测试
- 使用 AssertJ 流式断言
- 集成测试以 `*IT.java` 结尾，使用 Testcontainers

### 7.4 前端测试模式

- 使用 Vitest
- 使用 `vi.mock()` 模拟依赖
- 使用 `@vue/test-utils` 测试 Vue 组件
- jsdom 环境模拟浏览器 DOM

---

## 8. 配置文件命名

### 8.1 后端

| 文件 | 说明 |
|------|------|
| `.env` | 环境变量 (根目录) |
| `pom.xml` | Maven 依赖 |
| `application.yml` | Spring 配置 |

### 8.2 前端

| 文件 | 说明 |
|------|------|
| `vite.config.ts` | Vite 构建配置 |
| `vitest.config.ts` | Vitest 测试配置 |
| `eslint.config.ts` | ESLint 配置 (Flat Config) |
| `tsconfig.json` | TypeScript 配置 |

---

## 9. 端口规范

| Service | Port |
|---------|------|
| Backend (Spring) | 9001 |
| Console | 9002 |
| Management | 9003 |
| Recommend-Provider | 9004 |
| Recommend-Web | 9005 |
| MySQL | 23306 |
| Redis | 26379 |
| Nacos | 28848 |

---

## 10. 环境变量规范

**根目录 `.env`** 是唯一真实来源 (非 `backend-spring/.env`)。

| 变量 | 说明 |
|------|------|
| `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` | MySQL 连接 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis 连接 |
| `JWT_SECRET` | JWT 密钥 (最少 32 字符) |
| `NACOS_PORT` | Nacos 端口 (推荐服务) |
| `VITE_API_BASE_URL` | 前端 API 地址 |
