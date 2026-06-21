---
title: 主题系统（Theme）
tags: [theme, frontend, architecture]
status: living
updated: 2026-06-21
owner: frontend
aliases: [主题, theme]
sources:
  - shared/theme/src/
  - console/public/theme-bootstrap.js
  - management/public/theme-bootstrap.js
  - shared/design-system/style.css
---

# 主题系统（Theme）

> 前端主题系统的切面入口：分层、token、颜色模式、密度、字体、bootstrap（FOUC）。代码真源在 `shared/theme/`。本页是后续展开专题的锚点。

## 四层分层

| 层 | 职责 | 位置 |
| --- | --- | --- |
| **state** | 用户选择（mode + density），持久化 | `shared/theme/src/` |
| **tokens** | 设计 token（颜色 / 间距 / 字号…） | `shared/design-system/style.css` |
| **primitives** | 组件原语（shadcn-vue / reka-ui） | `console|management/src/components/ui/` |
| **bootstrap** | 消除 FOUC 的外置首屏脚本 | `console/public/theme-bootstrap.js`、`management/public/theme-bootstrap.js` |

## 颜色模式（`ThemeMode`）

`shared/theme/src/ThemeMode.ts`：

- `THEME_MODES = ['light', 'dark', 'system']`（`THEME_STORAGE_KEY = 'ulticode-theme'`）。
- `isThemeMode` / `parseThemeMode(value, fallback='system')` 校验与强制转换。
- 组合密度：`light/dark/system` × `compact/comfortable`。

`useTheme`（=`useColorTheme`）导出 `initTheme() / setTheme(mode) / cycleTheme()`，`AuthThemeToggle.cycleTheme` 按 `THEME_CYCLE` 顺序循环。

## 字体（全站统一）

**项目字体 = LXGW WenKai 楷体**，全站统一（含 Monaco 编辑器、ECharts 默认字体）。改前端字体在此统一，勿散落。

## Bootstrap 与 FOUC（强制单点）

`console/public/theme-bootstrap.js` 与 `management/public/theme-bootstrap.js` 是为消除 FOUC 引入的**外置脚本**，逻辑与 `shared/theme/src/applyThemeToDOM.ts` 一致。

> **禁止**在别处（`main.ts` 内联、组件 `onMounted` 等）重写一份 theme 初始化——重复实现会与 `shared/theme` 单例产生 hydration 不一致。
> 未来引入严格 CSP（无 `'unsafe-inline'`）时，需为 `<script src="/theme-bootstrap.js">` 加 nonce/hash，并同步更新 `index.html`。

## 主题同步守护

- `scripts/verify-theme-sync.mjs` —— 校验 theme 同步。
- `scripts/verify-typography-tokens.mjs` / `scripts/dev/typography-guard.sh` —— 字体 token 守护。

## 关联

- 前端全景 → [[codemap/frontend-apps]]
- 认证 UI（含 `AuthThemeToggle`）→ `shared/auth-ui`，见 [[codemap/frontend-apps]]
