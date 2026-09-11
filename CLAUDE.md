# Claude Code compatibility entry

@AGENTS.md

Follow the imported root guide and the nearest nested `AGENTS.md` for every task in this repository. They are the single source of truth for project rules.

## MySQL 容器化操作-字符集

`ulticode-mysql` 容器默认 `character_set_client=latin1`，手工 `docker exec mysql` 需显式指定 `utf8mb4`，否则中文会被双重编码（显示为 `æžå¨œ`）。

```bash
# 正确
set -a; source .env; set +a
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME"
```

后端 JDBC 已含 `useUnicode=true&characterEncoding=UTF-8`，仅手工 `docker exec` 路径需注意。

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
