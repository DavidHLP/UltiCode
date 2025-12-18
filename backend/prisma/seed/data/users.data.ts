// prisma/seed/data/users.data.ts
import * as crypto from 'crypto';

// 密码哈希函数（与 AuthService 中的实现保持一致）
function hashPassword(password: string): string {
  return crypto.createHash('sha256').update(password).digest('hex');
}

// 默认测试密码
const DEFAULT_PASSWORD = hashPassword('password123');

export const USER_IDS = {
  SHADCN: 'u-001',
  STACK_UNWIND: 'u-002',
  YUKI: 'user-yuki',
  ALEX: 'user-alex',
  CHEN: 'user-chen',
  MAX: 'user-max',
  SARA: 'user-sara',
  TOM: 'user-tom',
  LILY: 'user-lily',
  DAVID: 'user-david',
  EMMA: 'user-emma',
  KEVIN: 'user-kevin',
  TOURIST: 'user-tourist',
  JIANGLY: 'user-jiangly',
  BENQ: 'user-benq',
  ECNERWALA: 'user-ecnerwala',
  UM_NIK: 'user-um_nik',
  SCOTT: 'user-scott',
  PETR: 'user-petr',
} as const;

export const USER_USERNAMES = {
  SHADCN: 'shadcn',
  STACK_UNWIND: 'stack_unwind',
  YUKI: 'yuki_codes',
  ALEX: 'alex_algorithm',
  CHEN: 'chen_master',
  MAX: 'max_coder',
  SARA: 'sara_dev',
  TOM: 'tom_quick',
  LILY: 'lily_codes',
  DAVID: 'david_algo',
  EMMA: 'emma_swift',
  KEVIN: 'kevin_pro',
  TOURIST: 'tourist',
  JIANGLY: 'jiangly',
  BENQ: 'Benq',
  ECNERWALA: 'ecnerwala',
  UM_NIK: 'Um_nik',
  SCOTT: 'scott_wu',
  PETR: 'Petr',
} as const;

const data = {
  currentUserId: USER_IDS.SHADCN,
  users: [
    {
      id: USER_IDS.SHADCN,
      username: USER_USERNAMES.SHADCN,
      name: 'Shad',
      email: 'm@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=shadcn',
      password: DEFAULT_PASSWORD, // 默认密码: password123
    },
    {
      id: USER_IDS.STACK_UNWIND,
      username: USER_USERNAMES.STACK_UNWIND,
      name: 'Stack Unwind',
      email: 'su@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=stack_unwind',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.YUKI,
      username: USER_USERNAMES.YUKI,
      name: 'Yuki',
      email: 'yuki@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=yuki',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.ALEX,
      username: USER_USERNAMES.ALEX,
      name: 'Alex',
      email: 'alex@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=alex',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.CHEN,
      username: USER_USERNAMES.CHEN,
      name: 'Chen',
      email: 'chen@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=chen',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.MAX,
      username: USER_USERNAMES.MAX,
      name: 'Max',
      email: 'max@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=max',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.SARA,
      username: USER_USERNAMES.SARA,
      name: 'Sara',
      email: 'sara@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=sara',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.TOM,
      username: USER_USERNAMES.TOM,
      name: 'Tom',
      email: 'tom@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=tom',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.LILY,
      username: USER_USERNAMES.LILY,
      name: 'Lily',
      email: 'lily@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=lily',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.DAVID,
      username: USER_USERNAMES.DAVID,
      name: 'David',
      email: 'david@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=david',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.EMMA,
      username: USER_USERNAMES.EMMA,
      name: 'Emma',
      email: 'emma@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=emma',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.KEVIN,
      username: USER_USERNAMES.KEVIN,
      name: 'Kevin',
      email: 'kevin@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=kevin',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.TOURIST,
      username: USER_USERNAMES.TOURIST,
      name: 'Gennady',
      email: 'tourist@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=tourist',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.JIANGLY,
      username: USER_USERNAMES.JIANGLY,
      name: 'Jiang',
      email: 'jiangly@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=jiangly',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.BENQ,
      username: USER_USERNAMES.BENQ,
      name: 'Ben',
      email: 'ben@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=benq',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.ECNERWALA,
      username: USER_USERNAMES.ECNERWALA,
      name: 'Andrew',
      email: 'ecnerwala@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=ecnerwala',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.UM_NIK,
      username: USER_USERNAMES.UM_NIK,
      name: 'Nikolai',
      email: 'umnik@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=um_nik',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.SCOTT,
      username: USER_USERNAMES.SCOTT,
      name: 'Scott',
      email: 'scott@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=scott',
      password: DEFAULT_PASSWORD,
    },
    {
      id: USER_IDS.PETR,
      username: USER_USERNAMES.PETR,
      name: 'Petr',
      email: 'petr@example.com',
      avatar: 'https://api.dicebear.com/7.x/notionists/svg?seed=petr',
      password: DEFAULT_PASSWORD,
    },
  ],
} as const;

export default data;
