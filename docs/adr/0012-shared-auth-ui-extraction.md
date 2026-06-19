---
title: Extract auth UI components and view shells into shared/auth-ui
tags: [adr, auth, frontend, accepted]
status: accepted
updated: 2026-06-19
date: 2026-06-19
deciders: architect, frontend
supersedes: N/A
superseded_by: N/A
---

# 0012 — Extract auth UI components and view shells into `shared/auth-ui`

## 背景

`console/` (9002) 与 `management/` (9003) 在 2026-06 之前各自维护一套并行的 auth 页面组件目录（`views/auth/components/`），包含 `AuthButton / AuthCard / AuthDivider / AuthGrid / AuthInput / AuthThemeToggle / OAuthButton / LoginForm / RegisterForm` 共 8-9 个 Vue SFC。两个目录之间的 8 个组件**字节级相同**，其余 1-2 个只有 1-2 行差异（i18n key 路径或 SVG 路径）。

`shared/auth-core/` 早已承载了 **logic 层**（cookie / csrf / auth-state / axios 拦截器 / permission / refresh coordinator / auth-failure），但 **Vue UI 层** 仍完全重复。下沉 UI 既消除重复，又能：

- 让 OAuth 图标样式、`aria-hidden`、`focus-visible` 这类安全默认值在单一来源维护；
- 让 `AuthLayout` shell（form-side + pattern-side 网格 + 终端 status 面板）抽出后，console 与 management 的登录页只差 badge 文案 + 表单子组件；
- 后续新增 auth 页面（如 magic-link、2FA）只在 `shared/auth-ui/` 加一个表单，不再两个 app 各写一遍。

约束：

- `useAuthStore`（Pinia 单例）30+ 消费者跨两个 app，per-app 实例语义复杂，**不在本期下沉**。
- 两个 app 的 `User` 类型字段命名不同（console camelCase、management snake_case 走 `shared/auth-core`），跨端统一 `User` 影响面过大，**不在本期下沉**。
- `shared/badge-config/`、`shared/theme/` 等老共享包仍走"`console/src/shared -> ../../shared`" symlink；本期新增 `shared/auth-ui` 一并升为真 pnpm workspace 成员，让其拥有独立 `node_modules`，消除两个 app 在 `vite.config.ts` 里手工维护的 alias。

## 决策

我们将在 `shared/` 下新增 `auth-ui/` 包，作为两个前端共同消费的 Vue UI 库：

1. **`shared/auth-ui/src/components/`** — 9 个纯 UI 组件（`AuthButton / AuthCard / AuthDivider / AuthGrid / AuthInput / AuthThemeToggle / OAuthButton / LoginForm / RegisterForm`）+ `cn` 工具（re-export 自 `shared/auth-core`）。
2. **`shared/auth-ui/src/layouts/`** — `AuthLayout`（双栏 form + pattern shell）和 `AuthPatternBackground`（右侧网格 + 终端 spec 面板），通过 props（`badge / version / statusText / hidePattern / homeHref`）和 slots（`#form / #pattern`）配置。
3. **`shared/auth-ui/src/index.ts`** — barrel：`components/` + `layouts/` + 类型（`AuthPatternLine / AuthLayoutProps`）+ `cn`。
4. **`AuthLayout.vue` + `AuthPatternBackground.vue` 抽出**后，6 个 view 文件（console 4 + management 2）从 400-510 行瘦壳到 25-50 行，全部通过 `:on-submit` 回调接入各 app 的 `useAuthStore`。

**新增 `pnpm-workspace.yaml`**（`packages: ["shared/*", "console", "management"]`），让 `shared/auth-ui` 成为真 workspace 成员，配独立 `node_modules`。两个 app 不再需要在 `vite.config.ts` 手工 alias `vue-i18n / vue-router / lucide-vue-next / clsx / tailwind-merge`。

**`cn` 工具统一**：在 `shared/auth-core/src/utils.ts` 新建单一来源，`console/src/lib/utils.ts`、`management/src/lib/utils.ts`、`shared/auth-ui/src/components/cn.ts` 全部 re-export。

**`RegisterForm` payload `name` 语义修正**（M2）：移除 `name: username.value` 的隐式覆盖；新增可选 Name 输入框 + `showName` prop；payload 只在用户填写时才含 `name`。同时新增 `fieldErrors` prop 把服务端字段级错误渲染到对应 `<AuthInput>` 的 `:error`，避免吞在全局 `error` ref 里。

**i18n key 命名空间对齐**：

- `common.appearance.{light,dark,system}` 由 `AuthThemeToggle` 消费；management 在 `common` 命名空间补 3 个 key。
- `auth.messages.{loginFailed, registerFailed, passwordsDoNotMatch, contactAdmin}` 作为两个 app 共用的 fallback 错误键。
- `auth.register.{name, namePlaceholder}` 为新增的 Name 输入框服务。

## 备选方案

1. **不抽 layout，只抽 8 个组件** — 节省了 ~250 行 layout 模板重复，但 views 仍各自维护 form-side / pattern-side 的布局。新增 `AuthPatternBackground` 是这次抽取里**收益最大的一环**（终端 spec 块在原 LoginView 里 ~80 行），不抽就丧失主要价值。
2. **通过 monorepo + build step（rollup 打 ESM bundle）发布** — 业界标准做法，但本项目其余 `shared/*` 包都是源文件直接由消费方 Vite 解析（`shared/auth-core` 至今没有 build 步骤）；引入打包会破坏"统一风格"，并把 HMR 拖慢。改用 pnpm workspace + 源文件直接消费。
3. **把 `useAuthStore` 也下沉** — 30+ 消费者分布在两个 app 的 store/components/views，Pinia 单例 per-app 语义在共享包里需要 `createAuthStore(options)` 工厂 + 显式注入 API 客户端，影响面跨整个 console 与 management。本期不抽，单独 ADR 再评估。
4. **跨端统一 `User` 类型** — console 用 camelCase (`isActive / joinedAt`)，management 走 `shared/auth-core` 的 snake_case (`is_active / joined_at`)。改名要追所有 store/state/component，影响面广。本期保留 console 自己的 `types/auth.ts`，留 follow-up。
5. **保留 console + management 的 symlink 模式 + 手工 alias** — 在 `vite.config.ts` 手工 alias 5 个共享依赖，能跑但易腐（每次新增 `shared/auth-ui/src/**` 使用的 dep 都要两边同步改）。pnpm workspace 化一劳永逸。

## 影响

**正面**

- 删除 ~5,000 行重复 Vue SFC + 数百行 layout / i18n 重复。
- `cn` 三处实现归一，未来 `clsx`/`tailwind-merge` 升级只动一处。
- `AuthLayout` + `AuthPatternBackground` shell 让两个前端登录页的视觉一致性由代码结构自动保证（同一组件实例），badge 文案和右侧 spec 内容由 props 控制。
- `RegisterForm` 新增 `fieldErrors` prop：服务端能直接喂字段级错误（如 "username taken"）给 `<AuthInput>` 的 `:error`，不污染全局 `error` ref。
- pnpm workspace 让 `shared/auth-ui` 拥有独立 `node_modules`，类型检查 (`pnpm type-check`) 在 shared 包内独立可跑，新增共享组件不必先在两个 app 之一验证。

**负面**

- `shared/auth-ui` 的 `package.json` / `tsconfig.json` / `vitest.config.ts` 三个脚手架文件首次提交；新贡献者需要理解 pnpm workspace 结构（已通过本 ADR + `pnpm-workspace.yaml` 头部注释覆盖）。
- 新增 Name 输入框 + `fieldErrors` props 是 API 增量（向后兼容）；现有调用方不受影响。
- i18n 命名空间统一要求两个 app 在 PR 提交时同步加 key；后续多语言扩展（如日语）需在三个 `auth.*` namespace 都补。

**运维影响**

- `pnpm install` 仍跑在仓库根（`pnpm-workspace.yaml` 已声明所有成员）。
- Vite build 不变：`shared/auth-ui` 的源文件由消费方 Vite 直接 transform；`pnpm-lock.yaml` 行数从 ~1k 增到 ~10k（新增 `shared/auth-ui/node_modules`）。
- 没有 schema 变化、没有 API 端点变化、没有部署脚本变化。

## 参考

- **代码**：
  - `shared/auth-core/src/utils.ts` — `cn` 单一来源
  - `shared/auth-core/src/types.ts` — 新增 `RegisterRequest`
  - `shared/auth-core/src/index.ts` — re-export `cn / RegisterRequest`
  - `shared/auth-ui/src/{components,layouts,__tests__}/` — 13 个 .vue + 1 个 .ts + 4 个 spec
  - `shared/auth-ui/package.json` — peerDeps `vue / vue-i18n / vue-router`，deps `clsx / lucide-vue-next / tailwind-merge`
  - `console/src/views/auth/{Login,Register,ForgotPassword,ResetPassword}View.vue` — 瘦壳
  - `management/src/views/auth/{Login,Signup}View.vue` — 瘦壳
  - `console/src/lib/utils.ts`、`management/src/lib/utils.ts` — re-export `cn`
- **CODEMAPS**：
  - [[CODEMAPS/frontend]] §"Shared Packages" 增加 `auth-ui` 行
  - [[CODEMAPS/architecture]] §"Architecture Decisions" 增加 ADR-012
- **相关 ADR**：
  - [[0008-websocket-cookie-auth|adr/0008]] — WebSocket 鉴权使用 cookie，与本 ADR 共享 cookie/CSRF 决策（但不修改后端）
- **外部**：无（标准 pnpm workspace + Vue 3 共享包组织，无新供应商）