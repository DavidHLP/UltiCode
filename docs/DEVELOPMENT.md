# 开发、测试与规则

本页是开发相关当前知识的唯一主题入口，合并本地启动、验证、配置、规则与排障说明。脚本、构建文件、服务配置和前端包源码仍是精确行为来源。

## 本地开发

### 前置条件

- Docker + Compose v2：MySQL、Redis、Nacos 和可选 MeiliSearch。
- mise 管理的 Zulu Java 17、Node.js `>=22.12.0`、pnpm 10+、PM2、Docker Compose v2，以及 `curl`、`timeout`、`openssl`。
- 后端使用仓库内的 `services/mvnw`；不要用裸 Maven/Java 绕过启动入口。
- 从仓库根目录执行脚本。`.env` 由 `scripts/dev/init-env.sh` 生成，不能提交。


### Dev Container / Codespaces

仓库提供 `.devcontainer/devcontainer.json`，固定 Java 17、Node 22、pnpm、
mise、PM2、Maven wrapper 和 Docker-in-Docker。创建容器时只安装依赖和
Maven 离线缓存，不启动全栈；容器启动后的默认检查仅执行
`./scripts/dev/test.sh unit`。运行真实 journey 必须显式选择 scope。

mise Feature 使用 `ghcr.io/devcontainers-extra/features/mise:1`，配置与锁文件
保持一致；旧的 `devcontainers-contrib` 地址会导致 OCI 拉取失败。已有本地
服务运行时，修正配置不需要重建容器或重启服务。Zed 日志中的基础镜像
`docker inspect` 未命中表示本地尚未缓存镜像，不等同于拉取失败；
`No initialize command found` 表示未配置可选的初始化命令，无需添加空命令。

统一入口是根目录的薄脚本：

```bash
./ulticode dev
./ulticode host --config ulticode.yml
./ulticode doctor --json
```

`host` 配置只接受 `scope` 和 `observability` 两个无密钥键；启动和
生命周期逻辑仍由 `scripts/dev/*` 与 `devstack-manifest.sh` 所有。

### 推荐启动

```bash
./scripts/dev/init-env.sh
./scripts/dev/up.sh --mode dev-lite
```

`dev-lite` 是兼容默认：按 `auth → admin → app → notification → submission` 应用 Owner migrations，启动六个后端（含 Judge）并使用数据库 Search；不启动 Search worker，默认也不启动两个前端。`dev-lite` 与 `dev-full` 都要求 `APP_SUBMISSION_ROUTING_MODE=remote` 和 `SUBMISSION_CUTOVER_COMPLETE=true`；marker 未完成时 `up.sh` 会 fail closed。

按开发场景选择最小服务集合（`up.sh`/`stop.sh`/`doctor.sh` 共用同一 resolver，见 `scripts/dev/devstack-manifest.sh`）：

```bash
./scripts/dev/up.sh --scope app-journey      # 普通用户旅程：auth/app/notification/submission/judge + console
./scripts/dev/up.sh --scope admin            # 管理：auth/admin/app/notification/submission + management
./scripts/dev/up.sh --scope submission-judge # judge 路径：app/submission/judge，无 Search
./scripts/dev/up.sh --scope search           # indexed Search：auth/app/search + console + meili
./scripts/dev/up.sh --scope core             # Core parent 9108 + independent Judge; Auth/Admin contexts only when explicitly enabled
./scripts/dev/up.sh --scope full-stack       # 显式全量进程集
./scripts/dev/up.sh --scope full-stack --observability  # 显式选择 observability overlay
```

兼容命令（等价于对应 scope）保留可用：

```bash
./scripts/dev/up.sh --mode dev-lite
./scripts/dev/up.sh --mode dev-full
```

Search/Meili、Judge、observability 与前端只在被选中 scope 需要时才启动；`dev-lite` 默认不创建 Meili 容器。生命周期操作消费同一集合：`./scripts/dev/up.sh status|logs|health --scope <name>`，`./scripts/dev/stop.sh --scope <name>`（`--all` 停止全部）。`up.sh` 对已退役的 `legacy-rollback` 和未知 mode/scope fail closed。生产回滚使用部署方保留并校验的上一份完整 release descriptor，不能通过当前二进制恢复旧实现（见[部署、发布与回滚](OPERATIONS.md#部署发布与回滚)）。`up.sh` 消费 `scripts/dev/devstack-manifest.sh` 的 route、flag、worker、readiness 和 failure policy，不要直接用 Maven 或 PM2 启动 runtime。
### Agent 本地运行

`services/agent/` 是独立 Python 服务，不加入 Maven reactor，也不随默认开发栈启动。前端开发代理将 `/api/agent` 转发到 `127.0.0.1:8000`。在 `services/agent/` 执行：

```bash
uv sync --locked
ULTICODE_APP_BASE=http://127.0.0.1:9103 \
ULTICODE_AUTH_BASE=http://127.0.0.1:9101 \
ULTICODE_AGENT_ORIGIN=http://localhost:9002 \
uv run uvicorn --factory agent_service.runtime:create_app \
  --app-dir src --host 127.0.0.1 --port 8000
```

Origin 必须与浏览器地址完全一致。默认不调用外部模型，不开放确认、保存和恢复；普通线程读取仍使用现有登录会话。Agent 不签发登录 Cookie，也不从请求正文接受用户身份。

产品运行时不再读取历史 U02/U03/U04 验收产物、候选 commit 或源码 SHA。旧的 gate/head/base 环境配置应移除；不能把历史验收文件当作运行授权。以下开关分别控制产品能力：

- `ULTICODE_AGENT_LIVE_ANALYSIS=1`：显式启用真实模型调用；模型凭据必须通过安全环境或 secret store 提供。
- `ULTICODE_AGENT_BUDGET_POLICY`：可选的既有授权预算策略。配置后，运行时读取其已准备、已激活的真实周期和共享 guard，不创建或重置账本，不增加额度。未配置时，仅当 `ULTICODE_AGENT_ORIGIN` 是 loopback 地址，live 模式才使用无共享预算账本的本地开发调用；这类调用仍受单次分析的模型 token 与调用次数上限约束，没有累计费用上限。
- `ULTICODE_AGENT_PERSISTENCE_ENABLED=1`：显式开放确认、保存和恢复。它不授权模型调用，也不绕过 owner、CSRF、确认、版本和幂等检查。
- `ULTICODE_AGENT_MANUAL_ANALYSIS_ONLY=1`：保留本地只读源码分析模式，须与 live 开关一起使用；该模式使用固定预算且不开放持久化。

关闭 live 时也应移除预算策略选择并关闭 manual 标志；手动模式不能与持久化开关同时启用。旧 `ULTICODE_AGENT_U02_GATE_PATH`、`ULTICODE_AGENT_EXPECTED_HEAD`、`ULTICODE_AGENT_EXPECTED_BASE` 不再受支持，配置残留会明确报错。

常规本地开发可不设置预算策略 ID，以 loopback `ULTICODE_AGENT_ORIGIN`、`ULTICODE_AGENT_LIVE_ANALYSIS=1`、`DEEPSEEK_API_KEY` 和 `ULTICODE_AGENT_PERSISTENCE_ENABLED=1` 开放分析及保存。该模式会产生未经过共享账本累计的真实模型费用；确认与保存仍由用户显式操作。非 loopback 部署继续要求已有的有效预算策略。启动不调用模型；只有用户明确请求分析才会调用外部模型。

手动源码模式继续使用现有固定预算和 `DEEPSEEK_MODEL=deepseek-flash`。首次准备入口是 `uv run python prepare_manual_live_test_budget.py`；已有账本不应删除或重建。文件系统设备编号变化时，已有 `--rebind-device` 恢复路径校验原始绑定、inode、文件系统身份、账本与已结算用量，不增额、不修改原账本。缺失、替换或未结算状态仍拒绝恢复。

### Agent 工作流与业务入口

Console 的入口是共享提交详情中的可收起复盘面板，用于 `/submissions/:id` 和题目页选中的提交。“我的提交”直接进入对应结果；旧 `/personal/agent?threadId=...` 只负责验证线程归属并跳转，无线程时返回个人提交列表。

在前端构建或开发服务器启动时设置 `VITE_AGENT_REVIEW_ENABLED=true` 才显示入口，其他值均关闭。前端开关不启动 Python、不授权费用、不开放 Java 保存。仅本人、已知终态、明确 `contestSubmission: false` 的普通练习提交可用；旧 Java provider 缺少字段时不开放。Agent 服务再次核对来源资格。

打开面板后，认证的 `GET /agent/capabilities` 返回分析、持久化和来源模式。查询失败不影响原提交查看；打开、收起和恢复线程均不自动分析。`metadata` 模式使用提交元数据与授权参考资料；`owner_source` 模式要求用户明确同意源码外发，只读取当前线程绑定的本人源码，限制为 12,000 字符，不持久化源码正文或引用摘录。

`useAgentWorkflow` 管理草稿、事件和恢复。SQLite 是工作流状态与草稿的权威，LangGraph checkpoint 仅用于控制流。编辑使旧确认失效；确认绑定 owner、版本、内容摘要和有效期，确认本身不写 Java。保存使用原幂等键，未知结果先核对原操作，不自动换 key 或重发。保存后按 `receipt.planId` 读取 Java 记录；读回失败不等于写入失败。已保存内容的读取不依赖当前模型是否可用。

收起保留未保存编辑；切换提交、账号或卸载会隔离旧异步响应。取消不能撤销已发送的 Java 写入。渲染继续使用共享 Markdown 净化器、主题和双语组件。模型回答必须符合现有来源、工具轨迹和引用检查；这些检查不保证通用语义正确，也不能替代用户判断。

只读模型工具循环仍由 `agent_service.graph` 实现，`agent_loop.run_tool_loop(...)` 作为兼容入口；不允许模型选择身份、工作流节点或任意写工具。可选 MCP 只读工具桥通过 `ULTICODE_AGENT_MCP_TOOLS=1` 开启，不改变访问范围，详见 [Agent guide](../services/agent/README.md#optional-mcp-read-only-bridge)。

### Agent 验证

保留产品行为和信任边界测试，不再维护历史候选冻结、验收 artifact/SHA、封存留出集或独立 `e2e_*.py` 冒烟驱动。现有服务端用例覆盖身份、CSRF、来源资格、模型协议、引用、预算、确认、幂等、取消及未知结果恢复；前端用例覆盖复盘交互与净化。

```bash
# services/agent/
uv run pytest -q

# 仓库根目录
pnpm --dir apps/console test
pnpm --dir apps/console type-check
```

离线 scripted-model/MockTransport 和合成浏览器响应只证明相应测试路径，不证明真实模型质量、真实 Java 持久化或远端部署。真实用户体验验证须单独明确目标环境、数据范围和模型费用授权，直接通过产品工作流完成，不因清理测试而自动授权。

### 访问入口

| 表面 | 地址/端口 |
| --- | --- |
| Core parent | `9108`，`/api/v1/core/health/ready`，不直接持有业务表 |
| Landing | <http://localhost:9002/landing/>（独立 Vite：9004） |
| Console | <http://localhost:9002> |
| Management | <http://localhost:9003> |
| Auth/Admin/App/Notification | `9101/9102/9103/9105` |
| Submission owner | internal HTTP `9106` / Dubbo `20886` |
| Judge worker | Dubbo `20884`，无业务 HTTP |
| Search worker | `--mode dev-full` 或 `--mode dev-full --only search`，无业务 HTTP |
| Nacos | <http://localhost:28848/nacos> |

Base Compose 不发布基础设施或 backend 端口；开发覆盖层只绑定 loopback。生产启动、Judge remote daemon 和 TLS 证书见 [`OPERATIONS.md`](OPERATIONS.md)。

### 前端与共享包

```bash
pnpm --dir apps/console install
pnpm --dir apps/management install
pnpm --dir apps/landing install

pnpm --dir apps/console dev
pnpm --dir apps/management dev
pnpm --dir apps/landing dev
pnpm --dir packages/auth-core type-check
pnpm --dir packages/auth-core test:coverage
```

Landing 生产构建使用 `/landing/` 为基础路径，平台链接默认使用同源 Console。若 Landing 与 OJ 平台部署在不同主机，容器构建需设置 `VITE_PLATFORM_URL` Docker build arg 为 HTTP(S) origin；镜像发布工作流读取同名仓库变量。

前端只有一个根 `pnpm-lock.yaml`（成员 lock 已删除），所以 `.github/dependabot.yml` 的 npm 只有一个
`directory: "/"` 的 block。pnpm workspace 只能从根更新：成员目录下 Dependabot 从父目录取回的根 lock 会被
dependabot-core 当作 support file 丢弃，PR 只改 manifest，必然过不了 CI 的五处
`pnpm install --frozen-lockfile`，update job 还会按成员 manifest 报 `misconfigured_tooling`。
这个根 block 同时覆盖 `packages/*` 与根 manifest。
根 `package.json`、`pnpm-lock.yaml` 或 `pnpm-workspace.yaml` 变化会触发 Console、Management、Landing
以及 Docker CI 门禁，覆盖独立 Landing 与其镜像安装验证。

### Web v2 前端升级基线

`web-v2-upgrade` 是 Web v2 的升级分支，起点固定为
`638483a96a0b1dbc102aba14065d98bcea76b2cd`（`feat: add persistent Agent conversation workspace`）。
该提交是改动前的对照与回退来源，不随分支后续推进改变；基线定义不表示 Web v2 已发布，
也不将前端 package version 改为 `2.0.0`。

升级范围包括 `apps/console/`、`apps/management/`、独立 `apps/landing/` 及其消费的 `packages/`。Landing 页面、样式、双语文案与素材只由 Landing 应用持有；流程区标题使用浏览器平衡换行，避免窄屏中文末行仅剩单字，不缩小标题字号；生产由现有 Console Nginx 在 `/landing/` 提供；应用入口和路由保持独立，主题 bootstrap 与通用 UI 复用共享包。
沿用 Vue 3、TypeScript、Vite、Pinia、Vue Router、Tailwind CSS；通用组件统一使用 shadcn-vue；
依赖的精确版本由根 `pnpm-lock.yaml` 固定，不另建 v2 应用、第二份 lock 或平行共享层。
安装使用根目录 `pnpm install --frozen-lockfile`，Node.js 满足 `>=22.12.0`；
与当前前端 CI 对齐的环境为 Node 22.x、pnpm 11。

后续变更必须保留以下基线契约：

- Console 与 Management 保持各自路由、布局和权限边界，跨端行为归属现有 focused package；
  不把管理员专用 UI、隐藏用例或特权数据暴露到 Console。
- 保留现有 `Result` envelope、字段映射及请求/响应类型；不以 UI 升级隐式改变后端 API、
  Owner 边界、数据库或部署拓扑。确需契约变更时另行明确范围并同步双方消费者。
- 身份初始化先于路由安装，access/refresh token 仍使用 HttpOnly Cookie，权限仍由服务端校验；
  不新增 query/STOMP token 或浏览器明文凭据存储。
- 主题状态与 `data-theme` 写入归属 `packages/theme`；Markdown/KaTeX 渲染继续经过
  `packages/markdown-utils` 的消毒链路，不绕过既有安全边界。
- 中文/英文文案同步，保留键盘操作、焦点管理和窄屏可用性。UI 行为变化需要对应回归测试，
  关键用户旅程需要单独的浏览器验证，不能用构建成功代替。
- 保留 Agent 工作区、提交复盘与学习计划的既有资格、授权及降级行为；
  `VITE_AGENT_REVIEW_ENABLED` 默认关闭，前端开关不代表分析或保存授权。

复现前端基线时运行只读门禁，避免 `lint --fix` 或全树格式化改变对照源码：

```bash
(
  set -e
  pnpm install --frozen-lockfile
  for app in console management; do
    pnpm --dir "apps/$app" lint:check
    pnpm --dir "apps/$app" type-check
    pnpm --dir "apps/$app" test:coverage
    pnpm --dir "apps/$app" build-only
  done
  pnpm --dir apps/management validate:i18n-keys
  pnpm --dir packages/auth-core type-check
  pnpm --dir packages/auth-core test:coverage
  pnpm --dir packages/theme test
)
```

`type-check` 与 `build-only` 分开执行，等价覆盖 app 的 `build` 两个子步骤，避免重复类型检查。
app 测试排除的共享认证/主题测试必须独立运行；其他共享包受改动影响时运行该包声明的门禁。
每项验证单独记录退出码，失败不得由后续成功覆盖。上述检查不证明浏览器业务旅程、真实后端、
模型调用、远端部署或发布已验收；执行证据和当前状态放在对应 Linear / Notion 记录中，
本页只维护固定起点、兼容边界与复现方法。当前执行记录见
[Linear 前端升级基线](https://linear.app/davidhlp/document/ulticode-web-v2前端升级基线-74fbf53b2431) 与
[Notion 项目上下文](https://app.notion.com/p/3f374b09d6af81f2a890ecc638de067a)。

### 共享 shadcn-vue 与外观设置

`packages/design-system` 是三端通用 UI 的唯一所有者。官方组件位于 `src/components/ui/`，消费者使用 `@ulticode/design-system/ui/<group>`；只有确需单文件的适配才使用已声明的 `ui-files` 子路径。应用内不维护另一份 `components/ui` 或 CLI 配置。业务布局在各应用内组合共享组件，侧栏布局沿用官方 Sidebar Block 的 Provider、Inset、Trigger 与 Breadcrumb 组合，不引入官方示例中的虚构业务数据。

共享 Dialog 默认居中并限制在视口高度内，长表单可滚动；需要固定头尾的弹窗自行使用纵向 flex、`min-h-0` 内容区和内部滚动。CommandDialog 沿用相同的定位和高度边界。AlertDialog 在保留尺寸变体的同时保留视口边距，并允许长确认内容纵向滚动。管理端顶栏在窄屏自然换行；Markdown 编辑器按自身容器宽度切换分栏与上下布局，工具栏操作和双向滚动同步使用底层 textarea 元素。

题目详情的页签与操作参与正常文档流，移动端保留编辑入口。多语言代码查看器在语言列表到达或刷新时保留有效选择，否则选择首项；单语言展示不受影响。题目描述表单的高级和发布复选框通过共享 Checkbox 的 `model-value` / `update:model-value` 同步实际表单值，点击控件与点击外层卡片分别只处理一次。

Console 题解详情将作者信息与操作栏分行，避免在窄面板内互相挤压；题解列表的写作提示保留完整文字，操作随可用宽度换行。题解目录复用共享 Popover 的 Portal、碰撞避让和高度边界；选择章节后使用不滚动页面的方式返回入口焦点，外部交互仍保留默认焦点行为。

共享评论入口、元信息、操作栏和表单按钮允许换行。嵌套评论按自身容器宽度保留有界缩进，不递归占用整列头像宽度；作者、正文和回复操作保持可达，代码块继续使用自身的有界横向滚动。题解卡片已有正文内边距，不重复叠加评论区的横向内边距。

管理端 Markdown 的行内代码使用正文小字号 token，保留长内容换行；代码块保留原始行与空白，长行在代码块内横向滚动。题目描述、编辑预览和题解正文复用这组样式。 题目起始代码编辑字段使用等宽字体 token，不随普通文本输入框继承比例字体；保留原有换行与提交绑定。 管理端账户页的内联密码表单关闭后，将焦点恢复到重新挂载的入口按钮，并保留页面滚动位置。

系统设置的常规、邮件和功能开关面板使用共享 Switch 的 model 接口显示配置并发出字段 patch；切换只修改本地草稿，保存仍由设置工作区统一执行。操作按钮组允许随可用宽度换行。

竞赛创建向导使用固定头尾与独立滚动内容区，步骤在窄屏分为两列；无内边距的根容器由本地页脚提供间距。计分规则表单通过 Vee Validate 生成的提交处理器校验并发出保存事件。计分筛选、默认规则、比赛发布草稿、用例范围和题单可见性使用共享 Switch 的 model 接口；题单可见性保留原有自动保存流程。

向导确认页允许基本信息卡片收缩，长标题与标识符换行展示。共享 Command 在查询或注册的项目、分组变化后重新计算过滤结果；异步结果到达、项目移除和清空查询沿用相同的结果及空态显示规则。

首页场景卡片中的入口按卡片宽度换行并由内容决定高度，避免平板三列时按钮被卡片裁切。首页顶栏在空间不足时自然换行，保留全部导航与外观入口；末尾标题按原有两句文案分行，长入口按钮可换行。共享外观设置关闭时，若入口元素仍在文档中，则使用不滚动页面的方式返回焦点；入口已移除时保留 Dialog 的默认行为。

论坛列表的搜索与排序控件在可用宽度不足时换行；帖子元信息保留头像尺寸，分类、时间与作者允许分行，长作者名可断行。筛选结果为空时显示现有的本地化空态提示。

论坛分类导航读取现有社区接口，以社区标识作为稳定键并直接显示社区名称；加载失败提供重试，和有效的空列表区分。直接进入社区时初始化排序选项，当前排序文字随界面语言更新。Console 侧栏收起时使用图标入口，隐藏分组标题并保留展开状态，已关闭分组的链接仍可访问。 论坛搜索无匹配与没有帖子分开显示，长关键词允许换行，排序控件具有随语言更新的可访问名称。探索页使用现有导航翻译显示当前排序，保留 `explore` 请求值和接口返回的可选排序。

管理端题解列表的已标记与已发布数量来自当前分页，标签明确标注“本页”，不与接口返回的筛选总数混为全量统计。 举报列表的搜索框明确按完整实体 ID 精确筛选，复用现有 entityId 查询参数；清空时省略该参数，保留服务端分页和状态、类别、类型组合筛选，不伪装为全文搜索。 申诉列表同样按完整队列 ID 复用 queueId 精确筛选，保留状态与服务端分页，不对申诉原因做当前页伪全文搜索。 审核队列将筛选总数与全量状态聚合分别标注；举报和申诉列表由当前分页计算的待处理数量明确标注“本页”，不冒充全量聚合。

共享数据表的默认空态与自定义空态均在表格横向滚动区外渲染，筛选清空宽表时不保留挤压或裁切提示的表头列宽。共享全局/本地排行在有效空结果时显示本地化空态，不绘制空领奖台或加载更多入口；请求失败时隐藏旧排行并显示错误与重试，两个实际页面调用方沿用排行 store 的错误状态。 排行加载更多沿用现有 page/limit/total 契约，按当前国家筛选追加下一页并展示全部已加载条目；只在仍有后续页时显示入口。分页请求期间禁用重复加载，失败保留已有排行并在原入口重试，切换范围或刷新首页时取消旧分页，避免混入其他范围的条目。个人资料头像上传说明保留可换行的文本基准宽度，空间不足时将操作按钮换到下一行，不把格式与大小提示挤成窄列。个人资料统计主列允许在网格中收缩，年度热力图保留自身的横向滚动。个人主页热力图的零活动格与图例沿用共享仪表盘的中性明度差，亮色卡片上不使用接近白色的卡片/弱背景混合。用户详情的图表卡片同样允许收缩，长用户名换行；共享仪表盘热力图的星期、活动提示、图例和技能空态跟随界面语言，零活动格在亮色背景中保留可见明度差。技能雷达图在加载结束、容器显示后初始化，覆盖技能数据先于加载状态到达的情况。 个人主页 ECharts 雷达提示直接读取当前技能名称与计数，不依赖回调中不存在的指标定义；提示行使用 DOM textContent，不将技能名称拼入 HTML。个人主页三张 ECharts 图均将提示约束在各自图表内，避免窄卡片的提示被卡片边界裁切。 提交历史与学习进度读取失败显示各自的错误和重试入口，不显示成功空态；重试成功恢复真实图表，只有有效空响应显示开始提交或解题提示。 学习进度的计数轴使用整数刻度，两侧轴标题向图内对齐，绘图区顶部为轴标题与默认名称间距预留独立空间，避免标题被画布上缘截断；保留原有题数、小时单位与字号，不让长英文标题越出窄图边界。 仪表盘与用户详情共享的 SVG 技能雷达持续观察容器尺寸，侧栏或视口变化后重新计算几何，卸载时停止监听，不沿用首次挂载的宽度。 个人主页提交历史图为窄卡片的两行图例预留独立底部空间，提交数与语言分布计数轴使用整数刻度，避免小样本小数刻度与图例重叠。 个人主页的 ECharts 技能、提交历史和学习进度图观察实际图表容器，不仅依赖 window resize；侧栏与网格完成重排后按新尺寸重绘，避免画布沿用旧宽度裁切标签和图例。 两类用户资料页的提交次数使用现有具名 count 参数驱动英文单复数，保留中文计数格式。ID 资料入口先解析基础用户身份，再读取同一用户名的现有完整公开资料接口，提交次数、排名与通过率沿用该接口的统计；校验返回 ID 一致，失败或身份变化显示错误，不用基础用户响应缺省的零值冒充完整统计，也不以已解决题数除以提交数重新计算通过率。ID 资料读取失败时显示本地化错误说明与共享重试按钮；重试重新读取完整资料、解题统计与技能，不保留失败前的统计或显示成功空态。个人题解读取失败同样显示本地化错误和重试，不把请求失败当作暂无分享的题解；只有有效空响应显示开始写代码提示。个人提交列表读取失败保留搜索入口，以本地化错误和重试替代成功空表，不显示尚未提交代码或搜索无匹配提示；重试沿用原有提交读取接口与搜索条件。个人论坛帖子读取失败显示持久的本领域错误与重试，不仅弹出题单错误后显示暂无帖子；有效空响应才显示创建首帖提示。非空帖子操作菜单显式导入共享下拉菜单分隔组件，不依赖全局注册或未知自定义标签。个人题单概览读取失败同样显示持久错误与重试，失败时不显示本人/收藏计数、分类与精选空态或旧卡片；重试成功后恢复原标签、搜索条件与实际概览数据。收藏合集的文件夹列表读取失败显示本领域持久错误与强制重读入口，不把失败当作无合集或未选择合集；列表成功后恢复原有分栏与选择流程，不将写入操作错误误当作列表读取失败。共享评论入口取消空表单后恢复焦点到原入口，并保留滚动位置；论坛与题解沿用同一取消行为。题库每日挑战的窄屏今日提示、难度、周名、日数单位及日历展开按钮跟随当前语言，英文日数使用单复数；业务题名不作界面翻译。个人题单的精选卡片按名称和描述应用搜索，无匹配与空推荐分开显示；长关键词和卡片标题允许换行，保存按钮按卡片宽度换行。收藏合集的文件夹仅在桌面分栏时悬停，单列滚动不遮挡下方内容。

审核队列和审核统计面板按已有统计请求的加载与错误状态显示占位，不将缺失或缓存旧统计显示为当前零值；失败保留错误与刷新入口，统计分布也不伪装成成功空态。筛选队列总计与全量统计独立降级。

举报列表的“在队列中查看”保留 `queueId` 查询参数；审核页通过已有的单条只读接口打开目标详情，不依赖当前列表页包含目标。重复查询值不选择任意目标，切换目标或离开页面时取消旧请求，失败不打开成功详情。

评分规则列表在加载或请求失败时用占位符显示统计，失败时以可重试错误替代成功空表和旧列表操作；只有有效空结果才显示零统计与创建提示。评分规则表单每次打开时从当前规则或新建默认值重置表单，避免弹层卸载字段后再次打开出现空数值。

管理端提交详情及比赛创建向导、共享题目选择器、题目文件导入与通知创建/编辑弹窗保留共享 Dialog 的视口边距和动态高度边界，整张弹窗随内容滚动，不以固定头尾把短屏正文压成狭小滚动区，也不使用负边距抵消弹窗内边距。管理端表格仅为默认成功空态保留固定展示高度，自定义错误插槽按内容自然展开，避免错误说明被空白推到屏外。提交、通知、比赛、题目与题解列表读取失败时复用表格错误空插槽，隐藏上次成功行、分页及批量操作并保留筛选，不把请求失败显示为成功空结果；提交页独立成功统计不受列表失败影响。通知、比赛、题目与题解列表在加载或失败时同样显示不可用统计，不以默认零值代替未知状态。邮件管理沿用日志、模板和统计的整体读取契约；加载或失败时清除旧内容，统计显示不可用占位，失败显示持久错误和重试，不显示成功空态或旧模板操作；有效零与空响应保持原样，刷新和卸载后的过期响应不回写。管理端提交统计在未加载或读取失败时显示不可用占位和独立重试，不用零值或上次缓存代替失败；有效零统计保留为零，重试及重置不接受过期响应。共享题目选择器分别呈现加载、请求失败和有效空结果；失败时隐藏旧候选并允许按当前关键词重试，不将异步状态交给仅在非空搜索时渲染的 CommandEmpty。管理端题目、比赛与通知列表的页面标题与新增入口按可用宽度换行并保留间距，避免英文窄屏按钮越出卡片。管理端共享趋势图向 Unovis 传入数值时间戳和逐系列数值访问器数组，颜色访问器按系列返回单个 token；点标记使用受支持的 size 属性。测试用例导入提示中的 JSON 和行语法示例使用 vue-i18n 字面量插值保留花括号，避免将示例误编译为参数并中断弹窗渲染。隐藏判题用例编辑器的标题、样例计数和导入/导出/新增操作按容器宽度换行，窄屏不裁掉工具栏入口；列表与详情在窄屏纵向排列，中宽以上恢复分栏。管理仪表盘时间线的目标类型在明暗模式均使用前景文字 token，不将边框色用作暗色文字。管理端分析页的指标名称与排行标题允许换行，不省略必要内容。排行的百分比选项按接口的 0–100 单位使用当前语言格式化，最多保留一位小数；普通计数保持原有紧凑数字格式。周期选择框的已选文案直接绑定当前语言，切换语言无需重新打开菜单。 共享审计查看器的操作筛选同样直接绑定当前选项与语言，保留原有筛选值和请求契约。

管理用户列表的状态环与圆形头像使用相同圆角，保留状态颜色及独立文字徽标。 用户列表请求失败时保留筛选入口，以错误提示替代表格旧行和分页，统计显示占位符，不把旧页数据标为新页；有效结果恢复正常列表。

账户设置与管理端用户抽屉的隐藏头像选择器使用原生文件输入，保持屏幕阅读器名称和现有上传限制，按钮直接调用原生文件选择器。通知偏好通过 Label 与 Switch 的标识关联提供可访问名称，沿用原有自动保存与安全通知禁用规则。

DataTableToolbar 仅在提供 filters 插槽时显示筛选入口。个人提交记录区分搜索无结果与尚无提交，详情页作者和时间允许换行，并复用统一日期格式。个人题单标签在窄屏分为两列且由内容决定高度，空分类显示明确提示，新建题单公开选项沿用 Switch 的 model 接口。收藏夹颜色保持现有领域值，使用已安装的 Tailwind 色板渲染选项并暴露选择状态。

提交性能图表为头像与位置标题预留绘图区顶部空间；头像成功加载后由同一标记显示标题，失败时保留柱顶文字。通知列表操作栏与通知标题允许换行，手机保留操作菜单入口，偏好抽屉正文使用带内边距的独立滚动区。通知分页加载沿用至少一页的初始与重置约定。成就分类在窄屏分列显示文字，缺少积分时使用占位符。个人仪表盘的热力图与技能图网格项允许收缩，年度热力图保留内部横向滚动。

比赛浏览卡片的标题与开始时间允许换行，报名数与参赛数分别标注，计分状态使用明确的文字。卡片信息区随同一行高度调整，使底部入口保持对齐；状态、计时器与详情导航沿用原有数据和行为。窄屏分类标签采用图标与文字纵排并允许标签栏自然增高，保留完整文字和数量。

管理端比赛详情的四个页签在窄屏分为两列，统计卡按卡片容器宽度切换单列与双列，保留统计图标和完整名称。题目计数为正但缺少明细时显示明细不可用，不冒充尚未添加题目；真实零计数保留空态。

比赛详情统计值允许换行，规则折叠使用可聚焦的原生按钮。状态卡按自身容器宽度切换分栏，避免侧栏展开时按窗口断点挤压详情和操作按钮。题目通过率沿用问题接口的 0–100 百分数单位，仅保留一位小数，不再次乘以 100；题目和排名表格在自身容器内横向滚动，保留所有列。 题单页头按内容容器宽度换行，分类按钮保留自然宽度；进度图及图注按内容撑开卡片，避免窄屏换行后被固定高度裁切。 题单传入的完整题目数组在本地应用搜索、难度、状态、标签和会员筛选，题库分页仍由服务端筛选；题单空结果的随机选题不跳出当前题单。筛选菜单使用共享组件的受控 model 接口同步勾选与可访问状态。

已结束比赛的排名按接口真实页数分页，不再截断返回行或展示无响应的“查看全部”。分页期间禁用切页，区分加载、空结果与可重试错误；运行中继续使用原实时排名接口和轮询。

“我的比赛”由页面提供唯一标题和说明；分类加载时保留标签栏，只切换正文与加载提示，避免卸载当前键盘焦点。

比赛首页按内容容器宽度切换双栏，为排行榜保留完整列宽；独立排行榜网格子项允许收缩，窄屏仅在排名区域内横向滚动。领奖台随名称和参赛次数自然增高。往期比赛按自身宽度切换卡片与表格，完整显示标题和本地化日期；表头与操作列保持同宽，分页在空结果时保留第 1 页并禁用前后切换。

全球与本地排名入口通过 `scope` 查询参数指定范围，页面按钮同步更新该参数并重新读取排名。刷新与同页导航沿用地址中的范围，两个入口重定向时保留其他查询参数。

管理端提交详情使用有界高度、固定头尾和可收缩正文，长 ID 允许断行，代码仅在自身区域内横向滚动。状态显示与通过率统计复用共享状态规范化，筛选提交原始状态 key、使用 code 查找翻译；相对时间使用管理端本地化日期工具。

管理端共享筛选器从当前选项读取选中标签，并以筛选用途提供可访问名称；选择列在渲染时解析可访问名称，使切换语言无需重新打开控件。题目排序字段与方向分别表达，选中标签实时本地化；题解的全部标记状态与全部可见性选项保留各自用途。侧栏选中图标和文字继承所属行的前景色，避免自定义主题中与选中背景同色；收起后的图标入口仍保留完整可访问名称。

Console 侧栏外壳使用同一高度变量定义固定顶栏与主内容的锚点滚动留白；正文目录、带标识的内容和段落回退目标仍使用原生滚动，定位后保留完整标题。

论坛详情的窄屏目录复用共享 Popover，浮层脱离帖子卡片的裁切区域并按视口避让；短屏只滚动目录列表，选择章节后关闭并沿用现有锚点导航。

论坛详情操作栏和评论标题共用已加载评论树的递归计数，避免历史汇总统计与当前评论列表不一致。

共享内容目录在页面及嵌套面板滚动时，依据标题越过顶部留白的位置更新选中项；观察回调使用同一判定，并支持粗体小节的回退目标，避免首次大步滚动未触发观察回调时高亮停在上一章节。

Console 侧栏选中图标继承所在行的前景色，保持实心选中背景下的图标对比度；比赛侧栏按历史比赛的实际目标路径及排行榜的 `scope` 查询值显示选中状态，旧入口地址继续保持兼容。反馈页表单和帮助栏从大屏断点开始并排，避免平板展开侧栏后帮助栏过窄。反馈类型的当前标签读取现有选项翻译，随界面语言更新并保留选中值。

Console 路由切换到不同路径时回到页面顶部；浏览器前进、后退优先恢复历史滚动位置，同页查询筛选保留当前位置。已有 hash 目标使用其计算后的滚动留白定位，避免标题落入固定顶栏下方。

Console 共享 Markdown 编辑器保留原文缩进，但软换行续行不重复深层缩进，避免窄屏代码被挤成单字符列；字号与源文本不变。论坛编辑页标题沿用共享 Input 的默认内边距和提示文字颜色；标签选择复用现有 Popover 的边界定位、焦点返回和关闭行为，可用高度不足时仅在标签列表内部滚动。手机短屏允许页面纵向滚动至编辑器底部，操作栏保持可达，桌面保留编辑与预览双栏。已有标签标识显示对应名称，选中状态兼容现存标识与名称，读取时不改写保存值。

题解编辑页在大屏并排显示编辑器与预览，小屏上下排列并允许页面滚动，顶部操作栏保持可达。 无法解析创作上下文时保留明确错误与返回入口，禁用发布/更新并隐藏草稿已保存状态；共享发布方法同样拒绝初始化错误状态，不向服务发送陈旧草稿。话题选择复用共享 Popover 的视口边界、关闭与内部滚动行为；话题接口适配层将服务返回的数组映射到现有创作流程的 `topics` 字段，保留空列表与失败处理。

题单详情的操作按钮在可用宽度不足时换行，更多菜单保留可访问名称；题目表格独立横向滚动，保留通过率和难度列。

Console 共享表格的默认空态允许说明文字换行；自定义空态在无数据时按内容区宽度显示，不受表头列宽限制。非空数据保留表格及其独立横向滚动。

管理端备份页区分列表加载失败与成功空列表：加载失败时保留页面内错误提示，加载中或失败时统计显示不可用占位；刷新成功后恢复实际统计和列表。

运行监控的标题与健康状态行允许换行，服务检查卡片按内容区宽度排列；状态图标保持尺寸，长服务名及探测消息在卡片内断行。 六项监控读取独立降级，失败项保留不可用状态，不补零统计、伪造检查时间或空队列；页面显示可重试说明与占位，成功项保持可见。内存比例分母无效时不绘制进度或显示 NaN。

管理端帮助页的文档入口打开 GitHub 仓库 `main` 分支的 `docs/index.md` 文档地图；新窗口链接保留 `noopener noreferrer`，不依赖前端部署提供 `/docs/` 静态目录。

审核概览的状态卡按内容区可用宽度排列，标签允许断行、图标保持尺寸，同排数值底部对齐。状态卡和快捷入口使用保留原目标的路由链接，支持键盘导航和可见焦点；窄屏刷新按钮保留可访问名称。

申诉审核弹窗的正文允许收缩和长文本断行，批准与拒绝按钮按可用宽度换行，避免长翻译撑宽弹窗；保留原有高度边界及内部纵向滚动。

举报与申诉页的统计栏沿用审核队列的换行规则：统计项整体换行，标签与数值不逐字挤压，行间保留间距。

审核队列、举报和申诉列表的空态描述当前筛选结果，不推断系统全局无数据或没有待处理事项。

审核队列的操作菜单名称和详情实体类型复用现有翻译。审计列表的自定义搜索组以容器可用宽度为边界，保留桌面搜索框的宽度上限，不以固定最小宽度越出窄屏卡片。审计列表的选中筛选标签随界面语言更新，操作、实体类型和日期输入具有可访问名称；审计详情保留完整日志及实体 ID，长值允许换行，图标保持尺寸。 审计报告的筛选标签同样跟随语言，标签关联表单控件，长选中值允许换行；加载或查询失败时隐藏旧统计并显示不可用占位，成功空结果保留空态。

唯一 CLI 配置为 `packages/design-system/components.json`，使用 `reka-nova`、CSS variables、Lucide 与官方 RTL 组件。添加所需组件时从仓库根运行：

```bash
pnpm dlx shadcn-vue@2.8.2 add <component> -c packages/design-system
pnpm --dir packages/design-system type-check
pnpm --dir packages/design-system test
```

CLI 的版本与 `shadcn-vue/tailwind.css` 运行样式保持一致。该官方样式桥接 Reka 的 `data-state` / `data-orientation` 与 Nova 的状态变体，不能当作闲置 CLI 依赖删除。生成组件的内部 import 必须使用共享包公开子路径，不能绑定某个应用的 `@` 别名。仅集成的 Nova 组件风格和 Lucide 通用图标对外提供，不开放未集成风格；OAuth 等品牌标记保留其品牌图形。Monaco、ECharts、Unovis 通过共享语义 token 做最小适配。Monaco 按需加载已安装的 ESM 包与本地 Worker，不要求浏览器从外部 CDN 取得编辑器运行时。 题目工作台顶栏仅在宽桌面使用绝对居中的单行操作区，平板保留正常文档流与换行，避免英文导航、运行/提交和账户操作重叠。题目代码面板的命令组按容器宽度换行，保持自然高度，不把工具栏撑出边界或覆盖编辑器。 共享快捷键弹窗仅在 sm 以上扩大最大宽度，手机保留默认视口边距和整框滚动。

外观状态、白名单解析、持久化与 DOM 应用归属 `packages/theme`，设置弹窗归属 `packages/design-system/AppearanceSettings.vue`。最终用户通过界面调整主题色、基础色、图表配色、原生字体栈、圆角、密度、菜单样式及明暗模式，无需 CLI 或构建。预览更新当前界面及 Portal；应用成功才保存，取消或关闭恢复已保存外观，恢复默认直接保存默认值。存储不可用时显示失败，不把内存回退当作持久化成功。状态语义色独立于主题色，设置不能输入任意 CSS、HTML、脚本或布局。

偏好保存在当前浏览器、当前 origin 的 `ulticode-appearance:v1:` 命名空间中，并按经过认证的用户 ID 分区；访客使用独立分区。账号切换关闭预览并加载对应分区。`ulticode-appearance-account` 只用于首屏外观提示，不是认证凭证或授权来源。同源标签页通过 `storage` 事件同步；预览期间不覆盖草稿，取消时恢复最新已保存值。Console 的同源 `/landing/` 入口可共享偏好；独立开发端口或跨域部署不能共享 localStorage。当前没有服务端偏好接口，不提供跨设备账户同步。

修改主题 bootstrap 后同步三端 `public/theme-bootstrap.js`，并运行门禁：

```bash
pnpm --dir packages/theme sync:theme-bootstrap
pnpm --dir packages/theme verify:theme-sync
pnpm --dir packages/theme verify:typography-tokens
pnpm --dir packages/theme test
```

只有主题包写入 `data-theme`。应用根据认证状态选择用户外观分区，不改变 auth bootstrap 先于 router 的顺序。语义圆角与密度变量在共享组件中应用；图表使用独立明暗调色板、可见数值或文字编码，专业图表字体读取当前 UI 字体。中文与英文随应用 locale 更新；RTL 支持来自官方组件，但应用不因此新增未配置的语言。

管理端仪表板的统计、注册趋势和最近活动独立报告读取失败：失败项不显示旧缓存或成功空态，成功项保持可见。页面等待本轮所有读取结束后提供重试，沿用原有三个只读请求；最近活动读取显式使用集合层的拒绝传播选项，不混用共享统计与图表的错误引用。加载状态具有可访问名称，成功重试恢复实际内容。

Console 我的比赛由页签组件统一持有分类读取，页面外壳不重复发起初始读取。分类失败显示本地化错误与重试，不显示成功空态；重试沿用当前分类的只读接口，切换页签保持控件与焦点，并复用请求层最新请求优先的状态管理。

Console 比赛浏览分别持有进行中/即将开始列表与往期分页的读取错误；初次加载等待两组读取结束。失败分类只显示本地化错误和对应只读重试，不显示缓存卡片、成功空态或往期分页；另一组成功内容仍可访问，重试不卸载页签控件。

管理端认证外壳隐藏 OAuth、找回密码及公开注册入口；管理员账号仍由既有管理员或显式初始化流程提供。共享认证表单的 `hideOAuth` 属性在 Vue 模板中写作 `hide-o-auth`，不能写成会映射为不同属性的 `hide-oauth`。注册占位组件同样隐藏 OAuth，不改变其拒绝注册的处理函数或路由可达性。

管理端界面语言通过共享 `locale-preference` 保存在当前浏览器的 `ulticode-locale`，不调用个人资料接口同步；账号的 `preferred_language` 是编程语言偏好。HTTP 请求刷新失败或最终返回 401 时按现有策略处理登录失效；403 保留拒绝响应，不清除会话或跳转登录页。

验证应分别记录三端的 `lint:check`、`type-check`、`test` 与构建，以及受影响共享包的检查。浏览器应检查真实计算样式、菜单 Portal、窄屏、键盘焦点、系统模式变化、预览事务、刷新保持和同源同步。API 固定数据的浏览器检查只能证明前端交互，不能替代真实 Cookie 会话、后端提交评测、Agent 运行或远端部署验收。

### Optional external Adapters

- 对象存储是所有环境的必需依赖：`FileStoragePort` 只有 S3/RustFS 实现，
  `LocalStorage`、`APP_STORAGE_TYPE=local`、dev-lite 本地文件回退和运行时切换都已删除。
  RustFS 由 `docker/docker-compose.yml` 的 `rustfs` 服务提供，数据落在独立卷
  `rustfs_data`，容器以 `10001:10001` 运行。开发环境把 API/Console 发布到 loopback
  （默认 `127.0.0.1:9000`/`9001`，仅宿主机后端与人工排查使用），宿主机 PM2 后端用
  `APP_STORAGE_S3_ENDPOINT=http://127.0.0.1:9000` + `APP_STORAGE_S3_TLS_ENABLED=false`；
  生产后端在内部网络用 `https://rustfs:9000` + operator 提供的 `RUSTFS_TLS_CERT_DIR`
  （`rustfs_cert.pem`/`rustfs_key.pem`，证书/CA 必须被后端 JVM 信任），且不发布任何端口。
  只有 RustFS 容器挂载整个目录（服务端需要私钥）；`rustfs-init`、`rustfs-iam-init` 与
  backend-admin/backend-app 仅挂载 `rustfs_cert.pem`，运行时无法读到 TLS 私钥。
  缺失 endpoint、region、bucket、access key、secret key 或 TLS 配置时应用启动失败，绝不回退本地磁盘；
  非 loopback 明文 HTTP 仍然拒绝。配置齐备后还会在**上下文刷新期间**（HTTP 端口对外服务之前）对 bucket 做
  有界重试探测，失败即本次启动失败；`/health/ready` 报告 `storage` 组件，未经验证不会返回就绪。
  bucket 由一次性 `rustfs-init` 服务幂等创建，随后 `rustfs-iam-init` 创建 prefix-scoped
  App/Admin 用户；`APP_STORAGE_S3_*` 默认使用 App pair，PM2 的 Admin 进程显式使用
  Admin pair。凭据只来自 `.env`/部署密钥系统，不使用 RustFS 默认账号。
- 对象布局与读取策略：头像 `app/avatars/{accountId}/{uuid}.{ext}`（key 全部由服务端生成，
  扩展名来自内容嗅探而不是原始文件名），备份 `admin/backups/{yyyy}/{MM}/{backupId}.sql`。
  bucket 保持私有：浏览器只通过后端只读代理 `GET /api/users/avatars/{accountId}/{name}`
  读取头像（允许匿名；代理校验 key 语法与账号绑定，对象名为服务端 UUID），备份只通过 `/admin/backups/**` 鉴权端点下载；数据库保存 object key，
  不保存带环境地址的完整 URL。通用资料更新（HTTP `PATCH /users/me` 与 Dubbo
  `UpdateProfileCommand`）不接受指向 object store 的 avatar 值：这类 key 的清理意图可能已入队，
  只有头像上传端点能让一个 key 成为当前值。
- 旧本地文件（`uploads/avatars/*`、旧 `BACKUP_DIR/backup_*.sql`）用
  `scripts/dev/migrate-object-storage.sh` 迁移：默认 dry-run，`--apply` 才写入，上传后校验大小与
  checksum 并回读对象，校验通过后才切换数据库，且从不删除旧文件。
  `./scripts/dev/up.sh` 在启动 backend-app 前用同一个
  `scripts/runbooks/assert-legacy-objects-migrated.sh` 探针检查这些行，仍有未迁移行就拒绝启动，
  避免升级后的本地库直接显示指向不存在对象的头像代理 URL。探针只覆盖本次启动选中的 owner
  （`--scope app-journey` 不会被它不服务的备份行拦住），且先停掉仍在运行的对应旧进程再计数，
  避免探针与随后的 `startOrRestart` 之间写入新的遗留行。
  RustFS 实例级 smoke test 见 `scripts/dev/rustfs-smoke-test.sh`。
- Notification 保留 `LoggingSmtpSenderAdapter` 默认路径；真实 SMTP 只通过
  `SMTP_*`/`APP_EMAIL_ENABLED` 配置，不让业务 Module 依赖厂商 SDK。
- 本地 observability 使用 `docker/docker-compose.observability.yml`。托管 OTLP
  仅通过部署环境提供 HTTPS endpoint/header，并显式叠加
  `docker/docker-compose.observability-managed.yml`；仓库不创建外部账户或凭据。

修改共享包或认证代码时，按本页“测试与质量”在 Console、Management 和对应 package 分别验证。

### 数据库入口

```bash
./scripts/dev/migrate.sh info
./scripts/dev/migrate.sh validate
./scripts/dev/migrate.sh migrate
bash scripts/runbooks/owner-schema-contraction.sh preflight
```

完整迁移顺序、回滚和破坏性操作确认见 [`../init-db/README.md`](../init-db/README.md) 与 [`OPERATIONS.md`](OPERATIONS.md#数据库迁移与-owner-收敛)。
## 测试与质量

### 统一入口

CI 的静态任务运行 `bash scripts/test/zero-infra-validation-contract.sh --static-only`，无需 Java、mise 或前端依赖。省略 `--static-only` 会额外执行 unit deny 自证，需要预装 mise Java 17、pnpm 和前端依赖。

```bash
./scripts/dev/test.sh static       # 只读、零基础设施的结构门禁
./scripts/dev/test.sh unit         # static + 前端单测 + -Punit 后端门禁（deny 环境，无 *IT）
./scripts/dev/test.sh quick        # static + unit 兼容别名（已弃用，新代码请用全名）
./scripts/dev/test.sh full-local   # 容器 + 迁移 + Maven verify + 前端测试（原 quick 的重型语义）
./scripts/dev/test.sh full         # full-local + 前端构建、i18n 与依赖审计
./scripts/dev/test.sh integration  # full-local + Testcontainers/Sandbox/owner migration 安全演练
```

`static` 不调用 Docker、数据库、服务、Testcontainers、Maven verify 或 `pnpm install`，可用 deny-shim 自证（见 `scripts/test/zero-infra-validation-contract.sh`）；缺 shellcheck 等可选工具时跳过并提示。`full-local` 保留原 `quick` 的完整覆盖（MySQL/Redis、owner migration、Maven verify/JaCoCo、Console/Management coverage 与类型检查）。`full` 追加前端构建、i18n 与依赖审计；`integration` 追加 Testcontainers、数据库/Redis/Sandbox 相关集成测试。后端 `unit` 使用根 POM 的 `unit` profile（Surefire 排除 `*IT`/`*IntegrationTest` 含嵌套类，不传 `-Dtest` 选择器）；wrapper 剥离基础设施凭据并以 deny 环境实证（deny 运行 5786 测试零失败、零 Testcontainers、零 IT），零基础设施自证见 `scripts/test/zero-infra-validation-contract.sh`（含 unit deny 阶段）。具体阶段由 `scripts/dev/test.sh` 实现，不在本页复制脚本内部逻辑。

Shell 工具的文件断言和基线编排可单独运行 `bash scripts/test/shell-tooling-contract.sh`，也包含在 `static` 中。该测试使用临时文件和 Docker 替身检查失败传播、清理及基线不变性，不连接 Docker daemon 或真实数据库；真实迁移效果仍由基线和集成门禁验证。

owner/module 的 declarative source contract registry 由 scripts/test/owner-architecture-source-contract.rules 持有，并由 scripts/test/owner-architecture-source-contract.sh 执行；scripts/dev/architecture-contract-test.sh 仍是唯一架构门禁入口，parent 负责 static/full eligibility、执行顺序和结果汇总，并以 `--list-qualified static|dynamic` 提供逐行、无执行副作用的 CI 注册表视图。Backend CI 只消费 dynamic 视图，zero-infra 则从同一 static/dynamic 视图核对 static-only banner；child 脚本不在 workflow 中重复列出。

### Core 与 distributed 验证边界

| 层级 | 环境/副作用 | 能证明什么 | 明确不能证明 |
| --- | --- | --- | --- |
| `static` | 零基础设施、静态脚本与配置 | 依赖方向、allowlist、负向契约 | Owner 启动、数据库、消息、业务可用 |
| `unit` | deny 环境、`-Punit`、排除 `*IT` | 单元逻辑、fail-closed、生命周期交接 | 真实 Owner wiring、持久化、网络 |
| `core` | Core Maven parent/config/readiness smoke；Owner contexts disabled | Core parent、显式扫描、数据源工厂、readiness 语义 | enabled-owner bean graph、业务 HTTP/WS、数据库/Redis |
| enabled-owner wiring | disposable Owner artifacts + MySQL/Redis as required | 选定 Owner 的真实 child wiring 与 local Adapter 注入 | 完整业务旅程、生产 SLO/HA |
| distributed journey | disposable `app-journey` scope、seeded account/problem | login → Problem read → Bookmark write → ordinary-user denial | Core parity、Judge sandbox、生产流量 |
| Core journey | 显式 `CORE_ENABLED_OWNER_JOURNEY=1` disposable 门禁（Testcontainers）；默认不可执行 | Auth/Admin enabled wiring、readiness、HTTP login/grant/DB 持久化/重新登录权限读取、local read parity、fail-closed | RSA/JWKS、App 四步 journey、distributed transport 或生产同构结论 |

第一条代表性业务旅程固定为：`POST /auth/login`、`GET /problems/{id}`、
`POST /bookmarks/quick`、普通用户 `POST /problems` 得到 403/typed denial。
Submission→Judge→result 不属于这条首旅程。Core 的 readiness smoke 不得代替
enabled-owner wiring 或业务 journey。

### 按路径选择验证

| 变更范围 | 必需验证 |
| --- | --- |
| Core registry/assembly/lifecycle/local Adapter | `static` + `core-profile-contract.sh` + Core `-Punit`；若改变 enabled wiring，再运行 disposable wiring |
| Owner business module/endpoint | 对应 Owner `-Punit`、contract gate；涉及真实 journey 时运行命名 distributed scope |
| shared contract/platform/security | 相关 contract gate + distributed Owner tests；Core 受影响时追加 Core contract/smoke |
| 无法分类或跨边界变更 | 采用保守路径：distributed backend/contract checks + Core contract/smoke，不能只跑 static |

上述映射是验证路由，不代表每层已在本工作区执行；命令结果必须分别记录
`PASS`、`FAIL`、`NOT_RUN` 或 `BLOCKED_EXTERNAL`。

### 按触碰面验证

| 触碰面 | 最小命令 |
| --- | --- |
| Java 后端 | `(cd services && ./mvnw verify -B)` |
| 后端集成 | `(cd services && ./mvnw -Dtest='*IT' test -B)` |
| Console | `pnpm --dir apps/console lint && pnpm --dir apps/console type-check && pnpm --dir apps/console test:coverage && pnpm --dir apps/console build` |
| Management | `pnpm --dir apps/management lint && pnpm --dir apps/management type-check && pnpm --dir apps/management test:coverage && pnpm --dir apps/management validate:i18n-keys && pnpm --dir apps/management build` |
| `packages/auth-core` | `pnpm --dir packages/auth-core test:coverage && pnpm --dir packages/auth-core type-check` |
| migration/Compose | `docker compose --project-directory . --env-file .env -f docker/docker-compose.yml -f docker/docker-compose.dev.yml config >/dev/null` 与 `git diff --check` |
| 架构/文档 | `bash scripts/dev/architecture-contract-test.sh` 与 `bash scripts/dev/docs-contract-test.sh` |

### 测试约定

- `*IT.java` 是集成测试；普通 Maven test 排除它们，显式 `-Dtest='*IT'` 或 `integration` 才运行。
- 测试行为、边界、错误语义、幂等、并发和安全不变量；不要用空测试或 `@Disabled` 代替验证。
- 后端使用 JUnit 5、Mockito、Testcontainers、JaCoCo；前端使用 Vitest/V8，关键用户路径使用 Playwright。
- 共享包或 contract 变更需验证两个前端（若被双方消费）以及 API shape/兼容性门禁。
- 覆盖率报告是 CI/本地产物，不等于生产流量覆盖；阈值由 POM、package 配置和 `scripts/test/coverage-contract.sh` 维护。

### 安全与迁移门禁

架构门禁同时检查 Owner 单写者、JWT/CSRF、Redis ACL、Nacos identity、Dubbo mTLS、Compose 网络、Judge sandbox、Streams replay、migration preflight、contract compatibility 和文档关键事实。迁移脚本默认 dry-run 或 preflight；任何 backfill、contraction、rollback、credential 或生产动作都必须显式确认并保留可审计输出。

### 验证结果语义

`Repository Implemented`、`Locally Validated`、`Staging Validated`、`Production Applied` 不可混用。本仓库没有生产环境；disposable Compose/DinD 只能证明仓库侧行为。生产部署、真实流量、证书、外部 telemetry、HA failover 与远程 Judge 仍由部署方验证。
## 配置与环境变量

### 来源与边界

`.env` 是本地部署密管输入，由 `./scripts/dev/init-env.sh` 生成并被 Git 忽略；`.env.example` 与 `.env.test.example` 是可提交模板，不包含可用凭据。实现配置以各服务 `application.yml`、Compose 和 `ecosystem.config.cjs` 为准。

### 常用变量

| 变量 | 用途 | 规则 |
| --- | --- | --- |
| `DB_HOST/PORT/USER/PASSWORD/NAME` | 兼容基础 MySQL 连接 | 仅作为明确 owner 配置未提供时的 dev fallback |
| `AUTH_*`、`ADMIN_*`、`APP_*`、`SUBMISSION_*`、`NOTIFICATION_*` | Owner 数据库连接 | 生产必须显式提供 owner host、端口、库、用户和密码 |
| `REDIS_HOST/PORT/USERNAME/PASSWORD` | Redis 连接 | ACL principal 按 Owner 分开，命令/key/channel 受限 |
| `JWT_SECRET` | 仅 local compatibility profile 的 HMAC secret | 至少 32 字符；生产 access token 使用 RS256/JWKS |
| `NACOS_SERVER_ADDR` / `NACOS_SERVERS` | 服务发现 | dev 使用显式 standalone；prod/HA 使用 cluster peer list |
| `NACOS_USERNAME/PASSWORD` 及 `*_NACOS_*` | Nacos workload identity | 每个 workload 使用独立、namespace-scoped 账号；内置账号禁用。`DEFAULT_GROUP` 普通配置只读；Dubbo 元数据写入仅允许 `interface:version:serviceGroup:provider或consumer:backend-<owner>` 中本 workload 的 application 后缀，不授予整组配置写权限 |
| `DUBBO_NAMESPACE` | Dubbo 环境隔离 | prod 必须非空，不能静默回退到 dev |
| `CORS_ALLOWED_ORIGINS` / `FRONTEND_URL` | 浏览器来源与链接 | 生产使用部署域名，不能使用 wildcard |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `Secure=false` 只允许全 local profile |
| `JUDGE_DOCKER_HOST` / `JUDGE_DOCKER_CERT_DIR` / `SANDBOX_HOST_DIR` | 生产 Judge remote/rootless Docker TLS | 证书由外部 secret store 提供；不提交到 Git |
| `REDIS_ACL_DIR` | 运行时 ACL 目录 | ignored、原子物化；不把 hash snapshot 放入 Git |
| `TLS_CERT_DIR` | 前端生产 TLS 证书挂载 | secret mount；开发 HTTP 不需要 |

正常 `dev-lite`/`dev-full`、Owner migration 与生产 Compose 都要求 `APP_SUBMISSION_ROUTING_MODE=remote` 和 `SUBMISSION_CUTOVER_COMPLETE=true`；另外还需要独立 migration principal、`DUBBO_MTLS_CERT_DIR` 和外部 OTLP collector 等变量。缺失必需变量时应 fail closed，而不是生成默认凭据。

### 安全规则

- 不在源码、文档、日志、命令输出或提交中打印 password、token、private key、certificate 内容。
- Access/refresh token 使用 HttpOnly cookie；refresh token 只存 hash。
- `JWT_COOKIE_SECURE=false` 只能在明确的 `dev`、`test`、`ci` profile；生产启动拒绝混合绕过。
- 生产 Compose 不发布 MySQL、Redis、Nacos 或 backend 端口；开发仅 loopback。
- 改 `.env` 后通过 `./scripts/dev/up.sh --mode dev-lite --skip-install` 重新加载，不直接绕过 manifest 启动服务。

完整可用字段见 [`.env.example`](../.env.example) 和本页“本地开发”；不要在本文复制模板的全部变量。
## 编码指南与规则入口

项目契约由根 [AGENTS.md](../AGENTS.md) 与最近的嵌套指南维护。本页解释规则的分工与维护方式，不另建一套编码规范。

### 三层分工

| 层 | 职责 | 不承担的职责 |
| --- | --- | --- |
| 根及嵌套 AGENTS.md | 安全不变量、模块边界、授权与验证原则 | 通用语言教材 |
| [.claude/rules](../.claude/rules/README.md) / [.omp/rules](../.omp/rules/README.md) | 修改相关路径时补充少量风险提醒 | 强制全仓探索、逐项报告、重复项目指南 |
| [.codex/rules](../.codex/rules/README.md) | 高风险命令前缀的执行策略 | 路径匹配、代码风格、理解用户授权意图 |

Claude 使用 `paths`；OMP 扩展使用 `globs`，其 `paths` 字段当前不参与匹配。两份路径规则保留各自加载格式，同一主题的正文保持一致；不增加生成器或新的运行依赖。OMP 不再叠加全栈汇总规则或用于检查规则文本的运行时中断。JVM 诊断保留很短的常驻安全提醒，仅在实际附加进程时适用。

### 保留什么

只保留有明确后果的约束：数据或用户改动丢失、凭据泄漏、鉴权绕过、契约不兼容、越过模块或数据库所有权、错误的事务与并发语义。具体项目不变量仍以 AGENTS.md 为准。

调查和验证随改动风险扩大：小改动不要求变更矩阵、全链路图、全量构建或正式多角色审查；跨服务兼容、数据迁移、安全边界改变时，再补相应证据。按本页“测试与质量”选择适用检查，说明未验证的部分。

### 格式交给配置

缩进、引号、分号、换行、导入顺序等以受影响模块的格式器和 lint 配置为准；没有配置时沿用邻近代码。不要从另一应用复制格式配置，不为风格统一批量重写，也不通过 rules 添加任意方法长度、注释数量或命名后缀要求。

需要格式检查时，使用仓库已有工具并限定到改动文件。注意应用的 `lint`、`format` 脚本会修改文件；只读检查可选 `lint:check` 或格式器的检查模式。检查具体 package.json 后再运行，避免把全目录自动修复当作例行步骤。

### 执行策略的边界

Codex 前缀规则只能识别已列出的参数排列，无法覆盖任意脚本、包装器、绝对路径或选项位置。未命中不代表授权或安全；不要通过改写命令绕过审批。普通本地操作不额外设置 `prompt`，也不添加宽泛的 `allow`。

外部发布、数据删除等保留窄范围 `prompt`。它可能在用户已授权后仍要求工具层批准；agent 不再额外重复询问。项目策略是否加载和生效取决于可信配置层、启动加载及当前执行模式，不能把它当作完整沙箱或秘密扫描器。

### 维护与检查

添加规则前先确认现有指南、工具配置和测试是否已覆盖该问题。避免重复正文、固定版本信息和普通语言常识。修改路径时检查匹配与不匹配样例；修改命令策略时运行 `codex execpolicy check`，同时验证危险命令和普通工作命令。检查 diff、链接和空白即可验证纯规则文档修改；不声称因此验证了应用运行行为或模型性能。

参考：[Codex rules](https://learn.chatgpt.com/docs/agent-configuration/rules)、
[omp-path-rules](https://github.com/DavidHLP/omp-path-rules/blob/master/README.md)。
## 排障

### 启动与环境

- **服务启动绕过策略**：不要直接使用 PM2/Maven 启动 runtime；用 `scripts/dev/up.sh --mode dev-lite|dev-full`，否则会绕过 manifest、Owner readiness、migration 和 rollback gate。
- **环境变量改动未生效**：执行 `./scripts/dev/up.sh --mode dev-lite --skip-install`，不要手工重启单个进程。
- **Java 17 cgroup-v2 异常**：优先使用 mise 管理的 Zulu 17；旧的本地 17.0.2 可能在 JVM processor discovery 阶段失败。
- **Redis/MySQL/Testcontainers 不可达**：区分宿主权限/凭据/容器状态与源码失败。记录准确错误；不能把 `BLOCKED_EXTERNAL` 写成测试通过。
- **中文乱码**：容器内 MySQL 客户端显式使用 `--default-character-set=utf8mb4`，否则可能产生双重编码；迁移入口见 [`OPERATIONS.md`](OPERATIONS.md#数据库迁移与-owner-收敛)。

### 认证与路由

- 生产 Cookie policy、JWT/JWKS、CSRF、Nacos/Dubbo identity 失败时应 fail closed。不要通过关闭 CSRF、改用 access token refresh 或放宽 `anyRequest` 修复。
- WebSocket 只读取 `access_token` cookie；不要在 URL、query 或 STOMP 客户端 token 中传递凭据。
- Admin/privileged endpoint 必须同时检查路由和方法权限；审计 actor 取 principal/delegation，不取请求字段。

### 队列、Outbox 与 Worker

- Search/Judge/Notification backlog 先检查 PM2 状态、Redis ACL、PEL、DLQ 和下游健康；不要先删除 stream、PEL、ledger 或 version hash。
- Judge/Submission 使用 generation/attempt fence；重放前确认新 generation 和 owner receipt，避免旧 verdict 覆盖新状态。
- Notification poison event 进入 DB inbox；Search/Judge DLQ 的 replay/discard 见 [`WORKER_SLO_RUNBOOK.md`](../services/docs/WORKER_SLO_RUNBOOK.md)。
- 关闭服务时让 DrainGate 停止新 claim；保留未 ACK 的 PEL/lease 给现有 reaper 恢复。

## 迁移与部署

迁移问题先运行 preflight 和只读 parity/checksum；backfill 默认 dry-run，目标冲突停止并导出 failure artifact。生产 rollback 只能回到 schema-compatible artifact，不执行 downgrade。部署前检查 source commit、migration checksum、immutable digest manifest、Compose config 和健康 descriptor。

更详细的可执行步骤：

- [数据库迁移](OPERATIONS.md#数据库迁移与-owner-收敛)
- [部署与回滚](OPERATIONS.md#部署发布与回滚)
- [Services 问题注册表](../services/docs/SERVICES_ISSUES.md)
- [Worker SLO Runbook](../services/docs/WORKER_SLO_RUNBOOK.md)
