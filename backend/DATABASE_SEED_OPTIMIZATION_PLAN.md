# 数据库初始化设计优化方案

> 文档版本: 1.0.0
> 创建日期: 2026-02-07
> 适用范围: UltiCode-Public-Next Backend

---

## 目录

1. [现状分析](#1-现状分析)
2. [优化目标](#2-优化目标)
3. [架构设计](#3-架构设计)
4. [分层数据模型](#4-分层数据模型)
5. [环境策略](#5-环境策略)
6. [实现细节](#6-实现细节)
7. [迁移计划](#7-迁移计划)
8. [最佳实践](#8-最佳实践)

---

## 1. 现状分析

### 1.1 当前结构

```
prisma/seed/
├── index.ts                    # 主入口，串行执行
├── seed-*.ts                   # 各模块 seed 函数
└── data/
    ├── users.data.ts           # 用户数据（含明文密码）
    ├── problems.data.ts        # 题目数据
    ├── forum.data.ts           # 论坛数据
    └── ...
```

### 1.2 现有问题

| 问题类别 | 具体问题 | 影响 |
|---------|---------|------|
| **性能** | 单条插入 (`create`)，无批量操作 | 初始化耗时长 |
| **安全** | 密码硬编码在数据文件中 | 安全隐患 |
| **可维护性** | 数据与逻辑混杂，无版本控制 | 难以追踪变更 |
| **灵活性** | 无环境区分（dev/test/prod） | 生产环境风险 |
| **可靠性** | 无事务保护，失败后状态不一致 | 数据完整性问题 |
| **扩展性** | 新增模块需修改多处代码 | 开发效率低 |
| **调试** | 无进度追踪，无断点续传 | 调试困难 |

### 1.3 依赖图

```
┌─────────────────────────────────────────────────────────────────┐
│                        L0: 基础数据层                            │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────────────┐ │
│  │   Users  │  │ ProblemTags  │  │    SubmissionStatuses     │ │
│  └────┬─────┘  └──────┬───────┘  └────────────────────────────┘ │
└───────┼───────────────┼─────────────────────────────────────────┘
        │               │
┌───────┼───────────────┼─────────────────────────────────────────┐
│       ▼               ▼           L1: 主实体层                   │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────────┐   │
│  │ Problems │  │  ForumTags   │  │   ForumCommunities       │   │
│  └────┬─────┘  └──────┬───────┘  └───────────┬──────────────┘   │
└───────┼───────────────┼──────────────────────┼──────────────────┘
        │               │                      │
┌───────┼───────────────┼──────────────────────┼──────────────────┐
│       ▼               ▼                      ▼   L2: 关联实体层  │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────────┐   │
│  │ Contests │  │  ForumPosts  │  │   Solutions/Submissions  │   │
│  └────┬─────┘  └──────┬───────┘  └───────────┬──────────────┘   │
└───────┼───────────────┼──────────────────────┼──────────────────┘
        │               │                      │
┌───────┼───────────────┼──────────────────────┼──────────────────┐
│       ▼               ▼                      ▼   L3: 依赖关系层  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Translations / Permissions / EdgeOperations / Comments     │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 优化目标

### 2.1 核心目标

| 目标 | 描述 | 优先级 |
|------|------|--------|
| **高性能** | 批量操作，并行执行，3倍速提升 | P0 |
| **高安全** | 敏感数据加密存储，环境隔离 | P0 |
| **高可靠** | 事务保护，幂等操作，断点续传 | P1 |
| **高可维护** | 模块化设计，版本控制，文档完善 | P1 |
| **高扩展** | 插件式架构，易于添加新模块 | P2 |

### 2.2 量化指标

```yaml
性能指标:
  - seed_time_reduction: ≥ 60%      # 初始化时间减少
  - batch_insert_ratio: ≥ 90%       # 批量插入覆盖率

可靠性指标:
  - transaction_coverage: 100%      # 事务覆盖率
  - idempotent_operations: 100%     # 幂等操作比例
  - failure_recovery: ≤ 30s         # 故障恢复时间

安全指标:
  - plaintext_passwords: 0          # 明文密码数量
  - env_separation: complete        # 环境隔离
```

---

## 3. 架构设计

### 3.1 新目录结构

```
prisma/seed/
├── index.ts                        # 主入口（不变）
├── config/
│   ├── seed.config.ts              # Seed 配置
│   ├── environments/
│   │   ├── development.ts          # 开发环境配置
│   │   ├── test.ts                 # 测试环境配置
│   │   └── production.ts           # 生产环境配置
│   └── constants.ts                # 常量定义
│
├── core/
│   ├── seed-runner.ts              # Seed 执行引擎
│   ├── seed-context.ts             # 上下文管理
│   ├── seed-logger.ts              # 日志系统
│   ├── seed-validator.ts           # 数据验证器
│   └── seed-transaction.ts         # 事务管理器
│
├── modules/                        # 模块化 Seeders
│   ├── base/
│   │   ├── base.seeder.ts          # 基类
│   │   └── interfaces.ts           # 接口定义
│   ├── users/
│   │   ├── users.seeder.ts
│   │   ├── users.data.ts
│   │   └── users.factory.ts        # 数据工厂
│   ├── problems/
│   │   ├── problems.seeder.ts
│   │   ├── problems.data.ts
│   │   └── problems.factory.ts
│   ├── forum/
│   │   ├── forum.seeder.ts
│   │   ├── forum.data.ts
│   │   └── forum.factory.ts
│   ├── contests/
│   │   └── ...
│   └── permissions/
│       └── ...
│
├── fixtures/                       # 固定测试数据
│   ├── minimal.fixture.ts          # 最小数据集
│   ├── standard.fixture.ts         # 标准数据集
│   └── full.fixture.ts             # 完整数据集
│
└── utils/
    ├── batch-insert.ts             # 批量插入工具
    ├── id-generator.ts             # ID 生成器
    ├── password-hasher.ts          # 密码加密
    └── data-faker.ts               # 数据生成器
```

### 3.2 核心组件

#### 3.2.1 Seed 执行引擎

```typescript
// core/seed-runner.ts
export interface SeedRunnerOptions {
  environment: 'development' | 'test' | 'production';
  fixture: 'minimal' | 'standard' | 'full';
  modules?: string[];           // 指定模块
  skipModules?: string[];       // 跳过模块
  dryRun?: boolean;             // 干运行模式
  verbose?: boolean;            // 详细日志
  parallel?: boolean;           // 并行执行
  resume?: string;              // 断点续传 ID
}

export class SeedRunner {
  private context: SeedContext;
  private modules: Map<string, BaseSeeder>;
  private dependencyGraph: DependencyGraph;

  async run(options: SeedRunnerOptions): Promise<SeedResult> {
    // 1. 初始化上下文
    await this.initContext(options);

    // 2. 加载模块
    await this.loadModules(options);

    // 3. 构建依赖图
    this.buildDependencyGraph();

    // 4. 执行 seed
    return this.executeSeeds();
  }

  private async executeSeeds(): Promise<SeedResult> {
    const layers = this.dependencyGraph.getTopologicalLayers();

    for (const layer of layers) {
      // 同层模块可并行执行
      await Promise.all(
        layer.map(module => this.executeModule(module))
      );
    }
  }
}
```

#### 3.2.2 基础 Seeder 类

```typescript
// modules/base/base.seeder.ts
export abstract class BaseSeeder<T = unknown> {
  abstract readonly name: string;
  abstract readonly dependencies: string[];
  abstract readonly priority: number;

  protected context: SeedContext;
  protected prisma: PrismaClient;
  protected logger: SeedLogger;

  // 生命周期钩子
  abstract beforeSeed(): Promise<void>;
  abstract seed(): Promise<SeedModuleResult>;
  abstract afterSeed(): Promise<void>;
  abstract clear(): Promise<void>;

  // 批量插入辅助
  protected async batchCreate<R>(
    model: PrismaDelegate,
    data: R[],
    batchSize = 100
  ): Promise<number> {
    let created = 0;
    for (let i = 0; i < data.length; i += batchSize) {
      const batch = data.slice(i, i + batchSize);
      await model.createMany({ data: batch, skipDuplicates: true });
      created += batch.length;
      this.logger.progress(created, data.length);
    }
    return created;
  }

  // 幂等 upsert
  protected async upsertMany<R>(
    model: PrismaDelegate,
    data: R[],
    uniqueKey: keyof R
  ): Promise<number> {
    let upserted = 0;
    for (const item of data) {
      await model.upsert({
        where: { [uniqueKey]: item[uniqueKey] },
        create: item,
        update: item
      });
      upserted++;
    }
    return upserted;
  }
}
```

#### 3.2.3 上下文管理

```typescript
// core/seed-context.ts
export class SeedContext {
  readonly environment: Environment;
  readonly fixture: FixtureLevel;
  readonly startTime: Date;

  // 跨模块共享数据
  private sharedData: Map<string, unknown> = new Map();

  // 进度追踪
  private progress: SeedProgress = {
    completed: [],
    current: null,
    pending: [],
    failed: []
  };

  // 存储共享数据（如创建的用户 ID）
  set<T>(key: string, value: T): void {
    this.sharedData.set(key, value);
  }

  get<T>(key: string): T | undefined {
    return this.sharedData.get(key) as T;
  }

  // 进度持久化（支持断点续传）
  async saveProgress(): Promise<string> {
    const checkpointId = generateCheckpointId();
    await fs.writeFile(
      `.seed-checkpoint-${checkpointId}.json`,
      JSON.stringify(this.progress)
    );
    return checkpointId;
  }

  async loadProgress(checkpointId: string): Promise<void> {
    const data = await fs.readFile(`.seed-checkpoint-${checkpointId}.json`);
    this.progress = JSON.parse(data.toString());
  }
}
```

---

## 4. 分层数据模型

### 4.1 数据层级定义

```typescript
// config/seed.config.ts
export const SEED_LAYERS = {
  L0_FOUNDATION: {
    order: 0,
    parallel: true,
    modules: [
      'SubmissionStatuses',  // 无依赖，可并行
      'ProblemTags',
      'ForumTags'
    ]
  },
  L1_CORE_ENTITIES: {
    order: 1,
    parallel: false,
    modules: [
      'Users',               // 依赖 L0
      'ForumCommunities'
    ]
  },
  L2_MAIN_ENTITIES: {
    order: 2,
    parallel: true,
    modules: [
      'Problems',            // 依赖 Users, ProblemTags
      'ForumUsers',          // 依赖 Users
      'AdminUsers'
    ]
  },
  L3_RELATIONS: {
    order: 3,
    parallel: true,
    modules: [
      'Contests',            // 依赖 Problems
      'Solutions',
      'ForumPosts',
      'ProblemLists'
    ]
  },
  L4_DERIVED: {
    order: 4,
    parallel: true,
    modules: [
      'Submissions',
      'ForumComments',
      'SolutionComments'
    ]
  },
  L5_METADATA: {
    order: 5,
    parallel: true,
    modules: [
      'Translations',
      'Permissions',
      'EdgeOperations'
    ]
  }
} as const;
```

### 4.2 数据量配置

```typescript
// config/fixtures/fixture-sizes.ts
export const FIXTURE_SIZES = {
  minimal: {
    users: 5,
    problems: 3,
    forumPosts: 5,
    contests: 1,
    submissions: 10
  },
  standard: {
    users: 20,
    problems: 8,
    forumPosts: 30,
    contests: 5,
    submissions: 100
  },
  full: {
    users: 50,
    problems: 20,
    forumPosts: 100,
    contests: 15,
    submissions: 500
  }
} as const;
```

---

## 5. 环境策略

### 5.1 环境配置矩阵

| 配置项 | Development | Test | Production |
|--------|-------------|------|------------|
| **Fixture** | full | minimal | none |
| **Admin User** | 创建 | 创建 | 仅权限 |
| **Test Data** | 是 | 是 | 否 |
| **密码哈希** | 弱哈希 | 弱哈希 | 强哈希 |
| **事务** | 可选 | 必须 | 必须 |
| **日志级别** | debug | info | warn |
| **清理数据** | 允许 | 允许 | 禁止 |

### 5.2 环境配置实现

```typescript
// config/environments/development.ts
export const developmentConfig: EnvironmentConfig = {
  name: 'development',
  fixture: 'full',
  features: {
    createAdminUser: true,
    createTestData: true,
    allowClear: true,
    useWeakHash: true      // 加速开发
  },
  logging: {
    level: 'debug',
    showProgress: true,
    showSql: false
  },
  performance: {
    batchSize: 100,
    parallel: true,
    maxConcurrency: 4
  }
};

// config/environments/production.ts
export const productionConfig: EnvironmentConfig = {
  name: 'production',
  fixture: 'none',
  features: {
    createAdminUser: false,  // 仅通过迁移创建
    createTestData: false,
    allowClear: false,       // 禁止清空
    useWeakHash: false
  },
  logging: {
    level: 'warn',
    showProgress: false,
    showSql: false
  },
  modules: {
    allow: ['Permissions', 'SubmissionStatuses'],  // 仅允许静态数据
    deny: ['*']  // 禁止其他模块
  }
};
```

### 5.3 安全数据处理

```typescript
// utils/password-hasher.ts
import * as bcrypt from 'bcrypt';

export class PasswordHasher {
  private readonly saltRounds: number;

  constructor(environment: string) {
    // 开发/测试环境用低轮数加速
    this.saltRounds = environment === 'production' ? 12 : 4;
  }

  async hash(password: string): Promise<string> {
    return bcrypt.hash(password, this.saltRounds);
  }

  // 预计算常用测试密码
  async getTestPasswordHash(): Promise<string> {
    if (this.cachedTestHash) return this.cachedTestHash;
    this.cachedTestHash = await this.hash('password123');
    return this.cachedTestHash;
  }
}

// 环境变量驱动的密码
// .env.development
// SEED_ADMIN_PASSWORD=admin123
// SEED_TEST_PASSWORD=password123

// .env.production
// SEED_ADMIN_PASSWORD= (不设置，禁用 admin 创建)
```

---

## 6. 实现细节

### 6.1 批量插入优化

```typescript
// utils/batch-insert.ts
export async function batchInsert<T extends object>(
  prisma: PrismaClient,
  model: string,
  data: T[],
  options: BatchInsertOptions = {}
): Promise<BatchInsertResult> {
  const {
    batchSize = 100,
    skipDuplicates = true,
    onProgress,
    onError = 'throw'
  } = options;

  const results: BatchInsertResult = {
    inserted: 0,
    skipped: 0,
    failed: 0,
    errors: []
  };

  for (let i = 0; i < data.length; i += batchSize) {
    const batch = data.slice(i, i + batchSize);

    try {
      const result = await (prisma as any)[model].createMany({
        data: batch,
        skipDuplicates
      });
      results.inserted += result.count;
      results.skipped += batch.length - result.count;
    } catch (error) {
      if (onError === 'throw') throw error;
      if (onError === 'continue') {
        results.failed += batch.length;
        results.errors.push({ batch: i / batchSize, error });
      }
    }

    onProgress?.(i + batch.length, data.length);
  }

  return results;
}
```

### 6.2 数据工厂模式

```typescript
// modules/users/users.factory.ts
import { faker } from '@faker-js/faker';

export class UserFactory {
  private readonly hasher: PasswordHasher;
  private cachedPasswordHash: string | null = null;

  constructor(private readonly context: SeedContext) {
    this.hasher = new PasswordHasher(context.environment);
  }

  async init(): Promise<void> {
    // 预计算密码哈希
    this.cachedPasswordHash = await this.hasher.getTestPasswordHash();
  }

  create(overrides: Partial<UserCreateInput> = {}): UserCreateInput {
    return {
      id: generateUserId(),
      username: faker.internet.userName().toLowerCase(),
      email: faker.internet.email(),
      name: faker.person.fullName(),
      avatar: `https://api.dicebear.com/7.x/notionists/svg?seed=${faker.string.alphanumeric(8)}`,
      password: this.cachedPasswordHash!,
      bio: faker.lorem.sentence(),
      company: faker.company.name(),
      github: faker.internet.userName(),
      location: faker.location.city(),
      preferred_language: faker.helpers.arrayElement(['en-US', 'zh-CN', 'ja-JP']),
      ...overrides
    };
  }

  createMany(count: number, template?: Partial<UserCreateInput>): UserCreateInput[] {
    return Array.from({ length: count }, () => this.create(template));
  }

  // 预定义用户（确保一致性）
  createPredefined(): UserCreateInput[] {
    return PREDEFINED_USERS.map(user => ({
      ...this.create(),
      ...user,
      password: this.cachedPasswordHash!
    }));
  }
}
```

### 6.3 事务安全

```typescript
// core/seed-transaction.ts
export class SeedTransactionManager {
  constructor(private readonly prisma: PrismaClient) {}

  async runInTransaction<T>(
    fn: (tx: Prisma.TransactionClient) => Promise<T>,
    options: TransactionOptions = {}
  ): Promise<T> {
    const {
      maxWait = 5000,
      timeout = 60000,
      isolationLevel = 'ReadCommitted'
    } = options;

    return this.prisma.$transaction(fn, {
      maxWait,
      timeout,
      isolationLevel
    });
  }

  // 模块级事务包装
  async seedModuleWithTransaction(
    module: BaseSeeder,
    options?: TransactionOptions
  ): Promise<SeedModuleResult> {
    return this.runInTransaction(async (tx) => {
      // 注入事务客户端
      module.setTransactionClient(tx);

      try {
        await module.beforeSeed();
        const result = await module.seed();
        await module.afterSeed();
        return result;
      } catch (error) {
        // 事务自动回滚
        throw new SeedModuleError(module.name, error);
      }
    }, options);
  }
}
```

### 6.4 进度追踪与日志

```typescript
// core/seed-logger.ts
export class SeedLogger {
  private startTime: number;
  private moduleStartTimes: Map<string, number> = new Map();

  constructor(private readonly options: LoggerOptions) {}

  moduleStart(name: string): void {
    this.moduleStartTimes.set(name, Date.now());
    console.log(`\n📦 Seeding ${name}...`);
  }

  moduleComplete(name: string, result: SeedModuleResult): void {
    const elapsed = Date.now() - (this.moduleStartTimes.get(name) || 0);
    console.log(
      `   ✅ ${name}: ${result.count} records (${elapsed}ms)`
    );
  }

  moduleFailed(name: string, error: Error): void {
    const elapsed = Date.now() - (this.moduleStartTimes.get(name) || 0);
    console.error(
      `   ❌ ${name}: Failed after ${elapsed}ms`
    );
    if (this.options.verbose) {
      console.error(`      ${error.message}`);
    }
  }

  progress(current: number, total: number, label?: string): void {
    if (!this.options.showProgress) return;

    const percentage = Math.round((current / total) * 100);
    const bar = '█'.repeat(percentage / 5) + '░'.repeat(20 - percentage / 5);
    process.stdout.write(
      `\r   [${bar}] ${percentage}% (${current}/${total}) ${label || ''}`
    );

    if (current === total) {
      process.stdout.write('\n');
    }
  }

  summary(result: SeedResult): void {
    const elapsed = Date.now() - this.startTime;
    console.log('\n' + '═'.repeat(50));
    console.log('📊 Seed Summary');
    console.log('═'.repeat(50));
    console.log(`   Environment: ${result.environment}`);
    console.log(`   Fixture: ${result.fixture}`);
    console.log(`   Total time: ${elapsed}ms`);
    console.log(`   Modules: ${result.modules.length}`);
    console.log(`   Records: ${result.totalRecords}`);

    if (result.errors.length > 0) {
      console.log(`   ⚠️  Errors: ${result.errors.length}`);
    }
    console.log('═'.repeat(50) + '\n');
  }
}
```

---

## 7. 迁移计划

### 7.1 阶段规划

```
Phase 1: 基础设施 (Week 1)
├── 创建新目录结构
├── 实现 SeedRunner 核心
├── 实现 SeedLogger
└── 实现 BatchInsert 工具

Phase 2: 模块迁移 (Week 2)
├── 迁移 Users 模块（作为模板）
├── 迁移 Problems 模块
├── 迁移 Forum 模块
└── 迁移其他模块

Phase 3: 高级功能 (Week 3)
├── 实现环境配置
├── 实现事务管理
├── 实现断点续传
└── 实现数据工厂

Phase 4: 测试与文档 (Week 4)
├── 单元测试
├── 集成测试
├── 性能测试
└── 文档完善
```

### 7.2 渐进迁移策略

```typescript
// 保持向后兼容的 index.ts
import { SeedRunner } from './core/seed-runner';
import { legacySeed } from './legacy/index';

async function main() {
  const useNewSeed = process.env.USE_NEW_SEED === 'true';

  if (useNewSeed) {
    const runner = new SeedRunner();
    await runner.run({
      environment: getEnvironment(),
      fixture: getFixture(),
      verbose: true
    });
  } else {
    // 旧版 seed（过渡期）
    await legacySeed();
  }
}
```

### 7.3 回滚方案

1. **代码回滚**: 保留旧代码在 `legacy/` 目录
2. **数据回滚**: 使用 Prisma 迁移回滚
3. **环境变量开关**: `USE_NEW_SEED=false` 切回旧版

---

## 8. 最佳实践

### 8.1 编码规范

```typescript
// ✅ Good: 使用批量插入
await prisma.user.createMany({
  data: users,
  skipDuplicates: true
});

// ❌ Bad: 单条插入循环
for (const user of users) {
  await prisma.user.create({ data: user });
}

// ✅ Good: 使用 upsert 实现幂等
await prisma.rolePermission.upsert({
  where: { role_action_resource: { role, action, resource } },
  create: { role, action, resource },
  update: {}
});

// ❌ Bad: 先查再插
const exists = await prisma.rolePermission.findUnique(...);
if (!exists) await prisma.rolePermission.create(...);

// ✅ Good: 预计算哈希
const hash = await hasher.getTestPasswordHash();
const users = rawUsers.map(u => ({ ...u, password: hash }));

// ❌ Bad: 循环内哈希
for (const u of rawUsers) {
  u.password = await bcrypt.hash(u.password, 10);
}
```

### 8.2 数据定义规范

```typescript
// modules/users/users.data.ts

// ✅ Good: ID 常量化
export const USER_IDS = {
  ADMIN: 'u-admin-001',
  MODERATOR: 'u-mod-001',
  TEST_USER_1: 'u-test-001'
} as const;

// ✅ Good: 类型安全
export interface UserSeedData {
  id: string;
  username: string;
  email: string;
  role?: UserRole;
}

// ✅ Good: 预定义数据与生成数据分离
export const PREDEFINED_USERS: UserSeedData[] = [
  { id: USER_IDS.ADMIN, username: 'admin', email: 'admin@ulticode.com', role: 'SUPER_ADMIN' },
  { id: USER_IDS.MODERATOR, username: 'moderator', email: 'mod@ulticode.com', role: 'MODERATOR' }
];

export const GENERATED_USER_COUNT = 18;  // 动态生成数量
```

### 8.3 测试策略

```typescript
// __tests__/seed/users.seeder.spec.ts
describe('UsersSeeder', () => {
  let prisma: PrismaClient;
  let seeder: UsersSeeder;

  beforeAll(async () => {
    prisma = new PrismaClient();
    await prisma.$connect();
  });

  beforeEach(async () => {
    // 使用测试事务
    seeder = new UsersSeeder(createTestContext());
  });

  afterEach(async () => {
    // 回滚测试数据
    await prisma.user.deleteMany({
      where: { id: { startsWith: 'test-' } }
    });
  });

  it('should create predefined users', async () => {
    const result = await seeder.seed();
    expect(result.count).toBeGreaterThan(0);

    const admin = await prisma.user.findUnique({
      where: { username: 'admin' }
    });
    expect(admin).toBeDefined();
    expect(admin?.role).toBe('SUPER_ADMIN');
  });

  it('should be idempotent', async () => {
    await seeder.seed();
    const result2 = await seeder.seed();

    // 第二次应该 upsert，不报错
    expect(result2.count).toBeGreaterThan(0);
  });
});
```

### 8.4 性能优化清单

- [ ] 使用 `createMany` 替代循环 `create`
- [ ] 预计算密码哈希（单次计算复用）
- [ ] 同层模块并行执行
- [ ] 关闭不必要的 Prisma 日志
- [ ] 使用 `skipDuplicates` 避免冲突
- [ ] 批量提交（batchSize: 100-500）
- [ ] 延迟加载非必要模块

---

## 附录

### A. 命令行接口

```bash
# 标准开发 seed
npm run db:seed

# 指定环境
npm run db:seed -- --env=test

# 指定数据量
npm run db:seed -- --fixture=minimal

# 仅特定模块
npm run db:seed -- --modules=users,problems

# 干运行（不实际写入）
npm run db:seed -- --dry-run

# 断点续传
npm run db:seed -- --resume=checkpoint-abc123

# 详细日志
npm run db:seed -- --verbose
```

### B. 环境变量

```bash
# .env.development
SEED_ENV=development
SEED_FIXTURE=full
SEED_ADMIN_PASSWORD=admin123
SEED_VERBOSE=true
SEED_PARALLEL=true

# .env.test
SEED_ENV=test
SEED_FIXTURE=minimal
SEED_ADMIN_PASSWORD=testadmin
SEED_VERBOSE=false

# .env.production
SEED_ENV=production
SEED_FIXTURE=none
# SEED_ADMIN_PASSWORD 不设置
```

### C. 性能基准

| 操作 | 当前耗时 | 优化后预期 |
|------|---------|-----------|
| 完整 seed | ~8s | ~3s |
| 最小 seed | ~4s | ~1s |
| Users (20) | ~2s | ~0.3s |
| Problems (8) | ~1.5s | ~0.5s |
| Forum 数据 | ~3s | ~1s |

---

## 变更日志

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| 1.0.0 | 2026-02-07 | 初始版本 |
