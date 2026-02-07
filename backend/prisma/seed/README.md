# Database Seed System

This directory contains the optimized database seeding system for UltiCode.

## Quick Start

```bash
# Run legacy seed system (default)
pnpm run db:seed

# Run new optimized seed system
USE_NEW_SEED=true pnpm run db:seed

# Run with verbose logging
USE_NEW_SEED=true SEED_VERBOSE=true pnpm run db:seed

# Run specific environment
SEED_ENV=test USE_NEW_SEED=true pnpm run db:seed
```

## Architecture

The seed system follows a layered architecture:

```
prisma/seed/
├── core/                   # Core infrastructure
│   ├── interfaces.ts       # TypeScript interfaces
│   ├── seed-context.ts     # Cross-module data sharing
│   ├── seed-logger.ts      # Progress and logging
│   └── seed-runner.ts      # Orchestration engine
├── utils/                  # Utilities
│   ├── batch-insert.ts     # Batch insert operations
│   └── password-hasher.ts  # Password hashing
├── modules/                # Seeder modules
│   ├── base/               # Base class
│   ├── users/              # Users seeder
│   ├── problems/           # Problems seeder
│   └── ...                 # More modules
├── config/                 # Configuration
│   └── seed.config.ts      # Environment configs
├── data/                   # Seed data files
└── index.ts                # Entry point
```

## Dependency Layers

Seeders are organized by dependency:

| Layer | Modules | Description |
|-------|---------|-------------|
| L0 | SubmissionStatuses, ProblemTags | No dependencies |
| L1 | Users | Base entities |
| L2 | Problems, Forum | Depend on L1 |
| L3 | Contests, Solutions, ProblemLists | Depend on L2 |
| L4 | Submissions | Depend on L3 |
| L5 | Translations, Permissions | Final layer |

## Environment Configuration

| Config | Development | Test | Production |
|--------|-------------|------|------------|
| Fixture | standard | minimal | standard |
| Batch size | 100 | 50 | 100 |
| Parallel | true | false | false |
| Allow clear | true | true | false |
| Salt rounds | 4 | 4 | 12 |

## Creating a New Seeder

1. Create a new directory under `modules/`:

```typescript
// modules/my-feature/my-feature.seeder.ts
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import type { SeederMetadata, SeedModuleResult, TransactionClient } from '../../core/interfaces';

export class MyFeatureSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'MyFeature',
    version: '1.0.0',
    dependencies: ['Users', 'Problems'], // Required modules
    priority: 0, // Lower = earlier within same layer
    description: 'Seed my feature data',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx);
    await client.myTable.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx);

    // Get data from dependent modules
    const userIds = this.getOrThrow<string[]>('userIds');

    // Batch insert
    const result = await this.batchCreate(client.myTable, myData);

    // Store data for dependent modules
    this.set('myFeatureIds', myIds);

    return this.createResult(result.inserted, startTime);
  }
}

export const createMyFeatureSeeder = createSeederExport(MyFeatureSeeder);
```

2. Export from `modules/index.ts`:

```typescript
export { MyFeatureSeeder, createMyFeatureSeeder } from './my-feature';
```

3. Register in `index.ts`:

```typescript
runner.registerSeeders([
  // ... existing seeders
  createMyFeatureSeeder,
]);
```

## Context Keys

Standard keys for cross-module data:

| Key | Type | Module |
|-----|------|--------|
| `userIds` | `string[]` | Users |
| `userMap` | `Map<string, string>` | Users |
| `problemIds` | `number[]` | Problems |
| `problemMap` | `Map<string, number>` | Problems |
| `forumCommunityIds` | `string[]` | Forum |
| `contestIds` | `string[]` | Contests |
| `defaultPasswordHash` | `string` | Users |

## Testing

```bash
# Run seed tests
pnpm test -- --testPathPattern=seed

# Run specific test
pnpm test -- --testPathPattern=seed-runner
```

## Performance Tips

1. **Use batch insert**: Always prefer `batchCreate()` over sequential creates
2. **Pre-compute hashes**: Use `PasswordHasher` with caching for password hashing
3. **Share context data**: Store IDs in context to avoid lookups
4. **Minimize dependencies**: Keep dependency chains short

## Benchmarking

```bash
# Compare legacy vs new system
time pnpm run db:seed
time USE_NEW_SEED=true pnpm run db:seed
```

Target: 60%+ improvement in seed time with the new system.
