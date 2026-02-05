import { Test, TestingModule } from '@nestjs/testing';
import { UserService } from './user.service';
import { PrismaService } from '../prisma.service';

describe('UserService', () => {
  let service: UserService;
  let prisma: jest.Mocked<PrismaService>;

  const mockUser = {
    id: 'user-123',
    username: 'testuser',
    email: 'test@example.com',
    password: 'hashedpassword',
    avatar: 'avatar.png',
    karma: 0,
    bio: null,
    location: null,
    website: null,
    github: null,
    twitter: null,
    joined_at: new Date(),
    updated_at: new Date(),
  };

  const mockPrismaService = {
    user: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      count: jest.fn(),
      delete: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
    },
    globalRanking: {
      findUnique: jest.fn(),
    },
    submission: {
      findMany: jest.fn(),
    },
    problem: {
      groupBy: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        UserService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
      ],
    }).compile();

    service = module.get<UserService>(UserService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return array of users', async () => {
      (prisma.user.findMany as jest.Mock).mockResolvedValue([mockUser]);

      const result = await service.findAll();

      expect(result).toEqual([mockUser]);
      expect(prisma.user.findMany).toHaveBeenCalled();
    });

    it('should apply pagination when page and limit are provided', async () => {
      (prisma.user.findMany as jest.Mock).mockResolvedValue([mockUser]);

      await service.findAll({}, { page: 1, limit: 10 });

      expect(prisma.user.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          skip: 0,
          take: 10,
          orderBy: { joined_at: 'desc' },
        }),
      );
    });
  });

  describe('count', () => {
    it('should return count of users', async () => {
      (prisma.user.count as jest.Mock).mockResolvedValue(5);

      const result = await service.count();

      expect(result).toBe(5);
      expect(prisma.user.count).toHaveBeenCalled();
    });
  });

  describe('getProfileWithRank', () => {
    it('should return user with rank', async () => {
      (prisma.user.findUnique as jest.Mock).mockResolvedValue(mockUser);
      (prisma.globalRanking.findUnique as jest.Mock).mockResolvedValue({
        user_id: 'user-123',
        global_rank: 42,
      });

      const result = await service.getProfileWithRank('user-123');

      expect(result).toEqual({
        ...mockUser,
        rank: 42,
      });
    });

    it('should return null when user not found', async () => {
      (prisma.user.findUnique as jest.Mock).mockResolvedValue(null);

      const result = await service.getProfileWithRank('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('findOne', () => {
    it('should return user by id', async () => {
      (prisma.user.findUnique as jest.Mock).mockResolvedValue(mockUser);

      const result = await service.findOne('user-123');

      expect(result).toEqual(mockUser);
      expect(prisma.user.findUnique).toHaveBeenCalledWith({
        where: { id: 'user-123' },
      });
    });
  });

  describe('remove', () => {
    it('should delete user', async () => {
      (prisma.user.delete as jest.Mock).mockResolvedValue(mockUser);

      await service.remove('user-123');

      expect(prisma.user.delete).toHaveBeenCalledWith({
        where: { id: 'user-123' },
      });
    });
  });

  describe('findByUsername', () => {
    it('should return user by username', async () => {
      (prisma.user.findUnique as jest.Mock).mockResolvedValue(mockUser);

      const result = await service.findByUsername('testuser');

      expect(result).toEqual(mockUser);
      expect(prisma.user.findUnique).toHaveBeenCalledWith({
        where: { username: 'testuser' },
      });
    });
  });

  describe('findByEmail', () => {
    it('should return user by email', async () => {
      (prisma.user.findFirst as jest.Mock).mockResolvedValue(mockUser);

      const result = await service.findByEmail('test@example.com');

      expect(result).toEqual(mockUser);
      expect(prisma.user.findFirst).toHaveBeenCalledWith({
        where: { email: 'test@example.com' },
      });
    });
  });

  describe('create', () => {
    it('should create and return new user', async () => {
      const userData = {
        id: 'new-user-id',
        username: 'newuser',
        email: 'new@example.com',
        password: 'hashed',
      };

      (prisma.user.create as jest.Mock).mockResolvedValue({
        ...mockUser,
        ...userData,
      });

      const result = await service.create(userData);

      expect(result).toEqual(expect.objectContaining(userData));
      expect(prisma.user.create).toHaveBeenCalledWith({
        data: userData,
      });
    });
  });

  describe('update', () => {
    it('should update and return user', async () => {
      const userData = { bio: 'Updated bio' };

      (prisma.user.update as jest.Mock).mockResolvedValue({
        ...mockUser,
        bio: 'Updated bio',
      });

      const result = await service.update('user-123', userData);

      expect(result.bio).toBe('Updated bio');
      expect(prisma.user.update).toHaveBeenCalledWith({
        where: { id: 'user-123' },
        data: userData,
      });
    });
  });

  describe('getUserStats', () => {
    it('should return user statistics', async () => {
      (prisma.submission.findMany as jest.Mock)
        .mockResolvedValueOnce([
          {
            problem: { difficulty: 'Easy' },
            created_at: new Date(),
          },
          {
            problem: { difficulty: 'Medium' },
            created_at: new Date(),
          },
        ])
        .mockResolvedValueOnce([
          { created_at: new Date() },
          { created_at: new Date() },
        ]);

      (prisma.problem.groupBy as jest.Mock).mockResolvedValue([
        { difficulty: 'Easy', _count: { id: 10 } },
        { difficulty: 'Medium', _count: { id: 20 } },
      ]);

      const result = await service.getUserStats('user-123');

      expect(result).toHaveProperty('stats');
      expect(result).toHaveProperty('streak');
      expect(result).toHaveProperty('totalSolved');
      expect(result).toHaveProperty('heatmap');
    });
  });
});
