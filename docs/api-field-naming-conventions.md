# API 字段命名差异说明

> 本文件记录 UltiCode 后端/前端跨模块出现的同语义字段命名分歧。
> 列出的差异是 **历史成因**,本计划仅做澄清不做强制统一;新代码请遵循"目标命名"列。

## 概述

随着模块演进,同一概念在不同模块出现了多种命名形式。当前未统一的字段如下表,**新接口应严格遵循目标命名**,旧接口在不破坏前端兼容的前提下渐进迁移。

---

## 已知差异

### 1. 重置密码字段 `password` vs `newPassword`

| 模块 / 文件 | 字段名 | 用途 |
|------------|--------|------|
| `admin/dto/ResetPasswordRequest.java` | **`password`** | 管理员重置任意用户密码 (`POST /admin/users/{id}/reset-password`) |
| `auth/dto/ResetPasswordDTO.java` | `newPassword` | 用户通过邮件 token 自助重置 (`POST /auth/reset-password`) |
| `management/src/stores/admin/users.ts` | `password` | 前端调用 admin 端点,与 admin 模块对齐 |
| `console/src/views/auth/ResetPasswordView.vue` | `newPassword` | 前端调用 auth 端点,与 auth 模块对齐 |

**目标命名**: `newPassword`(语义更精确)
**当前状态**: 暂保持现状。admin 模块前后端已对齐 `password`,改名需同步两端 + 兼容性发布。新增重置密码相关字段统一用 `newPassword`。

---

### 2. 封禁原因 `bannedReason` vs `banReason`

| 模块 / 文件 | 字段名 | 备注 |
|------------|--------|------|
| `user/entity/User.java` | `bannedReason` (DB `banned_reason`) | 真值 |
| `admin/dto/AdminUserVO.java` | `banReason` | 历史 mismatch — 前端读这个 |
| `management/src/api/admin/users.ts` | `banReason` | 与 VO 一致 |

**目标命名**: `bannedReason`(与实体/DDL 一致)
**当前状态**: 前端期望 `banReason`,VO 已实际暴露 `banReason`(`AdminUserServiceImpl.toVO:441` 显式映射 `user.getBannedReason() → vo.setBanReason()`)。如要统一为 `bannedReason`,需同时改前端 + VO 字段名 + Vue 视图,工作量较大,**留待后续前端重构一并处理**。

---

### 3. 封禁时长 `until` (存在) vs `duration` (不存在)

| 模块 / 文件 | 字段名 | 备注 |
|------------|--------|------|
| `admin/dto/BanUserRequest.java` | **`until`** (ISO 8601 字符串) | 唯一真值 |
| `management/src/api/admin/users.ts` `BanUserDto` | `until` | ✅ 对齐 |
| 任何 `duration` 字段 | (不存在) | 前端如需"封禁 N 天",在客户端算 `now + N days` 后传 `until` |

**目标**: 维持 `until` 唯一标准。文档/Swagger 已显式说明。

---

### 4. 权限管理接口 (新增,本次修复)

| 接口 | 字段 | 备注 |
|------|------|------|
| `POST /admin/users/{id}/permissions` `GrantPermissionRequest` | `action` / `resource` / `expiresAt` | 与 `UserPermission` entity 字段一一对应;`expiresAt` 必须严格晚于当前时间 |
| `DELETE /admin/users/{id}/permissions` `RevokePermissionRequest` | `action` / `resource` | 撤销不存在的权限返回 200(REST DELETE 幂等) |
| 前端 `usersApi.grantPermission/revokePermission` | 已对齐 | management/src/api/admin/users.ts:125-133 |

---

## 命名规约总则(未来新接口)

1. **后端 DTO 字段**: `camelCase`,与数据库列 `snake_case` 通过 MyBatis `mapUnderscoreToCamelCase=true` 自动映射
2. **同概念字段跨模块必须统一**:命名差异要求事先在 `docs/` 提交说明并经评审
3. **VO/Entity 字段名**:VO 字段名以 entity 字段名为准;特殊情况(向后兼容)需在 `toVO()` 方法显式说明
4. **前端 TS 类型**:必须与后端字段名 1:1 对齐;前端 `User.banReason` 这类 mismatch 不允许新增
5. **password 类字段**:统一用 `newPassword` 表达"新密码"语义,避免 `password` 在不同上下文混淆(原密码 vs 新密码)

---

## 相关参考

- `docs/admin-users-api-test-report.md` — admin/users 13 接口实测报告(发现以上差异)
- 修复计划: `.claude/plans/docs-admin-users-api-test-report-md-merry-pixel.md`
- 字段对齐 skill: `cross-stack-dto-granularity-alignment`
