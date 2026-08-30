# Contract Compatibility Gate (api/* 二进制兼容门禁)

> 范围：`services/api/*` 五个 Dubbo 契约模块（`auth-api` / `admin-api` / `app-api` / `submission-api` / `notification-api`）
> 工具：`com.github.siom79.japicmp:japicmp-maven-plugin:0.26.1`（Maven Central / [MavenPlugin 文档](https://siom79.github.io/japicmp/MavenPlugin.html)）
> 绑定：`services/pom.xml` profile `contract-compat`，`verify` 阶段 `cmp` goal
> CI：`.github/workflows/_contract.yml`（reusable）由 `ci.yml` 按 path filter 触发

## 1. 为什么需要这个门禁

仓库已落地 per-service 独立发布（`service.version.*`、不可变镜像 digest manifest、按需 rollout/rollback），但 `api/*` 契约此前只有“形式上的 Dubbo 接口”而无机器化混合版本兼容证明。不同服务可能以不同版本运行，若契约出现二进制不兼容变更（删方法/改签名/降可见性/删字段等），旧消费者在滚动发布窗口内会以 `NoSuchMethodError` / `IncompatibleClassChangeError` 等形式崩溃。本门禁在 PR 阶段用 japicmp 对新旧 jar 做二进制对比，破坏性变更默认 fail。

## 2. 机制

### 2.1 Maven profile

`services/pom.xml`:

- `properties` 新增 `japicmp.version=0.26.1`（来源：[Maven Central](https://central.sonatype.com/artifact/com.github.siom79.japicmp/japicmp-maven-plugin) 与 [官方 MavenPlugin 页](https://siom79.github.io/japicmp/MavenPlugin.html) 最新版 `0.26.1`）与 `contract.compat.oldVersion`。
- `contract.compat.oldVersion` 默认 `${project.version}`（自比较，零 diff）。可通过 `-Dcontract.compat.oldVersion=<version>` 覆盖；也支持 Maven version range 如 `-Dcontract.compat.oldVersion=[0,1.0.0)`，由 Maven 从本地仓库中解析同 `groupId:artifactId` 的最近旧版本（japicmp 的 `oldVersion` 为 Maven dependency，version 支持 range；查证见 [MavenPlugin 文档](https://siom79.github.io/japicmp/MavenPlugin.html) 的 `<oldVersion><dependency><version>` 用法）。

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
- 仅在 `api/*` 五个模块生效：通过 `<parameter><includeModules><includeModule>backend-.*-api</includeModule></parameter>` 过滤（artifactId 正则）。`skipPomModules=true` 跳过聚合 pom；`packagingSupporteds=jar` 进一步收敛。满足“仅在 api/* 生效，保持最小”的要求。
- `breakBuildOnBinaryIncompatibleModifications=true`：二进制不兼容即 fail。
- `includeSynthetic=false`（默认）：合成方法/桥方法噪音不计入；`excludes` 保持空——不做业务豁免，业务豁免需走第 4 节流程。
- 其他模块（`platform/*`、各 Owner/Worker）即使在 reactor 中执行 `verify -P contract-compat` 也会被 `includeModules` 过滤为 skip，不产生报告也不 fail。

### 2.2 本地验证

自比较零 diff（验收必跑）：

```bash
export JAVA_HOME=/path/to/java17
cd services
./mvnw -pl api/auth-api -am -DskipTests install
./mvnw -pl api/auth-api -P contract-compat verify
# 预期：BUILD SUCCESS，target/japicmp/ 下生成 japicmp.xml/japicmp.html/japicmp.diff，且无 binary incompatible 项
```

跨版本对比（检测能力证明）：

```bash
# 1) 先以旧版本安装到本地仓库
./mvnw -pl api/auth-api -am -DskipTests install  # 版本 1.0.0

# 2)  bump 本地工作副本版本（例如改 revision 或直接用 -Dcontract.compat.oldVersion 指定旧版本）
#    若需模拟新版本，可临时改 pom 的 revision 或用 -Drevision=1.0.1：
./mvnw -pl api/auth-api -am -Drevision=1.0.1 -DskipTests package
./mvnw -pl api/auth-api -P contract-compat -Dcontract.compat.oldVersion=1.0.0 verify
# 无源码变更时零 diff；若临时删除一个 public 方法再跑，则应报 binary incompatible 并 fail，验证后 git restore。
```

> 说明：`api/*` 实际版本为 `${revision}`（固定 `1.0.0`），跨版本对比依赖本地仓库中已安装的旧 jar。CI 中通过 worktree 安装基线到本地仓库，再以当前工作区的 `target/` jar 作为 `newVersion` 对比（见 3.2）。

### 2.3 报告产物

每次执行在 `services/api/<name>/target/japicmp/` 生成：

- `japicmp.xml` / `japicmp.html` / `japicmp.diff`（`--skipXmlReport` 等默认全开；可用 `-Djapicmp.skipXmlReport=true` 等细粒度关闭）
- 控制台输出列出每个类的兼容性状态；`onlyModified=true` 时只打印变更类。

## 3. CI 接线

### 3.1 触发规则（ci.yml）

`ci.yml` 遵循“新 gate = 新 `_name.yml` + 一个 calling job + `ci-ok.needs` 一项 + `changes` path filter”既有模式：

- `changes` 新增输出 `contract`，filter：

  ```yaml
  contract:
    - 'services/api/**'
    - 'services/pom.xml'
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

- `workflow_call` 可选输入 `baseline_ref`（供 `workflow_dispatch` / PR 覆盖）；未提供时默认 `git describe --tags --abbrev=0` 取最近 release tag；无 tag 则 `::error` 并提示“需打 release tag”（`git tag v1.0.0 && git push origin v1.0.0`）。
- `checkout` 使用 `fetch-depth: 0`（需完整 tag 历史）。
- `git worktree add /tmp/baseline <baseline_ref>` 检出基线到临时目录。
- 基线：`/tmp/baseline/services ./mvnw -pl api/auth-api,api/admin-api,api/app-api,api/submission-api,api/notification-api -am -DskipTests install -B`（安装到本地仓库，供 `oldVersion` 解析）。
- 当前分支：`services ./mvnw -pl api/... -am -DskipTests package -B`（生成 `target/` jar 作为 `newVersion`，此时不覆盖本地仓库的基线 jar，保证对比有效）。
- 执行：`services ./mvnw -P contract-compat -pl api/... -am verify -B`（japicmp 以 `breakBuildOnBinaryIncompatibleModifications=true` 判定）。
- `actions/upload-artifact@v7` 上传 `services/api/**/target/japicmp/**`（`if: always()`，`retention-days: 7`）。
- `timeout-minutes: 20`、`setup-java@v5` + `cache: maven`、`chmod +x services/mvnw` 对齐 `_backend.yml` 风格。

> 注意：若后续需要“当前分支也 install”，应在 `verify` 之后再执行，以免覆盖基线 jar 导致自比较假 pass。

## 4. 破坏性变更处置

检测到 binary incompatible 时，CI 失败，需按以下任一路径处置：

1. **撤销/兼容化**：改为新增方法/重载、保留旧签名（deprecate）、新增 DTO 字段而非删除/改类型，确保二进制兼容后重新推送。
2. **走版本窗口（需升级 RpcPolicy）**：若必须破坏性变更：
   - 提升契约的语义版本（`revision` 或对应 `service.version.*` 的 major），并在对应 Dubbo `RpcPolicy` 常量或版本约束中声明不兼容窗口；
   - 采用双版本并存窗口：Provider 同时暴露新旧接口（或新旧 DTO 通过 `compat` 字段兼容），消费者分批升级，窗口期内 CI 可通过 `excludes` 临时豁免已公告的类（需评审），窗口结束后移除旧版本并清理豁免；
   - 发版时打 tag（如 `v2.0.0`），成为下一轮 `baseline_ref`。

当前 Submission mutation Interface 拆分采用兼容化而非豁免：

1. `backend-submission` 先发布 `SubmissionIntakePort`、`SubmissionVerdictWritePort`，同时继续发布 deprecated `SubmissionWritePort` 1.0.0 provider；旧消费者仍可调用全部真实能力。
2. App/Judge 再升级为只消费窄 Interface。升级期间不得先回滚 Submission provider；需要回滚时先回滚消费者，再回滚 provider。
3. 只有混合版本窗口、consumer drain 与回滚证据完成后，才允许删除 deprecated Interface/provider；删除属于后续 major/version-window 任务，不属于本次窄化。

`ProblemTitleLookupPort` 是新增的 App provider / Submission consumer Seam，发布顺序固定为 **App provider first → Submission consumer second**；回滚顺序固定为 **Submission consumer first → App provider second**。旧 App 不提供该 FQCN，因此禁止选择性先部署新 Submission；该顺序由架构门禁与发布交接共同保留，真实混合版本运行证据仍归 SVC-010。

P1-DATA-001 新增的 Submission user-stat batch fields 与 Problem-stat batch read 属于
wire-incompatible Interface additions，provider/reference 使用 version `1.1.0`；新的
`SubmissionAdjudicationReadPort` 是独立的 `1.0.0` provider。旧的
`SubmissionWritePort` 1.0.0 仍按本节的 N-1 窗口保留，不因本次 read cutover 删除。

豁免流程：原则上 `excludes` 不开放业务豁免；确需豁免（合成/桥接噪音除外）必须在 PR 中说明理由、影响面、回滚计划，并由 Owner 负责人批准后在 `pom` 的 `<parameter><excludes>` 中按 `package.Class#method` 精确列出，禁止通配 `*`。

## 5. 基线 tag 约定

- 发版打 tag 是建立契约基线的唯一方式：每次生产发布（或契约发布）执行 `git tag vX.Y.Z && git push origin vX.Y.Z`。
- `_contract.yml` 默认以 `git describe --tags --abbrev=0` 找到最近 tag 作为 `oldVersion` 来源；无 tag 时 CI 明确报错并提示需先打 tag。
- `workflow_dispatch` / PR 可通过输入 `baseline_ref` 覆盖基线（用于回溯或指定旧基线）。

## 6. 已知限制

- **同一 reactor**：`api/*` 与 `platform/*`（`backend-common` 等）及各 Owner 仍在同一 Maven reactor 中。契约变更若涉及 `platform/common` 的共享类型，需同步构建整个 reactor 才能通过编译；契约 jar 的本地安装与 CI 的 worktree 隔离仅解决二进制对比，不解决源码级同步构建的必要性。与 `PROJECT_DOCUMENTATION.md` 既有结论“api 与平台库仍同一 reactor，契约变更需同步构建”一致。
- **仅二进制兼容**：japicmp 检测二进制（`breakBuildOnBinaryIncompatibleModifications`）与源码兼容；语义/行为兼容（如字段含义变更、校验收紧）不在此门禁范围内，需配合契约测试与集成测试覆盖。
- **合成/桥接噪音已过滤**：`includeSynthetic=false` 过滤合成方法/桥方法；若仍出现噪音，应通过精确 `excludes` 而非关闭整个门禁处理。
