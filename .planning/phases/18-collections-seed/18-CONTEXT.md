# Phase 18: Collections Seed (V25) - Context

**Gathered:** 2026-04-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 18 delivers: ~50 scenario-based problem collections organized by difficulty, tags, and interview companies. Each collection has ≥3 items, icon (Lucide name), and color (Tailwind color). Collections reference PROBLEM_LIST entries from V15.

**Deliverable:** V25__collections_seed.sql with ~50 collections and ~200+ collection_items.

**Success Criteria:**
1. User can browse collections page and see ~50 collections by scenario type
2. User can view a collection with icon (Lucide name) and color (Tailwind color) styling
3. User can verify each collection contains ≥3 items
4. User can verify each collection item references a valid problem_list_id (PROBLEM_LIST target_type)

**Requirements (from REQUIREMENTS.md):** COL-01, COL-02, COL-03, COL-04

**Schema Facts:**
- `collections` table: id, user_id, name, description, icon, color, sort_order, is_default
- `collection_items` table: id, collection_id, target_id, target_type (PROBLEM_LIST|PROBLEM|etc), sort_order, note, created_at
- V15 added: list-interview-100, list-database, list-concurrency, list-graph-advanced
- V8 existing: list-essentials, list-intervals, list-sliding-window (with descriptions already)
- FK: collection_items.target_id → problem_lists.id (target_type = 'PROBLEM_LIST')
</domain>

<decisions>
## Implementation Decisions

### D-01: Collection Scenario Distribution (~50 total)
**Decision:** Mixed categorization — 3 difficulty tiers + tag-based + company-based

Rough distribution:
- **By Difficulty (3):** "算法入门精选" (Easy), "算法进阶挑战" (Medium), "hardcore 专家级" (Hard) — each referencing problems of that tier
- **By Topic/Tag (~20):** Array, String, Tree, Graph, Dynamic Programming, Greedy, Binary Search, Two Pointers, Sliding Window, Stack, Queue, Heap, Backtracking, Divide & Conquer, Bit Manipulation, Math, Geometry, etc.
- **By Company/Use Case (~15):** "FANG 面试高频" (Facebook/Amazon/Netflix/Google), "FLAGM 面试", "国内大厂面试" (BAT+字节+美团), "Startup 练手", "竞赛入门", "ACM 竞赛进阶"
- **By Contest Type (~5):** "周赛题目精选", "双周赛压轴题", "LeetCode Hot 100", "剑指 Offer 专项", "面试算法 100 题"
- **Featured Lists (3):** Reference the V15 featured lists directly (list-interview-100, list-database, list-concurrency, list-graph-advanced) as named collections with icon+color

**Rationale:** V15 problem_lists already exist with topic groupings. Building collections on top of them provides clean separation of concerns. V8 had user-specific collections; V25 shifts to platform-wide scenario collections.

### D-02: Icon Strategy
**Decision:** Semantic Lucide icon names per category

Mapping:
- Difficulty Easy: `Star`
- Difficulty Medium: `Flame`
- Difficulty Hard: `Zap`
- Array: `Square` or `Grid`
- String: `Type`
- Tree: `GitBranch`
- Graph: `Share2`
- Dynamic Programming: `Layers`
- Backtracking: `Undo2`
- Binary Search: `Search`
- Stack/Queue: `ListOrdered`
- Heap: `ArrowUpDown`
- Company/FANG: `Building2`
- Contest: `Trophy`
- Hot 100: `Flame`
- Custom: `Bookmark`

**Rationale:** Lucide icons are already used in the codebase. Semantic icons help users scan collections faster.

### D-03: Color Strategy
**Decision:** Tailwind color names, semantic per category

Mapping:
- Easy / Green: `emerald`
- Medium / Amber: `amber`
- Hard / Red: `rose`
- Company: `violet`
- Topic (generic): `sky`, `teal`, `orange`, `cyan` (alternating)
- Featured / Featured Lists: `amber` (warm, highlight)

**Rationale:** Tailwind color names (not hex) are stored in DB and rendered via CSS variable. Color semantic = difficulty signal + visual scanning aid.

### D-04: User ID Ownership
**Decision:** Admin users as creators — user-alex (primary), user-david, user-sara, user-chen as secondary owners

Rationale: Collections are platform-wide (not user-private). Use the same admin user pool from V15/V16 seeds for consistency.

### D-05: Collection Items target_type
**Decision:** Mix of PROBLEM_LIST (primary) and direct PROBLEM references

- Scenario/topic/company collections → reference PROBLEM_LIST (clean grouping via V15 lists)
- Difficulty tier collections → reference individual PROBLEMS directly (not all problems in a tier belong to same list)
- Minimum 3 items per collection enforced via INSERT design

### D-06: Icon and Color Storage Format
**Decision:** Store Lucide icon name string (e.g., `Star`) and Tailwind color name (e.g., `emerald`) directly in `icon` and `color` columns

**Rationale:** Frontend maps these to actual Lucide component and Tailwind class. No lookup tables needed. This matches existing V8 seed data pattern (icon/color columns exist but were NULL).

### D-07: FK Validation Strategy
**Decision:** Reference V15 problem_lists.id values: list-essentials, list-intervals, list-sliding-window, list-interview-100, list-database, list-concurrency, list-graph-advanced

**Rationale:** These are the only public PROBLEM_LIST entries. Need to verify their IDs are stable.

### D-08: Collection Naming Language
**Decision:** Chinese names with descriptive, user-facing labels

Examples: "数组专项训练", "字符串处理进阶", "图论算法面试高频", "FANG 面试算法合集", "LeetCode Hot 100 精选"

**Rationale:** Platform is Chinese-language primary. Chinese names match problem_lists V15 descriptions.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `db-manager/migrations/V8__collection_schema.sql` — collections + collection_items table schemas, existing seed data (6 collections, 7 items)
- `db-manager/migrations/V15__featured_problem_lists.sql` — problem_lists for list-interview-100, list-database, list-concurrency, list-graph-advanced (V15 adds these with banner configs)

### Prior Phase Context
- `.planning/REQUIREMENTS.md` — COL-01 through COL-04 requirements
- `.planning/ROADMAP.md` — Phase 18 goal and success criteria

### No external specs — requirements fully captured in decisions above
</canonical_refs>

<codebase_context>
## Existing Code Insights

### Reusable Assets
- V15 problem_lists: list-essentials, list-intervals, list-sliding-window, list-interview-100, list-database, list-concurrency, list-graph-advanced — these are the FK targets for collection_items.target_id with target_type='PROBLEM_LIST'
- V8 existing collections: 6 collections (user-private favorites) — these are NOT in scope for V25 (V25 creates platform-wide scenario collections)
- Lucide icon names — standard Lucide set used throughout console/management frontends
- Tailwind color names — standard Tailwind palette

### Established Patterns
- Seed data follows: SET FOREIGN_KEY_CHECKS=0 → INSERTs → SET FOREIGN_KEY_CHECKS=1
- UUID() for id columns
- NOW(3) for created_at
- user_id references: user-alex, user-david, user-sara, user-chen (admin pool)
- target_type enum: 'PROBLEM_LIST' | 'PROBLEM' | 'SOLUTION' | etc.

### Integration Points
- Collections API: GET /collections, GET /collections/{id}
- Collection items API: GET /collections/{id}/items
- Frontend: Collection browse page, collection detail page
- target_type='PROBLEM_LIST' → joins with problem_lists for display
</codebase_context>

<specifics>
## Specific Ideas

No specific UI reference cases — open to standard collection browse patterns.

Lucide icon set: Star, Flame, Zap, Square, Type, GitBranch, Share2, Layers, Undo2, Search, ListOrdered, ArrowUpDown, Building2, Trophy, Bookmark, etc.

Tailwind colors: emerald, amber, rose, violet, sky, teal, orange, cyan, indigo, lime, etc.
</specifics>

<deferred>
## Deferred Ideas

### Out of scope for Phase 18
- Collection comments or likes (future engagement feature)
- Personal private collections (V8 already has user-private favorites)
- Collection sharing or collaboration
- Auto-generated "similar problems" collections (recommendation service territory)

### Future Phase Candidates
- Contest-related collections (CONTEST-03 deferred to v1.5)
- Problem difficulty re-calibration based on actual submission acceptance rates
- User-specific collection recommendations based on submission history
</deferred>

---

*Phase: 18-collections-seed*
*Context gathered: 2026-04-19*
