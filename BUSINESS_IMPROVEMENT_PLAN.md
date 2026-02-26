# UltiCode 业务完善计划

> **文档版本**: 1.0
> **创建日期**: 2026-02-26
> **状态**: 待审核

本文档详细分析了 UltiCode 编程竞赛平台在业务层面的缺陷和问题，并提供了分阶段的完善计划。

---

## 目录

1. [执行摘要](#执行摘要)
2. [关键问题优先级矩阵](#关键问题优先级矩阵)
3. [后端业务缺陷](#后端业务缺陷)
4. [前端业务缺陷](#前端业务缺陷)
5. [管理后台缺陷](#管理后台缺陷)
6. [实施计划](#实施计划)
7. [技术债务清单](#技术债务清单)

---

## 执行摘要

### 当前状态评估

| 层级 | 完成度 | 主要问题 |
|------|--------|----------|
| 后端 (NestJS) | 85% | 支付未集成、邮件服务需配置 |
| 控制台 (Vue3) | 80% | OAuth不完整、实时更新需完善 |
| 管理后台 (Vue3) | 85% | 缺少统一审核队列、高级分析 |

### 核心发现

1. **~~沙箱系统~~** ✅ 已完成: Docker沙箱、VM沙箱、6种语言运行器全部实现
2. **商业缺失**: 订阅系统缺少支付集成，无法实际收费
3. **用户体验**: 缺少实时通知更新、OAuth登录不完整
4. **运营工具**: 缺少统一的内容审核队列和高级分析报表

---

## 关键问题优先级矩阵

```
┌─────────────────────────────────────────────────────────────────┐
│                        影响程度 (高)                              │
│    ┌──────────────────────┬───────────────────────────────────┐  │
│    │   ✅ 沙箱系统(已完成)  │    P0 - 支付集成                   │  │
│    │   Docker+6语言支持    │    订阅无法收费                     │  │
│    └──────────────────────┴───────────────────────────────────┘  │
│    ┌──────────────────────┬───────────────────────────────────┐  │
│    │   P1 - OAuth完善      │    P1 - 实时通知                   │  │
│    │   GitHub/Google登录   │    WebSocket通知                   │  │
│    └──────────────────────┴───────────────────────────────────┘  │
│                        影响程度 (低)                              │
│         实现难度 (低)                    实现难度 (高)            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 后端业务缺陷

### 1. 沙箱系统 [✅ 已完成]

**现状**: 完整实现，支持6种语言

**实现位置**:
- `backend/src/submission/sandbox/sandbox.interface.ts` - 接口定义
- `backend/src/submission/sandbox/docker-sandbox.service.ts` - Docker沙箱
- `backend/src/submission/sandbox/vm-sandbox.service.ts` - VM沙箱(开发用)
- `backend/src/submission/sandbox/sandbox.factory.ts` - 沙箱工厂
- `backend/src/submission/judge.service.ts` - 判题服务
- `backend/judge/Dockerfile.judge` - Docker镜像定义
- `backend/judge/runners/` - 语言运行器 (JS, Python, Java, C++, Go)

**已实现功能**:
- [x] Docker 容器隔离执行
- [x] 多语言支持 (JavaScript, TypeScript, Python, Java, C++, Go)
- [x] 资源限制 (CPU/内存/时间)
- [x] 安全沙箱隔离
- [x] 测试用例执行器
- [x] 结果收集和评分

**待完善**:
- [ ] 添加 Rust 语言支持
- [ ] 沙箱性能监控
- [ ] 执行日志记录

---

### 2. 支付集成 [P0 - 关键 - ✅ 已完成]

**现状**: Stripe支付集成已完成

**实现位置**:
- `backend/src/subscription/payment/stripe.service.ts` - Stripe服务
- `backend/src/subscription/payment/webhook.controller.ts` - Webhook处理
- `backend/src/subscription/user-subscription.controller.ts` - 用户端API
- `backend/prisma/schema.prisma` - 添加了Stripe相关字段

**已实现功能**:
- [x] Stripe SDK 集成
- [x] 订阅创建 Checkout Session
- [x] Webhook 处理 (checkout.session.completed, subscription.updated/deleted)
- [x] 用户端订阅管理 API
- [x] 订阅取消 (period end)
- [x] 订阅重新激活
- [x] Billing Portal 集成

**配置要求**:
- STRIPE_SECRET_KEY
- STRIPE_WEBHOOK_SECRET
- STRIPE_PRICE_PREMIUM_MONTHLY
- STRIPE_PRICE_PREMIUM_YEARLY

**待完善**:
- [ ] 发票生成
- [ ] 付款历史记录
- [ ] 前端订阅页面

---

### 3. 邮件服务 [P1 - 重要]

**现状**: 服务文件存在，但无实际邮件提供商配置

**位置**:
- `backend/src/email/email.service.ts`

**缺失功能**:
- [ ] SMTP/第三方邮件服务配置
- [ ] 邮件模板系统
- [ ] 批量邮件发送
- [ ] 邮件队列 (BullMQ)

**邮件类型需求**:
- 欢迎邮件
- 密码重置
- 邮箱验证
- 订阅确认
- 比赛提醒

**工作量估算**: 1 周

---

### 4. 搜索优化 [P2 - 次要]

**现状**: 基础 SQL LIKE 搜索

**改进建议**:
- [ ] Elasticsearch/Meilisearch 集成
- [ ] 全文搜索
- [ ] 搜索建议
- [ ] 搜索分析

---

### 5. 成就触发系统 [P2 - 次要]

**现状**: 成就模型存在，但无自动触发逻辑

**位置**:
- `backend/src/achievement/achievement.controller.ts:66-90`

**缺失功能**:
- [ ] 成就触发器 (在提交、解题等事件中)
- [ ] 等级系统
- [ ] 成就通知
- [ ] 成就进度追踪

**成就类型**:
| 类型 | 触发条件 | 示例 |
|------|----------|------|
| 解题 | AC数量 | 首次AC、100题、500题 |
| 连续 | 连续天数 | 连续7天、30天打卡 |
| 比赛 | 比赛参与 | 首次参赛、获奖 |
| 社区 | 贡献度 | 首次发帖、精选解答 |

---

### 6. 问题版本控制 [P2 - 次要]

**现状**: Schema 有字段但无实现

**缺失功能**:
- [ ] 问题历史版本
- [ ] 版本回滚
- [ ] 版本差异对比
- [ ] 变更日志

---

## 前端业务缺陷

### 1. OAuth 登录 [P1 - 重要]

**现状**: 后端有 GitHub OAuth，前端缺少完整流程

**位置**:
- `console/src/views/auth/LoginView.vue`
- `console/src/views/auth/RegisterView.vue`

**缺失功能**:
- [ ] GitHub OAuth 按钮
- [ ] Google OAuth 按钮
- [ ] OAuth 回调处理
- [ ] 账号关联

**实施建议**:
```vue
<!-- OAuthButton.vue -->
<template>
  <div class="oauth-buttons">
    <Button @click="loginWithGitHub">
      <GithubIcon />
      {{ t('auth.continueWithGitHub') }}
    </Button>
    <Button @click="loginWithGoogle">
      <GoogleIcon />
      {{ t('auth.continueWithGoogle') }}
    </Button>
  </div>
</template>
```

---

### 2. 实时更新 [P1 - 重要]

**现状**: WebSocket 基础设施存在，但功能不完整

**位置**:
- `console/src/lib/socket.ts`

**需要实时更新的场景**:
| 场景 | 更新内容 | 实现状态 |
|------|----------|----------|
| 比赛排名 | 排名变化 | ❌ 缺失 |
| 提交状态 | 判题结果 | ❌ 缺失 |
| 通知 | 新消息 | ✅ 已实现 |
| 社区动态 | 新帖子 | ❌ 缺失 |

---

### 3. 用户体验改进 [P2 - 次要]

**缺失功能**:
- [ ] 代码编辑器自动保存
- [ ] 离线编辑支持
- [ ] 快捷键提示
- [ ] 提交历史图表
- [ ] 学习进度追踪

---

### 4. 移动端优化 [P2 - 次要]

**问题**:
- 代码编辑器在小屏幕上体验差
- 导航菜单需要优化
- 表格数据展示需要适配

---

### 5. 可访问性 [P3 - 低优先级]

**缺失**:
- [ ] 键盘导航支持
- [ ] 屏幕阅读器支持
- [ ] 高对比度模式
- [ ] 减少动画选项

---

## 管理后台缺陷

### 1. 统一审核队列 [P1 - 重要]

**现状**: 分散的审核入口

**缺失功能**:
- [ ] 统一的内容审核队列
- [ ] 批量审核操作
- [ ] 审核历史
- [ ] 审核员分配

**建议界面**:
```
┌─────────────────────────────────────────────────┐
│  审核队列                    [全部] [待处理] [已处理]  │
├─────────────────────────────────────────────────┤
│  类型    │ 内容预览        │ 举报原因  │ 操作    │
│─────────┼────────────────┼──────────┼─────────│
│  问题   │ 两数之和...     │ 题目错误  │ [审核]  │
│  帖子   │ 关于DP的问题... │ 垃圾内容  │ [审核]  │
│  题解   │ 最优解法...     │ 抄袭     │ [审核]  │
└─────────────────────────────────────────────────┘
```

---

### 2. 高级分析报表 [P1 - 重要]

**现状**: 基础统计存在，缺少深度分析

**缺失报表**:
- [ ] 用户活跃度分析
- [ ] 题目完成率分析
- [ ] 比赛参与分析
- [ ] 收入分析 (订阅)
- [ ] 性能指标报表

---

### 3. 提交管理 [P2 - 次要]

**缺失功能**:
- [ ] 提交列表浏览
- [ ] 重新判题
- [ ] 批量重判
- [ ] 提交详情查看

---

### 4. 系统配置扩展 [P2 - 次要]

**缺失配置**:
- [ ] 邮件服务器配置 UI
- [ ] 速率限制设置
- [ ] 上传限制设置
- [ ] 功能开关

---

## 实施计划

### 阶段 1: 核心功能 (已完成大部分)

**目标**: 使平台达到可用状态

#### Sprint 1: 沙箱系统 ✅ 已完成

**实现位置**:
- `backend/src/submission/sandbox/sandbox.interface.ts`
- `backend/src/submission/sandbox/docker-sandbox.service.ts`
- `backend/src/submission/sandbox/vm-sandbox.service.ts`
- `backend/src/submission/sandbox/sandbox.factory.ts`
- `backend/src/submission/judge.service.ts`
- `backend/judge/Dockerfile.judge`
- `backend/judge/runners/` - JavaScript, Python, Java, C++, Go

#### Sprint 2: 支付集成 ✅ 已完成

**实现位置**:
- `backend/src/subscription/payment/stripe.service.ts`
- `backend/src/subscription/payment/webhook.controller.ts`
- `backend/src/subscription/user-subscription.controller.ts`
- `backend/prisma/schema.prisma` - Stripe字段
- `console/src/api/subscription.ts`
- `console/src/views/personal/SubscriptionView.vue`

#### Sprint 3: 邮件 + OAuth (进行中)

**任务**:
- [ ] 邮件服务配置
- [ ] 邮件模板
- [ ] OAuth 前端完成
- [ ] 测试所有认证流程

---

### 阶段 2: 用户体验 (3-4周)

#### Sprint 4: 实时更新 (2周)

**任务**:
- [ ] WebSocket 连接优化
- [ ] 比赛实时排名
- [ ] 提交状态实时更新
- [ ] 通知系统完善

#### Sprint 5: 前端优化 (2周)

**任务**:
- [ ] 代码编辑器增强
- [ ] 移动端适配
- [ ] 性能优化
- [ ] 无障碍支持

---

### 阶段 3: 运营工具 (2-3周)

#### Sprint 6: 管理后台增强

**任务**:
- [ ] 统一审核队列
- [ ] 高级分析报表
- [ ] 提交管理界面
- [ ] 系统配置扩展

---

### 阶段 4: 优化完善 (持续)

**任务**:
- [ ] 搜索优化
- [ ] 成就系统完善
- [ ] 性能调优
- [ ] 技术债务清理

---

## 技术债务清单

### 高优先级

| 债务 | 位置 | 影响 | 建议 |
|------|------|------|------|
| 沙箱存根 | `backend/src/submission/sandbox/` | 核心功能不可用 | 完整实现 |
| 支付缺失 | `backend/src/subscription/` | 无法收费 | 集成 Stripe |
| 测试覆盖不足 | 全项目 | 质量风险 | 补充测试 |

### 中优先级

| 债务 | 位置 | 影响 | 建议 |
|------|------|------|------|
| 缺少 E2E 测试 | `*/test/e2e/` | 回归风险 | 添加 E2E |
| 错误处理不一致 | 前端各组件 | 用户体验 | 统一处理 |
| API 文档缺失 | 后端 | 开发效率 | 添加 Swagger |

### 低优先级

| 债务 | 位置 | 影响 | 建议 |
|------|------|------|------|
| 部分组件未国际化 | 前端 | 国际化 | 补充 i18n |
| 代码注释不足 | 全项目 | 可维护性 | 添加注释 |
| 日志不统一 | 后端 | 调试困难 | 统一日志 |

---

## 测试策略

### 单元测试要求

- 后端服务层覆盖率: > 80%
- 前端 composables 覆盖率: > 80%
- 工具函数覆盖率: > 90%

### 集成测试要求

- 所有 API 端点测试
- 数据库操作测试
- 第三方服务 mock 测试

### E2E 测试关键路径

1. 用户注册 → 验证邮箱 → 登录
2. 浏览题目 → 提交代码 → 查看结果
3. 参加比赛 → 提交 → 查看排名
4. 订阅计划 → 支付 → 确认

---

## 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 沙箱安全问题 | 中 | 高 | 容器隔离、资源限制、代码审计 |
| 支付集成复杂 | 中 | 中 | 使用成熟 SDK、充分测试 |
| 性能瓶颈 | 中 | 中 | 负载测试、优化查询 |
| 第三方依赖变更 | 低 | 中 | 版本锁定、监控更新 |

---

## 附录

### A. 文件参考

**后端关键文件**:
```
backend/src/
├── submission/
│   ├── sandbox/           # 沙箱系统 - 需要实现
│   └── judge.service.ts   # 判题服务
├── subscription/
│   └── subscription.controller.ts  # 订阅管理
├── email/
│   └── email.service.ts   # 邮件服务
├── achievement/
│   └── achievement.controller.ts  # 成就系统
└── auth/
    └── auth.controller.ts # 认证
```

**前端关键文件**:
```
console/src/
├── views/
│   ├── auth/              # 认证页面
│   ├── problems/          # 题目相关
│   ├── contest/           # 比赛相关
│   └── personal/          # 个人中心
├── stores/
│   └── auth.ts            # 认证状态
└── lib/
    └── socket.ts          # WebSocket
```

**管理后台关键文件**:
```
management/src/
├── views/
│   ├── users/             # 用户管理
│   ├── problems/          # 题目管理
│   ├── contests/          # 比赛管理
│   └── forum/             # 论坛管理
└── stores/
    └── admin/             # 管理状态
```

### B. 相关资源

- [NestJS 文档](https://docs.nestjs.com/)
- [Vue 3 文档](https://vuejs.org/)
- [Stripe API](https://stripe.com/docs/api)
- [Docker SDK](https://docs.docker.com/engine/api/sdk/)

---

*本文档由 Claude Code 生成，用于指导 UltiCode 平台的业务完善工作。*
