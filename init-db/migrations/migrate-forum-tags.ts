import { PrismaClient } from '@prisma/client';
import { randomUUID } from 'crypto';

/**
 * Migration script to convert forum post tags from JSON arrays to normalized M:N relations
 *
 * This script should be run BEFORE dropping the tags JSON column from ForumPost
 *
 * Usage: npx ts-node prisma/migrations/migrate-forum-tags.ts
 */
async function migrateForumTags() {
  const prisma = new PrismaClient();

  try {
    console.log('Starting tag migration...');

    // 1. Collect all unique tags from JSON arrays
    const posts = await prisma.forumPost.findMany();
    const uniqueTags = new Set<string>();

    for (const post of posts) {
      const tags = post.tags as string[];
      if (Array.isArray(tags)) {
        tags.forEach(tag => uniqueTags.add(tag));
      }
    }

    console.log(`Found ${uniqueTags.size} unique tags across ${posts.length} posts`);

    // 2. Create ForumTag records
    const tagMap = new Map<string, string>();

    for (const tagName of uniqueTags) {
      const slug = tagName.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '');

      try {
        const tag = await prisma.forumTag.create({
          data: {
            id: randomUUID(),
            name: tagName,
            slug,
            usage_count: 0,
            created_at: new Date(),
          },
        });

        tagMap.set(tagName, tag.id);
        console.log(`✓ Created tag: ${tagName} (${tag.id})`);
      } catch (error) {
        console.warn(`⚠ Tag "${tagName}" already exists or failed to create:`, error);
        // Try to find existing tag
        const existingTag = await prisma.forumTag.findUnique({
          where: { slug },
        });
        if (existingTag) {
          tagMap.set(tagName, existingTag.id);
        }
      }
    }

    console.log(`\n${tagMap.size} tags ready for migration`);

    // 3. Create ForumPostTagRelation records
    let relationsCreated = 0;
    let relationsSkipped = 0;

    for (const post of posts) {
      const tags = post.tags as string[];
      if (Array.isArray(tags)) {
        for (const tagName of tags) {
          const tagId = tagMap.get(tagName);
          if (tagId) {
            try {
              await prisma.forumPostTagRelation.create({
                data: {
                  post_id: post.id,
                  tag_id: tagId,
                },
              });
              relationsCreated++;
            } catch (error) {
              // Relation might already exist (duplicate)
              relationsSkipped++;
            }
          }
        }
      }
    }

    console.log(`\n✓ Created ${relationsCreated} tag relations`);
    if (relationsSkipped > 0) {
      console.log(`⚠ Skipped ${relationsSkipped} duplicate relations`);
    }

    // 4. Update usage counts
    console.log('\nUpdating tag usage counts...');
    let tagsUpdated = 0;

    for (const [tagName, tagId] of tagMap) {
      const count = await prisma.forumPostTagRelation.count({
        where: { tag_id: tagId },
      });

      await prisma.forumTag.update({
        where: { id: tagId },
        data: { usage_count: count },
      });

      tagsUpdated++;
      if (tagsUpdated % 10 === 0) {
        console.log(`  Updated ${tagsUpdated}/${tagMap.size} tags...`);
      }
    }

    console.log(`✓ Updated usage counts for ${tagsUpdated} tags`);

    // 5. Verification
    console.log('\nVerifying migration...');
    const totalRelations = await prisma.forumPostTagRelation.count();
    const totalTags = await prisma.forumTag.count();

    console.log(`  Total tags: ${totalTags}`);
    console.log(`  Total relations: ${totalRelations}`);

    // Check for orphaned posts (posts with JSON tags but no relations)
    const postsWithJsonTags = posts.filter(p => {
      const tags = p.tags as string[];
      return Array.isArray(tags) && tags.length > 0;
    });

    const postsWithRelations = await prisma.forumPost.findMany({
      where: {
        tag_relations: {
          some: {},
        },
      },
      select: { id: true },
    });

    const orphanedPosts = postsWithJsonTags.length - postsWithRelations.length;
    if (orphanedPosts > 0) {
      console.warn(`⚠ Warning: ${orphanedPosts} posts have JSON tags but no relations`);
    } else {
      console.log('✓ All posts with tags have been migrated');
    }

    console.log('\n✅ Migration complete!');
    console.log('\nNext steps:');
    console.log('1. Verify the data in the database');
    console.log('2. Update backend entities to use tag relations');
    console.log('3. Remove the "tags Json" field from ForumPost in schema.prisma');
    console.log('4. Run another migration to drop the old tags column');

  } catch (error) {
    console.error('\n❌ Migration failed:', error);
    throw error;
  } finally {
    await prisma.$disconnect();
  }
}

// Run if executed directly
if (require.main === module) {
  migrateForumTags()
    .then(() => {
      console.log('\n✓ Script completed successfully');
      process.exit(0);
    })
    .catch((error) => {
      console.error('\n✗ Script failed:', error);
      process.exit(1);
    });
}

export default migrateForumTags;
