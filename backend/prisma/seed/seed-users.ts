import type { PrismaClient, User } from '@prisma/client';
import userData from '../../src/seed-data/user';

export async function clearUsers(prisma: PrismaClient): Promise<void> {
  await prisma.user.deleteMany();
}

export async function seedUsers(prisma: PrismaClient): Promise<User[]> {
  const users: User[] = [];

  for (const u of userData.users) {
    const user = await prisma.user.create({
      data: {
        id: u.id,
        username: u.username,
        name: u.name ?? null,
        email: u.email ?? null,
        avatar: u.avatar ?? null,
      },
    });
    users.push(user);
  }

  return users;
}
