import { Test, TestingModule } from '@nestjs/testing';
import { ForumService } from './forum.service';
import { PrismaService } from '../prisma.service';
import { VoteService } from '../vote/vote.service';
import { BookmarkService } from '../bookmark/bookmark.service';

describe('ForumService', () => {
  let service: ForumService;
  let prismaService: jest.Mocked<PrismaService>;
  let voteService: jest.Mocked<VoteService>;
  let bookmarkService: jest.Mocked<BookmarkService>;

  const mockPost = {
    id: 'post-123',
    title: 'Test Post',
    excerpt: 'Test excerpt',
    community_id: 'community-1',
    user_id: 'user-123',
    created_at: new Date(),
    views: 0,
    stats: { comments: 0, views: 0 },
    flair_type: null,
    flair_label: null,
    is_pinned: false,
    is_locked: false,
    tags: [],
    permalink: null,
    media: null,
    recommendation: null,
    vote_state: 'neutral' as const,
    is_saved: false,
    impressions: 0,
    is_flagged: false,
    flagged_reason: null,
    flagged_at: null,
    is_deleted: false,
    deleted_at: null,
    deleted_by: null,
    author: {
      id: 'user-123',
      username: 'testuser',
      avatar: null,
      karma: 0,
    },
    community: {
      id: 'community-1',
      name: 'Test Community',
      slug: 'test-community',
      description: '',
      members: 100,
      online: 0,
      icon: null,
      color: null,
      banner: null,
      posts_count: 50,
      posts_today: 0,
      posts_week: 0,
      is_official: false,
      is_featured: false,
      sort_order: 0,
      created_at: new Date(),
      visibility: 'PUBLIC' as const,
    },
  };

  const mockCommunity = {
    id: 'community-1',
    name: 'Test Community',
    slug: 'test-community',
    description: '',
    members: 100,
    online: 0,
    icon: null,
    color: null,
    banner: null,
    posts_count: 50,
    posts_today: 0,
    posts_week: 0,
    is_official: false,
    is_featured: false,
    sort_order: 0,
    created_at: new Date(),
    visibility: 'PUBLIC' as const,
  };

  const mockPrismaService = {
    forumPost: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    forumCommunity: {
      findMany: jest.fn(),
      findFirst: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
    },
    forumComment: {
      findMany: jest.fn(),
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
      count: jest.fn(),
      groupBy: jest.fn().mockResolvedValue([]),
    },
    forumTag: {
      findMany: jest.fn(),
    },
    forumCommunityRule: {
      findMany: jest.fn(),
    },
    forumCommunityLink: {
      findMany: jest.fn(),
    },
    forumCommunityMember: {
      findUnique: jest.fn(),
      create: jest.fn(),
      delete: jest.fn(),
      count: jest.fn(),
    },
    forumUser: {
      findUnique: jest.fn(),
      create: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ForumService,
        {
          provide: PrismaService,
          useValue: mockPrismaService,
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
    prismaService = module.get(PrismaService);
    voteService = module.get(VoteService);
    bookmarkService = module.get(BookmarkService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('findAllPosts', () => {
    it('should return all posts', async () => {
      prismaService.forumPost.findMany.mockResolvedValue([mockPost] as never);
      voteService.getVoteCountsBatch.mockResolvedValue(new Map());
      bookmarkService.getFavoriteCountsBatch.mockResolvedValue(new Map());
      bookmarkService.getBookmarkStatusBatch.mockResolvedValue(new Map());

      const result = await service.findAllPosts('user-123');

      expect(result).toHaveLength(1);
    });
  });

  describe('findOnePost', () => {
    it('should return a post by id', async () => {
      prismaService.forumPost.findUnique.mockResolvedValue(mockPost as never);
      voteService.getVoteCounts.mockResolvedValue({ likes: 5, dislikes: 0 });
      bookmarkService.getFavoriteCount.mockResolvedValue(2);
      bookmarkService.isInDefaultFolder.mockResolvedValue(true);

      const result = await service.findOnePost('post-123', 'user-123');

      expect(result).toBeDefined();
    });

    it('should return null for non-existent post', async () => {
      prismaService.forumPost.findUnique.mockResolvedValue(null);

      const result = await service.findOnePost('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('findAllCommunities', () => {
    it('should return all communities', async () => {
      prismaService.forumCommunity.findMany.mockResolvedValue([
        mockCommunity,
      ] as never);

      const result = await service.findAllCommunities();

      expect(result).toHaveLength(1);
    });

    it('should return only featured communities', async () => {
      prismaService.forumCommunity.findMany.mockResolvedValue([
        mockCommunity,
      ] as never);

      const result = await service.findAllCommunities({ featuredOnly: true });

      expect(result).toHaveLength(1);
    });
  });

  describe('findOneCommunity', () => {
    it('should return community with rules and links', async () => {
      prismaService.forumCommunity.findFirst.mockResolvedValue(
        mockCommunity as never,
      );
      prismaService.forumCommunityRule.findMany.mockResolvedValue([]);
      prismaService.forumCommunityLink.findMany.mockResolvedValue([]);

      const result = await service.findOneCommunity('test-community');

      expect(result).toHaveProperty('community');
      expect(result).toHaveProperty('rules');
      expect(result).toHaveProperty('links');
    });
  });

  describe('createPost', () => {
    it('should create a new post', async () => {
      prismaService.forumCommunity.findUnique.mockResolvedValue(
        mockCommunity as never,
      );
      prismaService.forumCommunity.update.mockResolvedValue({} as never);
      prismaService.forumPost.create.mockResolvedValue(mockPost as never);
      prismaService.forumUser.findUnique.mockResolvedValue(null);
      prismaService.forumUser.create.mockResolvedValue(
        mockPost.author as never,
      );

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
      prismaService.forumPost.findUnique.mockResolvedValue(mockPost as never);
      prismaService.forumPost.update.mockResolvedValue({} as never);
      prismaService.forumComment.create.mockResolvedValue({
        id: 'comment-123',
        body: 'Great post!',
        post_id: 'post-123',
        parent_id: null,
        author_id: 'user-456',
        created_at: new Date(),
        edited_at: null,
        is_pinned: false,
        is_locked: false,
        author: mockPost.author,
      } as never);
      prismaService.forumComment.count.mockResolvedValue(1);
      prismaService.forumUser.findUnique.mockResolvedValue(mockPost.author);
      prismaService.forumUser.create.mockResolvedValue(mockPost.author);

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
        user_id: 'user-123',
        community_id: 'community-1',
        role: 'MEMBER',
        joined_at: new Date(),
      };

      prismaService.forumCommunityMember.findUnique.mockResolvedValue(null);
      prismaService.forumCommunityMember.create.mockResolvedValue(
        mockMember as never,
      );
      prismaService.forumCommunity.update.mockResolvedValue({} as never);

      const result = await service.joinCommunity('user-123', 'community-1');

      expect(result).toBeDefined();
    });

    it('should return existing member if already joined', async () => {
      const mockMember = {
        id: 'member-123',
        user_id: 'user-123',
        community_id: 'community-1',
        role: 'MEMBER',
        joined_at: new Date(),
      };

      prismaService.forumCommunityMember.findUnique.mockResolvedValue(
        mockMember as never,
      );

      const result = await service.joinCommunity('user-123', 'community-1');

      expect(result).toBeDefined();
    });
  });
});
