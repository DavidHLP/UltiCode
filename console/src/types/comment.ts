export interface Comment {
  id: string | number;
  author: string;
  avatar: string;
  time: string;
  votes: number;
  likes: number;
  dislikes: number;
  userVote?: 0 | 1 | -1;
  content: string;
  isOp?: boolean;
  isOwn?: boolean;
  children?: Comment[];
}
