# Java Agent Rules

由 `Java开发手册_华山版_v1.5.0.md` 拆分生成，来源版本：1.5.0，更新日期：2019.06.19。

## 文件说明

| 文件 | 用途 |
|---|---|
| `01-programming-rules.md` | Java 编程规约：命名、常量、格式、OOP、集合、并发、控制语句、注释等 |
| `02-exception-log-rules.md` | 异常处理与日志规约 |
| `03-unit-test-rules.md` | 单元测试规约 |
| `04-security-rules.md` | 安全规约 |
| `05-mysql-rules.md` | MySQL 建表、索引、SQL、ORM 映射规约 |
| `06-project-structure-rules.md` | 工程结构、应用分层、依赖和服务器规约 |
| `07-design-rules.md` | 设计规约 |
| `08-code-review-checklist.md` | 给 Agent 执行 Code Review 的总检查清单 |

## 推荐使用方式

将这些文件放入你的 Agent/IDE 规则目录中，例如：

```text
.project-rules/
  01-programming-rules.md
  02-exception-log-rules.md
  ...
```

如果工具支持 `globs` 或 `alwaysApply`，可读取每个文件顶部的 YAML 元信息；如果不支持，也可以直接作为普通 Markdown 规则文档使用。

## 规则执行原则

- `【强制】`：Agent 必须检查并优先修复。
- `【推荐】`：默认采用，除非项目已有明确约定。
- `【参考】`：用于设计判断、风险提示和优化建议。
