// 共用日志配置：显式锚定到 /tmp/，避免 PM2 默认切到 ~/.pm2/logs/ 造成路径分裂
const LOG_OUT = (name) => `/tmp/ulticode-${name}-out.log`;
const LOG_ERR = (name) => `/tmp/ulticode-${name}-error.log`;
const logConfig = (name) => ({
  out_file: LOG_OUT(name),
  error_file: LOG_ERR(name),
  merge_logs: true,
  log_date_format: "YYYY-MM-DD HH:mm:ss",
  // 单文件 20MB，自动保留 5 个历史文件
  max_size: "20M",
  // 写满后保留 N 个轮转文件
  max_files: 5,
});

// 从项目根目录的 .env 读取 KEY=VALUE 配置，作为 PM2 环境变量的"基线"。
// 这样 .env 是唯一的真实来源（single source of truth），避免在 ecosystem.config.cjs
// 中重复硬编码 DB_PASSWORD / REDIS_PASSWORD 等敏感信息，防止占位符漂移。
// 任何显式声明的 env.* 仍会覆盖 .env 中的同名变量（保持原有覆盖语义）。
const fs = require("fs");
const path = require("path");

const ENV_FILE = path.resolve(__dirname, ".env");

function parseEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return {};
  const content = fs.readFileSync(filePath, "utf8");
  const result = {};
  for (const rawLine of content.split("\n")) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const eqIdx = line.indexOf("=");
    if (eqIdx === -1) continue;
    const key = line.slice(0, eqIdx).trim();
    let value = line.slice(eqIdx + 1).trim();
    // 去掉包裹的双引号或单引号
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (key) result[key] = value;
  }
  return result;
}

const envFromFile = parseEnvFile(ENV_FILE);

// 应用启动时输出关键检查（启动后通过 `pm2 logs ulticode-9001` 看不到，所以只在启动阶段打印一次）
const maskedKeys = [
  "DB_PASSWORD",
  "REDIS_PASSWORD",
  "JWT_SECRET",
  "MYSQL_ROOT_PASSWORD",
];
const maskedSummary = maskedKeys
  .filter((k) => envFromFile[k])
  .map((k) => `${k}=${"*".repeat(Math.min(8, String(envFromFile[k]).length))}`)
  .join(", ");
if (maskedSummary) {
  console.log(
    `[ecosystem.config] loaded ${Object.keys(envFromFile).length} vars from .env (${maskedSummary})`,
  );
}

module.exports = {
  apps: [
    {
      name: "ulticode-9001",
      // 先 clean install 编译，再启动 Spring Boot，确保代码干净
      // interpreter: "none" 让 PM2 直接 exec PATH 中的 bash (Git Bash on Windows / bash on Linux)
      // 注意: 不要用 interpreter: "bash" — PM2 会把 bash 路径和 args 拼成绝对路径调用，引发 Windows "cannot execute binary file"
      script: "bash",
      args: "-c \"cd backend-spring && ./mvnw clean install -DskipTests && ./mvnw spring-boot:run -Dmaven.test.skip=true\"",
      cwd: ".",
      interpreter: "none",
      instance_var: "INSTANCE_ID",
      ...logConfig("9001"),
      // env 完全覆盖父进程环境：先铺 .env 基线，再用显式值覆盖非敏感默认值。
      // 敏感字段（密码/密钥）必须来自 .env，绝不在此处硬编码。
      env: {
        ...envFromFile,
        // 应用配置
        SPRING_PROFILES_ACTIVE: "dev",
        // CORS
        CORS_ALLOWED_ORIGINS: "http://localhost:9002,http://localhost:9003",
      },
    },
    {
      // Vite 默认绑定到 "localhost"，在 Linux 上只解析为 IPv6 ::1，
      // 导致 up.sh 末尾用 127.0.0.1 的就绪检查失败。显式绑定 127.0.0.1
      // 既能通过校验，又符合项目"只 bind loopback"的安全策略。
      name: "ulticode-9002",
      script: "node",
      args: "./node_modules/vite/bin/vite.js --port 9002 --host 127.0.0.1",
      cwd: "./console",
      ...logConfig("9002"),
      env: {
        ...envFromFile,
        NODE_ENV: "development",
      },
    },
    {
      name: "ulticode-9003",
      script: "node",
      args: "./node_modules/vite/bin/vite.js --port 9003 --host 127.0.0.1",
      cwd: "./management",
      ...logConfig("9003"),
      env: {
        ...envFromFile,
        NODE_ENV: "development",
      },
    },
    {
      // 数据库迁移服务: 一次性跑 Flyway migrate, 成功即退出 (exit 0)
      // 启动顺序: 先 `pm2 start ulticode-init-db` 等 stopped, 再 `pm2 start ulticode-9001`
      name: "ulticode-init-db",
      script: "./scripts/dev/migrate.sh",
      args: "migrate",
      cwd: ".",
      interpreter: "bash",
      autorestart: false,
      ...logConfig("init-db"),
      env: {
        ...envFromFile,
      },
    },
    {
      // Arthas MCP 自愈 wrapper — 跟随 PM2 启动
      //   - 自愈 loop: 端口 8563 死了就重 attach (修复 pm2 restart 9001 后断连)
      //   - 也可由 Claude Code SessionStart hook 拉起 (见 .claude/settings.json)
      //   - 两路互斥: 任何一路发现 8563 已监听都会跳过, 不会重复 attach
      //   - 端口: 8563 (HTTP MCP 端点 /mcp, 协议 STATELESS — 项目级 pin, 见 infrastructure/arthas/arthas.properties)
      // 启动顺序: ulticode-9001 之后 (wrapper 等 Spring Boot 就绪再 attach)
      name: "ulticode-arthas",
      script: "./scripts/start-arthas.sh",
      cwd: ".",
      interpreter: "bash",
      // autorestart 开启: wrapper 进程崩溃时 PM2 自动拉起 (例如 attach 连续失败)
      autorestart: true,
      // 跟随 PM2 自启 (下次机器重启后随 PM2 一起)
      autostart: true,
      // 不限制重启次数 (自愈 loop 本身就预期长跑; PM2 不接受 -1, 留 9999)
      max_restarts: 9999,
      ...logConfig("arthas"),
      env: {
        ...envFromFile,
        // 标记本进程由 PM2 拉起, 供 arthas-session-start.sh 互斥判断
        ULTICODE_ARTHAS_LAUNCHER: "pm2",
      },
    },
  ],
};
