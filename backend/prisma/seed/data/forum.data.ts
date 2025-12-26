// prisma/seed/data/forum.data.ts
import { USER_IDS } from './users.data';

const communityId = 'community-technology';

// Community IDs
const COMMUNITIES = {
  INTERVIEW: 'community-interview',
  CAREER: 'community-career',
  COMPENSATION: 'community-compensation',
  TECHNOLOGY: 'community-technology',
};

const data = {
  forum_communities: [
    {
      id: COMMUNITIES.INTERVIEW,
      name: 'Interview Experience',
      slug: 'interview',
      description: 'Share and discuss interview experiences, questions, and preparation strategies.',
      icon: 'MessageSquare',
      color: '#3B82F6',
      banner: null,
      members: 12500,
      online: 450,
      posts_count: 3420,
      posts_today: 45,
      posts_week: 312,
      is_official: true,
      is_featured: true,
      sort_order: 1,
      visibility: 'PUBLIC',
      created_at: new Date('2024-01-01'),
    },
    {
      id: COMMUNITIES.CAREER,
      name: 'Career',
      slug: 'career',
      description: 'Career advice, job opportunities, and professional development discussions.',
      icon: 'Briefcase',
      color: '#10B981',
      banner: null,
      members: 8900,
      online: 220,
      posts_count: 2100,
      posts_today: 28,
      posts_week: 189,
      is_official: true,
      is_featured: true,
      sort_order: 2,
      visibility: 'PUBLIC',
      created_at: new Date('2024-01-01'),
    },
    {
      id: COMMUNITIES.COMPENSATION,
      name: 'Compensation',
      slug: 'compensation',
      description: 'Discuss salaries, benefits, and compensation packages in tech.',
      icon: 'DollarSign',
      color: '#F59E0B',
      banner: null,
      members: 15200,
      online: 680,
      posts_count: 4850,
      posts_today: 92,
      posts_week: 541,
      is_official: true,
      is_featured: true,
      sort_order: 3,
      visibility: 'PUBLIC',
      created_at: new Date('2024-01-01'),
    },
    {
      id: COMMUNITIES.TECHNOLOGY,
      name: 'Technology',
      slug: 'technology',
      description: 'Technical discussions, new technologies, algorithms, and best practices.',
      icon: 'Cpu',
      color: '#8B5CF6',
      banner: null,
      members: 9800,
      online: 305,
      posts_count: 2890,
      posts_today: 38,
      posts_week: 267,
      is_official: true,
      is_featured: true,
      sort_order: 4,
      visibility: 'PUBLIC',
      created_at: new Date('2024-01-01'),
    },
  ],
  forum_community_rules: [
    // Technology community rules
    {
      id: 'rule-tech-1',
      community_id: COMMUNITIES.TECHNOLOGY,
      title: 'Show your attempt',
      body: 'Include code snippets or reasoning with every technical question.',
      sort_order: 1,
      created_at: new Date('2024-01-01'),
    },
    {
      id: 'rule-tech-2',
      community_id: COMMUNITIES.TECHNOLOGY,
      title: 'Be constructive',
      body: 'Keep feedback actionable and respectful.',
      sort_order: 2,
      created_at: new Date('2024-01-01'),
    },
    {
      id: 'rule-tech-3',
      community_id: COMMUNITIES.TECHNOLOGY,
      title: 'Use spoiler tags',
      body: 'Mark solutions with spoiler tags for ongoing contests.',
      sort_order: 3,
      created_at: new Date('2024-01-01'),
    },
    // Interview community rules
    {
      id: 'rule-interview-1',
      community_id: COMMUNITIES.INTERVIEW,
      title: 'Be respectful',
      body: 'Everyone\'s interview experience is different. Be supportive and constructive.',
      sort_order: 1,
      created_at: new Date('2024-01-01'),
    },
    {
      id: 'rule-interview-2',
      community_id: COMMUNITIES.INTERVIEW,
      title: 'Protect confidentiality',
      body: 'Do not share confidential information or questions under NDA.',
      sort_order: 2,
      created_at: new Date('2024-01-01'),
    },
    // Career community rules
    {
      id: 'rule-career-1',
      community_id: COMMUNITIES.CAREER,
      title: 'Stay professional',
      body: 'Maintain professional discourse in all career discussions.',
      sort_order: 1,
      created_at: new Date('2024-01-01'),
    },
    // Compensation community rules
    {
      id: 'rule-comp-1',
      community_id: COMMUNITIES.COMPENSATION,
      title: 'Be honest and accurate',
      body: 'Share accurate compensation data to help the community.',
      sort_order: 1,
      created_at: new Date('2024-01-01'),
    },
  ],
  forum_community_links: [
    {
      id: 'link-tech-1',
      community_id: COMMUNITIES.TECHNOLOGY,
      label: 'Weekly Editorial',
      url: 'https://example.com/editorial',
      sort_order: 1,
    },
    {
      id: 'link-tech-2',
      community_id: COMMUNITIES.TECHNOLOGY,
      label: 'Discord Server',
      url: 'https://discord.gg/ulticode',
      sort_order: 2,
    },
    {
      id: 'link-interview-1',
      community_id: COMMUNITIES.INTERVIEW,
      label: 'Interview Prep Guide',
      url: 'https://example.com/interview-guide',
      sort_order: 1,
    },
  ],
  forum_tags: [
    { id: 'tag-rust', name: 'rust', slug: 'rust', color: '#CE422B', usage_count: 0, created_at: new Date() },
    { id: 'tag-performance', name: 'performance', slug: 'performance', color: '#F59E0B', usage_count: 0, created_at: new Date() },
    { id: 'tag-hashing', name: 'hashing', slug: 'hashing', color: '#3B82F6', usage_count: 0, created_at: new Date() },
    { id: 'tag-mindset', name: 'mindset', slug: 'mindset', color: '#8B5CF6', usage_count: 0, created_at: new Date() },
    { id: 'tag-psychology', name: 'psychology', slug: 'psychology', color: '#EC4899', usage_count: 0, created_at: new Date() },
    { id: 'tag-strategy', name: 'strategy', slug: 'strategy', color: '#10B981', usage_count: 0, created_at: new Date() },
    { id: 'tag-tutorial', name: 'tutorial', slug: 'tutorial', color: '#06B6D4', usage_count: 0, created_at: new Date() },
    { id: 'tag-visualization', name: 'visualization', slug: 'visualization', color: '#F59E0B', usage_count: 0, created_at: new Date() },
    { id: 'tag-data-structures', name: 'data-structures', slug: 'data-structures', color: '#8B5CF6', usage_count: 0, created_at: new Date() },
  ],
  forum_posts: [
    // Post 1: Hard Technical Question
    {
      id: 'post-rust-hashmap',
      community_id: communityId,
      user_id: USER_IDS.STACK_UNWIND,
      title:
        'Why is `std::collections::HashMap` slower than `fxhash` in competitive programming?',
      body: `I've been grinding AtCoder benchmarks and noticed a huge performance diff.

Standard HashMap:
\`\`\`rust
use std::collections::HashMap;
let mut map = HashMap::new();
// TLE on large test cases (2.5s)
\`\`\`

FxHash:
\`\`\`rust
use rustc_hash::FxHashMap;
let mut map = FxHashMap::default();
// AC (0.8s)
\`\`\`

I know \`std\` uses SipHash for DoS protection, but is the constants overhead really that massive? Or is it the collision rate?`,
      tags: ['rust', 'performance', 'hashing'],
      flair_type: 'question',

      is_saved: true,
      impressions: 3400,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-28T09:15:00.000Z',
    },
    // Post 2: Soft Discussion
    {
      id: 'post-contest-tilt',
      community_id: communityId,
      user_id: USER_IDS.DAVID,
      title:
        'The "30-Minute Wall": How do you reset mental state during a contest?',
      body: `Yesterday I bricked Q2. Spent 40 mins debugging a simple off-by-one error. After that, I couldn't focus on Q3/Q4 at all. My brain just felt "foggy" and panicked.

Do you have any physical or mental protocols to hard-reset? I've heard of people doing pushups or splashing water.`,
      tags: ['mindset', 'psychology', 'strategy'],
      flair_type: 'discussion',

      is_saved: false,
      impressions: 5120,
      is_pinned: true,
      is_locked: false,
      created_at: '2024-11-28T14:30:00.000Z',
    },
    // Post 3: Showcase/Guide
    {
      id: 'post-segtree-visual',
      community_id: communityId,
      user_id: USER_IDS.TOURIST,
      title: 'Visual Guide to Segment Trees (Lazy Propagation)',
      body: `I wrote a small interactive blog post visualizing how lazy tags flow down strictly during queries.

[Link to visualization](https://example.com/segtree-vis)

Key insight: "Lazy tags are just pending operations".
Most bugs come from:
1. Not pushing down before reading children.
2. Not updating the current node after children return.

Let me know if this helps!`,
      tags: ['tutorial', 'segment-tree', 'visualization'],
      flair_type: 'showcase',

      is_saved: true,
      impressions: 8900,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-27T10:00:00.000Z',
      cover_image:
        'https://images.unsplash.com/photo-1509228468518-180dd4864904?auto=format&fit=crop&w=1200&q=80',
    },
  ],

  forum_awards: [
    { id: 'award-insightful', label: 'Insightful' },
    { id: 'award-helpful', label: 'Helpful' },
    { id: 'award-gold', label: 'Gold' },
  ],
  forum_post_awards: [
    { post_id: 'post-rust-hashmap', award_id: 'award-helpful', count: 1 },
    { post_id: 'post-contest-tilt', award_id: 'award-insightful', count: 3 },
    { post_id: 'post-contest-tilt', award_id: 'award-gold', count: 1 },
    { post_id: 'post-segtree-visual', award_id: 'award-gold', count: 5 },
    { post_id: 'post-segtree-visual', award_id: 'award-insightful', count: 7 },
  ],
  forum_comments: [
    // Thread for Rust Hashmap
    {
      id: 'c-rust-1',
      post_id: 'post-rust-hashmap',
      parent_id: null,
      author_id: USER_IDS.BENQ,
      body: 'SipHash is cryptographically strong but slow. For CP, you never need DOS protection unless it is a specific "hack" round on Codeforces.',

      created_at: '2024-11-28T09:20:00.000Z',
    },
    {
      id: 'c-rust-2',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-1',
      author_id: USER_IDS.STACK_UNWIND,
      body: 'Ah makes sense. I thought standard library would optimize for general speed. `FxHash` it is then.',

      created_at: '2024-11-28T09:35:00.000Z',
    },
    {
      id: 'c-rust-3',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-2',
      author_id: USER_IDS.PETR,
      body: 'Be careful! FxHash is vulnerable to collisions. If someone generates anti-hash tests, you will TLE. `RandomState` with a fixed seed + simple hash is safer.',

      created_at: '2024-11-28T10:00:00.000Z',
    },
    {
      id: 'c-rust-4',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-3',
      author_id: USER_IDS.YUKI,
      body: 'Wait, does AtCoder allow anti-hash tests? I thought test cases were static.',

      created_at: '2024-11-28T10:15:00.000Z',
    },
    {
      id: 'c-rust-5',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-4',
      author_id: USER_IDS.PETR,
      body: 'They are static but setter might predict simple hashes. Randomized hashing is always strictly superior.',

      created_at: '2024-11-28T10:30:00.000Z',
    },
    {
      id: 'c-rust-6',
      post_id: 'post-rust-hashmap',
      parent_id: null,
      author_id: USER_IDS.ALEX,
      body: 'In C++ `std::unordered_map` is even worse because of cache locality (linked list buckets). `gp_hash_table` is the way.',

      created_at: '2024-11-28T11:00:00.000Z',
    },

    // Thread for Contest Tilt
    {
      id: 'c-tilt-1',
      post_id: 'post-contest-tilt',
      parent_id: null,
      author_id: USER_IDS.SHADCN,
      body: 'Breathing protocol: 4 sec in, 4 hold, 4 out. Do it 3 times. It forces heart rate down mechanically.',

      created_at: '2024-11-28T14:40:00.000Z',
    },
    {
      id: 'c-tilt-2',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-1',
      author_id: USER_IDS.DAVID,
      body: 'Will try this next mock. I usually just stare at the screen hyperventilating lol.',

      created_at: '2024-11-28T14:45:00.000Z',
    },
    {
      id: 'c-tilt-3',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-2',
      author_id: USER_IDS.LILY,
      body: 'Also, stand up. Physically changing your posture resets the "tunnel vision".',

      created_at: '2024-11-28T14:50:00.000Z',
    },
    {
      id: 'c-tilt-4',
      post_id: 'post-contest-tilt',
      parent_id: null,
      author_id: USER_IDS.SCOTT,
      body: "I usually rage quit and go play League. (Don't do this)",

      created_at: '2024-11-28T15:00:00.000Z',
    },
    {
      id: 'c-tilt-5',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-4',
      author_id: USER_IDS.TOM,
      body: 'Lol literally me last Codeforces round.',

      created_at: '2024-11-28T15:10:00.000Z',
    },
    {
      id: 'c-tilt-6',
      post_id: 'post-contest-tilt',
      parent_id: null,
      author_id: USER_IDS.SARA,
      body: 'I drink cold water. The temperature shock wakes up the prefrontal cortex.',

      created_at: '2024-11-28T15:30:00.000Z',
    },
    {
      id: 'c-tilt-7',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-6',
      author_id: USER_IDS.EMMA,
      body: 'Science!',

      created_at: '2024-11-28T15:45:00.000Z',
    },

    // Thread for Segtree
    {
      id: 'c-seg-1',
      post_id: 'post-segtree-visual',
      parent_id: null,
      author_id: USER_IDS.JIANGLY,
      body: 'Great visual. Small typo on slide 3: "propogate" -> "propagate".',

      created_at: '2024-11-27T10:10:00.000Z',
    },
    {
      id: 'c-seg-2',
      post_id: 'post-segtree-visual',
      parent_id: 'c-seg-1',
      author_id: USER_IDS.TOURIST,
      body: 'Fixed! Thanks. 🙏',

      created_at: '2024-11-27T10:15:00.000Z',
    },
    {
      id: 'c-seg-3',
      post_id: 'post-segtree-visual',
      parent_id: null,
      author_id: USER_IDS.KEVIN,
      body: 'Does this handle beatbeats? (Segment tree beats)',

      created_at: '2024-11-27T10:30:00.000Z',
    },
    {
      id: 'c-seg-4',
      post_id: 'post-segtree-visual',
      parent_id: 'c-seg-3',
      author_id: USER_IDS.TOURIST,
      body: 'Not yet. Beats requires tracking min/max/second_max which is harder to visualize cleanly.',

      created_at: '2024-11-27T10:45:00.000Z',
    },
    {
      id: 'c-seg-5',
      post_id: 'post-segtree-visual',
      parent_id: 'c-seg-3',
      author_id: USER_IDS.MAX,
      body: "Check out JiDriver's blog for beats visuals.",

      created_at: '2024-11-27T11:00:00.000Z',
    },
  ],
  forum_quick_filters: [
    { id: 'filter-new', label: 'New', value: 'new' },
    { id: 'filter-top', label: 'Top', value: 'top' },
    { id: 'filter-hot', label: 'Hot', value: 'hot' },
  ],
} as const;

export default data;
