---
name: ulticode-dev-ops
description: UltiCode development and operations reference covering PM2 process management, Docker containers, Arthas diagnostics, backend builds, and frontend dev commands. Trigger when managing services, restarting processes, or diagnosing runtime issues.
---

# UltiCode 开发运维操作

## PM2 进程管理

```bash
./scripts/dev/init-env.sh        # 首次生成随机本机环境
./scripts/dev/up.sh              # 完整启动
./scripts/dev/up.sh --skip-install # 依赖未变化时快速启动
pm2 start all                    # 后续启动
pm2 restart ulticode-9001        # 重启后端
pm2 restart ulticode-9002        # 重启 Console 前端
pm2 restart ulticode-9003        # 重启 Management 前端
pm2 stop all                     # 停止所有
pm2 logs                         # 查看日志
pm2 logs ulticode-9001           # 查看后端日志
pm2 status                       # 进程状态
pm2 monit                        # 实时监控
pm2 save                         # 保存进程列表
pm2 resurrect                    # 恢复进程列表
```

## 服务端口

| 端口 | 名称 | 类型 |
|------|------|------|
| 9001 | ulticode-9001 | Spring Boot 后端 |
| 9002 | ulticode-9002 | Console 前端 (Vite) |
| 9003 | ulticode-9003 | Management 前端 (Vite) |
| 23306 | ulticode-mysql | MySQL 9.1 |
| 26379 | ulticode-redis | Redis 7 |
| 28848 | ulticode-nacos | Nacos 2.3.2 |

## Docker 操作

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml up -d
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml down
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml logs mysql
```

禁止在命令中硬编码数据库密码；从根 `.env` 加载或使用开发脚本。

## Arthas 运行时诊断

项目根目录有 `arthas-boot.jar` (4.1.9)：

```bash
# 启动（选择 Java 进程）
java -jar arthas-boot.jar

# 附加到指定进程
java -jar arthas-boot.jar <pid>

# 常用命令
dashboard              # 系统总览
thread -n 5            # 最忙 5 个线程
jad <class>            # 反编译类
watch <class> <method> <expr>  # 观察方法调用
trace <class> <method>         # 方法调用路径
stack <class> <method>         # 调用堆栈
ognl '<expr>'                  # 执行 OGNL 表达式
```

## 后端构建

```bash
cd backend-spring
./mvnw spring-boot:run -Dmaven.test.skip=true  # 开发运行
./mvnw package -DskipTests                       # 构建
./mvnw compile                                    # 仅编译
./mvnw test                                       # 单元测试
./mvnw -Dtest='*IT' test                          # 集成测试
./mvnw verify                                     # 测试 + JaCoCo 校验
```

## 前端开发

```bash
cd console && pnpm dev      # Console 开发服务器
cd management && pnpm dev   # Management 开发服务器
cd console && pnpm build    # Console 构建
cd management && pnpm build # Management 构建
```

## 统一测试

```bash
./scripts/dev/test.sh quick
./scripts/dev/test.sh full
./scripts/dev/test.sh integration
```

## 日志排查

1. PM2 日志：`pm2 logs ulticode-9001`
2. Docker 日志：`docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml logs mysql`
3. Arthas：`trace` + `watch` 定位慢方法
4. 后端日志级别：生产环境 INFO，开发可开启 DEBUG
