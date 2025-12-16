// prisma/seed/data/vote.data.ts
import { USER_IDS } from './users.data';

// Define VoteTargetType locally to avoid importing from client in data file
type VoteTargetType = 'SOLUTION' | 'SOLUTION_COMMENT' | 'FORUM_POST' | 'FORUM_COMMENT';

export interface VoteSeedData {
  target_id: string;
  target_type: VoteTargetType;
  user_id: string;
  vote_type: number; // 1 or -1
}


const data = {
  votes: [


  ] as VoteSeedData[],
};

export default data;
