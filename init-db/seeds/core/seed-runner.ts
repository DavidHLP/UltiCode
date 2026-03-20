import type { PrismaClient } from '@prisma/client';
import type {
  SeedRunnerOptions,
  SeedResult,
  SeedModuleResult,
  SeederMetadata,
  ISeeder,
  SeedEnvironment,
  FixtureType,
} from './interfaces';
import { SeedContext } from './seed-context';
import { SeedLogger, createLogger } from './seed-logger';
import type { BaseSeeder, SeederFactory } from '../modules/base/base.seeder';

/**
 * Dependency graph node
 */
interface DependencyNode {
  name: string;
  factory: SeederFactory;
  dependencies: string[];
  priority: number;
  layer?: number;
}

/**
 * SeedRunner orchestrates the execution of seeder modules.
 *
 * Features:
 * - Dependency graph resolution with topological sorting
 * - Parallel execution within dependency layers
 * - Circular dependency detection
 * - Transaction support per module
 * - Progress tracking and logging
 */
export class SeedRunner {
  private prisma: PrismaClient;
  private seeders: Map<string, DependencyNode> = new Map();
  private logger: SeedLogger;

  constructor(prisma: PrismaClient, logger?: SeedLogger) {
    this.prisma = prisma;
    this.logger = logger || createLogger();
  }

  /**
   * Register a seeder factory
   */
  registerSeeder(factory: SeederFactory): void {
    // Create a temporary instance to get metadata
    const tempContext = new SeedContext('development', 'standard');
    const instance = factory(this.prisma, tempContext);
    const metadata = instance.metadata;

    this.seeders.set(metadata.name, {
      name: metadata.name,
      factory,
      dependencies: metadata.dependencies,
      priority: metadata.priority,
    });
  }

  /**
   * Register multiple seeder factories
   */
  registerSeeders(factories: SeederFactory[]): void {
    for (const factory of factories) {
      this.registerSeeder(factory);
    }
  }

  /**
   * Build the dependency graph and detect cycles
   */
  private buildDependencyGraph(): void {
    // Validate all dependencies exist
    for (const [name, node] of this.seeders) {
      for (const dep of node.dependencies) {
        if (!this.seeders.has(dep)) {
          throw new Error(
            `Seeder '${name}' depends on '${dep}', but '${dep}' is not registered.`,
          );
        }
      }
    }

    // Detect circular dependencies using DFS
    const visited = new Set<string>();
    const recursionStack = new Set<string>();

    const detectCycle = (name: string, path: string[]): void => {
      if (recursionStack.has(name)) {
        const cycleStart = path.indexOf(name);
        const cycle = path.slice(cycleStart).concat(name);
        throw new Error(
          `Circular dependency detected: ${cycle.join(' -> ')}`,
        );
      }

      if (visited.has(name)) {
        return;
      }

      visited.add(name);
      recursionStack.add(name);

      const node = this.seeders.get(name)!;
      for (const dep of node.dependencies) {
        detectCycle(dep, [...path, name]);
      }

      recursionStack.delete(name);
    };

    for (const name of this.seeders.keys()) {
      if (!visited.has(name)) {
        detectCycle(name, []);
      }
    }
  }

  /**
   * Get topologically sorted layers of seeders.
   * Seeders within the same layer can be executed in parallel.
   */
  getTopologicalLayers(): string[][] {
    this.buildDependencyGraph();

    const layers: string[][] = [];
    const assigned = new Set<string>();

    // Assign layers based on dependency depth
    const getLayer = (name: string): number => {
      const node = this.seeders.get(name)!;
      if (node.layer !== undefined) {
        return node.layer;
      }

      if (node.dependencies.length === 0) {
        node.layer = 0;
        return 0;
      }

      const maxDepLayer = Math.max(
        ...node.dependencies.map(dep => getLayer(dep)),
      );
      node.layer = maxDepLayer + 1;
      return node.layer;
    };

    // Calculate layer for each seeder
    for (const name of this.seeders.keys()) {
      getLayer(name);
    }

    // Group by layer
    const layerMap = new Map<number, DependencyNode[]>();
    for (const node of this.seeders.values()) {
      const layer = node.layer!;
      if (!layerMap.has(layer)) {
        layerMap.set(layer, []);
      }
      layerMap.get(layer)!.push(node);
    }

    // Sort layers and sort within layers by priority
    const sortedLayers = [...layerMap.keys()].sort((a, b) => a - b);
    for (const layerIndex of sortedLayers) {
      const nodesInLayer = layerMap.get(layerIndex)!;
      // Sort by priority within layer
      nodesInLayer.sort((a, b) => a.priority - b.priority);
      layers.push(nodesInLayer.map(n => n.name));
    }

    return layers;
  }

  /**
   * Run all registered seeders
   */
  async run(options: Partial<SeedRunnerOptions> = {}): Promise<SeedResult> {
    const opts: SeedRunnerOptions = {
      environment: (process.env.SEED_ENV as SeedEnvironment) || 'development',
      fixture: (process.env.SEED_FIXTURE as FixtureType) || 'standard',
      parallel: process.env.SEED_PARALLEL !== 'false',
      dryRun: process.env.SEED_DRY_RUN === 'true',
      verbose: process.env.SEED_VERBOSE === 'true',
      clear: true,
      ...options,
    };

    if (opts.verbose) {
      this.logger.setLevel('debug');
    }

    const context = new SeedContext(opts.environment, opts.fixture);
    const layers = this.getTopologicalLayers();
    const moduleResults: SeedModuleResult[] = [];

    this.logger.seedStart(opts.environment, opts.fixture, this.seeders.size);

    const startTime = Date.now();

    try {
      // Filter modules if specified
      const allowedModules = opts.modules
        ? new Set(opts.modules)
        : null;

      for (let layerIndex = 0; layerIndex < layers.length; layerIndex++) {
        let layerModules = layers[layerIndex];

        // Filter if modules option is set
        if (allowedModules) {
          layerModules = layerModules.filter(name => allowedModules.has(name));
          if (layerModules.length === 0) {
            continue;
          }
        }

        this.logger.layerStart(layerIndex, layerModules);

        // Create seeder instances for this layer
        const seedersInLayer = layerModules.map(name => {
          const node = this.seeders.get(name)!;
          return node.factory(this.prisma, context);
        });

        // Clear phase
        if (opts.clear && !opts.dryRun) {
          // Clear in reverse order within layer
          for (let i = seedersInLayer.length - 1; i >= 0; i--) {
            const seeder = seedersInLayer[i];
            context.markRunning(seeder.metadata.name);
            try {
              await seeder.clear();
            } catch (error) {
              const errorMsg = error instanceof Error ? error.message : String(error);
              this.logger.moduleFailed(seeder.metadata.name, `Clear failed: ${errorMsg}`);
              throw error;
            }
          }
        }

        // Seed phase
        if (opts.parallel && layerModules.length > 1) {
          // Parallel execution within layer
          const results = await Promise.all(
            seedersInLayer.map(seeder =>
              this.runSeeder(seeder, context, opts.dryRun),
            ),
          );
          moduleResults.push(...results);
        } else {
          // Sequential execution
          for (const seeder of seedersInLayer) {
            const result = await this.runSeeder(seeder, context, opts.dryRun);
            moduleResults.push(result);
          }
        }
      }

      const seedResult: SeedResult = {
        environment: opts.environment,
        fixture: opts.fixture,
        totalRecords: moduleResults.reduce((sum, r) => sum + r.count, 0),
        totalDuration: Date.now() - startTime,
        modules: moduleResults,
        success: true,
      };

      this.logger.seedComplete(seedResult);
      return seedResult;
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : String(error);

      const seedResult: SeedResult = {
        environment: opts.environment,
        fixture: opts.fixture,
        totalRecords: moduleResults.reduce((sum, r) => sum + r.count, 0),
        totalDuration: Date.now() - startTime,
        modules: moduleResults,
        success: false,
        error: errorMsg,
      };

      this.logger.seedComplete(seedResult);
      return seedResult;
    }
  }

  /**
   * Run a single seeder with error handling
   */
  private async runSeeder(
    seeder: BaseSeeder,
    context: SeedContext,
    dryRun: boolean,
  ): Promise<SeedModuleResult> {
    const name = seeder.metadata.name;

    context.markRunning(name);
    this.logger.moduleStart(name, seeder.metadata.description);

    if (dryRun) {
      const result: SeedModuleResult = {
        name,
        count: 0,
        duration: 0,
        errors: [],
        details: { dryRun: 1 },
      };
      context.markCompleted(name, result);
      this.logger.moduleComplete(result);
      return result;
    }

    try {
      const result = await seeder.seed();
      context.markCompleted(name, result);
      this.logger.moduleComplete(result);
      return result;
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      context.markFailed(name, errorMsg);
      this.logger.moduleFailed(name, errorMsg);
      throw error;
    }
  }

  /**
   * Get list of registered seeders
   */
  getRegisteredSeeders(): string[] {
    return [...this.seeders.keys()];
  }

  /**
   * Clear all seeders (for testing)
   */
  clearSeeders(): void {
    this.seeders.clear();
  }
}

/**
 * Create a seed runner instance
 */
export function createSeedRunner(
  prisma: PrismaClient,
  verbose: boolean = false,
): SeedRunner {
  const logger = createLogger(verbose);
  return new SeedRunner(prisma, logger);
}
