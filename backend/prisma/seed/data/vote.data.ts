// prisma/seed/data/vote.data.ts
import { USER_IDS, USER_USERNAMES } from './users.data';
import { SOLUTION_IDS } from './solutions.data';

// Define VoteTargetType locally to avoid importing from client in data file
type VoteTargetType = 'SOLUTION' | 'SOLUTION_COMMENT' | 'FORUM_POST' | 'FORUM_COMMENT';

export interface VoteSeedData {
  target_id: string;
  target_type: VoteTargetType;
  user_id: string;
  vote_type: number; // 1 or -1
}

// Helper to get all user IDs as an array
const ALL_USERS = Object.values(USER_IDS);


const data = {
  votes: [
    // ========================================================================
    // SOLUTION VOTES
    // ========================================================================
    // 1. TWO_SUM_OPTIMAL (Popular: 15 upvotes, 0 downvotes)
    ...ALL_USERS.slice(0, 15).map(userId => ({
      target_id: SOLUTION_IDS.TWO_SUM_OPTIMAL,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),

    // 2. TWO_SUM_BRUTE (Controversial: 5 upvotes, 3 downvotes)
    // Upvotes
    ...ALL_USERS.slice(0, 5).map(userId => ({
      target_id: SOLUTION_IDS.TWO_SUM_BRUTE,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),
    // Downvotes (different users)
    ...ALL_USERS.slice(5, 8).map(userId => ({
      target_id: SOLUTION_IDS.TWO_SUM_BRUTE,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: -1
    })),

    // 3. TWO_SUM_CPP (Niche: 4 upvotes)
    ...ALL_USERS.slice(10, 14).map(userId => ({
      target_id: SOLUTION_IDS.TWO_SUM_CPP,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),

    // 4. TWO_SUM_JAVA (3 upvotes)
    ...ALL_USERS.slice(14, 17).map(userId => ({
      target_id: SOLUTION_IDS.TWO_SUM_JAVA,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),

    // 5. LONGEST_SUBSTR_SLIDING (High Quality: 12 upvotes)
    ...ALL_USERS.slice(3, 15).map(userId => ({
      target_id: SOLUTION_IDS.LONGEST_SUBSTR_SLIDING,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),

    // 6. MERGE_INTERVALS_SORT (8 upvotes)
    ...ALL_USERS.slice(0, 8).map(userId => ({
      target_id: SOLUTION_IDS.MERGE_INTERVALS_SORT,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),

    // 7. MEDIAN_ARRAYS_BS (Complex: 10 upvotes, 2 downvotes)
    ...ALL_USERS.slice(0, 10).map(userId => ({
      target_id: SOLUTION_IDS.MEDIAN_ARRAYS_BS,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),
    ...ALL_USERS.slice(10, 12).map(userId => ({
      target_id: SOLUTION_IDS.MEDIAN_ARRAYS_BS,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: -1
    })),

    // 8. ISLANDS_DFS (9 upvotes)
    ...ALL_USERS.slice(5, 14).map(userId => ({
      target_id: SOLUTION_IDS.ISLANDS_DFS,
      target_type: 'SOLUTION',
      user_id: userId,
      vote_type: 1
    })),

  ] as VoteSeedData[],
};

export default data;
