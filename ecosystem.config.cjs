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
      script: "./node_modules/vite/bin/vite.js",
      args: "--port 9002 --host 127.0.0.1",
      cwd: "./console",
      interpreter: "none",
      ...logConfig("9002"),
      env: {
        ...envFromFile,
        NODE_ENV: "development",
      },
    },
    {
      name: "ulticode-9003",
      script: "./node_modules/vite/bin/vite.js",
      args: "--port 9003 --host 127.0.0.1",
      cwd: "./management",
      interpreter: "none",
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
      // Arthas MCP 服务: 自动附加到 Spring Boot (9001) 进程，提供 MCP 端点
      // 启动依赖: ulticode-9001 必须先就绪（脚本内置等待逻辑）
      // MCP 端点: http://localhost:8563/mcp (Streamable HTTP)
      name: "ulticode-arthas",
      script: "./scripts/start-arthas.sh",
      interpreter: "bash",
      // Arthas 附加后 HTTP/MCP agent 运行在目标 JVM 内，启动进程本身会退出
      // 所以 autorestart=false，避免 PM2 反复重启
      autorestart: false,
      // 等待 Spring Boot 启动需要时间，给足启动窗口
      kill_timeout: 10000,
      wait_ready: false,
      ...logConfig("arthas"),
      env: {
        ...envFromFile,
      },
    },
  ],
};
