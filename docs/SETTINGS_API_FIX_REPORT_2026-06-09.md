# `/admin/settings` API 修复回归报告

- 生成日期: 2026-06-09
- 对照: `docs/SETTINGS_API_TEST_REPORT_2026-06-09.md`（初测报告，5 个 P0/P1 bug）
- 分支: `fix/admin-settings-api-bugs`
- 服务: ulticode-9001 重启于 PID 564417
- 实施步骤: 8/8 完成

---

## 1. 结论

| 项 | 结果 |
|---|---|
| 是否通过 | ✅ **通过** |
| 5 个 P0/P1 bug | 全部修复并通过线上 curl + Testcontainers IT 双重验证 |
| 单元测试 | 19/19 绿 (`AdminSettingsControllerTest`) |
| 集成测试 | 9/9 绿 (`SystemSettingsServiceImplIT`，含 3 个 P0 回归断言) |
| 14 个 happy path | 14/14 HTTP 200 |
| 6 个负面用例 | 行为符合预期（见 §3） |

---

## 2. 修复内容总览

| 步骤 | 动作 | 涉及文件 |
|---|---|---|
| 1 | 提取 8 个 DTO 到 `dto/settings/` 包 | 新增 8 个文件 |
| 2 | 新增 `SystemSetting` Entity + Mapper | `entity/SystemSetting.java`, `mapper/SystemSettingMapper.java` |
| 3 | 新增 `SystemSettingsService` + Impl | `service/SystemSettingsService.java`, `service/impl/SystemSettingsServiceImpl.java`（含 SMTP 密码脱敏、维护模式单源）|
| 4 | 重构 Controller 调 service | `controller/AdminSettingsController.java`（从 387 行减到 ~150 行；删 7 个内部类；PATCH/POST 加 `@Valid`）|
| 5 | ErrorCode 加 4 个 SETTING_* 错误码 | `common/exception/ErrorCode.java`（200001-200004）|
| 6 | GlobalExceptionHandler 补 2 个 400 handler | `common/exception/GlobalExceptionHandler.java`（HttpMessageNotReadableException + MethodArgumentTypeMismatchException）|
| 7 | 单元 + 集成测试 | `controller/AdminSettingsControllerTest.java`（19 个测试），`service/impl/SystemSettingsServiceImplIT.java`（9 个 IT）|

---

## 3. 14 个端点 happy path（重启后实测）

| # | 方法 | 路径 | HTTP | 备注 |
|---|------|------|------|------|
| 1 | GET | `/admin/settings` | **200** | 默认值 |
| 2 | GET | `/admin/settings/all` | **200** | 28 个字段全部返回（§5.5 修复）|
| 3 | GET | `/admin/settings/email` | **200** | 密码字段返回 `***`（§8.1 修复）|
| 4 | GET | `/admin/settings/rate-limits` | **200** | 默认 100/10/5/20 |
| 5 | GET | `/admin/settings/uploads` | **200** | 默认 10MB/6 types/5 files |
| 6 | GET | `/admin/settings/features` | **200** | 8 个开关 |
| 7 | PATCH | `/admin/settings` | **200** | 持久化生效（§5.1 修复）|
| 8 | PATCH | `/admin/settings/email` | **200** | 持久化生效 + 脱敏 |
| 9 | PATCH | `/admin/settings/rate-limits` | **200** | 持久化生效 |
| 10 | PATCH | `/admin/settings/uploads` | **200** | 持久化生效 |
| 11 | PATCH | `/admin/settings/features` | **200** | 持久化生效 |
| 12 | POST | `/admin/settings/cache/clear` | **200** | 返回 `{clearedScopes:["settings"], timestamp:"..."}` |
| 13 | POST | `/admin/settings/maintenance` | **200** | 状态同步到 `general`（§5.2 修复）|
| 14 | POST | `/admin/settings/reset` | **200** | 5 行全删，下次 GET 重建默认 |

---

## 4. 6 个负面用例对比

| # | 场景 | 原报告 | 修复后 | 评估 |
|---|------|--------|--------|------|
| A | PATCH features 不带 CSRF | 403 | **403** | ✅ 不变 |
| B | GET settings 无 cookie | 401 | **401** | ✅ 不变 |
| **C** | PATCH features 非法 JSON `{not_valid_json` | 500 `Unknown error` | **400** `Malformed request body: Unexpected character...` | ✅ **§5.3 修复** |
| **D** | PATCH features `featureContest: "yes"` | 500 `Unknown error` | **400** `Malformed request body: ...` | ✅ **§5.3 修复** |
| E | PATCH features 空 body `{}` | 200 回显全 false | **200** 持久化全 false | ⚠️ 见 §6 |
| **F** | POST maintenance 空 body `{}` | 200 等价 disable | **400** `enabled is required` | ✅ **§5.4 修复** |

---

## 5. 三个 P0 bug 修复证据（在线 curl 复现）

### 5.1 持久化（§5.1）

```bash
# 1) PATCH rate-limits 到 999
$ curl -X PATCH .../admin/settings/rate-limits -d '{"rateLimitApi":"999",...}'
{"code":0,"data":{"rateLimitApi":"999",...}} HTTP=200

# 2) 立即 GET — 看到 999（不是 100）
$ curl .../admin/settings/rate-limits
{"code":0,"data":{"rateLimitApi":"999","rateLimitSubmission":"99","rateLimitAuth":"9","rateLimitUpload":"99"},...} HTTP=200
```

✅ PATCH 真正写入 `system_settings` 表。

### 5.2 维护模式单源（§5.2）

```bash
# 1) POST maintenance 开启
$ curl -X POST .../admin/settings/maintenance -d '{"enabled":true,"message":"verify maint"}'
{"code":0,"data":{"maintenanceMode":true,"message":"verify maint"}} HTTP=200

# 2) GET general — 看到 maintenanceMode=true
$ curl .../admin/settings
{"code":0,"data":{"maintenanceMode":true,"maintenanceMessage":"verify maint","enableRegistrations":false,"siteName":"R2Verify",...}} HTTP=200
```

✅ 维护模式与 `general` 共享同一行。

### 5.3 SMTP 密码脱敏（§8.1）

```bash
# 1) PATCH 真实密码
$ curl -X PATCH .../admin/settings/email -d '{"smtpPassword":"secret-verify"}'
{"code":0,"data":{"smtpPassword":"***","smtpSecure":false}} HTTP=200

# 2) GET — 密码字段始终是 ***
$ curl .../admin/settings/email
{"code":0,"data":{"smtpPassword":"***",...}} HTTP=200
```

✅ 密码永不以明文回显。

### 5.4 Reset 行为

```bash
$ curl -X POST .../admin/settings/reset
{"code":0,"data":{...defaults...}} HTTP=200

# 之后 GET r-l → 100/10/5/20（删 5 行后默认重建）
$ curl .../admin/settings/rate-limits
{"code":0,"data":{"rateLimitApi":"100",...}} HTTP=200
```

---

## 6. 已知遗留 / 后续优化

| # | 级别 | 项 | 说明 |
|---|------|----|------|
| 1 | 中 | **NEG-E 仍 200** | PATCH features 空 body 仍返回 200 并持久化。`FeatureTogglesVO` 没有 `@NotNull` 字段，原意是允许只 PATCH 部分字段。但当前实现是**全替换**语义，`{}` 会把 8 个开关全置 false。建议在管理前端始终传完整 8 字段；或后续加 `PATCH` 子路径 `/features/contest` 走 JSON Merge Patch 语义。 |
| 2 | 低 | **JaCoCo 阈值 5%** | pom.xml JaCoCo 阈值是 5%/2%，远低于 80% 项目规则。本次新增 19+9=28 个测试，未触动 pom 阈值；建议独立 PR 修复。 |
| 3 | 低 | **`description` 列未使用** | 实体 `SystemSetting.description` 字段保留但 service 写入时为 null（无 UI 入口）。如有需要可后续加 admin UI 标签。 |
| 4 | 低 | **PATCH /admin/settings body 类型变更** | 原 API 接收 `AllSettingsVO`（28 字段），现在接收 `GeneralSettingsVO`（6 字段）。这是符合 REST 语义的（父路径管"通用"设置），但**前端如果有调用此端点需要适配**。如果需要保留 28 字段版本，可调 `PATCH /admin/settings/all`。 |
| 5 | 低 | **`cache/clear` 是占位** | 当前返回 `{clearedScopes, timestamp}`，没真实清理任何缓存（settings 未走 Redis 缓存）。未来引入缓存时再实现。 |

---

## 7. 持久化方案

- **表**: `system_settings`（已存在，无新 migration）
- **Schema**: `key varchar(50) PK`, `value text`, `description varchar(255)`, `updated_at datetime(3)`
- **存储策略**: per-category JSON value，5 行（`general`/`email`/`rate-limits`/`uploads`/`features`）
- **保留字处理**: `key` 是 MySQL 保留字，entity 用 `@TableId("`key`")` 强制反引号
- **JSON 序列化**: Jackson `ObjectMapper` 注入；失败时回退默认 + warn 日志
- **SMTP 密码**: GET 脱敏 + PATCH mask 保留原值（`updateEmailSettings` 内读旧值覆盖）

---

## 8. 测试覆盖矩阵

| 区域 | 类型 | 文件 | 数量 | 覆盖的 P0 回归 |
|---|---|---|---|---|
| Controller | WebMvcTest | `AdminSettingsControllerTest` | 19 | 14 happy path + 3 负面 + 1 routing 验证 |
| Service | Testcontainers IT | `SystemSettingsServiceImplIT` | 9 | §5.1 持久化、§5.2 维护模式、§8.1 SMTP 脱敏 |

**Testcontainers 覆盖路径**:
- `DefaultSeeding` (1) — 空表 → 默认值
- `Persistence` (3) — 持久化、upsert 不冲突、reset 删行
- `MaintenanceConsistency` (2) — toggle true/false 同步到 general
- `SmtpPasswordMasking` (3) — 设真实值后 GET mask、mask 不覆盖原值、新值覆盖旧值

---

## 9. 改动文件清单

### 新增（12 个）

```
backend-spring/src/main/java/com/ulticode/modules/admin/
├── dto/settings/
│   ├── GeneralSettingsVO.java
│   ├── EmailSettingsVO.java
│   ├── RateLimitSettingsVO.java
│   ├── UploadSettingsVO.java
│   ├── FeatureTogglesVO.java
│   ├── AllSettingsVO.java
│   ├── MaintenanceModeRequest.java
│   └── MaintenanceModeVO.java
├── entity/SystemSetting.java
├── mapper/SystemSettingMapper.java
├── service/SystemSettingsService.java
└── service/impl/SystemSettingsServiceImpl.java

backend-spring/src/test/java/com/ulticode/modules/admin/
├── controller/AdminSettingsControllerTest.java
└── service/impl/SystemSettingsServiceImplIT.java
```

### 修改（3 个）

```
backend-spring/src/main/java/com/ulticode/modules/admin/
└── controller/AdminSettingsController.java            # 387 → ~150 行

backend-spring/src/main/java/com/ulticode/common/exception/
├── ErrorCode.java                                       # +4 个 SETTING_*
└── GlobalExceptionHandler.java                          # +2 个 400 handler
```

---

## 10. 验证命令

```bash
cd backend-spring

# 单元测试
./mvnw test -B -Dtest=AdminSettingsControllerTest
# → Tests run: 19, Failures: 0, Errors: 0, Skipped: 0

# 集成测试（Testcontainers MySQL）
./mvnw test -B -Dtest=SystemSettingsServiceImplIT
# → Tests run: 9, Failures: 0, Errors: 0, Skipped: 0

# 打包
./mvnw package -B -DskipTests
# → BUILD SUCCESS

# 重启
pm2 restart ulticode-9001
```

---

## 11. 风险评估

| 风险 | 状态 | 缓解 |
|---|---|---|
| **前端调用 `PATCH /admin/settings` 用 AllSettingsVO body** | 中 | 父路径现已接 `GeneralSettingsVO`；如前端用全 28 字段，Jackson 会忽略多余字段，不会报错但只持久化 6 个 |
| **数据库 `key` 列 reserved word** | 已处理 | `@TableId("`key`")` 强制反引号；IT 通过 |
| **并发 PATCH 同 key** | 低 | MyBatis-Plus `insertOrUpdate` 是原子 upsert；如有更高要求可后续加 `@Version` |
| **SMTP 密码 mask 与真实值误判** | 已处理 | mask 是常量 `"***"`；其他任意字符串视为真实密码 |
| **Reset 误操作** | 中 | `POST /reset` 不可逆（删 5 行）；建议前端加二次确认弹窗 |

---

## 12. 下一步

- [ ] Code review: `/code-review`
- [ ] 提交: `/ecc:prp-commit`
- [ ] 推 PR: `/ecc:prp-pr`
- [ ] 后续 Issue: 加 `PATCH /admin/settings/features/{flag}` 子路径（解决 NEG-E 遗留）
- [ ] 独立 PR: JaCoCo 阈值 5% → 80%

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
