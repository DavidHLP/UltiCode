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
