// UltiCode sandbox harness — wire serializer implementation (C++).
#include "serializer.hpp"

#include "json.hpp"

#include <cmath>
#include <queue>
#include <stdexcept>
#include <unordered_set>

namespace ulticode {

// ─── scalar / array deserializers ───────────────────────────────────────────
int parse_int(const std::string& wire) {
    return static_cast<int>(parseJson(wire).asInteger());
}
long long parse_long(const std::string& wire) {
    return parseJson(wire).asInteger();
}
double parse_double(const std::string& wire) {
    return parseJson(wire).asNumber();
}
bool parse_bool(const std::string& wire) {
    Json v = parseJson(wire);
    if (!v.isBool()) throw std::runtime_error("expected boolean, got non-bool");
    return v.asBool();
}
std::string parse_string(const std::string& wire) {
    Json v = parseJson(wire);
    return v.isString() ? v.asString() : (v.isNull() ? std::string() : toJson(v));
}

std::vector<int> parse_int_array(const std::string& wire) {
    std::vector<int> out;
    Json v = parseJson(wire);
    if (!v.isArray()) throw std::runtime_error("expected JSON array for int[]");
    out.reserve(v.arr.size());
    for (const auto& e : v.arr) out.push_back(static_cast<int>(e.asInteger()));
    return out;
}
std::vector<std::vector<int>> parse_int_2d_array(const std::string& wire) {
    std::vector<std::vector<int>> out;
    Json v = parseJson(wire);
    if (!v.isArray()) throw std::runtime_error("expected JSON array for int[][]");
    for (const auto& row : v.arr) {
        if (!row.isArray()) throw std::runtime_error("expected inner array for int[][]");
        std::vector<int> r;
        r.reserve(row.arr.size());
        for (const auto& e : row.arr) r.push_back(static_cast<int>(e.asInteger()));
        out.push_back(std::move(r));
    }
    return out;
}
std::vector<long long> parse_long_array(const std::string& wire) {
    std::vector<long long> out;
    Json v = parseJson(wire);
    if (!v.isArray()) throw std::runtime_error("expected JSON array for long[]");
    for (const auto& e : v.arr) out.push_back(e.asInteger());
    return out;
}
std::vector<std::string> parse_string_array(const std::string& wire) {
    std::vector<std::string> out;
    Json v = parseJson(wire);
    if (!v.isArray()) throw std::runtime_error("expected JSON array for String[]");
    for (const auto& e : v.arr) {
        out.push_back(e.isString() ? e.asString() : (e.isNull() ? std::string() : toJson(e)));
    }
    return out;
}

// ─── ListNode / TreeNode deserializers ──────────────────────────────────────
namespace {
ListNode* listFromJsonArray(const Json& v) {
    if (!v.isArray() || v.arr.empty()) return nullptr;
    ListNode* head = new ListNode(static_cast<int>(v.arr[0].asInteger()));
    ListNode* cur = head;
    for (std::size_t i = 1; i < v.arr.size(); ++i) {
        cur->next = new ListNode(static_cast<int>(v.arr[i].asInteger()));
        cur = cur->next;
    }
    return head;
}
}  // namespace

ListNode* parse_listnode(const std::string& wire) {
    return listFromJsonArray(parseJson(wire));
}
std::vector<ListNode*> parse_listnode_array(const std::string& wire) {
    std::vector<ListNode*> out;
    Json v = parseJson(wire);
    if (!v.isArray()) throw std::runtime_error("expected JSON array for ListNode[]");
    out.reserve(v.arr.size());
    for (const auto& e : v.arr) {
        out.push_back(e.isArray() ? listFromJsonArray(e)
                                  : (e.isNull() ? nullptr : listFromJsonArray(e)));
    }
    return out;
}

TreeNode* parse_treenode(const std::string& wire) {
    Json v = parseJson(wire);
    if (!v.isArray() || v.arr.empty() || v.arr[0].isNull()) return nullptr;
    TreeNode* root = new TreeNode(static_cast<int>(v.arr[0].asInteger()));
    std::queue<TreeNode*> q;
    q.push(root);
    std::size_t i = 1;
    while (!q.empty() && i < v.arr.size()) {
        TreeNode* node = q.front();
        q.pop();
        if (i < v.arr.size()) {
            const Json& lv = v.arr[i++];
            if (!lv.isNull()) {
                node->left = new TreeNode(static_cast<int>(lv.asInteger()));
                q.push(node->left);
            }
        }
        if (i < v.arr.size()) {
            const Json& rv = v.arr[i++];
            if (!rv.isNull()) {
                node->right = new TreeNode(static_cast<int>(rv.asInteger()));
                q.push(node->right);
            }
        }
    }
    return root;
}
std::vector<TreeNode*> parse_treenode_array(const std::string& wire) {
    std::vector<TreeNode*> out;
    Json v = parseJson(wire);
    if (!v.isArray()) throw std::runtime_error("expected JSON array for TreeNode[]");
    out.reserve(v.arr.size());
    for (const auto& e : v.arr) {
        out.push_back(e.isArray() ? parse_treenode(toJson(e))
                                  : (e.isNull() ? nullptr : parse_treenode(toJson(e))));
    }
    return out;
}

// ─── serializers ────────────────────────────────────────────────────────────
std::string serialize(int v) {
    return toJson(Json::makeInt(v));
}
std::string serialize(long long v) {
    // Use the exact 64-bit integer payload so large longs (beyond 2^53)
    // round-trip without double rounding.
    return toJson(Json::makeInt(v));
}
std::string serialize(double v) {
    if (!std::isfinite(v)) {
        throw std::runtime_error("Non-finite number in result");
    }
    return toJson(Json::makeNumber(v));
}
std::string serialize(bool v) {
    return v ? "true" : "false";
}
std::string serialize(const std::string& v) {
    return toJson(Json::makeString(v));
}

namespace {
Json jsonableInts(const std::vector<int>& v) {
    Json a = Json::makeArray();
    a.arr.reserve(v.size());
    for (int x : v) a.arr.push_back(Json::makeInt(x));
    return a;
}
Json jsonableLongs(const std::vector<long long>& v) {
    Json a = Json::makeArray();
    a.arr.reserve(v.size());
    for (long long x : v) a.arr.push_back(Json::makeInt(x));
    return a;
}
}  // namespace

std::string serialize(const std::vector<int>& v) {
    return toJson(jsonableInts(v));
}
std::string serialize(const std::vector<long long>& v) {
    return toJson(jsonableLongs(v));
}
std::string serialize(const std::vector<std::string>& v) {
    Json a = Json::makeArray();
    a.arr.reserve(v.size());
    for (const auto& s : v) a.arr.push_back(Json::makeString(s));
    return toJson(a);
}
std::string serialize(const std::vector<std::vector<int>>& v) {
    Json a = Json::makeArray();
    a.arr.reserve(v.size());
    for (const auto& row : v) a.arr.push_back(jsonableInts(row));
    return toJson(a);
}

namespace {
// Build the LeetCode-style JSON array for a single linked list. Shared by
// serialize(ListNode*) and serialize(vector<ListNode*>).
Json jsonListNode(ListNode* head) {
    Json a = Json::makeArray();
    ListNode* cur = head;
    while (cur != nullptr && a.arr.size() < static_cast<std::size_t>(LIST_NODE_TRAVERSAL_CAP)) {
        a.arr.push_back(Json::makeInt(cur->val));
        cur = cur->next;
    }
    return a;
}

// Build the LeetCode level-order JSON array for a single tree (null root →
// empty array; cycle-guarded; trailing nulls trimmed). Shared by
// serialize(TreeNode*) and serialize(vector<TreeNode*>).
Json jsonTreeNode(TreeNode* root) {
    if (root == nullptr) return Json::makeArray();
    std::vector<Json> raw;
    std::unordered_set<TreeNode*> visited;
    std::queue<TreeNode*> q;
    q.push(root);
    while (!q.empty()) {
        TreeNode* node = q.front();
        q.pop();
        if (node == nullptr) {
            raw.push_back(Json::makeNull());
            continue;
        }
        if (!visited.insert(node).second) {
            throw std::runtime_error("Cyclic reference in TreeNode result");
        }
        if (static_cast<int>(raw.size()) > MAX_JSONABLE_NODES) {
            throw std::runtime_error("TreeNode exceeds node limit");
        }
        raw.push_back(Json::makeInt(node->val));
        q.push(node->left);
        q.push(node->right);
    }
    int end = static_cast<int>(raw.size());
    while (end > 0 && raw[static_cast<std::size_t>(end - 1)].isNull()) --end;
    Json a = Json::makeArray();
    for (int i = 0; i < end; ++i) a.arr.push_back(raw[static_cast<std::size_t>(i)]);
    return a;
}
}  // namespace

std::string serialize(ListNode* head) {
    // null head → "[]" (LeetCode list-like policy).
    return toJson(jsonListNode(head));
}

std::string serialize(const std::vector<ListNode*>& v) {
    Json a = Json::makeArray();
    a.arr.reserve(v.size());
    for (ListNode* head : v) a.arr.push_back(jsonListNode(head));
    return toJson(a);
}

std::string serialize(TreeNode* root) {
    return toJson(jsonTreeNode(root));
}

std::string serialize(const std::vector<TreeNode*>& v) {
    Json a = Json::makeArray();
    a.arr.reserve(v.size());
    for (TreeNode* root : v) a.arr.push_back(jsonTreeNode(root));
    return toJson(a);
}

}  // namespace ulticode
