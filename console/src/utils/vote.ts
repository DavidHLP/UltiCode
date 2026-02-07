export type UserVote = 0 | 1 | -1;

export type VoteStateText = "upvoted" | "downvoted" | "neutral";

export const resolveUserVote = (
  userVote?: UserVote | null,
  voteState?: VoteStateText | null,
): UserVote => {
  if (userVote === 1 || userVote === -1 || userVote === 0) return userVote;
  if (voteState === "upvoted") return 1;
  if (voteState === "downvoted") return -1;
  return 0;
};

export const resolveVoteCounts = (
  likes?: number | null,
  dislikes?: number | null,
  stats?: { likes?: number | null; dislikes?: number | null } | null,
): { likes: number; dislikes: number } => {
  return {
    likes: likes ?? stats?.likes ?? 0,
    dislikes: dislikes ?? stats?.dislikes ?? 0,
  };
};
