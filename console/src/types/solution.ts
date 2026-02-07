export interface SolutionFeedItem {
  id: string;
  problem_id: string;
  problem?: {
    id: string;
    slug: string;
    title: string;
  };
  title: string;
  summary: string;
  highlight?: string;
  flair?: string;
  badges?: string[];
  authorId: string;
  isOwner?: boolean;
  author: {
    id: string;
    username: string;
    name: string;
    role: string;
    avatarColor?: string;
    avatar?: string;
  };
  stats: {
    views: number;
    comments: number;
    likes: number;
    dislikes: number;
  };
  score: number;
  is_pinned?: boolean;
  is_locked?: boolean;
  created_at: string;
  publishedAt: string; // alias or specific
  language: string;
  languageFilter?: string;
  topic?: {
    id: string;
    name: string;
    translatedName?: string;
  };
  topicName?: string; // flattened
  topicTranslated?: string; // flattened
  content: string;
  tags: string[];
  votes: number;
  views: number;
  likes: number; // flattened upvotes
  dislikes?: number; // flattened downvotes
  userVote?: 0 | 1 | -1;
}

export interface SolutionFeedResponse {
  items: SolutionFeedItem[];
  total: number;
  sortOptions?: { label: string; value: string }[];
}
