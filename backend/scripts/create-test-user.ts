
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  let user = await prisma.user.findUnique({ where: { id: 'user-1' } });
  if (!user) {
    console.log('User user-1 not found. Creating...');
    user = await prisma.user.create({
      data: {
        id: 'user-1',
        username: 'testuser',
        name: 'Test User',
        email: 'test@example.com',
        avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=testuser',
      },
    });
    console.log('User user-1 created.');
  } else {
    console.log('User user-1 already exists.');
  }

  // Also ensure comment-001 exists if needed, or check what comments exist
  const comments = await prisma.solutionComment.findMany({ select: { id: true }, take: 5 });
  console.log('Existing comments:', comments);
}

main()
  .catch((e) => console.error(e))
  .finally(async () => {
    await prisma.$disconnect();
  });
