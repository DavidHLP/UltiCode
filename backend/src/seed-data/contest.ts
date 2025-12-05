const data = {
  // Competitions
  contests: [
    {
      id: 'contest-weekly-477',
      title: 'Weekly Contest 477',
      slug: 'weekly-contest-477',
      contest_type: 'weekly',
      start_time: '2025-11-23T02:30:00Z',
      duration_minutes: 90,
      status: 'upcoming',
      registered_count: 3245,
      participant_count: 0,
      is_rated: true,
      description: 'Join this weekly contest to test your skills.',
      cover_image: 'https://assets.leetcode.cn/aliyun-lc-upload/contest-config/contest/wc_card_img.png',
    },
    {
      id: 'contest-biweekly-170',
      title: 'Biweekly Contest 170',
      slug: 'biweekly-contest-170',
      contest_type: 'biweekly',
      start_time: '2025-11-22T14:30:00Z',
      duration_minutes: 90,
      status: 'finished',
      registered_count: 2876,
      participant_count: 2654,
      is_rated: true,
      description: 'Biweekly contest with increasing difficulty.',
      cover_image: 'https://assets.leetcode.cn/aliyun-lc-upload/contest-config/contest/bc_card_img.png',
    },
  ],

  // Contest Problems (Only Problem 1 exists now)
  contest_problems: [
    {
      id: 'cp-477-1',
      contest_id: 'contest-weekly-477',
      problem_id: 1,
      problem_index: 'Q1',
      score: 3,
      solved_count: 0,
      submission_count: 0,
    },
    {
      id: 'cp-170-1',
      contest_id: 'contest-biweekly-170',
      problem_id: 1,
      problem_index: 'Q1',
      score: 3,
      solved_count: 2150,
      submission_count: 2650,
    },
  ],

  contest_participants: [],
  contest_rankings: [],
  global_rankings: [],
} as const;

export default data;
