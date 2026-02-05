import type { PrismaService } from '../prisma.service';
import {
  ForumPost as PrismaForumPost,
  ForumComment as PrismaForumComment,
  ForumCommunity as PrismaForumCommunity,
  ForumTag as PrismaForumTag,
  ForumCommunityRule as PrismaForumCommunityRule,
  ForumCommunityLink as PrismaForumCommunityLink,
  ForumCommunityMember as PrismaForumCommunityMember,
  ForumUser as PrismaForumUser,
  Prisma,
} from '@prisma/client';

// Prisma Client type for transaction support
export type PrismaClient = Prisma.TransactionClient | PrismaService;

// Type-compatible interfaces that match TypeORM entity shape
export interface ForumPost extends Omit<PrismaForumPost, 'stats'> {
  communityId: string;
  userId: string;
  flairType: string | null;
  flairLabel: string | null;
  isPinned: boolean;
  isLocked: boolean;
  createdAt: Date;
  community: ForumCommunity;
  author: ForumUser;
  tags: string[];
  voteState?: string;
  isSaved?: boolean;
  likes?: number;
  dislikes?: number;
  score?: number;
  userVote?: 0 | 1 | -1;
  flair?: { type: string; text: string };
  stats?: { views?: number; comments?: number; saves?: number };
}

export interface ForumComment extends PrismaForumComment {
  postId: string;
  parentId: string | null;
  authorId: string;
  editedAt: Date | null;
  isPinned: boolean;
  isLocked: boolean;
  createdAt: Date;
  author: ForumUser;
  parent?: ForumComment | null;
  likes?: number;
  dislikes?: number;
  upvotes?: number;
  userVote?: number;
}

export interface ForumCommunity extends PrismaForumCommunity {
  postsCount: number;
  postsToday: number;
  postsWeek: number;
  isOfficial: boolean;
  isFeatured: boolean;
  sortOrder: number;
  createdAt: Date;
}

export interface ForumTag extends PrismaForumTag {
  usageCount: number;
  createdAt: Date;
}

export interface ForumCommunityRule extends PrismaForumCommunityRule {
  communityId: string;
  sortOrder: number;
  createdAt: Date;
}

export interface ForumCommunityLink extends PrismaForumCommunityLink {
  communityId: string;
  sortOrder: number;
}

export interface ForumCommunityMember extends PrismaForumCommunityMember {
  communityId: string;
  userId: string;
  joinedAt: Date;
}

export type ForumUser = PrismaForumUser;

// Converter functions to transform Prisma results to TypeORM-compatible format
export function convertPostToTypeOrmFormat(
  post: PrismaForumPost & {
    author?: PrismaForumUser | null;
    community?: PrismaForumCommunity | null;
  },
): ForumPost {
  return {
    ...post,
    communityId: post.community_id,
    userId: post.user_id,
    flairType: post.flair_type,
    flairLabel: post.flair_label,
    isPinned: post.is_pinned,
    isLocked: post.is_locked,
    createdAt: post.created_at,
    tags: post.tags as string[],
    community: post.community
      ? convertCommunityToTypeOrmFormat(post.community)
      : (undefined as never),
    author: (post.author as ForumUser) || (undefined as never),
  } as ForumPost;
}

export function convertCommunityToTypeOrmFormat(
  community: PrismaForumCommunity,
): ForumCommunity {
  return {
    ...community,
    postsCount: community.posts_count,
    postsToday: community.posts_today,
    postsWeek: community.posts_week,
    isOfficial: community.is_official,
    isFeatured: community.is_featured,
    sortOrder: community.sort_order,
    createdAt: community.created_at,
  };
}

export function convertCommentToTypeOrmFormat(
  comment: PrismaForumComment & {
    author?: PrismaForumUser | null;
  },
): ForumComment {
  return {
    ...comment,
    postId: comment.post_id,
    parentId: comment.parent_id,
    authorId: comment.author_id,
    editedAt: comment.edited_at,
    isPinned: comment.is_pinned,
    isLocked: comment.is_locked,
    createdAt: comment.created_at,
    author: (comment.author as ForumUser) || (undefined as never),
  } as ForumComment;
}

export function convertTagToTypeOrmFormat(tag: PrismaForumTag): ForumTag {
  return {
    ...tag,
    usageCount: tag.usage_count,
    createdAt: tag.created_at,
  };
}

export function convertCommunityRuleToTypeOrmFormat(
  rule: PrismaForumCommunityRule,
): ForumCommunityRule {
  return {
    ...rule,
    communityId: rule.community_id,
    sortOrder: rule.sort_order,
    createdAt: rule.created_at,
  };
}

export function convertCommunityLinkToTypeOrmFormat(
  link: PrismaForumCommunityLink,
): ForumCommunityLink {
  return {
    ...link,
    communityId: link.community_id,
    sortOrder: link.sort_order,
  };
}

export function convertCommunityMemberToTypeOrmFormat(
  member: PrismaForumCommunityMember,
): ForumCommunityMember {
  return {
    ...member,
    communityId: member.community_id,
    userId: member.user_id,
    joinedAt: member.joined_at,
  };
}
