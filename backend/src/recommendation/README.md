# Recommendation Module

基于 on Nacos + Dubbo 分布式架构的推荐服务模块，集成 Java recommendation 微服务到 NestJS 后端。

## 架构

```
┌────────────────────┐                         ┌─────────────────────┐
│                    │                         │                         │   │
│  Nacos Server      │◄────── Nacos discovery ───►│  NestJS Backend    │
│  (28848)           │                         │ recommend-web (28081) │
│                    │                         │                         │   │
│                    ▼                         ▼                         ▔─┘
│                    │ recommend-provider │◄──── Dubbo RPC ───►│                 │
│                    │         (20881)           │
└────────────────────────────────────────────────────┘
```

Nacos 用于服务发现和注册中心，Dubbo 用于 RPC 通信。

Recommend-web 作为 REST API 确保层
NestJS Backend 通过 HTTP 调用 recommend-web

## 文件结构

```
backend/src/recommendation/
├── recommendation.module.ts      # Module definition
├── recommendation.controller.ts   # REST API endpoints
├── services/
│   ├── nacos.service.ts           # Nacos 服务发现
│   └── recommendation.service.ts  # Main recommendation service
├── dto/
│   └── recommend.dto.ts           # Request/Response DTOs
├── interfaces/
│   ├── recommendation.interface.ts              # TypeScript interfaces
│   └── recommendation-module-options.interface.ts  # Module options
└── index.ts                         # Barrel exports
```

## API Endpoints

| Method | Description |
|--------|-------------|
| `POST /recommendations` | 获取个性化推荐（支持认证） |
| `GET /recommendations/daily` | 获取每日练习推荐 |
| `GET /recommendations/similar/:problemId` | 获取相似题目推荐 |
| `GET /recommendations/weak-points` | 获取弱点强化推荐 |
| `GET /recommendations/challenge` | 获取挑战模式推荐 |
| `GET /recommendations/health` | 健康检查 |

## 环境变量
```bash
# Nacos Configuration
NACOS_SERVER_ADDR=localhost:28848
NACOS_NAMESPACE=public
NACOS_GROUP=DEFAULT_GROUP

# Recommendation Service Configuration
RECOMMENDATION_ENABLED=true
RECOMMENDATION_SERVICE_NAME=recommend-web
RECOMMENDATION_TIMEOUT=5000
RECOMMENDATION_FALLBACK_URL=http://localhost:28081
```

## 使用示例

```typescript
// 在 NestJS 模块中导入
import { RecommendationModule } from './recommendation/recommendation.module';

// 在控制器中使用
@Controller('problems')
export class ProblemsController {
  constructor(
    private readonly recommendationService: RecommendationService,
  ) {}

  @Get('recommendations')
  async getDailyRecommendations() {
    return this.recommendationService.getDailyRecommendations(userId, 10);
  }
}
```
## 构建和运行

### 1. 启动 Nacos
```bash
docker compose up -d
```

### 2. 启动 NestJS 后端
```bash
cd backend
pnpm install
```

### 3. 启动 Recommendation Java 服务
```bash
# 在 recommendation 目录下构建 Java 项目
cd recommendation
mvn clean package
mvn package -DskipTests

```

### 4. 运行服务检查
```bash
./shell/status.sh
```
## 故障排除

如果遇到问题，请检查：
1. **Nacos 连接**: 确保 Nacos 容器正在运行 (`docker compose ps | grep ulticode-nacos`)
2. **推荐服务连接**: 使用 `GET /recommendations/health` 端点检查

3. **环境变量**: 确保 `.env` 中的配置正确

## 后续优化建议

1. **添加缓存**: 可以使用 Redis 缓存推荐结果
2. **添加熔断**: 实现服务降级保护
3. **添加监控**: 集成 Prometheus/Grafana 监控
4. **添加单元测试**: 完善测试覆盖率
