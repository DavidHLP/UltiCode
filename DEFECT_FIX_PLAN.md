# UltiCode-Public-Next 缺陷修复计划

**创建日期**: 2026-02-08
**基于**: DEFECT_ANALYSIS_REPORT.md
**状态**: 待执行

---

## 修复阶段概览

| 阶段 | 优先级 | 时间范围 | 缺陷数量 | 预估总工时 |
|------|--------|----------|----------|------------|
| Phase 1 | P0 Critical | 1周内 | 4 | 4h |
| Phase 2 | P1 High | 2周内 | 6 | 25h |
| Phase 3 | P2 Medium | 1个月内 | 20 | 30h |
| Phase 4 | P3 Low | 长期 | 24 | 持续改进 |

---

## Phase 1: Critical 紧急修复 (P0 - 1周内)

### 1.1 启用 Docker 代码执行沙箱

**缺陷编号**: #1
**位置**: `backend/.env` (Line 19)
**预估工时**: 2h

#### 问题分析
当前 `JUDGE_CONTAINER_ENABLED=false`，使用不安全的 Node.js vm 模块执行用户代码，存在沙箱逃逸风险。

#### 修复步骤

```bash
# Step 1: 确认 Docker 环境已安装
docker --version
docker-compose --version

# Step 2: 构建 judge 容器镜像
cd backend
docker build -t ulticode-judge:latest -f judge/Dockerfile .

# Step 3: 验证镜像构建成功
docker images | grep ulticode-judge

# Step 4: 修改环境配置
```

**文件修改**: `backend/.env`
```diff
- JUDGE_CONTAINER_ENABLED=false
+ JUDGE_CONTAINER_ENABLED=true
```

#### 验证步骤
1. 启动后端服务 `pnpm run dev:backend`
2. 提交一个测试代码执行请求
3. 检查日志确认使用 Docker 容器执行
4. 测试沙箱隔离（尝试访问宿主机文件系统应失败）

#### 回滚方案
```bash
# 如遇问题，临时回滚
JUDGE_CONTAINER_ENABLED=false
```

---

### 1.2 替换所有硬编码密钥

**缺陷编号**: #2
**位置**: `backend/.env`, `backend/docker-compose.yml`
**预估工时**: 1h

#### 问题分析
- JWT_SECRET 使用默认值
- 数据库密码硬编码
- Redis 密码硬编码

#### 修复步骤

**Step 1: 生成强随机密钥**
```bash
# JWT Secret (64字节)
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
echo "JWT_SECRET=$JWT_SECRET"

# Database Password (32字节)
DB_PASSWORD=$(openssl rand -base64 32 | tr -d '\n' | tr -d '/')
echo "DB_PASSWORD=$DB_PASSWORD"

# Redis Password (32字节)
REDIS_PASSWORD=$(openssl rand -base64 32 | tr -d '\n' | tr -d '/')
echo "REDIS_PASSWORD=$REDIS_PASSWORD"
```

**Step 2: 创建 .env.example 模板**

**文件创建**: `backend/.env.example`
```env
# Application
NODE_ENV=development
PORT=3000

# Database
DATABASE_URL="mysql://ulticode:CHANGE_ME@localhost:3306/ulticode?charset=utf8mb4"
DB_PASSWORD=CHANGE_ME

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=CHANGE_ME

# JWT
JWT_SECRET=GENERATE_WITH_openssl_rand_base64_64
JWT_EXPIRES_IN=1d

# Judge
JUDGE_CONTAINER_ENABLED=true
```

**Step 3: 更新 .gitignore**

**文件修改**: `.gitignore`
```diff
+ # Environment files with secrets
+ .env
+ .env.local
+ .env.*.local
+ !.env.example
```

**Step 4: 更新 docker-compose.yml 使用环境变量**

**文件修改**: `backend/docker-compose.yml`
```yaml
services:
  mysql:
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD:-root}
      MYSQL_DATABASE: ulticode
      MYSQL_USER: ulticode
      MYSQL_PASSWORD: ${DB_PASSWORD}

  redis:
    command: redis-server --requirepass ${REDIS_PASSWORD}
```

#### 验证步骤
1. 确认 `.env` 不在 git 追踪中: `git status`
2. 重启所有服务测试连接
3. 验证 JWT 认证正常工作

---

### 1.3 启用全局限流

**缺陷编号**: #3
**位置**: `backend/src/app.module.ts` (Lines 88-94)
**预估工时**: 30min

#### 问题分析
ThrottlerGuard 全局守卫被注释，API 暴露于 DDoS 和暴力破解攻击风险。

#### 修复步骤

**文件修改**: `backend/src/app.module.ts`
```typescript
// 找到被注释的 ThrottlerGuard 配置，取消注释
import { APP_GUARD } from '@nestjs/core';
import { ThrottlerGuard } from '@nestjs/throttler';

@Module({
  providers: [
    // 取消注释以下配置
    {
      provide: APP_GUARD,
      useClass: ThrottlerGuard,
    },
  ],
})
export class AppModule {}
```

#### 配置调优建议
```typescript
// 在 ThrottlerModule.forRoot() 中配置
ThrottlerModule.forRoot({
  throttlers: [
    {
      ttl: 60000,      // 1分钟窗口
      limit: 100,      // 每分钟最多100请求
    },
  ],
}),
```

#### 验证步骤
1. 启动后端服务
2. 使用压测工具验证限流生效:
   ```bash
   # 快速发送超过限制的请求
   for i in {1..150}; do curl -s http://localhost:3000/api/health; done
   ```
3. 确认返回 429 Too Many Requests

---

### 1.4 添加 Docker 资源限制

**缺陷编号**: #29
**位置**: `backend/docker-compose.yml`
**预估工时**: 30min

#### 问题分析
MySQL 和 Redis 服务未配置资源限制，可能导致单个容器耗尽宿主机资源。

#### 修复步骤

**文件修改**: `backend/docker-compose.yml`
```yaml
services:
  mysql:
    image: mysql:9.1
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M
    # ... 其他配置

  redis:
    image: redis:7
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
        reservations:
          cpus: '0.1'
          memory: 128M
    # ... 其他配置
```

#### 验证步骤
```bash
# 重新创建容器
docker-compose down && docker-compose up -d

# 验证资源限制已应用
docker stats --no-stream
```

---

## Phase 2: High 高优先级修复 (P1 - 2周内)

### 2.1 修复 any 类型使用

**缺陷编号**: #4
**位置**: `backend/src/i18n/i18n.service.ts` (Line 300)
**预估工时**: 1h

#### 修复步骤

**文件修改**: `backend/src/i18n/i18n.service.ts`
```typescript
import { Prisma } from '@prisma/client';

// Before
private handlePrismaError(error: any, translation: {...}): never {

// After
private handlePrismaError(
  error: unknown,
  translation: {
    entityType: string;
    entityId: string | number;
    fieldName: string;
    locale: string;
  }
): never {
  if (error instanceof Prisma.PrismaClientKnownRequestError) {
    switch (error.code) {
      case 'P2002': // Unique constraint violation
        throw new ConflictException(
          `Translation already exists for ${translation.entityType}:${translation.entityId}`
        );
      case 'P2025': // Record not found
        throw new NotFoundException(
          `Entity ${translation.entityType}:${translation.entityId} not found`
        );
      default:
        throw new InternalServerErrorException('Database error');
    }
  }

  if (error instanceof Error) {
    throw new InternalServerErrorException(error.message);
  }

  throw new InternalServerErrorException('Unknown error occurred');
}
```

#### 验证步骤
```bash
pnpm type-check
pnpm test
```

---

### 2.2 优化 N+1 查询

**缺陷编号**: #5
**位置**: `backend/src/i18n/i18n.service.ts` (Lines 269-291)
**预估工时**: 2h

#### 问题分析
`checkForDuplicates` 方法在循环中逐个查询数据库，造成 N+1 查询问题。

#### 修复步骤

**文件修改**: `backend/src/i18n/i18n.service.ts`
```typescript
// Before: 循环中的单个查询
async checkForDuplicates(translations: TranslationInput[]): Promise<string[]> {
  const duplicates: string[] = [];
  for (const t of translations) {
    const existing = await this.prisma.translation.findFirst({
      where: {
        entity_type: t.entityType,
        entity_id: String(t.entityId),
        field_name: t.fieldName,
        locale: t.locale,
      },
    });
    if (existing) {
      duplicates.push(`${t.entityType}:${t.entityId}:${t.fieldName}:${t.locale}`);
    }
  }
  return duplicates;
}

// After: 批量查询
async checkForDuplicates(translations: TranslationInput[]): Promise<string[]> {
  if (translations.length === 0) return [];

  // 构建批量查询条件
  const conditions = translations.map(t => ({
    entity_type: t.entityType,
    entity_id: String(t.entityId),
    field_name: t.fieldName,
    locale: t.locale,
  }));

  // 单次查询获取所有存在的记录
  const existingRecords = await this.prisma.translation.findMany({
    where: { OR: conditions },
    select: {
      entity_type: true,
      entity_id: true,
      field_name: true,
      locale: true,
    },
  });

  // 创建 Set 用于快速查找
  const existingSet = new Set(
    existingRecords.map(r =>
      `${r.entity_type}:${r.entity_id}:${r.field_name}:${r.locale}`
    )
  );

  // 返回重复项
  return translations
    .map(t => `${t.entityType}:${t.entityId}:${t.fieldName}:${t.locale}`)
    .filter(key => existingSet.has(key));
}
```

#### 验证步骤
1. 编写性能测试用例比较前后查询次数
2. 使用 Prisma 的查询日志验证只有一次数据库查询

---

### 2.3 拆分大型 Console 组件

**缺陷编号**: #13
**位置**: Console View 组件
**预估工时**: 8h

#### 需要拆分的组件

| 组件 | 当前行数 | 目标 |
|------|----------|------|
| SolutionCard.vue | ~500+ | <200 |
| ProblemDetailView.vue | ~600+ | <300 |

#### 拆分策略

**SolutionCard.vue 拆分方案**:
```
console/src/components/solution/
├── SolutionCard.vue           # 主容器 (~100行)
├── SolutionHeader.vue         # 头部信息
├── SolutionCode.vue           # 代码展示区
├── SolutionStats.vue          # 统计信息
├── SolutionActions.vue        # 操作按钮
└── composables/
    └── useSolution.ts         # 共享逻辑
```

**ProblemDetailView.vue 拆分方案**:
```
console/src/views/problem/
├── ProblemDetailView.vue      # 主容器路由组件 (~150行)
├── components/
│   ├── ProblemDescription.vue # 题目描述
│   ├── ProblemExamples.vue    # 示例展示
│   ├── ProblemConstraints.vue # 约束条件
│   ├── ProblemSubmission.vue  # 提交区域
│   └── ProblemSidebar.vue     # 侧边栏
└── composables/
    └── useProblemDetail.ts    # 共享状态和逻辑
```

#### 实施步骤
1. 创建 composables 提取共享逻辑
2. 创建子组件并移动相关模板
3. 更新父组件使用子组件
4. 运行测试确保功能不变

---

### 2.4 统一 API 错误处理

**缺陷编号**: #14
**位置**: `console/src/api/**/*.ts`
**预估工时**: 4h

#### 修复步骤

**Step 1: 创建统一错误处理器**

**文件创建**: `console/src/api/interceptors/error.ts`
```typescript
import type { AxiosError, AxiosResponse } from 'axios';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import router from '@/router';

interface ApiError {
  message: string;
  statusCode: number;
  error?: string;
}

export function handleApiError(error: AxiosError<ApiError>): never {
  const toast = useToast();
  const response = error.response;

  if (!response) {
    // 网络错误
    toast.error('网络连接失败，请检查网络');
    throw error;
  }

  const { status, data } = response;

  switch (status) {
    case 401:
      // 未授权，清除 token 并跳转登录
      const authStore = useAuthStore();
      authStore.logout();
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
      break;

    case 403:
      toast.error('没有权限执行此操作');
      break;

    case 404:
      toast.error(data.message || '请求的资源不存在');
      break;

    case 422:
      toast.error(data.message || '请求参数错误');
      break;

    case 429:
      toast.error('请求过于频繁，请稍后再试');
      break;

    case 500:
      toast.error('服务器错误，请稍后再试');
      break;

    default:
      toast.error(data.message || '请求失败');
  }

  throw error;
}
```

**Step 2: 配置 Axios 拦截器**

**文件修改**: `console/src/api/client.ts`
```typescript
import axios from 'axios';
import { handleApiError } from './interceptors/error';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
});

// 响应拦截器
apiClient.interceptors.response.use(
  (response) => response,
  (error) => handleApiError(error)
);

export default apiClient;
```

---

### 2.5 后端权限验证

**缺陷编号**: #22
**位置**: Management 相关 API
**预估工时**: 4h

#### 问题分析
前端使用 `authStore.hasPermission` 控制 UI，但需确保后端也有对应的权限验证。

#### 修复步骤

**Step 1: 创建权限守卫**

**文件检查/创建**: `backend/src/common/guards/permissions.guard.ts`
```typescript
import { Injectable, CanActivate, ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { PERMISSIONS_KEY } from '../decorators/permissions.decorator';

@Injectable()
export class PermissionsGuard implements CanActivate {
  constructor(private reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const requiredPermissions = this.reflector.getAllAndOverride<string[]>(
      PERMISSIONS_KEY,
      [context.getHandler(), context.getClass()]
    );

    if (!requiredPermissions) {
      return true;
    }

    const { user } = context.switchToHttp().getRequest();
    return requiredPermissions.every((permission) =>
      user.permissions?.includes(permission)
    );
  }
}
```

**Step 2: 应用到 Problem Controller**

**文件修改**: `backend/src/problem/problem.controller.ts`
```typescript
import { Permissions } from '@/common/decorators/permissions.decorator';

@Controller('problems')
export class ProblemController {
  @Post()
  @Permissions('problem:create')
  async create(@Body() createProblemDto: CreateProblemDto) {
    // ...
  }

  @Patch(':id')
  @Permissions('problem:update')
  async update(@Param('id') id: string, @Body() updateProblemDto: UpdateProblemDto) {
    // ...
  }

  @Delete(':id')
  @Permissions('problem:delete')
  async remove(@Param('id') id: string) {
    // ...
  }
}
```

---

### 2.6 拆分 ProblemsListView

**缺陷编号**: #23
**位置**: `management/src/views/problems/ProblemsListView.vue` (910+ 行)
**预估工时**: 6h

#### 拆分方案

```
management/src/views/problems/
├── ProblemsListView.vue           # 主容器 (~200行)
├── components/
│   ├── ProblemsTable.vue          # 表格组件
│   ├── ProblemsFilters.vue        # 筛选器
│   ├── ProblemsActions.vue        # 批量操作
│   ├── ProblemStatusBadge.vue     # 状态徽章
│   └── columns/                   # 列定义
│       ├── index.ts               # 导出所有列
│       ├── titleColumn.ts         # 标题列
│       ├── statusColumn.ts        # 状态列
│       └── actionsColumn.ts       # 操作列
└── composables/
    ├── useProblemsQuery.ts        # 数据查询逻辑
    ├── useProblemsFilters.ts      # 筛选逻辑
    └── useProblemsBulkActions.ts  # 批量操作逻辑
```

#### 关键改进
1. 将 `h()` 渲染函数改为 Vue SFC
2. 列定义提取为独立配置文件
3. 业务逻辑抽取到 composables

---

## Phase 3: Medium 中优先级修复 (P2 - 1个月内)

### 3.1 后端 Medium 问题 (预估 8h)

| # | 问题 | 修复方案 |
|---|------|----------|
| 6 | 缺少输入验证装饰器 | 审查所有 DTO，添加 class-validator 装饰器 |
| 7 | 缺少 API 响应类型定义 | 添加 @ApiResponse() 装饰器 |
| 8 | 日志敏感信息泄露 | 审查日志输出，脱敏处理 |
| 9 | 缺少数据库连接池配置 | 在 schema.prisma 添加连接池配置 |
| 10 | 软删除中间件问题 | 添加 includeDeleted 选项 |

### 3.2 Console Medium 问题 (预估 8h)

| # | 问题 | 修复方案 |
|---|------|----------|
| 15 | 路由元数据类型不安全 | 创建 RouteMeta 接口 |
| 16 | 动态导入 authStore | 改为静态导入 |
| 17 | 响应式数据未正确解包 | 移除模板中的 .value |
| 18 | 缺少加载状态骨架屏 | 添加 Skeleton 组件 |

### 3.3 Management Medium 问题 (预估 6h)

| # | 问题 | 修复方案 |
|---|------|----------|
| 24 | 深度 watch 性能问题 | 改为监听特定属性 |
| 25 | 表单验证逻辑分散 | 使用 Zod 统一验证 |
| 26 | 缺少审计日志 | 添加操作日志功能 |

### 3.4 架构 Medium 问题 (预估 8h)

| # | 问题 | 修复方案 |
|---|------|----------|
| 30 | 缺少 .env gitignore 说明 | 更新 .gitignore 和文档 |
| 31 | 缺少健康检查端点 | 添加 /health 和 /ready |
| 32 | 缺少 CI/CD 配置 | 添加 GitHub Actions |

---

## Phase 4: Low 持续改进 (P3 - 长期)

### 4.1 测试覆盖率提升

**目标**: 核心业务逻辑覆盖率 > 80%

**优先测试模块**:
1. 认证授权模块
2. 代码判题模块
3. 提交评测模块

### 4.2 文档完善

**需要补充的文档**:
- [ ] API 文档 (Swagger/OpenAPI)
- [ ] 部署指南
- [ ] 贡献指南
- [ ] 架构设计文档

### 4.3 性能优化

- [ ] 实现图片懒加载
- [ ] 配置 Bundle 代码分割
- [ ] 添加骨架屏

### 4.4 共享组件库

**目标**: 创建 `@ulticode/ui` 共享包

```
packages/
├── ui/                  # 共享 UI 组件
│   ├── src/
│   │   ├── Button/
│   │   ├── Card/
│   │   ├── Input/
│   │   └── index.ts
│   └── package.json
└── utils/               # 共享工具函数
    ├── src/
    │   ├── date.ts
    │   ├── format.ts
    │   └── index.ts
    └── package.json
```

---

## 执行跟踪

### Phase 1 进度

- [ ] 1.1 启用 Docker 代码执行沙箱
- [ ] 1.2 替换所有硬编码密钥
- [ ] 1.3 启用全局限流
- [ ] 1.4 添加 Docker 资源限制

### Phase 2 进度

- [ ] 2.1 修复 any 类型使用
- [ ] 2.2 优化 N+1 查询
- [ ] 2.3 拆分大型 Console 组件
- [ ] 2.4 统一 API 错误处理
- [ ] 2.5 后端权限验证
- [ ] 2.6 拆分 ProblemsListView

### Phase 3 进度

- [ ] 3.1 后端 Medium 问题
- [ ] 3.2 Console Medium 问题
- [ ] 3.3 Management Medium 问题
- [ ] 3.4 架构 Medium 问题

---

## 附录: 快速参考

### 修复命令速查

```bash
# 代码质量检查
pnpm lint && pnpm type-check && pnpm test

# 重启 Docker 服务
docker-compose down && docker-compose up -d

# 生成随机密钥
openssl rand -base64 64

# 查看 Docker 资源使用
docker stats --no-stream
```

### 相关文档链接

- [NestJS Guards](https://docs.nestjs.com/guards)
- [Prisma Query Optimization](https://www.prisma.io/docs/guides/performance-and-optimization)
- [Vue 3 Composition API](https://vuejs.org/guide/extras/composition-api-faq.html)
- [Docker Compose Resources](https://docs.docker.com/compose/compose-file/deploy/)

---

**文档版本**: 1.0
**最后更新**: 2026-02-08
