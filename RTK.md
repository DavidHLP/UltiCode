# RTK 项目配置 - UltiCode

> RTK (Rust Token Killer) 配置 - 针对 UltiCode 项目优化
> 目标: 最大化 LLM token 节省率，同时保持开发效率

## 项目技术栈

- **Frontend**: Vue 3 + Vite + Tailwind CSS v4 + TypeScript
- **Backend**: Spring Boot 3.2.5 + Java 17 + MyBatis-Plus
- **Database**: MySQL + Redis
- **Build**: pnpm + Maven
- **Test**: Vitest + JUnit

## 推荐命令

### 文件查看
```bash
# 查看文件（自动过滤注释和空行）
rtk read src/main/java/com/ulticode/UserService.java

# 查看文件并压缩方法体（节省 60-90%）
rtk read src/main/java/com/ulticode/UserService.java -l aggressive

# 查看配置文件
rtk read application.yml
```

### Git 操作
```bash
# 查看状态（紧凑格式）
rtk git status

# 查看日志（单行格式）
rtk git log --oneline -20

# 查看 diff
rtk git diff

# 查看特定提交
rtk git show HEAD
```

### 搜索
```bash
# 搜索代码（分组显示，每文件最多15条）
rtk grep "class User" src/

# 查找文件
rtk find src -name "*.vue"
```

### 构建和测试
```bash
# Maven 构建（仅显示错误和摘要）
rtk mvn clean install -DskipTests

# 运行测试（仅显示失败测试）
rtk mvn test

# pnpm 操作
rtk pnpm install
rtk pnpm run build
rtk pnpm run test

# Vitest（节省 90-99%）
rtk vitest run
```

### 项目管理
```bash
# 列出文件
rtk ls -la

# 查看目录结构
rtk tree src/
```

## 环境变量

```bash
# 临时禁用 RTK（调试时使用）
RTK_DISABLED=1 mvn clean install

# 禁用遥测
export RTK_TELEMETRY_DISABLED=1
```

## 配置说明

全局配置: `~/.config/rtk/config.toml`

关键优化:
- passthrough_max_chars = 300（降低透传阈值，更多输出被优化）
- exclude_commands 中移除了 mvn install 和 gradle build（这些长输出可被RTK大幅优化）
- Java 优化: 移除注释、压缩方法体
- Vue 优化: 压缩 script 部分

## 节省统计

```bash
# 查看节省统计
rtk gain

# 查看历史命令
rtk gain --history

# 发现错过的节省机会
rtk discover
```

## 注意事项

1. **Hook 已安装**: 全局 hook 会自动重写 bash 命令，无需手动添加 `rtk` 前缀
2. **失败时查看完整输出**: 命令失败时，RTK 会保存完整输出到 `~/.local/share/rtk/tee/`
3. **不要滥用 RTK_DISABLED**: 仅在确实需要完整输出时使用，否则会降低节省率
4. **重启 OpenCode**: 安装 hook 后需要重启 OpenCode 以生效
