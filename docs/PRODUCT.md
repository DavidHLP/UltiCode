# 产品与领域

本文是 UltiCode 当前产品定位、用户角色、核心能力和领域边界的主题入口。实现、配置、契约和测试仍是行为真相；完整稳定术语表保留在根目录 [`CONTEXT.md`](../CONTEXT.md)。

## 产品定位

UltiCode 是一个可自托管的在线评测平台，提供题目、比赛、提交评测、用户互动和管理能力。仓库交付的是代码、配置、脚本和可复现的本地/容器验证路径；仓库本身不声称拥有生产环境、真实流量或外部凭据。

## 当前用户角色

- **普通用户**：注册/登录、浏览题目和比赛、提交代码、查看评测结果，并使用平台提供的社区或通知功能。
- **管理者**：通过 Management 端执行题目、比赛、用户、审核、提交管理和运营查询；权限受 Auth 身份与 Admin/Moderation 策略约束。
- **评测与运行操作者**：启动本地或受控环境，管理 Owner 数据、Judge/Search worker、迁移、备份和恢复；其操作必须遵循 [`OPERATIONS.md`](OPERATIONS.md) 与 Services runbook。
- **贡献者与自动化代理**：遵循 [`AGENTS.md`](../AGENTS.md)，先从 [`index.md`](index.md) 找到当前权威主题，再回到代码、配置和测试。

## 当前能力边界

### 用户与身份

Auth 负责账号、凭据、会话、OAuth/JWT/JWKS、权限和身份查询；浏览器 Cookie、CSRF、refresh rotation、WebSocket 身份和权限边界见 [`REFERENCE.md`](REFERENCE.md) 与 [`ARCHITECTURE.md`](ARCHITECTURE.md#安全架构与信任边界)。

### 题目、比赛与社区

App 负责用户侧题目、比赛、内容、社区、资料和搜索读取等业务边界。题目与比赛的具体字段、状态和管理入口以 App owner 实现、API contract 和共享类型为准；不要仅根据本页概括扩展 wire contract。

### 提交与评测

Submission 负责提交 intake、生命周期、generation/fence、verdict/result 和 rejudge 管理；Judge worker 消费受控队列并执行评测路径。提交状态的跨 Owner 读模型、队列、outbox/inbox 和失败语义见 [`ARCHITECTURE.md`](ARCHITECTURE.md#数据流契约与事务) 与 [`REFERENCE.md`](REFERENCE.md)。

### 通知与搜索

Notification 负责通知意图、Inbox、delivery ledger、重试和管理契约；Search worker 维护派生索引，App 在索引不可用时遵循已有的降级读取边界。Search 不是 Owner 业务数据的备份，也不是新的事实来源。

### 管理与运营

Admin 负责管理、审核、审计、设置、监控、备份编排和运营查询；具体权限、审批和恢复边界由 Auth、Admin 与 [`SERVICES_ISSUES.md`](../services/docs/SERVICES_ISSUES.md) 共同约束。

### 提交复盘与学习计划

Agent 是普通练习的复盘能力，不是独立聊天产品。主入口位于本人提交详情；题目页选中本人提交时复用同一面板，“我的提交”列表直接定位该次提交。旧 `/personal/agent` 仅承担历史线程链接迁移，不再要求用户手填提交 UUID。入口受 `VITE_AGENT_REVIEW_ENABLED=true` 显式启用控制，默认关闭；关闭或 Agent 不可用不改变题目、判题、源码查看与原题解流程。

这套整合沿用 [Notion 产品定义与交互契约](https://app.notion.com/p/3f374b09d6af81dca56fda4e4c1aaa35)、[DAV-54](https://linear.app/davidhlp/issue/DAV-54)、[DAV-56](https://linear.app/davidhlp/issue/DAV-56) 的“本人普通练习提交详情”主入口决定，复用 [GitHub #287](https://github.com/DavidHLP/UltiCode/pull/287) 的工作流交互和 [#288](https://github.com/DavidHLP/UltiCode/pull/288) 的受控本地分析边界。外部记录中的设计完成、历史测试及旧执行队列退役不代表当前版本已完成真实用户试点或正式模型/Java 验收；运行事实仍以当前代码、授权和当次验证为准。

能力按用户任务组织，而不是按 Agent 内部技术划分页面：

| 用户任务 | 业务内交互 | 保留的边界 |
| --- | --- | --- |
| 理解某次提交 | 从结果详情展开复盘，提交上下文自动绑定，明确开始后才请求分析 | 只接收本人、明确普通练习、已知终态；未知资格、比赛和判题中不开放 |
| 核对分析依据 | 同一面板展示事实、假设、来源及分析结果 | 引用来自服务端核验；资料不足不解释为已经找到 bug；不把合成语料当作真实提交源码 |
| 选择学习重点 | 使用问题建议或自行描述目标，查看可执行的复盘建议 | 不增加无限对话、多题画像、自动做题或判题控制 |
| 整理学习计划 | 查看、编辑并预览当前草稿，再分别确认和保存 | 确认绑定当前版本与摘要；编辑后需重新确认；能力未开放时不可保存 |
| 找回与核对结果 | URL 携带线程标识恢复；保存后按返回的 planId 读回 Java 记录 | 线程归属与提交必须匹配；unknown 查询原操作，不创建替代幂等键 |
| 暂停与返回练习 | 收起面板、显式取消或切换提交 | 收起不等于取消；取消不承诺回滚 Java；旧运行事件不能覆盖新上下文 |

数据范围在开始前说明：常规模式使用经授权的提交元数据与资料；已有本地 `owner_source` 手动模式才允许使用当前线程绑定的本人源码，并需要用户在界面明确确认源码外发范围。前端不上传编辑器内容、测试数据或用户凭据，也不自行开放新的源码能力。源码仅按现有后端限制在请求期使用，不写入 Agent 工作流状态。

可用性来自认证的 `/agent/capabilities`，但每次操作仍由服务端重新检查所有权、资格、预算与验收门禁。前端开关不是授权；保存相关能力不能从 `awaiting_confirmation` 状态推断。复盘面板复用现有主题、双语文案和组件，桌面/窄屏使用同一页内布局；打开移动焦点至标题，关闭回到入口，保留原返回路径。

首版不新增学习计划列表或工作流列表 API、全局悬浮聊天、管理后台、用户画像或统计埋点。保存不等于学习有效，点击与生成不等于需求得到验证；真实用户研究、质量评估、留出集及正式 U02/U03/U04 验收分别记录，不由 UI 或离线测试代替。

## 领域对象与术语

稳定术语以 [`CONTEXT.md`](../CONTEXT.md) 为准；当前主题中常见的边界对象包括：

- **Account / User / principal**：Auth 的身份、账号状态和认证主体。
- **Problem / Contest**：App 的题目与比赛聚合及其管理数据。
- **Submission / generation / attempt**：Submission 的提交生命周期和并发/重试隔离单位。
- **Verdict / result / fence**：评测结果及防止旧结果覆盖新状态的边界。
- **Owner / Worker**：拥有业务数据与写入权的服务，或处理派生/队列工作的独立 worker。
- **Contract / command / event / receipt**：跨 Owner 的接口、命令、事件和幂等回执；契约细节见 [`REFERENCE.md`](REFERENCE.md)。

本页只说明概念归属，不复制每个 DTO、数据库列或状态机；需要精确字段时应读取实现和 contract module。

## 拓扑与明确边界

当前默认拓扑是 distributed：五个 Data Owner（Auth、Admin、App、Submission、Notification）与两个 Worker（Judge、Search）各自保留边界。Core profile 是受限的可选验证 testbed，不是默认产品拓扑；其有效性还受本地输入和现有 issue gate 约束。

平台基础设施（MySQL、Redis、Nacos、MeiliSearch、SMTP、对象存储等）是运行依赖，不等于新的业务 Owner。共享基础设施的故障域、权限和恢复证据见 [`OPERATIONS.md`](OPERATIONS.md) 及 Services runbook。

仓库不承诺生产 active-active HA、透明故障转移、生产 RPO/RTO 或外部部署结果。Kubernetes、Kafka、Service Mesh 等未纳入当前产品能力；如需评估，先以源码、配置、门禁和 Services issue registry 为准。

## 继续阅读

- 产品与稳定术语：[`CONTEXT.md`](../CONTEXT.md)
- 当前架构与安全：[`ARCHITECTURE.md`](ARCHITECTURE.md)
- API、认证与契约：[`REFERENCE.md`](REFERENCE.md)
- 当前实现与边界：[`ARCHITECTURE.md`](ARCHITECTURE.md)、[`DEVELOPMENT.md`](DEVELOPMENT.md)、[`OPERATIONS.md`](OPERATIONS.md)。
