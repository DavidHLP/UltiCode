import { Test, TestingModule } from '@nestjs/testing';
import { ForumService } from './forum.service';
import { Repository } from 'typeorm';
import { ForumPost } from './entities/post.entity';
import { ForumCommunity } from './entities/community.entity';
import { ForumComment } from './entities/comment.entity';
import { ForumCommunityMember } from './entities/community-member.entity';
import { VoteService } from '../vote/vote.service';
import { BookmarkService } from '../bookmark/bookmark.service';

describe('ForumService', () => {
  let service: ForumService;
  let postsRepository: jest.Mocked<Repository<ForumPost>>;
  let communitiesRepository: jest.Mocked<Repository<ForumCommunity>>;
  let commentsRepository: jest.Mocked<Repository<ForumComment>>;
  let membersRepository: jest.Mocked<Repository<ForumCommunityMember>>;
  let voteService: jest.Mocked<VoteService>;
  let bookmarkService: jest.Mocked<BookmarkService>;

  const mockPost = {
    id: 'post-123',
    title: 'Test Post',
    excerpt: 'Test excerpt',
    communityId: 'community-1',
    userId: 'user-123',
    createdAt: new Date(),
    views: 0,
    stats: { comments: 0, views: 0 },
    author: {
      id: 'user-123',
      username: 'testuser',
      avatar: null,
    },
    community: {
      id: 'community-1',
      name: 'Test Community',
      slug: 'test-community',
    },
  };

  const mockCommunity = {
    id: 'community-1',
    name: 'Test Community',
    slug: 'test-community',
    visibility: 'PUBLIC',
    members: 100,
    postsCount: 50,
    sortOrder: 0,
    createdAt: new Date(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ForumService,
        {
          provide: 'ForumPostRepository',
          useValue: {
            find: jest.fn(),
            findOne: jest.fn(),
            create: jest.fn(),
            save: jest.fn(),
            update: jest.fn(),
            delete: jest.fn(),
            increment: jest.fn(),
            decrement: jest.fn(),
            createQueryBuilder: jest.fn().mockReturnThis(),
            getMany: jest.fn(),
            getRawMany: jest.fn(),
          },
        },
        {
          provide: 'ForumCommunityRepository',
          useValue: {
            find: jest.fn(),
            findOne: jest.fn(),
            increment: jest.fn(),
            decrement: jest.fn(),
            createQueryBuilder: jest.fn().mockReturnThis(),
            getMany: jest.fn(),
          },
        },
        {
          provide: 'ForumCommentRepository',
          useValue: {
            find: jest.fn(),
            findOne: jest.fn(),
            create: jest.fn(),
            save: jest.fn(),
            update: jest.fn(),
            delete: jest.fn(),
            count: jest.fn(),
            createQueryBuilder: jest.fn().mockReturnThis(),
          },
        },
        {
          provide: 'ForumTagRepository',
          useValue: { find: jest.fn() },
        },
        {
          provide: 'ForumCommunityRuleRepository',
          useValue: { find: jest.fn() },
        },
        {
          provide: 'ForumCommunityLinkRepository',
          useValue: { find: jest.fn() },
        },
        {
          provide: 'ForumCommunityMemberRepository',
          useValue: {
            findOne: jest.fn(),
            create: jest.fn(),
            save: jest.fn(),
            delete: jest.fn(),
            count: jest.fn(),
          },
        },
        {
          provide: 'ForumUserRepository',
          useValue: {
            findOne: jest.fn(),
            create: jest.fn(),
            save: jest.fn(),
          },
        },
        {
          provide: VoteService,
          useValue: {
            getVoteCountsBatch: jest.fn().mockResolvedValue(new Map()),
            getUserVotesBatch: jest.fn().mockResolvedValue(new Map()),
            getVoteCounts: jest
              .fn()
              .mockResolvedValue({ likes: 0, dislikes: 0 }),
          },
        },
        {
          provide: BookmarkService,
          useValue: {
            getFavoriteCountsBatch: jest.fn().mockResolvedValue(new Map()),
            getBookmarkStatusBatch: jest.fn().mockResolvedValue(new Map()),
            getFavoriteCount: jest.fn().mockResolvedValue(0),
            isInDefaultFolder: jest.fn().mockResolvedValue(false),
          },
        },
      ],
    }).compile();

    service = module.get<ForumService>(ForumService);
    postsRepository = module.get('ForumPostRepository');
    communitiesRepository = module.get('ForumCommunityRepository');
    commentsRepository = module.get('ForumCommentRepository');
    membersRepository = module.get('ForumCommunityMemberRepository');
    voteService = module.get(VoteService);
    bookmarkService = module.get(BookmarkService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAllPosts', () => {
    it('should return all posts', async () => {
      postsRepository.find.mockResolvedValue([mockPost] as never);
      voteService.getVoteCountsBatch.mockResolvedValue(new Map());
      commentsRepository.createQueryBuilder.mockReturnValue({
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        groupBy: jest.fn().mockReturnThis(),
        getRawMany: jest.fn().mockResolvedValue([]),
      } as never);
      bookmarkService.getFavoriteCountsBatch.mockResolvedValue(new Map());
      bookmarkService.getBookmarkStatusBatch.mockResolvedValue(new Map());

      const result = await service.findAllPosts('user-123');

      expect(result).toHaveLength(1);
    });
  });

  describe('findOnePost', () => {
    it('should return a post by id', async () => {
      postsRepository.findOne.mockResolvedValue(mockPost as never);
      voteService.getVoteCounts.mockResolvedValue({ likes: 5, dislikes: 0 });
      commentsRepository.createQueryBuilder.mockReturnValue({
        select: jest.fn().mockReturnThis(),
        addSelect: jest.fn().mockReturnThis(),
        where: jest.fn().mockReturnThis(),
        groupBy: jest.fn().mockReturnThis(),
        getRawMany: jest
          .fn()
          .mockResolvedValue([{ postId: 'post-123', count: '5' }]),
      } as never);
      bookmarkService.getFavoriteCount.mockResolvedValue(2);
      bookmarkService.isInDefaultFolder.mockResolvedValue(true);

      const result = await service.findOnePost('post-123', 'user-123');

      expect(result).toBeDefined();
    });

    it('should return null for non-existent post', async () => {
      postsRepository.findOne.mockResolvedValue(null);

      const result = await service.findOnePost('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('findAllCommunities', () => {
    it('should return all communities', async () => {
      communitiesRepository.createQueryBuilder.mockReturnValue({
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        addOrderBy: jest.fn().mockReturnThis(),
        getMany: jest.fn().mockResolvedValue([mockCommunity] as never),
      } as never);

      const result = await service.findAllCommunities();

      expect(result).toHaveLength(1);
    });

    it('should return only featured communities', async () => {
      communitiesRepository.createQueryBuilder.mockReturnValue({
        andWhere: jest.fn().mockReturnThis(),
        orderBy: jest.fn().mockReturnThis(),
        addOrderBy: jest.fn().mockReturnThis(),
        getMany: jest.fn().mockResolvedValue([mockCommunity] as never),
      } as never);

      const result = await service.findAllCommunities({ featuredOnly: true });

      expect(result).toHaveLength(1);
    });
  });

  describe('findOneCommunity', () => {
    it('should return community with rules and links', async () => {
      communitiesRepository.findOne.mockResolvedValue(mockCommunity as never);

      const result = await service.findOneCommunity('test-community');

      expect(result).toHaveProperty('community');
      expect(result).toHaveProperty('rules');
      expect(result).toHaveProperty('links');
    });
  });

  describe('createPost', () => {
    it('should create a new post', async () => {
      communitiesRepository.findOne.mockResolvedValue(mockCommunity as never);
      communitiesRepository.increment.mockResolvedValue({} as never);
      postsRepository.create.mockReturnValue(mockPost as never);
      postsRepository.save.mockResolvedValue(mockPost as never);

      const result = await service.createPost(
        {
          title: 'New Post',
          excerpt: 'Post content',
          communityId: 'community-1',
        },
        { id: 'user-123', username: 'testuser' },
      );

      expect(result).toBeDefined();
    });
  });

  describe('createComment', () => {
    it('should create a new comment', async () => {
      postsRepository.findOne.mockResolvedValue(mockPost as never);
      postsRepository.update.mockResolvedValue({} as never);
      commentsRepository.create.mockReturnValue({
        id: 'comment-123',
        body: 'Great post!',
      } as never);
      commentsRepository.save.mockResolvedValue({} as never);
      commentsRepository.count.mockResolvedValue(1);

      const result = await service.createComment(
        'post-123',
        'Great post!',
        null,
        { id: 'user-456', username: 'commenter' },
      );

      expect(result).toBeDefined();
    });
  });

  describe('joinCommunity', () => {
    it('should add user to community', async () => {
      const mockMember = {
        id: 'member-123',
        userId: 'user-123',
        communityId: 'community-1',
        role: 'MEMBER',
      };

      membersRepository.findOne.mockResolvedValue(null);
      membersRepository.create.mockReturnValue(mockMember as never);
      membersRepository.save.mockResolvedValue(mockMember as never);
      communitiesRepository.increment.mockResolvedValue({} as never);

      const result = await service.joinCommunity('user-123', 'community-1');

      expect(result).toEqual(mockMember);
    });

    it('should return existing member if already joined', async () => {
      const mockMember = {
        id: 'member-123',
        userId: 'user-123',
        communityId: 'community-1',
        role: 'MEMBER',
      };

      membersRepository.findOne.mockResolvedValue(mockMember as never);

      const result = await service.joinCommunity('user-123', 'community-1');

      expect(result).toEqual(mockMember);
    });
  });
});
