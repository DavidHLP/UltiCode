-- ============================================================
-- V20260602020000__Seed_Submissions_Test_Data.sql
-- 为 submissions 管理页面设计合理的测试数据
-- 覆盖：多种状态、多语言、多用户、不同时间分布
-- ============================================================

-- 确保测试用户存在（复用 solutions 种子数据中的用户）
INSERT IGNORE INTO users (id, username, name, email, avatar, password, bio, role, is_active, is_banned, is_deleted) VALUES
('user-alice-001', 'alice_coder', 'Alice Johnson', 'alice@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=alice', '$2a$10$dummy', '算法爱好者，专注动态规划', 'USER', 1, 0, 0),
('user-bob-002', 'bob_dev', 'Bob Smith', 'bob@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=bob', '$2a$10$dummy', 'Java / Python 双修，热爱系统设计', 'USER', 1, 0, 0),
('user-carol-003', 'carol_wu', 'Carol Wu', 'carol@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=carol', '$2a$10$dummy', '竞赛党，ACMer', 'USER', 1, 0, 0),
('user-david-004', 'david_chen', 'David Chen', 'david@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=david', '$2a$10$dummy', '前端转全栈，喜欢写题解', 'USER', 1, 0, 0),
('user-eva-005', 'eva_zhang', 'Eva Zhang', 'eva@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=eva', '$2a$10$dummy', 'Python 达人，AI 初学者', 'USER', 1, 0, 0),
('user-frank-006', 'frank_lee', 'Frank Lee', 'frank@example.com', 'https://api.dicebear.com/7.x/avataaars/svg?seed=frank', '$2a$10$dummy', 'C++ 手写高性能代码', 'USER', 1, 0, 0);

-- ============================================================
-- Accepted 提交（最优解，各种语言）
-- ============================================================
INSERT IGNORE INTO submissions (id, problem_id, user_id, language, code, status, runtime, memory, notes, created_at, runtime_percentile, memory_percentile, test_details, memoryDistBinsMb, runtimeDistBinsMs, retry_count) VALUES

-- Alice: Python 哈希表，最快解法
('sub-001', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            return [seen[complement], i]\n        seen[num] = i\n    return []',
'Accepted', 36, 16.2, '哈希表 O(n) 解法',
DATE_SUB(NOW(), INTERVAL 7 DAY), 95.5, 88.3,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 2, 'memory', 15.1), JSON_OBJECT('status', 'AC', 'time', 3, 'memory', 15.8)),
JSON_ARRAY(0,2,5,8,12,16,20,25,30,35),
JSON_ARRAY(0,10,20,30,40,50,80,100,150,200),
0),

-- Alice: 第二次提交，优化版
('sub-002', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    hashmap = {}\n    for i, n in enumerate(nums):\n        diff = target - n\n        if diff in hashmap:\n            return [hashmap[diff], i]\n        hashmap[n] = i',
'Accepted', 32, 15.8, NULL,
DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 10 MINUTE, 97.2, 91.0,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 14.8)),
JSON_ARRAY(0,1,4,7,11,15,19,24,29,34),
JSON_ARRAY(0,8,16,24,32,40,60,80,120,180),
0),

-- Bob: Java 哈希表
('sub-003', 1, 'user-bob-002', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[]{map.get(complement), i};\n            }\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Accepted', 2, 42.5, 'Java HashMap 解法',
DATE_SUB(NOW(), INTERVAL 5 DAY), 92.1, 75.6,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 1, 'memory', 40.2)),
JSON_ARRAY(0,3,6,10,15,20,28,35,42,50),
JSON_ARRAY(0,1,2,3,4,5,8,10,15,20),
0),

-- Carol: C++ 哈希表（竞赛风格）
('sub-004', 1, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> mp;\n        for (int i = 0; i < nums.size(); ++i) {\n            if (mp.count(target - nums[i]))\n                return {mp[target - nums[i]], i};\n            mp[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Accepted', 0, 12.4, 'C++ 竞赛写法，0ms',
DATE_SUB(NOW(), INTERVAL 3 DAY), 99.8, 96.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 0, 'memory', 11.5)),
JSON_ARRAY(0,1,3,5,8,10,12,14,16,20),
JSON_ARRAY(0,0,1,1,2,2,3,4,5,8),
0),

-- Frank: C++ 暴力（首次尝试）
('sub-005', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        for (int i = 0; i < nums.size(); ++i)\n            for (int j = i + 1; j < nums.size(); ++j)\n                if (nums[i] + nums[j] == target)\n                    return {i, j};\n        return {};\n    }\n};',
'Accepted', 280, 10.8, '暴力 O(n^2)，勉强通过',
DATE_SUB(NOW(), INTERVAL 6 DAY), 12.3, 98.1,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 275, 'memory', 10.0)),
JSON_ARRAY(0,1,2,4,6,8,9,10,11,12),
JSON_ARRAY(0,50,100,150,200,250,280,300,350,400),
0),

-- David: JavaScript 哈希表
('sub-006', 1, 'user-david-004', 'javascript',
'var twoSum = function(nums, target) {\n    const map = new Map();\n    for (let i = 0; i < nums.length; i++) {\n        const complement = target - nums[i];\n        if (map.has(complement)) {\n            return [map.get(complement), i];\n        }\n        map.set(nums[i], i);\n    }\n    return [];\n};',
'Accepted', 56, 48.2, NULL,
DATE_SUB(NOW(), INTERVAL 2 DAY), 85.4, 62.1,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 50, 'memory', 46.0)),
JSON_ARRAY(0,4,8,12,16,20,28,36,44,52),
JSON_ARRAY(0,10,20,30,40,50,60,80,100,140),
0),

-- Eva: Python 一次通过
('sub-007', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    d = {}\n    for i, v in enumerate(nums):\n        if target - v in d:\n            return [d[target - v], i]\n        d[v] = i',
'Accepted', 40, 16.0, '简洁写法',
DATE_SUB(NOW(), INTERVAL 1 DAY), 93.0, 89.5,
JSON_ARRAY(JSON_OBJECT('status', 'AC', 'time', 35, 'memory', 15.2)),
JSON_ARRAY(0,2,4,7,10,14,17,19,21,23),
JSON_ARRAY(0,8,16,24,32,40,55,70,100,150),
0),

-- ============================================================
-- Wrong Answer 提交
-- ============================================================

-- Eva: 边界条件错误
('sub-008', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]\n    return [-1, -1]',
'Wrong Answer', 320, 14.5, '返回值格式错误，应为 [] 而非 [-1,-1]',
DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 48 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 48, 'memory', 15.0)),
NULL, NULL, 0),

-- David: 排序后索引丢失
('sub-009', 1, 'user-david-004', 'python3',
'def two_sum(nums, target):\n    nums_sorted = sorted(nums)\n    left, right = 0, len(nums_sorted) - 1\n    while left < right:\n        s = nums_sorted[left] + nums_sorted[right]\n        if s == target:\n            return [left, right]\n        elif s < target:\n            left += 1\n        else:\n            right -= 1',
'Wrong Answer', 45, 15.2, '排序后索引丢失，未映射回原数组',
DATE_SUB(NOW(), INTERVAL 6 DAY) - INTERVAL 2 HOUR, NULL, NULL,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 35 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 35, 'memory', 15.0)),
NULL, NULL, 0),

-- Bob: 返回值问题
('sub-010', 1, 'user-bob-002', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        int[] result = new int[2];\n        for (int i = 0; i < nums.length; i++) {\n            for (int j = 0; j < nums.length; j++) {\n                if (nums[i] + nums[j] == target && i != j) {\n                    result[0] = i;\n                    result[1] = j;\n                    return result;\n                }\n            }\n        }\n        return result;\n    }\n}',
'Wrong Answer', 380, 45.1, NULL,
DATE_SUB(NOW(), INTERVAL 5 DAY) - INTERVAL 1 HOUR, NULL, NULL,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 49 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 49, 'memory', 15.0)),
NULL, NULL, 0),

-- ============================================================
-- Time Limit Exceeded 提交
-- ============================================================

-- Eva: O(n^2) 超时
('sub-011', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]',
'Time Limit Exceeded', 2000, 14.0, '暴力 O(n^2) 超时',
DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, NULL,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 30 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 30, 'memory', 15.0)),
NULL, NULL, 0),

-- Frank: C++ 嵌套循环超时
('sub-012', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        int n = nums.size();\n        for (int i = 0; i < n; i++)\n            for (int j = i + 1; j < n; j++)\n                if (nums[i] + nums[j] == target)\n                    return {i, j};\n        return {};\n    }\n};',
'Time Limit Exceeded', 1500, 8.5, 'C++ 暴力在大数据集超时',
DATE_SUB(NOW(), INTERVAL 6 DAY) - INTERVAL 30 MINUTE, NULL, NULL,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 40 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 40, 'memory', 15.0)),
NULL, NULL, 0),

-- ============================================================
-- Runtime Error 提交
-- ============================================================

-- David: 空指针异常
('sub-013', 1, 'user-david-004', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = null;\n        for (int i = 0; i < nums.length; i++) {\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Runtime Error', 0, 0.0, 'NullPointerException - map 未初始化',
DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 0 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 0, 'memory', 15.0)),
NULL, NULL, 0),

-- Eva: 数组越界
('sub-014', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    n = len(nums)\n    for i in range(n):\n        complement = target - nums[i]\n        for j in range(i + 1, n + 1):\n            if nums[j] == complement:\n                return [i, j]',
'Runtime Error', 5, 13.0, 'IndexError - range 越界',
DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 5 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 5, 'memory', 15.0)),
NULL, NULL, 0),

-- ============================================================
-- Compile Error 提交
-- ============================================================

-- Bob: 语法错误
('sub-015', 1, 'user-bob-002', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>()\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[]{map.get(complement), i};\n            }\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Compile Error', 0, 0.0, '缺少分号',
DATE_SUB(NOW(), INTERVAL 5 DAY) + INTERVAL 30 MINUTE, NULL, NULL, NULL, NULL, NULL, 0),

-- Carol: C++ 头文件遗漏
('sub-016', 1, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> mp;\n        for (int i = 0; i < nums.size(); ++i) {\n            if (mp.count(target - nums[i]))\n                return {mp[target - nums[i]], i};\n            mp[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Compile Error', 0, 0.0, '未包含 vector 和 unordered_map 头文件',
DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 15 MINUTE, NULL, NULL, NULL, NULL, NULL, 0),

-- ============================================================
-- Memory Limit Exceeded 提交
-- ============================================================

-- Frank: 大数组复制
('sub-017', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        vector<int> copy = nums;\n        map<int, vector<int>> mp;\n        for (int i = 0; i < nums.size(); ++i)\n            mp[nums[i]].push_back(i);\n        for (int i = 0; i < nums.size(); ++i) {\n            int comp = target - nums[i];\n            if (mp.count(comp)) {\n                for (int j : mp[comp])\n                    if (j != i) return {i, j};\n            }\n        }\n        return {};\n    }\n};',
'Memory Limit Exceeded', 180, 512.0, '不必要的数组复制导致内存超限',
DATE_SUB(NOW(), INTERVAL 4 DAY) - INTERVAL 2 HOUR, NULL, NULL, NULL, NULL, NULL, 0),

-- ============================================================
-- Pending / Judging 提交（当前队列中）
-- ============================================================

-- Eva: 刚提交，等待中
('sub-018', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    for i, n in enumerate(nums):\n        complement = target - n\n        for j in range(i+1, len(nums)):\n            if nums[j] == complement:\n                return [i, j]',
'Pending', 0, 0.0, NULL,
DATE_SUB(NOW(), INTERVAL 2 MINUTE), NULL, NULL, NULL, NULL, NULL, 0),

-- Frank: 正在评测
('sub-019', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int,int> m;\n        for(int i=0;i<nums.size();i++){\n            if(m.count(target-nums[i])) return {m[target-nums[i]],i};\n            m[nums[i]]=i;\n        }\n        return {};\n    }\n};',
'Judging', 0, 0.0, NULL,
DATE_SUB(NOW(), INTERVAL 1 MINUTE), NULL, NULL, NULL, NULL, NULL, 0),

-- ============================================================
-- Presentation Error 提交
-- ============================================================

-- David: 输出格式不对
('sub-020', 1, 'user-david-004', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            print(seen[complement], i)\n            return [seen[complement], i]\n        seen[num] = i',
'Presentation Error', 38, 15.5, '多余的 print 输出导致格式错误',
DATE_SUB(NOW(), INTERVAL 2 DAY) - INTERVAL 3 HOUR, NULL, NULL, NULL, NULL, NULL, 0),

-- ============================================================
-- Output Limit Exceeded 提交
-- ============================================================

-- Eva: 死循环输出
('sub-021', 1, 'user-eva-005', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            while True:\n                print(i, seen[complement])\n        seen[num] = i',
'Output Limit Exceeded', 150, 25.0, '死循环导致输出超限',
DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 2 HOUR, NULL, NULL, NULL, NULL, NULL, 0),

-- ============================================================
-- System Error 提交
-- ============================================================

('sub-022', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    return [0, 1]',
'System Error', 0, 0.0, '评测机异常，需重新提交',
DATE_SUB(NOW(), INTERVAL 4 DAY) + INTERVAL 5 HOUR, NULL, NULL, NULL, NULL, NULL, 1),

-- ============================================================
-- 重新评测后的提交（retry_count > 0）
-- ============================================================

('sub-023', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    seen = {}\n    for i, num in enumerate(nums):\n        complement = target - num\n        if complement in seen:\n            return [seen[complement], i]\n        seen[num] = i\n    return []',
'Accepted', 34, 16.0, '重新评测通过',
DATE_SUB(NOW(), INTERVAL 5 DAY), 96.0, 89.0,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,2,4,7,10,14,17,19,21,24),
JSON_ARRAY(0,8,16,24,32,40,55,70,100,150),
2),

-- ============================================================
-- 更多 Accepted 提交（丰富统计数据）
-- ============================================================

-- Alice: Java 解法
('sub-024', 1, 'user-alice-001', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Map<Integer, Integer> map = new HashMap<>();\n        for (int i = 0; i < nums.length; i++) {\n            int complement = target - nums[i];\n            if (map.containsKey(complement)) {\n                return new int[]{map.get(complement), i};\n            }\n            map.put(nums[i], i);\n        }\n        return new int[]{};\n    }\n}',
'Accepted', 3, 44.0, 'Java 版本',
DATE_SUB(NOW(), INTERVAL 6 DAY) + INTERVAL 2 HOUR, 91.0, 73.0,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,3,6,10,15,20,28,35,42,50),
JSON_ARRAY(0,1,2,3,4,5,8,10,15,20),
0),

-- Bob: Python 解法
('sub-025', 1, 'user-bob-002', 'python3',
'def two_sum(nums, target):\n    lookup = {}\n    for i, num in enumerate(nums):\n        if target - num in lookup:\n            return [lookup[target - num], i]\n        lookup[num] = i\n    return []',
'Accepted', 38, 16.5, 'Python 版本',
DATE_SUB(NOW(), INTERVAL 4 DAY) + INTERVAL 1 HOUR, 94.0, 87.0,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,2,5,8,12,16,20,25,30,35),
JSON_ARRAY(0,8,16,24,32,40,55,70,100,150),
0),

-- Carol: Python 优雅写法
('sub-026', 1, 'user-carol-003', 'python3',
'def two_sum(nums, target):\n    d = {}\n    for i, v in enumerate(nums):\n        j = d.get(target - v)\n        if j is not None:\n            return [j, i]\n        d[v] = i',
'Accepted', 35, 15.9, '使用 walrus 风格',
DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 4 HOUR, 96.5, 90.2,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,1,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,7,14,21,28,35,50,65,90,130),
0),

-- David: Python 解法
('sub-027', 1, 'user-david-004', 'python3',
'def two_sum(nums, target):\n    h = {}\n    for i in range(len(nums)):\n        n = nums[i]\n        m = target - n\n        if m in h:\n            return [h[m], i]\n        h[n] = i',
'Accepted', 42, 16.1, NULL,
DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 3 HOUR, 92.0, 88.0,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,2,4,7,10,14,17,20,23,26),
JSON_ARRAY(0,8,16,24,32,42,55,70,100,150),
0),

-- Frank: 最终 Accepted
('sub-028', 1, 'user-frank-006', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> seen;\n        for (int i = 0; i < nums.size(); ++i) {\n            int comp = target - nums[i];\n            if (seen.find(comp) != seen.end())\n                return {seen[comp], i};\n            seen[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Accepted', 4, 13.2, '从暴力到哈希表的进化',
DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 6 HOUR, 98.5, 95.0,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,1,3,5,8,10,12,14,16,20),
JSON_ARRAY(0,1,2,3,4,5,6,7,8,10),
0),

-- Admin: 管理员测试提交
('sub-029', 1, 'u-admin-001', 'python3',
'def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]',
'Accepted', 285, 14.0, '管理员测试',
DATE_SUB(NOW(), INTERVAL 10 DAY), 10.0, 97.0,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,1,2,4,6,8,9,10,11,12),
JSON_ARRAY(0,50,100,150,200,250,280,300,350,400),
0),

-- Admin: C++ 测试
('sub-030', 1, 'u-admin-001', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        unordered_map<int, int> m;\n        for (int i = 0; i < nums.size(); i++) {\n            if (m.count(target - nums[i]))\n                return {m[target - nums[i]], i};\n            m[nums[i]] = i;\n        }\n        return {};\n    }\n};',
'Accepted', 3, 11.5, '管理员 C++ 测试',
DATE_SUB(NOW(), INTERVAL 10 DAY) + INTERVAL 20 MINUTE, 98.0, 97.5,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,1,2,4,6,8,10,11,12,13),
JSON_ARRAY(0,0,1,2,3,3,4,5,6,8),
0),

-- ============================================================
-- 最近 24 小时内的提交（测试 last24h 统计）
-- ============================================================

-- Alice: 今天的新提交
('sub-031', 1, 'user-alice-001', 'python3',
'def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]',
'Accepted', 120, 18.0, '一行解法（性能一般）',
DATE_SUB(NOW(), INTERVAL 3 HOUR), 78.0, 82.0,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,2,5,8,12,16,20,25,30,35),
JSON_ARRAY(0,20,40,60,80,100,120,140,160,180),
0),

-- Bob: 今天的错误提交
('sub-032', 1, 'user-bob-002', 'java',
'class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        Arrays.sort(nums);\n        int left = 0, right = nums.length - 1;\n        while (left < right) {\n            int sum = nums[left] + nums[right];\n            if (sum == target) return new int[]{left, right};\n            else if (sum < target) left++;\n            else right--;\n        }\n        return new int[]{};\n    }\n}',
'Wrong Answer', 5, 42.0, '排序破坏索引',
DATE_SUB(NOW(), INTERVAL 2 HOUR), NULL, NULL, NULL, NULL, NULL, 0),

-- Carol: 今天的提交
('sub-033', 1, 'user-carol-003', 'cpp17',
'class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        int n = nums.size();\n        for (int i = 0; i < n; i++)\n            for (int j = i + 1; j < n; j++)\n                if (nums[i] + nums[j] == target)\n                    return {i, j};\n        return {};\n    }\n};',
'Accepted', 290, 10.5, '暴力解法也能过',
DATE_SUB(NOW(), INTERVAL 1 HOUR), 8.0, 98.5,
JSON_ARRAY(JSON_OBJECT('status', CASE WHEN 50 >= 40 THEN 'AC' ELSE 'WA' END, 'time', 50, 'memory', 15.0)),
JSON_ARRAY(0,1,2,4,6,8,9,10,11,12),
JSON_ARRAY(0,50,100,150,200,250,280,300,350,400),
0);
