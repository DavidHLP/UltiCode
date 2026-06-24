# Sidebar 视觉契约统一 + 沉淀到 shared

> 状态: 📋 Planned · 创建: 2026-06-24 · 关联 commit: `be6152baf` (WIP baseline)

## 1. 背景

`shared/sidebar-menu` 当前只暴露 3 个原子组件（`SidebarMenuItem` / `SidebarMenuSubItem` / `SidebarGroupCollapsible`），且 `SidebarGroupCollapsible` 仅是 reka-ui `CollapsibleRoot` 的透传包装。业务侧（console 的 `SidebarNav` / `NavUser` / `SidebarListSections` 与 management 的 `NavMain` / `NavUser` / `NavSecondary` / `NavDocuments`）不得不**自己拼**视觉：

- 重复书写 `border-l-4 border-[var(--accent-electric)] bg-[var(--accent-electric)]/8` 激活条 class
- 重复书写 2xs 大写 + `tracking-widest` 分组标题
- 重复折叠 chevron 旋转模板
- 重复"行末 hover 显示 MoreHorizontal"按钮结构
- 两份 `NavUser.vue`（console 284 行 / management 140 行）独立实现同一套"头像 + 名称 + 邮箱 + DropdownMenu"

结果是：4 张视觉截图（账户设置 / 题单树 / 论坛 / 比赛）**像素级一致只是巧合**（共享 design tokens），**模板结构到处复制**导致：

1. 新增一种菜单形态 = 两份 app 各加 50+ 行模板
2. 调整激活条颜色或间距 = 全仓搜索替换
3. 业务侧开发无法获得"原子级"复用

## 2. 目标

把"深色底 + 4px 蓝色激活条 + 2xs 大写分组标题 + 折叠 chevron + 用户顶栏 + hover 显示 MoreHorizontal"这一**视觉契约**从业务侧模板中抽出，沉淀为 `shared/sidebar-menu` 内的完整组件库。验收标准：

- 业务侧**不再**手写 `border-l-4 border-[var(--accent-electric)]` 等 class
- console 与 management 的同一种菜单形态**复用同一份** SFC
- 新增 6 个共享组件 + 6 份单测 + 2 份文档
- 净减约 700 行重复模板

## 3. 不变量

- 工作区 commit `be6152baf` 干净（远端 ahead 72 / behind 1 是历史遗留，**不 push**）
- `shared/sidebar-menu` 现有 3 个组件**API 不破坏**（向后兼容）
- `tsconfig.app.json` 已 include `../shared/sidebar-menu/src`（console + management 都已能引入）
- 不引入新依赖（复用 `clsx` + `tailwind-merge`）
- 不改 i18n key / 不改后端 / 不动数据库
- 不动 `useSidebarLists` / `SidebarListDialogs` / `Calendars.vue` 等 console 私有业务组件
- 不动 `PersonalView` / `AccountSecurityView` / `BookmarksView` 等已 commit 的视图组件

## 4. 阶段切分

```
[Stage 1] shared: 视觉契约（CSS token）                      ⏱ ~0.5d
    ↓
[Stage 2] shared: 增强现有 3 个原子组件（向后兼容）           ⏱ ~0.5d
    ↓
[Stage 3] shared: 3 个新组件（Group / Parent / NavUser / IconButton）  ⏱ ~1d
    ↓
[Stage 4] shared: 单元测试覆盖 6 个组件                       ⏱ ~0.5d
    ↓
[Stage 5] console: 替换 AppSidebar / SidebarNav / NavUser / SidebarListSections  ⏱ ~1d
    ↓
[Stage 6] management: 替换 NavMain / NavUser / NavSecondary / NavDocuments   ⏱ ~1d
    ↓
[Stage 7] 文档：shared/sidebar-menu/README.md + wiki 概念页   ⏱ ~0.3d
    ↓
[Stage 8] 验证：type-check + lint + vitest + i18n + visual smoke  ⏱ ~0.2d
```

**总计：~5 人天**（1 个工作日内可完成不含 visual smoke 调试）

## 5. 详细动作

### Stage 1 — `shared/sidebar-menu/src/styles/sidebar-menu.css` 视觉契约

在现有 keyframes 之后追加命名 class 集合（业务侧可直接引用或 override）：

- `.uc-sidebar-item` — 主菜单行：左 4px 透明 / 激活时变 `--accent-electric`；高 `h-9`；hover `--silver-200/40` 背景
- `.uc-sidebar-sub-item` — 子菜单行：同结构，高 `h-8`、font-semibold
- `.uc-sidebar-group-label` — 2xs 大写、`tracking-widest`、`silver-500/400`、hover 变 `--accent-electric`
- `.uc-sidebar-sub-list` — 子项容器：左侧 1px silver-200 竖线 + `pl-2`

**Dark mode**：用 `.dark` 前缀切换 `--silver-400/500` 字色

**验收**：console/management 的 main.css 已 import 此文件，无需新引入

### Stage 2 — 增强现有 3 个组件（API 兼容）

| 组件 | 新增 props | 行为 |
|---|---|---|
| `SidebarMenuItem` | `badge?` / `badgeVariant?` / `iconClass?` / `showChevron?: boolean` | 启用 `showChevron` 时渲染右侧 chevron + emit `toggle` |
| `SidebarMenuSubItem` | `badge?` / `iconClass?` | — |
| `SidebarGroupCollapsible` | 升级为 `SidebarGroup`：新增 `title` / `icon?` / `collapsible?: boolean = true` / `defaultOpen?: boolean = true` / `active?: boolean` | 暴露 `v-model:open` |

激活/未激活切换统一走 `data-active="true|false"` attribute（Stage 1 CSS hook）

### Stage 3 — 3 个新组件

| 组件 | 用途 | 取代 |
|---|---|---|
| `SidebarGroup` | 完整分组容器：标题行 + chevron + slot | console / management 模板里"分组标题"整段（~30 行） |
| `SidebarParentItem` | 父项 = 链接 + 自带可折叠子项（点行跳路由、点 chevron 折叠）| console `SidebarNav.vue` ~80 行 / management `NavMain.vue` ~90 行 |
| `SidebarNavUser` | 顶部用户条：头像 + 名称 + 邮箱 + role 徽章 + DropdownMenu(menuItems) | console `NavUser.vue` 284 行 → 60 行调用；management 140 行 → 50 行 |
| `SidebarIconButton` | 行末"hover 时显示"操作按钮（默认 opacity-0，group-hover 100）| `SidebarListSections` / `NavDocuments` 中 6+ 处重复 button 写法 |

### Stage 4 — 单测

`shared/sidebar-menu/src/__tests__/` 新建：

- `SidebarMenuItem.spec.ts` — `data-active` / `data-size` / `router-link` vs `a` 渲染
- `SidebarMenuSubItem.spec.ts` — 激活/非激活 class 切换
- `SidebarGroupCollapsible.spec.ts` — 标题渲染 / 默认展开 / 折叠动画
- `SidebarParentItem.spec.ts` — 点击行 vs 点击 chevron 行为差异
- `SidebarNavUser.spec.ts` — avatar / role 徽章 / menuItems 渲染
- `SidebarIconButton.spec.ts` — 默认 opacity 0 / hover opacity 100

**测试栈**：vitest + `@vue/test-utils`（已就绪；shared 已有 `vitest.config.ts`）

### Stage 5 — console 接入

| 业务文件 | 替换内容 | 目标行数 |
|---|---|---|
| `console/src/features/sider/AppSidebar.vue` | header 改用 `SidebarNavUser` | -20 |
| `console/src/features/sider/SidebarNav.vue` | collapsed 分支删（`SidebarGroup` 自带）；父项用 `SidebarParentItem` | 345 → 90 |
| `console/src/features/sider/NavUser.vue` | 改用 `SidebarNavUser` 传 menuItems | 284 → 60 |
| `console/src/features/sider/components/SidebarListSections.vue` | 4 处 hover 按钮改用 `SidebarIconButton` | 375 → 320 |

**约束**：

- 保留 `useSidebarLists` / `SidebarListDialogs` / `Calendars.vue` 不动
- 保留 `sidebar.data.ts` 形状不变
- 不改 `PersonalView` / `AccountSecurityView` / `BookmarksView` 等视图组件

**console 验收**：

- 4 张截图的视觉**与改造前像素级一致**
- collapsed 态行为正常
- vitest 现有 8+ 个 spec 全过

### Stage 6 — management 接入

| 业务文件 | 替换内容 | 目标行数 |
|---|---|---|
| `management/src/components/layout/AppSidebar.vue` | 内容（数据）不动 | 0 |
| `management/src/components/layout/NavMain.vue` | 父项用 `SidebarParentItem`；主行用 `SidebarMenuItem` | 264 → 110 |
| `management/src/components/layout/NavUser.vue` | 改用 `SidebarNavUser` | 140 → 50 |
| `management/src/components/layout/NavSecondary.vue` | 改用 `SidebarMenuItem` 替代手写 `SidebarMenuButton` | 75 → 30 |
| `management/src/components/layout/NavDocuments.vue` | 改用 `SidebarMenuItem` + `SidebarIconButton` | 85 → 50 |

**management 验收**：

- 导航行为不变（权限过滤、跳转、折叠）
- 类型检查通过
- vitest 现有 spec 全过

### Stage 7 — 文档

- `shared/sidebar-menu/README.md` 新建：组件清单 + 视觉契约图 + 业务接入示例 + 反模式清单
- `wiki/concepts/sidebar-menu.md` 新建：起源、契约、CSS token 起源、与 `shared/auth-ui` / `shared/badge-config` / `shared/theme` 的关系
- `wiki/SCHEMA.md` 更新：在 entities / concepts 列表追加 `sidebar-menu`
- 跑 `scripts/dev/wiki-manifest.sh` 刷新 `wiki/.meta/manifest.json`（CLAUDE.md §10 流程）

### Stage 8 — 验证

```bash
# 后端保险跑
cd backend-spring && ./mvnw -q test -B -Dtest='UserServiceImplTest' 2>&1 | tail -20

# shared
cd shared/sidebar-menu && pnpm test && pnpm type-check

# console
cd console && pnpm lint && pnpm type-check && pnpm test && pnpm validate:i18n-keys

# management
cd management && pnpm lint && pnpm type-check && pnpm test && pnpm validate:i18n-keys

# 全局空白
git diff --check
```

**Visual smoke**（手动，必做）：

- console:9002 打开 / personal / problemset / contest / forum 四个 context，确认 4 张截图视觉一致
- management:9003 展开 / 折叠 sidebar，确认权限菜单 / 搜索 / 帮助 / 设置渲染

## 6. 提交计划

每个 Stage 一个 commit，独立可工作、便于 review / revert：

```
1. refactor(shared/sidebar-menu): add visual contract CSS class set
2. refactor(shared/sidebar-menu): extend SidebarMenuItem / SubItem / Group with badge + icon props
3. feat(shared/sidebar-menu): add SidebarGroup / SidebarParentItem / SidebarNavUser / SidebarIconButton
4. test(shared/sidebar-menu): cover 6 sidebar components with vitest
5. refactor(console): adopt shared sidebar components in AppSidebar / SidebarNav / NavUser / SidebarListSections
6. refactor(management): adopt shared sidebar components in NavMain / NavUser / NavSecondary / NavDocuments
7. docs(shared/sidebar-menu): add README + wiki concept page
```

## 7. 风险 & 缓解

| 风险 | 缓解 |
|---|---|
| console / management 视觉实际**不是**像素级一致（icon 字体库不同：lucide vs tabler）| 接入完成后**只对 console 做 visual smoke**（用截图比 pixel），management 走"行为 + type-check + lint" |
| 替换 `NavUser` 后 role 徽章定位 / 颜色对不上 | Stage 5 / 6 替换**保留所有 wrapper class**（rounded / border / spacing），只把"组件树"换成 shared |
| shared 组件被两个 app 引用时，路径解析失败 | tsconfig 已 include，无需新增；运行时 Vite 解析 `@/shared/sidebar-menu/src` 走 symlink |
| 触发 wiki-manifest 校验失败 | Stage 7 跑 `scripts/dev/wiki-manifest.sh` 刷新 `wiki/.meta/manifest.json` |
| SidebarParentItem 行为差异：console 的"父项 = 链接 + 可折叠" vs management "父项 = 不跳 + 折叠" | 通过 `onParentClick?: () =\u003e void` prop 区分：传了 = 跳；不传 = 仅折叠 |

## 8. 不在本次范围

- ❌ 不动 `SidebarListSections` 的"树形 hover 显示 MoreHorizontal"业务逻辑（仅替换按钮为 `SidebarIconButton`）
- ❌ 不动 `useSidebarLists` 的 CRUD 状态机
- ❌ 不动 `Calendars.vue`（题单按钮 + dialogs）
- ❌ 不动 `AccountSecurityView` / `AccountNotificationsView` 等视图（已 commit，留给后续任务）
- ❌ 不动后端 / 数据库 / i18n key / theme tokens
- ❌ 不引入新依赖 / 不升 vue-tsc / 不改 tsconfig

## 9. 关联

- 上游 commit: `be6152baf chore: rollup WIP for unified sidebar work and account/user updates`
- 下游预期 commits: `refactor(shared/sidebar-menu): add visual contract CSS class set`（Stage 1 首 commit）
- 关联 wiki 页: `wiki/concepts/sidebar-menu.md`（Stage 7 创建）
- 关联 shared 包: `@ulticode/sidebar-menu`（workspace 已在 `pnpm-workspace.yaml` 注册）
- 关联设计 token: `--accent-electric` / `--silver-*` / `--solarized-base01`（在 `shared/design-system/style.css` 定义）
## 10. Landed (2026-06-24)

已按 8 个 Stage 全部落地（commits a47423c4d → 94fe6f362）。三份代码审查（.CR-2026-06-24.md / .CR-2026-06-24.codex.md / .review.md）的修订已吸收进实施计划 .claude/PRPs/plans/sidebar-menu-unification.plan.md，逐条：

- B1/C2：CSS 在 console/management style.css 显式 @import（而非"已 import 无需新引入"）
- B2：新建 shared/sidebar-menu/vitest.config.ts（jsdom + @vitejs/plugin-vue）+ @vue/test-utils，27 spec 全过
- B3/A1：SidebarGroupCollapsible 不改名（避免与本地 shadcn ui/sidebar/SidebarGroup.vue 撞名），新增 SidebarParentItem 承担"父项=链接+折叠"职责，废弃原计划的撞名新 SidebarGroup
- B4：management AppSidebar.vue（271 行）纳入 Stage 6 范围（不再标"0 改动"）
- H1：shadcn 原语（@/components/ui/sidebar）与 shared 视觉契约层（@/shared/sidebar-menu/src）的双套同名体系 + 别名 import 约定已固化
- H2：shared 组件 icon 一律由 prop/slot 传入，绝不 import 图标库（console=lucide / management=tabler 共存）

实施偏差（详见 .claude/PRPs/reports/sidebar-menu-unification-report.md）：

- console NavUser / AppSidebar / SidebarListSections 保留——NavUser 的 DropdownMenu trigger+菜单+guest/auth/notification/logout 业务逻辑密集，SidebarListSections 的 group/item named-hover 约定，均与 shared 展示组件结构不兼容。console sidebar.data 当前扁平（无 children），父项分支为死代码，已用 SidebarParentItem 替换并清理 handleParentClick / openParents / watch（净减 ~100 行）。
- management 父项 / plain item 因 font-mono / text-xs 终端风格 + collapsed-tooltip 依赖 shadcn SidebarMenuButton，保留组件、仅提取 itemRowClass(active) 去重三处激活 class；子项已用 SharedSidebarMenuSubItem。
- 知识沉淀：wiki/concepts/sidebar-menu.md（ADR-005）。
