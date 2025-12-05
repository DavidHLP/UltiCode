import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ForumPost } from './forum-post.entity';
import { ForumCommunity } from './forum-community.entity';
import forumSeedData from '../seed-data/forum';
import { ForumComment } from './forum-comment.entity';
import { ForumUser } from './forum-user.entity';

interface SeedPost {
  id: string;
  community_id: string;
  user_id: string;
  title: string;
  body: string;
  tags?: string[];
  flair_type?: string;
  vote_state?: string;
  is_saved: boolean;
  impressions: number;
  is_pinned: boolean;
  is_locked: boolean;
  created_at: string;
}

interface SeedComment {
  id: string;
  post_id: string;
  parent_id: string | null;
  author_id: string;
  body: string;
  upvotes: number;
  created_at: string;
  edited_at?: string | null;
}

interface SeedUser {
  username: string;
  karma?: number;
  avatar?: string;
}

interface SeedCommunity {
  id: string;
  name: string;
  slug: string;
  description: string;
  members: number;
  online: number;
}

interface SeedData {
  forum_posts: SeedPost[];
  forum_comments: SeedComment[];
  forum_users: SeedUser[];
  forum_communities: SeedCommunity[];
  forum_trending_topics: {
    id: string;
    title: string;
    posts: number;
    trend?: 'up' | 'down' | 'stable';
  }[];
  forum_moderators: {
    id: string;
    community_id: string;
    username: string;
    title: string;
  }[];
  forum_quick_filters: { id: string; label: string; value: string }[];
}

const typedSeedData = forumSeedData as unknown as SeedData;

export interface ForumTrendingTopic {
  id: string;
  name: string;
  posts_count: number;
  trend?: 'up' | 'down' | 'stable';
}

export interface ForumModerator {
  id: string;
  username: string;
  role: 'moderator' | 'admin';
  avatar?: string;
  karma?: number;
}

export interface ForumQuickFilter {
  id: string;
  label: string;
  value: string;
}

@Injectable()
export class ForumService {
  constructor(
    @InjectRepository(ForumPost)
    private postsRepository: Repository<ForumPost>,
    @InjectRepository(ForumCommunity)
    private communitiesRepository: Repository<ForumCommunity>,
    @InjectRepository(ForumComment)
    private commentsRepository: Repository<ForumComment>,
  ) {}

  async findAllPosts(): Promise<ForumPost[]> {
    return this.postsRepository.find({
      relations: ['author', 'community'],
      order: { created_at: 'DESC' },
    });
  }

  async findOnePost(id: string): Promise<ForumPost | null> {
    return this.postsRepository.findOne({
      where: { id },
      relations: ['author', 'community'],
    });
  }

  async getThread(
    id: string,
  ): Promise<(ForumPost & { comments: ForumComment[] }) | null> {
    const post = await this.postsRepository.findOne({
      where: { id },
      relations: ['author', 'community'],
    });
    if (post) {
      const comments = await this.commentsRepository.find({
        where: { post_id: id },
        relations: ['author'],
        order: { created_at: 'ASC' },
      });
      return { ...post, comments };
    }

    // Fallback to seed data if database is empty
    const seedPost = typedSeedData.forum_posts.find((p) => p.id === id);
    if (!seedPost) {
      return null;
    }

    const seedComments = typedSeedData.forum_comments
      .filter((c) => c.post_id === id)
      .map((c: SeedComment) => ({
        ...c,
        created_at: new Date(c.created_at),
        edited_at: (() => {
          const rawEditedAt =
            typeof c.edited_at === 'string' ? c.edited_at : undefined;
          return rawEditedAt ? new Date(rawEditedAt) : null;
        })(),
        parent_id: c.parent_id ?? null,
        author: typedSeedData.forum_users.find(
          (u) => u.username === c.author_id,
        ),
      })) as ForumComment[];

    const seedAuthor = typedSeedData.forum_users.find(
      (u) => u.username === seedPost.user_id,
    );
    const seedCommunity = typedSeedData.forum_communities.find(
      (comm) => comm.id === seedPost.community_id,
    );

    const threadPost: ForumPost = {
      id: seedPost.id,
      community_id: seedPost.community_id,
      community: {
        id: seedCommunity?.id ?? seedPost.community_id,
        name: seedCommunity?.name ?? 'Community',
        slug: seedCommunity?.slug ?? seedPost.community_id,
        description: seedCommunity?.description ?? '',
        members: seedCommunity?.members ?? 0,
        online: seedCommunity?.online ?? 0,
      },
      user_id: seedPost.user_id,
      author: {
        username: seedAuthor?.username ?? seedPost.user_id,
        avatar: seedAuthor?.avatar ?? '',
        karma: seedAuthor?.karma ?? 0,
      } as ForumUser,
      permalink: '',
      title: seedPost.title,
      flair_type: seedPost.flair_type ?? null,
      flair_label: null,
      tags: Array.isArray(seedPost.tags) ? [...seedPost.tags] : [],
      excerpt: seedPost.body ?? '',
      media: null,
      recommendation: null,
      vote_state: seedPost.vote_state ?? 'neutral',
      is_saved: seedPost.is_saved,
      impressions: seedPost.impressions,
      is_pinned: seedPost.is_pinned,
      is_locked: seedPost.is_locked,
      created_at: new Date(seedPost.created_at),
    };

    return { ...threadPost, comments: seedComments };
  }

  async findAllCommunities(): Promise<ForumCommunity[]> {
    return this.communitiesRepository.find();
  }

  async getTrendingPosts(): Promise<ForumPost[]> {
    return this.postsRepository.find({
      relations: ['author', 'community'],
      order: { impressions: 'DESC' },
      take: 5,
    });
  }

  getTrendingTopics(): ForumTrendingTopic[] {
    return forumSeedData.forum_trending_topics.map((topic) => ({
      id: topic.id,
      name: topic.title,
      posts_count: topic.posts,
      trend: topic.trend,
    }));
  }

  getModerators(): ForumModerator[] {
    return forumSeedData.forum_moderators.map((moderator) => {
      return {
        id: moderator.id,
        username: moderator.username,
        role: 'moderator',
        avatar: undefined,
        karma: forumSeedData.forum_users.find(
          (u) => u.username === moderator.username,
        )?.karma,
      };
    });
  }

  getQuickFilters(): ForumQuickFilter[] {
    return forumSeedData.forum_quick_filters.map((filter) => ({
      id: filter.id,
      label: filter.label,
      value: filter.value,
    }));
  }
}
