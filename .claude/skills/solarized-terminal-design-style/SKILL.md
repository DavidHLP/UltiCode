---
name: solarized-terminal-design-style
description: "UltiCode Console 项目的 Solarized Terminal / Precision 风格设计系统规范，用于保持前端界面风格一致性"
user-invocable: false
origin: auto-extracted
---

# Solarized Terminal 设计风格规范

**提取时间:** 2026-06-02
**上下文:** UltiCode Console 前端项目（基于 Vue 3 + TailwindCSS v4 + Reka UI）采用了一套非常独特且高度一致的 Solarized 终端精密设计系统（Solarized Terminal / Precision Design System）。为了在后续的功能开发中保持界面美感、交互逻辑和设计语言的绝对一致性，提炼并封装此设计系统规范。

---

## 核心设计理念

Console 项目的整体设计旨在营造一种**复古终端数据看板（Terminal/Dashboard）**的极客精密感，同时保证长时期使用的视觉舒适度。核心原则包括：

1. **绝对直角 (Sharp Corners)**: 项目全局设置 `--radius: 0`。所有卡片、按钮、输入框、徽章等组件在任何模式下均无圆角，全部为 90 度直角。
2. **Solarized 色彩系统**: 基于经典的 Solarized 配色方案，采用 `oklch` 色彩空间定义。提供低对比度、高可读性的 Light / Dark 两套主题。
3. **数据拟真与等宽排版 (Monospace Priority)**: 数据展示、数字、状态、代码及命令行相关区域必须优先使用等宽字体（JetBrains Mono），并使用等宽数字对齐以防抖动。
4. **终端微观动效 (Micro-Animations)**: 模拟终端闪烁光标、扫描线显现、卡片悬浮浮动（精密投影与微小平移）、渐次延迟入场等。

---

## 设计规范细节

### 1. 字体与排版样式 (Typography)

* **全局 Sans 字体**: 用于常规界面文本与描述性段落。
* **等宽数据字体 (`.font-data`)**: 用于数字、指标、表格数据、代码和标签。
  * 字体声明: `"JetBrains Mono", "SF Mono", "Roboto Mono", ui-monospace, monospace`
  * 属性配置: 启用等宽数字对齐 `font-feature-settings: "tnum" on, "lnum" on;`
* **等宽数字对齐 (`.tabular-nums`)**:
  * 属性配置: `font-variant-numeric: tabular-nums;`，保证列表数据或数值在快速变化时位置不会抖动。

### 2. 色彩代币规范 (oklch Colors)

色彩在 [/console/src/style.css](file:///home/david/project/UltiCode-Public-Next/console/src/style.css) 中定义：

#### 基础背景与前景 (Light / Dark)
| 变量名 | Light 模式值 | Dark 模式值 | 作用描述 |
| :--- | :--- | :--- | :--- |
| `--background` | `oklch(0.9735 0.0261 90.1)` | `oklch(0.2673 0.0486 219.8)` | 全局背景层 (Solarized Base3 / Base03) |
| `--foreground` | `oklch(0.5682 0.0285 221.9)` | `oklch(0.6537 0.0197 205.3)` | 主文本前景层 (Solarized Base00 / Base0) |
| `--card` | `oklch(0.9735 0.0261 90.1)` | `oklch(0.3092 0.0518 219.7)` | 卡片表面背景层 |
| `--surface-elevated`| `oklch(0.9735 0.0261 90.1)`| `oklch(0.3092 0.0518 219.7)` | 悬浮提升层 |
| `--surface-sunken`  | `oklch(0.9306 0.0260 92.4)` | `oklch(0.2673 0.0486 219.8)` | 凹陷背景层（输入框、次级容器背景）|

#### 主色彩与终端语义色彩 (Terminal Status Colors)
在全局通用，不随 Light/Dark 改变：
* **核心高亮 (Electric Blue)**: `--accent-electric: oklch(0.6149 0.1394 244.9)` - 激活状态、焦点边框、提示词。
* **成功/Solved/Easy**: `--terminal-green: oklch(0.6444 0.1508 118.6)`
* **警告/Attempted/Medium**: `--terminal-amber: oklch(0.6545 0.1340 85.7)`
* **错误/Hard**: `--terminal-red: oklch(0.5863 0.2064 27.1)`
* **信息/Info**: `--terminal-cyan: oklch(0.6437 0.1019 187.4)`
* **紫色**: `--terminal-purple: oklch(0.5924 0.2025 355.9)`

#### 单色灰度银色尺 (Monotones)
* 用于细边框、次要描述文本。Light/Dark 进行了倒置映射以确保合适的对比度：
  * Light 模式: `--silver-50` (最浅灰 bg) 至 `--silver-900` (最深体 text)。
  * Dark 模式: 倒置配置，使得 `--silver-50` 对应深色背景，`--silver-900` 对应浅色前景。

---

## 组件样式实现模式 (Component Implementation Patterns)

### 1. 终端卡片 (Cards)
* **精密卡片 (`.precision-card`)**:
  * 基础样式: 直角、1px 银色细边框、Sunken/Elevated 搭配。
  * 动效样式: 悬浮时产生微小的 Y 轴平移，并伴有软阴影扩张。
  * 实现示例:
    ```html
    <div class="rounded-none border border-silver bg-card p-4 transition-all hover:-translate-y-0.5 hover:shadow-md">
      ...
    </div>
    ```
* **终端卡片头 (`.terminal-card-header`)**:
  * 顶部有独特的浅色背景和底部精细的点阵/渐变线条作为 LCD 分隔线。
  * 实现示例: 采用类类样式 `.terminal-card-header`，或手动使用背景渐变生成单像素下边框。

### 2. 状态徽章 (Badges)
* 统一使用直角徽章 `.terminal-badge`，文本大写，字符间距微调。
* 使用 `color-mix` 与背景混合（通常 15% 透明度底色，30%~40% 透明度边框）。
* 实现示例：
  ```html
  <span class="font-data text-xs px-2 py-0.5 border bg-[color-mix(in_oklch,var(--terminal-green)_15%,transparent)] text-[var(--terminal-green)] border-[color-mix(in_oklch,var(--terminal-green)_30%,transparent)]">
    SOLVED
  </span>
  ```

### 3. ASCII 进度条 (ASCII Progress Bars)
* 避免使用现代圆润的 HTML5 进度条。
* 采用等宽字符点阵或方块拼合形式的进度展示（如 `[■■■■■□□□□□]` 或精细模拟的 ASCII Progress）。
* 结构配合 `.ascii-progress`, `.ascii-progress-track`, `.ascii-progress-fill` 完成字符染色。

### 4. 终端输入框 (Inputs)
* 必须为绝对直角 `rounded-none`。
* 边框为 `.border-silver`，底色为暗色的凹陷层 `bg-surface-sunken`。
* 聚焦时使用 Electric Blue 作为边框，并附加柔和的 `shadow-accent`（发光边框效果）。
  ```html
  <input class="rounded-none border border-silver bg-[var(--surface-sunken)] px-3 py-1.5 font-data focus:outline-none focus:border-[var(--accent-electric)] focus:ring-2 focus:ring-[var(--accent-electric-glow)]" />
  ```

### 5. 分隔线 (Separators)
* 项目中广泛使用点阵、虚线和双层微细虚线。
* `.terminal-separator` 类使用重复的线性渐变（`repeating-linear-gradient`）生成点阵分割效果：
  ```css
  background: repeating-linear-gradient(to bottom, var(--silver-200) 0px, var(--silver-200) 1px, transparent 1px, transparent 3px);
  ```

---

## 避坑指南与常见错误 (Anti-Patterns)

* **❌ 严禁使用任何 `rounded-*` 类名 (如 `rounded-md`, `rounded-lg`, `rounded-full`)。**
  * *例外情况:* 仅在极少数非对称拟物化小徽章或绝对外部依赖项中允许使用，常规开发中所有元素必须是 `rounded-none` 或完全无圆角。
* **❌ 严禁使用粗重的拟物阴影。**
  * 必须使用经过细致调节的 `--shadow-float` (低透黑色 oklch(0 0 0 / 0.08) 在 Light 下，或 oklch(0 0 0 / 0.25) 在 Dark 下)。
* **❌ 严禁在等宽数据展示中使用默认 Sans-serif 字体。**
  * 代码、分数值、状态百分比、时间戳、卡片计数指标等，均需包裹在 `.font-data` / `font-mono` 中。
* **❌ 严禁使用高饱和度的大红大绿。**
  * 状态的绿、黄、红必须使用项目定义的 `--terminal-green` / `--terminal-amber` / `--terminal-red` 以保持 Solarized 风格的柔和与严肃感。
