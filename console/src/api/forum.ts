import { apiDelete, apiGet, apiPatch, apiPost } from "@/utils/request";
import type {
  ForumComment,
  ForumCommunity,
  ForumCommunityRule,
  ForumCommunityLink,
  ForumPost,
  ForumThread,
  ForumTag,
} from "@/types/forum";

export async function fetchForumPosts(): Promise<ForumPost[]> {
  const response = await apiGet<
    | Array<{
        userId: string;
        authorUsername?: string;
        authorAvatar?: string;
      } & Omit<ForumPost, "author">>
    | { items: Array<{ userId: string; authorUsername?: string; authorAvatar?: string } & Omit<ForumPost, "author">> }
  >("/forum/posts");

  // Backend returns PageResult(items=[...]) — extract items array
  const rows = Array.isArray(response) ? response : response.items;

  return rows.map((post) => ({
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername ?? post.userId,
      avatar: post.authorAvatar,
    },
  }));
}

export async function fetchForumPost(postId: string): Promise<ForumPost> {
  const post = await apiGet<
    {
      userId: string;
      authorUsername?: string;
      authorAvatar?: string;
      communityId?: string;
      communityName?: string;
      communitySlug?: string;
    } & Omit<ForumPost, "author" | "community">
  >(`/forum/posts/${postId}`);

  return {
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername ?? post.userId,
      avatar: post.authorAvatar,
    },
    community: post.communityId
      ? ({
          id: post.communityId,
          name: post.communityName ?? "",
          slug: post.communitySlug ?? "",
        } as ForumCommunity)
      : undefined,
  };
}

export async function fetchForumCommunities(options?: {
  featured?: boolean;
}): Promise<ForumCommunity[]> {
  return apiGet<ForumCommunity[]>("/forum/communities", {
    params: options?.featured ? { featured: "true" } : undefined,
  });
}

export async function fetchForumCommunity(slugOrId: string): Promise<{
  community: ForumCommunity | null;
  rules: ForumCommunityRule[];
  links: ForumCommunityLink[];
}> {
  return apiGet(`/forum/communities/${slugOrId}`);
}

export async function fetchCommunityPosts(
  slug: string,
  options?: { sortBy?: "hot" | "new" | "top" },
): Promise<ForumPost[]> {
  const response = await apiGet<
    | Array<{
        userId: string;
        authorUsername?: string;
        authorAvatar?: string;
      } & Omit<ForumPost, "author">>
    | { items: Array<{ userId: string; authorUsername?: string; authorAvatar?: string } & Omit<ForumPost, "author">> }
  >(`/forum/communities/${slug}/posts`, {
    params: options?.sortBy ? { sortBy: options.sortBy } : undefined,
  });

  // Backend returns PageResult(items=[...]) — extract items array
  const rows = Array.isArray(response) ? response : response.items;

  return rows.map((post) => ({
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername ?? post.userId,
      avatar: post.authorAvatar,
    },
  }));
}

export async function fetchForumTags(): Promise<ForumTag[]> {
  return apiGet<ForumTag[]>("/forum/tags");
}

export async function fetchForumQuickFilters(): Promise<
  Array<{ label: string; value: string }>
> {
  return apiGet<Array<{ label: string; value: string }>>(
    "/forum/quick-filters",
  );
}

export async function fetchForumThread(
  postId: string,
): Promise<ForumThread> {
  // Transform API response from { post, comments } wrapper to flat ForumThread structure
  const response = await apiGet<{
    post: ForumThread & {
      userId: string;
      authorUsername: string;
      authorAvatar: string;
    };
    comments: ForumComment[];
  }>(`/forum/posts/${postId}/thread`);

  const { post, comments } = response;

  // Transform flat author fields to nested author object that ForumPost expects
  return {
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername,
      avatar: post.authorAvatar,
    },
    comments,
  };
}

export async function createForumComment(
  postId: string,
  body: string,
  parentId?: string | null,
): Promise<void> {
  await apiPost<unknown>(`/forum/posts/${postId}/comments`, {
    body,
    parentId: parentId ?? null,
  });
}

export async function updateForumComment(
  commentId: string,
  body: string,
): Promise<void> {
  await apiPatch(`/forum/comments/${commentId}`, { body });
}

export async function deleteForumComment(commentId: string): Promise<void> {
  await apiDelete(`/forum/comments/${commentId}`);
}

export async function recordForumView(postId: string) {
  // Non-critical analytics — skip global auth error handler to avoid
  // clearing the user session on CSRF rotation or transient 403s
  return apiPost(`/forum/posts/${postId}/view`, {}, { skipErrorHandler: true });
}

export async function recordForumShare(postId: string) {
  return apiPost(`/forum/posts/${postId}/share`, {}, { skipErrorHandler: true });
}

export async function joinForumCommunity(id: string): Promise<void> {
  await apiPost(`/forum/communities/${id}/join`);
}

export async function leaveForumCommunity(id: string): Promise<void> {
  await apiPost(`/forum/communities/${id}/leave`);
}

export async function createForumPost(input: {
  title: string;
  excerpt: string;
  body?: string;
  communityId: string;
  tags?: string[];
  flairType?: string | null;
  flairLabel?: string | null;
  media?: unknown[];
}): Promise<ForumPost> {
  return apiPost<ForumPost>("/forum/posts", input);
}

export async function updateForumPost(
  postId: string,
  input: Partial<{
    title: string;
    excerpt: string;
    body?: string;
    tags: string[];
    flairType: string | null;
    flairLabel: string | null;
    isPinned: boolean;
    isLocked: boolean;
    media?: unknown[];
  }>,
): Promise<ForumPost> {
  return apiPatch<ForumPost>(`/forum/posts/${postId}`, input);
}

export async function deleteForumPost(postId: string): Promise<void> {
  await apiDelete(`/forum/posts/${postId}`);
}

export async function fetchMyForumPosts(): Promise<ForumPost[]> {
  const response = await apiGet<
    | Array<{
        userId: string;
        authorUsername?: string;
        authorAvatar?: string;
        communityId?: string;
        communityName?: string;
        communitySlug?: string;
      } & Omit<ForumPost, "author" | "community">>
    | {
        items: Array<{
          userId: string;
          authorUsername?: string;
          authorAvatar?: string;
          communityId?: string;
          communityName?: string;
          communitySlug?: string;
        } & Omit<ForumPost, "author" | "community">>;
      }
  >("/forum/me/posts");

  const rows = Array.isArray(response) ? response : response.items;

  return rows.map((post) => ({
    ...post,
    author: {
      id: post.userId,
      username: post.authorUsername ?? post.userId,
      avatar: post.authorAvatar,
    },
    community: post.communityId
      ? ({
          id: post.communityId,
          name: post.communityName ?? "",
          slug: post.communitySlug ?? "",
        } as ForumCommunity)
      : undefined,
  }));
}
