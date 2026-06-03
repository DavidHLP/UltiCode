-- Seed Test Data: Solutions
-- ------------------------------------------------------------
-- 拆分自 V20260602_120200__Insert_Test_Data.sql (Section: Solutions)
-- 维护指南: 修改 / 扩展题解测试数据, 仅编辑本文件
--
-- 依赖: 必须先执行 V20260603_120300__Seed_Users_And_Permissions (user_id 引用)
-- 设计:  12 个用户每个 1 个题解, 分布到 6 道题 (每题 2 篇)
--        11 个用户使用固定 ID, admin (UUID) 通过子查询按 username 解析
-- 状态:  全部已发布 (is_published=1), 未标记, 未删除, 未置顶 (干净状态)
-- ------------------------------------------------------------

-- ===== 清理旧 V120400 残留数据 (id 前缀 sol-001 ~ sol-015) =====
DELETE FROM `solutions`
WHERE `id` IN (
    'sol-001', 'sol-002', 'sol-003', 'sol-004', 'sol-005',
    'sol-006', 'sol-007', 'sol-008', 'sol-009', 'sol-010',
    'sol-011', 'sol-012', 'sol-013', 'sol-014', 'sol-015'
);

-- ===== 12 个题解: 1 user = 1 solution =====
INSERT IGNORE INTO `solutions` (
    `id`, `problem_id`, `user_id`, `title`, `content`, `summary`, `language`, `tags`,
    `views`, `likes`, `dislikes`, `comment_count`,
    `is_published`, `published_at`, `published_by`,
    `is_flagged`, `flagged_reason`, `flagged_at`,
    `is_deleted`, `is_pinned`,
    `created_at`, `updated_at`
) VALUES

-- 1. user-alice-001 (USER) → problem 1 (两数之和)
('sol-s-001', 1, 'user-alice-001',
 '两数之和 哈希表最优解',
 '## 思路\n\n遍历数组的同时,用哈希表记录 `value → index` 的映射。对于每个 `nums[i]`,查找 `target - nums[i]` 是否已经出现过。\n\n## 代码\n\n```python\nclass Solution:\n    def twoSum(self, nums, target):\n        seen = {}\n        for i, n in enumerate(nums):\n            if target - n in seen:\n                return [seen[target - n], i]\n            seen[n] = i\n        return []\n```\n\n## 复杂度\n- 时间 O(n), 哈希表查找均摊 O(1)\n- 空间 O(n)',
 'Python 哈希表一次遍历解法, 时间复杂度 O(n)',
 'python', '["哈希表", "数组"]',
 1250, 45, 2, 3, 1, NOW(3), 'user-alice-001',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 2. user-bob-002 (USER) → problem 6 (反转链表)
('sol-s-002', 6, 'user-bob-002',
 '反转链表 三指针迭代法',
 '## 思路\n\n用 prev / curr / next 三个指针原地反转, 避免额外空间。\n\n## 代码\n\n```java\nclass Solution {\n    public ListNode reverseList(ListNode head) {\n        ListNode prev = null, curr = head;\n        while (curr != null) {\n            ListNode next = curr.next;\n            curr.next = prev;\n            prev = curr;\n            curr = next;\n        }\n        return prev;\n    }\n}\n```\n\n## 复杂度\n- 时间 O(n)\n- 空间 O(1)',
 'Java 迭代解法, 三指针原地反转',
 'java', '["链表", "迭代"]',
 890, 32, 1, 2, 1, NOW(3), 'user-bob-002',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 3. user-carol-003 (USER) → problem 2 (两数相加)
('sol-s-003', 2, 'user-carol-003',
 '两数相加 进位处理详解',
 '## 思路\n\n同时遍历两条链表, 维护 `carry` 进位变量。每轮取两节点值 (缺失补 0) 加进位, 当前位 = 和 % 10, 新进位 = 和 / 10。\n\n## 代码\n\n```python\nclass Solution:\n    def addTwoNumbers(self, l1, l2):\n        dummy = ListNode(0)\n        cur, carry = dummy, 0\n        while l1 or l2 or carry:\n            v1 = l1.val if l1 else 0\n            v2 = l2.val if l2 else 0\n            s = v1 + v2 + carry\n            cur.next = ListNode(s % 10)\n            carry = s // 10\n            cur = cur.next\n            l1 = l1.next if l1 else None\n            l2 = l2.next if l2 else None\n        return dummy.next\n```\n\n## 关键点\n- 循环条件包含 `carry`, 防止最高位进位丢失\n- 哑节点 dummy 简化头节点处理',
 '链表加法 Python 详解, 含进位与哑节点技巧',
 'python', '["链表", "数学"]',
 1100, 38, 3, 5, 1, NOW(3), 'user-carol-003',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 4. user-david-004 (USER) → problem 3 (无重复字符的最长子串)
('sol-s-004', 3, 'user-david-004',
 '滑动窗口巧解最长无重复子串',
 '## 思路\n\n用左闭右开区间 `[left, right)` 作为窗口, 哈希表记录窗口内字符。右指针每次右移一格, 若字符已在窗口内则收缩左边界。\n\n## 代码\n\n```javascript\nfunction lengthOfLongestSubstring(s) {\n    const idx = new Map();\n    let left = 0, ans = 0;\n    for (let right = 0; right < s.length; right++) {\n        const c = s[right];\n        if (idx.has(c) && idx.get(c) >= left) {\n            left = idx.get(c) + 1;\n        }\n        idx.set(c, right);\n        ans = Math.max(ans, right - left + 1);\n    }\n    return ans;\n}\n```\n\n## 复杂度\n- 时间 O(n)\n- 空间 O(字符集大小)',
 'JavaScript 滑动窗口 + 哈希表, 时间 O(n)',
 'javascript', '["滑动窗口", "哈希表", "字符串"]',
 760, 28, 0, 1, 1, NOW(3), 'user-david-004',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 5. user-eva-005 (USER) → problem 4 (寻找两个正序数组的中位数)
('sol-s-005', 4, 'user-eva-005',
 '二分查找求两正序数组中位数',
 '## 思路\n\n对较短的数组 `nums1` 二分, 寻找分割点 `i`, 使得左侧元素数量满足 `(m+n+1)/2`。中位数由分割线两侧的最大/最小值决定。\n\n## 代码\n\n```cpp\nclass Solution {\npublic:\n    double findMedianSortedArrays(vector<int>& a, vector<int>& b) {\n        if (a.size() > b.size()) return findMedianSortedArrays(b, a);\n        int m = a.size(), n = b.size();\n        int lo = 0, hi = m;\n        while (lo <= hi) {\n            int i = (lo + hi) / 2;\n            int j = (m + n + 1) / 2 - i;\n            int aLeft  = (i == 0) ? INT_MIN : a[i-1];\n            int aRight = (i == m) ? INT_MAX : a[i];\n            int bLeft  = (j == 0) ? INT_MIN : b[j-1];\n            int bRight = (j == n) ? INT_MAX : b[j];\n            if (aLeft <= bRight && bLeft <= aRight) {\n                if ((m + n) % 2 == 0)\n                    return (max(aLeft, bLeft) + min(aRight, bRight)) / 2.0;\n                return max(aLeft, bLeft);\n            } else if (aLeft > bRight) {\n                hi = i - 1;\n            } else {\n                lo = i + 1;\n            }\n        }\n        return 0.0;\n    }\n};\n```\n\n## 复杂度\n- 时间 O(log min(m, n))\n- 空间 O(1)',
 'C++ 二分查找, 时间复杂度 O(log(min(m,n)))',
 'cpp', '["二分查找", "分治", "数组"]',
 420, 15, 1, 2, 1, NOW(3), 'user-eva-005',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 6. user-frank-006 (USER) → problem 7 (合并K个升序链表)
('sol-s-006', 7, 'user-frank-006',
 '最小堆合并K个升序链表',
 '## 思路\n\n把每个链表的头节点入最小堆, 每次弹出最小节点接到结果链表, 并把该节点的 next 压入堆。\n\n## 代码\n\n```cpp\nclass Solution {\npublic:\n    ListNode* mergeKLists(vector<ListNode*>& lists) {\n        auto cmp = [](ListNode* a, ListNode* b) { return a->val > b->val; };\n        priority_queue<ListNode*, vector<ListNode*>, decltype(cmp)> pq(cmp);\n        for (auto h : lists) if (h) pq.push(h);\n        ListNode dummy(0), *cur = &dummy;\n        while (!pq.empty()) {\n            auto n = pq.top(); pq.pop();\n            cur->next = n;\n            cur = cur->next;\n            if (n->next) pq.push(n->next);\n        }\n        return dummy.next;\n    }\n};\n```\n\n## 复杂度\n- 时间 O(N log k), N 为总节点数\n- 空间 O(k)',
 'C++ 优先队列 (最小堆) 实现, 时间 O(N log k)',
 'cpp', '["链表", "堆", "分治"]',
 350, 12, 0, 1, 1, NOW(3), 'user-frank-006',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 7. mod-mike-001 (MODERATOR) → problem 1 (两数之和)
('sol-s-007', 1, 'mod-mike-001',
 '社区版主视角:两数之和的多种解法对比',
 '## 三种主流解法\n\n1. **暴力枚举** — O(n²) 时间, O(1) 空间, 简单但慢\n2. **哈希表** — O(n) 时间, O(n) 空间, 一次遍历, 推荐\n3. **排序 + 双指针** — O(n log n) 时间, 需返回原下标时不可用\n\n## 推荐写法\n\n哈希表一遍遍历, 边存边查, 是面试最优解。\n\n```python\ndef two_sum(nums, target):\n    seen = {}\n    for i, n in enumerate(nums):\n        if target - n in seen:\n            return [seen[target - n], i]\n        seen[n] = i\n```\n\n## 社区建议\n\n新手先理解暴力解法, 再过渡到哈希表; 面试时务必主动说明时空复杂度权衡。',
 '版主总结的三种解法对比与社区建议',
 'python', '["哈希表", "数组", "双指针"]',
 680, 22, 1, 4, 1, NOW(3), 'mod-mike-001',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 8. mod-nina-002 (MODERATOR) → problem 6 (反转链表)
('sol-s-008', 6, 'mod-nina-002',
 '反转链表 递归与迭代双解',
 '## 迭代法 (推荐)\n\n三指针 prev/curr/next, 时间 O(n), 空间 O(1)。\n\n```java\npublic ListNode reverseList(ListNode head) {\n    ListNode prev = null, curr = head;\n    while (curr != null) {\n        ListNode next = curr.next;\n        curr.next = prev;\n        prev = curr;\n        curr = next;\n    }\n    return prev;\n}\n```\n\n## 递归法 (优雅但有栈开销)\n\n```java\npublic ListNode reverseList(ListNode head) {\n    if (head == null || head.next == null) return head;\n    ListNode newHead = reverseList(head.next);\n    head.next.next = head;\n    head.next = null;\n    return newHead;\n}\n```\n\n## 何时选哪个\n\n- 链表长 (n > 10⁴) 优先迭代, 避免栈溢出\n- 短链表或教学场景, 递归更清晰',
 'Java 双解对比: 迭代 vs 递归',
 'java', '["链表", "递归", "迭代"]',
 540, 18, 0, 2, 1, NOW(3), 'mod-nina-002',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 9. admin-002 (ADMIN) → problem 3 (无重复字符的最长子串)
('sol-s-009', 3, 'admin-002',
 '滑动窗口复杂度深度分析',
 '## 算法核心\n\n滑动窗口本质是双指针的特例: 右指针主动扩张, 左指针被动收缩。维护一个"合法窗口"的不变量。\n\n## 三种实现\n\n| 实现 | 时间 | 空间 | 适用 |\n|------|------|------|------|\n| 哈希集合 + while 收缩 | O(2n) | O(字符集) | 通用 |\n| 哈希表记录下标 | O(n) | O(字符集) | 字符串 |\n| 数组记录下标 | O(n) | O(128) | ASCII |\n\n## 选型建议\n\n- 字符集有限 (ASCII) → 用数组最快\n- 字符集大 (Unicode) → 哈希表更灵活\n- 教学/竞赛 → 哈希集合最易理解\n\n## 工程注意\n\n面试时常被追问 "如果字符集是整个 Unicode 怎么办", 答: 哈希表 + while 收缩, 整体仍 O(n)。',
 '滑动窗口三种实现对比与工程选型',
 'python', '["滑动窗口", "哈希表", "字符串"]',
 480, 16, 0, 3, 1, NOW(3), 'admin-002',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 10. super-root-001 (SUPER_ADMIN) → problem 2 (两数相加)
('sol-s-010', 2, 'super-root-001',
 '链表加法的高性能 Go 实现',
 '## 实现\n\n```go\nfunc addTwoNumbers(l1, l2 *ListNode) *ListNode {\n    head := &ListNode{}\n    cur, carry := head, 0\n    for l1 != nil || l2 != nil || carry != 0 {\n        v := carry\n        if l1 != nil { v += l1.Val; l1 = l1.Next }\n        if l2 != nil { v += l2.Val; l2 = l2.Next }\n        cur.Next = &ListNode{Val: v % 10}\n        cur = cur.Next\n        carry = v / 10\n    }\n    return head.Next\n}\n```\n\n## 性能要点\n\n- 单次遍历, 无额外分配 (除结果节点)\n- Go 的零值和指针语义让代码更紧凑\n- `carry != 0` 必须出现在循环条件, 否则最高位进位丢失',
 'Go 链表加法, 单次遍历无额外开销',
 'go', '["链表", "数学"]',
 720, 26, 1, 4, 1, NOW(3), 'super-root-001',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 11. super-vp-002 (SUPER_ADMIN) → problem 7 (合并K个升序链表)
('sol-s-011', 7, 'super-vp-002',
 '分治法合并K个升序链表',
 '## 思路\n\n借鉴归并排序的两两合并策略: 递归地将链表数组折半, 直到子数组长度 ≤ 1, 再两两合并。\n\n## 代码\n\n```java\nclass Solution {\n    public ListNode mergeKLists(ListNode[] lists) {\n        if (lists.length == 0) return null;\n        return merge(lists, 0, lists.length - 1);\n    }\n    private ListNode merge(ListNode[] lists, int lo, int hi) {\n        if (lo == hi) return lists[lo];\n        int mid = lo + (hi - lo) / 2;\n        return mergeTwo(merge(lists, lo, mid), merge(lists, mid + 1, hi));\n    }\n    private ListNode mergeTwo(ListNode a, ListNode b) {\n        ListNode dummy = new ListNode(0), cur = dummy;\n        while (a != null && b != null) {\n            if (a.val < b.val) { cur.next = a; a = a.next; }\n            else { cur.next = b; b = b.next; }\n            cur = cur.next;\n        }\n        cur.next = (a != null) ? a : b;\n        return dummy.next;\n    }\n}\n```\n\n## 复杂度\n- 时间 O(N log k)\n- 空间 O(log k) 递归栈',
 'Java 分治递归, 借鉴归并排序',
 'java', '["链表", "分治", "归并"]',
 380, 14, 0, 2, 1, NOW(3), 'super-vp-002',
 0, NULL, NULL, 0, 0, NOW(3), NOW(3)),

-- 12. admin (V20260602_120100, UUID 形式) → problem 4 (中位数)
('sol-s-012', 4, (SELECT `id` FROM `users` WHERE `username` = 'admin' AND `role` = 'ADMIN' LIMIT 1),
 '中位数问题的工程化思考',
 '## 思路\n\n二分查找找分割点是经典解法, 但工程上常被替换为: 直接合并 + 排序 + 取中位数, 简单稳健。\n\n## 工程权衡\n\n| 维度 | 二分查找 | 合并排序 |\n|------|----------|----------|\n| 时间 | O(log(m+n)) | O((m+n) log(m+n)) |\n| 空间 | O(1) | O(m+n) |\n| 代码复杂度 | 高 | 低 |\n| 维护成本 | 高 | 低 |\n\n## 选型\n\n- 在线面试 / 算法竞赛 → 二分\n- 后端服务 / 业务代码 → 合并, 易读易维护, 性能差异在大数据下才显著',
 '工程视角的中位数解法选型与权衡',
 'python', '["二分查找", "分治", "数组"]',
 290, 9, 0, 1, 1, NOW(3), (SELECT `id` FROM `users` WHERE `username` = 'admin' AND `role` = 'ADMIN' LIMIT 1),
 0, NULL, NULL, 0, 0, NOW(3), NOW(3));

-- Verify:
--   SELECT COUNT(*) FROM solutions;                  -- 期望: 12
--   SELECT user_id, COUNT(*) FROM solutions GROUP BY user_id HAVING COUNT(*) > 1;  -- 期望: 空
--   SELECT problem_id, COUNT(*) FROM solutions GROUP BY problem_id;  -- 期望: 每题 2
--   SELECT is_published, is_flagged, is_deleted, is_pinned, COUNT(*) FROM solutions GROUP BY 1,2,3,4;
--   期望: 仅 1 行, is_published=1, is_flagged=0, is_deleted=0, is_pinned=0, COUNT=12
