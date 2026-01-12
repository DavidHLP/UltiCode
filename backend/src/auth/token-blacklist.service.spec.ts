import { Test, TestingModule } from '@nestjs/testing';
import { TokenBlacklistService } from './token-blacklist.service';
import Redis from 'ioredis';

describe('TokenBlacklistService', () => {
  let service: TokenBlacklistService;
  let redis: jest.Mocked<Redis>;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TokenBlacklistService,
        {
          provide: 'Redis',
          useValue: {
            set: jest.fn(),
            get: jest.fn(),
            del: jest.fn(),
            quit: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<TokenBlacklistService>(TokenBlacklistService);
    redis = module.get('Redis');

    // Replace the internal redis instance with our mock
    (service as any).redis = redis;
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('addToBlacklist', () => {
    it('should add token to blacklist with default TTL', async () => {
      redis.set.mockResolvedValue('OK');

      await service.addToBlacklist('test-token');

      expect(redis.set).toHaveBeenCalledWith(
        expect.stringContaining('blacklist:token:'),
        '1',
        'EX',
        7 * 24 * 60 * 60, // 7 days
      );
    });

    it('should add token to blacklist with custom TTL', async () => {
      redis.set.mockResolvedValue('OK');

      await service.addToBlacklist('test-token', 3600);

      expect(redis.set).toHaveBeenCalledWith(
        expect.stringContaining('blacklist:token:'),
        '1',
        'EX',
        3600,
      );
    });
  });

  describe('isBlacklisted', () => {
    it('should return true for blacklisted token', async () => {
      redis.get.mockResolvedValue('1');

      const result = await service.isBlacklisted('blacklisted-token');

      expect(result).toBe(true);
      expect(redis.get).toHaveBeenCalledWith(
        expect.stringContaining('blacklist:token:'),
      );
    });

    it('should return false for non-blacklisted token', async () => {
      redis.get.mockResolvedValue(null);

      const result = await service.isBlacklisted('valid-token');

      expect(result).toBe(false);
    });
  });

  describe('removeFromBlacklist', () => {
    it('should remove token from blacklist', async () => {
      redis.del.mockResolvedValue(1);

      await service.removeFromBlacklist('blacklisted-token');

      expect(redis.del).toHaveBeenCalledWith(
        expect.stringContaining('blacklist:token:'),
      );
    });
  });

  describe('onModuleDestroy', () => {
    it('should close Redis connection on module destroy', async () => {
      redis.quit.mockResolvedValue('OK');

      await service.onModuleDestroy();

      expect(redis.quit).toHaveBeenCalled();
    });
  });
});
