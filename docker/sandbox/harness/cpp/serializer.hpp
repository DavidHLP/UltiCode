// UltiCode sandbox harness — wire serializer (C++).
//
// Bidirectional conversion between the stringified-JSON `value` fields of
// input.json and the typed C++ values the user's Solution method expects,
// plus typed result serialization. Semantics mirror the Java harness
// (Harness.java adaptArg/toListNode/fromListNode/toTreeNode/fromTreeNode/
// jsonable) so /run and /submit verdicts are byte-identical across languages.
//
// Key policy (aligned with Java Harness.isListLike): a null ListNode*/TreeNode*
// return is serialized as "[]", not "null" — LeetCode test data uses [] as the
// canonical empty answer, so a null return on empty input must still pass.
#ifndef ULTICODE_SERIALIZER_HPP
#define ULTICODE_SERIALIZER_HPP

#include "oj_types.hpp"

#include <string>
#include <vector>

namespace ulticode {

// Limits mirror Java Harness constants (LIST_NODE_TRAVERSAL_CAP,
// MAX_JSONABLE_NODES, MAX_NESTING_DEPTH).
inline constexpr int LIST_NODE_TRAVERSAL_CAP = 100000;
inline constexpr int MAX_JSONABLE_NODES = 100000;
inline constexpr int MAX_NESTING_DEPTH = 1000;

// ─── deserializers ──────────────────────────────────────────────────────────
// Each `value` field is a stringified JSON literal; these parse it into the
// typed C++ value matching the method argument. The generated runner selects
// one per input.json `type` hint (see main.cpp parserForType).
int parse_int(const std::string& wire);
long long parse_long(const std::string& wire);
double parse_double(const std::string& wire);
bool parse_bool(const std::string& wire);
std::string parse_string(const std::string& wire);
std::vector<int> parse_int_array(const std::string& wire);
std::vector<std::vector<int>> parse_int_2d_array(const std::string& wire);
std::vector<long long> parse_long_array(const std::string& wire);
std::vector<std::string> parse_string_array(const std::string& wire);
ListNode* parse_listnode(const std::string& wire);
std::vector<ListNode*> parse_listnode_array(const std::string& wire);
TreeNode* parse_treenode(const std::string& wire);
std::vector<TreeNode*> parse_treenode_array(const std::string& wire);

// ─── serializers (overload set, resolved at the runner call site) ───────────
// ListNode*/TreeNode*/vector overloads map null/empty to "[]" (list-like
// policy). Double serializers throw on non-finite values (→ per-case RE).
std::string serialize(int v);
std::string serialize(long long v);
std::string serialize(double v);
std::string serialize(bool v);
std::string serialize(const std::string& v);
std::string serialize(const std::vector<int>& v);
std::string serialize(const std::vector<std::vector<int>>& v);
std::string serialize(const std::vector<long long>& v);
std::string serialize(const std::vector<std::string>& v);
std::string serialize(ListNode* v);
std::string serialize(TreeNode* v);
// Node-array return types (DFORM ListNode[]/TreeNode[] equivalents). Without
// these, a valid solution returning vector<ListNode*> hits a missing-overload
// Compile Error at the generated runner's call site.
std::string serialize(const std::vector<ListNode*>& v);
std::string serialize(const std::vector<TreeNode*>& v);

}  // namespace ulticode

#endif  // ULTICODE_SERIALIZER_HPP
