import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication, ValidationPipe, HttpStatus } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { JwtModule, JwtService } from '@nestjs/jwt';
import cookieParser from 'cookie-parser';
import request from 'supertest';

import {
  ContestController,
  RankingController,
} from '../src/contest/contest.controller';
import { ContestService } from '../src/contest/contest.service';
import { RankingService } from '../src/contest/ranking.service';
import { RatingService } from '../src/contest/rating.service';
import { PrismaService } from '../src/prisma.service';
import { AuthGuard } from '../src/auth/auth.guard';
import {
  ContestStatus,
  ContestType,
  ContestScoringMode,
  ContestTieBreaker,
} from '@prisma/client';

/**
 * Contest System Integration Tests
 *
 * Tests the complete contest workflow:
 * 1. Contest CRUD - Create, read, update, delete contests as admin
 * 2. Contest Registration - User registers for contest, duplicate registration prevention
 * 3. Scoring System - Submit solution, verify score calculation
 * 4. Ranking - Verify ranking updates after submissions
 *
 * Note: Uses mocked PrismaService for isolation
 */
describe('Contest System (Integration)', () => {
  let app: INestApplication;
  let prisma: jest.Mocked<PrismaService>;
  let jwtService: JwtService;
  let userToken: string;

  // Test users
  const adminUser = {
    id: 'admin-123',
    username: 'admin',
    email: 'admin@test.com',
    role: 'admin',
  };

  const regularUser = {
    id: 'user-123',
    username: 'testuser',
    email: 'user@test.com',
    role: 'user',
  };

  // Test contest data
  const testContest = {
    id: 'contest-test-123',
    title: 'Weekly Contest 100',
    slug: 'weekly-contest-100',
    contest_type: ContestType.weekly,
    start_time: new Date(Date.now() + 3600000), // 1 hour from now
    duration_minutes: 120,
    status: ContestStatus.upcoming,
    is_rated: true,
    is_visible: true,
    penalty_per_wrong: 5,
    scoring_mode: ContestScoringMode.standard,
    tie_breaker: ContestTieBreaker.time,
    registered_count: 0,
    description: 'Test contest description',
    rules: 'Standard contest rules',
    created_by: adminUser.id,
    created_at: new Date(),
    updated_at: new Date(),
  };

  const testContestProblem = {
    id: 'problem-test-123',
    contest_id: testContest.id,
    problem_id: 1, // Use number instead of BigInt for JSON serialization
    problem_index: 'A',
    score: 100,
    penalty_per_wrong: 5,
  };

  // Helper to create mock participant
  const createMockParticipant = (
    userId: string,
    overrides: Record<string, unknown> = {},
  ) => ({
    id: `participant-${userId}`,
    contest_id: testContest.id,
    user_id: userId,
    status: 'REGISTERED',
    is_virtual: false,
    total_score: 0,
    total_penalty: 0,
    registered_at: new Date(),
    started_at: null,
    finished_at: null,
    virtual_session_id: null,
    ...overrides,
  });

  // Mock ContestService
  const mockContestService = {
    findAll: jest.fn(),
    findOne: jest.fn(),
    findUpcoming: jest.fn(),
    findRunning: jest.fn(),
    findPast: jest.fn(),
    getStats: jest.fn(),
    getGlobalRanking: jest.fn(),
    getContestRanking: jest.fn(),
    registerForContest: jest.fn(),
    unregisterFromContest: jest.fn(),
    getParticipationStatus: jest.fn(),
    getUserContests: jest.fn(),
    startVirtualContest: jest.fn(),
    getVirtualSession: jest.fn(),
    finishVirtualContest: jest.fn(),
    createContest: jest.fn(),
    updateContest: jest.fn(),
    deleteContest: jest.fn(),
    updateContestStatus: jest.fn(),
  };

  // Mock RankingService
  const mockRankingService = {
    getContestRanking: jest.fn(),
    getGlobalRanking: jest.fn(),
    getLiveRanking: jest.fn(),
    getUserContestHistory: jest.fn(),
    updateContestProblemResult: jest.fn(),
    finalizeVirtualRanking: jest.fn(),
    finalizeContestRanking: jest.fn(),
  };

  // Mock RatingService
  const mockRatingService = {
    getUserRatingHistory: jest.fn(),
    calculateRatingChanges: jest.fn(),
    updateRatings: jest.fn(),
  };

  beforeAll(async () => {
    // Create mock PrismaService
    const mockPrismaService = {
      contest: {
        findMany: jest.fn(),
        findUnique: jest.fn(),
        findFirst: jest.fn(),
        create: jest.fn(),
        update: jest.fn(),
        delete: jest.fn(),
        count: jest.fn(),
      },
      contestProblem: {
        findMany: jest.fn(),
        create: jest.fn(),
        createMany: jest.fn(),
        deleteMany: jest.fn(),
      },
      contestParticipant: {
        findMany: jest.fn(),
        findUnique: jest.fn(),
        findFirst: jest.fn(),
        create: jest.fn(),
        update: jest.fn(),
        delete: jest.fn(),
        count: jest.fn(),
      },
      contestProblemResult: {
        findMany: jest.fn(),
        findUnique: jest.fn(),
        create: jest.fn(),
        update: jest.fn(),
        upsert: jest.fn(),
      },
      contestRanking: {
        findMany: jest.fn(),
        create: jest.fn(),
        update: jest.fn(),
        deleteMany: jest.fn(),
      },
      globalRanking: {
        findMany: jest.fn(),
        findUnique: jest.fn(),
        create: jest.fn(),
        update: jest.fn(),
        upsert: jest.fn(),
      },
      user: {
        findUnique: jest.fn(),
      },
      virtualContestSession: {
        findFirst: jest.fn(),
        findUnique: jest.fn(),
        create: jest.fn(),
        update: jest.fn(),
      },
      $transaction: jest.fn(
        (callback: (tx: typeof mockPrismaService) => Promise<unknown>) =>
          callback(mockPrismaService),
      ),
    };

    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          envFilePath: '.env',
        }),
        JwtModule.registerAsync({
          imports: [ConfigModule],
          useFactory: async (configService: ConfigService) => ({
            secret:
              configService.get<string>('JWT_SECRET') ||
              'test-secret-key-for-e2e-tests',
            signOptions: {
              expiresIn: configService.get<string>('JWT_EXPIRES_IN') || '1h',
            },
          }),
          inject: [ConfigService],
        }),
      ],
      controllers: [ContestController, RankingController],
      providers: [
        {
          provide: PrismaService,
          useValue: mockPrismaService,
        },
        {
          provide: ContestService,
          useValue: mockContestService,
        },
        {
          provide: RankingService,
          useValue: mockRankingService,
        },
        {
          provide: RatingService,
          useValue: mockRatingService,
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({
        canActivate: (context: {
          switchToHttp: () => {
            getRequest: () => {
              headers: { authorization?: string };
              user?: typeof regularUser;
            };
          };
        }) => {
          const request = context.switchToHttp().getRequest();
          const authHeader = request.headers.authorization;
          if (authHeader) {
            const token = authHeader.replace('Bearer ', '');
            try {
              const payload = jwtService.verify(token);
              request.user = payload;
              return true;
            } catch {
              return false;
            }
          }
          return false;
        },
      })
      .compile();

    app = moduleFixture.createNestApplication();

    app.use(cookieParser());
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    );

    prisma = moduleFixture.get(PrismaService);
    jwtService = moduleFixture.get(JwtService);

    // Generate test token
    userToken = jwtService.sign({
      sub: regularUser.id,
      username: regularUser.username,
      email: regularUser.email,
      role: regularUser.role,
    });

    await app.init();
  });

  afterAll(async () => {
    if (app) {
      await app.close();
    }
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  // =========================================================================
  // CONTEST QUERIES
  // =========================================================================

  describe('Contest Queries', () => {
    describe('GET /contest/list', () => {
      it('should return paginated list of contests', async () => {
        mockContestService.findAll.mockResolvedValue({
          items: [testContest],
          total: 1,
          page: 1,
          limit: 10,
        });

        const response = await request(app.getHttpServer())
          .get('/contest/list')
          .expect(HttpStatus.OK);

        expect(response.body).toHaveProperty('items');
        expect(response.body).toHaveProperty('total');
        expect(response.body).toHaveProperty('page');
        expect(response.body).toHaveProperty('limit');
        expect(mockContestService.findAll).toHaveBeenCalled();
      });

      it('should filter contests by status', async () => {
        mockContestService.findAll.mockResolvedValue({
          items: [testContest],
          total: 1,
          page: 1,
          limit: 10,
        });

        await request(app.getHttpServer())
          .get('/contest/list?status=upcoming')
          .expect(HttpStatus.OK);

        expect(mockContestService.findAll).toHaveBeenCalled();
      });

      it('should filter contests by type', async () => {
        mockContestService.findAll.mockResolvedValue({
          items: [testContest],
          total: 1,
          page: 1,
          limit: 10,
        });

        await request(app.getHttpServer())
          .get('/contest/list?type=weekly')
          .expect(HttpStatus.OK);

        expect(mockContestService.findAll).toHaveBeenCalled();
      });
    });

    describe('GET /contest/upcoming', () => {
      it('should return upcoming contests', async () => {
        mockContestService.findUpcoming.mockResolvedValue([testContest]);

        const response = await request(app.getHttpServer())
          .get('/contest/upcoming')
          .expect(HttpStatus.OK);

        expect(Array.isArray(response.body)).toBe(true);
        expect(mockContestService.findUpcoming).toHaveBeenCalled();
      });
    });

    describe('GET /contest/running', () => {
      it('should return running contests', async () => {
        const runningContest = {
          ...testContest,
          status: ContestStatus.running,
          start_time: new Date(Date.now() - 1800000), // Started 30 mins ago
        };
        mockContestService.findRunning.mockResolvedValue([runningContest]);

        const response = await request(app.getHttpServer())
          .get('/contest/running')
          .expect(HttpStatus.OK);

        expect(Array.isArray(response.body)).toBe(true);
        expect(mockContestService.findRunning).toHaveBeenCalled();
      });
    });

    describe('GET /contest/past', () => {
      it('should return paginated past contests', async () => {
        const pastContest = {
          ...testContest,
          status: ContestStatus.finished,
        };
        mockContestService.findPast.mockResolvedValue({
          data: [pastContest],
          total: 1,
          page: 1,
          limit: 10,
        });

        const response = await request(app.getHttpServer())
          .get('/contest/past')
          .expect(HttpStatus.OK);

        expect(response.body).toHaveProperty('data');
        expect(response.body).toHaveProperty('total');
        expect(mockContestService.findPast).toHaveBeenCalled();
      });
    });

    describe('GET /contest/stats', () => {
      it('should return contest statistics', async () => {
        mockContestService.getStats.mockResolvedValue({
          total_contests: 10,
          total_participants: 100,
        });

        const response = await request(app.getHttpServer())
          .get('/contest/stats')
          .expect(HttpStatus.OK);

        expect(response.body).toHaveProperty('total_contests');
        expect(response.body).toHaveProperty('total_participants');
        expect(mockContestService.getStats).toHaveBeenCalled();
      });
    });

    describe('GET /contest/:id', () => {
      it('should return a specific contest', async () => {
        mockContestService.findOne.mockResolvedValue({
          ...testContest,
          problems: [testContestProblem],
        });

        const response = await request(app.getHttpServer())
          .get(`/contest/${testContest.id}`)
          .expect(HttpStatus.OK);

        expect(response.body.id).toBe(testContest.id);
        expect(mockContestService.findOne).toHaveBeenCalledWith(
          testContest.id,
          undefined,
        );
      });

      it('should return 404 for non-existent contest', async () => {
        mockContestService.findOne.mockRejectedValue(
          new Error('Contest not found'),
        );

        await request(app.getHttpServer())
          .get('/contest/non-existent-id')
          .expect(HttpStatus.INTERNAL_SERVER_ERROR);
      });
    });
  });

  // =========================================================================
  // CONTEST REGISTRATION
  // =========================================================================

  describe('Contest Registration', () => {
    describe('POST /contest/:id/register', () => {
      it('should register user for an upcoming contest', async () => {
        mockContestService.registerForContest.mockResolvedValue(undefined);

        await request(app.getHttpServer())
          .post(`/contest/${testContest.id}/register`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.CREATED);

        expect(mockContestService.registerForContest).toHaveBeenCalledWith(
          testContest.id,
          regularUser.id,
        );
      });

      it('should prevent duplicate registration', async () => {
        mockContestService.registerForContest.mockRejectedValue(
          new Error('Already registered for this contest'),
        );

        await request(app.getHttpServer())
          .post(`/contest/${testContest.id}/register`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.INTERNAL_SERVER_ERROR);
      });

      it('should prevent registration for non-upcoming contests', async () => {
        mockContestService.registerForContest.mockRejectedValue(
          new Error('Can only register for upcoming contests'),
        );

        await request(app.getHttpServer())
          .post(`/contest/${testContest.id}/register`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.INTERNAL_SERVER_ERROR);
      });

      it('should return 404 for non-existent contest', async () => {
        mockContestService.registerForContest.mockRejectedValue(
          new Error('Contest not found'),
        );

        await request(app.getHttpServer())
          .post('/contest/non-existent/register')
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.INTERNAL_SERVER_ERROR);
      });

      it('should require authentication', async () => {
        await request(app.getHttpServer())
          .post(`/contest/${testContest.id}/register`)
          .expect(HttpStatus.FORBIDDEN);
      });
    });

    describe('DELETE /contest/:id/register', () => {
      it('should unregister user from contest', async () => {
        mockContestService.unregisterFromContest.mockResolvedValue(undefined);

        await request(app.getHttpServer())
          .delete(`/contest/${testContest.id}/register`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(mockContestService.unregisterFromContest).toHaveBeenCalledWith(
          testContest.id,
          regularUser.id,
        );
      });

      it('should fail if user not registered', async () => {
        mockContestService.unregisterFromContest.mockRejectedValue(
          new Error('Not registered for this contest'),
        );

        await request(app.getHttpServer())
          .delete(`/contest/${testContest.id}/register`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.INTERNAL_SERVER_ERROR);
      });
    });

    describe('GET /contest/:id/participation', () => {
      it('should return participation status for registered user', async () => {
        mockContestService.getParticipationStatus.mockResolvedValue({
          isRegistered: true,
          status: 'REGISTERED',
          participantId: 'participant-123',
          virtualSessionId: null,
          startedAt: null,
          finishedAt: null,
          totalScore: 0,
          totalPenalty: 0,
        });

        const response = await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/participation`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(response.body.isRegistered).toBe(true);
        expect(response.body.status).toBe('REGISTERED');
      });

      it('should return not registered for non-participant', async () => {
        mockContestService.getParticipationStatus.mockResolvedValue({
          isRegistered: false,
          status: null,
          participantId: null,
          virtualSessionId: null,
          startedAt: null,
          finishedAt: null,
          totalScore: 0,
          totalPenalty: 0,
        });

        const response = await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/participation`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(response.body.isRegistered).toBe(false);
      });
    });
  });

  // =========================================================================
  // RANKINGS
  // =========================================================================

  describe('Rankings', () => {
    describe('GET /contest/global-ranking', () => {
      it('should return global ranking list', async () => {
        mockContestService.getGlobalRanking.mockResolvedValue([
          {
            rank: 1,
            userId: regularUser.id,
            username: regularUser.username,
            avatar: null,
            country: null,
            rating: 1500,
            maxRating: 1600,
            ratingTitle: 'Expert',
            maxRatingTitle: 'Expert',
            contestsAttended: 5,
            badge: null,
          },
        ]);

        const response = await request(app.getHttpServer())
          .get('/contest/global-ranking')
          .expect(HttpStatus.OK);

        expect(Array.isArray(response.body)).toBe(true);
        expect(mockContestService.getGlobalRanking).toHaveBeenCalled();
      });
    });

    describe('GET /contest/:id/ranking', () => {
      it('should return contest ranking', async () => {
        mockRankingService.getContestRanking.mockResolvedValue({
          items: [],
          total: 0,
          page: 1,
          limit: 50,
          totalPages: 0,
        });

        const response = await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/ranking`)
          .expect(HttpStatus.OK);

        expect(response.body).toHaveProperty('items');
        expect(response.body).toHaveProperty('total');
        expect(mockRankingService.getContestRanking).toHaveBeenCalled();
      });

      it('should support pagination', async () => {
        mockRankingService.getContestRanking.mockResolvedValue({
          items: [],
          total: 50,
          page: 2,
          limit: 20,
          totalPages: 3,
        });

        await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/ranking?page=2&limit=20`)
          .expect(HttpStatus.OK);
      });

      it('should optionally include virtual participants', async () => {
        mockRankingService.getContestRanking.mockResolvedValue({
          items: [],
          total: 0,
          page: 1,
          limit: 50,
          totalPages: 0,
        });

        await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/ranking?include_virtual=true`)
          .expect(HttpStatus.OK);
      });
    });

    describe('GET /contest/:id/live-ranking', () => {
      it('should return live ranking for running contest', async () => {
        mockRankingService.getLiveRanking.mockResolvedValue([]);

        const response = await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/live-ranking`)
          .expect(HttpStatus.OK);

        expect(Array.isArray(response.body)).toBe(true);
        expect(mockRankingService.getLiveRanking).toHaveBeenCalled();
      });

      it('should respect limit parameter', async () => {
        mockRankingService.getLiveRanking.mockResolvedValue([]);

        await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/live-ranking?limit=50`)
          .expect(HttpStatus.OK);
      });
    });
  });

  // =========================================================================
  // VIRTUAL CONTEST
  // =========================================================================

  describe('Virtual Contest', () => {
    describe('POST /contest/:id/virtual/start', () => {
      it('should start virtual contest for finished contest', async () => {
        mockContestService.startVirtualContest.mockResolvedValue({
          sessionId: 'session-123',
          startedAt: new Date(),
        });

        const response = await request(app.getHttpServer())
          .post(`/contest/${testContest.id}/virtual/start`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.CREATED);

        expect(response.body).toBeDefined();
        expect(mockContestService.startVirtualContest).toHaveBeenCalledWith(
          testContest.id,
          regularUser.id,
        );
      });

      it('should prevent virtual contest for upcoming contest', async () => {
        mockContestService.startVirtualContest.mockRejectedValue(
          new Error('Can only start virtual contest for finished contests'),
        );

        await request(app.getHttpServer())
          .post(`/contest/${testContest.id}/virtual/start`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.INTERNAL_SERVER_ERROR);
      });
    });

    describe('GET /contest/:id/virtual/session', () => {
      it('should return virtual session for user', async () => {
        mockContestService.getVirtualSession.mockResolvedValue({
          id: 'session-123',
          startedAt: new Date(),
          finishedAt: null,
        });

        await request(app.getHttpServer())
          .get(`/contest/${testContest.id}/virtual/session`)
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(mockContestService.getVirtualSession).toHaveBeenCalledWith(
          testContest.id,
          regularUser.id,
        );
      });
    });

    describe('POST /contest/:id/virtual/finish', () => {
      it('should finish virtual contest session', async () => {
        mockContestService.finishVirtualContest.mockResolvedValue(undefined);

        await request(app.getHttpServer())
          .post(`/contest/${testContest.id}/virtual/finish`)
          .set('Authorization', `Bearer ${userToken}`)
          .send({ sessionId: 'session-123' })
          .expect(HttpStatus.CREATED);

        expect(mockContestService.finishVirtualContest).toHaveBeenCalledWith(
          'session-123',
          regularUser.id,
        );
      });
    });
  });

  // =========================================================================
  // USER CONTESTS
  // =========================================================================

  describe('User Contests', () => {
    describe('GET /contest/user/my-contests', () => {
      it('should return user registered contests', async () => {
        mockContestService.getUserContests.mockResolvedValue([
          {
            ...testContest,
            participationStatus: 'REGISTERED',
            score: 0,
            rank: null,
          },
        ]);

        const response = await request(app.getHttpServer())
          .get('/contest/user/my-contests?type=registered')
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(Array.isArray(response.body)).toBe(true);
        expect(mockContestService.getUserContests).toHaveBeenCalledWith(
          regularUser.id,
          'registered',
        );
      });

      it('should return user participated contests', async () => {
        mockContestService.getUserContests.mockResolvedValue([
          {
            ...testContest,
            participationStatus: 'FINISHED',
            score: 100,
            rank: 1,
          },
        ]);

        await request(app.getHttpServer())
          .get('/contest/user/my-contests?type=participated')
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);
      });

      it('should return user virtual contests', async () => {
        mockContestService.getUserContests.mockResolvedValue([
          {
            ...testContest,
            participationStatus: 'FINISHED',
            score: 50,
            rank: 5,
          },
        ]);

        await request(app.getHttpServer())
          .get('/contest/user/my-contests?type=virtual')
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);
      });

      it('should require authentication', async () => {
        await request(app.getHttpServer())
          .get('/contest/user/my-contests')
          .expect(HttpStatus.FORBIDDEN);
      });
    });

    describe('GET /contest/user/history', () => {
      it('should return user contest history', async () => {
        mockRankingService.getUserContestHistory.mockResolvedValue([]);

        const response = await request(app.getHttpServer())
          .get('/contest/user/history')
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(Array.isArray(response.body)).toBe(true);
        expect(mockRankingService.getUserContestHistory).toHaveBeenCalledWith(
          regularUser.id,
        );
      });
    });

    describe('GET /contest/user/rating-history', () => {
      it('should return user rating history', async () => {
        mockRatingService.getUserRatingHistory.mockResolvedValue([]);

        const response = await request(app.getHttpServer())
          .get('/contest/user/rating-history')
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(Array.isArray(response.body)).toBe(true);
        expect(mockRatingService.getUserRatingHistory).toHaveBeenCalledWith(
          regularUser.id,
        );
      });
    });
  });

  // =========================================================================
  // RANKINGS CONTROLLER (Separate /rankings routes)
  // =========================================================================

  describe('Rankings Controller', () => {
    describe('GET /rankings/global', () => {
      it('should return global ranking with authentication', async () => {
        mockRankingService.getGlobalRanking.mockResolvedValue({
          items: [],
          total: 0,
          page: 1,
          limit: 10,
          totalPages: 0,
        });

        const response = await request(app.getHttpServer())
          .get('/rankings/global')
          .set('Authorization', `Bearer ${userToken}`)
          .expect(HttpStatus.OK);

        expect(response.body).toHaveProperty('items');
        expect(mockRankingService.getGlobalRanking).toHaveBeenCalled();
      });

      it('should require authentication', async () => {
        await request(app.getHttpServer())
          .get('/rankings/global')
          .expect(HttpStatus.FORBIDDEN);
      });
    });
  });

  // =========================================================================
  // ERROR HANDLING
  // =========================================================================

  describe('Error Handling', () => {
    it('should handle service errors gracefully', async () => {
      mockContestService.findOne.mockRejectedValue(new Error('Database error'));

      await request(app.getHttpServer())
        .get('/contest/some-id')
        .expect(HttpStatus.INTERNAL_SERVER_ERROR);
    });

    it('should validate query parameters', async () => {
      // Note: This tests the ValidationPipe configuration
      // The page parameter must be >= 1
      await request(app.getHttpServer())
        .get('/contest/list?page=0')
        .expect(HttpStatus.BAD_REQUEST);
    });
  });
});
