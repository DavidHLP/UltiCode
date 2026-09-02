# P2-APP-005 App 私有 module 深化 pilot

> status: COMPLETE (reversible pilot)
> evidence level: Repository Implemented
> depends_on: P2-APP-002, P2-APP-004
> gate: GATE-APP-SPLIT-CANDIDATE

## 结论

单进程内已证明 implementation locality。**保持当前 `backend-app-web` 单一部署**；不需要为 Problem/Moderation 新增进程或数据库。后续深化沿同一 pattern 继续将 Forum/Solution 抽为库形态模块，无需物理拆分。

## Pilot 选择

- 选择无跨域事务的单一 vertical slice：`ProblemAdministrationDomainService`。
- 理由：Problem 写路径与 Contest/Forum/Solution 无跨 Owner DB 事务；P2-APP-002 矩阵已标注该边界。

## 深化前后

**Before**：`ProblemServiceImpl` 直接在 `app-web` 处理 Problem 写入逻辑。

**After**：

- 私有模块 `services/app/modules/problem`（`backend-problem-domain`）提供：
  - `Problem` entity（纯注解，无 MyBatis 运行时）、`CreateProblemDTO/UpdateProblemDTO`
  - 纯领域服务 `ProblemAdministrationDomainService` 及其实现 `ProblemAdministrationDomainServiceImpl`
  - 端口 `ProblemDetailDomainPort`, `ProblemVersionDomainPort` 等
- `services/app/modules/moderation`（`backend-moderation-domain`）同理提供 `ContentModerationDomainService`
- `app-web` 通过 `AppDomainServiceConfig` 将领域服务注册为 Spring Bean，无 pass-through：

```java
// services/app/app-web/src/main/java/com/ulticode/app/config/AppDomainServiceConfig.java:42-47
@Bean
public ProblemAdministrationDomainService problemAdministrationDomainService(...) {
  return new ProblemAdministrationDomainServiceImpl(writePort, detailPort, versionPort, clock);
}
```

- Provider 仅跨一小接口调用：

```java
// services/app/app-web/src/main/java/com/ulticode/app/dubbo/provider/ProblemAdministrationProvider.java:34
private final ProblemAdministrationDomainService domainService;
```

- 旧 `app-web` 直写实现已删除；`ProblemServiceImpl` 仅作编排与 projection，委托领域服务。

## 满足验收

- [x] Caller 只跨一个小接口（Provider → DomainService / DomainService → WritePort）
- [x] Tests 通过该接口验证：`services/app/modules/problem/src/test`, `services/app/modules/moderation/src/test`, `ProblemAdministrationProviderTest`, `ContentModerationProviderTest`
- [x] HTTP/Dubbo contract 行为不变：Provider 仍暴露 `app-api` 契约，无新增 Remote DTO shape
- [x] 旧 pass-through 已删除：无重复 `ProblemServiceImpl` 写入实现

## Before/after 依赖图

```
Before: app-web: controller → serviceImpl (contains domain logic) → mapper → entity
After:  backend-problem-domain: entity + DTO + ProblemAdministrationDomainService (pure)
        backend-moderation-domain: ContentModerationDomainService (pure)
        app-web: controller → ProblemServiceImpl (orchestration) → DomainService → PortAdapter → mapper
               + Provider → DomainService (single seam)
```

`services/app/pom.xml:18-29` 父 POM 聚合 `backend-problem-domain`, `backend-contest-domain`, `backend-moderation-domain`, `backend-app-web`；`app-web` 编译依赖三者。`backend-judge-runtime` 不进入 Problem 领域链。

## 验证

```bash
(cd services && mise exec java@zulu-17.68.203.0 -- bash ./mvnw -pl app/modules/problem,app/modules/moderation,app/app-web -am test -B -Dtest='*DomainService*,*Provider*')
bash scripts/dev/architecture-contract-test.sh
```

当前 `P2-APP-002` 矩阵与本 pilot 共同证明：逻辑模块深化不产生物理部署单元；`GATE-APP-SPLIT-CANDIDATE` 仍为 No-Go。

## 回退

单提交可回退：删除 `AppDomainServiceConfig` 注册、将实现移回 `app-web`，POM 聚合保持。未创建新进程/数据源，不影响回滚。
