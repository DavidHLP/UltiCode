# 标签/徽章统一化变更计划

> 生成日期: 2026-06-01
> 扫描范围: `console/`, `management/`, `shared/`

---

## 现状诊断

项目中存在 **两套并行的 Badge 系统**，且使用方式高度分散：

| 系统 | 位置 | 变体数 | 使用方式 |
|------|------|--------|---------|
| shadcn-vue `<Badge>` | 两端共用 | 4 (`default/secondary/destructive/outline`) | 模板组件 + CVA variant |
| `.terminal-badge-*` CSS | management 专用 | 7 (`success/warning/error/info/purple/electric/neutral`) | CSS 类名直挂 / `SemanticBadge.vue` / `useSemanticBadge.ts` |

### 核心问题

1. **同义重复** — management 同时用 `<Badge>` 和 `terminal-badge` 渲染同类数据（如难度、角色），语义相同但视觉不同
2. **无集中映射** — console 端每个 View 自行硬编码 variant 映射，无 `semantic-maps.ts` 等价物
3. **CSS 直挂** — management 有 6+ 个文件直接拼接 `terminal-badge terminal-badge-${color}` 字符串，绕过组件
4. **console 无终端风格** — console 的 CSS 里定义了 `terminal-badge` 但无 `.vue` 文件使用，标签仍用 shadcn 默认样式
5. **颜色不一致** — 难度 `EASY` 在 management Badge 中是 `default`(primary 蓝)，在 terminal-badge 中是 `success`(绿)，在 console 中是 `text-emerald`

---

## 现有 Badge 组件清单

### CSS 变量 (两端 `:root`)

| 变量 | oklch 值 |
|------|----------|
| `--terminal-green` | `oklch(0.6444 0.1508 118.6)` |
| `--terminal-amber` | `oklch(0.6545 0.134 85.7)` |
| `--terminal-red` | `oklch(0.5863 0.2064 27.1)` |
| `--terminal-cyan` | `oklch(0.6437 0.1019 187.4)` |
| `--terminal-purple` | `oklch(0.5924 0.2025 355.9)` |
| `--accent-electric` | `oklch(0.6149 0.1394 244.9)` |
| `--silver-500` | `oklch(0.5682 0.0285 221.9)` (light) / `oklch(0.6537 0.0197 205.3)` (dark) |

### CSS 类 (`.terminal-badge-*`)

定义于 `management/src/style.css` (line 910-950) 和 `console/src/style.css` (line 782-828):

| 类名 | 颜色变量 | 公式 |
|------|----------|------|
| `.terminal-badge-success` | `--terminal-green` | bg: 15% opacity, border: 30% opacity, text: full |
| `.terminal-badge-warning` | `--terminal-amber` | 同上 |
| `.terminal-badge-error` | `--terminal-red` | 同上 |
| `.terminal-badge-info` | `--terminal-cyan` | 同上 |
| `.terminal-badge-purple` | `--terminal-purple` | 同上 |
| `.terminal-badge-electric` | `--accent-electric` | 同上 |
| `.terminal-badge-neutral` | `--silver-500` | 同上 |
| `.terminal-badge-primary` | `--accent-primary` | console 独有 |

### Vue 组件

| 组件 | 位置 | 变体 | 特性 |
|------|------|------|------|
| `Badge.vue` (shadcn) | 两端 `components/ui/badge/` | default/secondary/destructive/outline | CVA, Reka UI Primitive |
| `TerminalBadge.vue` | `management/components/ui/terminal/` | success/warning/error/info/default | pulse 动画 |
| `SemanticBadge.vue` | `management/components/ui/terminal/` | 7 色 + dot/pulse/size(xs/sm/md) | 终端风格 |
| `ContestStatusBadge.vue` | 两端各自 | 比赛状态专用 | console 用 `<Badge>`, management 用 raw `<span>` |
| `RatingBadge.vue` | `console/views/contest/components/` | 连续数值着色 | 内联 style |
| `AchievementBadge.vue` | `console/components/achievement/` | 圆形成就徽章 | 完全自定义 |
| `NotificationBadge.vue` | `console/components/notification/` | 红色数字角标 | Bell + Badge + Popover |

### TypeScript 工具

| 文件 | 位置 | 用途 |
|------|------|------|
| `semantic-types.ts` | `management/components/ui/terminal/` | `SemanticColor` 类型 + `BadgeOptions` 接口 |
| `useSemanticBadge.ts` | `management/components/ui/terminal/` | `badge()` VNode 渲染函数 |
| `semantic-maps.ts` | `management/components/ui/terminal/` | 所有枚举→SemanticColor 映射 |
| `lib/ui/roles.ts` | `management/lib/` | `getRoleBadgeVariant()` 返回 `BadgeVariant` |
| `lib/entities/*.ts` | `management/lib/` | 各实体的 `get*BadgeVariant()` 函数 |
| `views/audit/utils.ts` | `management/views/audit/` | `COLOR_TO_CLASS` 重复映射 |

---

## 完整枚举映射清单

### 1. Problem Difficulty

| 值 | 显示 (i18n key) | Badge variant | Terminal SemanticColor | Console 文字色 |
|---|---|---|---|---|
| `EASY` | `problem.difficulty.easy` | `default` (primary) | `success` | `text-[var(--terminal-green)]` |
| `MEDIUM` | `problem.difficulty.medium` | `secondary` | `warning` | `text-[var(--terminal-amber)]` |
| `HARD` | `problem.difficulty.hard` | `destructive` | `error` | `text-[var(--terminal-red)]` |

### 2. User Role

| 值 | 显示 | Badge variant | SemanticColor |
|---|---|---|---|
| `SUPER_ADMIN` | Super Admin | `destructive` | `purple` |
| `ADMIN` | Admin | `default` | `info` |
| `MODERATOR` | Moderator | `secondary` | `warning` |
| `USER` | User | `outline` | `neutral` |

### 3. User Status

| 值 | 显示 | Badge variant | SemanticColor |
|---|---|---|---|
| ACTIVE | Active | `default` | `success` |
| INACTIVE | Inactive | `secondary` | `neutral` |
| BANNED | Banned | `destructive` | `error` |

### 4. Contest Status

| 值 | 显示 | SemanticColor | Console Badge | Console 自定义 CSS |
|---|---|---|---|---|
| `draft` | Draft | `neutral` | `secondary` | — |
| `published` | Published | `electric` | — | — |
| `registering` | Registering | `success` | — | — |
| `upcoming` | Upcoming | `warning` | `outline` | amber bg+text+border |
| `ongoing` / `RUNNING` | Running | `error` | `outline` | red bg+text+border, pulse dot |
| `freezing` | Freezing | `purple` | — | — |
| `finished` | Finished | `neutral` | `outline` | muted bg+text+border |
| `archived` | Archived | `neutral` | — | — |
| `CANCELLED` | Cancelled | — | `secondary` | — |

### 5. Contest Type

| 值 | SemanticColor |
|---|---|
| `IOI` / `ICPC` / `VIRTUAL` | `info` |
| `PUBLIC` | `success` |
| `PRIVATE` | `warning` |
| `CUSTOM` | `electric` |

### 6. Submission Status

| 值 | SemanticColor | Pulse |
|---|---|---|
| `ACCEPTED` | `success` | no |
| `PENDING` / `JUDGING` | `warning` | yes |
| `WRONG_ANSWER` / `TLE` / `MLE` / `RE` / `CE` | `error` | no |

### 7. Solution Visibility / Approval

| Visibility | SemanticColor | Approval | SemanticColor |
|---|---|---|---|
| PUBLIC | `success` | approved | `success` |
| PRIVATE | `warning` | rejected | `error` |
| HIDDEN | `neutral` | pending | `warning` |

### 8. Forum Post Status

| 条件 | SemanticColor |
|---|---|
| isDeleted | `error` |
| isLocked | `warning` |
| isPinned | `success` |
| isFlagged | `error` |
| default | `neutral` |

### 9. Moderation Status

| 值 | SemanticColor |
|---|---|
| `PENDING` | `warning` |
| `UNDER_REVIEW` | `info` |
| `RESOLVED` | `success` |
| `DISMISSED` | `error` |
| `APPEAL_PENDING` | `purple` |

### 10. Moderation Report Category

| 值 | SemanticColor |
|---|---|
| `SPAM` / `MISINFORMATION` / `WRONG_ANSWER` | `warning` |
| `HARASSMENT` / `HATE_SPEECH` / `VIOLENCE` / `SEXUAL_CONTENT` | `error` |
| `COPYRIGHT` | `purple` |
| `OTHER` | `neutral` |

### 11. Notification Type

| 值 | SemanticColor |
|---|---|
| `SYSTEM` / `COMMENT` / `REPLY` / `MENTION` | `info` |
| `CONTEST` | `success` |
| `SUBMISSION` | `warning` |

### 12. Audit Action (动态匹配)

| Action 包含 | SemanticColor |
|---|---|
| `CREATE` / `GRANT` / `PUBLISH` | `success` |
| `UPDATE` / `UNBAN` | `info` |
| `DELETE` / `BAN` / `REVOKE` | `error` |
| 其他 | `info` |

### 13. Comment Type & Status

| Type | SemanticColor | Status | SemanticColor |
|---|---|---|---|
| forum | `neutral` | deleted | `error` |
| solution | `neutral` | flagged | `error` |
| | | active | `success` |

### 14. Problem List Visibility

| 值 | SemanticColor |
|---|---|
| `PUBLIC` | `success` |
| `PRIVATE` | `warning` |
| `UNLISTED` | `neutral` |

---

## 变更计划

### Phase 0: 共享层提取

在 `shared/` 中新建 `badge-config` 模块，建立唯一的枚举→语义映射：

```
shared/
└── badge-config/
    ├── semantic-colors.ts          # SemanticColor 类型 (7色)
    ├── maps/
    │   ├── difficulty.ts           # EASY→success, MEDIUM→warning, HARD→error
    │   ├── user-role.ts            # SUPER_ADMIN→purple, ADMIN→info, ...
    │   ├── user-status.ts          # ACTIVE→success, INACTIVE→neutral, BANNED→error
    │   ├── contest-status.ts       # draft→neutral, ongoing→error, ...
    │   ├── contest-type.ts         # PUBLIC→success, PRIVATE→warning, ...
    │   ├── submission-status.ts    # ACCEPTED→success, PENDING→warning, ...
    │   ├── moderation-status.ts    # PENDING→warning, RESOLVED→success, ...
    │   ├── forum-status.ts         # deleted→error, locked→secondary, ...
    │   ├── solution-visibility.ts  # PUBLIC→success, PRIVATE→warning, ...
    │   ├── notification-type.ts    # SYSTEM→info, CONTEST→success, ...
    │   └── audit-action.ts         # CREATE→success, DELETE→error, ...
    ├── icons.ts                    # 每个枚举值的可选图标映射
    └── index.ts                    # 统一导出
```

**为什么放在 `shared/`？** — 两端都需要读取同一份映射，避免 console 和 management 各维护一套。

### Phase 1: management — 统一到 terminal-badge 体系 ✅ COMPLETE

> **Status**: ✅ Complete (2026-06-01)
> **Report**: `.claude/PRPs/reports/badge-unification-phase1-management-report.md`
> **Changes**: 16 files, +134 / -334 lines (net -200)

**目标**: 所有状态/枚举标签统一使用 `SemanticBadge` 组件或 `useSemanticBadge.ts` 的 `badge()` 函数。

| 文件 | 当前 | 变更 |
|------|------|------|
| `management/src/lib/entities/problem.ts` | `h(Badge, { variant: getDifficultyBadgeVariant })` | 改用 `badge(difficulty, 'problem.difficulty')` |
| `management/src/lib/entities/user.ts` | `h(Badge, { variant })` | 改用 `badge(role, 'users.role')` |
| `management/src/lib/entities/forum.ts` | `h(Badge, { variant })` | 改用 `badge(status, 'forum.status')` |
| `management/src/lib/entities/solution.ts` | `h(Badge, { variant })` | 改用 `badge(visibility, 'solutions.visibility')` |
| `management/src/lib/entities/comment.ts` | `h(Badge, { variant })` | 改用 `badge(status, 'comments.status')` |
| `management/src/lib/entities/problem-list.ts` | `h(Badge, { variant })` | 改用 `badge(visibility, 'problemLists.visibility')` |
| `management/src/views/problem-lists/components/ProblemsManager.vue` | 直挂 CSS `terminal-badge-${color}` | 改用 `<SemanticBadge>` |
| `management/src/views/notifications/NotificationsListView.vue` | 直挂 CSS `terminal-badge ${class}` | 改用 `<SemanticBadge>` |
| `management/src/views/audit/AuditLogDetailDrawer.vue` | 直挂 CSS `terminal-badge terminal-badge-info` | 改用 `<SemanticBadge color="info">` |
| `management/src/views/audit/AuditLogsView.vue` | 直挂 CSS | 改用 `badge()` |
| `management/src/views/audit/utils.ts` | 本地 `COLOR_TO_CLASS` 重复映射 | 删除，引用共享 `semantic-colors` |
| `management/src/views/problems/components/ProblemAuditDrawer.vue` | 直挂 CSS | 改用 `badge()` |
| `management/src/views/contests/components/ScoringRuleSelector.vue` | 直挂 CSS `terminal-badge-success` | 改用 `<SemanticBadge color="success">` |
| `management/src/views/system/MonitoringView.vue` | `<Badge variant="default/destructive">` | 改用 `<SemanticBadge>` |

**保留 `<Badge>` 的场景**（不改）：

- 标签筛选器（选中/未选中 toggle）— `variant="default"/"outline"` 是 UI 交互态，非数据语义
- 表单内的选项标记（如语言选择、标签选择）— 同上

### Phase 2: console — 引入 SemanticBadge 组件 ✅ COMPLETE

> **Status**: ✅ Complete (2026-06-01)
> **Report**: `.claude/PRPs/reports/badge-unification-phase2-console-report.md`
> **Changes**: 11 files (5 created, 6 modified)

console 端当前完全没有终端风格标签。变更：

1. **复制核心文件到 console**:
   - `console/src/components/ui/terminal/SemanticBadge.vue`
   - `console/src/components/ui/terminal/semantic-types.ts`
   - `console/src/components/ui/terminal/useSemanticBadge.ts`

2. **从 `shared/badge-config/` 导入映射**，替代各 View 中的硬编码：

| 文件 | 当前 | 变更 |
|------|------|------|
| `console/src/views/problems/description/DescriptionView.vue` | `text-[var(--terminal-green)]` 内联 | `<SemanticBadge :color="getDifficultyColor(diff)">` |
| `console/src/views/contest/components/ContestStatusBadge.vue` | 自定义 CSS + `<Badge>` 包装 | 改用 `<SemanticBadge :color="getContestStatusColor(status)">` |
| `console/src/views/problems/submissions/SubmissionsListView.vue` | `text-[var(--terminal-green)]` 内联 | `<SemanticBadge :color="getSubmissionStatusColor(status)">` |
| `console/src/views/personal/ForumPostsView.vue` | `<Badge>` + 硬编码 variant | `<SemanticBadge>` |
| `console/src/views/personal/SolutionsView.vue` | `<Badge>` + 硬编码 variant | `<SemanticBadge>` |
| `console/src/views/problem-list/ProblemListView.vue` | `<Badge>` + 动态 variant | `<SemanticBadge>` |
| `console/src/views/contest/components/MyContests.vue` | `<Badge>` + `getStatusBadge()` | `<SemanticBadge>` |

**保留 `<Badge>` 的场景**（不改）：

- 通知徽章 (`NotificationBadge.vue`) — 红色数字角标，非语义标签
- 侧边栏菜单徽章 (`SidebarMenuBadge.vue`) — 计数器，非语义标签
- 交互式标签筛选 (`TagFilter.vue`) — toggle 状态，非数据语义
- 成就徽章 (`AchievementBadge.vue`) — 完全自定义圆形组件
- RatingBadge (`RatingBadge.vue`) — 连续数值着色，不适用枚举标签

### Phase 3: CSS 同步

1. **console `style.css`** — 确认 `terminal-badge` 基类和 7 个变体类与 management 一致（已基本一致，仅多一个 `terminal-badge-primary`，可保留或移除）
2. **management `style.css`** — 无需变更，已完整
3. **删除冗余映射**:
   - `management/src/views/audit/utils.ts` 中的 `COLOR_TO_CLASS` → 删除
   - `management/src/lib/ui/roles.ts` 中的 `getRoleBadgeVariant()` → 迁移到 `shared/badge-config/user-role.ts`

### Phase 4: 类型统一

```typescript
// shared/badge-config/semantic-colors.ts
export type SemanticColor = 'success' | 'warning' | 'error' | 'info' | 'purple' | 'electric' | 'neutral'

// 替代 management 端的 BadgeVariant
// 新增: 管理端的 get*BadgeVariant 函数全部改为返回 SemanticColor
```

`BadgeVariant` 类型（`default/secondary/destructive/outline`）仅保留给交互式 `<Badge>` 使用，枚举数据标签全部切换到 `SemanticColor`。

---

## 颜色语义规范（最终版）

| SemanticColor | oklch 变量 | 语义含义 | 典型场景 |
|---|---|---|---|
| `success` | `--terminal-green` | 成功/活跃/公开/简单 | ACTIVE, ACCEPTED, PUBLIC, EASY, RESOLVED |
| `warning` | `--terminal-amber` | 警告/待处理/中等 | PENDING, MEDIUM, PRIVATE, JUDGING |
| `error` | `--terminal-red` | 错误/拒绝/困难 | BANNED, HARD, DELETED, WRONG_ANSWER |
| `info` | `--terminal-cyan` | 信息/管理/类别 | ADMIN, SYSTEM, UNDER_REVIEW, IOI |
| `purple` | `--terminal-purple` | 高级/特殊 | SUPER_ADMIN, APPEAL_PENDING, COPYRIGHT |
| `electric` | `--accent-electric` | 电光蓝/自定义 | CUSTOM, PUBLISHED |
| `neutral` | `--silver-500` | 中性/默认/归档 | USER, DRAFT, FINISHED, ARCHIVED |

---

## 影响范围评估

| 维度 | 数量 |
|------|------|
| 需修改的 management 文件 | ~15 个 |
| 需修改的 console 文件 | ~8 个 |
| 新增 shared 文件 | ~12 个 |
| 新增 console 组件 | 3 个（SemanticBadge + types + composable） |
| 可删除的重复代码 | `audit/utils.ts` COLOR_TO_CLASS, `roles.ts` getRoleBadgeVariant, 各 entity 文件中的 BadgeVariant |
| 需更新的测试 | `LivePreviewPanel.spec.ts` (查询 `.terminal-badge`) |

---

## 执行顺序

```
Phase 0 (shared) → Phase 1 (management) → Phase 2 (console) → Phase 3 (CSS) → Phase 4 (类型清理)
```

Phase 0 和 Phase 1 可以并行启动（Phase 1 先用 management 现有的 semantic-maps，Phase 0 完成后切换 import 来源）。
