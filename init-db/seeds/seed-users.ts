import type { PrismaClient, User } from '@prisma/client';
import * as bcrypt from 'bcrypt';
import usersData from './data/users.data';

export async function clearUsers(prisma: PrismaClient): Promise<void> {
  await prisma.user.deleteMany();
}

export async function seedUsers(prisma: PrismaClient): Promise<User[]> {
  const users: User[] = [];

  for (const u of usersData.users) {
    const password = u.password
      ? u.password.startsWith('$2a$') ||
        u.password.startsWith('$2b$') ||
        u.password.startsWith('$2y$')
        ? u.password
        : await bcrypt.hash(u.password, 10)
      : null;

    const user = await prisma.user.create({
      data: {
        id: u.id,
        username: u.username,
        name: u.name ?? null,
        email: u.email ?? null,
        avatar: u.avatar ?? null,
        password,
        bio: (u as any).bio ?? null,
        company: (u as any).company ?? null,
        github: (u as any).github ?? null,
        location: (u as any).location ?? null,
        twitter: (u as any).twitter ?? null,
        website: (u as any).website ?? null,
        preferred_language: (u as any).preferred_language ?? null,
        last_login_at: (u as any).last_login_at ?? null,
      },
    });
    users.push(user);
  }

  return users;
}
