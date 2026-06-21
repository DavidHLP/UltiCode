---
title: 前端应用全景
tags: [mirror, architecture, frontend]
status: living
updated: 2026-06-21
owner: frontend
---

<!-- mirror: 手写 -->

# 前端应用全景

> Vue 3.5 + TypeScript + Vite + Pinia + Vue Router + Tailwind v4 + shadcn-vue(reka-ui)。两个前端 + 一个共享层。console=9002（用户端），management=9003（管理端），PM2 管。

## console（用户端，`console/src/`）

主要视图域：

| 域 | 视图 |
| --- | --- |
| 着陆/认证 | `LandingView`、`auth/{Login,Register,ForgotPassword,ResetPassword}View` |
| 题目 | `problems/ProblemDetailView`、`problems/code/CodeView`（含 `CodeEditor`）、`problem-set/ProblemSetView`、`problem-list/` |
| 比赛 | `contest/{ContestHome,Browse,My,Rankings}View`、`contest/detailed/ContestDetailView`、`VirtualContestTimer`（见 [[virtual-contest]]） |
| 论坛 | `forum/{ForumFeed,Thread,Editor,Feedback,Guidelines}View` |
| 个人 | `personal/{Personal,Account,Bookmarks,Notifications,Submissions,Solutions,ProblemLists,Subscription,ForumPosts}View`（含热力图/雷达图/进度图组件） |
| 其他 | `dashboard/PersonalDashboardView`、`achievements/AchievementGalleryView`、`post-editor/solutions/` |

API 调用模式：直接 `apiGet/apiPost`（`utils/request.ts` 统一 Axios + CSRF + 401 重试）。stores：`contest / contestProblemShell / editorSettings / header / notification / problemEditor / userStats`。

## management（管理端，`management/src/`）

| 域 | 视图 |
| --- | --- |
| 总览 | `dashboard/DashboardView`、`analytics/`（5 报表：UserActivity/Performance/ProblemCompletion/ContestParticipation/Revenue）、`audit/` |
| 比赛 | `contests/`（含 6 步 wizard：BasicInfo/Schedule/Problems/ScoringRule/Review）+ `ScoringRulesView` + `contest/`（运行态） |
| 内容 | `problems/`（编辑表单：Description/Code/Cases/Constraints/Examples/Hints）、`problem-lists/`、`solutions/`、`comments/` |
| 审核 | `moderation/{ModerationDashboard,Queue,Reports,Appeals}View`（含 BatchAction / ActionHistoryTimeline） |
| 用户/系统 | `users/`、`notifications/`、`tags/`、`settings/`、`system/`、`account/`、`help/` |

API 模式：**typed 封装**（`api/<area>.ts` 导出带类型函数，如 `moderationQueueApi`）。DataTable 列名 i18n：`t('table.columnNames.${columnId}')`。有 Playwright E2E。

## shared（`shared/`，7 个包）

| 包 | 职责 |
| --- | --- |
| `auth-core` | cookie / csrf / auth-state / permission / refreshCoordinator / axiosCsrfInterceptor —— 跨端认证内核 |
| `auth-ui` | LoginForm / RegisterForm / OAuthButton / AuthLayout —— 认证 UI 抽取 |
| `badge-config` | SemanticBadge / 语义色 |
| `sandbox-types` | envelope / input-spec / verdict / per-case / oj-type —— 沙箱契约（见 [[sandbox-d-form]]） |
| `theme` | applyThemeToDOM / ThemeMode / useTheme / typography —— 主题系统（见 [[theme/README]]） |
| `design-system` | style.css |
| （root `package.json`） | workspace 协调 |

> ⚠️ `shared/auth-core` 改动必须在该包内 `pnpm test + type-check`，并在两个前端验证。

## 关联

- 主题系统 → [[theme/README]]
- 沙箱契约类型源 → [[sandbox-d-form]]
- 认证内核 → [[refresh-token]]
- 后端对应模块 → [[backend-modules]]
