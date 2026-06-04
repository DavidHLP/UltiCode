# UltiCode `.claude/rules/` 规则集

本目录按 [Claude Code 官方规范](https://code.claude.com/docs/zh-CN/memory#%E4%BD%BF%E7%94%A8-claude/rules/-%E7%BB%84%E7%BB%87%E8%A7%84%E5%88%99) 组织。

## 目录结构

```
.claude/rules/
├── README.md                        # 本文件
├── backend/                         # Spring Boot 后端规则
│   ├── 01-java-programming.md
│   ├── 02-java-exception-logging.md
│   ├── 03-java-unit-testing.md
│   ├── 04-java-security.md
│   ├── 05-mysql-database.md
│   ├── 06-java-project-structure.md
│   ├── 07-java-design.md
│   ├── 08-java-code-review-checklist.md
│   └── code-review-java-spring.md   # 项目特定补充
├── frontend/                        # Vue 3 + TypeScript 前端规则
│   ├── 01-vue3-typescript.md
│   ├── 02-vue-router-pinia.md
│   └── 03-vitest-testing.md
└── database/                        # 数据库与迁移规则
    ├── 01-flyway-migrations.md
    └── 02-mysql-coding.md
```

## 加载行为

Claude Code 通过 YAML frontmatter 决定规则的加载时机：

| 规则类型 | 加载时机 | 上下文开销 |
|---|---|---|
| 不带 `paths:` | 每次会话启动时无条件加载 | 高 |
| 带 `paths:` | 仅当 Claude 处理与 glob 匹配的文件时加载 | 低（按需） |

本目录的规则**全部带 `paths:`**，按需加载，节省上下文。

## Frontmatter 标准格式

```yaml
---
paths:
  - "**/*.java"
  - "**/*Mapper.java"
description: 一句话说明本规则用途
source: 来源手册（可选）
version: 来源版本（可选）
---
```

- `paths` 支持 glob 模式（`**`、`*`、`{}` 等），多个模式分行写。
- `description` 是必填项，Claude 在加载决策时使用。
- `source` / `version` 仅用于溯源，工具不解析。

## 规则执行原则

- **【强制】**：Agent 必须检查并优先修复
- **【推荐】**：默认采用，除非项目已有明确约定
- **【参考】**：用于设计判断、风险提示和优化建议

## 添加新规则

1. 选择合适的子目录（backend/frontend/database）
2. 文件名格式：`<NN>-<主题>.md`，数字前缀表达优先级/章节
3. frontmatter 必填 `description`；如果只对特定路径生效则必填 `paths`
4. 规则内容保持精简（< 200 行）；超大规则用 [paths frontmatter 限定范围](https://code.claude.com/docs/zh-CN/memory#path-specific-rules)
