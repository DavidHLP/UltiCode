---
title: Design Tokens 速查
tags: [reference, frontend, living]
status: living
updated: 2026-06-19
owner: design-system
---

# Design Tokens 速查

> **范围**：UltiCode 前端所有 Design Token 的**清单、含义、取值范围**。每个 token 都能在
> `shared/design-system/style.css` 或 `shared/theme/src/typography.css` 里直接搜到定义。
>
> 配套阅读：[`architecture.md`](./architecture.md) · [`theme-modes.md`](./theme-modes.md) ·
> [`components.md`](./components.md)

## 0. 命名规范

| 前缀              | 含义                         | 由谁定义                                 | 例子                       |
| ----------------- | ---------------------------- | ---------------------------------------- | -------------------------- |
| `--uc-font-*`     | 字体家族                     | `shared/theme/src/typography.css`        | `--uc-font-ui`             |
| `--uc-text-*`     | 字号                         | `shared/theme/src/typography.css`        | `--uc-text-sm` (14px)      |
| `--uc-leading-*`  | 行高                         | `shared/theme/src/typography.css`        | `--uc-leading-normal`      |
| `--uc-font-weight-*` | 字重                      | `shared/theme/src/typography.css`        | `--uc-font-weight-bold`    |
| `--uc-tracking-*` | 字距（letter-spacing）       | `shared/theme/src/typography.css`        | `--uc-tracking-label`      |
| `--uc-type-*`     | 语义角色（role 组合）        | `shared/theme/src/typography.css`        | `--uc-type-body-size`      |
| `--solarized-*`   | Solarized 调色板原始 oklch 值 | `shared/design-system/style.css`        | `--solarized-base03`       |
| `--silver-*`      | 灰阶（中性色阶）             | `shared/design-system/style.css`        | `--silver-200`             |
| `--terminal-*`    | 终端调色（绿/琥珀/红/青/紫） | `shared/design-system/style.css`        | `--terminal-green`         |
| `--status-*`      | 业务状态色                   | `shared/design-system/style.css`         | `--status-success`         |
| `--accent-*`      | 强调色（蓝色）               | `shared/design-system/style.css`         | `--accent-primary`         |
| `--surface-*`     | 表面层级                     | `shared/design-system/style.css`         | `--surface-elevated`       |
| `--background` / `--foreground` / `--card` / `--primary` / `--muted` / `--border` / `--ring` / `--destructive` | shadcn-vue 桥接 | `shared/design-system/style.css` | `--background`             |
| `--radius`        | 圆角基数                     | `shared/design-system/style.css`         | `--radius: 0`              |
| `--shadow-*`      | 阴影                         | `shared/design-system/style.css`         | `--shadow-float-hover`     |
| `--transition-*`  | 过渡时长                     | `shared/design-system/style.css`         | `--transition-fast`        |
| `--ease-*`        | 缓动函数                     | `shared/design-system/style.css`         | `--ease-out-expo`          |
| `--duration-*`    | 动效时长                     | `shared/design-system/style.css`         | `--duration-normal`        |
| `--scrollbar-*`   | 滚动条                       | `shared/design-system/style.css`         | `--scrollbar-thumb`        |

**硬约束**：

- 任何 `var(--uc-*)` 或 `var(--silver-*)` 引用**必须**最终解析到一个 `oklch()` 值。
  hex / rgb / hsl 颜色**禁止**出现在 token 定义里。
- `--uc-*` 不在 light/dark 间切换；切换是颜色 token（`--background` / `--silver-*` / `--terminal-*`）
  的事，字体 token 是**全局统一**的（楷体项目）。
- 间距 / 圆角 / z-index **没有自定义 token**——直接用 Tailwind v4 默认（`p-4` / `rounded-md` / `z-10` 等）。
  圆角基数是 `--radius: 0`，意味着 `rounded-md` / `rounded-lg` 全部解析到 `0px`
  （"Precision Dashboard" 风格——故意硬朗）。

## 1. 颜色（Color）

### 1.1 Solarized 调色板（不可变原料）

定义在 `shared/design-system/style.css` 的 `:root`。**所有颜色 token 派生自此**。
原版 Solarized 由 Ethan Schoonover 设计；本仓库用 oklch 重写以获得更好的色相一致性。

| Token                       | oklch                          | 角色                       |
| --------------------------- | ------------------------------ | -------------------------- |
| `--solarized-base03`        | `oklch(0.2673 0.0486 219.8)`   | 极深蓝灰（dark 背景）      |
| `--solarized-base02`        | `oklch(0.3092 0.0518 219.7)`   | 深蓝灰（dark elevated 表面）|
| `--solarized-base01`        | `oklch(0.523  0.0283 219.1)`  | 中深蓝灰（dark 二级文字）  |
| `--solarized-base00`        | `oklch(0.5682 0.0285 221.9)`  | 中性灰（dark / light 文字）|
| `--solarized-base0`         | `oklch(0.6537 0.0197 205.3)`  | 浅灰（light 文字）         |
| `--solarized-base1`         | `oklch(0.6979 0.0159 196.8)`  | 浅暖灰                     |
| `--solarized-base2`         | `oklch(0.9306 0.026  92.4)`   | 暖白（light 背景）         |
| `--solarized-base3`         | `oklch(0.9735 0.0261 90.1)`   | 极暖白（light elevated）   |
| `--solarized-yellow`        | `oklch(0.6545 0.134  85.7)`   | 警告                       |
| `--solarized-orange`        | `oklch(0.5808 0.1732 39.1)`   | 强警告                     |
| `--solarized-red`           | `oklch(0.5863 0.2064 27.1)`   | 错误                       |
| `--solarized-magenta`       | `oklch(0.5924 0.2025 355.9)`  | 装饰                       |
| `--solarized-violet`        | `oklch(0.524  0.144  286.0)`  | 装饰                       |
| `--solarized-blue`          | `oklch(0.6149 0.1394 244.9)`  | **强调色 / 链接 / 选中**   |
| `--solarized-cyan`          | `oklch(0.6437 0.1019 187.4)`  | 信息                       |
| `--solarized-green`         | `oklch(0.6444 0.1508 118.6)`  | 成功                       |

### 1.2 灰阶（silver scale）

Solarized 调色板中性色有限，无法覆盖所有中性灰场景。`--silver-*` 单独定一档灰阶，
**深色 / 浅色模式下值会互换**（dark 时 `--silver-50` 反而最深）。

| Token          | Light             | Dark              | 典型用途                |
| -------------- | ----------------- | ----------------- | ----------------------- |
| `--silver-50`  | `oklch(0.9735 0.0261 90.1)`（最浅） | `oklch(0.2673 0.0486 219.8)`（最深） | 卡片底层                |
| `--silver-100` | `oklch(0.9306 0.026  92.4)`  | `oklch(0.3092 0.0518 219.7)`  | 凹陷面、输入框背景      |
| `--silver-200` | `oklch(0.6979 0.0159 196.8)`  | `oklch(0.523  0.0283 219.1)`  | 边框、分割线            |
| `--silver-300` | `oklch(0.6537 0.0197 205.3)`  | `oklch(0.523  0.0283 219.1)`  | 强边框                  |
| `--silver-400` | `oklch(0.6537 0.0197 205.3)`  | `oklch(0.6537 0.0197 205.3)`  | 静默文字、行号          |
| `--silver-500` | `oklch(0.5682 0.0285 221.9)`  | `oklch(0.6537 0.0197 205.3)`  | 次级文字、icon          |
| `--silver-600` | `oklch(0.523  0.0283 219.1)`  | `oklch(0.6979 0.0159 196.8)`  | 标题文字                |
| `--silver-700` | `oklch(0.523  0.0283 219.1)`  | `oklch(0.9306 0.026  92.4)`   | 主要文字                |
| `--silver-800` | `oklch(0.3092 0.0518 219.7)`  | `oklch(0.9735 0.0261 90.1)`   | 反色文字                |
| `--silver-900` | `oklch(0.2673 0.0486 219.8)`  | `oklch(0.9735 0.0261 90.1)`   | 反色文字（深背景）      |

> **为什么 silver scale 是"双向"的？** 这是设计系统的取舍：让同一组 class
> （`text-silver-500` / `bg-silver-100`）在 light/dark 下都有合适的对比度，
> 不用每个组件都写 `.dark .text-something`。
> 但代价是 token 名不再自解释——所以 `silver-50` 在 light 下是浅色、在 dark 下反而是深色。

### 1.3 终端调色（terminal accents）

Solarized 调色板里和"信号灯"语义对齐的 5 个色：

```css
--terminal-green:  oklch(0.6444 0.1508 118.6);  /* success, 成功提交 */
--terminal-amber:  oklch(0.6545 0.134  85.7);   /* warning, 部分通过 */
--terminal-red:    oklch(0.5863 0.2064 27.1);   /* error, 编译失败 */
--terminal-cyan:    oklch(0.6437 0.1019 187.4);  /* info, 提示 */
--terminal-purple:  oklch(0.5924 0.2025 355.9); /* 装饰, OJ 排行榜 */
```

Light / dark 同值（Solarized 设计的"跨模式恒定"特性）。

### 1.4 状态 / 强调 / 表面（业务语义）

```css
/* 业务状态 */
--status-success: var(--solarized-green);
--status-warning: var(--solarized-yellow);
--status-error:   var(--solarized-red);

/* 强调（蓝色，跨模式）*/
--accent-primary: var(--solarized-blue);
--accent-glow:    color-mix(in oklch, var(--solarized-blue) 15%, transparent);
--accent-electric:        var(--solarized-blue);
--accent-electric-glow:   var(--solarized-blue / 0.15);

/* 表面层级（light / dark 不同）*/
--surface-elevated: oklch(0.9735 0.0261 90.1);  /* light: 最亮 */
--surface-sunken:   oklch(0.9306 0.026  92.4);  /* light: 稍暗 */
--surface-elevated: oklch(0.3092 0.0518 219.7); /* dark:  比背景亮 */
--surface-sunken:   oklch(0.2673 0.0486 219.8); /* dark:  比背景暗 */
```

### 1.5 shadcn-vue 桥接（语义层）

`shared/design-system/style.css` 里的 `:root { --background: …; }` 段把 Solarized
色映射到 shadcn-vue / Radix 期望的命名。这样 `<Card>`、`<Dialog>` 等开箱组件能直接
用 Tailwind 的 `bg-background` / `text-foreground`：

| shadcn-vue token     | Light 源               | Dark 源                | 用途                  |
| -------------------- | ---------------------- | ---------------------- | --------------------- |
| `--background`       | `--solarized-base2`    | `--solarized-base03`   | 全局背景              |
| `--foreground`       | `--solarized-base00`   | `--solarized-base0`    | 全局文字              |
| `--card`             | `--solarized-base3`    | `--solarized-base02`   | 卡片背景              |
| `--card-foreground`  | `--solarized-base00`   | `--solarized-base0`    | 卡片文字              |
| `--popover`          | `--solarized-base3`    | `--solarized-base02`   | 弹层背景              |
| `--primary`          | `--solarized-base03`   | `--solarized-base1`    | 主按钮（深底白字）    |
| `--primary-foreground` | `--solarized-base3`  | `--solarized-base03`   | 主按钮文字            |
| `--secondary`        | `--solarized-base2`    | `--solarized-base02`   | 次按钮背景            |
| `--muted`            | `--solarized-base2`    | `--solarized-base02`   | 静默区域背景          |
| `--muted-foreground` | `--solarized-base01`   | `--solarized-base01`   | 静默区域文字          |
| `--accent`           | `--solarized-blue`     | `--solarized-blue`     | 链接、强调            |
| `--destructive`      | `--solarized-red`      | `--solarized-red`      | 删除按钮              |
| `--border`           | silver-200 半透明      | silver-300 半透明      | 边框                  |
| `--ring`             | `--solarized-blue`     | `--solarized-blue`     | 焦点环                |
| `--chart-1`..`--chart-5` | blue / cyan / green / yellow / orange | 同 | 图表色阶          |
| `--sidebar*`         | 衍生                  | 衍生                  | 侧边栏专用            |

> **不要新加 shadcn-vue token**。如果需要某种颜色但 shadcn-vue 没暴露，写到 `--silver-*` /
> `--terminal-*` / 自定义 `--something-*` 段。

### 1.6 light / dark 切换实现

```css
/* shared/design-system/style.css */

:root {
  --background: var(--solarized-base2);
  --foreground: var(--solarized-base00);
  /* … */
}

.dark {
  --background: var(--solarized-base03);
  --foreground: var(--solarized-base0);
  /* … */
}
```

切换机制：**`<html>` 上的 `.dark` 类**（`@custom-variant dark (&:is(.dark *))`
让 Tailwind `dark:bg-foo` 变体走这个类）。由 `applyThemeToDOM()` 唯一控制写入。

`@custom-variant` 这一行**关键**——它让 Tailwind 的 `dark:bg-card` 在编译时
变成 `.dark .bg-card`，而不是默认的 `@media (prefers-color-scheme: dark) .bg-card`。
如果改回媒体查询，system 模式就崩了。

## 2. 字体（Typography）

完整定义见 `shared/theme/src/typography.css`，元数据见 `shared/theme/src/typography.ts`。

### 2.1 基础 Token

#### 字体家族

```css
--uc-font-ui:    "LXGW WenKai", "Noto Sans SC", ui-sans-serif, system-ui, ...;
--uc-font-code:  "LXGW WenKai", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace;
--uc-font-data:  "LXGW WenKai", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace;
--uc-font-prose: var(--uc-font-ui);
```

> **项目范围统一使用楷体**（2026-06-19 决议）。`--uc-font-code` / `--uc-font-data` 也指向
> 楷体以确保代码/数据区与 UI 一致。Monaco 编辑器和 ECharts 字体由应用层显式传入。

#### 字号

| Token           | rem    | px   | 用途                                       |
| --------------- | ------ | ---- | ------------------------------------------ |
| `--uc-text-2xs` | 0.625  | 10   | 极小号：表头、终端 label、行号             |
| `--uc-text-xxs` | 0.6875 | 11   | 小号：徽章、header 按钮                   |
| `--uc-text-xs`  | 0.75   | 12   | 紧凑：caption、表格 cell                  |
| `--uc-text-sm`  | 0.875  | 14   | **默认 app body + 控件**                  |
| `--uc-text-md`  | 1      | 16   | 阅读：移动端输入、markdown 正文           |
| `--uc-text-lg`  | 1.125  | 18   | 抽屉标题、强调卡片                        |
| `--uc-text-xl`  | 1.25   | 20   | 页面标题                                  |
| `--uc-text-2xl` | 1.5    | 24   | 营销 / auth / 高强调                      |
| `--uc-text-3xl` | 1.875  | 30   | 落地页 hero（极少用）                     |

#### 行高

| Token                    | 值    | 用途                |
| ------------------------ | ----- | ------------------- |
| `--uc-leading-none`      | 1     | 标题、icon 文字     |
| `--uc-leading-tight`     | 1.25  | 页面标题            |
| `--uc-leading-snug`      | 1.35  | 段落标题            |
| `--uc-leading-code`      | 1.4   | 代码块、表内文字    |
| `--uc-leading-normal`    | 1.6   | **默认正文**        |
| `--uc-leading-relaxed`   | 1.75  | markdown 长文       |

#### 字重

| Token                       | 值 |
| --------------------------- | -- |
| `--uc-font-weight-regular`  | 400 |
| `--uc-font-weight-medium`   | 500 |
| `--uc-font-weight-semibold` | 600 |
| `--uc-font-weight-bold`     | 700 |

#### 字距（letter-spacing）

| Token                          | 值     | 用途                  |
| ------------------------------ | ------ | --------------------- |
| `--uc-tracking-normal`         | 0      | 默认                  |
| `--uc-tracking-label`          | 0.05em | label / 组件内文      |
| `--uc-tracking-terminal`       | 0.1em  | 终端风大写 label      |
| `--uc-tracking-terminal-wide` | 0.15em | 极端（极少见）        |

#### 数字 features

```css
--uc-font-feature-data: "tnum" on, "lnum" on;
```

启用**表格数字**（等宽数字）和**衬线数字**——仪表盘数字对齐必备。

### 2.2 语义角色（Semantic Roles）

基础 Token 组合出"角色"——告诉组件**用哪个**而不是**多大**：

```css
/* 基础 */
--uc-type-body-family: var(--uc-font-ui);
--uc-type-body-size:   var(--uc-text-sm);   /* comfortable 下; compact 下 = --uc-text-sm 也 */
--uc-type-body-line-height: var(--uc-leading-normal);
--uc-type-body-weight: var(--uc-font-weight-regular);

/* 标题 */
--uc-type-page-title-size:     var(--uc-text-xl);    /* comfortable: 2xl */
--uc-type-section-title-size:  var(--uc-text-lg);
--uc-type-card-title-size:     var(--uc-text-sm);
--uc-type-card-title-weight:   var(--uc-font-weight-semibold);

/* 控件 */
--uc-type-control-size:        var(--uc-text-sm);    /* compact: xs */
--uc-type-control-line-height: var(--uc-leading-tight);
--uc-type-control-weight:      var(--uc-font-weight-medium);

/* 标签 */
--uc-type-label-size:          var(--uc-text-xs);
--uc-type-label-line-height:   var(--uc-leading-tight);
--uc-type-label-weight:        var(--uc-font-weight-medium);

/* 表格 */
--uc-type-table-header-size:   var(--uc-text-2xs);
--uc-type-table-cell-size:     var(--uc-text-xs);    /* compact: xs */

/* 数据 / 代码 */
--uc-type-data-size:           var(--uc-text-xs);
--uc-type-code-size:           0.85em;
--uc-type-markdown-size:       var(--uc-text-md);    /* compact: sm */
```

> 完整映射表见 [`components.md` §2](./components.md#2-语义角色-semantic-roles)。

### 2.3 工具类（utility classes）

| Class                | font-family       | font-size            | line-height         | weight      | 用途                  |
| -------------------- | ----------------- | -------------------- | ------------------- | ----------- | --------------------- |
| `.uc-type-body`      | `--uc-font-ui`    | `--uc-type-body-size`| `--uc-leading-normal` | regular  | app body              |
| `.uc-type-page-title`| `--uc-font-ui`    | `--uc-type-page-title-size` | tight | semibold  | 页面标题              |
| `.uc-type-section-title` | `--uc-font-ui` | `--uc-type-section-title-size` | snug | semibold  | 段落标题              |
| `.uc-type-card-title`| `--uc-font-ui`    | `--uc-type-card-title-size` | snug | semibold | 卡片标题              |
| `.uc-type-control`   | `--uc-font-ui`    | `--uc-type-control-size` | tight | medium | 按钮 / 输入框         |
| `.uc-type-label`     | `--uc-font-data`  | `--uc-type-label-size` | tight | medium | 终端 label            |
| `.uc-type-data`      | `--uc-font-data`  | `--uc-type-data-size` | code | regular | 数字 / 表格 cell      |
| `.uc-type-code`      | `--uc-font-code`  | `--uc-type-code-size` | code | regular | 行内 code             |

**组件应该用 utility class 而不是任意 Tailwind 值**。例如，按钮文字写
`class="uc-type-control"` 而不是 `class="text-sm font-medium leading-tight"`。

## 3. 圆角（Radius）

```css
--radius: 0;   /* 故意为 0，"Precision Dashboard" 硬朗风格 */
```

> **不要**改这个值。所有 `rounded-md` / `rounded-lg` / `rounded-xl` 全部解析到 `0px`。
> 圆角不是 UltiCode 的视觉语言。

## 4. 阴影（Shadow）

```css
--shadow-float:        0 4px 20px -4px oklch(0 0 0 / 0.08);   /* light */
--shadow-float-hover:  0 8px 30px -4px oklch(0 0 0 / 0.12);   /* light */
--shadow-float:        0 4px 20px -4px oklch(0 0 0 / 0.25);   /* dark */
--shadow-float-hover:  0 8px 30px -4px oklch(0 0 0 / 0.35);   /* dark */
```

**只有两个阴影**——轻浮起 / 重浮起。其他视觉层次用 `--surface-*` / `--border` 表达。

## 5. 动效（Motion）

### 5.1 时长 + 缓动（二选一）

```css
/* 现代风（推荐）—— 显式时长 + 显式缓动 */
--duration-fast:    200ms;
--duration-normal:  350ms;
--ease-out-expo:    cubic-bezier(0.16, 1, 0.3, 1);   /* 减速明显 */
--ease-spring:      cubic-bezier(0.34, 1.56, 0.64, 1); /* 微微回弹 */

/* 兼容风 —— Tailwind transition timing */
--transition-fast:   150ms cubic-bezier(0.4, 0, 0.2, 1);
--transition-normal: 250ms cubic-bezier(0.4, 0, 0.2, 1);
--transition-slow:   400ms cubic-bezier(0.4, 0, 0.2, 1);
```

> **约定**：新代码用现代风（`var(--duration-fast) var(--ease-out-expo)`）。
> 老代码还在用 `--transition-*`，迁移完再统一。

### 5.2 关键帧（keyframes）

`shared/design-system/style.css` 里定义了 5 个：

| keyframes              | 用途                                | 触发 class           |
| ---------------------- | ----------------------------------- | -------------------- |
| `slide-up-fade`        | 元素渐入 + 上移 8px                 | `.animate-stagger > *` |
| `cursor-blink`         | 终端光标 1Hz 闪烁                   | `.terminal-cursor`   |
| `scan-reveal`          | clip-path 横向扫开                  | `.animate-scan`      |
| `subtle-pulse`         | 透明度 0.6 ↔ 1 慢呼吸              | `.animate-pulse-subtle` |
| `collapsible-up` / `collapsible-down` | Radix Collapsible 配套 | Reka UI 内部使用     |

**`prefers-reduced-motion` 已处理**：所有动效在用户系统级开启减少动效时降级为 `none`。
**不要**在组件里重复写 `@media (prefers-reduced-motion: reduce) { ... }`，
除非你引入了一个新的 keyframe。

## 6. 滚动条（Scrollbar）

```css
--scrollbar-thumb:         oklch(0.45 0.03 220);   /* light/dark 不同 */
--scrollbar-thumb-hover:   oklch(0.55 0.025 210);
--scrollbar-thumb-active:  var(--solarized-base0);
```

应用方式：WebKit 用 `::-webkit-scrollbar-thumb`（详见
[`shared/design-system/style.css`](../../shared/design-system/style.css) 的 `@layer base` 段）；
Firefox 用 `scrollbar-color: var(--scrollbar-thumb) transparent;`。

> **不要**修改 `::-webkit-scrollbar { width: 8px; }`——宽度是项目级硬约束（保证表格、代码块的滚动条
> 不挤压内容）。

## 7. 间距 / Z-Index / 断点

**项目没有自定义 spacing / z-index / breakpoint token**。直接用 Tailwind v4 默认：

```html
<div class="p-4 gap-2">                <!-- spacing -->
<div class="z-10 sticky top-0">        <!-- z-index -->
<div class="md:flex lg:grid">          <!-- breakpoint -->
```

需要时也可以写任意值（Tailwind JIT 支持）：

```html
<div class="p-[18px] gap-[7px]">       <!-- 字面量 -->
```

但**优先用默认刻度**（`p-4` 而不是 `p-[18px]`）——保持视觉一致。

## 8. 速查表：我想表达……应该用哪个 token？

| 意图                     | Token                                          | 例子                                       |
| ------------------------ | ---------------------------------------------- | ------------------------------------------ |
| 页面背景                 | `--background` / `bg-background`              | `<body class="bg-background">`              |
| 主要文字                 | `--foreground` / `text-foreground`            | `class="text-foreground"`                  |
| 卡片背景                 | `--card` / `bg-card`                          | `class="bg-card"`                          |
| 弱化文字                 | `--muted-foreground` / `text-muted-foreground`| `class="text-muted-foreground"`            |
| 边框                     | `--border` / `border-border`                  | `class="border border-border"`             |
| 强调 / 链接 / 选中       | `--accent` / `bg-accent text-accent-foreground` | `class="text-accent"`                     |
| 删除 / 危险              | `--destructive` / `bg-destructive`            | `class="bg-destructive text-destructive-foreground"` |
| 焦点环                   | `--ring` / `ring-ring`                        | `class="ring-2 ring-ring"`                 |
| 数字 / 表格 cell         | `class="uc-type-data tabular-nums"`           | `<td class="uc-type-data tabular-nums">`  |
| 终端 label               | `class="terminal-label"`                      | `<span class="terminal-label">USER</span>` |
| 卡片                     | `class="terminal-card"` 或 `class="precision-card"` | `<div class="terminal-card">`             |
| 入场动画                 | `class="animate-stagger"`                     | `<ul class="animate-stagger">`            |
| 中等留白                 | `class="p-4 gap-2"`（Tailwind 默认）          | —                                          |

## 9. 参见

- [`architecture.md`](./architecture.md) — Token 在分层里的位置
- [`theme-modes.md`](./theme-modes.md) — light/dark 怎么切；密度怎么写
- [`components.md`](./components.md) — 完整的组件原语清单
- [`extension.md`](./extension.md) — 加新 token 的步骤
- `shared/design-system/style.css` — Token 定义真源
- `shared/theme/src/typography.css` — 字体 Token 真源
- `shared/theme/src/typography.ts` — 字体 Token 元数据（给 ECharts / Monaco / 测试用）
