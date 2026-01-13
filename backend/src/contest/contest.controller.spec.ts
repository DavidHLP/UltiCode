import { Test, TestingModule } from '@nestjs/testing';
import { ContestController } from './contest.controller';
import { ContestService } from './contest.service';
import { RankingService } from './ranking.service';
import { RatingService } from './rating.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { AuthGuard } from '../auth/auth.guard';

describe('ContestController', () => {
  let controller: ContestController;
  let contestService: jest.Mocked<ContestService>;
  let _rankingService: jest.Mocked<RankingService>;
  let _ratingService: jest.Mocked<RatingService>;

  const mockContest = {
    id: 'contest-123',
    title: 'Weekly Contest',
    slug: 'weekly-contest-1',
    start_time: new Date(),
    duration_minutes: 120,
    status: 'upcoming',
  };

  const mockReq = {
    user: { id: 'user-123' },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ContestController],
      providers: [
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn(),
            verify: jest.fn(),
          },
        },
        {
          provide: Reflector,
          useValue: {
            get: jest.fn(),
            getAll: jest.fn(),
          },
        },
        {
          provide: ModuleRef,
          useValue: {
            get: jest.fn(),
          },
        },
        {
          provide: ContestService,
          useValue: {
            findAll: jest.fn(),
            findOne: jest.fn(),
            findUpcoming: jest.fn(),
            findRunning: jest.fn(),
            findPast: jest.fn(),
            getStats: jest.fn(),
            registerForContest: jest.fn(),
            unregisterFromContest: jest.fn(),
            getParticipationStatus: jest.fn(),
            startVirtualContest: jest.fn(),
            getVirtualSession: jest.fn(),
            finishVirtualContest: jest.fn(),
            getUserContests: jest.fn(),
            getGlobalRanking: jest.fn(),
            getContestRanking: jest.fn(),
          },
        },
        {
          provide: RankingService,
          useValue: {
            getContestRanking: jest.fn(),
            getLiveRanking: jest.fn(),
            getUserContestHistory: jest.fn(),
            getGlobalRanking: jest.fn(),
          },
        },
        {
          provide: RatingService,
          useValue: {
            getUserRatingHistory: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<ContestController>(ContestController);
    contestService = module.get(ContestService);
    _rankingService = module.get(RankingService);
    _ratingService = module.get(RatingService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('findAll', () => {
    it('should return paginated contests', async () => {
      const mockResponse = {
        items: [mockContest],
        total: 1,
        page: 1,
        limit: 10,
      };

      contestService.findAll.mockResolvedValue(mockResponse as never);

      const result = await controller.findAll({});

      expect(result).toEqual(mockResponse);
      expect(contestService.findAll).toHaveBeenCalledWith({}, undefined);
    });
  });

  describe('findUpcoming', () => {
    it('should return upcoming contests', async () => {
      contestService.findUpcoming.mockResolvedValue([mockContest] as never);

      const result = await controller.findUpcoming();

      expect(result).toEqual([mockContest]);
      expect(contestService.findUpcoming).toHaveBeenCalledWith(undefined);
    });
  });

  describe('findRunning', () => {
    it('should return running contests', async () => {
      const runningContest = { ...mockContest, status: 'running' };
      contestService.findRunning.mockResolvedValue([runningContest] as never);

      const result = await controller.findRunning();

      expect(result).toEqual([runningContest]);
    });
  });

  describe('getStats', () => {
    it('should return contest statistics', async () => {
      const mockStats = {
        total_contests: 10,
        total_participants: 100,
      };

      contestService.getStats.mockResolvedValue(mockStats);

      const result = await controller.getStats();

      expect(result).toEqual(mockStats);
    });
  });

  describe('register', () => {
    it('should register user for contest', async () => {
      contestService.registerForContest.mockResolvedValue(undefined);

      await controller.register('contest-123', mockReq as any);

      expect(contestService.registerForContest).toHaveBeenCalledWith(
        'contest-123',
        'user-123',
      );
    });
  });

  describe('unregister', () => {
    it('should unregister user from contest', async () => {
      contestService.unregisterFromContest.mockResolvedValue(undefined);

      await controller.unregister('contest-123', mockReq as any);

      expect(contestService.unregisterFromContest).toHaveBeenCalledWith(
        'contest-123',
        'user-123',
      );
    });
  });

  describe('findOne', () => {
    it('should return a contest by id', async () => {
      contestService.findOne.mockResolvedValue(mockContest as never);

      const result = await controller.findOne('contest-123');

      expect(result).toEqual(mockContest);
    });
  });
});
