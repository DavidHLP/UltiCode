import { Test, TestingModule } from '@nestjs/testing';
import { UserService } from './user.service';
import { Repository } from 'typeorm';
import { User, UserRole } from './user.entity';
import { PrismaService } from '../prisma.service';

describe('UserService', () => {
  let service: UserService;
  let usersRepository: jest.Mocked<Repository<User>>;
  let prisma: jest.Mocked<PrismaService>;

  const mockUser = {
    id: 'user-123',
    username: 'testuser',
    email: 'test@example.com',
    name: 'Test User',
    role: UserRole.USER,
    joined_at: new Date(),
    avatar: 'avatar.png',
    is_active: true,
    is_banned: false,
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        UserService,
        {
          provide: 'UserRepository',
          useValue: {
            find: jest.fn(),
            findOneBy: jest.fn(),
            count: jest.fn(),
            delete: jest.fn(),
            create: jest.fn(),
            save: jest.fn(),
            update: jest.fn(),
          },
        },
        {
          provide: PrismaService,
          useValue: {
            globalRanking: {
              findUnique: jest.fn(),
            },
            submission: {
              findMany: jest.fn(),
            },
            problem: {
              groupBy: jest.fn(),
            },
          },
        },
      ],
    }).compile();

    service = module.get<UserService>(UserService);
    usersRepository = module.get('UserRepository');
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAll', () => {
    it('should return array of users', async () => {
      usersRepository.find.mockResolvedValue([mockUser]);

      const result = await service.findAll();

      expect(result).toEqual([mockUser]);
      expect(usersRepository.find).toHaveBeenCalled();
    });

    it('should apply pagination when page and limit are provided', async () => {
      usersRepository.find.mockResolvedValue([mockUser]);

      await service.findAll({}, { page: 1, limit: 10 });

      expect(usersRepository.find).toHaveBeenCalledWith(
        expect.objectContaining({
          skip: 0,
          take: 10,
          order: { joined_at: 'DESC' },
        }),
      );
    });
  });

  describe('count', () => {
    it('should return count of users', async () => {
      usersRepository.count.mockResolvedValue(5);

      const result = await service.count();

      expect(result).toBe(5);
      expect(usersRepository.count).toHaveBeenCalled();
    });
  });

  describe('getProfileWithRank', () => {
    it('should return user with rank', async () => {
      usersRepository.findOneBy.mockResolvedValue(mockUser);
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
      usersRepository.findOneBy.mockResolvedValue(null);

      const result = await service.getProfileWithRank('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('findOne', () => {
    it('should return user by id', async () => {
      usersRepository.findOneBy.mockResolvedValue(mockUser);

      const result = await service.findOne('user-123');

      expect(result).toEqual(mockUser);
      expect(usersRepository.findOneBy).toHaveBeenCalledWith({
        id: 'user-123',
      });
    });
  });

  describe('remove', () => {
    it('should delete user', async () => {
      usersRepository.delete.mockResolvedValue({ affected: 1 } as never);

      await service.remove('user-123');

      expect(usersRepository.delete).toHaveBeenCalledWith('user-123');
    });
  });

  describe('findByUsername', () => {
    it('should return user by username', async () => {
      usersRepository.findOneBy.mockResolvedValue(mockUser);

      const result = await service.findByUsername('testuser');

      expect(result).toEqual(mockUser);
      expect(usersRepository.findOneBy).toHaveBeenCalledWith({
        username: 'testuser',
      });
    });
  });

  describe('findByEmail', () => {
    it('should return user by email', async () => {
      usersRepository.findOneBy.mockResolvedValue(mockUser);

      const result = await service.findByEmail('test@example.com');

      expect(result).toEqual(mockUser);
      expect(usersRepository.findOneBy).toHaveBeenCalledWith({
        email: 'test@example.com',
      });
    });
  });

  describe('create', () => {
    it('should create and return new user', async () => {
      const userData = {
        username: 'newuser',
        email: 'new@example.com',
      };

      usersRepository.create.mockReturnValue(userData as User);
      usersRepository.save.mockResolvedValue(userData as User);

      const result = await service.create(userData);

      expect(result).toEqual(userData);
      expect(usersRepository.create).toHaveBeenCalledWith(userData);
      expect(usersRepository.save).toHaveBeenCalled();
    });
  });

  describe('update', () => {
    it('should update and return user', async () => {
      const userData = { name: 'Updated Name' };

      usersRepository.update.mockResolvedValue({} as never);
      usersRepository.findOneBy.mockResolvedValue({
        ...mockUser,
        name: 'Updated Name',
      });

      const result = await service.update('user-123', userData);

      expect(result.name).toBe('Updated Name');
      expect(usersRepository.update).toHaveBeenCalledWith('user-123', userData);
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
