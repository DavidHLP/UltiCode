import type { SeedModuleResult, SeedResult, BatchProgress } from './interfaces';
import type { ModuleProgress } from './seed-context';

/**
 * Log level for the seed logger
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'silent';

/**
 * Log level priority (lower = more verbose)
 */
const LOG_LEVEL_PRIORITY: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
  silent: 4,
};

/**
 * Seed logger with progress tracking and formatted output
 */
export class SeedLogger {
  private level: LogLevel;
  private startTime: number = 0;
  private moduleCount: number = 0;
  private completedCount: number = 0;

  constructor(level: LogLevel = 'info') {
    this.level = level;
  }

  /**
   * Check if a log level should be output
   */
  private shouldLog(level: LogLevel): boolean {
    return LOG_LEVEL_PRIORITY[level] >= LOG_LEVEL_PRIORITY[this.level];
  }

  /**
   * Format duration in human-readable format
   */
  private formatDuration(ms: number): string {
    if (ms < 1000) {
      return `${ms}ms`;
    }
    if (ms < 60000) {
      return `${(ms / 1000).toFixed(2)}s`;
    }
    const minutes = Math.floor(ms / 60000);
    const seconds = ((ms % 60000) / 1000).toFixed(1);
    return `${minutes}m ${seconds}s`;
  }

  /**
   * Create a progress bar string
   */
  private createProgressBar(percentage: number, width: number = 20): string {
    const filled = Math.round((percentage / 100) * width);
    const empty = width - filled;
    const bar = '█'.repeat(filled) + '░'.repeat(empty);
    return `[${bar}] ${percentage.toFixed(0)}%`;
  }

  // ============ Lifecycle Methods ============

  /**
   * Log the start of a seed run
   */
  seedStart(environment: string, fixture: string, moduleCount: number): void {
    this.startTime = Date.now();
    this.moduleCount = moduleCount;
    this.completedCount = 0;

    if (!this.shouldLog('info')) return;

    console.log('');
    console.log('╔════════════════════════════════════════════════════════════════╗');
    console.log('║                    DATABASE SEED RUNNER                         ║');
    console.log('╚════════════════════════════════════════════════════════════════╝');
    console.log('');
    console.log(`  Environment: ${environment}`);
    console.log(`  Fixture:     ${fixture}`);
    console.log(`  Modules:     ${moduleCount}`);
    console.log('');
    console.log('─'.repeat(68));
    console.log('');
  }

  /**
   * Log the completion of a seed run
   */
  seedComplete(result: SeedResult): void {
    if (!this.shouldLog('info')) return;

    console.log('');
    console.log('─'.repeat(68));
    console.log('');

    if (result.success) {
      console.log('✅ SEED COMPLETED SUCCESSFULLY');
    } else {
      console.log('❌ SEED FAILED');
      if (result.error) {
        console.log(`   Error: ${result.error}`);
      }
    }

    console.log('');
    this.summary(result.modules);
  }

  // ============ Module Methods ============

  /**
   * Log the start of a module
   */
  moduleStart(name: string, description?: string): void {
    if (!this.shouldLog('info')) return;

    const desc = description ? ` - ${description}` : '';
    console.log(`⏳ ${name}${desc}`);
  }

  /**
   * Log the completion of a module
   */
  moduleComplete(result: SeedModuleResult): void {
    this.completedCount++;

    if (!this.shouldLog('info')) return;

    const duration = this.formatDuration(result.duration);
    let details = '';

    if (result.details && Object.keys(result.details).length > 0) {
      const parts = Object.entries(result.details)
        .map(([key, count]) => `${key}: ${count}`)
        .join(', ');
      details = ` (${parts})`;
    }

    console.log(`   ✓ ${result.name}: ${result.count} records${details} [${duration}]`);

    if (result.errors.length > 0 && this.shouldLog('warn')) {
      for (const error of result.errors) {
        console.log(`   ⚠ ${error}`);
      }
    }
  }

  /**
   * Log a module failure
   */
  moduleFailed(name: string, error: string): void {
    if (!this.shouldLog('error')) return;

    console.error(`   ✗ ${name}: FAILED`);
    console.error(`     ${error}`);
  }

  /**
   * Log a module being skipped
   */
  moduleSkipped(name: string, reason?: string): void {
    if (!this.shouldLog('info')) return;

    const msg = reason ? ` (${reason})` : '';
    console.log(`   ⊘ ${name}: skipped${msg}`);
  }

  // ============ Progress Methods ============

  /**
   * Log batch progress (for large inserts)
   */
  progress(progress: BatchProgress): void {
    if (!this.shouldLog('debug')) return;

    const bar = this.createProgressBar(progress.percentage);
    process.stdout.write(`\r     ${bar} ${progress.processed}/${progress.total}`);

    if (progress.processed === progress.total) {
      console.log(''); // New line after completion
    }
  }

  /**
   * Log layer execution start
   */
  layerStart(layerIndex: number, modules: string[]): void {
    if (!this.shouldLog('debug')) return;

    console.log(`\n📦 Layer ${layerIndex + 1}: ${modules.join(', ')}`);
  }

  // ============ Summary Methods ============

  /**
   * Print a summary of all module results
   */
  summary(results: SeedModuleResult[]): void {
    if (!this.shouldLog('info')) return;

    const totalDuration = Date.now() - this.startTime;
    const totalRecords = results.reduce((sum, r) => sum + r.count, 0);
    const failed = results.filter(r => r.errors.length > 0);

    console.log('┌────────────────────────────────────────────────────────────────┐');
    console.log('│                          SUMMARY                               │');
    console.log('├────────────────────────────────────────────────────────────────┤');
    console.log(`│  Total Records:  ${totalRecords.toString().padEnd(45)}│`);
    console.log(`│  Total Duration: ${this.formatDuration(totalDuration).padEnd(45)}│`);
    console.log(`│  Modules:        ${results.length} completed, ${failed.length} with warnings`.padEnd(64) + '│');
    console.log('└────────────────────────────────────────────────────────────────┘');

    // Module breakdown
    if (this.shouldLog('debug')) {
      console.log('');
      console.log('Module breakdown:');
      for (const result of results) {
        const pct = ((result.duration / totalDuration) * 100).toFixed(1);
        console.log(`  ${result.name.padEnd(25)} ${result.count.toString().padStart(6)} records  ${this.formatDuration(result.duration).padStart(8)}  (${pct}%)`);
      }
    }
  }

  // ============ General Logging ============

  /**
   * Log a debug message
   */
  debug(message: string): void {
    if (!this.shouldLog('debug')) return;
    console.log(`  [DEBUG] ${message}`);
  }

  /**
   * Log an info message
   */
  info(message: string): void {
    if (!this.shouldLog('info')) return;
    console.log(`  ${message}`);
  }

  /**
   * Log a warning message
   */
  warn(message: string): void {
    if (!this.shouldLog('warn')) return;
    console.warn(`  ⚠ ${message}`);
  }

  /**
   * Log an error message
   */
  error(message: string): void {
    if (!this.shouldLog('error')) return;
    console.error(`  ❌ ${message}`);
  }

  /**
   * Set log level
   */
  setLevel(level: LogLevel): void {
    this.level = level;
  }
}

/**
 * Create a default logger instance
 */
export function createLogger(verbose: boolean = false): SeedLogger {
  return new SeedLogger(verbose ? 'debug' : 'info');
}
