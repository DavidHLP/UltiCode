# i18n 设计规范

Management 前端的国际化设计约定与操作指南。

## 命名规范

| 场景 | 规范 | 示例 |
|------|------|------|
| 一般 key | camelCase | `totalUsers`, `createProblem` |
| 枚举值 | ALL_CAPS | `LOGIN`, `CREATE`, `EDUCATIONAL` |
| API 字段映射 | 匹配后端字段名 | `joined_at`, `ip_address`（仅限 `table.columnNames`） |

### API 字段映射例外

`table.columnNames` 中的 snake_case key（如 `joined_at`）匹配后端 API 返回的字段名。这些 key 通过 `DataTable.vue` 的 `resolveColumnName(columnId)` 动态引用，**不能改为 camelCase**。

类似的，`moderation.entityTypes` 中部分值（如 `forum_post`）匹配后端枚举值，保留 snake_case。

## 动态 key 规范

所有动态 key 拼接**必须**提供 fallback 值：

```ts
// ✅ 正确 — key 缺失时显示可读文本
t(`problems.difficulty.${difficulty}`, difficulty)

// ❌ 错误 — key 缺失时显示原始 key 字符串
t(`problems.difficulty.${difficulty}`)
```

vue-i18n 的 `t(key, fallback)` 在 key 不存在时返回 fallback 参数值。

## 检查工具

| 工具 | 命令 | 用途 |
|------|------|------|
| 翻译完整性检查 | `pnpm check:i18n` | locale 间一致性 + 代码→locale 覆盖率 |
| JSON 报告 | `pnpm check:i18n --json` | CI 消费的结构化输出 |
| i18n 测试 | `pnpm vitest run src/i18n/__tests__/` | 命名规范 + 覆盖率回归测试 |
| 表格列名一致性 | `pnpm vitest run src/i18n/__tests__/table-keys.spec.ts` | zh-CN/en-US columnNames 一致性 |

CI 在 management 代码变更时自动运行上述检查。

## 新增 key 流程

1. 在 `src/i18n/locales/zh-CN/modules/xxx.ts` 添加 key
2. 在 `src/i18n/locales/en-US/modules/xxx.ts` 添加对应英文翻译
3. 运行 `pnpm check:i18n` 验证无缺失
4. 运行 `pnpm test` 确认无回归

## 目录结构

```
src/i18n/
  index.ts              — i18n 实例、loadLocale、setLocale、t
  types.ts              — SupportedLocale、MessageSchema、LocaleConfig
  utils.ts              — formatDateByLocale、formatNumberByLocale、formatCompactNumber
  check.ts              — 翻译完整性检查脚本
  utils/storage.ts      — locale 持久化（localStorage → sessionStorage → memory）
  __tests__/
    table-keys.spec.ts      — 表格列名 key 一致性
    naming-convention.spec.ts — 命名规范检查
    i18n-coverage.spec.ts   — 代码→locale 覆盖率
  locales/
    zh-CN/modules/      — 25 个 .ts 模块
    en-US/modules/      — 25 个 .ts 模块
```
