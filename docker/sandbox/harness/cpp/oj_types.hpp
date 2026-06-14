// UltiCode sandbox harness — OJ data types (C++).
//
// Provides the linked-list / tree node structs that LeetCode-style user
// solutions reference. The harness exposes them via -I/opt/harness/cpp
// so the user's solution.cpp does NOT redefine them; a user who pastes a
// real `struct ListNode {...}` gets a g++ redefinition error which the
// harness maps to a Compile Error verdict (same policy as LeetCode).
//
// Mirrors docker/sandbox/harness/java/ListNode.java + TreeNode.java.
#ifndef ULTICODE_OJ_TYPES_HPP
#define ULTICODE_OJ_TYPES_HPP

namespace ulticode {

// singly-linked list node (LeetCode "Definition for singly-linked list").
struct ListNode {
    int val;
    ListNode* next;

    ListNode() : val(0), next(nullptr) {}
    explicit ListNode(int x) : val(x), next(nullptr) {}
    ListNode(int x, ListNode* nxt) : val(x), next(nxt) {}
};

// binary tree node (LeetCode "Definition for a binary tree node").
struct TreeNode {
    int val;
    TreeNode* left;
    TreeNode* right;

    TreeNode() : val(0), left(nullptr), right(nullptr) {}
    explicit TreeNode(int x) : val(x), left(nullptr), right(nullptr) {}
    TreeNode(int x, TreeNode* l, TreeNode* r) : val(x), left(l), right(r) {}
};

}  // namespace ulticode

#endif  // ULTICODE_OJ_TYPES_HPP
