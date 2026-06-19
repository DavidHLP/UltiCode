---
title: 组件级覆盖策略与原语清单
tags: [reference, frontend, living]
status: living
updated: 2026-06-19
owner: design-system
---

# 组件级覆盖策略与原语清单

> **范围**：UltiCode 提供的所有"组件原语"（`.terminal-*` / `.precision-*` / `.font-*` /
> `.uc-type-*` / `.animate-*` / `.hljs-*` / `.ascii-progress` / `.header-btn`），
> 以及**写新组件时**应当遵守的覆盖策略。
>
> 配套阅读：[`architecture.md`](./architecture.md) · [`design-tokens.md`](./design-tokens.md) ·
> [`extension.md`](./extension.md)

## 1. 覆盖策略（重要）

写新组件时，**优先级从高到低**：

| 优先级 | 选择                          | 适用场景                                  | 反例                                      |
| ------ | ----------------------------- | ----------------------------------------- | ----------------------------------------- |
| **P0** | **已有原语 class**            | 完全匹配需求                              | 重复发明 `.my-button`                    |
| **P1** | **Tailwind utility + token**  | 简单布局：边距、颜色、字号                | `class="bg-[#fff]"` 写 hex               |
| **P2** | **`<style scoped>` + token**  | 组件特有的复杂样式（pseudo 状态、动画）   | 写 hex / rgb                              |
| **P3** | **`style="..."` 内联**        | 几乎不用——只允许动态值（如图表尺寸）      | 写 `style="color: red"`                  |

**绝对禁止**：

```vue
<!-- ❌ hex / rgb / hsl -->
<div class="bg-[#1a2332] text-[rgb(255,255,255)]">

<!-- ❌ 直接读 token 字面量到 style -->
<div :style="{ color: '#fff' }">

<!-- ❌ 写自己的颜色而不走 token -->
<div class="text-slate-900">  <!-- Tailwind 默认 slate 调色，不在主题里 -->
```

**正确**：

```vue
<!-- ✅ 走 token（dark 模式自动适配） -->
<div class="bg-card text-foreground border border-border">

<!-- ✅ 复杂样式用 scoped + token -->
<style scoped>
.special-card {
  background: color-mix(in oklch, var(--accent) 8%, transparent);
  border: 1px solid var(--border);
}
.special-card:hover {
  background: color-mix(in oklch, var(--accent) 18%, transparent);
}
</style>
```

**为什么这么严？** 因为主题切换（dark 模式）依赖**所有颜色都通过 `var(--*)` 解析**。
一旦你写一个 hex 写死，dark 模式切换就改不动那一处——这个组件就成了"主题孤儿"。

## 2. 语义角色（Semantic Roles）

详细 token 见 [`design-tokens.md` §2.2](./design-tokens.md#22-语义角色semantic-roles)。
下面是**"我是组件作者，该用哪个"** 的决策表：

| 我在写什么                 | 用哪个 utility class          | 例                                   |
| -------------------------- | ----------------------------- | ------------------------------------ |
| `<body>` 默认文字          | `class="uc-type-body"`        | `body { font: ...; }` 已在 design-system 里设好，不需要在组件上再写 |
| `<h1>` 页面主标题          | `class="uc-type-page-title"`  | `<h1 class="uc-type-page-title">`    |
| `<h2>` 段落标题            | `class="uc-type-section-title"` | `<h2 class="uc-type-section-title">` |
| 卡片标题                   | `class="uc-type-card-title"`  | `<h3 class="uc-type-card-title">`    |
| 按钮文字 / 输入框          | `class="uc-type-control"`     | `<button class="uc-type-control">`   |
| 终端风 label（大写 + 间距）| `class="terminal-label"`      | `<span class="terminal-label">USER</span>` |
| 表格 cell 数字             | `class="uc-type-data tabular-nums"` | `<td class="uc-type-data tabular-nums">` |
| 行内 code                  | `class="uc-type-code"`        | `<code class="uc-type-code">`        |
| Markdown 正文              | `class="markdown-block"`      | `<article class="markdown-block">`   |

> **不要**自己拼 `class="text-sm font-medium leading-tight"`——已经在
> `.uc-type-control` 里了。**拼出来的不算，会和密度切换脱钩**。

## 3. Utility 类

### 3.1 字体 utility（来自 typography.css）

| Class         | 用途                          | 何时不要用                              |
| ------------- | ----------------------------- | --------------------------------------- |
| `font-sans`   | 楷体（项目统一字体）          | 想用等宽（用 `font-mono`，虽然也是楷体） |
| `font-mono`   | 楷体（别名 `font-sans`）      | 数值用 `font-data`                      |
| `font-data`   | 楷体 + tabular-nums           | 普通文字用 `font-sans`                  |
| `tabular-nums` | 数字等宽（font-feature）     | —                                       |

> **项目范围统一使用楷体**（2026-06-19 决议）。所以 `font-mono` 和 `font-sans` 在
> 实际上都解析到同一个楷体——`font-mono` 类保留是为了代码区的**语义意图**清晰。

### 3.2 终端风（terminal-*）

来自 `shared/design-system/style.css` 的 "Terminal Precision Design System" 段。
**给 OJ 平台数据密集页面用**——提交详情、排行榜、状态码、控制台输出。

| Class                          | 作用                                                                 |
| ------------------------------ | -------------------------------------------------------------------- |
| `.terminal-card`               | 硬朗卡片：圆角 0，1px 边框，`--shadow-float`                         |
| `.terminal-card-header`        | 卡片头：上分隔线 + 大写 label 样式                                  |
| `.terminal-tab`                | 标签页按钮：响应式过渡，0 圆角                                      |
| `.terminal-separator`          | 3px 虚线分隔（`repeating-linear-gradient`）                          |
| `.terminal-input`              | 输入框：硬朗 1px 边框 + 蓝色焦点环                                 |
| `.terminal-label`              | 文字：大写 + 极小号 + 大字距 + 灰文字                              |
| `.terminal-comment`            | 文字：斜体 + `//` 前缀 + 灰                                        |
| `.terminal-prompt`             | 文字：蓝色 + `> ` 前缀                                             |
| `.terminal-cursor`             | 蓝色闪烁方块（1Hz）                                                |
| `.terminal-badge`              | 通用徽章底（透明背景 + 字号 + 字距）                               |
| `.terminal-badge-success`      | 绿底绿字绿边（`oklch green 15% transparent` 背景）                  |
| `.terminal-badge-warning`      | 琥珀色                                                            |
| `.terminal-badge-error`        | 红色                                                              |
| `.terminal-badge-info`         | 青色                                                              |
| `.terminal-badge-purple`       | 紫色                                                              |
| `.terminal-badge-electric`     | 蓝色                                                              |
| `.terminal-badge-neutral`      | 银灰                                                              |
| `.terminal-badge-primary`      | 主蓝（带 40% 透明边框）                                            |
| `.terminal-success` / `.terminal-warning` / `.terminal-error` / `.terminal-info` | 纯文字颜色：绿/琥珀/红/青              |
| `.terminal-table`              | 表格容器：横向滚动 + 触屏优化                                      |
| `.terminal-table-row`          | 表格行：hover 浅表面色                                             |
| `.terminal-row-num`            | 行号：右对齐 + 极小号 + 灰 + tabular                               |
| `.terminal-kv-key` / `.terminal-kv-value` | 键值对文字（label + data 风格）                           |

**徽章模式**（`color-mix(in oklch, var(--terminal-X) 15%, transparent)`）的妙处：
颜色随主题自动适配——dark 模式下的 15% 透明度和 light 模式下视觉等效。
**不要**改这个 15%。

### 3.3 精准仪表盘（precision-*）

给数据卡片 / 图表容器用。**比 terminal 风格更"软"一点**。

| Class                | 作用                                                                 |
| -------------------- | -------------------------------------------------------------------- |
| `.precision-card`    | 卡片：hover 上移 2px + 阴影增强                                     |
| `.precision-divider` | 1px 渐变分隔线（中间实，两端透）                                     |
| `.border-silver`     | 边框色 = silver-200（light）/ silver-300（dark），自动适配           |
| `.shadow-float`      | 应用 `--shadow-float`                                                |
| `.timeline-line`     | 左边线（24px 之下画 1px 垂直线）                                    |
| `.glow-accent`       | 0 0 0 3px `--accent-glow`（蓝色光晕）                               |
| `.status-success` / `.status-warning` / `.status-error` | 纯文字颜色：绿/黄/红                |

### 3.4 动画 utility

| Class                       | 动画                       | 典型场景                  |
| --------------------------- | -------------------------- | ------------------------- |
| `.animate-stagger > *`      | 子元素依次淡入（50ms 错开）| 列表 / 卡片网格加载       |
| `.animate-pulse-subtle`     | 透明度 0.6 ↔ 1 慢呼吸      | "正在加载"指示            |
| `.animate-scan`             | clip-path 横向扫开         | 一次性揭示                |
| `.terminal-cursor`          | 1Hz 闪烁                   | 终端 prompt               |

> **不要**自己写 `@keyframes`——除非 5 个内置动画都不满足。
> 加新动画时同步加 `prefers-reduced-motion` 处理（参见
> [`shared/design-system/style.css`](../../shared/design-system/style.css) 末段）。

### 3.5 Markdown / KaTeX / highlight.js

```vue
<article class="markdown-block">
  <h1>问题描述</h1>
  <p>...</p>
  <pre><code class="language-cpp">...</code></pre>
</article>
```

`.markdown-block` 在 `shared/design-system/style.css` 里定义了完整的 Markdown
样式契约（h1-h6、p、code、pre、blockquote、table、list）。
**问题描述 / 题解 / 论坛 / 题单** 都用这个 class。

management 还有一个 `.prose` 别名——同样的样式集。**新代码不要再用 `.prose`**，
迁移完成后会移除别名。

代码高亮：`highlight.js` 已经在 `console/index.html` 通过 CDN 引入
（`github-dark.min.css`），但被 `.hljs` 类重写为 Solarized 调色板。
**不要**直接引入其他 highlight.js 主题。

数学：`katex.min.css` 同样通过 CDN 引入。**不要**直接 `class="katex"` 改样式——
通过 `--katex-*` token（如有）或在 markdown 容器里覆盖。

### 3.6 杂项

| Class                  | 作用                                                                 |
| ---------------------- | -------------------------------------------------------------------- |
| `.header-btn`          | 顶部导航按钮：硬朗 32px 高、大写 11px、半透明灰                     |
| `.ascii-progress` / `.ascii-progress-track` / `.ascii-progress-fill` | 终端风进度条：等宽字符 + 蓝色填充              |
| `.scrollbar-hide`      | 完全隐藏滚动条（用于装饰性横向滚动）                                |
| `.markdown-block` / `.prose` | Markdown 正文容器                                              |
| `.glow-accent`         | 0 0 0 3px 蓝色光晕（`--accent-glow`）                               |

## 4. 组件级 override 的工作流

**场景**：你的组件需要"卡片默认样式 + 蓝色左边框"。

**反例**：

```vue
<style scoped>
.my-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);  /* ❌ 这部分还行 */
  border-radius: 8px;                    /* ❌ 项目里所有圆角都是 0 */
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1); /* ❌ 写死了阴影 + 用了 rgba */
}
</style>
```

**正确**：

```vue
<template>
  <div class="terminal-card my-special-card">
    <slot />
  </div>
</template>

<style scoped>
.my-special-card {
  border-left: 3px solid var(--accent);
  /* 其它都从 .terminal-card 继承，不用写 */
}
</style>
```

或者用 Tailwind utility：

```vue
<template>
  <div class="terminal-card border-l-[3px] border-l-accent">
    <slot />
  </div>
</template>
```

**复杂状态**（如 hover / focus / active）：

```vue
<style scoped>
.my-special-card {
  background: var(--card);
  transition: background var(--duration-fast) var(--ease-out-expo);
}
.my-special-card:hover {
  background: color-mix(in oklch, var(--accent) 8%, var(--card));
}
</style>
```

## 5. 组件级 vs 全局 utility 的边界

**组件级 `<style scoped>` 应该：**

- 写**只属于这个组件**的样式
- 引用全局 token（`var(--*)`）
- 写**伪状态**（`:hover` / `:focus` / `:active` / `:disabled`）
- 写**结构性**样式（`position`、`display: grid`、自定义 grid template）

**组件级 `<style scoped>` 不应该：**

- 定义新颜色（应该走 token，见 [`extension.md` §1](./extension.md#1-加新token)）
- 重新声明全局原语（不要 `.my-button { @apply .terminal-card }`——直接用 `class="terminal-card"`）
- 写响应式断点以外的字面值（间距/字号用 token 或 Tailwind）

**全局 utility 应该：**

- 跨多个组件复用
- 有**明确语义**（"我是 terminal label" 不是 "我字号 11px 大写"）
- 用 token 而不是字面量

## 6. 实战：写一个"赛况实时滚动"组件

```vue
<!-- LiveContestFeed.vue -->
<template>
  <div class="terminal-card overflow-hidden">
    <div class="terminal-card-header">
      <span class="terminal-label">LIVE</span>
      <span class="ml-auto font-data tabular-nums">{{ submissions.length }}</span>
    </div>
    <ul class="animate-stagger">
      <li
        v-for="sub in submissions"
        :key="sub.id"
        class="terminal-table-row flex items-center gap-3 px-3 py-2"
      >
        <span class="terminal-row-num w-10">{{ sub.id }}</span>
        <span class="uc-type-control flex-1">{{ sub.user }}</span>
        <span
          :class="[
            'terminal-badge',
            verdictClass(sub.verdict),
          ]"
        >
          {{ sub.verdict }}
        </span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
// 注意：没有 style scoped — 全靠终端风原语
</script>
```

**检查清单**：

- [x] 用了 `.terminal-card` 卡片原语
- [x] 用了 `.terminal-card-header` 卡片头
- [x] 用了 `.terminal-label` 大写 label
- [x] 用了 `.font-data` + `tabular-nums` 等宽数字
- [x] 用了 `.animate-stagger` 错开入场
- [x] 用了 `.terminal-table-row` 行 hover
- [x] 用了 `.terminal-row-num` 灰色行号
- [x] 用了 `.uc-type-control` 控件字号
- [x] 用了 `.terminal-badge-*` 状态徽章
- [ ] **没有**写自己的颜色 / 阴影 / 字号
- [ ] **没有**写 hex / rgb

## 7. 参见

- [`architecture.md`](./architecture.md) — 原语在分层里的位置（L4）
- [`design-tokens.md`](./design-tokens.md) — 原语背后引用的 token
- [`theme-modes.md`](./theme-modes.md) — 原语怎么响应 dark 模式
- [`extension.md`](./extension.md) — 加新原语的步骤
- `shared/design-system/style.css` — 原语实现真源（1257 行）
