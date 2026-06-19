# 项目截图

本目录收录在 README 顶部"项目截图"区块里展示的界面截图。

## 命名约定

`{area}-{view}-{theme}.{ext}`

- `area` — 功能区，例如 `analytics` / `forum` / `contests` / `problems` / `admin`
- `view` — 具体视图，例如 `dashboard` / `thread-list` / `thread-detail` / `problem-list` / `problem-detail`
- `theme` — 主题，`light` 或 `dark`
- `ext` — 扩展名，建议使用 `.png`（无损，便于缩略）

## 收录清单

| 文件名 | 视图 | 主题 | 应用 | 用途 |
|---|---|---|---|---|
| `forum-list-light.png` | 论坛 — 帖子列表 + 筛选器 + 自定义列 | light | console (9002) | 展示社区列表交互能力（仅此一张 light 主题图，8002 默认走 light） |
| `forum-thread-dark.png` | 论坛 — 平台 (Platform) tab 帖子流 | dark | console (9002) | 展示社区内容浏览的主入口 |
| `forum-detail-dark.png` | 论坛 — 单帖详情（带运营复盘内容） | dark | console (9002) | 展示 Markdown 富文本渲染 |
| `contests-dark.png` | UltiCode 竞赛 — 排行榜（带奖牌 emoji）+ 往期竞赛列表 + 操作列 | dark | console (9002) | 展示竞赛系统核心 UI |
| `problem-set-dark.png` | 题库 — 三个专题卡片（必刷题单/经典题/区间与排序）+ 题单列表 + 每日一题日历 | dark | console (9002) | 展示题单系统入口与日历互动 |
| `problem-detail-dark.png` | 题目详情 — 题目描述 / 题解 / 提交记录 tabs + Monaco 代码编辑器 + 测试用例 tabs | dark | console (9002) | 展示在线编程 IDE 集成 |
| `analytics-dashboard-dark.png` | 管理后台 — 数据分析（4 块指标卡 + 活跃用户趋势 + 活跃时段热力图） | dark | management (9003) | 展示 admin 数据可视化能力 |
| `admin-dashboard-dark.png` | 管理后台 — 仪表板（4 块 stat card + 用户注册趋势 + 活动时间线） | dark | management (9003) | 展示 admin dashboard 的实时活动总览 |

## 主题分布说明

- **9002 console** 1 张 light + 5 张 dark：项目里 9002 走 light 主题是开发默认，dark 主题才是设计语言的重点展示
- **9003 management** 2 张 dark：管理端整体走 dark 主题（终端精密仪器风格），无 light 变体

## 如何判断截图来自 9002 还是 9003

- **9002 console** sidebar 顶部有 "数据概览" section header，"帮助" 不带"获取"
- **9003 management** sidebar 顶部直接是 "仪表板"，"获取帮助" 全名，且"用户与安全"下有"审核管理 / 通知管理"子项（9002 没有）

## 维护

- 添加新截图时，先按上面的命名约定生成文件，再在此表追加一行
- 如果 README 引用的图片找不到，CI/文档构建可能会失败 — 提交前确认 `docs/screenshots/` 下文件存在
- 推荐尺寸：宽 ≤ 1600 px，文件 ≤ 500 KB。可以用 `pngquant` 或 `cwebp` 压缩
