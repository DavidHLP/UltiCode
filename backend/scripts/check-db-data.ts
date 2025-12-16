
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  const user = await prisma.user.findUnique({ where: { id: 'user-1' } });
  console.log('User user-1:', user);

  const comment = await prisma.solutionComment.findUnique({ where: { id: 'comment-001' } });
  console.log('Comment comment-001:', comment);

  const allComments = await prisma.solutionComment.findMany({ take: 5 });
  console.log('All Comments Sample:', allComments);
}

main()
  .catch((e) => console.error(e))
  .finally(async () => {
    await prisma.$disconnect();
  });
