---
title: 扩展指南
tags: [reference, frontend, living]
status: living
updated: 2026-06-19
owner: design-system
---

# 扩展指南

> **范围**：在 UltiCode 主题系统里**加新东西**的步骤。覆盖：新 token、新工具类、新组件原语、
> 新颜色模式、新密度档。
>
> 配套阅读：[[theme/README]] · [[theme/architecture]] ·
> [[design-tokens]] · [[theme-modes]] ·
> [[components]]

## 0. 通用清单（任何扩展都走一遍）

- [ ] **先查是否已有**：90% 的"加新东西"其实是复用。`grep -rn "var(--foo" shared/` 看看。
- [ ] **三处同步**：所有改动同时落 Token 层（CSS） + 状态层（TS） + 文档。
- [ ] **测试覆盖**：`shared/theme/__tests__/` 加一个 spec。
- [ ] **双端验证**：`pnpm test` + `pnpm type-check` 在 `shared/theme/` 跑，
      然后 console + management 都跑 `pnpm build`。
- [ ] **更新文档**：本目录对应的 .md + 父 README 的"改动如何被收录"表。

## 1. 加新 token

### 1.1 加新颜色 token（如 `--status-info`）

**情景**：需要"信息性提示"的颜色，目前只有 success / warning / error。

1. **加 token 定义**（`shared/design-system/style.css`）：
   ```css
   :root {
     /* 已有的 success / warning / error 旁边 */
     --status-info: var(--solarized-cyan);
   }
   /* 如果 dark 下需要不同值： */
   .dark {
     --status-info: oklch(0.7 0.12 187.4);  /* 更亮一点 */
   }
   ```
   > 用 oklch 不用 hex / rgb。`color-mix()` 透明变体不需要单独定义——用 `color-mix(in oklch, var(--status-info) 15%, transparent)`。

2. **加 utility（如果要用 Tailwind）**（同文件）：
   ```css
   @theme inline {
     --color-status-info: var(--status-info);
   }
   ```
   然后 `class="text-status-info"` 就能用。

3. **加测试**（`shared/theme/__tests__/typography.spec.ts` 之外，新文件）：
   ```typescript
   // shared/theme/__tests__/designTokens.spec.ts
   import { describe, expect, it } from 'vitest'

   describe('color tokens', () => {
     it('--status-info is defined and is an oklch value', () => {
       // 解析 stylesheet（开发期）—— 也可以用 happy-dom
       const styles = Array.from(document.styleSheets)
         .flatMap((s) => Array.from(s.cssRules))
       const rule = styles.find(
         (r) => r.cssText.includes('--status-info')
       ) as CSSStyleRule | undefined
       expect(rule).toBeDefined()
     })
   })
   ```
   > 当前仓库没这套自动测试——下面 §1.2 给出更轻量的"白名单"方式。

4. **更新文档**：在 [[design-tokens#14 状态 强调 表面业务语义|`design-tokens.md` §1.4]] 加一行表格。

### 1.2 用"白名单 + 搜索"代替运行时测试

替代上面的运行时测试：写一个 guardrail 脚本（`.scripts/check-design-tokens.ts`），
启动时跑 `grep -E "var\(--xxx"`，确认所有 `--xxx` 都在白名单里。

```typescript
// shared/theme/scripts/check-design-tokens.ts
const ALLOWED_PREFIXES = [
  '--uc-font-', '--uc-text-', '--uc-leading-', '--uc-font-weight-',
  '--uc-tracking-', '--uc-type-',
  '--solarized-', '--silver-', '--terminal-', '--status-',
  '--accent-', '--surface-',
  '--background', '--foreground', '--card', '--primary',
  '--secondary', '--muted', '--border', '--input', '--ring',
  '--destructive', '--popover', '--chart-', '--sidebar',
  '--radius', '--shadow-', '--transition-', '--ease-',
  '--duration-', '--scrollbar-',
  '--header-padding-y', '--stats-padding-y',  // management/style.css 局部
]
// …
```

这个脚本留给将来补——目前**靠 Code Review 拦截**。

### 1.3 加新字号（如 `--uc-text-4xl`）

```css
/* shared/theme/src/typography.css */
--uc-text-4xl: 2.25rem;  /* 36px */
```

同步：

1. **`typography.ts`** 的 `typographySizes` 加 `text4xl: '2.25rem'`
2. **`Tailwind @theme inline`** 加 `--text-4xl: var(--uc-text-4xl)`（让 `text-4xl` 工具类可用）
3. **文档**：[[design-tokens#字体家族|`design-tokens.md` §2.1 字号表]] 加一行
4. **测试**：`typography.spec.ts` 加 `expect(typographySizes).toHaveProperty('text4xl')`

### 1.4 加新间距（一般不需要）

**项目没有自定义 spacing**——直接用 Tailwind 默认（`p-4` / `gap-2` 等）。
**如果**真的需要，Tailwind v4 支持：

```css
@theme inline {
  --spacing-18: 4.5rem;
}
```

然后 `class="p-18"` 就能用。同步文档即可。

## 2. 加新工具类（utility class）

### 2.1 加简单 utility（如 `.text-link`）

```css
/* shared/design-system/style.css */
.text-link {
  color: var(--accent);
  text-underline-offset: 2px;
  transition: color var(--transition-fast);
}
.text-link:hover {
  color: color-mix(in oklch, var(--accent) 75%, var(--foreground));
  text-decoration: underline;
}
```

**不要**同时加到 typography.ts 的 `typographyUtilityClasses` 数组里——
那个数组是**字体/排版相关**的 utility，不是所有 utility。

### 2.2 加字体/排版 utility（要在 `typographyUtilityClasses` 注册）

```css
/* shared/theme/src/typography.css */
.uc-type-hero {
  font-family: var(--uc-font-ui);
  font-size: var(--uc-text-3xl);
  line-height: var(--uc-leading-tight);
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: var(--uc-tracking-normal);
}
```

```typescript
// shared/theme/src/typography.ts
export const typographyUtilityClasses = [
  // ...已有
  'uc-type-hero',  // ← 新增
] as const
```

不注册不会报错，但 guardrail 脚本未来会用这个数组做白名单检查。

## 3. 加新组件原语

**例**：加一个"高亮成功提交"的 `.terminal-callout-success` 组件原语。

1. **写在 `shared/design-system/style.css`**：
   ```css
   .terminal-callout {
     border-left: 3px solid var(--accent);
     padding: 12px 16px;
     background: var(--card);
   }
   .terminal-callout-success {
     border-left-color: var(--status-success);
     background: color-mix(in oklch, var(--status-success) 8%, var(--card));
   }
   ```

2. **在 [[components#32 终端风terminal|`components.md` §3.2]]** 加一行表格。

3. **写一个示例组件**（`console/src/components/_examples/TerminalCalloutExample.vue`），
   跑 `pnpm dev` 目视确认 dark 模式下颜色对。

4. **Code Review 时 reviewer 检查**：
   - [ ] 用 token 而不是 hex
   - [ ] 在 light 和 dark 下都试过
   - [ ] 加进 docs

## 4. 加新颜色模式 / 密度档

详见 [[theme-modes#4 扩展指南|`theme-modes.md` §4]]。简版：

| 加什么          | 改哪些文件                                                                 |
| --------------- | -------------------------------------------------------------------------- |
| **新颜色模式**  | `ThemeMode.ts` + `applyThemeToDOM.ts` + `shared/design-system/style.css` + 两个 `theme-bootstrap.js` + 测试 + 文档 |
| **新密度档**    | `typography.ts` + `typography.css`（`[data-uc-density="..."]` 段） + 应用 `main.ts` + 测试 + 文档 |

## 5. 改公共 API

`shared/theme/src/index.ts` 改 export 时：

1. **先看谁在用**：`grep -rn "from '@ulticode/theme'" console/src management/src`
2. **如果新加 export**：直接加
3. **如果删 / 改名**：先看是不是迁移期内——可能要走 `deprecated` 别名

```typescript
// 改名迁移示例
/** @deprecated use {@link useColorTheme} instead. */
export const useTheme = useColorTheme
export const useColorTheme = () => { /* 新实现 */ }
```

## 6. 改 `shared/design-system/style.css`（破坏性改动）

**任何**改 `shared/design-system/style.css` 的 PR 都需要：

- [ ] 在 console + management 跑 `pnpm build`
- [ ] 用浏览器的 dev tools 切换 dark 模式目视确认
- [ ] 至少看 3 个页面：登录、列表、详情
- [ ] 跑 `shared/theme pnpm test`
- [ ] 如果新增/删除 token，更新 [[design-tokens]]

**特别注意**：

- 改 shadcn-vue 桥接段（`--background` / `--foreground` / `--card` / `--primary` 等）→
  shadcn-vue 组件会全盘变。
- 改 `tailwindcss` / `tw-animate-css` 的 import 顺序 → 工具类会失效。
- 改 `:root` 段的颜色顺序 → 某些 `color-mix` 透明变体的视觉等效会变（不推荐）。

## 7. 迁移检查清单（把已有代码迁到 token 体系）

**情景**：发现老代码里有 `style="color: #fff"` 或 `class="bg-slate-900"`，要怎么办。

1. **搜索定位**：
   ```bash
   rg "style=.*color:" console/src management/src --type vue --type ts
   rg "bg-(slate|gray|zinc|red|blue|green)-" console/src management/src --type vue
   ```

2. **逐个替换**：
   | 旧                                    | 新                                                |
   | ------------------------------------- | ------------------------------------------------- |
   | `style="color: #fff"`                | `class="text-foreground"`                         |
   | `class="bg-slate-900"`                | `class="bg-background"`                           |
   | `class="text-slate-500"`              | `class="text-muted-foreground"`                   |
   | `class="border-slate-200"`            | `class="border border-border"`                    |
   | `class="text-red-500"`                | `class="text-destructive"`                        |
   | `class="bg-blue-500"`                 | `class="bg-accent text-accent-foreground"`        |
   | `class="text-green-500"`              | `class="text-status-success"`                     |
   | 任意 `style="..."` 颜色               | 对应 token                                        |

3. **提交时**：
   - 主题相关 commit 单独发（`refactor(theme): migrate slate-* to design tokens`）
   - 不要混在功能 commit 里——回滚困难

## 8. 故障排查

### 8.1 改了 token，UI 没变

- 是不是 `.dark` 没切换？DevTools 看 `<html>` 的 class
- 是不是 scoped 样式覆盖了？`getComputedStyle` 看 `font-family` / `color` 解析
- 是不是 Tailwind 没重新生成？`rm -rf node_modules/.vite && pnpm dev`

### 8.2 dark 模式不生效

- 是不是 `applyThemeToDOM` 没跑？看 console 报错
- 是不是 `localStorage` 被禁用了？DevTools → Application → Local Storage
- 是不是 FOUC 引导脚本丢了？看 console / `<head>` 源码

### 8.3 密度切换不生效

- 是不是 `applyTypographyDensity('comfortable')` 在 `main.ts` 没调？
- 是不是组件用了 `class="text-sm"` 而不是 `class="uc-type-body"`？前者在 compact 下不变

### 8.4 字体没加载

- 看 Network 标签：jsDelivr / Google Fonts 请求是否 200
- DevTools → Rendering → "Disable web fonts" 关掉
- 看 `typography.css` 顶部 `@import` 顺序

## 9. 参见

- [[theme/README]] — 索引
- [[theme/architecture]] — 怎么分层
- [[design-tokens]] — 全部 token 速查
- [[theme-modes]] — 颜色模式 / 密度 / 切换 / FOUC
- [[components]] — 组件原语清单
