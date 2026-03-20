import {
  PasswordHasher,
  DEFAULT_PASSWORD,
  createPasswordHasher,
  hashDefaultPassword,
} from '../../utils/password-hasher';

describe('PasswordHasher', () => {
  describe('constructor and salt rounds', () => {
    it('should use low salt rounds for development', () => {
      const hasher = new PasswordHasher('development');
      expect(hasher.getSaltRounds()).toBe(4);
    });

    it('should use low salt rounds for test', () => {
      const hasher = new PasswordHasher('test');
      expect(hasher.getSaltRounds()).toBe(4);
    });

    it('should use high salt rounds for production', () => {
      const hasher = new PasswordHasher('production');
      expect(hasher.getSaltRounds()).toBe(12);
    });

    it('should allow overriding salt rounds', () => {
      const hasher = new PasswordHasher('development');
      hasher.setSaltRounds(6);
      expect(hasher.getSaltRounds()).toBe(6);
    });

    it('should clear cache when salt rounds change', async () => {
      const hasher = new PasswordHasher('development');
      await hasher.hash('test');
      expect(hasher.getCacheStats().size).toBe(1);

      hasher.setSaltRounds(6);
      expect(hasher.getCacheStats().size).toBe(0);
    });
  });

  describe('hash', () => {
    it('should hash a password', async () => {
      const hasher = new PasswordHasher('development');
      const hash = await hasher.hash('testPassword');

      expect(hash).toMatch(/^\$2[aby]\$/);
      expect(hash).not.toBe('testPassword');
    });

    it('should cache repeated passwords', async () => {
      const hasher = new PasswordHasher('development');

      const hash1 = await hasher.hash('samePassword');
      const hash2 = await hasher.hash('samePassword');

      expect(hash1).toBe(hash2);
      expect(hasher.getCacheStats().size).toBe(1);
    });

    it('should cache different passwords separately', async () => {
      const hasher = new PasswordHasher('development');

      await hasher.hash('password1');
      await hasher.hash('password2');

      expect(hasher.getCacheStats().size).toBe(2);
    });
  });

  describe('getDefaultHash', () => {
    it('should return hash for default password', async () => {
      const hasher = new PasswordHasher('development');
      const hash = await hasher.getDefaultHash();

      expect(hash).toMatch(/^\$2[aby]\$/);
    });

    it('should cache default password hash', async () => {
      const hasher = new PasswordHasher('development');

      await hasher.getDefaultHash();
      await hasher.getDefaultHash();

      expect(hasher.getCacheStats().passwords).toContain(DEFAULT_PASSWORD);
    });
  });

  describe('isHashed', () => {
    it('should detect bcrypt hashes', () => {
      const hasher = new PasswordHasher('development');

      expect(hasher.isHashed('$2a$10$somehash')).toBe(true);
      expect(hasher.isHashed('$2b$10$somehash')).toBe(true);
      expect(hasher.isHashed('$2y$10$somehash')).toBe(true);
    });

    it('should not detect plain passwords', () => {
      const hasher = new PasswordHasher('development');

      expect(hasher.isHashed('plainPassword')).toBe(false);
      expect(hasher.isHashed('password123')).toBe(false);
    });
  });

  describe('ensureHashed', () => {
    it('should return already hashed passwords as-is', async () => {
      const hasher = new PasswordHasher('development');
      const existing = '$2a$10$existinghash';

      const result = await hasher.ensureHashed(existing);

      expect(result).toBe(existing);
    });

    it('should hash plain passwords', async () => {
      const hasher = new PasswordHasher('development');

      const result = await hasher.ensureHashed('plainPassword');

      expect(result).toMatch(/^\$2[aby]\$/);
      expect(result).not.toBe('plainPassword');
    });
  });

  describe('warmup', () => {
    it('should pre-compute hashes for multiple passwords', async () => {
      const hasher = new PasswordHasher('development');

      await hasher.warmup(['pass1', 'pass2', 'pass3']);

      expect(hasher.getCacheStats().size).toBe(3);
    });

    it('should deduplicate passwords', async () => {
      const hasher = new PasswordHasher('development');

      await hasher.warmup(['same', 'same', 'same']);

      expect(hasher.getCacheStats().size).toBe(1);
    });
  });

  describe('clearCache', () => {
    it('should clear all cached hashes', async () => {
      const hasher = new PasswordHasher('development');
      await hasher.hash('test1');
      await hasher.hash('test2');

      hasher.clearCache();

      expect(hasher.getCacheStats().size).toBe(0);
    });
  });
});

describe('createPasswordHasher', () => {
  it('should create hasher with default development environment', () => {
    const hasher = createPasswordHasher();
    expect(hasher.getSaltRounds()).toBe(4);
  });

  it('should create hasher with specified environment', () => {
    const hasher = createPasswordHasher('production');
    expect(hasher.getSaltRounds()).toBe(12);
  });
});

describe('hashDefaultPassword', () => {
  it('should hash the default password', async () => {
    const hash = await hashDefaultPassword();
    expect(hash).toMatch(/^\$2[aby]\$/);
  });

  it('should use environment-specific salt rounds', async () => {
    // Production uses 12 rounds, development uses 4
    // Hash length/format is the same, but production takes longer
    const devHash = await hashDefaultPassword('development');
    const prodHash = await hashDefaultPassword('production');

    // Both should be valid bcrypt hashes
    expect(devHash).toMatch(/^\$2[aby]\$04\$/);
    expect(prodHash).toMatch(/^\$2[aby]\$12\$/);
  });
});
