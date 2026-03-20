"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@prisma/client");
const prisma = new client_1.PrismaClient();
async function main() {
    const tags = await prisma.problemTag.findMany();
    console.log(`Found ${tags.length} tags`);
    const slugify = (text) => text.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)+/g, '');
    for (const tag of tags) {
        const slug = slugify(tag.label);
    }
}
main().catch(e => console.error(e)).finally(() => prisma.$disconnect());
//# sourceMappingURL=check_tags.js.map