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
        'A community for competitive programmers to discuss algorithms, share solutions, and learn from each other.',
      members: 4280,
      online: 186,
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
    {
      id: 'post-two-sum',
      community_id: communityId,
      user_id: USER_USERNAMES.TOURIST,
      title: 'Does the Two Sum hashmap need two passes?',
      body: `I've seen some solutions that first populate the entire hashmap, then do a second pass to find complements. But isn't a single pass sufficient? 

Here's my reasoning: if we check for the complement BEFORE inserting the current number, we avoid counting the same element twice. Am I missing any edge case where two passes would be necessary?`,
      tags: ['two-sum', 'hashmap', 'algorithms'],
      flair_type: 'discussion',
      vote_state: 'upvoted',
      is_saved: false,
      impressions: 1520,
      is_pinned: true,
      is_locked: false,
      created_at: '2024-11-15T08:30:00.000Z',
    },
    {
      id: 'post-dp-vs-recursion',
      community_id: communityId,
      user_id: USER_USERNAMES.JIANGLY,
      title: 'When to use bottom-up DP vs memoized recursion?',
      body: `I understand both approaches lead to the same time complexity, but I'm struggling to decide which to use in contests. 

Bottom-up seems more space-efficient in some cases, but top-down with memoization feels more intuitive to implement. What's your decision framework?`,
      tags: ['dynamic-programming', 'recursion', 'optimization'],
      flair_type: 'question',
      vote_state: 'neutral',
      is_saved: true,
      impressions: 2340,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-18T14:15:00.000Z',
    },
    {
      id: 'post-segment-tree',
      community_id: communityId,
      user_id: USER_USERNAMES.BENQ,
      title: 'Lazy propagation explained with examples',
      body: `After struggling with lazy propagation for weeks, I finally understood it! Here's my explanation:

**Key insight**: Lazy propagation is about "procrastinating" updates. Instead of updating every node immediately, we store pending updates and only apply them when we actually need to access that node.

**When to use it**: Range updates + Range queries. If you only have point updates, basic segment tree is enough.

Happy to answer questions if anyone is confused!`,
      tags: ['segment-tree', 'data-structures', 'tutorial'],
      flair_type: 'showcase',
      vote_state: 'upvoted',
      is_saved: true,
      impressions: 3890,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-20T09:45:00.000Z',
    },
    {
      id: 'post-binary-search-bugs',
      community_id: communityId,
      user_id: USER_USERNAMES.ECNERWALA,
      title: 'The most common binary search bugs and how to avoid them',
      body: `I've reviewed hundreds of contest submissions and here are the top binary search bugs:

1. **Off-by-one errors**: Use \`lo <= hi\` vs \`lo < hi\` consistently
2. **Integer overflow**: \`mid = lo + (hi - lo) / 2\` instead of \`(lo + hi) / 2\`
3. **Infinite loops**: Make sure \`lo\` or \`hi\` always changes in each iteration
4. **Wrong boundary updates**: \`lo = mid + 1\` vs \`lo = mid\` depends on your condition

What bugs have bitten you?`,
      tags: ['binary-search', 'debugging', 'tips'],
      flair_type: 'discussion',
      vote_state: 'upvoted',
      is_saved: false,
      impressions: 4120,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-21T11:20:00.000Z',
    },
    {
      id: 'post-graph-representation',
      community_id: communityId,
      user_id: USER_USERNAMES.PETR,
      title: 'Adjacency list vs adjacency matrix: when to use which?',
      body: `Quick reference for graph representation:

**Adjacency List**: 
- Sparse graphs (E << V²)
- Most competitive programming problems
- O(V + E) space

**Adjacency Matrix**:
- Dense graphs (E ≈ V²)
- Need O(1) edge lookup
- Floyd-Warshall, transitive closure
- O(V²) space

What's your default choice?`,
      tags: ['graphs', 'data-structures', 'optimization'],
      flair_type: 'question',
      vote_state: 'neutral',
      is_saved: false,
      impressions: 1890,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-22T16:00:00.000Z',
    },
    {
      id: 'post-contest-strategy',
      community_id: communityId,
      user_id: USER_USERNAMES.SCOTT,
      title: 'How I improved from 1600 to 2100 rating in 6 months',
      body: `Sharing my journey and what worked for me:

1. **Upsolving**: Solved every problem I couldn't finish during contest
2. **Topic focus**: Spent 2 weeks on each weak topic (DP, graphs, etc.)
3. **Reading editorials**: Even for problems I solved, to learn optimal approaches
4. **Virtual contests**: Did 2-3 per week on past rounds
5. **Implementation practice**: Speed matters, practiced typing clean code fast

The biggest change was mindset - stopped guessing and started thinking systematically.`,
      tags: ['rating', 'improvement', 'motivation'],
      flair_type: 'showcase',
      vote_state: 'upvoted',
      is_saved: true,
      impressions: 5670,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-23T20:30:00.000Z',
    },
    {
      id: 'post-string-algorithms',
      community_id: communityId,
      user_id: USER_USERNAMES.UM_NIK,
      title: 'KMP vs Z-algorithm vs Rolling Hash: which to learn first?',
      body: `I'm trying to learn string algorithms but there are so many options. For substring matching:

- **KMP**: Classic, O(n+m), but the failure function is confusing
- **Z-algorithm**: Allegedly simpler, same complexity
- **Rolling Hash**: Easy to implement, but collision risk

For competitive programming, which should I master first? I only have time to deeply learn one right now.`,
      tags: ['strings', 'algorithms', 'learning'],
      flair_type: 'question',
      vote_state: 'neutral',
      is_saved: false,
      impressions: 2180,
      is_pinned: false,
      is_locked: false,
      created_at: '2024-11-24T13:45:00.000Z',
    },
    {
      id: 'post-weekly-contest-395',
      community_id: communityId,
      user_id: USER_USERNAMES.SHADCN,
      title: '[Discussion] Weekly Contest 395 - Problem Analysis',
      body: `Let's discuss this week's contest!

**Q1**: Straightforward simulation
**Q2**: Nice prefix sum problem, watch out for edge cases
**Q3**: DP with bitmask - the key insight was...
**Q4**: Hard graph problem, still working on understanding the solution

How did everyone do? Share your approaches!`,
      tags: ['weekly-contest', 'discussion', 'contest-395'],
      flair_type: 'announcement',
      vote_state: 'neutral',
      is_saved: false,
      impressions: 3240,
      is_pinned: true,
      is_locked: false,
      created_at: '2024-11-24T16:00:00.000Z',
    },
  ],
  forum_post_stats: [
    { id: 'stats-two-sum', post_id: 'post-two-sum', score: 156, comments: 8, awards: 2, saves: 45, shares: 12 },
    { id: 'stats-dp-vs-recursion', post_id: 'post-dp-vs-recursion', score: 234, comments: 15, awards: 3, saves: 89, shares: 23 },
    { id: 'stats-segment-tree', post_id: 'post-segment-tree', score: 412, comments: 22, awards: 5, saves: 156, shares: 45 },
    { id: 'stats-binary-search', post_id: 'post-binary-search-bugs', score: 389, comments: 31, awards: 4, saves: 203, shares: 67 },
    { id: 'stats-graph-rep', post_id: 'post-graph-representation', score: 178, comments: 12, awards: 1, saves: 67, shares: 18 },
    { id: 'stats-contest-strategy', post_id: 'post-contest-strategy', score: 567, comments: 45, awards: 8, saves: 312, shares: 89 },
    { id: 'stats-string-algo', post_id: 'post-string-algorithms', score: 145, comments: 18, awards: 1, saves: 52, shares: 14 },
    { id: 'stats-weekly-395', post_id: 'post-weekly-contest-395', score: 89, comments: 34, awards: 0, saves: 23, shares: 8 },
  ],
  forum_awards: [
    { id: 'award-insightful', label: 'Insightful' },
    { id: 'award-helpful', label: 'Helpful' },
    { id: 'award-gold', label: 'Gold' },
  ],
  forum_post_awards: [
    { post_id: 'post-two-sum', award_id: 'award-insightful', count: 2 },
    { post_id: 'post-dp-vs-recursion', award_id: 'award-helpful', count: 3 },
    { post_id: 'post-segment-tree', award_id: 'award-gold', count: 2 },
    { post_id: 'post-segment-tree', award_id: 'award-insightful', count: 3 },
    { post_id: 'post-binary-search-bugs', award_id: 'award-helpful', count: 4 },
    { post_id: 'post-contest-strategy', award_id: 'award-gold', count: 5 },
    { post_id: 'post-contest-strategy', award_id: 'award-insightful', count: 3 },
  ],
  forum_comments: [
    // Comments on Two Sum post
    {
      id: 'comment-ts-1',
      post_id: 'post-two-sum',
      parent_id: null,
      author_id: USER_USERNAMES.JIANGLY,
      body: 'Single pass is indeed sufficient. The two-pass version is just more intuitive for some people because it separates "build hashmap" from "query hashmap".',
      upvotes: 45,
      created_at: '2024-11-15T09:00:00.000Z',
    },
    {
      id: 'comment-ts-2',
      post_id: 'post-two-sum',
      parent_id: 'comment-ts-1',
      author_id: USER_USERNAMES.TOURIST,
      body: 'Thanks! That makes sense. I was worried I was missing something subtle.',
      upvotes: 12,
      created_at: '2024-11-15T09:15:00.000Z',
    },
    {
      id: 'comment-ts-3',
      post_id: 'post-two-sum',
      parent_id: null,
      author_id: USER_USERNAMES.BENQ,
      body: 'The only edge case to watch is when target = 2 * nums[i]. Make sure you check for complement BEFORE inserting, or you\'ll match the same element with itself.',
      upvotes: 78,
      created_at: '2024-11-15T10:30:00.000Z',
    },
    // Comments on DP vs Recursion post
    {
      id: 'comment-dp-1',
      post_id: 'post-dp-vs-recursion',
      parent_id: null,
      author_id: USER_USERNAMES.TOURIST,
      body: 'My rule of thumb: start with memoized recursion for correctness, convert to bottom-up if you need to optimize space or if the recursion depth would cause stack overflow.',
      upvotes: 89,
      created_at: '2024-11-18T15:00:00.000Z',
    },
    {
      id: 'comment-dp-2',
      post_id: 'post-dp-vs-recursion',
      parent_id: 'comment-dp-1',
      author_id: USER_USERNAMES.JIANGLY,
      body: 'Agreed. During contests I almost always use top-down because it\'s faster to implement and debug.',
      upvotes: 56,
      created_at: '2024-11-18T15:30:00.000Z',
    },
    {
      id: 'comment-dp-3',
      post_id: 'post-dp-vs-recursion',
      parent_id: null,
      author_id: USER_USERNAMES.ECNERWALA,
      body: 'Bottom-up is necessary when you want to apply space optimization (like only keeping the previous row for 2D DP). Also useful when the recurrence has a natural iterative structure.',
      upvotes: 67,
      created_at: '2024-11-18T16:45:00.000Z',
    },
    // Comments on Segment Tree post
    {
      id: 'comment-st-1',
      post_id: 'post-segment-tree',
      parent_id: null,
      author_id: USER_USERNAMES.PETR,
      body: 'Great explanation! One thing I\'d add: the hardest part of lazy propagation is getting the push_down function right. Make sure you handle the ordering of operations correctly.',
      upvotes: 34,
      created_at: '2024-11-20T10:30:00.000Z',
    },
    {
      id: 'comment-st-2',
      post_id: 'post-segment-tree',
      parent_id: null,
      author_id: USER_USERNAMES.SCOTT,
      body: 'Can you share your template code? I keep getting bugs in my implementation.',
      upvotes: 23,
      created_at: '2024-11-20T11:00:00.000Z',
    },
    {
      id: 'comment-st-3',
      post_id: 'post-segment-tree',
      parent_id: 'comment-st-2',
      author_id: USER_USERNAMES.BENQ,
      body: 'I\'ll post a follow-up with my template! The key is to always push_down before accessing children.',
      upvotes: 45,
      created_at: '2024-11-20T11:30:00.000Z',
    },
    // Comments on Binary Search post
    {
      id: 'comment-bs-1',
      post_id: 'post-binary-search-bugs',
      parent_id: null,
      author_id: USER_USERNAMES.UM_NIK,
      body: 'The hardest bug for me was choosing between `hi = mid` vs `hi = mid - 1`. I now always think about what invariant I\'m maintaining.',
      upvotes: 56,
      created_at: '2024-11-21T12:00:00.000Z',
    },
    {
      id: 'comment-bs-2',
      post_id: 'post-binary-search-bugs',
      parent_id: null,
      author_id: USER_USERNAMES.SHADCN,
      body: 'I use the "boundary" approach: find the first element that satisfies some condition. Makes it easier to reason about.',
      upvotes: 78,
      created_at: '2024-11-21T13:15:00.000Z',
    },
    // Comments on Contest Strategy post
    {
      id: 'comment-cs-1',
      post_id: 'post-contest-strategy',
      parent_id: null,
      author_id: USER_USERNAMES.STACK_UNWIND,
      body: 'This is super motivating! I\'m currently at 1400 and feel stuck. Going to try your upsolving approach.',
      upvotes: 34,
      created_at: '2024-11-23T21:00:00.000Z',
    },
    {
      id: 'comment-cs-2',
      post_id: 'post-contest-strategy',
      parent_id: 'comment-cs-1',
      author_id: USER_USERNAMES.SCOTT,
      body: 'You\'ve got this! The 1400-1600 range was hardest for me too. Keep grinding!',
      upvotes: 45,
      created_at: '2024-11-23T21:30:00.000Z',
    },
    {
      id: 'comment-cs-3',
      post_id: 'post-contest-strategy',
      parent_id: null,
      author_id: USER_USERNAMES.ALEX,
      body: 'How many hours per day did you practice? I can only manage 1-2 hours on weekdays.',
      upvotes: 23,
      created_at: '2024-11-23T22:00:00.000Z',
    },
    {
      id: 'comment-cs-4',
      post_id: 'post-contest-strategy',
      parent_id: 'comment-cs-3',
      author_id: USER_USERNAMES.SCOTT,
      body: 'About 2-3 hours on weekdays, more on weekends. Quality matters more than quantity though - focused practice on weak areas.',
      upvotes: 67,
      created_at: '2024-11-23T22:30:00.000Z',
    },
    // Comments on String Algorithms post
    {
      id: 'comment-sa-1',
      post_id: 'post-string-algorithms',
      parent_id: null,
      author_id: USER_USERNAMES.BENQ,
      body: 'Learn Z-algorithm first. It\'s simpler to understand and implement correctly. KMP\'s failure function is notoriously tricky.',
      upvotes: 89,
      created_at: '2024-11-24T14:00:00.000Z',
    },
    {
      id: 'comment-sa-2',
      post_id: 'post-string-algorithms',
      parent_id: 'comment-sa-1',
      author_id: USER_USERNAMES.TOURIST,
      body: 'I\'d actually recommend rolling hash for contests. Yes there\'s collision risk, but using multiple bases makes it negligible. Plus it\'s way more versatile.',
      upvotes: 67,
      created_at: '2024-11-24T14:30:00.000Z',
    },
    {
      id: 'comment-sa-3',
      post_id: 'post-string-algorithms',
      parent_id: 'comment-sa-2',
      author_id: USER_USERNAMES.UM_NIK,
      body: 'Thanks both! I\'ll start with Z-algorithm since it seems like a good foundation, then learn rolling hash for the versatility.',
      upvotes: 12,
      created_at: '2024-11-24T15:00:00.000Z',
    },
  ],
  forum_quick_filters: [
    { id: 'filter-new', label: 'New', value: 'new' },
    { id: 'filter-top', label: 'Top', value: 'top' },
    { id: 'filter-hot', label: 'Hot', value: 'hot' },
  ],
  forum_trending_topics: [
    { id: 'topic-dp', title: 'Dynamic Programming', posts: 156, trend: 'up' },
    { id: 'topic-graphs', title: 'Graph Algorithms', posts: 89, trend: 'up' },
    { id: 'topic-binary-search', title: 'Binary Search', posts: 67, trend: 'stable' },
    { id: 'topic-segment-tree', title: 'Segment Trees', posts: 45, trend: 'up' },
  ],
  forum_moderators: [
    {
      id: 'mod-tourist',
      community_id: communityId,
      username: USER_USERNAMES.TOURIST,
      title: 'Head Moderator',
    },
    {
      id: 'mod-benq',
      community_id: communityId,
      username: USER_USERNAMES.BENQ,
      title: 'Moderator',
    },
  ],
} as const;

export default data;
