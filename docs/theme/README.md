---
title: Theme System — 主题系统总览
tags: [index, reference, frontend, living]
status: living
updated: 2026-06-19
owner: design-system
---

# Theme System — 主题系统总览

> **读者**：所有在 `console/`、`management/`、`shared/design-system/`、`shared/theme/` 里**写、改、查**
> 颜色/字体/密度/动效/组件样式的前端工程师。
>
> 本目录是 UltiCode 前端**主题系统**的单一真相源。代码改动以本文档为依据，文档与代码冲突时**以代码为准**，
> 但请同步回写本文档。

## 这是什么

UltiCode 的"主题" = **设计 Token（Design Tokens）+ 颜色模式（Color Mode）+ 密度（Density）+ 组件级语义类
（Component Primitives）** 的总称。它由三块组成：

| 层           | 物理位置                          | 责任                                                                    |
| ------------ | --------------------------------- | ----------------------------------------------------------------------- |
| **状态层**   | `shared/theme/src/`               | `ThemeMode` 类型、`localStorage` 抽象、`useColorTheme` 组合式、密度写入 |
| **Token 层** | `shared/design-system/style.css`<br>`shared/theme/src/typography.css` | CSS 变量定义、Tailwind v4 `@theme inline` 暴露、工具类 |
| **引导层**   | `console/public/theme-bootstrap.js`<br>`management/public/theme-bootstrap.js` | 在 Vue bundle 之前应用 `.dark`，消除 FOUC |

任何颜色、字体、间距、阴影、动效都**只允许**通过这三层暴露的 token 引用。直接写 `#000`、
`12px`、`JetBrains Mono` 等具体值会被 Code Review 拦截（参见
[`shared/theme/CLAUDE.md` 的 `frontend-rules.md`](../../.claude/rules/frontend-rules.md)）。

## 文件地图

```
docs/theme/                         ← 本目录（设计文档）
├── README.md                       ← 你正在读
├── architecture.md                 ← 分层架构、文件归属、加载时序、生命周期
├── design-tokens.md                ← 全部 Design Token 速查（颜色/字体/间距/阴影/动效/…）
├── theme-modes.md                  ← 颜色模式（light/dark/system）+ 密度（compact/comfortable）+ 切换
├── components.md                   ← 组件级 override 模式与现成原语（terminal-*/precision-*/animation/…）
└── extension.md                    ← 扩展指南：新增 token / 主题 / 密度 / 组件模式
```

## 推荐阅读顺序

| 你的工作                       | 必读                                         | 选读                                  |
| ------------------------------ | -------------------------------------------- | ------------------------------------- |
| 改一个组件的样式 / 颜色        | `components.md` §3                          | `design-tokens.md`（找具体 token 名）|
| 加一个新页面的视觉             | `components.md` §1、§2                      | `design-tokens.md` §1、§2            |
| 写新组件的样式系统             | `design-tokens.md`、`components.md` §3      | `architecture.md` §3                 |
| 调整 light/dark 行为           | `theme-modes.md` §1、§2                     | `architecture.md` §2                 |
| 加新颜色模式（如高对比度）     | `theme-modes.md` §4                         | `extension.md` §2                    |
| 调整 console/management 密度   | `theme-modes.md` §3                         | `extension.md` §3                    |
| 加新 token / 工具类 / 原语     | `extension.md`                              | `design-tokens.md`                    |

## 关键约束（先记住这 5 条）

1. **颜色只能用 `oklch()` 表达**，禁止 hex / rgb / hsl。Solarized 调色板的所有色值
   已经在 [[design-tokens#1 颜色 Color|`design-tokens.md` §1]] 列全。
2. **Token 引用一律 `var(--uc-*)` 或 `var(--silver-*)`**，不写裸字面量。Tailwind 工具类
   已是 token 的别名（`text-sm` → `--uc-text-sm`），所以 `class="text-sm font-sans"` 是合法的。
3. **`.dark` 是唯一的颜色模式开关**，由 `applyThemeToDOM()` 写到 `<html>`。
   **不要**用 `prefers-color-scheme` 媒体查询——会与 `system` 模式冲突。
4. **密度属性写在 `<html>` 的 `data-uc-density` 上**，由 `applyTypographyDensity()`
   唯一写入。组件中**禁止**直接 `document.documentElement.dataset.ucDensity = '...'`。
5. **任何 `shared/` 改动必须双端验证**（`pnpm test` + `pnpm type-check` 在包里跑，
   然后 console + management 都跑一遍构建）——这是根 `AGENTS.md` 的横切要求。

## 改动如何被收录

| 触发场景                            | 必须同步的文档                                  |
| ----------------------------------- | ----------------------------------------------- |
| 新增 / 改名 / 删一个 `--uc-*` token | `design-tokens.md` §2                          |
| 新增 / 改名 / 删一个工具类          | `components.md` §3、§4                          |
| 新增颜色模式                        | `theme-modes.md` §4 + `extension.md` §2         |
| 新增密度档                          | `theme-modes.md` §3 + `extension.md` §3         |
| 改 `shared/theme/src/index.ts` 公共 API | `architecture.md` §1                            |
| 改 `theme-bootstrap.js` 行为        | `theme-modes.md` §1、§2                         |

PR 描述里附上文档同步的 checklist（参见根 [[SCHEMA#8 更新流程|`SCHEMA.md` §8]]）。

## 参见

- [[frontend|docs/CODEMAPS/frontend.md]] — 仓库前端架构、路由、store
- [[SCHEMA|docs/SCHEMA.md]] — 文档 wiki schema（三层 / 三动作 / frontmatter / 命名 / 链接）
- `shared/theme/src/` — 状态层代码（`ThemeMode.ts` / `storage.ts` / `useTheme.ts` / `applyThemeToDOM.ts`）
- `shared/design-system/style.css` — Token 层主文件（1257 行）
- `shared/theme/src/typography.css` — 字体 Token 主文件
