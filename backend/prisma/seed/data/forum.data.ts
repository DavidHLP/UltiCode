// prisma/seed/data/forum.data.ts
import { USER_USERNAMES } from './users.data';

const communityId = 'community-ulticode';

const data = {
  forum_communities: [
    {
      id: communityId,
      name: 'UltiCode Forum',
      slug: 'ulticode',
      description:
        'A community for competitive programmers to discuss contests, algorithms, and practice routines.',
      members: 4820,
      online: 205,
    },
  ],
  forum_community_rules: [
    {
      id: 'rule-show-attempt',
      community_id: communityId,
      title: 'Show your attempt',
      body: 'Include snippets or reasoning with every question.',
    },
    {
      id: 'rule-be-kind',
      community_id: communityId,
      title: 'Be constructive',
      body: 'Keep feedback actionable and respectful.',
    },
    {
      id: 'rule-no-spoilers',
      community_id: communityId,
      title: 'Use spoiler tags',
      body: 'Mark solutions with spoiler tags for ongoing contests.',
    },
  ],
  forum_community_links: [
    {
      id: 'link-editorial',
      community_id: communityId,
      label: 'Weekly Editorial',
      url: 'https://example.com/editorial',
    },
    {
      id: 'link-discord',
      community_id: communityId,
      label: 'Discord Server',
      url: 'https://discord.gg/ulticode',
    },
  ],
  forum_posts: [
    // Post 1: Hard Technical Question
    {
      id: 'post-rust-hashmap',
      community_id: communityId,
      user_id: USER_USERNAMES.STACK_UNWIND,
      title: 'Why is `std::collections::HashMap` slower than `fxhash` in competitive programming?',
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
      vote_state: 'upvoted',
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
      user_id: USER_USERNAMES.DAVID,
      title: 'The "30-Minute Wall": How do you reset mental state during a contest?',
      body: `Yesterday I bricked Q2. Spent 40 mins debugging a simple off-by-one error. After that, I couldn't focus on Q3/Q4 at all. My brain just felt "foggy" and panicked.

Do you have any physical or mental protocols to hard-reset? I've heard of people doing pushups or splashing water.`,
      tags: ['mindset', 'psychology', 'strategy'],
      flair_type: 'discussion',
      vote_state: 'neutral',
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
      user_id: USER_USERNAMES.TOURIST,
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
      vote_state: 'upvoted',
      is_saved: true,
      impressions: 8900,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-27T10:00:00.000Z',
      cover_image:
        'https://images.unsplash.com/photo-1509228468518-180dd4864904?auto=format&fit=crop&w=1200&q=80',
    },
  ],
  forum_post_stats: [
    {
      id: 'stats-rust-hashmap',
      post_id: 'post-rust-hashmap',
      score: 156,
      comments: 6,
      awards: 1,
      saves: 84,
      shares: 12,
    },
    {
      id: 'stats-contest-tilt',
      post_id: 'post-contest-tilt',
      score: 342,
      comments: 7,
      awards: 4,
      saves: 120,
      shares: 45,
    },
    {
      id: 'stats-segtree-visual',
      post_id: 'post-segtree-visual',
      score: 890,
      comments: 5,
      awards: 12,
      saves: 450,
      shares: 128,
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
      author_id: USER_USERNAMES.BENQ,
      body: 'SipHash is cryptographically strong but slow. For CP, you never need DOS protection unless it is a specific "hack" round on Codeforces.',
      upvotes: 45,
      created_at: '2024-11-28T09:20:00.000Z',
    },
    {
      id: 'c-rust-2',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-1',
      author_id: USER_USERNAMES.STACK_UNWIND,
      body: 'Ah makes sense. I thought standard library would optimize for general speed. `FxHash` it is then.',
      upvotes: 12,
      created_at: '2024-11-28T09:35:00.000Z',
    },
    {
      id: 'c-rust-3',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-2',
      author_id: USER_USERNAMES.PETR,
      body: 'Be careful! FxHash is vulnerable to collisions. If someone generates anti-hash tests, you will TLE. `RandomState` with a fixed seed + simple hash is safer.',
      upvotes: 89,
      created_at: '2024-11-28T10:00:00.000Z',
    },
    {
      id: 'c-rust-4',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-3',
      author_id: USER_USERNAMES.YUKI,
      body: 'Wait, does AtCoder allow anti-hash tests? I thought test cases were static.',
      upvotes: 5,
      created_at: '2024-11-28T10:15:00.000Z',
    },
    {
      id: 'c-rust-5',
      post_id: 'post-rust-hashmap',
      parent_id: 'c-rust-4',
      author_id: USER_USERNAMES.PETR,
      body: 'They are static but setter might predict simple hashes. Randomized hashing is always strictly superior.',
      upvotes: 34,
      created_at: '2024-11-28T10:30:00.000Z',
    },
    {
      id: 'c-rust-6',
      post_id: 'post-rust-hashmap',
      parent_id: null,
      author_id: USER_USERNAMES.ALEX,
      body: 'In C++ `std::unordered_map` is even worse because of cache locality (linked list buckets). `gp_hash_table` is the way.',
      upvotes: 22,
      created_at: '2024-11-28T11:00:00.000Z',
    },

    // Thread for Contest Tilt
    {
      id: 'c-tilt-1',
      post_id: 'post-contest-tilt',
      parent_id: null,
      author_id: USER_USERNAMES.SHADCN,
      body: 'Breathing protocol: 4 sec in, 4 hold, 4 out. Do it 3 times. It forces heart rate down mechanically.',
      upvotes: 120,
      created_at: '2024-11-28T14:40:00.000Z',
    },
    {
      id: 'c-tilt-2',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-1',
      author_id: USER_USERNAMES.DAVID,
      body: 'Will try this next mock. I usually just stare at the screen hyperventilating lol.',
      upvotes: 45,
      created_at: '2024-11-28T14:45:00.000Z',
    },
    {
      id: 'c-tilt-3',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-2',
      author_id: USER_USERNAMES.LILY,
      body: 'Also, stand up. Physically changing your posture resets the "tunnel vision".',
      upvotes: 67,
      created_at: '2024-11-28T14:50:00.000Z',
    },
    {
      id: 'c-tilt-4',
      post_id: 'post-contest-tilt',
      parent_id: null,
      author_id: USER_USERNAMES.SCOTT,
      body: 'I usually rage quit and go play League. (Don\'t do this)',
      upvotes: 230,
      created_at: '2024-11-28T15:00:00.000Z',
    },
    {
      id: 'c-tilt-5',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-4',
      author_id: USER_USERNAMES.TOM,
      body: 'Lol literally me last Codeforces round.',
      upvotes: 12,
      created_at: '2024-11-28T15:10:00.000Z',
    },
    {
      id: 'c-tilt-6',
      post_id: 'post-contest-tilt',
      parent_id: null,
      author_id: USER_USERNAMES.SARA,
      body: 'I drink cold water. The temperature shock wakes up the prefrontal cortex.',
      upvotes: 34,
      created_at: '2024-11-28T15:30:00.000Z',
    },
    {
      id: 'c-tilt-7',
      post_id: 'post-contest-tilt',
      parent_id: 'c-tilt-6',
      author_id: USER_USERNAMES.EMMA,
      body: 'Science!',
      upvotes: 5,
      created_at: '2024-11-28T15:45:00.000Z',
    },

    // Thread for Segtree
    {
      id: 'c-seg-1',
      post_id: 'post-segtree-visual',
      parent_id: null,
      author_id: USER_USERNAMES.JIANGLY,
      body: 'Great visual. Small typo on slide 3: "propogate" -> "propagate".',
      upvotes: 56,
      created_at: '2024-11-27T10:10:00.000Z',
    },
    {
      id: 'c-seg-2',
      post_id: 'post-segtree-visual',
      parent_id: 'c-seg-1',
      author_id: USER_USERNAMES.TOURIST,
      body: 'Fixed! Thanks. 🙏',
      upvotes: 28,
      created_at: '2024-11-27T10:15:00.000Z',
    },
    {
      id: 'c-seg-3',
      post_id: 'post-segtree-visual',
      parent_id: null,
      author_id: USER_USERNAMES.KEVIN,
      body: 'Does this handle beatbeats? (Segment tree beats)',
      upvotes: 12,
      created_at: '2024-11-27T10:30:00.000Z',
    },
    {
      id: 'c-seg-4',
      post_id: 'post-segtree-visual',
      parent_id: 'c-seg-3',
      author_id: USER_USERNAMES.TOURIST,
      body: 'Not yet. Beats requires tracking min/max/second_max which is harder to visualize cleanly.',
      upvotes: 45,
      created_at: '2024-11-27T10:45:00.000Z',
    },
    {
      id: 'c-seg-5',
      post_id: 'post-segtree-visual',
      parent_id: 'c-seg-3',
      author_id: USER_USERNAMES.MAX,
      body: 'Check out JiDriver\'s blog for beats visuals.',
      upvotes: 8,
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
