import type { PrismaClient, Difficulty, ProblemStatus } from '@prisma/client';
import problemsData from './data/problems.data';
import problemDetailsData from './data/problem-details.data';

type ProblemCompanySeed = string | { id: string; name: string; logo?: string };
type ProblemCompanyNormalized = { id: string; name: string; logo?: string };

export async function clearProblems(prisma: PrismaClient): Promise<void> {
  // 子表在前，父表在后
  await prisma.problemExample.deleteMany();
  await prisma.problemLanguage.deleteMany();
  await prisma.problemTagRelation.deleteMany();
  await prisma.problemDetail.deleteMany();
  await prisma.problemTag.deleteMany();
  await prisma.problem.deleteMany();
}

export interface SeedProblemsResult {
  count: number;
  problemIds: number[];
}

export async function seedProblems(prisma: PrismaClient): Promise<SeedProblemsResult> {
  // Seed problems
  for (const p of problemsData.problems) {
    await prisma.problem.create({
      data: {
        id: BigInt(p.id),
        slug: p.slug,
        title: p.title,
        difficulty: p.difficulty as Difficulty,
        acceptance_rate: p.acceptance_rate,
        status: p.status as ProblemStatus,
        is_premium: p.is_premium,
        has_solution: p.has_solution,
        completed_time: p.completed_time ? new Date(p.completed_time) : null,
      },
    });
  }

  // Seed problem tags
  for (const tag of problemsData.problem_tags) {
    await prisma.problemTag.create({
      data: {
        id: tag.id,
        label: tag.label,
      },
    });
  }

  // Seed tag relations
  for (const rel of problemsData.problem_tag_relations) {
    await prisma.problemTagRelation.create({
      data: {
        problem_id: BigInt(rel.problem_id),
        tag_id: rel.tag_id,
      },
    });
  }

  // Seed problem details
  for (const pd of problemDetailsData.problem_details) {
    const companies = Array.isArray(pd.companies)
      ? (pd.companies as ProblemCompanySeed[]).map<ProblemCompanyNormalized>((company) =>
          typeof company === 'string'
            ? { id: company.toLowerCase().replace(/\s+/g, '-'), name: company }
            : { ...company }
        )
      : null;

    await prisma.problemDetail.create({
      data: {
        id: pd.id,
        problem_id: BigInt(pd.problem_id),
        slug: pd.slug,
        summary: pd.summary,
        companies: companies,
        likes: pd.id === 'pd-two-sum' ? 54300 : 0,
        dislikes: pd.id === 'pd-two-sum' ? 1800 : 0,
        difficulty_rating: pd.difficulty_rating,
        updated_at: new Date(pd.updated_at),
        follow_up: pd.follow_up ?? null,
        constraints_json: pd.constraints_json,
        hints: pd.id === 'pd-two-sum' ? [
          'A brute force approach is simple. Loop through each element x and find if there is another value that equals to target – x.',
          'So, if we fix one of the numbers, say x, we have to scan the entire array to find the next number y which is value - x where value is the input parameter. Can we change our array somehow so that this search becomes faster?',
          'The second train of thought is, without changing the array, can we use additional space to somehow make the search faster? This is where a hash map comes in handy.',
        ] : null,
      },
    });
  }

  // Seed problem examples
  for (const ex of problemDetailsData.problem_examples) {
    // Parse inputs
    const inputText = ex.input_text;
    const assignRegex = /([a-zA-Z0-9_]+)\s*=\s*/g;
    let match: RegExpExecArray | null;
    const pairs: { name: string; valueStart: number; valueEnd?: number }[] = [];

    while ((match = assignRegex.exec(inputText)) !== null) {
      if (pairs.length > 0) {
        pairs[pairs.length - 1].valueEnd = match.index;
      }
      pairs.push({ name: match[1], valueStart: assignRegex.lastIndex });
    }

    const inputs: { name: string; value: string }[] = [];
    if (pairs.length > 0) {
      pairs[pairs.length - 1].valueEnd = inputText.length;
      for (const pair of pairs) {
        let val = inputText.slice(pair.valueStart, pair.valueEnd).trim();
        if (val.endsWith(',')) {
          val = val.slice(0, -1).trim();
        }
        inputs.push({ name: pair.name, value: val });
      }
    }

    await prisma.problemExample.create({
      data: {
        id: ex.id,
        problem_id: BigInt(ex.problem_id),
        example_order: ex.example_order,
        input_text: ex.input_text,
        output_text: ex.output_text,
        explanation: ex.explanation ?? null,
        inputs: inputs.length ? inputs : null,
      },
    });
  }

  // Seed problem languages
  for (const lang of problemDetailsData.problem_languages) {
    await prisma.problemLanguage.create({
      data: {
        id: lang.id,
        problem_id: BigInt(lang.problem_id),
        label: lang.label,
        value: lang.value,
        starter_code: lang.starterCode,
      },
    });
  }

  return {
    count: problemsData.problems.length,
    problemIds: problemsData.problems.map((p) => p.id),
  };
}
