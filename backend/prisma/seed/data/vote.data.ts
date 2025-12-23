// prisma/seed/data/vote.data.ts
import { USER_IDS } from './users.data';
import { PROBLEM_IDS } from './problems.data';

// Define EdgeOperationTargetType/EdgeOperationType locally to avoid importing from client in data file
type EdgeOperationTargetType =
  | 'SOLUTION'
  | 'SOLUTION_COMMENT'
  | 'FORUM_POST'
  | 'FORUM_COMMENT'
  | 'PROBLEM';
type EdgeOperationType = 'VOTE_UP' | 'VOTE_DOWN';

export interface VoteSeedData {
  target_id: string;
  target_type: EdgeOperationTargetType;
  user_id: string;
  operation_type: EdgeOperationType;
}


const data = {
  votes: [
    {
      target_id: PROBLEM_IDS.TWO_SUM.toString(),
      target_type: 'PROBLEM',
      user_id: USER_IDS.SHADCN,
      operation_type: 'VOTE_UP',
    },
    {
      target_id: PROBLEM_IDS.TWO_SUM.toString(),
      target_type: 'PROBLEM',
      user_id: USER_IDS.YUKI,
      operation_type: 'VOTE_UP',
    },
    {
      target_id: PROBLEM_IDS.LONGEST_SUBSTRING.toString(),
      target_type: 'PROBLEM',
      user_id: USER_IDS.DAVID,
      operation_type: 'VOTE_DOWN',
    },
  ] as VoteSeedData[],
};

export default data;
