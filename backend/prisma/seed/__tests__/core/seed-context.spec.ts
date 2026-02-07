import { SeedContext, CONTEXT_KEYS } from '../../core/seed-context';

describe('SeedContext', () => {
  let context: SeedContext;

  beforeEach(() => {
    context = new SeedContext('development', 'standard');
  });

  describe('data storage', () => {
    it('should store and retrieve typed values', () => {
      context.set('testKey', 'testValue');
      expect(context.get<string>('testKey')).toBe('testValue');
    });

    it('should return undefined for non-existent keys', () => {
      expect(context.get('nonExistent')).toBeUndefined();
    });

    it('should throw when getOrThrow is called for missing key', () => {
      expect(() => context.getOrThrow('missing')).toThrow(
        "SeedContext: Key 'missing' not found",
      );
    });

    it('should return value when getOrThrow is called for existing key', () => {
      context.set('exists', 42);
      expect(context.getOrThrow<number>('exists')).toBe(42);
    });

    it('should check if key exists', () => {
      context.set('key', 'value');
      expect(context.has('key')).toBe(true);
      expect(context.has('noKey')).toBe(false);
    });

    it('should delete keys', () => {
      context.set('key', 'value');
      expect(context.delete('key')).toBe(true);
      expect(context.has('key')).toBe(false);
    });

    it('should clear all data', () => {
      context.set('key1', 'value1');
      context.set('key2', 'value2');
      context.clearData();
      expect(context.has('key1')).toBe(false);
      expect(context.has('key2')).toBe(false);
    });
  });

  describe('progress tracking', () => {
    it('should mark modules as pending', () => {
      context.markPending('TestModule');
      expect(context.getProgress('TestModule')?.status).toBe('pending');
    });

    it('should mark modules as running with start time', () => {
      context.markRunning('TestModule');
      const progress = context.getProgress('TestModule');
      expect(progress?.status).toBe('running');
      expect(progress?.startTime).toBeDefined();
    });

    it('should mark modules as completed with result', () => {
      context.markRunning('TestModule');
      context.markCompleted('TestModule', {
        name: 'TestModule',
        count: 10,
        duration: 100,
        errors: [],
      });
      const progress = context.getProgress('TestModule');
      expect(progress?.status).toBe('completed');
      expect(progress?.result?.count).toBe(10);
    });

    it('should mark modules as failed with error', () => {
      context.markRunning('TestModule');
      context.markFailed('TestModule', 'Test error');
      const progress = context.getProgress('TestModule');
      expect(progress?.status).toBe('failed');
      expect(progress?.error).toBe('Test error');
    });

    it('should get modules by status', () => {
      context.markPending('Module1');
      context.markRunning('Module2');
      context.markCompleted('Module3', {
        name: 'Module3',
        count: 0,
        duration: 0,
        errors: [],
      });

      expect(context.getPendingModules()).toContain('Module1');
      expect(context.getModulesByStatus('running')).toContain('Module2');
      expect(context.getCompletedModules()).toContain('Module3');
    });

    it('should check if module is completed', () => {
      context.markCompleted('Done', {
        name: 'Done',
        count: 0,
        duration: 0,
        errors: [],
      });
      expect(context.isCompleted('Done')).toBe(true);
      expect(context.isCompleted('NotDone')).toBe(false);
    });

    it('should check if dependencies are completed', () => {
      context.markCompleted('Dep1', {
        name: 'Dep1',
        count: 0,
        duration: 0,
        errors: [],
      });
      context.markCompleted('Dep2', {
        name: 'Dep2',
        count: 0,
        duration: 0,
        errors: [],
      });

      expect(context.areDependenciesCompleted(['Dep1', 'Dep2'])).toBe(true);
      expect(context.areDependenciesCompleted(['Dep1', 'Dep3'])).toBe(false);
    });

    it('should calculate total duration', () => {
      context.markCompleted('M1', {
        name: 'M1',
        count: 0,
        duration: 100,
        errors: [],
      });
      context.markCompleted('M2', {
        name: 'M2',
        count: 0,
        duration: 200,
        errors: [],
      });

      expect(context.getTotalDuration()).toBe(300);
    });

    it('should calculate total records', () => {
      context.markCompleted('M1', {
        name: 'M1',
        count: 10,
        duration: 0,
        errors: [],
      });
      context.markCompleted('M2', {
        name: 'M2',
        count: 20,
        duration: 0,
        errors: [],
      });

      expect(context.getTotalRecords()).toBe(30);
    });
  });

  describe('CONTEXT_KEYS', () => {
    it('should have standard keys defined', () => {
      expect(CONTEXT_KEYS.USER_IDS).toBe('userIds');
      expect(CONTEXT_KEYS.PROBLEM_IDS).toBe('problemIds');
      expect(CONTEXT_KEYS.DEFAULT_PASSWORD_HASH).toBe('defaultPasswordHash');
    });
  });
});
