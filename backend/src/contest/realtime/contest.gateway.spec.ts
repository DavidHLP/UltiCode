import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { Socket } from 'socket.io';
import { ContestGateway } from './contest.gateway';
import { TokenBlacklistService } from '../../auth/token-blacklist.service';
import { UserService } from '../../user/user.service';
import { FEATURE_FLAGS } from '../../common/config/feature-flags.config';

// Mock feature flags
jest.mock('../../common/config/feature-flags.config', () => ({
  FEATURE_FLAGS: {
    ENABLE_REALTIME_RANKING: true,
    ENABLE_FIRST_SOLVE_NOTIFICATIONS: true,
  },
  isFeatureEnabled: jest.fn((flag: string) => true),
}));

describe('ContestGateway', () => {
  let gateway: ContestGateway;
  let jwtService: jest.Mocked<JwtService>;
  let tokenBlacklistService: jest.Mocked<TokenBlacklistService>;
  let userService: jest.Mocked<UserService>;

  const mockUser = {
    id: 'user-1',
    username: 'testuser',
    email: 'test@example.com',
    role: 'user',
  };

  // Valid UUID for testing
  const validContestId = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

  const createMockSocket = (overrides: Partial<Socket> = {}): Socket => {
    return {
      id: 'socket-1',
      handshake: {
        auth: {},
        headers: {},
        query: {},
      },
      join: jest.fn(),
      leave: jest.fn(),
      emit: jest.fn(),
      to: jest.fn().mockReturnThis(),
      disconnect: jest.fn(),
      rooms: new Set(),
      data: {},
      ...overrides,
    } as unknown as Socket;
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ContestGateway,
        {
          provide: JwtService,
          useValue: {
            verifyAsync: jest.fn(),
            decode: jest.fn(),
          },
        },
        {
          provide: TokenBlacklistService,
          useValue: {
            isBlacklisted: jest.fn().mockResolvedValue(false),
          },
        },
        {
          provide: UserService,
          useValue: {
            findOne: jest.fn().mockResolvedValue(mockUser),
          },
        },
      ],
    }).compile();

    gateway = module.get<ContestGateway>(ContestGateway);
    jwtService = module.get(JwtService);
    tokenBlacklistService = module.get(TokenBlacklistService);
    userService = module.get(UserService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('handleConnection', () => {
    it('should allow connection with valid token in auth', async () => {
      const client = createMockSocket({
        handshake: {
          auth: { token: 'valid-token' },
          headers: {},
          query: {},
        },
      });

      jwtService.verifyAsync.mockResolvedValue({
        sub: 'user-1',
        username: 'testuser',
        role: 'user',
      });

      await gateway.handleConnection(client);

      expect(jwtService.verifyAsync).toHaveBeenCalledWith('valid-token');
      expect(client.data.userId).toBe('user-1');
      expect(client.data.username).toBe('testuser');
    });

    it('should allow connection with valid token in headers', async () => {
      const client = createMockSocket({
        handshake: {
          auth: {},
          headers: {
            authorization: 'Bearer valid-token',
          },
          query: {},
        },
      });

      jwtService.verifyAsync.mockResolvedValue({
        sub: 'user-1',
        username: 'testuser',
        role: 'user',
      });

      await gateway.handleConnection(client);

      expect(jwtService.verifyAsync).toHaveBeenCalledWith('valid-token');
      expect(client.data.userId).toBe('user-1');
    });

    it('should disconnect client without token', async () => {
      const client = createMockSocket();

      await gateway.handleConnection(client);

      expect(client.disconnect).toHaveBeenCalledWith(true);
    });

    it('should disconnect client with invalid token', async () => {
      const client = createMockSocket({
        handshake: {
          auth: { token: 'invalid-token' },
          headers: {},
          query: {},
        },
      });

      jwtService.verifyAsync.mockRejectedValue(new Error('Invalid token'));

      await gateway.handleConnection(client);

      expect(client.disconnect).toHaveBeenCalledWith(true);
    });

    it('should disconnect client with blacklisted token', async () => {
      const client = createMockSocket({
        handshake: {
          auth: { token: 'blacklisted-token' },
          headers: {},
          query: {},
        },
      });

      tokenBlacklistService.isBlacklisted.mockResolvedValue(true);

      await gateway.handleConnection(client);

      expect(client.disconnect).toHaveBeenCalledWith(true);
    });

    it('should allow connection with valid token in cookie', async () => {
      const client = createMockSocket({
        handshake: {
          auth: {},
          headers: {
            cookie: 'access_token=cookie-token; other=value',
          },
          query: {},
        },
      });

      jwtService.verifyAsync.mockResolvedValue({
        sub: 'user-1',
        username: 'testuser',
        role: 'user',
      });

      await gateway.handleConnection(client);

      expect(jwtService.verifyAsync).toHaveBeenCalledWith('cookie-token');
      expect(client.data.userId).toBe('user-1');
      expect(client.data.username).toBe('testuser');
    });
  });

  describe('handleDisconnect', () => {
    it('should clean up on disconnect', () => {
      const client = createMockSocket();
      client.data.userId = 'user-1';

      gateway.handleDisconnect(client);

      // No error should be thrown
    });
  });

  describe('handleJoinContest', () => {
    it('should allow user to join contest room', async () => {
      const client = createMockSocket();
      client.data.userId = 'user-1';
      client.data.username = 'testuser';

      const result = await gateway.handleJoinContest(client, validContestId);

      expect(client.join).toHaveBeenCalledWith(`contest:${validContestId}`);
      expect(result).toEqual({
        success: true,
        contestId: validContestId,
        message: `Successfully joined contest ${validContestId}`,
      });
    });

    it('should reject join without authentication', async () => {
      const client = createMockSocket();
      // No userId set

      await expect(
        gateway.handleJoinContest(client, validContestId),
      ).rejects.toThrow('You must be authenticated to join a contest');
    });

    it('should reject join with invalid contest ID format', async () => {
      const client = createMockSocket();
      client.data.userId = 'user-1';
      client.data.username = 'testuser';

      await expect(
        gateway.handleJoinContest(client, 'invalid-id'),
      ).rejects.toThrow('Invalid contest ID format');
    });
  });

  describe('handleLeaveContest', () => {
    it('should allow user to leave contest room', async () => {
      const client = createMockSocket();
      client.data.userId = 'user-1';
      client.rooms = new Set([`contest:${validContestId}`]);

      const result = await gateway.handleLeaveContest(client, validContestId);

      expect(client.leave).toHaveBeenCalledWith(`contest:${validContestId}`);
      expect(result).toEqual({
        success: true,
        contestId: validContestId,
        message: `Successfully left contest ${validContestId}`,
      });
    });

    it('should handle leave when not in room', async () => {
      const client = createMockSocket();
      client.data.userId = 'user-1';
      client.rooms = new Set();

      const result = await gateway.handleLeaveContest(client, validContestId);

      expect(result).toEqual({
        success: true,
        contestId: validContestId,
        message: `You were not in contest ${validContestId}`,
      });
    });

    it('should reject leave with invalid contest ID format', async () => {
      const client = createMockSocket();
      client.data.userId = 'user-1';

      await expect(
        gateway.handleLeaveContest(client, 'invalid-id'),
      ).rejects.toThrow('Invalid contest ID format');
    });
  });

  describe('emitRankingUpdate', () => {
    it('should emit ranking update to contest room when feature enabled', () => {
      const mockTo = jest.fn().mockReturnThis();
      const mockEmit = jest.fn();

      gateway['server'] = {
        to: mockTo,
        emit: mockEmit,
      } as any;

      const rankingData = {
        contestId: validContestId,
        rankings: [
          {
            rank: 1,
            userId: 'user-1',
            username: 'user1',
            score: 100,
            solvedCount: 5,
          },
          {
            rank: 2,
            userId: 'user-2',
            username: 'user2',
            score: 90,
            solvedCount: 4,
          },
        ],
      };

      gateway.emitRankingUpdate(validContestId, rankingData);

      expect(mockTo).toHaveBeenCalledWith(`contest:${validContestId}`);
      // The gateway adds updatedAt automatically, so we check for partial match
      expect(mockEmit).toHaveBeenCalledWith(
        'ranking_update',
        expect.objectContaining({
          ...rankingData,
          updatedAt: expect.any(Date),
        }),
      );
    });

    it('should use provided updatedAt if given', () => {
      const mockTo = jest.fn().mockReturnThis();
      const mockEmit = jest.fn();

      gateway['server'] = {
        to: mockTo,
        emit: mockEmit,
      } as any;

      const providedDate = new Date('2024-01-01T00:00:00Z');
      const rankingData = {
        contestId: validContestId,
        rankings: [],
        updatedAt: providedDate,
      };

      gateway.emitRankingUpdate(validContestId, rankingData);

      expect(mockEmit).toHaveBeenCalledWith(
        'ranking_update',
        expect.objectContaining({
          updatedAt: providedDate,
        }),
      );
    });
  });

  describe('emitFirstSolve', () => {
    it('should emit first solve notification', () => {
      const mockTo = jest.fn().mockReturnThis();
      const mockEmit = jest.fn();

      gateway['server'] = {
        to: mockTo,
        emit: mockEmit,
      } as any;

      const firstSolveData = {
        contestId: validContestId,
        problemId: 'problem-1',
        problemTitle: 'Test Problem',
        userId: 'user-1',
        username: 'testuser',
        solvedAt: new Date(),
      };

      gateway.emitFirstSolve(validContestId, firstSolveData);

      expect(mockTo).toHaveBeenCalledWith(`contest:${validContestId}`);
      expect(mockEmit).toHaveBeenCalledWith('first_solve', firstSolveData);
    });
  });

  describe('emitAnnouncement', () => {
    it('should emit announcement to contest room', () => {
      const mockTo = jest.fn().mockReturnThis();
      const mockEmit = jest.fn();

      gateway['server'] = {
        to: mockTo,
        emit: mockEmit,
      } as any;

      const announcementData = {
        id: 'announcement-1',
        contestId: validContestId,
        title: 'Test Announcement',
        content: 'This is a test',
        createdAt: new Date(),
      };

      gateway.emitAnnouncement(validContestId, announcementData);

      expect(mockTo).toHaveBeenCalledWith(`contest:${validContestId}`);
      expect(mockEmit).toHaveBeenCalledWith('announcement', announcementData);
    });
  });

  describe('emitContestStatus', () => {
    it('should emit contest status update', () => {
      const mockTo = jest.fn().mockReturnThis();
      const mockEmit = jest.fn();

      gateway['server'] = {
        to: mockTo,
        emit: mockEmit,
      } as any;

      const statusData = {
        contestId: validContestId,
        status: 'running' as const,
        startedAt: new Date(),
        endsAt: new Date(Date.now() + 3600000),
      };

      gateway.emitContestStatus(validContestId, statusData);

      expect(mockTo).toHaveBeenCalledWith(`contest:${validContestId}`);
      expect(mockEmit).toHaveBeenCalledWith('contest_status', statusData);
    });
  });

  describe('emitSubmissionResult', () => {
    it('should emit submission result to specific user', () => {
      const mockTo = jest.fn().mockReturnThis();
      const mockEmit = jest.fn();

      gateway['server'] = {
        to: mockTo,
        emit: mockEmit,
      } as any;

      const submissionData = {
        submissionId: 'submission-1',
        contestId: validContestId,
        problemId: 'problem-1',
        userId: 'user-1',
        status: 'accepted',
        score: 100,
        judgedAt: new Date(),
      };

      gateway.emitSubmissionResult('user-1', submissionData);

      expect(mockTo).toHaveBeenCalledWith('user:user-1');
      expect(mockEmit).toHaveBeenCalledWith(
        'submission_result',
        submissionData,
      );
    });
  });

  describe('getConnectionCount', () => {
    it('should return 0 when no connections', () => {
      gateway['server'] = {
        sockets: {
          sockets: new Map(),
        },
      } as any;

      const count = gateway.getConnectionCount();

      expect(count).toBe(0);
    });

    it('should return correct connection count', () => {
      const sockets = new Map();
      sockets.set('socket-1', {});
      sockets.set('socket-2', {});
      sockets.set('socket-3', {});

      gateway['server'] = {
        sockets: {
          sockets,
        },
      } as any;

      const count = gateway.getConnectionCount();

      expect(count).toBe(3);
    });
  });

  describe('getContestRoomSize', () => {
    it('should return 0 when no clients in room', () => {
      const mockAdapter = {
        rooms: new Map(),
      };

      gateway['server'] = {
        sockets: {
          adapter: mockAdapter,
        },
      } as any;

      const count = gateway.getContestRoomSize(validContestId);

      expect(count).toBe(0);
    });

    it('should return correct room size', () => {
      const roomSet = new Set(['socket-1', 'socket-2', 'socket-3']);
      const rooms = new Map();
      rooms.set(`contest:${validContestId}`, roomSet);

      const mockAdapter = {
        rooms,
      };

      gateway['server'] = {
        sockets: {
          adapter: mockAdapter,
        },
      } as any;

      const count = gateway.getContestRoomSize(validContestId);

      expect(count).toBe(3);
    });
  });
});
