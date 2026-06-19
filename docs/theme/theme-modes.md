---
title: 主题模式与密度
tags: [reference, frontend, living]
status: living
updated: 2026-06-19
owner: design-system
---

# 主题模式与密度

> **范围**：UltiCode 前端的**两种模式切换**机制——颜色（light / dark / system）
> 和密度（comfortable / compact）。覆盖公共 API、运行时生命周期、FOUC 防止、
> 以及"如何加新模式"。
>
> 配套阅读：[[theme/architecture]] · [[design-tokens]] ·
> [[components]]

## 0. 两个独立维度

| 维度           | 取值                          | 由谁控制              | 写在哪儿                  |
| -------------- | ----------------------------- | --------------------- | ------------------------- |
| **颜色模式**   | `light` / `dark` / `system`   | 用户                  | `<html class="dark">`     |
| **密度**       | `comfortable` / `compact`     | 应用（构建时定）      | `<html data-uc-density>`  |

它们**正交**：可以同时是 `light + compact`（management 默认）、`dark + comfortable`（console dark 模式）、
`system + comfortable`（任何 app 跟随系统）。**不要**把它们合并成一个 `theme` 概念。

## 1. 颜色模式

### 1.1 状态机

```typescript
// shared/theme/src/ThemeMode.ts
export const THEME_MODES = ['light', 'dark', 'system'] as const
export type ThemeMode = (typeof THEME_MODES)[number]

export const THEME_CYCLE: readonly ThemeMode[] = THEME_MODES
//                                    ↑ cycle 顺序：light → dark → system → light → ...

export const THEME_STORAGE_KEY = 'ulticode-theme'   // localStorage key
```

**循环顺序**故意按 `light → dark → system` 而不是 `light → dark → light → dark`。
原因：用户首次落地看到 light 之后，**最常见的下一步**是"试试看 dark 是什么样"，然后
"觉得还是跟系统走方便"——所以 system 放在最容易到达的位置。

### 1.2 持久化

```typescript
// shared/theme/src/storage.ts
const storage = getThemeStorage()  // 探测环境，返回 localStorage 或内存 shim
storage.getItem(THEME_STORAGE_KEY)  // → 'light' | 'dark' | 'system' | null
storage.setItem(THEME_STORAGE_KEY, mode)
```

**`getThemeStorage()` 探测**：

1. 优先 `globalThis.localStorage`（浏览器）
2. 探测接口是否完整（`getItem` / `setItem` 都是 function）——vitest 4 的 `Object` stub 会失败
3. 失败则用 `MemoryStorageBackend`（Map 后端）
4. 测试可用 `setThemeStorage(customInstance)` 注入桩

**错误策略**：
- 读失败 → `console.warn` + 返回 `null`（解析为默认 `system`，不阻塞渲染）
- 写失败 → `console.error`（用户**明确**的选择没被持久化，运维要知道）

### 1.3 应用到 DOM

```typescript
// shared/theme/src/applyThemeToDOM.ts
export function isDarkMode(mode: ThemeMode): boolean {
  if (mode === 'dark') return true
  if (mode === 'light') return false
  // mode === 'system'
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function applyThemeToDOM(mode: ThemeMode): void {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  if (isDarkMode(mode)) {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
}
```

**关键设计**：
- **纯函数**（无 Vue / Pinia / localStorage 依赖）——同一份代码在三个地方跑（见 §1.5）
- **服务端安全**（`typeof document === 'undefined'` 直接 return）
- **matchMedia 缺失时降级为 light**（罕见，但 SSR / 旧浏览器不能崩）

### 1.4 system 模式 + 系统主题变化

```typescript
// shared/theme/src/useTheme.ts
let mediaQueryList: MediaQueryList | null = null

export function initTheme() {
  // …
  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    mediaQueryList = window.matchMedia('(prefers-color-scheme: dark)')
    mediaQueryList.addEventListener('change', handleSystemThemeChange)
  }
}

function handleSystemThemeChange() {
  if (theme.value === 'system') {
    applyThemeToDOM('system')  // 重新评估
  }
}
```

用户**只在 `system` 模式下**跟系统走。选了 `light` / `dark` 后即使系统切了，
应用不变——尊重用户**显式**选择。

### 1.5 FOUC 防止（无白屏闪烁）

```
时间线（毫秒）：

 0ms       HTML 解析到 <head>
 1ms       解析到 <script src="/theme-bootstrap.js">    ← 同步执行
            → 读 localStorage.ulticode-theme
            → applyThemeToDOM(mode) → <html class="dark"> 落地
 2ms       解析到 <script type="module" src="/src/main.ts">  ← 异步，启动模块解析
 5ms       main.ts 调 initTheme()
            → 同源 mode 重新校准（防止引导脚本和单例 mode 不一致）
            → 注册 matchMedia 监听
50ms       Vue mount('#app')
```

**引导脚本**（`console/public/theme-bootstrap.js` 与 `management/public/theme-bootstrap.js`）
必须是**纯 ES JS**——它在 Vite 静态资源服务上跑，没有 TypeScript 编译。
所以引导脚本是 `applyThemeToDOM` 逻辑的**手抄**（参见
[[theme/architecture#23 引导层consolepublicmanagementpublic|`architecture.md` §2.3]]）。
两份文件目前几乎一样，**任何逻辑修改必须两处同步**。

> **未来改进**：把引导脚本抽到 `shared/theme/src/bootstrap.js`，然后两个前端
> 通过 Vite 的 `?raw` import 内联到 `index.html` 的 `<script>` 里——但这会改变
> 部署形态，单独 PR。

### 1.6 Vue 组合式

```typescript
// shared/theme/src/useTheme.ts
export function useColorTheme(): {
  theme: Ref<ThemeMode>     // 注意：故意不返回 Readonly<Ref>，详见下方注释
  setTheme: (mode: ThemeMode) => void
  cycleTheme: () => ThemeMode
}
```

**故意不用 `Readonly<Ref<ThemeMode>>`**：vue-tsc 3.x 在 template 里只对
`Ref<T>` 自动解包，对 `Readonly<Ref<T>>` 的深只读代理识别不到——会导致
`v-if="theme === 'dark'"` 报类型错误。函数注释里写明了"读取就好，修改必须用 setTheme"。

**`useTheme` 是 deprecated 别名**——保留只是为了迁移期不破坏 import。
新代码必须用 `useColorTheme`。

**`cycleTheme` 用法**（典型 UI 组件 `<AuthThemeToggle>`）：

```vue
<script setup lang="ts">
import { useColorTheme } from '@ulticode/theme'
const { theme, cycleTheme } = useColorTheme()
</script>

<template>
  <button @click="cycleTheme">
    {{ theme === 'light' ? '☀' : theme === 'dark' ? '☾' : '◐' }}
  </button>
</template>
```

### 1.7 完整生命周期

```
用户首次访问
  ↓
FOUC 脚本 → applyThemeToDOM('system')  → <html class="">  （不设）
  ↓
main.ts → initTheme()
  ├─ 读 localStorage（可能拿到 'light'/'dark'/'system'/null）
  ├─ parseThemeMode(stored)  // null → 'system'
  ├─ theme.value = mode
  ├─ applyThemeToDOM(mode)  // 再次校准
  └─ 注册 matchMedia 监听
  ↓
用户点击 AuthThemeToggle → cycleTheme() → setTheme('dark')
  ├─ theme.value = 'dark'
  ├─ localStorage.ulticode-theme = 'dark'
  └─ applyThemeToDOM('dark')  // <html class="dark">
  ↓
（用户在 OS 设置里把系统切到 dark）
  ↓
matchMedia 触发 handleSystemThemeChange
  ├─ theme.value 是 'dark' → 不动
  （如果 theme.value 是 'system' → applyThemeToDOM('system') 重新评估）
```

## 2. 密度（Density）

### 2.1 两个 profile

```typescript
// shared/theme/src/typography.ts
export const TYPOGRAPHY_DENSITIES = ['comfortable', 'compact'] as const
export type TypographyDensity = (typeof TYPOGRAPHY_DENSITIES)[number]
```

| Profile      | 适用         | 特征                                |
| ------------ | ------------ | ----------------------------------- |
| `comfortable` | console      | 长阅读、markdown、问题描述、题解    |
| `compact`     | management   | 表格、审核队列、仪表盘、密集列表   |

**密度 ≠ 颜色模式**。密度影响的是**字号 / 控件高度 / 表格 cell 字号**，
不切换任何颜色。

### 2.2 怎么生效

```typescript
// shared/theme/src/typography.ts
export function applyTypographyDensity(density: TypographyDensity): void {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.ucDensity = density
}
```

`shared/theme/src/typography.css` 用 `[data-uc-density="..."]` 选择器覆盖
`--uc-type-*-size`：

```css
:root,
[data-uc-density="comfortable"] {
  --uc-type-body-size:        var(--uc-text-sm);
  --uc-type-control-size:     var(--uc-text-sm);
  --uc-type-table-cell-size:  var(--uc-text-sm);
  --uc-type-page-title-size:  var(--uc-text-2xl);
  --uc-type-markdown-size:    var(--uc-text-md);
}

[data-uc-density="compact"] {
  --uc-type-body-size:        var(--uc-text-sm);
  --uc-type-control-size:     var(--uc-text-xs);     /* ← 关键差异 */
  --uc-type-table-cell-size:  var(--uc-text-xs);     /* ← 关键差异 */
  --uc-type-page-title-size:  var(--uc-text-xl);
  --uc-type-markdown-size:    var(--uc-text-sm);
}
```

> body 字号在两种密度下都是 `--uc-text-sm`——所以**用户视觉上密度感来自控件/表格/标题**，
> 而不是正文。这是有意的：避免 management 的"小正文"让用户读得累。

### 2.3 谁负责写

**只有 `applyTypographyDensity()` 能写 `data-uc-density`**。
组件中**禁止** `document.documentElement.dataset.ucDensity = 'compact'`。

**每个应用在 `main.ts` 写一次**：

```typescript
// console/src/main.ts
import { applyTypographyDensity } from '@ulticode/theme'
applyTypographyDensity('comfortable')

// management/src/main.ts
import { applyTypographyDensity } from '@ulticode/theme'
applyTypographyDensity('compact')
```

### 2.4 读取当前密度（rare）

```typescript
import { getTypographyDensity } from '@ulticode/theme'
const density = getTypographyDensity()  // 'comfortable' | 'compact' | null
```

目前**没有组件读它**——它是给将来"用户可在 settings 里覆盖"预留的接口。
如果发现某组件需要根据密度调整逻辑，先停下来想：是不是该加个新的
`--uc-type-foo-size` token 而不是 JS 条件分支？

## 3. 总结：两份配置的最终落地

| 应用        | 颜色模式（运行时）       | 密度（构建时）     | 在哪行代码        |
| ----------- | ------------------------ | ------------------ | ----------------- |
| console     | 用户选择（localStorage） | `comfortable`      | `main.ts:42`      |
| management  | 用户选择（localStorage） | `compact`          | `main.ts:30`      |

`<html>` 元素最终长这样（假设 console 用户选了 dark）：

```html
<html class="dark" data-uc-density="comfortable">
```

## 4. 扩展指南

### 4.1 加新颜色模式（如高对比度 `high-contrast`）

**需求**：为视障用户加一个 high-contrast 模式，比 dark 模式对比度更高。

**步骤**：

1. **扩展联合类型**（`shared/theme/src/ThemeMode.ts`）：
   ```typescript
   export const THEME_MODES = ['light', 'dark', 'high-contrast', 'system'] as const
   export type ThemeMode = (typeof THEME_MODES)[number]
   export const THEME_CYCLE: readonly ThemeMode[] = THEME_MODES
   // → 注意：现在 cycle 顺序变成 light → dark → high-contrast → system
   ```

2. **更新 `isThemeMode` type guard**（同文件）：
   ```typescript
   export function isThemeMode(value: unknown): value is ThemeMode {
     return ['light', 'dark', 'high-contrast', 'system'].includes(value as string)
   }
   ```

3. **改 `applyThemeToDOM`**（`shared/theme/src/applyThemeToDOM.ts`）：
   ```typescript
   export function isDarkMode(mode: ThemeMode): boolean {
     if (mode === 'dark' || mode === 'high-contrast') return true
     if (mode === 'light') return false
     // system
     return matchMedia('(prefers-color-scheme: dark)').matches
   }
   ```

4. **改 `<html>` 类策略**：现在 high-contrast 模式也是 `.dark` 类——但视觉不同。
   改为用**两个独立的类**：
   ```typescript
   export function applyThemeToDOM(mode: ThemeMode): void {
     const root = document.documentElement
     root.classList.toggle('dark', isDarkMode(mode))
     root.classList.toggle('high-contrast', mode === 'high-contrast')
   }
   ```

5. **加 CSS 变量**（`shared/design-system/style.css`）：
   ```css
   .high-contrast {
     --background: oklch(0 0 0);
     --foreground: oklch(1 0 0);
     --border: oklch(1 0 0);
     /* … 全部用纯黑白 + 提高对比 */
   }
   ```

6. **同步 FOUC 引导脚本**（`console/public/theme-bootstrap.js` +
   `management/public/theme-bootstrap.js`）——手抄 `isDarkMode` 和 `applyThemeToDOM`。

7. **更新 `<AuthThemeToggle>` UI**——加个图标。

8. **加测试**（`shared/theme/__tests__/useTheme.spec.ts`）——覆盖新 mode 的 cycle / persistence。

9. **更新本文档** + [[theme/README]] 的"改动如何被收录"表。

### 4.2 加新密度档（如 `extra-compact`，适合 13 寸屏管理端）

**步骤**：

1. **扩展联合类型**（`shared/theme/src/typography.ts`）：
   ```typescript
   export const TYPOGRAPHY_DENSITIES = ['comfortable', 'compact', 'extra-compact'] as const
   export type TypographyDensity = (typeof TYPOGRAPHY_DENSITIES)[number]
   ```

2. **改 `applyTypographyDensity` / `getTypographyDensity`**——已经接受 `TypographyDensity` 类型，
   不用改逻辑。

3. **加 CSS profile**（`shared/theme/src/typography.css`）：
   ```css
   [data-uc-density="extra-compact"] {
     --uc-type-body-size:        var(--uc-text-xs);   /* 比 compact 还小 */
     --uc-type-control-size:     var(--uc-text-2xs);
     --uc-type-table-cell-size:  var(--uc-text-2xs);
     --uc-type-page-title-size:  var(--uc-text-lg);
     --uc-type-markdown-size:    var(--uc-text-xs);
   }
   ```

4. **更新 `main.ts`**——`applyTypographyDensity('extra-compact')`。

5. **加测试**（`shared/theme/__tests__/typography.spec.ts`）——断言新 profile 存在。

6. **更新本文档** §2.1 + [[theme/README]] 的"改动如何被收录"表。

## 5. 参见

- [[theme/architecture]] — 模式状态机怎么和 Token 层联动
- [[design-tokens]] — 颜色 token + light/dark 切换表
- [[components]] — 组件怎么读 token（不写 `style="color: #fff"`）
- [[extension]] — 加新 token 的步骤
- `shared/theme/src/useTheme.ts` — 状态机主文件
- `shared/theme/src/applyThemeToDOM.ts` — DOM 应用
- `shared/theme/src/storage.ts` — localStorage 抽象
- `console/public/theme-bootstrap.js` / `management/public/theme-bootstrap.js` — FOUC 引导
