import { Test, TestingModule } from '@nestjs/testing';
import { ForumController } from './forum.controller';
import { ForumService } from './forum.service';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { AuthGuard } from '../auth/auth.guard';

describe('ForumController', () => {
  let controller: ForumController;
  let forumService: jest.Mocked<ForumService>;

  const mockPost = {
    id: 'post-123',
    title: 'Test Post',
    excerpt: 'Test excerpt',
    communityId: 'community-1',
    userId: 'user-123',
    createdAt: new Date(),
  };

  const mockReq = {
    user: { id: 'user-123', username: 'testuser', avatar: null },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [ForumController],
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
          provide: ForumService,
          useValue: {
            findAllPosts: jest.fn(),
            findOnePost: jest.fn(),
            findPostsByUser: jest.fn(),
            createPost: jest.fn(),
            updatePost: jest.fn(),
            deletePost: jest.fn(),
            getThread: jest.fn(),
            findAllCommunities: jest.fn(),
            findOneCommunity: jest.fn(),
            findPostsByCommunity: jest.fn(),
            findAllTags: jest.fn(),
            joinCommunity: jest.fn(),
            leaveCommunity: jest.fn(),
            recordShare: jest.fn(),
            recordView: jest.fn(),
            createComment: jest.fn(),
            updateComment: jest.fn(),
            deleteComment: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<ForumController>(ForumController);
    forumService = module.get(ForumService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('findAllPosts', () => {
    it('should return all posts', async () => {
      forumService.findAllPosts.mockResolvedValue([mockPost] as never);

      const result = await controller.findAllPosts(mockReq as any);

      expect(result).toEqual([mockPost]);
      expect(forumService.findAllPosts).toHaveBeenCalledWith('user-123');
    });
  });

  describe('findOnePost', () => {
    it('should return a post by id', async () => {
      forumService.findOnePost.mockResolvedValue(mockPost as never);

      const result = await controller.findOnePost('post-123', mockReq as any);

      expect(result).toEqual(mockPost);
      expect(forumService.findOnePost).toHaveBeenCalledWith(
        'post-123',
        'user-123',
      );
    });
  });

  describe('findMyPosts', () => {
    it('should return posts for current user', async () => {
      forumService.findPostsByUser.mockResolvedValue([mockPost] as never);

      const result = await controller.findMyPosts(mockReq as any);

      expect(result).toEqual([mockPost]);
      expect(forumService.findPostsByUser).toHaveBeenCalledWith(
        'user-123',
        'user-123',
      );
    });
  });

  describe('createPost', () => {
    it('should create a new post', async () => {
      forumService.createPost.mockResolvedValue(mockPost as never);

      const result = await controller.createPost(
        {
          title: 'New Post',
          excerpt: 'Post content',
          communityId: 'community-1',
        },
        mockReq as any,
      );

      expect(result).toEqual(mockPost);
    });
  });

  describe('updatePost', () => {
    it('should update a post', async () => {
      forumService.updatePost.mockResolvedValue(mockPost as never);

      const result = await controller.updatePost(
        'post-123',
        { title: 'Updated Title' },
        mockReq as any,
      );

      expect(result).toEqual(mockPost);
      expect(forumService.updatePost).toHaveBeenCalledWith(
        'post-123',
        'user-123',
        expect.any(Object),
      );
    });
  });

  describe('deletePost', () => {
    it('should delete a post', async () => {
      forumService.deletePost.mockResolvedValue(undefined);

      await controller.deletePost('post-123', mockReq as any);

      expect(forumService.deletePost).toHaveBeenCalledWith(
        'post-123',
        'user-123',
      );
    });
  });

  describe('findAllCommunities', () => {
    it('should return all communities', async () => {
      const mockCommunities = [{ id: 'community-1', name: 'Test Community' }];
      forumService.findAllCommunities.mockResolvedValue(
        mockCommunities as never,
      );

      const result = await controller.findAllCommunities();

      expect(result).toEqual(mockCommunities);
    });
  });

  describe('createComment', () => {
    it('should create a new comment', async () => {
      const mockComment = {
        id: 'comment-123',
        body: 'Great post!',
      };
      forumService.createComment.mockResolvedValue(mockComment as never);

      const result = await controller.createComment(
        'post-123',
        { body: 'Great post!', parentId: null },
        mockReq as any,
      );

      expect(result).toEqual(mockComment);
    });
  });

  describe('joinCommunity', () => {
    it('should join a community', async () => {
      const mockMember = {
        id: 'member-123',
        userId: 'user-123',
        communityId: 'community-1',
      };
      forumService.joinCommunity.mockResolvedValue(mockMember as never);

      const result = await controller.joinCommunity(
        'community-1',
        mockReq as any,
      );

      expect(result).toEqual(mockMember);
    });
  });
});
