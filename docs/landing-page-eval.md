# Console 落地页设计评估与扩充方案

> 评估对象: `console/src/views/LandingView.vue` (435 行, 4 个 `<section>`)
> 评估日期: 2026-07-11
> 评估视角: 项目经理 / 美术设计 / 前端开发

---

## 一、项目经理 (PM) 视角

### 现状

`LandingView.vue` 435 行, 仅 4 个 `<section>` —— Header / Hero(workbench 演示) / Why-UltiCode(三列价值) / CTA / Footer。首屏能讲清"我们是什么", 无法回答用户三连问: 凭什么信你 / 解决什么具体场景 / 现在要不要用。

### 核心缺失

1. **零社会证明** — 无用户数、题库数、提交量、在线人数。技术社区用户(尤其 OI/ACM 出身)对数字敏感, 缺少 `50K+ 用户 / 3K+ 题目 / 7 种语言` 一类锚点, 转化率会被同类竞品(Luogu / Codeforces / LeetCode)碾压。
2. **零使用场景细分** — 学员刷题 / 高校 OJ / 企业内训 / 算法竞赛 / 求职面试, 差异化定位未说明。
3. **零功能清单** — 在线 IDE / 判题队列 / 比赛系统 / 讨论区 / 题单标签 / Markdown 题解 / 个人统计, 这些都没列。用户不会点"免费开始"因为他不知道点完能干嘛。
4. **零信任锚** — 无 FAQ、无路线图、无 changelog、无"谁在用"。冷启动项目最缺的就是可信度铺垫。
5. **CTA 单点** — 只有底部一个"免费开始"。缺少次级 CTA(看题库 / 读文档 / 加群), 不同决策阶段的用户没出口。
6. **i18n 文案** — 43 个 key, 文案密度足够, 但全集中在 hero。后续 section 需新增 `landing.feature.*` / `landing.useCase.*` / `landing.faq.*` / `landing.cta.*` 等命名空间。

---

## 二、美术设计 (Art) 视角

### 现状

单色调 + silver 边框 + 方块阴影(`shadow-[6px_6px_0]`), 赛博朋克 / 工程师审美, 识别度高, 但视觉密度单一。

### 问题

1. **节奏单调** — Hero 之后直接进三列卡片, 中间无视觉断点(分隔线 / 数据带 / 滚动 pin)。
2. **缺主视觉素材** — 0 个图片 / 视频 / 插画 / 截图。技术产品最容易出彩的就是"判题队列动画 / IDE 截图 / 排行榜动效", 纯文字 + 方框说服力弱。
3. **配色维度不足** — 单色系 + electric 强调色, 易审美疲劳。需: 数据可视化色(绿 / 橙 / 红 = AC / PE / WA)、状态语义色、图表渐变。
4. **缺对比段落** — 现在全是亮底。中间应出现一处**深色 / 暗调段落**(例如终端风 demo 区、代码高亮墙), 制造"翻页感"。
5. **图标全靠 lucide** — 12+ 章节全用同套线性图标, 缺少自定义品牌图形 / 像素艺术 / 数据海报。
6. **缺动效语言** — workbench 输出动画是亮点, 但只有这一处。应补充: 滚动触发数字滚动、悬停 lift、章节切换 fade-up、Live ticker(在线判题滚动)。
7. **缺品牌故事视觉** — 没有 logo 演化、品牌色板、字体宣言, 只有文字。

---

## 三、前端开发 (Dev) 视角

### 现状

单文件 435 行, 导入只有 `vue / vue-router / pinia store / vue-i18n`, 无第三方动画库、无图表库。纯模板 + 少量交互, 技术债可控。

### 可复用资产

- `useI18n` + 现有 43 个 key → 新 section 复用同一 namespace `landing.*`
- `silver` / `electric` / `surface-sunken` CSS 变量已定义 → 主题一致
- `container max-w-6xl px-4` 栅格已用 → 直接复用
- workbench 的 reactive 状态机(`step / progress`)可抽成 `useLandingDemo` composable, 后续 demo 区复用
- `lucide-vue-next` 已存在 → 全部 icon 直接用

### 实现约束

1. **不要堆雪碧图** — 用 SVG / CSS 渐变 + emoji / 真实截图, 体积小。
2. **图片必须 lazy + webp + srcset** — `loading="lazy" decoding="async"`, 避免首屏 LCP 劣化。
3. **动画首选 CSS + Vue Transition** — 避免引入 GSAP / motion-one。`prefers-reduced-motion` 必须尊重。
4. **滚动触发数字** — 用 `IntersectionObserver` + `requestAnimationFrame`, 不引第三方。
5. **暗色段落** — 用 `dark:` 修饰符 + 现有 CSS 变量, 不另写主题。
6. **新增 section 建议拆子组件** — `LandingHero.vue` / `LandingFeatures.vue` / `LandingFAQ.vue` / `LandingFooter.vue`, 单文件不超过 200 行, `LandingView.vue` 退化为编排器。

---

## 四、扩充建议 —— 新增 6 个 section

按从上到下顺序加, 落地后总长度约 1100 行(拆 7 个子组件, 每个 < 200 行):

| # | Section 名 | 目的 | 关键元素 |
|---|---|---|---|
| 1 | **Social Proof Bar** | 即时建立信任 | `10K+ 注册用户 / 3.5K+ 题目 / 50+ 语言 / 99.9% 判题可用率` + Logo 墙(可后置) |
| 2 | **Feature Grid (3×2 或 2×3)** | 让用户秒懂"能干嘛" | 6 大功能卡: 在线 IDE / 多语言判题 / 比赛系统 / 题单收藏 / Markdown 题解 / 实时讨论 |
| 3 | **Use Cases (Tab 切换)** | 场景化定位 | 学员刷题 / 高校 OJ / 企业内训 / 竞赛训练 / 面试备战, 每个 tab 配图文 |
| 4 | **Live Ticker (可选)** | 营造活跃感 | 滚动条: `[14:32] user_8x2k AC 困难 DP · [14:33] 比赛 #1024 开赛 · …` 真实或模拟数据 |
| 5 | **Roadmap / Changelog 简版** | 透明度 + 长期信号 | 时间轴: 2025 Q1 比赛系统 → Q2 题单 → Q3 AI 辅助 → Q4 企业版, 每条带状态 chip |
| 6 | **FAQ Accordion (8 条)** | 拦截销售疑虑 | "是否免费 / 判题速度 / 数据隐私 / 学校能用吗 / 支持语言 / 离线模式 / API / 商业合作" |
| 7 | **Final CTA + Footer 增强** | 收口转化 | 加次级 CTA(看题库 / 读文档 / GitHub Star) + 三列 footer(产品 / 资源 / 社区) + 备案号 + 友链 |

### 文案 i18n namespace 命名建议

```
landing.social.*          # 数字带
landing.feature.{key}     # 功能卡
landing.usecase.{key}     # 场景
landing.timeline.{key}    # 路线图
landing.faq.{key}         # 问答
landing.footer.{key}      # 底部
```

---

## 五、结论

现版是 **Hero + 收口** 两页式架构, 适合"产品已起飞、靠品牌记忆"阶段; UltiCode 还在冷启动, 落地页必须升级为 **Hero → 信任 → 功能 → 场景 → 路线 → FAQ → CTA** 七段式叙事。

三个角度优先级: **PM 内容 > 美术节奏 > Dev 拆分**, 先补内容再打磨视觉再拆组件, 顺序反了会白做。

---

## 六、下一步可选动作

- [x] 完成实施与文件拆分：新增 `landing/LandingCapabilities.vue`、`LandingUseCases.vue`、`LandingTrust.vue`，保留 `LandingView.vue` 负责 Header、Hero、CTA 和页面编排。
- [x] Social Proof Bar 与 FAQ 已直接实现，并通过响应式及键盘交互测试。
- [x] 核实真实数据来源：当前没有可供落地页使用的公开统计接口，因此没有展示用户数、题目数、提交量或可用率；Social Proof 改为当前导航可直接验证的产品能力。
- [x] 中英文 `landing.social.*`、`landing.feature.*`、`landing.usecase.*`、`landing.timeline.*`、`landing.faq.*` 已同步落盘。

---

## 七、实施结果（2026-07-11）

最终叙事为：**Hero → 可核验能力 → 功能 → 场景 → 能力记录 → FAQ → CTA**。

- 删除了无来源数字、虚构 live ticker 和未来季度路线图，避免冷启动产品用假数据换取“热闹感”。
- Social Proof 展示题库、竞赛、社区三个真实入口；功能段用 `SOURCE → COMPILE → RUN → AC` 判题流水线作为视觉签名。
- 场景段提供日常刷题、高校教学、竞赛训练和面试准备四个可访问 tab；能力记录只列当前已经开放的功能。
- FAQ 明确区分首屏模拟器与真实判题，并如实回答免费范围、凭证安全、课程使用、语言、API 与离线模式。
- 动效保持克制：只保留现有工作台输出与必要状态过渡，继续尊重 `prefers-reduced-motion`。
- 未新增图片依赖或第三方动画库，因此不存在 LCP 图片、`srcset` 或新运行时依赖成本。
