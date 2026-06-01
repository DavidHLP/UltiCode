# Management 前端 i18n 适配分析报告

**扫描范围**: `management/src/` 全量 708 个 `.vue` / `.ts` 文件
**i18n 框架**: vue-i18n v11.4.4 (Composition API)
**支持语言**: zh-CN (默认), en-US
**当前翻译规模**: ~2,639 个 key，25 个模块
**生成日期**: 2026-06-01

---

## 一、总体评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | 7/10 | 模块化分层、懒加载、类型支持良好 |
| Key 路径一致性 | 3/10 | **196 个 key 路径不匹配，system 子模块全部失效** |
| 命名规范 | 6/10 | 主流 camelCase，但有 28 个 snake_case 混入 |
| 动态 key 安全性 | 5/10 | 50+ 处动态拼接，无静态检查保障 |
| 日期/数字格式化 | 3/10 | **大量硬编码 'en-US'，工具函数存在但未被使用** |
| 文本覆盖率 | 4/10 | **122+ 处硬编码英文，3 个文件使用内联 i18n** |
| API 错误国际化 | 5/10 | 框架设计良好，但 store/request 层有硬编码 |
| 翻译完整性 | 4/10 | 检查工具存在但未 CI 集成，~28 个 en-US key 缺失 |

**综合评分: 4.7/10** — 架构基础扎实，但执行层面存在大面积适配缺口。

---

## 二、i18n 架构概况

### 2.1 核心配置

- **Library**: vue-i18n v11.4.4 (Composition API 模式)
- `legacy: false` — 使用 Composition API
- `globalInjection: true` — 模板中可直接使用 `$t`
- 默认 locale: `zh-CN`，Fallback locale: `zh-CN`
- `silentTranslationWarn: true`，`missingWarn` 仅开发环境
- **懒加载**: `zh-CN` 启动时加载，`en-US` 切换时动态 `import()`
- **Locale 检测优先级**: localStorage → 浏览器语言 → `zh-CN`

### 2.2 目录结构

```
src/i18n/
  index.ts              -- 主入口：创建 i18n 实例，导出 loadLocale/setLocale/t
  types.ts              -- 类型定义：SupportedLocale, MessageSchema, LocaleConfig
  utils.ts              -- 工具函数：locale 切换、日期/数字格式化、复数
  check.ts              -- 翻译完整性检查脚本 (npx tsx src/i18n/check.ts)
  utils/
    storage.ts          -- 多级 locale 持久化 (localStorage → sessionStorage → memory)
  __tests__/
    table-keys.spec.ts  -- Vitest 测试：表格列名 key 一致性
  locales/
    zh-CN/
      index.ts          -- 聚合 25 个模块
      modules/          -- 25 个 .ts 模块文件
    en-US/
      index.ts          -- 同上
      modules/          -- 25 个 .ts 模块文件
```

### 2.3 模块分布与 Key 统计

| 模块 | zh-CN Keys | en-US Keys | 差异 |
|------|-----------|-----------|------|
| problems | 445 | 439 | +6 |
| moderation | 287 | 287 | 0 |
| contests | 270 | 258 | +12 |
| users | 132 | 131 | +1 |
| settings | 128 | 126 | +2 |
| problemLists | 126 | 125 | +1 |
| system | 120 | 119 | +1 |
| dashboard | 108 | 108 | 0 |
| table | 101 | 101 | 0 |
| solutions | 99 | 99 | 0 |
| submissions | 93 | 93 | 0 |
| analytics | 89 | 89 | 0 |
| notifications | 82 | 81 | +1 |
| comments | 75 | 75 | 0 |
| auth | 68 | 68 | 0 |
| tags | 65 | 64 | +1 |
| common | 64 | 64 | 0 |
| errors | 59 | 59 | 0 |
| scoring-rules | 59 | 58 | +1 |
| forum | 154 | 154 | 0 |
| audit | 176 | 176 | 0 |
| billing | 34 | 34 | 0 |
| account | 37 | 37 | 0 |
| audit-report | 26 | 26 | 0 |
| nav | 27 | 27 | 0 |
| **Total** | **~2,639** | **~2,611** | **~28** |

### 2.4 使用模式

**Pattern A — 直接 `useI18n()`**（最常见）:
```ts
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
```

**Pattern B — `useLocale()` composable**（推荐）:
```ts
import { useLocale } from '@/composables/useLocale'
const { t, currentLocale, setLocale, toggleLocale } = useLocale()
```

---

## 三、问题清单（按严重程度排序）

### 🔴 P0 — 功能性缺陷（用户可见的错误）

#### 1. System 模块 key 路径不匹配 — 196 个 key 静默失效

**根因**: 代码中使用 `t('backup.title')`，但 locale 文件中实际路径是 `system.backup.title`。由于 `silentTranslationWarn: true`，用户看到的是原始 key 字符串而非翻译文本。

| 子模块 | 受影响 key 数 | 所在文件 |
|--------|-------------|---------|
| `backup.*` | ~25 | `views/system/BackupView.vue` |
| `email.*` | ~30 | `views/system/EmailView.vue` |
| `monitoring.*` | ~25 | `views/system/MonitoringView.vue` |
| `errors.*` | ~12 | 错误页面 |
| `auditReport.*` | 全部 | 审计报告 |
| `problems.bulk.*` | 部分 | 批量操作 |
| `solutions.approval.*` | 部分 | 审批流程 |
| `submissions.batchRejudge*` | 部分 | 批量重判 |
| `users.actions.*` | 部分 | 用户操作 |

#### 2. en-US 缺失 ~28 个 key

| 模块 | 缺失数 | 典型缺失 |
|------|--------|---------|
| `contests` | +12 | `EDUCATIONAL`, `MONTHLY`, `PRIVATE`, `PUBLIC`, `RATED` 等 |
| `problems` | +6 | 批量操作相关 |
| `users` | +1 | — |
| `settings` | +2 | — |
| `system` | +1 | — |
| 其他 | +6 | 散布在各模块 |

---

### 🟠 P1 — 日期/数字格式化硬编码

#### 3. 日期格式硬编码 `'en-US'` — 中文用户看到英文日期

**核心问题文件**:

| 文件 | 行号 | 问题 |
|------|------|------|
| `lib/format/date.ts` | 10, 35, 71, 101, 122 | 所有函数默认 locale 为 `'en-US'` |
| `views/dashboard/DashboardView.vue` | 26, 34 | `toLocaleTimeString('en-US')` |
| `views/analytics/composables/useAnalyticsReports.ts` | 40, 48 | 同上 |
| `components/dashboard/AreaChart.vue` | 249 | `toLocaleDateString('en-US')` |
| `views/contest/components/ContestCard.vue` | 63 | 同上 |

#### 4. 日期格式不传 locale — 依赖浏览器默认值

以下 10+ 处 `toLocaleDateString()` / `toLocaleString()` 调用没有传入 locale 参数：

| 文件 | 行号 |
|------|------|
| `views/contests/ContestDetailDrawer.vue` | 193 |
| `views/billing/BillingView.vue` | 69 |
| `views/account/AccountView.vue` | 351, 363 |
| `views/moderation/components/ActionHistoryTimeline.vue` | 97 |
| `views/moderation/components/ModerationDetailDrawer.vue` | 67 |
| `views/moderation/components/EntityPreviewCard.vue` | 65 |
| `views/forum/ForumPostDetailDrawer.vue` | 42 |
| `views/problems/tabs/OverviewTab.vue` | 261, 268, 275 |
| `views/problem-lists/components/ProblemsManager.vue` | 215 |
| `views/contests/wizard/StepReview.vue` | 53 |

#### 5. 货币/数字硬编码

| 文件 | 行号 | 问题 |
|------|------|------|
| `views/analytics/composables/useAnalyticsReports.ts` | 78 | `'$' + num.toFixed(2)` 硬编码美元符号 |
| 同上 | 68-69 | `K`/`M` 后缀硬编码 |
| `components/analytics/AnalyticsTagCloud.vue` | 55-56 | 同上 |
| `components/analytics/AnalyticsBarList.vue` | 44 | 同上 |

---

### 🟡 P2 — 硬编码英文文本（122+ 处）

#### 6. Vue 模板中的硬编码文本

**典型高频问题文件**:

| 文件 | 行号 | 硬编码文本 |
|------|------|-----------|
| `views/auth/components/OAuthButton.vue` | 30 | `Continue with GitHub` |
| `views/moderation/ReportsView.vue` | 214 | `REPORTS` |
| `views/moderation/ModerationQueueView.vue` | 317 | `CONTENT MODERATION` |
| `views/moderation/AppealsView.vue` | 218 | `APPEALS` |
| `views/moderation/components/ModerationActionPanel.vue` | 245 | `days` |
| `views/contests/ContestsListView.vue` | 213-231 | `total:`, `running:`, `upcoming:`, `finished:` |
| `views/notifications/NotificationsListView.vue` | 315-340 | `total:`, `system:`, `contest:`, `submission:`, `system announcements` |
| `views/moderation/ModerationDashboardView.vue` | 243, 274 | `No data available` |
| `views/problem-lists/ProblemListsListView.vue` | 165 | `total:` |
| `views/contests/components/ContestOverviewTab.vue` | 41 | `HIDDEN` |
| `views/problem-lists/components/BasicInfoSection.vue` | 199, 200 | `// saved`, `// error` |

#### 7. 内联 `<i18n>` 块绕过集中管理

以下 3 个文件使用了组件级 `<i18n lang="json">` 块，翻译无法被 `check.ts` 检测：

- `views/problems/view/ViewCasesView.vue`
- `views/problems/view/ViewCodeView.vue`
- `views/problems/view/ViewDescriptionView.vue`

---

### 🟢 P3 — 设计改进建议

#### 8. 命名规范混用

- **snake_case** (28 个 key): `dashboard.ts` 和 `table.ts` 中的 `user_registered`, `forum_post`, `joined_at` 等
- **ALL_CAPS** (296 个 key): 枚举值使用，合理但需文档化

#### 9. 动态 key 拼接 — 50+ 处无静态检查

```typescript
// 典型动态 key 拼接示例
t(`problems.difficulty.${difficulty.toLowerCase()}`)
t(`moderation.status.${status}`)
t(`billing.status.${subscription.status}`)
t(`table.columnNames.${columnId}`, columnId)
// ... 50+ 处
```

涉及前缀:
- `problems.difficulty.*`, `problems.status.*`, `problems.bulk.*`
- `moderation.status.*`, `moderation.categories.*`, `moderation.actions.*`, `moderation.entityTypes.*`
- `billing.status.*`, `billing.plans.*`
- `users.filters.role.*`
- `audit.actionTypes.*`, `audit.entityTypes.*`
- `solutions.visibility.*`
- `comments.type.*`
- `email.status.*`, `backup.status.*`, `monitoring.status.*`

#### 10. API 错误消息硬编码

`utils/request.ts` 中:
- 第 256 行: `'Request failed'`
- 第 310 行: `'Request canceled'`
- 第 373 行: `'Permission denied'`

#### 11. 缺少 CI/CD 集成

`check.ts` 存在但未集成到构建流程，无 pre-commit hook 或 CI step。

---

## 四、执行计划

### Phase 1: 紧急修复（P0 — 1~2 天）

> 目标: 消除用户可见的翻译缺失问题

#### 任务 1.1: 修复 system 模块 key 路径不匹配

方案选择:

| 方案 | 做法 | 影响范围 | 风险 |
|------|------|---------|------|
| **A: 修改代码（推荐）** | 将 `t('backup.title')` 改为 `t('system.backup.title')` | ~3 个 View 文件 | 低 |
| B: 拆分 locale | 将 `system.ts` 中的 backup/email/monitoring 提取为独立模块 | locale 结构变更 | 中 |

**执行步骤**:

- [ ] `views/system/BackupView.vue` — 所有 `t('backup.` → `t('system.backup.`
- [ ] `views/system/EmailView.vue` — 所有 `t('email.` → `t('system.email.`
- [ ] `views/system/MonitoringView.vue` — 所有 `t('monitoring.` → `t('system.monitoring.`
- [ ] 检查 `errors.*`, `auditReport.*`, `problems.bulk.*` 等其他路径不匹配

#### 任务 1.2: 补齐 en-US 缺失的 ~28 个 key

- [ ] 运行 `npx tsx src/i18n/check.ts` 获取精确差异
- [ ] 在 `en-US/modules/contests.ts` 补齐 12 个缺失 key
- [ ] 在 `en-US/modules/problems.ts` 补齐 6 个缺失 key
- [ ] 其他模块逐一补齐

---

### Phase 2: 日期/数字格式化修复（P1 — 2~3 天）

> 目标: 所有日期/数字/货币跟随用户 locale 显示

#### 任务 2.1: 统一使用 i18n 工具函数

项目已有两套工具函数:

| 工具 | 位置 | 推荐 |
|------|------|------|
| `formatDateByLocale`, `formatNumberByLocale` | `i18n/utils.ts` | ✅ 推荐使用 |
| `formatDate`, `formatDateTime` | `lib/format/date.ts` | 需修复默认 locale |

**执行步骤**:

- [ ] 修改 `lib/format/date.ts` — 将所有 `'en-US'` 默认值改为从 i18n 获取当前 locale
- [ ] 创建一个 `useFormatDate()` composable 封装 locale 感知的格式化
- [ ] 全局搜索替换所有 `toLocaleDateString('en-US')` 调用（5 处）
- [ ] 全局搜索替换所有无 locale 的 `toLocaleDateString()` / `toLocaleString()` 调用（10+ 处）

#### 任务 2.2: 货币和数字本地化

- [ ] 将 `'$' + num.toFixed(2)` 替换为 `Intl.NumberFormat` + i18n key
- [ ] 将 `K`/`M` 后缀改为 i18n key（中文可能用 `万`/`亿`）
- [ ] 统一到 `i18n/utils.ts` 的 `formatNumberByLocale`

---

### Phase 3: 硬编码文本清理（P2 — 3~5 天）

> 目标: 消除所有模板中的硬编码英文文本

#### 任务 3.1: 批量处理高频硬编码

按模块分批处理:

| 批次 | 模块 | 预计工作量 |
|------|------|-----------|
| 3.1a | `moderation` (ReportsView, AppealsView, ModerationQueueView, ModerationDashboardView) | 0.5 天 |
| 3.1b | `contests` (ContestsListView, ContestOverviewTab) | 0.5 天 |
| 3.1c | `notifications` (NotificationsListView) | 0.5 天 |
| 3.1d | `auth` (OAuthButton) | 0.25 天 |
| 3.1e | `problem-lists` (ProblemListsListView, BasicInfoSection) | 0.25 天 |
| 3.1f | 其他散布文件 | 1 天 |

流程: 对每个文件:
1. 提取硬编码文本 → 写入对应 locale 模块
2. 模板中替换为 `$t('module.key')`
3. 同步 en-US 翻译

#### 任务 3.2: 迁移内联 `<i18n>` 块

- [ ] `ViewCasesView.vue` — 将 `<i18n>` 块中的 key 迁移到 `problems.ts` locale 模块
- [ ] `ViewCodeView.vue` — 同上
- [ ] `ViewDescriptionView.vue` — 同上
- [ ] 删除 3 个文件的 `<i18n>` 块

#### 任务 3.3: 统一 request.ts 错误消息

- [ ] 在 `errors.ts` locale 模块中添加: `request.failed`, `request.canceled`, `request.permissionDenied`
- [ ] 修改 `utils/request.ts` 使用 i18n key

---

### Phase 4: 质量加固（P3 — 2~3 天）

> 目标: 防止问题再次出现

#### 任务 4.1: 命名规范统一

- [ ] 将 `dashboard.ts` 和 `table.ts` 中的 28 个 snake_case key 改为 camelCase
- [ ] 更新所有引用这些 key 的代码
- [ ] 在 `docs/i18n-design.md` 中明确: 枚举值用 ALL_CAPS，其余用 camelCase

#### 任务 4.2: 增强 check.ts

- [ ] 添加"代码中使用但 locale 中缺失"的检测（当前只检测两个 locale 间的差异）
- [ ] 添加动态 key 前缀的完整性检查
- [ ] 输出 JSON 报告便于 CI 消费

#### 任务 4.3: CI/CD 集成

- [ ] 在 GitHub Actions 中添加 i18n 检查 step
- [ ] 添加 pre-commit hook 运行 `check.ts`
- [ ] 将 i18n 完整性检查作为 PR 合并的必要条件

#### 任务 4.4: 动态 key 安全网

- [ ] 为所有动态 key 拼接添加 fallback 值: `` t(`key.${dynamic}`, dynamic) ``
- [ ] 考虑使用类型安全的 key 映射表替代模板字符串

---

## 五、工作量估算

| Phase | 优先级 | 工作量 | 影响 |
|-------|--------|--------|------|
| Phase 1 | 🔴 P0 | 1~2 天 | 修复 196 个失效 key + 28 个缺失翻译 |
| Phase 2 | 🟠 P1 | 2~3 天 | 修复全部日期/数字/货币格式化 |
| Phase 3 | 🟡 P2 | 3~5 天 | 清理 122+ 处硬编码文本 |
| Phase 4 | 🟢 P3 | 2~3 天 | CI 集成 + 命名规范 + 安全网 |
| **合计** | | **8~13 天** | |

---

## 六、验证方法

```bash
# 1. 运行翻译完整性检查
cd management && npx tsx src/i18n/check.ts

# 2. 切换到 en-US 检查是否有原始 key 暴露
# 在浏览器中: LanguageSwitcher → English → 遍历所有页面

# 3. 构建检查
pnpm type-check && pnpm build

# 4. 运行现有测试
pnpm test

# 5. 搜索残留硬编码中文
grep -rn '[一-鿿]' src/ --include='*.vue' --include='*.ts' | grep -v 'i18n/' | grep -v 'node_modules' | grep -v '/\*'

# 6. 搜索残留硬编码英文（排除注释和 import）
grep -rn '>[A-Z][a-z]' src/views/ --include='*.vue' | grep -v 't(' | grep -v '<!--'
```

---

## 七、相关文件索引

| 文件 | 用途 |
|------|------|
| `src/i18n/index.ts` | i18n 实例创建与配置 |
| `src/i18n/types.ts` | 类型定义 (SupportedLocale, MessageSchema) |
| `src/i18n/utils.ts` | 格式化工具函数 |
| `src/i18n/check.ts` | 翻译完整性检查脚本 |
| `src/i18n/utils/storage.ts` | Locale 持久化 |
| `src/i18n/__tests__/table-keys.spec.ts` | 表格列名 key 一致性测试 |
| `src/composables/useLocale.ts` | 推荐的 locale composable |
| `src/components/LanguageSwitcher.vue` | 语言切换组件 |
| `src/lib/format/date.ts` | 日期格式化（需修复） |
| `src/utils/request.ts` | HTTP 请求（含硬编码错误消息） |
| `src/utils/error.ts` | 错误处理（框架设计良好） |
| `docs/i18n-design.md` | i18n 设计文档 |
