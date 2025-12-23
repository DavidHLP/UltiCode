// prisma/seed/data/view.data.ts
import { USER_IDS } from './users.data';
// We don't have explicit IDs exported for solutions/posts in their data files usually,
// but let's assume some common IDs or string literals if we can't import variables.
// Actually, solution IDs are usually UUIDs generated. This is tricky.
// If solution IDs are not static in seed data, we can't seed views reliably here.
// Let's check `solutions.data.ts`.
// For now, I will leave the array empty but define the structure, OR
// I will check if I can import solution IDs.
// If `solutions.data.ts` exports IDs, I'll use them. Otherwise, I'll skip seeding views for now or use hardcoded IDs if they are hardcoded there.

type ViewTargetType = 'SOLUTION' | 'FORUM_POST';

export interface ViewSeedData {
  target_id: string;
  target_type: ViewTargetType;
  user_id?: string;
  ip?: string;
}

const data = {
  views: [] as ViewSeedData[],
};

export default data;
