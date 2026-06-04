# UltiCode Spring Boot 项目规则

> 本文件补充全局 ECC Java 规则，定义 UltiCode 后端项目特有的架构约定和模式。
> 与项目现有风格冲突时，以此文件为准。

## 适用范围

`backend-spring/` 目录下所有 Java 代码。

---

## 架构分层

项目遵循 `Controller → Service → Mapper (MyBatis-Plus) → Entity` 分层：

```
com.ulticode.modules.{module}/
├── controller/      # REST 端点，参数校验，调用 Service
├── service/         # 业务逻辑接口
│   └── impl/        # 业务逻辑实现
├── mapper/          # MyBatis-Plus Mapper 接口
├── entity/          # DO 类（与数据库表一一对应）
└── dto/             # DTO 类（数据传输对象）
```

### 层间规则

1. **Controller 层**：仅处理 HTTP 协议相关逻辑（参数绑定、权限注解、响应封装），不包含业务逻辑。
2. **Service 层**：所有业务逻辑在 Service 中实现。Controller 调用 Service，Service 调用 Mapper。
3. **Mapper 层**：继承 `BaseMapper<T>` 提供标准 CRUD。自定义查询使用 `@Select`/`@Update` 等注解。
4. **禁止跨层调用**：Controller 不得直接调用 Mapper；Service 不得返回 Entity 给 Controller（应通过 DTO/VO 转换）。

---

## 响应封装

所有 API 响应必须使用 `Result<T>` 封装：

```java
// 成功响应
return Result.success(data);
return Result.success();

// 错误响应
return Result.error(code, message);
throw new BusinessException(ErrorCode.XXX);
```

### Result 结构

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "traceId": "t-1717000000000"
}
```

- `code = 0` 表示成功，非零表示错误
- `traceId` 用于请求追踪
- 分页响应使用 `PageResult.of(list, total, page, limit)`

### 错误处理

- 业务异常：抛出 `BusinessException(ErrorCode.XXX)`
- 参数校验异常：由 `GlobalExceptionHandler` 自动处理 `@Valid` 注解触发的异常
- 禁止在 Controller 中 try-catch 业务异常，统一由 `GlobalExceptionHandler` 处理

---

## DTO 映射

使用 MapStruct 进行 Entity ↔ DTO 转换：

```java
@Mapper(componentModel = "spring")
public interface ProblemMapper {
    ProblemDTO toDto(Problem entity);
    Problem toEntity(ProblemDTO dto);
}
```

### 命名约定

- 数据传输对象：`XxxDTO`（Service/Controller 间传输）
- 展示对象：`XxxVO`（返回给前端）
- 查询对象：`XxxQuery`（接收查询参数）
- **注意**：项目 DTO 和 VO 之间界限不严格，部分模块混用。新增时优先区分 DTO（输入）和 VO（输出）。

---

## 安全注解

项目提供以下自定义注解，Controller 方法按需使用：

| 注解 | 用途 | 示例 |
|------|------|------|
| `@RequireRole("ADMIN")` | 角色校验 | 管理员接口 |
| `@CheckBan` | 封禁检查 | 用户操作接口 |
| `@RateLimit` | 速率限制 | 敏感操作接口 |
| `@Audited` | 操作审计 | 关键变更接口 |
| `@CurrentUser` | 当前用户注入 | 获取当前用户信息 |

---

## 数据库映射

### Entity 约定

- 所有 Entity 使用 Lombok `@Data`
- 主键类型 `String`（UUID），非自增
- 必备字段：`id`, `create_time`, `update_time`
- 逻辑删除字段：`is_deleted`（Entity 属性名 `deleted`，Mapper 中映射 `is_deleted`）
- 布尔字段：数据库 `is_xxx`，Entity 属性名 `xxx`（去掉 is_ 前缀）

### MyBatis-Plus 使用

- 标准 CRUD：直接使用 `BaseMapper<T>` 提供的方法
- 自定义查询：使用 `@Select`/`@Insert`/`@Update`/`@Delete` 注解
- 禁止在 XML 中写 SQL（项目不使用 XML Mapper）
- 分页：使用 `Page<T>` 对象 + `selectPage` 方法

---

## Redis 使用

通过 `RedisService` 统一访问 Redis：

```java
@Autowired
private RedisService redisService;

// 常用操作
redisService.set(key, value, expireSeconds);
redisService.get(key);
redisService.increment(key);
redisService.delete(key);
```

缓存 Key 命名约定：`ulticode:{module}:{entity}:{id}`，参见 `CacheConstants`。

---

## 配置管理

- 配置类统一放在 `com.ulticode.common.config` 包下
- 外部配置通过 `application.yml` + Nacos 管理
- 敏感配置（数据库密码、JWT 密钥等）通过环境变量注入
- Feature Flags 通过 `FeatureFlagsProperties` 管理

---

## 模块结构约定

每个业务模块遵循相同的目录结构：

```
modules/{module}/
├── controller/     # REST 控制器
├── service/        # 服务接口
│   └── impl/       # 服务实现
├── mapper/         # 数据访问层
├── entity/         # 数据对象 (DO)
└── dto/            # 数据传输对象
```

新增模块时必须遵循此结构。参考现有模块（如 `problem`, `contest`, `forum`）作为模板。

---

## 与前端协作

### 响应格式

后端返回 `snake_case` 数据库字段名（MyBatis 默认）。前端 Console 使用 `apiGet/apiPost` 并手动映射为 `camelCase`。

### DTO 字段命名

- 后端 DTO 字段使用 `camelCase`
- 数据库列名使用 `snake_case`
- MyBatis-Plus 自动处理映射（`mapUnderscoreToCamelCase = true`）

### i18n

后端错误消息不硬编码中文，使用 ErrorCode 枚举。前端根据 error code 映射到对应语言的提示。
