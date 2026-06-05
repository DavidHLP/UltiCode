import { describe, expect, it } from "vitest";

import {
  buildCommentTree,
  buildSolutionCommentTree,
} from "../comment-tree-builder";
import type { ForumComment, SolutionComment } from "@/types/comment";

describe("comment-tree-builder", () => {
  it("keeps nested forum replies attached to rendered root comments", () => {
    const comments: ForumComment[] = [
      {
        id: "root",
        body: "root comment",
        authorId: "u1",
        authorUsername: "alice",
        createdAt: "2026-06-01T00:00:00Z",
        replies: [
          {
            id: "child",
            parentId: "root",
            body: "child comment",
            authorId: "u2",
            authorUsername: "bob",
            createdAt: "2026-06-01T00:01:00Z",
          },
        ],
      },
    ];

    const tree = buildCommentTree(comments);

    expect(tree).toHaveLength(1);
    expect(tree[0].children).toHaveLength(1);
    expect(tree[0].children?.[0]).toMatchObject({
      id: "child",
      author: "bob",
      content: "child comment",
    });
  });

  it("keeps flat forum replies attached to rendered root comments", () => {
    const comments: ForumComment[] = [
      {
        id: "root",
        body: "root comment",
        authorId: "u1",
        authorUsername: "alice",
        createdAt: "2026-06-01T00:00:00Z",
      },
      {
        id: "child",
        parentId: "root",
        body: "child comment",
        authorId: "u2",
        authorUsername: "bob",
        createdAt: "2026-06-01T00:01:00Z",
      },
    ];

    const tree = buildCommentTree(comments);

    expect(tree).toHaveLength(1);
    expect(tree[0].children?.map((child) => child.id)).toEqual(["child"]);
  });

  it("keeps solution replies attached to rendered root comments", () => {
    const comments: SolutionComment[] = [
      {
        id: "root",
        content: "root solution comment",
        authorId: "u1",
        authorUsername: "alice",
        createdAt: "2026-06-01T00:00:00Z",
      },
      {
        id: "child",
        parentId: "root",
        content: "child solution comment",
        authorId: "u2",
        authorUsername: "bob",
        createdAt: "2026-06-01T00:01:00Z",
      },
    ];

    const tree = buildSolutionCommentTree(comments);

    expect(tree).toHaveLength(1);
    expect(tree[0].children?.map((child) => child.id)).toEqual(["child"]);
  });
});
