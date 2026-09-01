# Contract Compatibility Gate (api/* 二进制兼容门禁)

> 范围：`services/api/*` 四个 Dubbo 契约模块（`auth-api` / `app-api` / `submission-api` / `notification-api`）；未落地且无消费者的 Admin notification migration placeholder 已删除
> 工具：`com.github.siom79.japicmp:japicmp-maven-plugin:0.26.1`（Maven Central / [MavenPlugin 文档](https://siom79.github.io/japicmp/MavenPlugin.html)）
> 绑定：`services/pom.xml` profile `contract-compat`，`verify` 阶段 `cmp` goal
> CI：`.github/workflows/_contract.yml`（reusable）由 `ci.yml` 按 path filter 触发

## 1. 为什么需要这个门禁

仓库已落地 per-service 独立发布（`service.version.*`、不可变镜像 digest manifest、按需 rollout/rollback），但 `api/*` 契约此前只有“形式上的 Dubbo 接口”而无机器化混合版本兼容证明。不同服务可能以不同版本运行，若契约出现二进制不兼容变更（删方法/改签名/降可见性/删字段等），旧消费者在滚动发布窗口内会以 `NoSuchMethodError` / `IncompatibleClassChangeError` 等形式崩溃。本门禁在 PR 阶段用 japicmp 对新旧 jar 做二进制对比，破坏性变更默认 fail。

## 1.1 合同归属与生命周期

| 模块 | Provider owner | 已知 Consumer | Transport | Lifecycle |
| --- | --- | --- | --- | --- |
| `backend-auth-api` | `backend-auth` | App、Admin、Submission、Notification | 内部 Dubbo request/response | Dubbo interface `1.0.0` / Maven artifact revision `2.0.0`；账号/授权查询与幂等管理命令 |
| `backend-app-api` | `backend-app` | Admin、Submission、Judge、Notification 及 App 内部适配器 | 内部 Dubbo request/response、事件与 WS payload | Dubbo interface `1.0.0` / Maven artifact revision `2.0.0`；App 领域查询/命令及显式跨 owner seams |
| `backend-submission-api` | `backend-submission` | App、Admin、Judge | 内部 Dubbo request/response、Redis Streams、生命周期事件 | Dubbo interface `1.0.0`；Maven artifact revision `2.0.0` 删除无仓库消费者的 N-1 合同 |
| `backend-notification-api` | `backend-notification` | App、Admin | 内部 Dubbo request/response、事件与 WS payload | Dubbo interface `1.0.0` / Maven artifact revision `2.0.0`；通知命令/意图与 reconciliation read seam |

每个模块只承载其 Provider owner 的无实现合同；Consumer 通过显式 port/service
接口引用，不能把 Entity、Mapper、ServiceImpl、Repository 或 owner 实现带入合同。
未发现真实 Consumer 的迁移 placeholder 不进入 reactor；因此 Admin notification
placeholder 已删除，而不是伪造 Provider、Consumer 或兼容 facade。

## 2. 机制

### 2.1 Maven profile

`services/pom.xml`:

- `properties` 新增 `japicmp.version=0.26.1`（来源：[Maven Central](https://central.sonatype.com/artifact/com.github.siom79.japicmp/japicmp-maven-plugin) 与 [官方 MavenPlugin 页](https://siom79.github.io/japicmp/MavenPlugin.html) 最新版 `0.26.1`）与 `contract.compat.oldVersion`。
- `contract.compat.oldVersion` 默认是 `__missing_contract_baseline__`；没有显式基线时故意失败，禁止自比较假通过。CI 从 standalone baseline 元数据计算并传入真实版本；本地可通过 `-Dcontract.compat.oldVersion=<version>` 指定已安装基线，也支持 Maven version range 如 `-Dcontract.compat.oldVersion=[0,1.0.0)`，由 Maven 从本地仓库中解析同 `groupId:artifactId` 的最近旧版本（japicmp 的 `oldVersion` 为 Maven dependency，version 支持 range；查证见 [MavenPlugin 文档](https://siom79.github.io/japicmp/MavenPlugin.html) 的 `<oldVersion><dependency><version>` 用法）。
- `contract-compat` profile 还绑定 Maven Enforcer `requireProperty`；若仍使用该 sentinel，`validate` 阶段即以明确的 baseline 错误退出。japicmp 的报告 guard 另外拒绝缺少 `oldJar`/`newJar`/`oldVersion` 元数据的 `n.a.` 结果，并拒绝 old/new 自比较。

profile 定义（精简）：

```xml
<profile>
  <id>contract-compat</id>
  <build>
    <plugins>
      <plugin>
        <groupId>com.github.siom79.japicmp</groupId>
        <artifactId>japicmp-maven-plugin</artifactId>
        <version>${japicmp.version}</version>
        <configuration>
          <oldVersion>
            <dependency>
              <groupId>${project.groupId}</groupId>
              <artifactId>${project.artifactId}</artifactId>
              <version>${contract.compat.oldVersion}</version>
              <type>jar</type>
            </dependency>
          </oldVersion>
          <newVersion>
            <file>
              <path>${project.build.directory}/${project.artifactId}-${project.version}.${project.packaging}</path>
            </file>
          </newVersion>
          <parameter>
            <onlyModified>true</onlyModified>
            <accessModifier>public</accessModifier>
            <breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>
            <includeSynthetic>false</includeSynthetic>
            <onlyBinaryIncompatible>false</onlyBinaryIncompatible>
            <skipPomModules>true</skipPomModules>
            <packagingSupporteds><packagingSupported>jar</packagingSupported></packagingSupporteds>
            <includeModules><includeModule>backend-.*-api</includeModule></includeModules>
          </parameter>
        </configuration>
        <executions>
          <execution>
            <id>contract-compat-check</id>
            <phase>verify</phase>
            <goals><goal>cmp</goal></goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

关键点：

- 目标 `cmp`（插件文档的 goal 名；在 pom 中即 `japicmp` 功能），绑定 `verify`（`mvn verify` 触发）。
- `oldVersion` 参数化；`newVersion` 指向当前 `target/` 产物。
- 仅在 `api/*` 四个模块生效：通过 `<parameter><includeModules><includeModule>backend-.*-api</includeModule></parameter>` 过滤（artifactId 正则）。`skipPomModules=true` 跳过聚合 pom；`packagingSupporteds=jar` 进一步收敛。满足“仅在 api/* 生效，保持最小”的要求。
- `breakBuildOnBinaryIncompatibleModifications=true`：二进制不兼容即 fail。
- `includeSynthetic=false`（默认）：合成方法/桥方法噪音不计入；`excludes` 保持空——不做业务豁免，业务豁免需走第 4 节流程。
- 其他模块（`platform/*`、各 Owner/Worker）即使在 reactor 中执行 `verify -P contract-compat` 也会被 `includeModules` 过滤为 skip，不产生报告也不 fail。

### 2.2 本地验证

显式基线的跨版本零 diff（验收必跑）：

```bash
export JAVA_HOME=/path/to/java17
cd services
./mvnw -Drevision=1.0.0 -pl api/auth-api -am -DskipTests install
./mvnw -Drevision=2.0.0-ci.local -Dcontract.compat.oldVersion=1.0.0 \
  -pl api/auth-api -am -P contract-compat verify
# 预期：BUILD SUCCESS，target/japicmp/ 下生成 contract-compat-check.xml/html/diff，且无 binary incompatible 项
```

跨版本对比（检测能力证明）：

```bash
# 1) 先以旧版本安装到本地仓库
./mvnw -Drevision=1.0.0 -pl api/auth-api -am -DskipTests install

# 2)  bump 本地工作副本版本（例如改 revision 或直接用 -Dcontract.compat.oldVersion 指定旧版本）
#    若需模拟新版本，可临时改 pom 的 revision 或用 -Drevision=1.0.1：
./mvnw -pl api/auth-api -am -Drevision=2.0.0-ci.local -DskipTests package
./mvnw -Drevision=2.0.0-ci.local -P contract-compat -Dcontract.compat.oldVersion=1.0.0 \
  -pl api/auth-api -am verify
# 无源码变更时零 diff；若临时删除一个 public 方法再跑，则应报 binary incompatible 并 fail，验证后 git restore。
```

> 说明：`api/*` 的当前版本来自 `${revision}`；跨版本验证必须在 package 与 verify 两步使用同一、且不同于旧版本的 revision。CI 从 standalone baseline 的 Maven `revision` 元数据计算 `oldVersion` 并用同一 revision 安装，再以 `<current_revision>-ci.<run_id>` 构建当前 `target/` jar 作为 `newVersion` 对比，避免自比较（见 3.2）。若基线没有 standalone API contracts（例如旧 monolithic layout），ownership boundary 仍运行，但输出 `baseline has no standalone API contracts; compatibility comparison skipped` 并跳过当前构建、japicmp 和 distinct-artifact guard。

若当前 revision 与 baseline 的 major 不同，workflow 会明确输出 `intentional major contract release; compatibility comparison skipped after repository retirement proof`，运行 `submission-compatibility-retirement-contract.sh`，并跳过 japicmp 与 distinct-artifact guard。该 skip 只由 major 版本差异触发；同 major 变更仍必须执行二进制兼容比较，不能手工设置 skip。

### 2.3 报告产物

每次执行在 `services/api/<name>/target/japicmp/` 生成：

- `contract-compat-check.xml` / `contract-compat-check.html` / `contract-compat-check.diff`（execution id 为 `contract-compat-check`；可用 `-Djapicmp.skipXmlReport=true` 等细粒度关闭）
- 控制台输出列出每个类的兼容性状态；`onlyModified=true` 时只打印变更类。

## 3. CI 接线

### 3.1 触发规则（ci.yml）

`ci.yml` 遵循“新 gate = 新 `_name.yml` + 一个 calling job + `ci-ok.needs` 一项 + `changes` path filter”既有模式：

- `changes` 新增输出 `contract`，filter：

  ```yaml
  contract:
    - 'services/api/**'
    - 'services/pom.xml'
    - 'services/docs/CONTRACT_COMPAT_GATE.md'
    - 'scripts/test/api-contract-boundary-contract.sh'
    - 'scripts/test/dubbo-provider-reference-contract.sh'
    - '.github/workflows/ci.yml'
    - '.github/workflows/_contract.yml'
  ```

- 新增 job：

  ```yaml
  contract:
    name: Contract Compatibility
    needs: changes
    if: ${{ !cancelled() && needs.changes.outputs.contract == 'true' }}
    uses: ./.github/workflows/_contract.yml
    secrets: inherit
  ```

- `ci-ok.needs` 追加 `contract`（skipped 视作 green，docs-only PR 不阻塞）。

### 3.2 _contract.yml 逻辑

- reusable `workflow_call` 声明可选输入 `baseline_ref`；调用方若声明并传递该输入即可覆盖基线。当前 `ci.yml` 不声明 `workflow_dispatch` 的 `baseline_ref` 输入；未传递时默认 `git describe --tags --abbrev=0` 取最近 release tag；无 tag 则 `::error` 并提示需建立 release tag。
- `checkout` 使用 `fetch-depth: 0`（需完整 tag 历史）。
- `git worktree add /tmp/baseline <baseline_ref>` 检出基线到临时目录。
- 基线：先从 `/tmp/baseline/services` 的 Maven `revision` 元数据解析基线版本，再执行 `./mvnw -Drevision=<baseline_revision> -pl api/auth-api,api/app-api,api/submission-api,api/notification-api -am -DskipTests install -B`（安装到本地仓库，供 `oldVersion` 解析）。若四个 standalone API module 不存在，则记录明确 skip 并不执行兼容比较。
- 当前分支：`services ./mvnw -Drevision=<current_revision>-ci.<run_id> -pl api/... -am -DskipTests package -B`（生成 `target/` jar 作为 `newVersion`，package 与 verify 使用完全相同的 distinct revision）。
- 执行：`services ./mvnw -Drevision=<current_revision>-ci.<run_id> -Dcontract.compat.oldVersion=<baseline_revision> -P contract-compat -pl api/... -am verify -B`（japicmp 以 `breakBuildOnBinaryIncompatibleModifications=true` 判定，并由 XML guard 拒绝 oldJar/newJar 自比较）。
- `actions/upload-artifact@v7` 上传 `services/api/**/target/japicmp/**`（`if: always()`，`retention-days: 7`）。
- `timeout-minutes: 20`、`setup-java@v5` + `cache: maven`、`chmod +x services/mvnw` 对齐 `_backend.yml` 风格。

> 注意：若后续需要“当前分支也 install”，应在 `verify` 之后再执行，以免覆盖基线 jar 导致自比较假 pass。

## 4. 破坏性变更处置

检测到 binary incompatible 时，CI 失败，需按以下任一路径处置：

1. **撤销/兼容化**：改为新增方法/重载、保留旧签名（deprecate）、新增 DTO 字段而非删除/改类型，确保二进制兼容后重新推送。
2. **走版本窗口（需升级 RpcPolicy）**：若必须破坏性变更：
   - 提升契约的语义版本（`revision` 或对应 `service.version.*` 的 major），并在对应 Dubbo `RpcPolicy` 常量或版本约束中声明不兼容窗口；
   - 采用双版本并存窗口：Provider 同时暴露新旧接口（或新旧 DTO 通过 `compat` 字段兼容），消费者分批升级，窗口期内 CI 可通过 `excludes` 临时豁免已公告的类（需评审），窗口结束后移除旧版本并清理豁免；
   - 发版时打 tag（如 `v2.0.0`），成为下一轮 reusable workflow 的 `baseline_ref`。

当前 Submission mutation Interface 拆分已完成 major contract release：

1. `backend-submission` 发布 `SubmissionIntakePort`、`SubmissionVerdictWritePort`，App/Judge/Admin 只消费窄 Interface。
2. 本仓库没有部署中的 N-1 consumer 或 registry；源码 inventory 为零仓库消费者，短时虚拟 14-day ledger 覆盖 write/fence/read drain、checksum、error budget 和 rollback。
3. `SubmissionWritePort`、`SubmissionAnalyticsPort` 与 `SubmissionWriteProvider` 已从 2.0.0 API/provider 产物删除。2.0.0 是显式 major release；同 major 的后续修改仍必须通过 japicmp，不能用 skip 绕过。

`ProblemTitleLookupPort` 是新增的 App provider / Submission consumer Seam，发布顺序固定为 **App provider first → Submission consumer second**；回滚顺序固定为 **Submission consumer first → App provider second**。旧 App 不提供该 FQCN，因此禁止选择性先部署新 Submission；该顺序由架构门禁与发布交接共同保留，真实混合版本运行证据仍归 SVC-010。

Dubbo provider/reference inventory 由 `scripts/test/dubbo-provider-reference-contract.sh` 生成：每行记录 owner、consumer、group、version、interface、调用字段和源码路径；脚本拒绝重复 provider、任何无仓库 consumer 的 provider，以及缺少 group 的 reference。当前不再允许 compatibility exception。

P1-DATA-001 新增的 Submission user-stat batch fields 与 Problem-stat batch read 属于
wire-incompatible Interface additions，provider/reference 使用 version `1.1.0`；新的
`SubmissionAdjudicationReadPort` 是独立的 `1.0.0` provider。旧的
旧的 `SubmissionWritePort`/`SubmissionAnalyticsPort` 不再保留；它们已随 2.0.0 major contract release 删除。

豁免流程：原则上 `excludes` 不开放业务豁免；确需豁免（合成/桥接噪音除外）必须在 PR 中说明理由、影响面、回滚计划，并由 Owner 负责人批准后在 `pom` 的 `<parameter><excludes>` 中按 `package.Class#method` 精确列出，禁止通配 `*`。

## 5. 基线 tag 约定

- 发版打 tag 是建立契约基线的唯一方式：每次契约发布执行 `git tag vX.Y.Z`；本仓库验证不执行 push。
- `_contract.yml` 默认以 `git describe --tags --abbrev=0` 找到最近 tag 作为 `oldVersion` 来源；无 tag 时 CI 明确报错并提示需先打 tag。
- reusable `workflow_call` 的调用方可声明并传递 `baseline_ref` 覆盖基线；当前 `ci.yml` 的 `workflow_dispatch` 没有声明或传递该输入。

## 6. 已知限制

- **同一 reactor**：`api/*` 与 `platform/*`（`backend-common` 等）及各 Owner 仍在同一 Maven reactor 中。契约变更若涉及 `platform/common` 的共享类型，需同步构建整个 reactor 才能通过编译；契约 jar 的本地安装与 CI 的 worktree 隔离仅解决二进制对比，不解决源码级同步构建的必要性。与 `PROJECT_DOCUMENTATION.md` 既有结论“api 与平台库仍同一 reactor，契约变更需同步构建”一致。
- **仅二进制兼容**：japicmp 检测二进制（`breakBuildOnBinaryIncompatibleModifications`）与源码兼容；语义/行为兼容（如字段含义变更、校验收紧）不在此门禁范围内，需配合契约测试与集成测试覆盖。
- **合成/桥接噪音已过滤**：`includeSynthetic=false` 过滤合成方法/桥方法；若仍出现噪音，应通过精确 `excludes` 而非关闭整个门禁处理。
