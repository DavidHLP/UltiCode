import { FEATURE_FLAGS, isFeatureEnabled } from './feature-flags.config';

describe('FeatureFlags', () => {
  const originalEnv = process.env;

  beforeEach(() => {
    jest.resetModules();
    process.env = { ...originalEnv };
  });

  afterAll(() => {
    process.env = originalEnv;
  });

  describe('USE_NEW_CONTEST_SYSTEM', () => {
    it('should be true when FEATURE_NEW_CONTEST is "true"', () => {
      process.env.FEATURE_NEW_CONTEST = 'true';
      jest.resetModules();
      const { FEATURE_FLAGS } = require('./feature-flags.config');
      expect(FEATURE_FLAGS.USE_NEW_CONTEST_SYSTEM).toBe(true);
    });

    it('should be false by default', () => {
      delete process.env.FEATURE_NEW_CONTEST;
      jest.resetModules();
      const { FEATURE_FLAGS } = require('./feature-flags.config');
      expect(FEATURE_FLAGS.USE_NEW_CONTEST_SYSTEM).toBe(false);
    });
  });

  describe('isFeatureEnabled', () => {
    it('should return boolean value for valid flag', () => {
      expect(typeof isFeatureEnabled('USE_NEW_CONTEST_SYSTEM')).toBe('boolean');
    });
  });
});