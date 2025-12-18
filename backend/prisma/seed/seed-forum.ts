// prisma/seed/seed-forum.ts
import { PrismaClient, FlairType, Prisma } from '@prisma/client';
import forumData from './data/forum.data';
import usersData from './data/users.data';

export async function clearForum(prisma: PrismaClient): Promise<void> {
  await prisma.forumComment.deleteMany();
  await prisma.forumPost.deleteMany();
  await prisma.forumUser.deleteMany();
  await prisma.forumCommunity.deleteMany();
}

export interface SeedForumResult {
  postsCount: number;
  commentsCount: number;
  usersCount: number;
}

export async function seedForum(
  prisma: PrismaClient,
): Promise<SeedForumResult> {
  // Seed communities
  for (const community of forumData.forum_communities) {
    await prisma.forumCommunity.create({
      data: {
        id: community.id,
        name: community.name,
        slug: community.slug,
        description: community.description,
        members: community.members,
        online: community.online,
      },
    });
  }

  // Seed forum users from main users data (sync username + avatar)
  const seededUserIds = new Set<string>();
  for (const user of usersData.users) {
    await prisma.forumUser.create({
      data: {
        id: user.id,
        username: user.username,
        avatar: user.avatar || null,
        karma: 0,
      },
    });
    seededUserIds.add(user.id);
  }

  // Create stats map


  // Seed posts
  for (const post of forumData.forum_posts) {
    // Skip if user doesn't exist
    if (!seededUserIds.has(post.user_id)) {
      console.warn(`Skipping post ${post.id}: user ${post.user_id} not found`);
      continue;
    }

    const commentCount = forumData.forum_comments.filter(
      (c) => c.post_id === post.id,
    ).length;
    const statsJson = {
      comments: commentCount,
      saves: 0,
      shares: 0,
      awards: 0,
      views: 0,
    };

    const media = (post as any).cover_image
      ? [
          {
            type: 'image',
            kind: 'image',
            src: (post as any).cover_image,
            ratio: 16 / 9,
          },
        ]
      : Prisma.DbNull;

    await prisma.forumPost.create({
      data: {
        id: post.id,
        community_id: post.community_id,
        user_id: post.user_id,
        permalink: null,
        title: post.title,
        flair_type: post.flair_type as FlairType,
        flair_label: null,
        tags: post.tags,
        excerpt: post.body,
        media: media,
        recommendation: Prisma.DbNull,

        is_saved: post.is_saved,
        impressions: post.impressions,
        is_pinned: post.is_pinned,
        is_locked: post.is_locked,
        created_at: new Date(post.created_at),
        stats: statsJson,
      },
    });
  }

  // Seed comments (respecting parent order)
  const commentIds = new Set<string>();
  for (const comment of forumData.forum_comments) {
    // Skip if author doesn't exist
    if (!seededUserIds.has(comment.author_id)) {
      console.warn(
        `Skipping comment ${comment.id}: author ${comment.author_id} not found`,
      );
      continue;
    }
    // Skip if parent doesn't exist yet (shouldn't happen with proper ordering)
    if (comment.parent_id && !commentIds.has(comment.parent_id)) {
      console.warn(
        `
      Skipping comment ${comment.id}: parent ${comment.parent_id} not found
      `,
      );
      continue;
    }
    await prisma.forumComment.create({
      data: {
        id: comment.id,
        post_id: comment.post_id,
        parent_id: comment.parent_id,
        author_id: comment.author_id,
        body: comment.body,
        markdown: null,

        created_at: new Date(comment.created_at),
        edited_at: null,
        is_pinned: false,
        is_locked: false,
      },
    });
    commentIds.add(comment.id);
  }

  return {
    postsCount: forumData.forum_posts.length,
    commentsCount: forumData.forum_comments.length,
    usersCount: seededUserIds.size,
  };
}
