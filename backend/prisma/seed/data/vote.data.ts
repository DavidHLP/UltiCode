// prisma/seed/data/vote.data.ts
import { USER_IDS } from './users.data';
import { SOLUTION_IDS } from './solutions.data';

// Define VoteTargetType locally to avoid importing from client in data file (optional, but cleaner)
type VoteTargetType = 'SOLUTION' | 'SOLUTION_COMMENT' | 'FORUM_POST' | 'FORUM_COMMENT';

export interface VoteSeedData {
  target_id: string;
  target_type: VoteTargetType;
  user_id: string;
  vote_type: number; // 1 or -1
}

const data = {
  votes: [
    // ========================================================================
    // SOLUTION VOTES
    // ========================================================================
    // Votes for sol-001 (340 upvotes, 12 downvotes) - Mocking a subset
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.MAX,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.SARA,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.TOM,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.LILY,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.DAVID,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.EMMA,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.KEVIN,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.TOURIST,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.JIANGLY,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: USER_IDS.BENQ,
      vote_type: 1,
    },

    // Votes for sol-002 (125 upvotes, 35 downvotes)
    {
      target_id: SOLUTION_IDS.TWO_SUM_BRUTE,
      target_type: 'SOLUTION',
      user_id: USER_IDS.LILY,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_BRUTE,
      target_type: 'SOLUTION',
      user_id: USER_IDS.EMMA,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_BRUTE,
      target_type: 'SOLUTION',
      user_id: USER_IDS.YUKI,
      vote_type: -1,
    },
    
    // Votes for sol-003
    {
      target_id: SOLUTION_IDS.TWO_SUM_CPP,
      target_type: 'SOLUTION',
      user_id: USER_IDS.KEVIN,
      vote_type: 1,
    },

    // Votes for sol-004
    {
      target_id: SOLUTION_IDS.TWO_SUM_JAVA,
      target_type: 'SOLUTION',
      user_id: USER_IDS.SARA,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.TWO_SUM_JAVA,
      target_type: 'SOLUTION',
      user_id: USER_IDS.TOM,
      vote_type: 1,
    },
    
    // Votes for sol-005
    {
      target_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      target_type: 'SOLUTION',
      user_id: USER_IDS.SHADCN,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      target_type: 'SOLUTION',
      user_id: USER_IDS.CHEN,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      target_type: 'SOLUTION',
      user_id: USER_IDS.SCOTT,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      target_type: 'SOLUTION',
      user_id: USER_IDS.PETR,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      target_type: 'SOLUTION',
      user_id: USER_IDS.UM_NIK,
      vote_type: -1,
    },

    // Votes for sol-006
    {
      target_id: SOLUTION_IDS.MERGE_INTERVALS_SORT,
      target_type: 'SOLUTION',
      user_id: USER_IDS.STACK_UNWIND,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.MERGE_INTERVALS_SORT,
      target_type: 'SOLUTION',
      user_id: USER_IDS.ALEX,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.MERGE_INTERVALS_SORT,
      target_type: 'SOLUTION',
      user_id: USER_IDS.KEVIN,
      vote_type: 1,
    },

    // Votes for sol-007
    {
      target_id: SOLUTION_IDS.MEDIAN_ARRAYS_BS,
      target_type: 'SOLUTION',
      user_id: USER_IDS.ECNERWALA,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.MEDIAN_ARRAYS_BS,
      target_type: 'SOLUTION',
      user_id: USER_IDS.TOURIST,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.MEDIAN_ARRAYS_BS,
      target_type: 'SOLUTION',
      user_id: USER_IDS.JIANGLY,
      vote_type: 1,
    },

    // Votes for sol-008
    {
      target_id: SOLUTION_IDS.ISLANDS_DFS,
      target_type: 'SOLUTION',
      user_id: USER_IDS.DAVID,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.ISLANDS_DFS,
      target_type: 'SOLUTION',
      user_id: USER_IDS.YUKI,
      vote_type: 1,
    },
    {
      target_id: SOLUTION_IDS.ISLANDS_DFS,
      target_type: 'SOLUTION',
      user_id: USER_IDS.LILY,
      vote_type: 1,
    },

    // ========================================================================
    // FORUM POST VOTES
    // ========================================================================
    // post-rust-hashmap
    {
      target_id: 'post-rust-hashmap',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.BENQ,
      vote_type: 1,
    },
    {
      target_id: 'post-rust-hashmap',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.TOURIST,
      vote_type: 1,
    },
    {
      target_id: 'post-rust-hashmap',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.PETR,
      vote_type: 1,
    },

    // post-contest-tilt
    {
      target_id: 'post-contest-tilt',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.SHADCN,
      vote_type: 1,
    },
    {
      target_id: 'post-contest-tilt',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.LILY,
      vote_type: 1,
    },
    {
      target_id: 'post-contest-tilt',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.TOM,
      vote_type: 1,
    },
    
    // post-segtree-visual
    {
      target_id: 'post-segtree-visual',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.JIANGLY,
      vote_type: 1,
    },
    {
      target_id: 'post-segtree-visual',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.MAX,
      vote_type: 1,
    },
    {
      target_id: 'post-segtree-visual',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.ALEX,
      vote_type: 1,
    },
    {
      target_id: 'post-segtree-visual',
      target_type: 'FORUM_POST',
      user_id: USER_IDS.KEVIN,
      vote_type: 1,
    },

    // ========================================================================
    // FORUM COMMENT VOTES
    // ========================================================================
    // c-rust-1 (SipHash is slow)
    {
      target_id: 'c-rust-1',
      target_type: 'FORUM_COMMENT',
      user_id: USER_IDS.STACK_UNWIND,
      vote_type: 1,
    },
    {
      target_id: 'c-rust-1',
      target_type: 'FORUM_COMMENT',
      user_id: USER_IDS.ALEX,
      vote_type: 1,
    },
  ] as VoteSeedData[],
};

export default data;
