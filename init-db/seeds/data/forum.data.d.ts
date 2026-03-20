declare const data: {
    readonly forum_communities: readonly [{
        readonly id: string;
        readonly name: "Interview Experience";
        readonly slug: "interview";
        readonly description: "Share and discuss interview experiences, questions, and preparation strategies.";
        readonly icon: "MessageSquare";
        readonly color: "#3B82F6";
        readonly banner: null;
        readonly members: 12500;
        readonly online: 450;
        readonly posts_count: 3420;
        readonly posts_today: 45;
        readonly posts_week: 312;
        readonly is_official: true;
        readonly is_featured: true;
        readonly sort_order: 1;
        readonly visibility: "PUBLIC";
        readonly created_at: Date;
    }, {
        readonly id: string;
        readonly name: "Career";
        readonly slug: "career";
        readonly description: "Career advice, job opportunities, and professional development discussions.";
        readonly icon: "Briefcase";
        readonly color: "#10B981";
        readonly banner: null;
        readonly members: 8900;
        readonly online: 220;
        readonly posts_count: 2100;
        readonly posts_today: 28;
        readonly posts_week: 189;
        readonly is_official: true;
        readonly is_featured: true;
        readonly sort_order: 2;
        readonly visibility: "PUBLIC";
        readonly created_at: Date;
    }, {
        readonly id: string;
        readonly name: "Compensation";
        readonly slug: "compensation";
        readonly description: "Discuss salaries, benefits, and compensation packages in tech.";
        readonly icon: "DollarSign";
        readonly color: "#F59E0B";
        readonly banner: null;
        readonly members: 15200;
        readonly online: 680;
        readonly posts_count: 4850;
        readonly posts_today: 92;
        readonly posts_week: 541;
        readonly is_official: true;
        readonly is_featured: true;
        readonly sort_order: 3;
        readonly visibility: "PUBLIC";
        readonly created_at: Date;
    }, {
        readonly id: string;
        readonly name: "Technology";
        readonly slug: "technology";
        readonly description: "Technical discussions, new technologies, algorithms, and best practices.";
        readonly icon: "Cpu";
        readonly color: "#8B5CF6";
        readonly banner: null;
        readonly members: 9800;
        readonly online: 305;
        readonly posts_count: 2890;
        readonly posts_today: 38;
        readonly posts_week: 267;
        readonly is_official: true;
        readonly is_featured: true;
        readonly sort_order: 4;
        readonly visibility: "PUBLIC";
        readonly created_at: Date;
    }];
    readonly forum_community_rules: readonly [{
        readonly id: "rule-tech-1";
        readonly community_id: string;
        readonly title: "Show your attempt";
        readonly body: "Include code snippets or reasoning with every technical question.";
        readonly sort_order: 1;
        readonly created_at: Date;
    }, {
        readonly id: "rule-tech-2";
        readonly community_id: string;
        readonly title: "Be constructive";
        readonly body: "Keep feedback actionable and respectful.";
        readonly sort_order: 2;
        readonly created_at: Date;
    }, {
        readonly id: "rule-tech-3";
        readonly community_id: string;
        readonly title: "Use spoiler tags";
        readonly body: "Mark solutions with spoiler tags for ongoing contests.";
        readonly sort_order: 3;
        readonly created_at: Date;
    }, {
        readonly id: "rule-interview-1";
        readonly community_id: string;
        readonly title: "Be respectful";
        readonly body: "Everyone's interview experience is different. Be supportive and constructive.";
        readonly sort_order: 1;
        readonly created_at: Date;
    }, {
        readonly id: "rule-interview-2";
        readonly community_id: string;
        readonly title: "Protect confidentiality";
        readonly body: "Do not share confidential information or questions under NDA.";
        readonly sort_order: 2;
        readonly created_at: Date;
    }, {
        readonly id: "rule-career-1";
        readonly community_id: string;
        readonly title: "Stay professional";
        readonly body: "Maintain professional discourse in all career discussions.";
        readonly sort_order: 1;
        readonly created_at: Date;
    }, {
        readonly id: "rule-comp-1";
        readonly community_id: string;
        readonly title: "Be honest and accurate";
        readonly body: "Share accurate compensation data to help the community.";
        readonly sort_order: 1;
        readonly created_at: Date;
    }];
    readonly forum_community_links: readonly [{
        readonly id: "link-tech-1";
        readonly community_id: string;
        readonly label: "Weekly Editorial";
        readonly url: "https://example.com/editorial";
        readonly sort_order: 1;
    }, {
        readonly id: "link-tech-2";
        readonly community_id: string;
        readonly label: "Discord Server";
        readonly url: "https://discord.gg/ulticode";
        readonly sort_order: 2;
    }, {
        readonly id: "link-interview-1";
        readonly community_id: string;
        readonly label: "Interview Prep Guide";
        readonly url: "https://example.com/interview-guide";
        readonly sort_order: 1;
    }];
    readonly forum_tags: readonly [{
        readonly id: "tag-typescript";
        readonly name: "typescript";
        readonly slug: "typescript";
        readonly color: "#3178C6";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-performance";
        readonly name: "performance";
        readonly slug: "performance";
        readonly color: "#F59E0B";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-hashing";
        readonly name: "hashing";
        readonly slug: "hashing";
        readonly color: "#3B82F6";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-mindset";
        readonly name: "mindset";
        readonly slug: "mindset";
        readonly color: "#8B5CF6";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-psychology";
        readonly name: "psychology";
        readonly slug: "psychology";
        readonly color: "#EC4899";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-strategy";
        readonly name: "strategy";
        readonly slug: "strategy";
        readonly color: "#10B981";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-tutorial";
        readonly name: "tutorial";
        readonly slug: "tutorial";
        readonly color: "#06B6D4";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-visualization";
        readonly name: "visualization";
        readonly slug: "visualization";
        readonly color: "#F59E0B";
        readonly usage_count: 0;
        readonly created_at: Date;
    }, {
        readonly id: "tag-data-structures";
        readonly name: "data-structures";
        readonly slug: "data-structures";
        readonly color: "#8B5CF6";
        readonly usage_count: 0;
        readonly created_at: Date;
    }];
    readonly forum_posts: readonly [{
        readonly id: "post-rust-hashmap";
        readonly community_id: "community-technology";
        readonly user_id: "u-002";
        readonly title: "Why does `Map` feel slower than plain objects in JavaScript CP?";
        readonly body: "I've been grinding AtCoder benchmarks and noticed a huge performance diff.\n\nStandard Map:\n```typescript\nconst map = new Map<number, number>();\n// TLE on large test cases (2.5s)\n```\n\nPlain object:\n```typescript\nconst map: Record<number, number> = Object.create(null);\n// AC (0.8s)\n```\n\nIs this just overhead from `Map`'s hashing, or am I missing a V8 optimization trick?";
        readonly tags: readonly ["typescript", "performance", "hashing"];
        readonly flair_type: "question";
        readonly is_saved: true;
        readonly impressions: 3400;
        readonly is_pinned: false;
        readonly is_locked: false;
        readonly created_at: "2024-11-28T09:15:00.000Z";
    }, {
        readonly id: "post-contest-tilt";
        readonly community_id: "community-technology";
        readonly user_id: "user-david";
        readonly title: "The \"30-Minute Wall\": How do you reset mental state during a contest?";
        readonly body: "Yesterday I bricked Q2. Spent 40 mins debugging a simple off-by-one error. After that, I couldn't focus on Q3/Q4 at all. My brain just felt \"foggy\" and panicked.\n\nDo you have any physical or mental protocols to hard-reset? I've heard of people doing pushups or splashing water.";
        readonly tags: readonly ["mindset", "psychology", "strategy"];
        readonly flair_type: "discussion";
        readonly is_saved: false;
        readonly impressions: 5120;
        readonly is_pinned: true;
        readonly is_locked: false;
        readonly created_at: "2024-11-28T14:30:00.000Z";
    }, {
        readonly id: "post-segtree-visual";
        readonly community_id: "community-technology";
        readonly user_id: "user-tourist";
        readonly title: "Visual Guide to Segment Trees (Lazy Propagation)";
        readonly body: "I wrote a small interactive blog post visualizing how lazy tags flow down strictly during queries.\n\n[Link to visualization](https://example.com/segtree-vis)\n\nKey insight: \"Lazy tags are just pending operations\".\nMost bugs come from:\n1. Not pushing down before reading children.\n2. Not updating the current node after children return.\n\nLet me know if this helps!";
        readonly tags: readonly ["tutorial", "segment-tree", "visualization"];
        readonly flair_type: "showcase";
        readonly is_saved: true;
        readonly impressions: 8900;
        readonly is_pinned: false;
        readonly is_locked: false;
        readonly created_at: "2024-11-27T10:00:00.000Z";
        readonly cover_image: "https://images.unsplash.com/photo-1509228468518-180dd4864904?auto=format&fit=crop&w=1200&q=80";
    }];
    readonly forum_awards: readonly [{
        readonly id: "award-insightful";
        readonly label: "Insightful";
    }, {
        readonly id: "award-helpful";
        readonly label: "Helpful";
    }, {
        readonly id: "award-gold";
        readonly label: "Gold";
    }];
    readonly forum_post_awards: readonly [{
        readonly post_id: "post-rust-hashmap";
        readonly award_id: "award-helpful";
        readonly count: 1;
    }, {
        readonly post_id: "post-contest-tilt";
        readonly award_id: "award-insightful";
        readonly count: 3;
    }, {
        readonly post_id: "post-contest-tilt";
        readonly award_id: "award-gold";
        readonly count: 1;
    }, {
        readonly post_id: "post-segtree-visual";
        readonly award_id: "award-gold";
        readonly count: 5;
    }, {
        readonly post_id: "post-segtree-visual";
        readonly award_id: "award-insightful";
        readonly count: 7;
    }];
    readonly forum_comments: readonly [{
        readonly id: "c-rust-1";
        readonly post_id: "post-rust-hashmap";
        readonly parent_id: null;
        readonly author_id: "user-benq";
        readonly body: "Maps have extra overhead for hashing + boxed keys. For CP, a null-prototype object or array often wins if your keys are small integers.";
        readonly created_at: "2024-11-28T09:20:00.000Z";
    }, {
        readonly id: "c-rust-2";
        readonly post_id: "post-rust-hashmap";
        readonly parent_id: "c-rust-1";
        readonly author_id: "u-002";
        readonly body: "Ah makes sense. I assumed Map would be fastest by default. I will try a null-prototype object.";
        readonly created_at: "2024-11-28T09:35:00.000Z";
    }, {
        readonly id: "c-rust-3";
        readonly post_id: "post-rust-hashmap";
        readonly parent_id: "c-rust-2";
        readonly author_id: "user-petr";
        readonly body: "Be careful with objects: stringifying keys or using mixed types can tank performance. Stick to consistent key types.";
        readonly created_at: "2024-11-28T10:00:00.000Z";
    }, {
        readonly id: "c-rust-4";
        readonly post_id: "post-rust-hashmap";
        readonly parent_id: "c-rust-3";
        readonly author_id: "user-yuki";
        readonly body: "Do JS judges ever include adversarial key patterns? Or are test cases mostly static?";
        readonly created_at: "2024-11-28T10:15:00.000Z";
    }, {
        readonly id: "c-rust-5";
        readonly post_id: "post-rust-hashmap";
        readonly parent_id: "c-rust-4";
        readonly author_id: "user-petr";
        readonly body: "They are static, but bad key distributions still hurt. If key space is dense, use arrays; otherwise Map is fine.";
        readonly created_at: "2024-11-28T10:30:00.000Z";
    }, {
        readonly id: "c-rust-6";
        readonly post_id: "post-rust-hashmap";
        readonly parent_id: null;
        readonly author_id: "user-alex";
        readonly body: "In JS, arrays are usually fastest for dense integer keys; Map is better for sparse keys or non-numeric keys.";
        readonly created_at: "2024-11-28T11:00:00.000Z";
    }, {
        readonly id: "c-tilt-1";
        readonly post_id: "post-contest-tilt";
        readonly parent_id: null;
        readonly author_id: "u-001";
        readonly body: "Breathing protocol: 4 sec in, 4 hold, 4 out. Do it 3 times. It forces heart rate down mechanically.";
        readonly created_at: "2024-11-28T14:40:00.000Z";
    }, {
        readonly id: "c-tilt-2";
        readonly post_id: "post-contest-tilt";
        readonly parent_id: "c-tilt-1";
        readonly author_id: "user-david";
        readonly body: "Will try this next mock. I usually just stare at the screen hyperventilating lol.";
        readonly created_at: "2024-11-28T14:45:00.000Z";
    }, {
        readonly id: "c-tilt-3";
        readonly post_id: "post-contest-tilt";
        readonly parent_id: "c-tilt-2";
        readonly author_id: "user-lily";
        readonly body: "Also, stand up. Physically changing your posture resets the \"tunnel vision\".";
        readonly created_at: "2024-11-28T14:50:00.000Z";
    }, {
        readonly id: "c-tilt-4";
        readonly post_id: "post-contest-tilt";
        readonly parent_id: null;
        readonly author_id: "user-scott";
        readonly body: "I usually rage quit and go play League. (Don't do this)";
        readonly created_at: "2024-11-28T15:00:00.000Z";
    }, {
        readonly id: "c-tilt-5";
        readonly post_id: "post-contest-tilt";
        readonly parent_id: "c-tilt-4";
        readonly author_id: "user-tom";
        readonly body: "Lol literally me last Codeforces round.";
        readonly created_at: "2024-11-28T15:10:00.000Z";
    }, {
        readonly id: "c-tilt-6";
        readonly post_id: "post-contest-tilt";
        readonly parent_id: null;
        readonly author_id: "user-sara";
        readonly body: "I drink cold water. The temperature shock wakes up the prefrontal cortex.";
        readonly created_at: "2024-11-28T15:30:00.000Z";
    }, {
        readonly id: "c-tilt-7";
        readonly post_id: "post-contest-tilt";
        readonly parent_id: "c-tilt-6";
        readonly author_id: "user-emma";
        readonly body: "Science!";
        readonly created_at: "2024-11-28T15:45:00.000Z";
    }, {
        readonly id: "c-seg-1";
        readonly post_id: "post-segtree-visual";
        readonly parent_id: null;
        readonly author_id: "user-jiangly";
        readonly body: "Great visual. Small typo on slide 3: \"propogate\" -> \"propagate\".";
        readonly created_at: "2024-11-27T10:10:00.000Z";
    }, {
        readonly id: "c-seg-2";
        readonly post_id: "post-segtree-visual";
        readonly parent_id: "c-seg-1";
        readonly author_id: "user-tourist";
        readonly body: "Fixed! Thanks. 🙏";
        readonly created_at: "2024-11-27T10:15:00.000Z";
    }, {
        readonly id: "c-seg-3";
        readonly post_id: "post-segtree-visual";
        readonly parent_id: null;
        readonly author_id: "user-kevin";
        readonly body: "Does this handle beatbeats? (Segment tree beats)";
        readonly created_at: "2024-11-27T10:30:00.000Z";
    }, {
        readonly id: "c-seg-4";
        readonly post_id: "post-segtree-visual";
        readonly parent_id: "c-seg-3";
        readonly author_id: "user-tourist";
        readonly body: "Not yet. Beats requires tracking min/max/second_max which is harder to visualize cleanly.";
        readonly created_at: "2024-11-27T10:45:00.000Z";
    }, {
        readonly id: "c-seg-5";
        readonly post_id: "post-segtree-visual";
        readonly parent_id: "c-seg-3";
        readonly author_id: "user-max";
        readonly body: "Check out JiDriver's blog for beats visuals.";
        readonly created_at: "2024-11-27T11:00:00.000Z";
    }];
    readonly forum_quick_filters: readonly [{
        readonly id: "filter-new";
        readonly label: "New";
        readonly value: "new";
    }, {
        readonly id: "filter-top";
        readonly label: "Top";
        readonly value: "top";
    }, {
        readonly id: "filter-hot";
        readonly label: "Hot";
        readonly value: "hot";
    }];
};
export default data;
