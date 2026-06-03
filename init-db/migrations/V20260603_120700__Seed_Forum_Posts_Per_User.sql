-- Seed Test Data: Forum Posts (1 per user)
-- ------------------------------------------------------------
-- 拆分自 V20260602_120200__Insert_Test_Data.sql (Section: Forum)
-- 维护指南: 修改 / 扩展论坛帖子测试数据, 仅编辑本文件
--
-- 依赖: 必须先执行 V20260603_120300__Seed_Users_And_Permissions (user_id 引用)
-- 设计:  12 个用户每个 1 条 forum_post, 分布到 3 个 community
--        11 个用户使用固定 ID, admin (UUID) 通过子查询按 username 解析
-- 状态:  全部 neutral 投票, 未标记, 未删除 (2 条置顶公告, 其余普通帖)
-- 正文:  excerpt 字段是 post 主体内容 (前端 ForumEditorView 同步写 excerpt=body)
-- ------------------------------------------------------------

-- ===== 清理旧 V120700 残留数据 (id 前缀 fpost-001 ~ fpost-012) =====
DELETE FROM `forum_posts`
WHERE `id` IN (
    'fpost-001', 'fpost-002', 'fpost-003', 'fpost-004',
    'fpost-005', 'fpost-006', 'fpost-007', 'fpost-008',
    'fpost-009', 'fpost-010', 'fpost-011', 'fpost-012'
);

-- ===== 0. 预置 3 个 forum_communities =====
INSERT IGNORE INTO `forum_communities` (
    `id`, `name`, `slug`, `description`, `members`, `online`,
    `icon`, `color`, `banner`,
    `posts_count`, `posts_today`, `posts_week`,
    `is_official`, `is_featured`, `sort_order`, `created_at`, `visibility`
) VALUES
('fcomm-general', '综合交流', 'general', '程序人生、灌水、八卦、问答集合', 1200, 86, NULL, '#6366f1', NULL, 0, 0, 0, 1, 1, 10, NOW(3), 'PUBLIC'),
('fcomm-algorithms', '算法题解', 'algorithms', '分享算法思路、数据结构与复杂度优化', 850, 54, NULL, '#22c55e', NULL, 0, 0, 0, 1, 1, 20, NOW(3), 'PUBLIC'),
('fcomm-careers', '求职招聘', 'careers', '面试经验、内推、简历互评与 Offer 讨论', 640, 32, NULL, '#f59e0b', NULL, 0, 0, 0, 1, 0, 30, NOW(3), 'PUBLIC');

-- ===== 1. 预置 6 个 forum_tags =====
INSERT IGNORE INTO `forum_tags` (
    `id`, `name`, `slug`, `description`, `color`, `usage_count`, `created_at`
) VALUES
('ftag-hash',    '哈希表',   'hash',     '哈希表 / HashMap 相关讨论',     '#3b82f6', 0, NOW(3)),
('ftag-dp',      '动态规划', 'dp',       'DP 状态转移与优化',             '#8b5cf6', 0, NOW(3)),
('ftag-ll',      '链表',     'linked-list','链表操作与边界处理',          '#10b981', 0, NOW(3)),
('ftag-sql',     '数据库',   'sql',      'SQL 优化与索引设计',            '#ef4444', 0, NOW(3)),
('ftag-interview','面试',    'interview','面试经验与算法套路',           '#f59e0b', 0, NOW(3)),
('ftag-career',  '求职',     'career',   '求职准备与职业发展',           '#14b8a6', 0, NOW(3));

-- ===== 2. 预置 forum_users (legacy 镜像表) =====
INSERT IGNORE INTO `forum_users` (`id`, `username`, `avatar`, `karma`) VALUES
('user-alice-001',  'alice_coder',   NULL, 320),
('user-bob-002',    'bob_dev',       NULL, 180),
('user-carol-003',  'carol_wu',      NULL, 260),
('user-david-004',  'david_chen',    NULL, 410),
('user-eva-005',    'eva_zhang',     NULL, 220),
('user-frank-006',  'frank_lee',     NULL, 150),
('mod-mike-001',    'mike_mod',      NULL, 520),
('mod-nina-002',    'nina_mod',      NULL, 480),
('super-root-001',  'super_root',    NULL, 999),
('super-vp-002',    'super_vp',      NULL, 880),
('admin-002',       'admin_two',     NULL, 700),
('9f6bc78a-5f21-11f1-950a-8ef0eeeb1ca8', 'admin', NULL, 1200);

-- ===== 3. 12 个 forum_post: 1 user = 1 post, 每条 excerpt 均为 500~1500 字 Markdown 正文 =====
INSERT IGNORE INTO `forum_posts` (
    `id`, `community_id`, `user_id`, `permalink`, `title`,
    `flair_type`, `flair_label`, `tags`, `excerpt`, `media`, `recommendation`,
    `vote_state`, `is_saved`, `impressions`,
    `is_pinned`, `is_locked`, `created_at`,
    `stats`, `views`,
    `is_flagged`, `flagged_reason`, `flagged_at`,
    `is_deleted`, `deleted_at`, `deleted_by`
) VALUES

-- 1. user-alice-001 (USER) → fcomm-algorithms  题解长文
('fpost-001', 'fcomm-algorithms', 'user-alice-001',
 'two-sum-hashmap-once-pass',
 '两数之和 哈希表一次遍历的最优解 (含 4 种语言实现 + 复杂度推导)',
 'showcase', '题解',
 JSON_ARRAY('ftag-hash', 'ftag-interview'),
 '# 两数之和 哈希表一次遍历的最优解

## 题目回顾

给定一个整数数组 `nums` 和目标值 `target`, 请返回两个下标 `i`、`j`, 使得 `nums[i] + nums[j] = target`。每个输入只对应一个答案, 不能重复使用同一个元素。

最朴素的想法是双重循环, 枚举所有 `(i, j)` 对, 时间复杂度 `O(n^2)`。当 `n` 超过 10^4 就会明显超时。下面我们用哈希表把它降到 `O(n)`。

## 思路

遍历数组的同时, 用哈希表维护 `value → index` 的映射。对于当前位置 `i`, 我们要找的是 `target - nums[i]` 这个值之前是否出现过。如果出现过, 直接返回它在哈希表里记录的下标。

## 关键点

- **一次遍历**: 不需要先把所有元素都塞进哈希表再开始查, 边遍历边维护即可。
- **下标存放位置**: 表里存的是 `value → index`, 不是 `index → value`, 否则查表时还要再扫一遍数组。
- **避免自匹配**: 题目保证答案唯一, 边遍历边写入天然避免了 `i == j` 的问题。

## Python 实现

```python
class Solution:
    def twoSum(self, nums: list[int], target: int) -> list[int]:
        seen = {}  # value -> index
        for i, n in enumerate(nums):
            if target - n in seen:
                return [seen[target - n], i]
            seen[n] = i
        return []
```

## Java 实现

```java
class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int need = target - nums[i];
            if (seen.containsKey(need)) {
                return new int[]{seen.get(need), i};
            }
            seen.put(nums[i], i);
        }
        return new int[0];
    }
}
```

## JavaScript 实现

```javascript
function twoSum(nums, target) {
  const seen = new Map();
  for (let i = 0; i < nums.length; i++) {
    const need = target - nums[i];
    if (seen.has(need)) return [seen.get(need), i];
    seen.set(nums[i], i);
  }
  return [];
}
```

## Go 实现

```go
func twoSum(nums []int, target int) []int {
    seen := make(map[int]int)
    for i, n := range nums {
        if j, ok := seen[target-n]; ok {
            return []int{j, i}
        }
        seen[n] = i
    }
    return nil
}
```

## 复杂度分析

- **时间**: `O(n)`, 哈希表查找均摊 `O(1)`, 整个数组只扫一遍。
- **空间**: `O(n)`, 极端情况下所有元素都不匹配, 哈希表里要存 `n` 个键。

## 变种思考

如果题目改成"返回所有满足条件的下标对", 就不能用一次遍历了, 需要 `O(n)` 建表 + `O(k)` 输出。或者用双指针先排序再扫, 但那样会改变原数组顺序, 看场景取舍。',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/800x400/6366f1/ffffff?text=HashMap+Trace', 'alt', '两数之和哈希表遍历轨迹图'),
   JSON_OBJECT('type', 'code-embed', 'language', 'python', 'content', 'seen = {}\nfor i, n in enumerate(nums): ...')
 ),
 JSON_OBJECT('related', JSON_ARRAY('fpost-004'), 'algorithm_hint', '同属哈希表+一次遍历套路'),
 'neutral', 0, 1280, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 86, 'downvotes', 2, 'comments', 12, 'bookmarks', 24),
 1280, 0, NULL, NULL, 0, NULL, NULL),

-- 2. user-bob-002 (USER) → fcomm-algorithms
('fpost-002', 'fcomm-algorithms', 'user-bob-002',
 'reverse-linkedlist-three-pointers',
 '反转链表: 三指针原地迭代解法 (含边界情况 + 图解)',
 'showcase', '题解',
 JSON_ARRAY('ftag-ll'),
 '# 反转链表: 三指针原地迭代

## 问题

给你单链表的头节点 `head`, 请反转链表并返回新的头节点。

## 为什么不用额外空间

很多人第一反应是把节点逐个 `push` 进栈, 再 `pop` 出来连成新链表, 时间 `O(n)`、空间 `O(n)`。但我们可以用三个指针在原链表上原地反转, 空间降到 `O(1)`。

## 思路

维护三个指针:
- `prev`: 初始为 `null`, 表示已经反转部分的尾巴
- `curr`: 初始为 `head`, 当前要处理的节点
- `next`: 临时保存 `curr.next`, 因为下一步要把 `curr.next` 改成 `prev`

每一步: 先用 `next` 记住 `curr.next`, 然后把 `curr.next` 指向 `prev`, 再把 `prev`、`curr` 整体后移。

## Java 实现

```java
class Solution {
    public ListNode reverseList(ListNode head) {
        ListNode prev = null, curr = head;
        while (curr != null) {
            ListNode next = curr.next;  // 1. 先存
            curr.next = prev;           // 2. 反向
            prev = curr;                // 3. prev 后移
            curr = next;                // 4. curr 后移
        }
        return prev;
    }
}
```

## 边界情况

| 输入 | 期望 | 我的实现 |
|------|------|----------|
| 空链表 `null` | `null` | `curr` 直接为 `null`, 跳过循环, 返回 `null` ✅ |
| 单节点 `1->null` | `1->null` | 循环一次, `prev=1, curr=null`, 返回 `prev` ✅ |
| 两节点 `1->2->null` | `2->1->null` | 两轮, 最终 `prev=2, curr=null` ✅ |
| 循环引用 | 不合法 | 不会终止, 但题目保证无环 |

## 复杂度

- 时间: `O(n)`, 每个节点访问一次。
- 空间: `O(1)`, 三个指针。

## 递归解法对比

递归也能写, 但递归深度是 `O(n)`, 在链很长时会爆栈。面试时建议先写迭代, 再口头提一下递归版本。

```java
public ListNode reverseList(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode newHead = reverseList(head.next);
    head.next.next = head;
    head.next = null;
    return newHead;
}
```

## 一句话总结

> 保存后继 → 反向指针 → 整体后移, 三步循环直到 `curr == null`。',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/800x300/10b981/ffffff?text=3+Pointers+Trace', 'alt', '三指针迭代轨迹')
 ),
 NULL,
 'neutral', 0, 920, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 54, 'downvotes', 1, 'comments', 8, 'bookmarks', 17),
 920, 0, NULL, NULL, 0, NULL, NULL),

-- 3. user-carol-003 (USER) → fcomm-algorithms
('fpost-003', 'fcomm-algorithms', 'user-carol-003',
 'add-two-numbers-carry',
 '两数相加链表版: 进位处理的两种边界 + 哑节点技巧',
 'discussion', '讨论',
 JSON_ARRAY('ftag-ll'),
 '# 两数相加 (链表版) 详解

## 题目

两个非空链表代表两个非负整数, 数字按逆序存储, 每个节点一位。把两数相加, 返回同样形式的链表。

例: `2->4->3` + `5->6->4` = `7->0->8` (即 342 + 465 = 807)。

## 思路

同时遍历两条链表, 维护一个进位变量 `carry`。每轮取两节点值 (缺失补 0) 加上进位, 当前位 = 和 % 10, 新进位 = 和 / 10。

## Python 实现

```python
class Solution:
    def addTwoNumbers(self, l1: ListNode, l2: ListNode) -> ListNode:
        dummy = ListNode(0)         # 哑节点, 简化头节点处理
        cur, carry = dummy, 0
        while l1 or l2 or carry:    # 关键: 循环条件必须包含 carry
            v1 = l1.val if l1 else 0
            v2 = l2.val if l2 else 0
            s = v1 + v2 + carry
            cur.next = ListNode(s % 10)
            carry = s // 10
            cur = cur.next
            l1 = l1.next if l1 else None
            l2 = l2.next if l2 else None
        return dummy.next
```

## 两个最容易踩的坑

### 1. 漏掉最高位进位

如果只写 `while l1 or l2`, 当两个链表都遍历完但 `carry` 还是 1 时 (例: 5 + 5 = 10), 程序会直接退出, 丢掉最高位。**正确写法是 `while l1 or l2 or carry`**。

### 2. 头节点处理

如果不用哑节点, 第一轮就要单独判断"当前节点是不是头节点", 代码会很丑。引入一个 `dummy` 节点, 让所有节点都用统一的 `cur.next = ...` 处理, 循环结束后返回 `dummy.next` 即可。

## 复杂度

- 时间: `O(max(m, n))`, 取决于较长链表长度。
- 空间: `O(max(m, n))`, 新链表长度。

## 跟进的练习题

- [LeetCode 445] 两数相加 II (正序存储), 难度上升一档, 要用栈辅助。
- [LeetCode 67] 二进制版, 思路完全一致, 只是 base 从 10 变 2。

## 评论区想讨论的点

大家觉得进位处理时, 用 `if carry` 单独加一个尾部节点 (代码更短但分支多) 好不好? 还是坚持统一循环条件 (代码略长但逻辑直白)?',
 NULL,
 JSON_OBJECT('related', JSON_ARRAY('fpost-002'), 'difficulty', 'medium'),
 'neutral', 0, 760, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 41, 'downvotes', 0, 'comments', 15, 'bookmarks', 9),
 760, 0, NULL, NULL, 0, NULL, NULL),

-- 4. user-david-004 (USER) → fcomm-algorithms
('fpost-004', 'fcomm-algorithms', 'user-david-004',
 'longest-substring-sliding-window',
 '滑动窗口解最长无重复子串 (含 HashMap 与数组两种实现)',
 'showcase', '题解',
 JSON_ARRAY('ftag-hash'),
 '# 滑动窗口解最长无重复子串

## 题目

给定一个字符串 `s`, 请找出其中不包含重复字符的最长子串的长度。

## 暴力思路

枚举所有子串 `(i, j)`, 判断 `s[i..j]` 是否有重复字符。时间 `O(n^3)`, `n=10^5` 必爆。

## 滑动窗口

用一个左闭右开区间 `[left, right)` 作为窗口, 哈希表记录窗口内字符最近一次出现的下标。右指针每次右移一格, 如果该字符已经在窗口内, 就把 `left` 跳到它上次出现位置的下一格。

## JavaScript 实现

```javascript
function lengthOfLongestSubstring(s) {
  const idx = new Map();
  let left = 0, ans = 0;
  for (let right = 0; right < s.length; right++) {
    const c = s[right];
    if (idx.has(c) && idx.get(c) >= left) {
      left = idx.get(c) + 1;
    }
    idx.set(c, right);
    ans = Math.max(ans, right - left + 1);
  }
  return ans;
}
```

## 为什么用 `idx.get(c) >= left` 这个判断

哈希表里可能存着窗口外的旧位置。比如字符串 `"abba"`, 处理到第二个 `b` 时, 哈希表里 `a` 还指向 0。但此时 `left` 已经是 2 了, 旧的 0 在窗口外, 不应触发收缩。

## 数组版 (字符集有限时更快)

如果字符集已知且较小 (比如只含 ASCII), 可以用 128 长度的数组代替哈希表, 性能更稳定:

```javascript
function lengthOfLongestSubstring(s) {
  const idx = new Array(128).fill(-1);
  let left = 0, ans = 0;
  for (let right = 0; right < s.length; right++) {
    const c = s.charCodeAt(right);
    if (idx[c] >= left) left = idx[c] + 1;
    idx[c] = right;
    ans = Math.max(ans, right - left + 1);
  }
  return ans;
}
```

## 复杂度

- 时间: `O(n)`, 每个字符被访问常数次。
- 空间: `O(字符集大小)`, 哈希表最多 `O(n)`。

## 模板

```text
int left = 0, ans = 0;
for (int right = 0; right < n; right++) {
    window.add(s[right]);
    while (window 不满足条件) {
        window.remove(s[left]);
        left++;
    }
    ans = max(ans, right - left + 1);
}
```

这是"求最长/最大"类滑动窗口的标准模板, 把"求最短"换成外层 `while` 收缩条件即可。',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/800x350/3b82f6/ffffff?text=Sliding+Window', 'alt', '滑动窗口示意图')
 ),
 JSON_OBJECT('related', JSON_ARRAY('fpost-001'), 'tag_chain', 'hash'),
 'neutral', 0, 1480, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 112, 'downvotes', 3, 'comments', 22, 'bookmarks', 38),
 1480, 0, NULL, NULL, 0, NULL, NULL),

-- 5. user-eva-005 (USER) → fcomm-careers
('fpost-005', 'fcomm-careers', 'user-eva-005',
 'median-sorted-arrays-interview',
 '面试被问两正序数组中位数: 我的完整复盘 + 求指点',
 'question', '求助',
 JSON_ARRAY('ftag-interview', 'ftag-career'),
 '# 面试被问两正序数组中位数: 求指点

## 背景

昨天面字节后端, 二面算法题: 给定两个升序数组 `nums1`、`nums2`, 找它们合并后的中位数, 要求 `O(log(min(m, n)))`。我写出了核心二分, 但被追问时脑子一片空白, 跪了。

## 我当时的解法

```cpp
class Solution {
public:
    double findMedianSortedArrays(vector<int>& a, vector<int>& b) {
        if (a.size() > b.size()) return findMedianSortedArrays(b, a);
        int m = a.size(), n = b.size();
        int lo = 0, hi = m;
        while (lo <= hi) {
            int i = (lo + hi) / 2;
            int j = (m + n + 1) / 2 - i;
            int aLeft  = (i == 0) ? INT_MIN : a[i-1];
            int aRight = (i == m) ? INT_MAX : a[i];
            int bLeft  = (j == 0) ? INT_MIN : b[j-1];
            int bRight = (j == n) ? INT_MAX : b[j];
            if (aLeft <= bRight && bLeft <= aRight) {
                if ((m + n) % 2 == 0)
                    return (max(aLeft, bLeft) + min(aRight, bRight)) / 2.0;
                return max(aLeft, bLeft);
            } else if (aLeft > bRight) {
                hi = i - 1;
            } else {
                lo = i + 1;
            }
        }
        return 0.0;
    }
};
```

写是写出来了, 但面试官追问的几个问题我都没答好:

## 1. "为什么对较短的数组二分?"

我说了"短数组二分搜索空间小", 但说不出更深的理由。猜测是为了保证 `j` 的取值一定合法 (因为 `i ∈ [0, m]`, `j = (m+n+1)/2 - i` 在 `m ≤ n` 时才能保证 `j ∈ [0, n]`)。

## 2. "为什么不二分较长那个?"

当时我愣了, 越想越乱。是不是因为较短那个二分时, 边界条件更少 (`i=0` 和 `i=m` 两种极端情况更可控)?

## 3. "奇偶长度统一怎么理解?"

`(m+n+1)/2` 这个式子是左半部分的元素数, 加上中位数自身。我理解但说不清为什么"奇数"和"偶数"可以统一在同一个公式里。

## 4. "工程上你会怎么选?"

我说"看场景", 但其实没想清楚。如果是热路径每秒百万次调用, 我会选 `O(log(min(m,n)))`; 如果是用户级接口, 简单 `merge` 后取中位数 `O(m+n)` 完全够用, 代码可读性更重要。

## 求大家指点

- 上面 4 个问题大家会怎么答?
- 有没有更接地气的讲解博客? (我看过官方题解, 但偏数学)
- 面试中被追问基础概念答不上来, 怎么挽回?',
 NULL,
 JSON_OBJECT('related', JSON_ARRAY('fpost-008', 'fpost-010')),
 'neutral', 0, 540, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 28, 'downvotes', 0, 'comments', 19, 'bookmarks', 5),
 540, 0, NULL, NULL, 0, NULL, NULL),

-- 6. user-frank-006 (USER) → fcomm-general
('fpost-006', 'fcomm-general', 'user-frank-006',
 'merge-k-lists-priority-queue',
 '合并 K 个升序链表: 最小堆 vs 分治, 工程上我选堆',
 'discussion', '讨论',
 JSON_ARRAY('ftag-ll'),
 '# 合并 K 个升序链表: 最小堆 vs 分治

## 题目

给定 `k` 条升序链表, 合并成一条升序链表。LeetCode 23, 难度 Hard。

## 解法 A: 最小堆 (优先队列)

把每个链表的头节点入最小堆, 每次弹出堆顶 (最小值), 接到结果链表, 再把该节点的 `next` 压入堆。

```cpp
class Solution {
public:
    ListNode* mergeKLists(vector<ListNode*>& lists) {
        auto cmp = [](ListNode* a, ListNode* b) { return a->val > b->val; };
        priority_queue<ListNode*, vector<ListNode*>, decltype(cmp)> pq(cmp);
        for (auto h : lists) if (h) pq.push(h);
        ListNode dummy(0), *cur = &dummy;
        while (!pq.empty()) {
            auto n = pq.top(); pq.pop();
            cur->next = n;
            cur = cur->next;
            if (n->next) pq.push(n->next);
        }
        return dummy.next;
    }
};
```

## 解法 B: 分治递归

借鉴归并排序的两两合并策略: 递归地将链表数组折半, 直到子数组长度 ≤ 1, 再两两合并。

```cpp
class Solution {
public:
    ListNode* mergeKLists(vector<ListNode*>& lists) {
        if (lists.empty()) return nullptr;
        return merge(lists, 0, lists.size() - 1);
    }
private:
    ListNode* merge(vector<ListNode*>& lists, int lo, int hi) {
        if (lo == hi) return lists[lo];
        int mid = lo + (hi - lo) / 2;
        return mergeTwo(merge(lists, lo, mid), merge(lists, mid + 1, hi));
    }
    ListNode* mergeTwo(ListNode* a, ListNode* b) {
        ListNode dummy(0), *cur = &dummy;
        while (a && b) {
            if (a->val < b->val) { cur->next = a; a = a->next; }
            else { cur->next = b; b = b->next; }
            cur = cur->next;
        }
        cur->next = a ? a : b;
        return dummy.next;
    }
};
```

## 复杂度对比

| 维度 | 最小堆 | 分治 |
|------|--------|------|
| 时间 | `O(N log k)` | `O(N log k)` |
| 空间 | `O(k)` 堆 | `O(log k)` 递归栈 |
| 实现难度 | 中 (优先队列的 lambda) | 中 (递归边界 + 哑节点) |
| 调试难度 | 低 (循环结构清晰) | 中 (递归调用栈深) |
| 工程稳定性 | 高 | 中 (栈深时可能爆栈) |

## 我选堆的 3 个理由

1. **可调试性**: 堆版本是循环结构, 我可以打断点看每一步堆顶是谁, 而递归版断点跳来跳去, 复杂链表很难追。
2. **线上稳定性**: 链表长度极端时 (比如 `k=10000`), 递归版会爆栈; 堆版本无栈深限制。
3. **代码可读**: 团队里新人多, 堆版本 "一次弹一个、压一个" 的语义比"递归到叶子再回溯"更直观。

## 大家怎么选?

有没有生产环境实际跑过分治 + 尾递归优化版本? 性能差距真的有理论上那么大吗? 欢迎大家分享经验。',
 NULL,
 JSON_OBJECT('related', JSON_ARRAY('fpost-002', 'fpost-003')),
 'neutral', 0, 410, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 33, 'downvotes', 2, 'comments', 27, 'bookmarks', 6),
 410, 0, NULL, NULL, 0, NULL, NULL),

-- 7. mod-mike-001 (MODERATOR) → fcomm-general  社区规则公告
('fpost-007', 'fcomm-general', 'mod-mike-001',
 'community-rules-2026',
 '社区规则更新 (2026 版): 求职帖需附 offer 截图, 重复帖将被下沉',
 'announcement', '公告',
 JSON_ARRAY('ftag-career'),
 '# 社区规则更新 (2026 版)

## 背景

最近两周, 求职招聘区出现了大量重复/虚假/钓鱼帖, 包括:
- 同一个 offer 信息被改个公司名反复发
- "培训贷" 套路贴伪装成内推
- 一些账号 (基本是注册当天) 集中发 "求内推", 但回复时引导加私人微信

为维护社区质量, 经运营组与版主团队讨论, 决定更新以下规则, **自 2026-06-10 起严格执行**。

## 新增规则

### 1. 求职帖必须附 offer 截图或 offer 邮件

任何 "我拿到了 X 公司 Y 岗位 offer, 求评价" 类主题, 必须附上:
- offer 邮件截图 (含姓名打码)
- 或 offer letter 关键页 (公司抬头、岗位、薪资范围可打码, 但岗位标识必须可见)

未附带的, 一律下沉至 "待补充材料" 标签, 7 天内未补全则删除。

### 2. 内推帖必须明确公司、岗位、内推人

模板:
```
[公司] [岗位] [工作地] [内推人 (可匿名但需可联系)]
[JD 关键 3 条]
[内推方式: 邮件 / 链接 / 私信]
```

缺任何一项视为无效帖。

### 3. 培训贷 / 套路帖零容忍

凡涉及以下关键词的, 立即锁定 + 标记 + 隐藏, 账号直接永封:
- "包就业 / 包 offer / 0 基础入学"
- "学完分期 / 培训贷 / 先学后付"
- "内推保过 / 保 offer"

### 4. 一周内同账号同主题最多 3 帖

避免"灌水刷屏"。超出的自动合并到上一帖。

## 申诉通道

如果你认为你的帖子被误判, 请在帖内 @任意版主, 或发邮件到 `report@ulticode.example.com`, 我们会在 24 小时内回复。

## 致谢

感谢 @nina_mod 整理的违规案例库, 也感谢所有积极举报的社区成员。社区质量靠大家, 我们一起维护。',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/1200x400/f59e0b/ffffff?text=Community+Rules+2026', 'alt', '社区规则更新 banner')
 ),
 NULL,
 'neutral', 0, 2100, 1, 0, NOW(3),
 JSON_OBJECT('upvotes', 156, 'downvotes', 8, 'comments', 64, 'bookmarks', 21),
 2100, 0, NULL, NULL, 0, NULL, NULL),

-- 8. mod-nina-002 (MODERATOR) → fcomm-careers
('fpost-008', 'fcomm-careers', 'mod-nina-002',
 'interview-cheatsheet-2026',
 '2026 届秋招算法面试 cheatsheet (按出现频率排序, 持续更新)',
 'showcase', '资料',
 JSON_ARRAY('ftag-interview', 'ftag-career'),
 '# 2026 届秋招算法面试 cheatsheet

> 维护者: @nina_mod, 上次更新: 2026-06-02
> 数据来源: 过去 6 个月社区分享的 200+ 真实面经
> 欢迎评论区补充, 采纳后署名 + 资料贡献

## 怎么用这份 cheatsheet

按"出现频率"降序排列, 越靠前出现越多次。每道题给出:
- 难度 (Easy / Medium / Hard)
- 出现过的公司 (出现 ≥ 3 次的公司列出)
- 关键思路 (一句话)
- 推荐的 follow-up (面试官常追问的方向)

## Tier 1: 出现 ≥ 30 次, 必背

| 排名 | 题目 | 难度 | 关键思路 | 出现公司 |
|------|------|------|----------|----------|
| 1 | 两数之和 | Easy | 哈希表一次遍历 | 字节、阿里、腾讯、美团、京东 |
| 2 | 反转链表 | Easy | 三指针原地 | 字节、阿里、腾讯、美团、华为 |
| 3 | 有效的括号 | Easy | 栈匹配 | 字节、腾讯、美团 |
| 4 | 二分查找 | Easy | 模板 + 边界 | 全部 |
| 5 | 合并两个有序链表 | Easy | 哑节点 + 双指针 | 字节、阿里 |
| 6 | LRU 缓存 | Medium | 哈希表 + 双向链表 | 字节、阿里、腾讯、美团、滴滴 |
| 7 | 二叉树的层序遍历 | Medium | BFS 队列 | 字节、阿里、腾讯 |
| 8 | 最长无重复子串 | Medium | 滑动窗口 | 字节、腾讯、美团 |
| 9 | 岛屿数量 | Medium | DFS/BFS 标记 | 字节、阿里、腾讯 |
| 10 | 接雨水 | Hard | 双指针 / 单调栈 | 字节、阿里 |

## Tier 2: 出现 15~30 次, 高频

(列表略, 完整版见 GitHub 仓库, 评论区求更可以补全)

## Tier 3: 出现 5~15 次, 选择性准备

(列表略)

## 复盘模板 (我每次面试完都填)

```markdown
## 公司: XXX
## 岗位: XXX
## 轮次: X 面
## 题目:
1. ...
2. ...
## 我答得如何:
- 第 1 题: ✅ 一次 AC, 时间复杂度讲清楚, 追问也答上来
- 第 2 题: ❌ 思路对了, 但边界 case 漏掉 (空指针), 面试官提示后改对
## 复盘:
- 准备度: 7/10
- 表达: 6/10 (紧张, 声音抖)
- 下次改进: 写代码前先口述一遍思路
```

## 评论区我想收集

- 你最近一次面试的 Tier 1 题目, 命中了 cheatsheet 的哪几道?
- 哪些 Tier 2 / Tier 3 题目在你那家公司出现频率反常地高? (可能说明该公司偏好某类题型)
- 你有什么"压箱底"的面试技巧? 我会整合到月度更新里。',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/1200x600/14b8a6/ffffff?text=Interview+Cheatsheet', 'alt', 'cheatsheet 封面'),
   JSON_OBJECT('type', 'link', 'url', 'https://github.com/ulticode/cheatsheet-2026', 'title', 'GitHub 仓库 (持续更新)')
 ),
 JSON_OBJECT('related', JSON_ARRAY('fpost-005', 'fpost-010'), 'tag_chain', JSON_ARRAY('ftag-interview', 'ftag-career')),
 'neutral', 0, 3400, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 240, 'downvotes', 4, 'comments', 88, 'bookmarks', 312),
 3400, 0, NULL, NULL, 0, NULL, NULL),

-- 9. super-root-001 (SUPER_ADMIN) → fcomm-general  平台公告
('fpost-009', 'fcomm-general', 'super-root-001',
 'platform-roadmap-q3-2026',
 '平台 Q3 路线图: 评测机扩容 + 防作弊 2.0 + 全新讨论区',
 'announcement', '官方',
 JSON_ARRAY(),
 '# 平台 Q3 路线图 (2026 年 7~9 月)

各位用户好, 这是平台 Q3 的核心规划。详细 PRD 已在 GitHub 公开, 欢迎大家提 issue。

## 一、评测机扩容 (7 月)

**背景**: Q2 末评测队列平均等待 12 秒, 高峰期达到 45 秒, 严重影响用户提交体验。

**改造**:
- 评测机从 32 核扩到 **128 核** (2 台 64 核物理机)
- 引入**预判调度**: 提交时根据历史数据预判评测时长, 优先调度长任务到空闲节点
- **结果**: 期望平均排队 12s → 3s, 95 分位 45s → 10s

**风险**: 新增节点首次部署可能有不稳定, 7 月前两周会有"灰度" (10% 流量)。

## 二、防作弊 2.0 (8 月)

**目标**: 打击"题解抄袭 + 变量名替换"和"AI 代写"两大类作弊。

**手段**:
1. **静态分析层**: 检测可疑的"高熵变量名 + 异常注释缺失"模式
2. **行为层**: 检测"提交前 3 秒内大量浏览题解"的反常行为
3. **社区举报层**: 与版主团队联动, 7 天内累计 3 次举报的提交进入人工审核

**注意**: 防作弊 2.0 不会影响普通用户的正常提交, 只针对明确违规。

## 三、讨论区重构 (9 月)

**痛点**: 当前讨论区是简单"帖子 + 评论", 信息密度低, 老帖沉没快, 优质讨论难沉淀。

**改造**:
- 引入**标签系统** (前端 v2, 已部分上线)
- 引入**Wiki 模式**: 优质讨论可被版主或原作者标记为 Wiki, 进入对应知识库
- 引入**悬赏机制**: 提问者可悬赏积分, 答主可获积分, 引导高质量回答

**注意**: 9 月改造对老数据完全兼容, 不会迁移或删除任何帖子。

## 四、其他

- **i18n**: 英文版 UI 已完成 60%, 7 月底全量上线
- **移动端**: PWA 体验优化, 包括离线题单、推送通知
- **可访问性**: 屏幕阅读器适配, 符合 WCAG 2.1 AA

## 反馈渠道

- 产品 issue: GitHub `ulticode/ulticode` 仓库
- 紧急问题: `urgent@ulticode.example.com`
- 一般建议: 评论区

## 致谢

感谢过去一个季度在社区里提了大量高质量建议的 200+ 位用户, 我们会持续把社区声音反映到产品决策里。',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/1200x500/6366f1/ffffff?text=Q3+Roadmap', 'alt', 'Q3 路线图 banner'),
   JSON_OBJECT('type', 'link', 'url', 'https://github.com/ulticode/ulticode/issues/2026-q3', 'title', '完整 PRD')
 ),
 JSON_OBJECT('related', JSON_ARRAY('fpost-007', 'fpost-012')),
 'neutral', 0, 5200, 1, 0, NOW(3),
 JSON_OBJECT('upvotes', 412, 'downvotes', 6, 'comments', 156, 'bookmarks', 198),
 5200, 0, NULL, NULL, 0, NULL, NULL),

-- 10. super-vp-002 (SUPER_ADMIN) → fcomm-algorithms
('fpost-010', 'fcomm-algorithms', 'super-vp-002',
 'dp-state-design-patterns',
 '动态规划状态设计的 4 种套路 + 实战',
 'showcase', '题解',
 JSON_ARRAY('ftag-dp'),
 '# 动态规划状态设计的 4 种套路

很多人觉得 DP 难, 其实是"状态设计"难。**代码只是状态的翻译**, 状态对了, 代码就自然写出来了。下面是我总结的 4 种套路, 适用于 90% 的面试 DP 题。

## 套路 1: 线性 DP — 状态是"前 i 个位置的最优"

**特征**: 状态跟下标 `i` 绑定, 转移是 `dp[i] = f(dp[i-1], dp[i-2], ...)`

**经典题**:
- 70. 爬楼梯
- 198. 打家劫舍
- 53. 最大子数组和 (Kadane)

**模板**:
```python
# dp[i] = 前 i 个位置的最优解
dp = [0] * (n + 1)
dp[0] = base_case
dp[1] = base_case
for i in range(2, n + 1):
    dp[i] = max(dp[i-1], dp[i-2] + nums[i-2])  # 例: 打家劫舍
return dp[n]
```

## 套路 2: 区间 DP — 状态是"区间 [i, j] 的最优"

**特征**: 状态是二维, 且 `i ≤ j`, 转移时枚举分割点 `k ∈ [i, j-1]`。

**经典题**:
- 312. 戳气球
- 516. 最长回文子序列
- 1000. 合并石头的最低成本

**模板**:
```python
# dp[i][j] = 子区间 [i, j] 的最优解
# 枚举长度, 再枚举起点
for length in range(2, n + 1):
    for i in range(n - length + 1):
        j = i + length - 1
        for k in range(i, j):
            dp[i][j] = min(dp[i][j], dp[i][k] + dp[k+1][j] + cost(i, j, k))
```

## 套路 3: 状压 DP — 状态是"集合的二进制表示"

**特征**: 当 `n ≤ 20` 时, 集合可以用 `int` 的二进制位表示, 状态数 `2^n`, 时间 `O(n^2 * 2^n)`。

**经典题**:
- 464. 我能赢吗
- 847. 访问所有节点的最短路径
- 1434. 每个人戴不同帽子的方案数

**模板**:
```python
# dp[mask] = 已访问节点集合为 mask 时的最优解
dp = [float(''inf'')] * (1 << n)
dp[0] = 0
for mask in range(1 << n):
    last = bits(mask)  # 最后一个访问的节点
    for nxt in range(n):
        if mask & (1 << nxt) == 0:
            new_mask = mask | (1 << nxt)
            dp[new_mask] = min(dp[new_mask], dp[mask] + cost(last, nxt))
```

## 套路 4: 树形 DP — 状态是"以某节点为根的子树"

**特征**: 状态依附在树的节点上, 通常做一次 DFS 后序遍历, 自底向上更新。

**经典题**:
- 124. 二叉树中的最大路径和
- 337. 打家劫舍 III
- 543. 二叉树的直径

**模板**:
```python
def dfs(node):
    if not node: return 0
    left = dfs(node.left)
    right = dfs(node.right)
    # 假设状态是"经过 node 的最长路径"
    ans = max(ans, left + right + node.val)
    return max(left, right) + node.val
```

## 实战建议

1. **先识别套路**: 看题目问"最优"还是"方案数", 是 1D 还是 2D 状态。
2. **再写状态定义**: 用一句话说出 `dp[i]` 或 `dp[i][j]` 是什么。
3. **最后写转移**: 从状态定义推转移, 不要反过来。
4. **验证 base case**: `dp[0]`、`dp[1]` 手动算一遍, 再对拍小数据。

## 评论区想讨论的

- 你见过哪些"非典型" DP 题, 套这 4 个套路套不上的?
- 状态设计时, 你是先写状态还是先写转移?',
 NULL,
 JSON_OBJECT('related', JSON_ARRAY('fpost-001', 'fpost-004', 'fpost-002')),
 'neutral', 0, 1880, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 198, 'downvotes', 5, 'comments', 47, 'bookmarks', 96),
 1880, 0, NULL, NULL, 0, NULL, NULL),

-- 11. admin-002 (ADMIN) → fcomm-careers
('fpost-011', 'fcomm-careers', 'admin-002',
 'moderation-guidelines-career',
 '招聘区审核要点: 6 类常见违规模式 + 处置流程 (运营组整理)',
 'announcement', '公告',
 JSON_ARRAY('ftag-career'),
 '# 招聘区审核要点 (运营组整理)

> 适用对象: 所有版主、社区运营、内容审核志愿者
> 最后更新: 2026-06-01
> 阅读时间: 约 8 分钟

## 为什么需要这份文档

过去 3 个月, 招聘区日均新增 200+ 帖, 其中约 12% 存在不同程度的违规。靠"凭感觉审核"已经不够, 我们需要一份统一的判定标准, 既保护求职者, 也避免误伤正常帖。

## 6 类常见违规模式

### 1. 假 offer / 假内推 (高发)

**特征**:
- 内推邮箱是免费域名 (gmail / outlook / 163), 不是公司域名
- JD 内容空泛, 没有具体岗位描述
- "内推" 实为引流到私域 (微信 / 知识星球)

**处置**: 立即锁定, 账号观察 7 天, 累计 2 次永封。

### 2. 培训贷 / 套路贷 (严重)

**特征**:
- "0 基础入学 / 包就业 / 包 offer"
- "学完分期 / 先学后付 / 培训贷"
- "保 offer 不过退费" (任何形式的"保过")

**处置**: 永封发帖账号 + 关联手机号, 同步上报网信办。

### 3. 重复 / 灌水帖 (高发)

**特征**:
- 同一账号 7 天内同主题 ≥ 3 帖
- 同一 offer 信息改公司名反复发
- "求内推" 模板帖 (无任何具体信息)

**处置**: 第一次合并到上一帖, 第二次下沉, 第三次锁帖 7 天。

### 4. 隐私泄露 (严重)

**特征**:
- 帖中包含他人手机号、身份证号、邮箱、家庭住址
- 截图未打码, 含个人敏感信息

**处置**: 立即隐藏, 通知发帖人修改, 严重者直接删除并封号。

### 5. 歧视 / 侮辱性内容 (零容忍)

**特征**:
- 性别、年龄、学历、地域歧视
- 任何形式的侮辱性言论

**处置**: 立即删除 + 永封, 无申诉通道。

### 6. 商业广告 (灰色)

**特征**:
- "加我微信送 50G 面试资料"
- "扫码进群, 每日推送内推"
- 简历辅导、模拟面试等付费服务导流

**处置**: 第一次警告 + 移除广告内容, 第二次锁帖 7 天, 第三次永封。

## 处置流程

```text
发现违规 → 截图存档 → 判定违规类型 → 选择处置动作 → 站内信通知 → 申诉窗口 (24h)
```

**申诉**:
- 7 天内可申诉一次
- 申诉由 2 名以上版主共同判定
- 判定结果在 24h 内回复

## 工具

- 审核后台: `https://admin.ulticode.example.com/moderation/queue`
- 违规案例库 (内部): `https://internal.ulticode.example.com/wiki/moderation`
- 紧急联系 (24h): `urgent-mod@ulticode.example.com`

## 致谢

感谢过去 3 个月在审核岗位付出 200+ 小时的 8 位版主, 也感谢所有积极举报的社区成员。',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/1200x400/ef4444/ffffff?text=Moderation+Guidelines', 'alt', '审核要点 banner')
 ),
 NULL,
 'neutral', 0, 1100, 0, 0, NOW(3),
 JSON_OBJECT('upvotes', 88, 'downvotes', 1, 'comments', 23, 'bookmarks', 14),
 1100, 0, NULL, NULL, 0, NULL, NULL),

-- 12. admin (UUID 形式) → fcomm-general
('fpost-012', 'fcomm-general',
 (SELECT `id` FROM `users` WHERE `username` = 'admin' AND `role` = 'ADMIN' LIMIT 1),
 'welcome-to-ulticode-forum',
 '欢迎来到 UltiCode 论坛: 提问、分享、互帮互助',
 'announcement', '官方',
 JSON_ARRAY(),
 '# 欢迎来到 UltiCode 论坛 👋

## 这是什么地方

UltiCode 论坛是 UltiCode 用户的**非官方交流区**。我们鼓励的话题包括:
- 算法题解、思路分享、复杂度讨论
- 面试经验、内推、简历互评
- 平台 bug 反馈、功能建议
- 程序人生的吐槽、八卦、干货

## 不是什么地方

- 不是**树洞**: 个人情感问题请找专业咨询, 论坛不提供心理支持
- 不是**广告板**: 任何形式的引流都会被立刻清理 (详见版规)
- 不是**吵架平台**: 技术观点欢迎讨论, 人身攻击零容忍

## 怎么开始

1. **新用户**: 建议先逛逛 "综合交流" 和 "算法题解" 区, 看看大家在聊什么
2. **提问前**: 搜一下历史帖, 90% 的常见问题都有现成答案
3. **写新帖**: 标题写清楚, 正文带上下文 (你尝试过什么、卡在哪里), 详见 [提问的智慧]
4. **回答问题**: 即使是新手也能帮忙, 你的"我也遇到过"就是最好的鼓励

## 核心原则

> **Be kind. Be helpful. Be honest.**

我们相信:
- 善意优先: 默认对方是善意的, 哪怕表达不够好
- 帮助他人: 回答别人问题的过程, 也是自己成长的过程
- 诚实表达: 不懂就说不懂, 不要装懂误导人

## 版主团队

- @mike_mod (社区运营)
- @nina_mod (求职招聘)
- @super_root (平台方向)
- @super_vp (技术方向)

任何问题, 站内信 @ 任意版主即可。

## 致谢

感谢所有为社区贡献内容的用户, 你们让这个社区变得更好。

最后, 欢迎大家提建议, 我们会持续改进。

— UltiCode 团队',
 JSON_ARRAY(
   JSON_OBJECT('type', 'image', 'url', 'https://placehold.co/1200x500/6366f1/ffffff?text=Welcome+to+UltiCode+Forum', 'alt', '欢迎 banner')
 ),
 JSON_OBJECT('related', JSON_ARRAY('fpost-007', 'fpost-009', 'fpost-011')),
 'neutral', 0, 8800, 1, 0, NOW(3),
 JSON_OBJECT('upvotes', 720, 'downvotes', 12, 'comments', 256, 'bookmarks', 540),
 8800, 0, NULL, NULL, 0, NULL, NULL);

-- ===== 4. 反向回填 community.posts_count =====
UPDATE `forum_communities` c
SET `posts_count` = (
    SELECT COUNT(*) FROM `forum_posts` p
    WHERE p.`community_id` = c.`id` AND p.`is_deleted` = 0
)
WHERE c.`id` IN ('fcomm-general', 'fcomm-algorithms', 'fcomm-careers');

-- ===== 5. 反向回填 tag.usage_count =====
UPDATE `forum_tags` t
SET `usage_count` = (
    SELECT COUNT(*) FROM `forum_posts` p
    WHERE JSON_CONTAINS(p.`tags`, JSON_QUOTE(t.`id`)) AND p.`is_deleted` = 0
)
WHERE t.`id` IN ('ftag-hash', 'ftag-dp', 'ftag-ll', 'ftag-sql', 'ftag-interview', 'ftag-career');

-- Verify:
--   SELECT user_id, COUNT(*) AS cnt FROM forum_posts GROUP BY user_id HAVING cnt = 0;     -- 期望: 空
--   SELECT (SELECT COUNT(*) FROM users WHERE is_deleted=0) AS users,
--          (SELECT COUNT(DISTINCT user_id) FROM forum_posts) AS users_with_post;           -- 期望: 相等
--   SELECT id, posts_count FROM forum_communities WHERE id LIKE 'fcomm-%';
--   SELECT id, usage_count FROM forum_tags WHERE id LIKE 'ftag-%';
--   SELECT COUNT(*), SUM(is_deleted), SUM(is_flagged), SUM(is_pinned) FROM forum_posts;   -- 期望: 12/0/0/2
--   SELECT id, LENGTH(excerpt) AS body_chars FROM forum_posts ORDER BY body_chars;          -- 期望: 全部 > 500
