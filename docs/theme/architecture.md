---
title: 主题系统架构
tags: [reference, frontend, living]
status: living
updated: 2026-06-19
owner: design-system
---

# 主题系统架构

> **范围**：解释 UltiCode 前端主题系统的分层、文件归属、加载时序、运行时生命周期，
> 以及"为什么用两个包（`shared/theme` + `shared/design-system`）"。
>
> 配套阅读：[[theme/README]] · [[design-tokens]] ·
> [[theme-modes]]

## 1. 分层

主题系统自下而上分四层。**每一层只依赖下一层**：

```
┌──────────────────────────────────────────────────────────────────────┐
│ L4 · 组件原语（Component Primitives）                                 │
│     shared/design-system/style.css 的 .terminal-* / .precision-* /   │
│     .font-* / .uc-type-* / .animate-* / .hljs-*                       │
│     → 组件代码 import 这些类名，不再写自己的颜色/字面量                │
├──────────────────────────────────────────────────────────────────────┤
│ L3 · 语义角色（Semantic Roles）                                       │
│     --uc-type-body / --uc-type-control / --uc-type-page-title / …     │
│     --uc-type-* 工具类（.uc-type-body, .uc-type-page-title, …）        │
│     .markdown-block / .prose                                          │
│     → "按钮用 control 字号、表格 cell 用 data 字号"这类**意图**        │
├──────────────────────────────────────────────────────────────────────┤
│ L2 · 基础 Token（Foundation Tokens）                                  │
│     颜色 --background/--foreground/--silver-50..900/--terminal-*      │
│     字体 --uc-font-ui/--uc-font-code/--uc-text-xs/--uc-leading-*      │
│     间距/阴影/动效/圆角/滚动条 — Tailwind v4 + 自定义 @theme inline    │
│     → "底层原料"，永远不应该被组件直接读                                │
├──────────────────────────────────────────────────────────────────────┤
│ L1 · 状态机（Theme State）                                            │
│     shared/theme/src/useTheme.ts → 模块单例 Ref<ThemeMode>            │
│     shared/theme/src/storage.ts  → localStorage 抽象                  │
│     shared/theme/src/applyThemeToDOM.ts → <html> dark 类切换           │
│     shared/theme/src/ThemeMode.ts → 'light'|'dark'|'system' 联合类型  │
│     → "用户选了哪个模式、要不要听系统、当前生效哪个"                    │
└──────────────────────────────────────────────────────────────────────┘
        ▲
        │  L0 · 引导（Bootstrap）
        │  console/public/theme-bootstrap.js、management/public/theme-bootstrap.js
        │  在 <script src> 同步执行，避免 Vue bundle 解析完成前出现"白屏→黑屏"闪烁
```

**为什么是四层而不是三层？** L1（状态机）和 L0（引导脚本）必须分开：
引导脚本是**纯 JS、无依赖、必须 inline 在 `<head>`**，不能引 Vue/Pinia。状态机则假定已经在 bundle 里，
可以放心用 `ref`、`watch`、`addEventListener`。

## 2. 文件归属

### 2.1 状态层（`shared/theme/`）

```
shared/theme/
├── package.json
├── src/
│   ├── index.ts                  ← 公共 API 出口（barrel）
│   ├── ThemeMode.ts              ← 类型 + 常量 + 解析
│   ├── storage.ts                ← localStorage 抽象（含内存后端）
│   ├── applyThemeToDOM.ts        ← 纯函数：把 mode 翻译成 <html> 类
│   ├── useTheme.ts               ← 模块单例 + Vue 组合式（useColorTheme）
│   ├── typography.ts             ← 字体 Token 元数据 + applyTypographyDensity
│   └── typography.css            ← 字体基础 Token + 语义角色 + 工具类
└── __tests__/
    ├── typography.spec.ts
    └── useTheme.spec.ts
```

**单一职责**：
- `ThemeMode.ts` 只放类型和常量。所有 `ThemeMode` 消费者从这里 `import { type ThemeMode }`。
- `storage.ts` 是 `localStorage` 的 try/catch 包装 + 测试桩。`getThemeStorage()` 在
  第一次访问时探测环境（浏览器 localStorage / vitest 4 stub / 内存 shim），之后缓存。
- `applyThemeToDOM.ts` 是**纯函数**——同一份逻辑在三个地方运行：FOUC 引导脚本、
  `main.ts` 的早期初始化、模块单例的延迟初始化。
- `useTheme.ts` 是**模块单例**（`theme` ref 在 module scope）。`useColorTheme()`
  只是把它暴露给组件。所有 `console/` 和 `management/` 共享同一份单例实现。
- `typography.ts` 把 CSS 变量名（如 `--uc-type-body-size`）也以 TypeScript
  常量再导出一遍，给 ECharts / Monaco / 测试这些**不能直接读 CSS 变量**的消费者用。

### 2.2 Token 层（`shared/design-system/`）

```
shared/design-system/
└── style.css                     ← 1257 行，3 个逻辑分区
    ├── @import "../theme/src/typography.css"   ← 字体 Token
    ├── @import "tailwindcss"                    ← Tailwind v4
    ├── @import "tw-animate-css"                ← shadcn-vue 动画
    ├── @import "katex/dist/katex.min.css"     ← KaTeX
    ├── @custom-variant dark (&:is(.dark *))   ← 让 Tailwind dark: 变体走 .dark 类
    ├── @layer base                             ← 元素默认样式（html/body/h1-h6/pre/code）
    │   ├── :root { /* Solarized Light tokens */ }
    │   └── .dark { /* Solarized Dark overrides */ }
    ├── @layer base
    │   ├── @theme inline { --font-sans, --font-mono, --text-*, --leading-*, --tracking-* }
    │   └── shadcn-vue CSS 变量桥接（--background, --foreground, --card, --primary, …）
    ├── 组件原语（terminal-*, precision-*, font-*, uc-type-*, animate-*, hljs）
    └── 高级工具（.markdown-block, .header-btn, .ascii-progress, .hljs, .scrollbar-*）
```

**为什么"颜色"和"字体"不在同一个文件？**
- 字体（`typography.css`）已经迁移到了 2026-06 的"集中字体基础"重构（`typography.css` 头部注释有完整来由）。
  它和"状态机"（`useTheme.ts` / `storage.ts`）共属 `shared/theme/` 包，因为字体加载是**运行时关心**的（FOUC 也要管）。
- 颜色 / 阴影 / 间距留在 `shared/design-system/style.css`，因为它和 shadcn-vue / Tailwind 的
  CSS 变量桥接绑得太紧（shadcn-vue 的 `--background` / `--foreground` 必须和我们的 `--background` / `--foreground` 同名）。

### 2.3 引导层（`console/public/`、`management/public/`）

```
console/public/theme-bootstrap.js
management/public/theme-bootstrap.js     ← 两个文件目前几乎完全一样；可考虑未来抽到 shared
```

**为什么需要 inline 引导脚本？**
- Vue + Vite bundle 在 `<body>` 末尾才执行；`<head>` 解析完到 bundle 接管之间
  会有一帧（甚至几帧）的"未主题化"白屏。
- 引导脚本是普通 ES JS（**不是** TS——它跑在 Vite 静态服务上，没经过 TS 编译），
  同步读 `localStorage.ulticode-theme` 并立刻给 `<html>` 加/去 `.dark` 类。
- 在 `applyThemeToDOM` 的注释里写得很清楚："同样的逻辑在三处跑：FOUC 脚本、
  main.ts 早期 init、单例延迟 init"——任何一处的修改都需要在另外两处同步。

## 3. 加载时序

### 3.1 浏览器首屏（按下 F5 到第一次像素）

```
T=0   浏览器解析 HTML
T=ε   解析到 <link rel="preconnect" href="https://cdn.jsdelivr.net">
T=ε   解析到 <script src="/theme-bootstrap.js"></script>
        → 同步读 localStorage.ulticode-theme
        → applyThemeToDOM(mode)            ← <html class="dark"> 落地
T=ε+1 解析到 <script type="module" src="/src/main.ts"></script>  ← Vite 启动模块解析
T=ε+2 main.ts 调 initTheme()
        → 同源 mode → <html class> 重新校准
        → 注册 matchMedia('(prefers-color-scheme: dark)') 监听
T=ε+3 Vue createApp().mount('#app')
T=ε+4 组件 onMounted → useColorTheme()   ← 不再初始化，只读共享 ref
```

**关键点**：T=0 到 T=ε+2 之间 `<html>` 上**没有 Vue、没有 ref**——只有 vanilla JS 的
`document.documentElement.classList`。这就是为什么 `applyThemeToDOM.ts` 必须**无依赖**。

### 3.2 CSS @import 顺序

`shared/design-system/style.css` 顶部：

```css
@import "../theme/src/typography.css";   /* 1. 字体 */
@import "tailwindcss";                    /* 2. Tailwind v4 */
@import "tw-animate-css";                 /* 3. shadcn-vue 动画 */
@import "katex/dist/katex.min.css";       /* 4. KaTeX */
```

顺序意义：字体 Token 必须先于 Tailwind（因为 Tailwind 的 `text-sm` 通过 `@theme inline`
最终解析到 `--uc-text-sm`）；Tailwind 必须先于组件原语（因为 `.terminal-card` 里用了
`@apply border-border`，这个 utility 在 Tailwind 之前不存在）。**不要改动这个顺序**，
否则会有几类工具类失效（详见 [[components#3 Utility 类|`components.md` §3]]）。

### 3.3 主题切换（运行中）

```
用户点击 AuthThemeToggle
   ↓
cycleTheme()                              ← useTheme.ts
   ↓
setTheme(nextMode)                        ← 修改 Ref + 写 localStorage + applyThemeToDOM
   ↓
document.documentElement.classList.toggle('dark', isDark(nextMode))
   ↓
CSS 引擎立刻重新计算所有受 .dark 影响的变量
   ↓
所有组件不需要重新渲染 —— 颜色直接变
```

**性能说明**：颜色切换是纯 CSS 变量重新计算，**不触发 Vue 重渲染**。这是为什么我们
坚持"颜色用 token、不要 inline style"——一旦组件写了 `style="color: #fff"`，主题切换
就**改不动**那一处。

## 4. 公共 API（`shared/theme/src/index.ts`）

```typescript
// 类型 & 常量
export {
  THEME_CYCLE, THEME_MODES, THEME_STORAGE_KEY,
  isThemeMode, parseThemeMode,
  type ThemeMode,
} from './ThemeMode'

// 存储抽象（测试可注入桩）
export { getThemeStorage, setThemeStorage, type ThemeStorage } from './storage'

// 纯 DOM 工具（FOUC 脚本也用）
export { applyThemeToDOM, isDarkMode } from './applyThemeToDOM'

// 字体 Token 元数据 + 密度写入
export {
  TYPOGRAPHY_DENSITIES, TYPOGRAPHY_DENSITY,
  applyTypographyDensity, getTypographyDensity,
  typographyCssVariables, typographyFoundationPrefixes,
  typographySizes, typographyUtilityClasses,
  type TypographyCssVariable, type TypographyDensity,
  type TypographySizeToken, type TypographyUtilityClass,
} from './typography'

// Vue 组合式
export { cycleTheme, initTheme, setTheme, useColorTheme, useTheme }
  from './useTheme'
```

**`__resetForTest` 不在 barrel 里**——它是测试专用入口，生产代码不能 import。
`useTheme` 是 `useColorTheme` 的 deprecated 别名，迁移期保留，新代码必须用 `useColorTheme`。

## 5. 跨端约定

- 改 `shared/theme/` → `cd shared/theme && pnpm test && pnpm type-check` → 两个前端都跑构建
- 改 `shared/design-system/style.css` → 两个前端都跑 `pnpm build` 目视确认
- 改 `theme-bootstrap.js` → **必须**同步 console 和 management 两份（目前是手抄，未来可以
  抽到 `shared/theme/src/bootstrap.js` 通过 Vite 的 `?raw` 导入）

## 参见

- [[design-tokens]] — 颜色/字体/间距/阴影/动效全部 token 速查
- [[theme-modes]] — light/dark/system + compact/comfortable 切换机制
- [[components]] — 组件级 override 模式
- [[extension]] — 新增 token / 主题 / 密度 / 组件的步骤
- [[frontend|docs/CODEMAPS/frontend.md]] — 仓库前端架构总览
