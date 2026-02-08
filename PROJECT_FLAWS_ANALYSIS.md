# UltiCode-Public-Next 项目缺陷分析报告

**分析日期**: 2026-02-08
**分析范围**: backend, console, management

---

## 目录

1. [关键安全漏洞](#1-关键安全漏洞)
2. [后端架构缺陷](#2-后端架构缺陷)
3. [控制台前端缺陷](#3-控制台前端缺陷)
4. [管理后台缺陷](#4-管理后台缺陷)
5. [跨模块共性问题](#5-跨模块共性问题)
6. [优先级修复建议](#6-优先级修复建议)

---

## 1. 关键安全漏洞

### 1.1 🔴 [严重] Node.js vm 模块沙箱逃逸漏洞

**文件**: `backend/src/submission/judge.service.ts`
**行号**: 3, 94, 101, 106, 148

**问题描述**:
代码评测服务使用 Node.js 内置的 `vm` 模块执行用户提交的 JavaScript/TypeScript 代码。然而，`vm` 模块**并非为安全沙箱设计**，存在已知的沙箱逃逸漏洞。

```typescript
// Line 3
import * as vm from 'vm';

// Line 94 - 创建不安全的上下文
vm.createContext(context);

// Line 101 - 暴露全局上下文，进一步加剧风险
context.globalThis = context;

// Line 106, 148 - 在不安全环境中执行用户代码
new vm.Script(code).runInContext(context);
```

**潜在影响**:
- 恶意用户可执行任意系统命令
- 读写服务器敏感文件（如 `/etc/passwd`、配置文件）
- 访问环境变量和敏感信息
- 发起拒绝服务攻击
- 完全控制服务器

**修复建议**:
1. **立即停止使用 Node.js `vm` 模块执行不受信任的代码**
2. 使用容器化方案（Docker、gVisor、Firecracker）
3. 使用专业沙箱工具（nsjail、seccomp）
4. 严格限制网络访问和系统调用

---

## 2. 后端架构缺陷

### 2.1 数据库设计问题

#### 2.1.1 软删除索引不一致

**文件**: `backend/prisma/schema.prisma`

| 模型 | 行号 | 问题 |
|------|------|------|
| ForumComment | 533-541 | 缺少 `is_deleted` 复合索引 |
| SolutionComment | 751-759 | 缺少 `is_deleted` 复合索引 |
| Problem | 119-120 | ✅ 正确实现 |
| Contest | 230 | ✅ 正确实现 |

**建议**: 为所有软删除模型添加 `@@index([is_deleted, created_at])` 复合索引。

#### 2.1.2 软删除模型不完整

**文件**: `backend/src/prisma.service.ts` (Line 30-37)

部分应该支持软删除的模型缺少 `is_deleted`/`deleted_at` 字段：
- `ProblemList` (schema.prisma line 636)
- `Bookmark` (schema.prisma line 687)

#### 2.1.3 ForumUser 缺少审计字段

**文件**: `backend/prisma/schema.prisma` (Line 466)

`ForumUser` 模型缺少 `created_at` 和 `updated_at` 字段，不符合其他模型的审计规范。

### 2.2 业务逻辑缺陷

#### 2.2.1 竞赛注册竞态条件

**文件**: `backend/src/contest/services/contest-participation.service.ts`
**行号**: 28-36, 75-84

```typescript
// registerForContest 和 unregisterFromContest 使用读后写模式
// 缺少显式事务，可能导致:
// - 并发注册时 registered_count 计数不准确
// - 重复注册问题
```

**修复建议**: 使用 Prisma 事务或数据库唯一约束。

#### 2.2.2 软删除查询遗漏

**文件**: `backend/src/prisma.service.ts` (Line 48-74)

软删除中间件仅拦截写操作，**读操作仍会返回已删除记录**。每个查询都需要手动添加 `where: { is_deleted: false }`。

**修复建议**: 实现全局读取中间件自动过滤已删除记录。

#### 2.2.3 内存限制检测不准确

**文件**: `backend/src/submission/judge.service.ts` (Line 309)

```typescript
currentMemoryMb() {
  return process.memoryUsage().heapUsed / 1024 / 1024;
}
```

`process.memoryUsage()` 返回的是整个 Node.js 进程的内存，而非沙箱脚本的独立内存。当多个评测任务并发执行时，MLE 判定将不准确。

### 2.3 API 设计问题

#### 2.3.1 BigInt ID 类型不一致

**文件**:
- `backend/src/problem/problem.controller.ts` (Line 49, 116)
- `backend/src/submission/services/submission-execution.service.ts` (Line 13)

数据库中 `Problem.id` 是 `BigInt`，但在多处被转换为 `Number`。当 ID 超过 `Number.MAX_SAFE_INTEGER` (2^53) 时会导致精度丢失。

**修复建议**: 全栈使用字符串序列化 BigInt。

#### 2.3.2 Premium 内容返回结构不一致

**文件**: `backend/src/problem/problem.service.ts` (Line 334-371)

`findOneWithPremiumCheck` 根据用户权限返回不同的对象结构（完整对象 vs 预览对象），导致前端处理困难。

**修复建议**: 返回一致的对象结构，使用显式标志位指示访问权限。

### 2.4 性能问题

#### 2.4.1 权限检查 N+1 查询

**文件**: `backend/src/admin/services/permission.service.ts` (Line 23, 33)

`hasPermission` 方法每次调用执行两次数据库查询。如果在单个请求中多次检查权限，会产生 N+1 问题。

**修复建议**: 缓存用户权限到内存或 Redis。

#### 2.4.2 同步代码执行阻塞 API

**文件**: `backend/src/submission/services/submission-execution.service.ts` (Line 25)

`run` 方法同步调用 `judgeService.judge`，可能阻塞 API 响应。

**修复建议**: 将执行请求也提交到 BullMQ 队列异步处理。

---

## 3. 控制台前端缺陷

### 3.1 组件架构问题

#### 3.1.1 路由复用组件状态问题

**文件**: `console/src/router/index.ts` (Line 19, 23, 31, 69, 73, 77, 81, 87)

多个路由复用同一组件但传递不同 props：
- `/forum/popular` 和 `/forum/all` 复用 `ForumFeedView.vue`
- `/contest/past` 等复用 `ContestView.vue`

如果组件未正确监听 props 变化，会导致数据不刷新或状态残留。

**修复建议**:
1. 在组件中使用 `watch` 监听 props 变化
2. 或在 `RouterView` 添加 `:key="$route.fullPath"` 强制重新创建

### 3.2 类型安全问题

#### 3.2.1 使用 `unknown` 类型

| 文件 | 行号 | 问题 |
|------|------|------|
| `console/src/types/problem-detail.ts` | 62 | `examples: unknown[]` 缺少具体类型 |
| `console/src/composables/useErrorHandler.ts` | 31, 109 | `error: unknown` 缺少错误类型定义 |
| `console/src/views/personal/BookmarksView.vue` | 233, 242, 258 | 使用 `as unknown as [Type]` 类型断言 |

#### 3.2.2 元数据类型不安全

**文件**: `console/src/types/bookmark.ts` (Line 31)

```typescript
metadata?: Record<string, unknown>;  // 过于宽泛
```

**修复建议**: 使用 discriminated union 根据 `targetType` 定义具体的元数据类型。

### 3.3 潜在问题

- **加载状态**: 部分 API 调用可能缺少 loading 指示器
- **缓存失效**: 缺少系统性的缓存失效机制
- **Props drilling**: 需要深入审查是否存在过度的属性传递

---

## 4. 管理后台缺陷

### 4.1 安全问题

#### 4.1.1 角色定义使用魔法字符串

**文件**: `management/src/lib/ui/roles.ts` (Line 6-17)

```typescript
function getRoleBadgeVariant(role: string) {
  switch (role) {
    case 'SUPER_ADMIN': ...
    case 'ADMIN': ...
    // 魔法字符串，容易拼写错误
  }
}
```

**修复建议**: 使用 TypeScript enum 或 const object 定义角色。

#### 4.1.2 审计日志覆盖不完整 (潜在)

需要验证后端是否对所有敏感操作（用户创建、角色变更、权限授予、数据删除）生成审计日志。

### 4.2 代码复用问题

#### 4.2.1 UI 组件重复

**目录**: `management/src/components/ui/`

管理后台和用户控制台存在大量重复的基础 UI 组件（Button、Input、Dialog、Table 等）。

**修复建议**: 创建共享的 `@ulticode/ui` 包，由两个前端项目共同使用。

### 4.3 国际化问题

#### 4.3.1 硬编码字符串

**文件**: `management/src/api/admin/users.ts`

```typescript
// Line 113
toast.success('User created successfully');
// Line 130
toast.success('User has been banned');
```

**文件**: `management/src/stores/admin/audit.ts` (Line 28, 50, 68)

```typescript
'Failed to fetch audit logs'  // 硬编码错误消息
```

**修复建议**: 所有用户可见字符串应使用 i18n 系统。

### 4.4 类型安全问题

#### 4.4.1 审计日志值类型宽泛

**文件**: `management/src/api/admin/audit.ts` (Line 22-23)

```typescript
old_values?: unknown;
new_values?: unknown;
```

需要在使用时进行类型断言或类型守卫。

### 4.5 状态管理

#### 4.5.1 跨 Store 依赖

`management/src/stores/admin/` 目录下有多个细分 Store。需确保：
- 更新用户后刷新审计日志
- 删除数据后更新 Dashboard 统计

#### 4.5.2 数据陈旧风险

Pinia Store 中的数据在 CUD 操作后需要显式刷新，否则 UI 可能显示过期信息。

---

## 5. 跨模块共性问题

### 5.1 错误处理不统一

| 模块 | 问题 |
|------|------|
| Backend | 缺少统一的错误响应格式 |
| Console | 有 `useErrorHandler`，但覆盖不完整 |
| Management | 使用通用 `console.error`，缺少用户友好提示 |

### 5.2 类型定义分散

三个模块独立定义类型，缺少共享的类型包，可能导致前后端类型不一致。

### 5.3 验证逻辑位置

客户端验证不完整或缺失，过度依赖后端验证导致用户体验差。

---

## 6. 优先级修复建议

### P0 - 立即修复（安全关键）

| 问题 | 文件 | 影响 |
|------|------|------|
| vm 沙箱逃逸漏洞 | `judge.service.ts` | 服务器可能被完全控制 |

### P1 - 高优先级（1-2 周内）

| 问题 | 文件 |
|------|------|
| 竞赛注册竞态条件 | `contest-participation.service.ts` |
| BigInt ID 类型不一致 | 多处 controller/service |
| 软删除查询遗漏 | `prisma.service.ts` |

### P2 - 中优先级（1 个月内）

| 问题 | 模块 |
|------|------|
| 数据库索引优化 | Backend |
| UI 组件共享库 | Console + Management |
| i18n 硬编码字符串 | Management |
| 权限检查 N+1 查询 | Backend |

### P3 - 低优先级（持续改进）

| 问题 | 模块 |
|------|------|
| 类型安全增强 | 全部 |
| 路由组件状态问题 | Console |
| 错误处理统一 | 全部 |
| 审计字段完善 | Backend |

---

## 附录：检查清单

### 安全审查
- [ ] 替换 vm 模块为安全沙箱方案
- [ ] 审计所有用户输入处理
- [ ] 验证所有敏感操作的审计日志

### 代码质量
- [ ] 添加缺失的数据库索引
- [ ] 统一软删除实现
- [ ] 创建共享类型包
- [ ] 创建共享 UI 组件包

### 性能优化
- [ ] 实现权限缓存
- [ ] 异步化代码执行服务
- [ ] 优化 N+1 查询

---

*报告生成于 2026-02-08，由项目分析工具自动生成*
