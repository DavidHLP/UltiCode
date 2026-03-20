import type { PrismaClient, FlairType } from '@prisma/client';
import { Prisma } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { CONTEXT_KEYS } from '../../core/seed-context';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import forumData from '../../data/forum.data';
import usersData from '../../data/users.data';

/**
 * Forum seeder - creates communities, forum users, posts, and comments.
 *
 * Layer: L2 (depends on Users)
 *
 * This is a combined seeder that handles all forum-related entities
 * in the correct dependency order.
 */
export class ForumSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Forum',
    version: '1.0.0',
    dependencies: ['Users'],
    priority: 1,
    description: 'Seed forum communities, users, posts, and comments',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    // Clear in dependency order (child tables first)
    await client.forumComment.deleteMany();
    await client.forumPost.deleteMany();
    await client.forumPostTagRelation.deleteMany();
    await client.forumCommunityMember.deleteMany();
    await client.forumCommunityLink.deleteMany();
    await client.forumCommunityRule.deleteMany();
    await client.forumCommunityTag.deleteMany();
    await client.forumTag.deleteMany();
    await client.forumUser.deleteMany();
    await client.forumCommunity.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {};

    // 1. Seed communities using batch insert
    const communityData = forumData.forum_communities.map((c) => ({
      id: c.id,
      name: c.name,
      slug: c.slug,
      description: c.description,
      icon: c.icon,
      color: c.color,
      banner: c.banner,
      members: c.members,
      online: c.online,
      posts_count: c.posts_count,
      posts_today: c.posts_today,
      posts_week: c.posts_week,
      is_official: c.is_official,
      is_featured: c.is_featured,
      sort_order: c.sort_order,
      visibility: c.visibility as 'PUBLIC' | 'PRIVATE' | 'RESTRICTED',
      created_at: c.created_at,
    }));

    const communityResult = await client.forumCommunity.createMany({
      data: communityData,
      skipDuplicates: true,
    });
    details.communities = communityResult.count;

    // Store community IDs in context
    this.set(CONTEXT_KEYS.FORUM_COMMUNITY_IDS, communityData.map((c) => c.id));

    // 2. Seed tags
    const tagResult = await client.forumTag.createMany({
      data: [...forumData.forum_tags],
      skipDuplicates: true,
    });
    details.tags = tagResult.count;

    // 3. Seed community rules
    const ruleResult = await client.forumCommunityRule.createMany({
      data: [...forumData.forum_community_rules],
      skipDuplicates: true,
    });
    details.rules = ruleResult.count;

    // 4. Seed community links
    const linkResult = await client.forumCommunityLink.createMany({
      data: [...forumData.forum_community_links],
      skipDuplicates: true,
    });
    details.links = linkResult.count;

    // 5. Seed forum users from main users data
    const forumUserData = usersData.users.map((u) => ({
      id: u.id,
      username: u.username,
      avatar: u.avatar || null,
      karma: 0,
    }));

    const forumUserResult = await client.forumUser.createMany({
      data: forumUserData,
      skipDuplicates: true,
    });
    details.forumUsers = forumUserResult.count;

    const seededUserIds = new Set(usersData.users.map((u) => u.id));

    // 6. Seed posts
    const postData = forumData.forum_posts
      .filter((post) => seededUserIds.has(post.user_id))
      .map((post) => {
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

        const media = (post as Record<string, unknown>).cover_image
          ? [
              {
                type: 'image',
                kind: 'image',
                src: (post as Record<string, unknown>).cover_image,
                ratio: 16 / 9,
              },
            ]
          : Prisma.DbNull;

        return {
          id: post.id,
          community_id: post.community_id,
          user_id: post.user_id,
          permalink: null,
          title: post.title,
          flair_type: post.flair_type as FlairType,
          flair_label: null,
          tags: post.tags,
          excerpt: post.body,
          media: media as Prisma.InputJsonValue,
          recommendation: Prisma.DbNull,
          is_saved: post.is_saved,
          impressions: post.impressions,
          is_pinned: post.is_pinned,
          is_locked: post.is_locked,
          created_at: new Date(post.created_at),
          stats: statsJson,
        };
      });

    const postResult = await client.forumPost.createMany({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      data: postData as any,
      skipDuplicates: true,
    });
    details.posts = postResult.count;

    // Store post IDs in context
    this.set(CONTEXT_KEYS.FORUM_POST_IDS, postData.map((p) => p.id));

    // 7. Seed comments (need to respect parent order for self-referencing FK)
    // Sort comments so parents come before children
    const sortedComments = [...forumData.forum_comments].sort((a, b) => {
      if (a.parent_id === null && b.parent_id !== null) return -1;
      if (a.parent_id !== null && b.parent_id === null) return 1;
      return 0;
    });

    // Insert comments in batches, tracking which parent IDs have been inserted
    const insertedCommentIds = new Set<string>();
    let commentsInserted = 0;

    // First pass: root comments (no parent)
    const rootComments = sortedComments
      .filter((c) => c.parent_id === null && seededUserIds.has(c.author_id))
      .map((c) => ({
        id: c.id,
        post_id: c.post_id,
        parent_id: null,
        author_id: c.author_id,
        body: c.body,
        markdown: null,
        created_at: new Date(c.created_at),
        edited_at: null,
        is_pinned: false,
        is_locked: false,
      }));

    if (rootComments.length > 0) {
      const rootResult = await client.forumComment.createMany({
        data: rootComments,
        skipDuplicates: true,
      });
      commentsInserted += rootResult.count;
      rootComments.forEach((c) => insertedCommentIds.add(c.id));
    }

    // Second pass: child comments (have parent)
    const childComments = sortedComments
      .filter(
        (c) =>
          c.parent_id !== null &&
          seededUserIds.has(c.author_id) &&
          insertedCommentIds.has(c.parent_id),
      )
      .map((c) => ({
        id: c.id,
        post_id: c.post_id,
        parent_id: c.parent_id,
        author_id: c.author_id,
        body: c.body,
        markdown: null,
        created_at: new Date(c.created_at),
        edited_at: null,
        is_pinned: false,
        is_locked: false,
      }));

    if (childComments.length > 0) {
      const childResult = await client.forumComment.createMany({
        data: childComments,
        skipDuplicates: true,
      });
      commentsInserted += childResult.count;
    }

    details.comments = commentsInserted;

    const totalCount = Object.values(details).reduce((sum, n) => sum + n, 0);
    return this.createResult(totalCount, startTime, details);
  }
}

export const createForumSeeder = createSeederExport(ForumSeeder);
