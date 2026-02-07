import type { PrismaClient } from '@prisma/client';
import { SeedRunner, createSeedRunner } from '../../core/seed-runner';
import { SeedContext } from '../../core/seed-context';
import { BaseSeeder } from '../../modules/base/base.seeder';
import type { SeederMetadata, SeedModuleResult, TransactionClient } from '../../core/interfaces';

// Mock seeders for testing
class MockSeederA extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'SeederA',
    version: '1.0.0',
    dependencies: [],
    priority: 0,
  };

  async clear(): Promise<void> {}
  async seed(): Promise<SeedModuleResult> {
    this.set('seederA_ran', true);
    return this.createResult(10, Date.now());
  }
}

class MockSeederB extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'SeederB',
    version: '1.0.0',
    dependencies: ['SeederA'],
    priority: 0,
  };

  async clear(): Promise<void> {}
  async seed(): Promise<SeedModuleResult> {
    const aRan = this.get<boolean>('seederA_ran');
    if (!aRan) throw new Error('SeederA should have run first');
    return this.createResult(20, Date.now());
  }
}

class MockSeederC extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'SeederC',
    version: '1.0.0',
    dependencies: ['SeederA'],
    priority: 1,
  };

  async clear(): Promise<void> {}
  async seed(): Promise<SeedModuleResult> {
    return this.createResult(30, Date.now());
  }
}

class MockSeederD extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'SeederD',
    version: '1.0.0',
    dependencies: ['SeederB', 'SeederC'],
    priority: 0,
  };

  async clear(): Promise<void> {}
  async seed(): Promise<SeedModuleResult> {
    return this.createResult(40, Date.now());
  }
}

// Circular dependency seeders
class CircularA extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'CircularA',
    version: '1.0.0',
    dependencies: ['CircularB'],
    priority: 0,
  };

  async clear(): Promise<void> {}
  async seed(): Promise<SeedModuleResult> {
    return this.createResult(0, Date.now());
  }
}

class CircularB extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'CircularB',
    version: '1.0.0',
    dependencies: ['CircularA'],
    priority: 0,
  };

  async clear(): Promise<void> {}
  async seed(): Promise<SeedModuleResult> {
    return this.createResult(0, Date.now());
  }
}

describe('SeedRunner', () => {
  let mockPrisma: PrismaClient;
  let runner: SeedRunner;

  beforeEach(() => {
    mockPrisma = {} as PrismaClient;
    runner = createSeedRunner(mockPrisma);
  });

  describe('registerSeeder', () => {
    it('should register a seeder factory', () => {
      runner.registerSeeder((prisma, context) => new MockSeederA(prisma, context));

      expect(runner.getRegisteredSeeders()).toContain('SeederA');
    });

    it('should register multiple seeders', () => {
      runner.registerSeeders([
        (prisma, context) => new MockSeederA(prisma, context),
        (prisma, context) => new MockSeederB(prisma, context),
      ]);

      expect(runner.getRegisteredSeeders()).toHaveLength(2);
    });
  });

  describe('getTopologicalLayers', () => {
    it('should return seeders in dependency layers', () => {
      runner.registerSeeders([
        (prisma, context) => new MockSeederA(prisma, context),
        (prisma, context) => new MockSeederB(prisma, context),
        (prisma, context) => new MockSeederC(prisma, context),
        (prisma, context) => new MockSeederD(prisma, context),
      ]);

      const layers = runner.getTopologicalLayers();

      // Layer 0: SeederA (no deps)
      expect(layers[0]).toContain('SeederA');
      // Layer 1: SeederB, SeederC (depend on A)
      expect(layers[1]).toContain('SeederB');
      expect(layers[1]).toContain('SeederC');
      // Layer 2: SeederD (depends on B and C)
      expect(layers[2]).toContain('SeederD');
    });

    it('should sort within layers by priority', () => {
      runner.registerSeeders([
        (prisma, context) => new MockSeederA(prisma, context),
        (prisma, context) => new MockSeederB(prisma, context),
        (prisma, context) => new MockSeederC(prisma, context),
      ]);

      const layers = runner.getTopologicalLayers();

      // SeederB has priority 0, SeederC has priority 1
      expect(layers[1].indexOf('SeederB')).toBeLessThan(layers[1].indexOf('SeederC'));
    });

    it('should detect missing dependencies', () => {
      runner.registerSeeder((prisma, context) => new MockSeederB(prisma, context));

      expect(() => runner.getTopologicalLayers()).toThrow(
        "Seeder 'SeederB' depends on 'SeederA', but 'SeederA' is not registered",
      );
    });

    it('should detect circular dependencies', () => {
      runner.registerSeeders([
        (prisma, context) => new CircularA(prisma, context),
        (prisma, context) => new CircularB(prisma, context),
      ]);

      expect(() => runner.getTopologicalLayers()).toThrow('Circular dependency detected');
    });
  });

  describe('run', () => {
    it('should execute seeders in order', async () => {
      runner.registerSeeders([
        (prisma, context) => new MockSeederA(prisma, context),
        (prisma, context) => new MockSeederB(prisma, context),
      ]);

      const result = await runner.run({ clear: false });

      expect(result.success).toBe(true);
      expect(result.modules).toHaveLength(2);
      expect(result.totalRecords).toBe(30); // 10 + 20
    });

    it('should respect dry run mode', async () => {
      runner.registerSeeder((prisma, context) => new MockSeederA(prisma, context));

      const result = await runner.run({ dryRun: true });

      expect(result.success).toBe(true);
      expect(result.modules[0].details?.dryRun).toBe(1);
    });

    it('should filter modules when specified', async () => {
      runner.registerSeeders([
        (prisma, context) => new MockSeederA(prisma, context),
        (prisma, context) => new MockSeederB(prisma, context),
      ]);

      const result = await runner.run({
        modules: ['SeederA'],
        clear: false,
      });

      expect(result.modules).toHaveLength(1);
      expect(result.modules[0].name).toBe('SeederA');
    });
  });

  describe('clearSeeders', () => {
    it('should remove all registered seeders', () => {
      runner.registerSeeder((prisma, context) => new MockSeederA(prisma, context));
      expect(runner.getRegisteredSeeders()).toHaveLength(1);

      runner.clearSeeders();
      expect(runner.getRegisteredSeeders()).toHaveLength(0);
    });
  });
});
