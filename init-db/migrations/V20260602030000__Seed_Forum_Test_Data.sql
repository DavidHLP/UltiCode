-- ============================================================
-- Seed: Forum Test Data (论坛测试数据)
-- Date : 2026-06-02
-- Desc : 为论坛模块插入分类、标签、帖子、回复及投票测试数据
-- ============================================================

-- ----------------------------------------------------------
-- 1. 论坛分类 (forum_categories)
-- ----------------------------------------------------------
INSERT INTO forum_categories (id, name, slug, description, icon, color, sort_order, is_active, create_time, update_time) VALUES
(1,  '综合讨论',   'general',       '综合讨论区，自由交流编程相关话题',                'message-circle', '#6366f1', 1, 1, NOW(), NOW()),
(2,  '算法交流',   'algorithm',     '讨论算法思路、数据结构与竞赛题目',                'code',           '#f59e0b', 2, 1, NOW(), NOW()),
(3,  '求职面经',   'career',        '面试经验分享、求职技巧与职业发展',                'briefcase',      '#10b981', 3, 1, NOW(), NOW()),
(4,  '项目展示',   'showcase',      '展示你的开源项目、个人作品与练手项目',            'rocket',         '#ef4444', 4, 1, NOW(), NOW()),
(5,  '学习资源',   'resources',     '优质教程、书籍推荐与学习路线分享',                'book-open',      '#8b5cf6', 5, 1, NOW(), NOW()),
(6,  '站务反馈',   'site-feedback', '平台使用问题反馈与功能建议',                      'megaphone',      '#ec4899', 6, 1, NOW(), NOW());

-- ----------------------------------------------------------
-- 2. 论坛标签 (forum_tags)
-- ----------------------------------------------------------
INSERT INTO forum_tags (id, name, slug, color, usage_count, create_time, update_time) VALUES
(1,  '动态规划',   'dp',            '#f59e0b', 12, NOW(), NOW()),
(2,  '图论',       'graph',         '#10b981',  8, NOW(), NOW()),
(3,  '贪心',       'greedy',        '#ef4444',  5, NOW(), NOW()),
(4,  '数论',       'math',          '#8b5cf6',  6, NOW(), NOW()),
(5,  '字符串',     'string',        '#6366f1',  7, NOW(), NOW()),
(6,  'Java',       'java',          '#f97316', 10, NOW(), NOW()),
(7,  'Python',     'python',        '#3b82f6',  9, NOW(), NOW()),
(8,  'C++',        'cpp',           '#64748b', 11, NOW(), NOW()),
(9,  '前端',       'frontend',      '#ec4899',  4, NOW(), NOW()),
(10, '面试',       'interview',     '#14b8a6',  3, NOW(), NOW()),
(11, '实习',       'internship',    '#a855f7',  2, NOW(), NOW()),
(12, '开源',       'open-source',   '#f43f5e',  1, NOW(), NOW()),
(13, '教程',       'tutorial',      '#22c55e',  3, NOW(), NOW()),
(14, '竞赛',       'competitive',   '#eab308',  6, NOW(), NOW()),
(15, '调试技巧',   'debugging',     '#0ea5e9',  4, NOW(), NOW());

-- ----------------------------------------------------------
-- 3. 论坛帖子 (forum_posts)
-- ----------------------------------------------------------
INSERT INTO forum_posts (id, user_id, category_id, title, slug, content, status, is_pinned, view_count, like_count, comment_count, create_time, update_time) VALUES
(1, 2, 2,
 '动态规划入门指南：从零开始理解状态转移',
 'dp-beginner-guide',
 '最近在刷题的时候发现很多同学对动态规划感到头疼，今天来分享一下我的学习心得。\n\n## 什么是动态规划？\n\n动态规划（Dynamic Programming，简称 DP）是一种通过把原问题分解为相对简单的子问题来求解复杂问题的方法。它的核心思想是：\n\n1. **最优子结构**：问题的最优解包含子问题的最优解\n2. **重叠子问题**：子问题会被重复计算\n3. **状态转移方程**：描述问题各阶段之间关系的公式\n\n## 经典入门题目\n\n### 1. 斐波那契数列\n\n这是最简单的 DP 问题，状态转移方程为：`dp[i] = dp[i-1] + dp[i-2]`\n\n### 2. 爬楼梯\n\n每次可以爬 1 或 2 个台阶，到达第 n 阶有多少种方法？\n\n```python\ndef climb_stairs(n):\n    if n <= 2:\n        return n\n    dp = [0] * (n + 1)\n    dp[1], dp[2] = 1, 2\n    for i in range(3, n + 1):\n        dp[i] = dp[i-1] + dp[i-2]\n    return dp[n]\n```\n\n### 3. 最长递增子序列\n\n这是一道经典的 DP 题，时间复杂度可以优化到 O(n log n)。\n\n## 学习建议\n\n1. 先理解暴力递归解法\n2. 加记忆化优化\n3. 改写为自底向上的 DP\n4. 尝试空间优化\n\n希望大家都能攻克 DP 这个难关！',
 'published', 1, 1523, 86, 12, NOW() - INTERVAL 7 DAY, NOW()),

(2, 3, 3,
 '字节跳动 2025 暑期实习后端面经分享',
 'bytedance-2025-summer-intern-backend',
 '上周刚结束字节跳动后端开发实习的面试，来分享一下完整的面试流程和题目。\n\n## 一面（技术面）\n\n### 算法题\n1. LeetCode 200 - 岛屿数量\n2. 手写 LRU 缓存\n\n### 八股文\n- Java HashMap 底层实现\n- ConcurrentHashMap 如何保证线程安全\n- MySQL 索引底层结构（B+ 树）\n- Redis 持久化方式（RDB vs AOF）\n- Spring Bean 的生命周期\n\n### 项目相关\n- 介绍你的项目中比较有技术含量的部分\n- 你是如何解决高并发场景下的问题的？\n\n## 二面（技术面）\n\n### 算法题\n1. 二叉树的锯齿形层序遍历\n2. 设计一个短链接系统\n\n### 系统设计\n- 如何设计一个消息队列？\n- 分布式 ID 生成方案对比\n\n## 三面（HR 面）\n\n- 职业规划\n- 为什么选择字节\n- 期望薪资\n\n## 总结\n\n整体面试体验不错，面试官都很专业。算法题难度中等，八股文问得比较深入。建议准备面试的同学重点复习 Java 并发和 MySQL 索引相关内容。\n\n祝大家都能拿到心仪的 offer！',
 'published', 0, 892, 67, 23, NOW() - INTERVAL 5 DAY, NOW()),

(3, 4, 4,
 '开源了一个在线代码编辑器，支持 20+ 种语言',
 'open-sourced-online-code-editor',
 '大家好！我花了几个月时间开发了一个基于 Web 的在线代码编辑器，现在开源了！\n\n## 功能特性\n\n- 支持 20+ 种编程语言的语法高亮\n- 实时代码执行和输出\n- 多人协作编辑（基于 CRDT）\n- 代码片段分享\n- 暗色/亮色主题切换\n\n## 技术栈\n\n- 前端：Vue 3 + Monaco Editor\n- 后端：Spring Boot + Docker（代码沙箱）\n- 实时协作：WebSocket + Yjs\n- 数据库：MySQL + Redis\n\n## 在线体验\n\n可以直接访问在线版本试用，也可以本地部署。\n\n## GitHub 地址\n\n欢迎 Star 和 PR！\n\n## 后续计划\n\n1. 增加代码评审功能\n2. 支持更多语言的运行时\n3. 添加 AI 代码补全\n4. 移动端适配\n\n希望大家能多提建议和反馈！',
 'published', 0, 456, 34, 8, NOW() - INTERVAL 4 DAY, NOW()),

(4, 5, 5,
 '推荐几本数据结构与算法的经典书籍',
 'recommend-dsa-books',
 '学习算法以来读了不少书，给大家推荐几本我觉得最值得读的。\n\n## 入门级\n\n### 《算法图解》\n- 适合完全没有基础的同学\n- 图文并茂，通俗易懂\n- 覆盖了基础算法和数据结构\n\n### 《大话数据结构》\n- 国内作者，语言幽默\n- 例子贴近生活\n- 适合入门快速过一遍\n\n## 进阶级\n\n### 《算法（第4版）》- Sedgewick\n- 普林斯顿大学教材\n- Java 实现，代码清晰\n- 配套 Coursera 课程非常棒\n\n### 《数据结构与算法分析》- Mark Allen Weiss\n- 理论和实践兼顾\n- 习题丰富，难度梯度合理\n\n## 竞赛级\n\n### 《算法竞赛入门经典》- 刘汝佳\n- ACM 竞赛必读\n- C++ 实现\n- 例题经典\n\n### 《算法导论》（CLRS）\n- 算法领域的"圣经"\n- 理论性较强，不建议入门直接读\n- 适合作为工具书查阅\n\n## 学习建议\n\n1. 先快速过一本入门书建立整体认知\n2. 刷 LeetCode 配合进阶书籍深入理解\n3. 竞赛党直接上紫书和白书\n4. CLRS 作为字典，遇到不懂的理论再查\n\n大家还有什么好书推荐吗？欢迎评论区补充！',
 'published', 0, 678, 45, 15, NOW() - INTERVAL 3 DAY, NOW()),

(5, 2, 2,
 '图论最短路径算法总结：Dijkstra、Bellman-Ford、Floyd',
 'shortest-path-algorithms-summary',
 '图论中最短路径是高频考点，今天来总结三大经典算法。\n\n## 1. Dijkstra 算法\n\n### 适用场景\n- 边权非负的单源最短路径\n- 时间复杂度：O((V+E) log V)（优先队列优化）\n\n### 核心思想\n贪心地选择当前距离最小的未访问节点，用其更新邻接节点的距离。\n\n```cpp\nvoid dijkstra(int src) {\n    priority_queue<pair<int,int>, vector<pair<int,int>>, greater<>> pq;\n    dist[src] = 0;\n    pq.push({0, src});\n    while (!pq.empty()) {\n        auto [d, u] = pq.top(); pq.pop();\n        if (d > dist[u]) continue;\n        for (auto [v, w] : adj[u]) {\n            if (dist[u] + w < dist[v]) {\n                dist[v] = dist[u] + w;\n                pq.push({dist[v], v});\n            }\n        }\n    }\n}\n```\n\n## 2. Bellman-Ford 算法\n\n### 适用场景\n- 可以处理负权边\n- 可以检测负权环\n- 时间复杂度：O(VE)\n\n### 核心思想\n对所有边进行 V-1 轮松弛操作。\n\n## 3. Floyd 算法\n\n### 适用场景\n- 多源最短路径\n- 时间复杂度：O(V³)\n- 适合稠密图\n\n### 核心思想\n三重循环，尝试以每个节点为中转点更新路径。\n\n## 算法选择指南\n\n| 场景 | 推荐算法 |\n|------|----------|\n| 非负权单源 | Dijkstra |\n| 有负权边 | Bellman-Ford |\n| 多源最短路径 | Floyd |\n| 检测负环 | Bellman-Ford |\n\n希望对大家有帮助！有问题欢迎讨论。',
 'published', 0, 345, 28, 6, NOW() - INTERVAL 2 DAY, NOW()),

(6, 3, 1,
 '大家平时都用什么 IDE 写代码？来投票吧！',
 'what-ide-do-you-use-poll',
 '最近想换 IDE，想看看大家都在用什么。来投个票吧！\n\n## 我用过的 IDE\n\n1. **VS Code** - 轻量、插件丰富，主力开发工具\n2. **IntelliJ IDEA** - Java 开发首选，智能提示太强了\n3. **Vim/Neovim** - 服务器上编辑配置文件必备\n4. **Cursor** - AI 辅助编程，效率提升明显\n\n## 各语言推荐\n\n- Java：IntelliJ IDEA\n- Python：PyCharm / VS Code\n- C++：CLion / VS Code\n- 前端：VS Code\n- Rust：RustRover / VS Code\n\n## 我的选择\n\n目前主力是 VS Code + IntelliJ 双持，小项目用 VS Code，大项目用 IntelliJ。\n\n大家呢？评论区说说你的选择和理由！',
 'published', 0, 234, 19, 31, NOW() - INTERVAL 1 DAY, NOW()),

(7, 4, 6,
 '建议增加代码片段收藏功能',
 'feature-request-code-snippet-bookmark',
 '使用平台有一段时间了，整体体验不错。有一个功能建议：\n\n## 建议内容\n\n增加**代码片段收藏**功能，方便用户收藏和整理在题解或帖子中看到的好代码。\n\n## 使用场景\n\n1. 看到一道题的巧妙解法，想收藏起来以后复习\n2. 收集各种模板代码，比赛时快速查找\n3. 整理自己常用的代码片段\n\n## 建议的功能设计\n\n- 在题解和帖子中增加"收藏代码"按钮\n- 个人中心增加"代码收藏"管理页面\n- 支持给收藏的代码打标签和分类\n- 支持搜索已收藏的代码\n\n希望开发团队能考虑一下，谢谢！',
 'published', 0, 89, 12, 5, NOW() - INTERVAL 12 HOUR, NOW()),

(8, 5, 5,
 '分享一个 Python 算法模板库，竞赛党必备',
 'python-algorithm-template-library',
 '最近整理了一份 Python 算法竞赛常用模板，分享给大家。\n\n## 包含的模板\n\n### 基础算法\n- 二分查找\n- 前缀和与差分\n- 双指针\n- 滑动窗口\n\n### 数据结构\n- 并查集\n- 线段树\n- 树状数组\n- 单调栈/队列\n\n### 图论\n- DFS/BFS\n- 拓扑排序\n- 最小生成树\n- 网络流\n\n### 动态规划\n- 背包问题\n- 区间 DP\n- 树形 DP\n- 状态压缩 DP\n\n### 数学\n- 快速幂\n- GCD/LCM\n- 组合数\n- 素数筛\n\n## 使用方式\n\n直接复制到比赛环境中使用即可，每个模板都有注释说明。\n\n## 示例\n\n```python\nclass UnionFind:\n    def __init__(self, n):\n        self.parent = list(range(n))\n        self.rank = [0] * n\n\n    def find(self, x):\n        if self.parent[x] != x:\n            self.parent[x] = self.find(self.parent[x])\n        return self.parent[x]\n\n    def union(self, x, y):\n        px, py = self.find(x), self.find(y)\n        if px == py:\n            return\n        if self.rank[px] < self.rank[py]:\n            px, py = py, px\n        self.parent[py] = px\n        if self.rank[px] == self.rank[py]:\n            self.rank[px] += 1\n```\n\n欢迎补充和改进！',
 'published', 0, 567, 41, 9, NOW() - INTERVAL 8 HOUR, NOW()),

(9, 2, 3,
 '腾讯提前批后端面试总结（已拿 offer）',
 'tencent-early-batch-backend-offer',
 '终于拿到腾讯的 offer 了！分享一下完整的面试经历。\n\n## 背景\n\n- 985 硕士，后端方向\n- 一段大厂实习经历\n- LeetCode 刷了 400+\n\n## 提前批流程\n\n总共三轮技术面 + 一轮 HR 面。\n\n### 一面\n\n算法：\n1. 合并 K 个升序链表\n2. 设计微信抢红包系统\n\n八股：\n- TCP 三次握手和四次挥手\n- HTTP/2 与 HTTP/1.1 的区别\n- MySQL 事务隔离级别\n- 如何避免幻读\n\n### 二面\n\n算法：\n1. 二叉树中的最大路径和\n2. 实现一个线程安全的单例模式\n\n项目深挖：\n- 你项目中的缓存策略是怎么设计的？\n- 遇到过缓存击穿吗？怎么解决的？\n- 数据库分库分表的方案了解吗？\n\n### 三面\n\n- 职业规划\n- 技术视野（最近关注什么新技术）\n- 反问环节\n\n## 经验总结\n\n1. 算法是基础，每天坚持刷题\n2. 八股要深入理解，不要死记硬背\n3. 项目要有亮点，能说清楚技术选型原因\n4. 沟通表达很重要，面试时思路要清晰\n\n祝大家秋招顺利！',
 'published', 0, 1023, 78, 18, NOW() - INTERVAL 6 HOUR, NOW()),

(10, 3, 2,
 '字符串匹配算法详解：从暴力到 KMP',
 'string-matching-from-brute-force-to-kmp',
 '字符串匹配是算法中的经典问题，今天来详细讲解从暴力到 KMP 的演进过程。\n\n## 暴力匹配\n\n时间复杂度 O(n*m)，最朴素的方法，逐个字符比较。\n\n```python\ndef brute_force(text, pattern):\n    n, m = len(text), len(pattern)\n    for i in range(n - m + 1):\n        if text[i:i+m] == pattern:\n            return i\n    return -1\n```\n\n## KMP 算法\n\n### 核心思想\n利用已经匹配过的信息，避免主串指针回退。关键在于构建 next 数组（前缀函数）。\n\n### next 数组的含义\nnext[i] 表示 pattern[0...i] 这个子串的最长相同前后缀长度。\n\n### 代码实现\n\n```python\ndef build_next(pattern):\n    m = len(pattern)\n    nxt = [0] * m\n    j = 0\n    for i in range(1, m):\n        while j > 0 and pattern[i] != pattern[j]:\n            j = nxt[j - 1]\n        if pattern[i] == pattern[j]:\n            j += 1\n        nxt[i] = j\n    return nxt\n\ndef kmp_search(text, pattern):\n    nxt = build_next(pattern)\n    j = 0\n    for i in range(len(text)):\n        while j > 0 and text[i] != pattern[j]:\n            j = nxt[j - 1]\n        if text[i] == pattern[j]:\n            j += 1\n        if j == len(pattern):\n            return i - j + 1\n    return -1\n```\n\n## 复杂度分析\n\n| 算法 | 预处理 | 匹配 | 总计 |\n|------|--------|------|------|\n| 暴力 | O(1) | O(nm) | O(nm) |\n| KMP | O(m) | O(n) | O(n+m) |\n\n## 其他字符串匹配算法\n\n- Rabin-Karp：基于哈希的匹配\n- Boyer-Moore：从右向左匹配\n- Sunday 算法：实战中最快的单模式匹配\n\n有问题欢迎讨论！',
 'published', 0, 412, 33, 7, NOW() - INTERVAL 3 HOUR, NOW());

-- ----------------------------------------------------------
-- 4. 帖子-标签关联 (forum_post_tags)
-- ----------------------------------------------------------
INSERT INTO forum_post_tags (post_id, tag_id, create_time) VALUES
-- 帖子1：动态规划入门指南
(1, 1, NOW()),
(1, 7, NOW()),
(1, 13, NOW()),
-- 帖子2：字节面经
(2, 10, NOW()),
(2, 11, NOW()),
(2, 6, NOW()),
-- 帖子3：在线代码编辑器
(3, 12, NOW()),
(3, 9, NOW()),
(3, 6, NOW()),
-- 帖子4：算法书籍推荐
(4, 13, NOW()),
(4, 14, NOW()),
-- 帖子5：最短路径算法
(5, 2, NOW()),
(5, 8, NOW()),
(5, 14, NOW()),
-- 帖子6：IDE 投票
(6, 6, NOW()),
(6, 7, NOW()),
(6, 8, NOW()),
-- 帖子7：功能建议
(7, 15, NOW()),
-- 帖子8：Python 模板库
(8, 7, NOW()),
(8, 14, NOW()),
(8, 13, NOW()),
-- 帖子9：腾讯面经
(9, 10, NOW()),
(9, 6, NOW()),
-- 帖子10：字符串匹配
(10, 5, NOW()),
(10, 7, NOW()),
(10, 14, NOW());

-- ----------------------------------------------------------
-- 5. 帖子回复/评论 (forum_comments)
-- ----------------------------------------------------------
INSERT INTO forum_comments (id, post_id, user_id, parent_id, content, like_count, create_time, update_time) VALUES
-- 帖子1 的回复
(1, 1, 3, NULL,
 '写得太好了！状态转移方程那部分终于让我理解了。之前一直搞不清 DP 和贪心的区别，现在明白了：贪心是局部最优，DP 是全局最优。',
 12, NOW() - INTERVAL 6 DAY, NOW()),
(2, 1, 4, 1,
 '对，贪心不回头，DP 会利用之前的结果。建议再看看背包问题，更能体会 DP 的精髓。',
 5, NOW() - INTERVAL 6 DAY, NOW()),
(3, 1, 5, NULL,
 '补充一个学习路径：可以先从记忆化搜索开始理解 DP，再过渡到自底向上的写法，这样理解会更自然。',
 8, NOW() - INTERVAL 5 DAY, NOW()),

-- 帖子2 的回复
(4, 2, 2, NULL,
 '感谢分享！请问 ConcurrentHashMap 那道题你是怎么回答的？是讲分段锁还是 CAS？',
 9, NOW() - INTERVAL 4 DAY, NOW()),
(5, 2, 3, 4,
 '我主要讲了 JDK8 的实现：用 CAS + synchronized 优化了分段锁，锁粒度从 Segment 级别降到了 Node 级别。面试官似乎对这个回答比较满意。',
 6, NOW() - INTERVAL 4 DAY, NOW()),
(6, 2, 6, NULL,
 '同求问，短链接系统设计那道题你是怎么设计的？',
 4, NOW() - INTERVAL 3 DAY, NOW()),
(7, 2, 3, 6,
 '短链接主要涉及：1) 长短链接映射（用自增 ID + Base62 编码）2) 重定向（302 + 缓存）3) 防止冲突（布隆过滤器）4) 高可用设计。面试时画了架构图，面试官追问了缓存一致性。',
 7, NOW() - INTERVAL 3 DAY, NOW()),

-- 帖子3 的回复
(8, 3, 2, NULL,
 '项目很棒！请问多人协作的冲突解决是怎么实现的？用的 OT 还是 CRDT？',
 5, NOW() - INTERVAL 3 DAY, NOW()),
(9, 3, 4, 8,
 '用的 CRDT，基于 Yjs 库实现的。OT 实现起来比较复杂，需要服务端参与冲突解决，CRDT 可以纯客户端解决。',
 3, NOW() - INTERVAL 3 DAY, NOW()),
(10, 3, 5, NULL,
 '支持！代码沙箱是用 Docker 实现的吗？安全性怎么保证的？',
 4, NOW() - INTERVAL 2 DAY, NOW()),

-- 帖子4 的回复
(11, 4, 2, NULL,
 '强烈推荐《算法（第4版）》！配套的 Coursera 课程确实很棒，Algorithms I & II 我都看完了，收获很大。',
 8, NOW() - INTERVAL 2 DAY, NOW()),
(12, 4, 3, NULL,
 '补充推荐《挑战程序设计竞赛》，日本作者写的，思路很独特，适合竞赛入门。另外《算法竞赛进阶指南》李煜东写的也很不错。',
 6, NOW() - INTERVAL 2 DAY, NOW()),
(13, 4, 6, NULL,
 '入门的话我还推荐《Hello 算法》，开源免费，图文并茂，适合零基础。网站上有动画演示，特别直观。',
 10, NOW() - INTERVAL 1 DAY, NOW()),

-- 帖子5 的回复
(14, 5, 3, NULL,
 '总结得很到位！补充一点：Dijkstra 在稠密图上可以直接用数组实现，不一定要用优先队列，时间复杂度是 O(V²)，某些情况下比堆优化更快。',
 4, NOW() - INTERVAL 1 DAY, NOW()),
(15, 5, 6, NULL,
 '请问 SPFA 和 Bellman-Ford 是什么关系？经常看到有人说 SPFA 已死，这是怎么回事？',
 3, NOW() - INTERVAL 18 HOUR, NOW()),
(16, 5, 2, 15,
 'SPFA 是 Bellman-Ford 的队列优化版本。"SPFA 已死"是因为在比赛中可能会被故意构造的数据卡到 O(VE) 的最坏复杂度。不过日常刷题 SPFA 还是很好用的，代码也更简洁。',
 5, NOW() - INTERVAL 17 HOUR, NOW()),

-- 帖子6 的回复
(17, 6, 4, NULL,
 'VS Code + IntelliJ 双持 +1。不过最近在尝试 Cursor，AI 辅助确实能提高效率，特别是写模板代码的时候。',
 3, NOW() - INTERVAL 20 HOUR, NOW()),
(18, 6, 2, NULL,
 'Neovim 党路过。配好 LazyVim 之后开发体验不输 VS Code，而且终端里操作效率很高。就是学习曲线比较陡峭。',
 7, NOW() - INTERVAL 18 HOUR, NOW()),
(19, 6, 5, NULL,
 'PyCharm 用户。虽然比较重，但是 Python 的调试、重构和类型检查体验是其他 IDE 比不了的。',
 2, NOW() - INTERVAL 15 HOUR, NOW()),

-- 帖子7 的回复
(20, 7, 2, NULL,
 '好建议！我也有这个需求，经常看到好的题解想收藏代码但只能收藏整个帖子。如果可以支持代码片段级别的收藏就更好了。',
 8, NOW() - INTERVAL 10 HOUR, NOW()),
(21, 7, 6, NULL,
 '支持！另外建议加上代码片段的搜索功能，收藏多了之后管理起来很麻烦。',
 4, NOW() - INTERVAL 8 HOUR, NOW()),

-- 帖子8 的回复
(22, 8, 2, NULL,
 '模板很全！不过建议线段树模板加上 lazy propagation（延迟标记），竞赛中大多数线段树题都需要这个。',
 6, NOW() - INTERVAL 6 HOUR, NOW()),
(23, 8, 5, NULL,
 '感谢分享！请问 Python 打竞赛的话速度会不会有问题？有些题 Python 跑不过。',
 3, NOW() - INTERVAL 5 HOUR, NOW()),
(24, 8, 3, 23,
 '确实会。Python 比 C++ 慢大概 5-10 倍，有些卡时间的题只能用 C++。不过大部分题 Python 够用，实在不行可以 PyPy。',
 4, NOW() - INTERVAL 4 HOUR, NOW()),

-- 帖子9 的回复
(25, 9, 4, NULL,
 '恭喜拿 offer！请问你 LeetCode 刷了多长时间？有没有重点刷哪些类型的题？',
 5, NOW() - INTERVAL 5 HOUR, NOW()),
(26, 9, 2, NULL,
 '缓存击穿那道题你是怎么回答的？我想了解一下标准答案。',
 3, NOW() - INTERVAL 4 HOUR, NOW()),
(27, 9, 5, NULL,
 '微信抢红包系统设计挺有意思的，能详细说说你的设计思路吗？',
 4, NOW() - INTERVAL 3 HOUR, NOW()),

-- 帖子10 的回复
(28, 10, 4, NULL,
 'KMP 的 next 数组理解起来确实需要时间，我是通过手动模拟几遍才真正理解的。建议初学者拿纸笔走一遍流程。',
 3, NOW() - INTERVAL 2 HOUR, NOW()),
(29, 10, 6, NULL,
 '推荐一下 Sunday 算法！在实际工程中比 KMP 快很多，实现也简单。核心思想是从右向左匹配，失配时根据主串下一个字符决定滑动距离。',
 5, NOW() - INTERVAL 1 HOUR, NOW());

-- ----------------------------------------------------------
-- 6. 帖子投票 (forum_post_votes)
-- ----------------------------------------------------------
INSERT INTO forum_post_votes (user_id, post_id, vote_type, create_time) VALUES
-- 帖子1 的投票（赞）
(3, 1, 'up', NOW() - INTERVAL 6 DAY),
(4, 1, 'up', NOW() - INTERVAL 6 DAY),
(5, 1, 'up', NOW() - INTERVAL 5 DAY),
(6, 1, 'up', NOW() - INTERVAL 4 DAY),
-- 帖子2 的投票（赞）
(2, 2, 'up', NOW() - INTERVAL 4 DAY),
(4, 2, 'up', NOW() - INTERVAL 4 DAY),
(5, 2, 'up', NOW() - INTERVAL 3 DAY),
(6, 2, 'up', NOW() - INTERVAL 3 DAY),
-- 帖子3 的投票（赞）
(2, 3, 'up', NOW() - INTERVAL 3 DAY),
(3, 3, 'up', NOW() - INTERVAL 2 DAY),
-- 帖子4 的投票（赞）
(2, 4, 'up', NOW() - INTERVAL 2 DAY),
(3, 4, 'up', NOW() - INTERVAL 2 DAY),
(6, 4, 'up', NOW() - INTERVAL 1 DAY),
-- 帖子5 的投票
(3, 5, 'up', NOW() - INTERVAL 1 DAY),
(6, 5, 'up', NOW() - INTERVAL 18 HOUR),
-- 帖子9 的投票（赞）
(3, 9, 'up', NOW() - INTERVAL 5 HOUR),
(4, 9, 'up', NOW() - INTERVAL 4 HOUR),
(5, 9, 'up', NOW() - INTERVAL 3 HOUR),
(6, 9, 'up', NOW() - INTERVAL 2 HOUR),
-- 帖子10 的投票（赞）
(4, 10, 'up', NOW() - INTERVAL 2 HOUR),
(6, 10, 'up', NOW() - INTERVAL 1 HOUR);

-- ----------------------------------------------------------
-- 7. 帖子收藏 (forum_post_bookmarks)
-- ----------------------------------------------------------
INSERT INTO forum_post_bookmarks (user_id, post_id, create_time) VALUES
(3, 1, NOW() - INTERVAL 6 DAY),
(5, 1, NOW() - INTERVAL 5 DAY),
(2, 2, NOW() - INTERVAL 4 DAY),
(4, 2, NOW() - INTERVAL 4 DAY),
(5, 4, NOW() - INTERVAL 2 DAY),
(2, 5, NOW() - INTERVAL 1 DAY),
(3, 8, NOW() - INTERVAL 6 HOUR),
(4, 9, NOW() - INTERVAL 5 HOUR),
(2, 10, NOW() - INTERVAL 2 HOUR);
