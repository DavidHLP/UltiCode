import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { DataSource } from 'typeorm';
import { User } from './user/user.entity';
import { Problem } from './problem/problem.entity';
import { ProblemDetail } from './problem/problem-detail.entity';
import { ProblemTag } from './problem/problem-tag.entity';
import { ProblemTagRelation } from './problem/problem-tag-relation.entity';
import { ProblemLanguage } from './problem/problem-language.entity';
import { ProblemExample } from './problem/problem-example.entity';
import { Contest } from './contest/contest.entity';
import { ContestProblem } from './contest/contest-problem.entity';
import { ForumPost } from './forum/forum-post.entity';
import { ForumCommunity } from './forum/forum-community.entity';
import { ForumUser } from './forum/forum-user.entity';
import { ForumComment } from './forum/forum-comment.entity';
import { ProblemListGroup } from './problem-list/problem-list-group.entity';
import { ProblemList } from './problem-list/problem-list.entity';

// Import data
import userData from './seed-data/user';
import problemsData from './seed-data/problems';
import problemDetailsData from './seed-data/problem-detail';
import contestsData from './seed-data/contest';
import forumData from './seed-data/forum';
import problemListsData from './seed-data/problem-lists';

type ProblemCompanySeed = string | { id: string; name: string; logo?: string };
type ProblemCompanyNormalized = { id: string; name: string; logo?: string };

async function bootstrap() {
  const app = await NestFactory.createApplicationContext(AppModule);
  const dataSource = app.get(DataSource);

  console.log('Seeding data...');

  // Reset database
  await dataSource.synchronize(true);
  console.log('Database synced (cleared)');

  // Users
  const userRepository = dataSource.getRepository(User);
  const users = userData.users.map((u) => ({ ...u }));
  await userRepository.save(users);
  console.log('Users seeded');

  // Problems
  const problemRepository = dataSource.getRepository(Problem);
  const problems = problemsData.problems.map((p) => ({
    ...p,
    completed_time:
      'completed_time' in p && p.completed_time
        ? new Date(p.completed_time as string)
        : undefined,
  }));
  await problemRepository.save(problems);
  console.log('Problems seeded');

  // Problem Details
  const problemDetailRepository = dataSource.getRepository(ProblemDetail);
  const details = problemDetailsData.problem_details.map((pd) => ({
    ...pd,
    companies: Array.isArray(pd.companies)
      ? (pd.companies as ProblemCompanySeed[]).map<ProblemCompanyNormalized>(
          (company) =>
            typeof company === 'string'
              ? {
                  id: company.toLowerCase().replace(/\s+/g, '-'),
                  name: company,
                }
              : { ...company },
        )
      : null,
    constraints_json: Array.isArray(pd.constraints_json)
      ? [...pd.constraints_json]
      : [],
    updated_at: new Date(pd.updated_at),
    hints:
      pd.id === 'pd-two-sum'
        ? [
            'A brute force approach is simple. Loop through each element x and find if there is another value that equals to target – x.',
            'So, if we fix one of the numbers, say x, we have to scan the entire array to find the next number y which is value - x where value is the input parameter. Can we change our array somehow so that this search becomes faster?',
            'The second train of thought is, without changing the array, can we use additional space to somehow make the search faster? This is where a hash map comes in handy.',
          ]
        : [],
  }));
  await problemDetailRepository.save(details);
  console.log('Problem Details seeded');

  // Problem Tags
  const problemTagRepository = dataSource.getRepository(ProblemTag);
  await problemTagRepository.save(
    problemsData.problem_tags.map((tag) => ({ ...tag })),
  );
  console.log('Problem Tags seeded');

  // Problem Tag Relations
  const problemTagRelationRepository =
    dataSource.getRepository(ProblemTagRelation);
  await problemTagRelationRepository.save(
    problemsData.problem_tag_relations.map((rel) => ({ ...rel })),
  );
  console.log('Problem Tag Relations seeded');

  const problemLanguageRepository = dataSource.getRepository(ProblemLanguage);
  await problemLanguageRepository.save(
    problemDetailsData.problem_languages.map((lang) => ({ ...lang })),
  );
  console.log('Problem Languages seeded');

  // Problem Examples
  const problemExampleRepository = dataSource.getRepository(ProblemExample);
  await problemExampleRepository.save(
    problemDetailsData.problem_examples.map((ex) => {
      const inputs: { name: string; value: string }[] = [];

      // Simple parsing: key = value, key2 = value2
      const inputText = ex.input_text;

      // Regex to find "name =" pattern
      const assignRegex = /([a-zA-Z0-9_]+)\s*=\s*/g;
      let match: RegExpExecArray | null;

      const pairs: { name: string; valueStart: number; valueEnd?: number }[] =
        [];

      while ((match = assignRegex.exec(inputText)) !== null) {
        if (pairs.length > 0) {
          pairs[pairs.length - 1].valueEnd = match.index;
        }
        pairs.push({ name: match[1], valueStart: assignRegex.lastIndex });
      }

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

      return {
        ...ex,
        inputText: ex.input_text,
        outputText: ex.output_text,
        inputs: inputs.length ? inputs : undefined,
      };
    }),
  );
  console.log('Problem Examples seeded');

  // Contests
  const contestRepository = dataSource.getRepository(Contest);
  const contests = contestsData.contests.map((c) => ({
    ...c,
    start_time: new Date(c.start_time),
  }));
  await contestRepository.save(contests);
  console.log('Contests seeded');

  // Contest Problems
  const contestProblemRepository = dataSource.getRepository(ContestProblem);
  await contestProblemRepository.save(
    contestsData.contest_problems.map((cp) => ({ ...cp })),
  );
  console.log('Contest Problems seeded');

  // Forum Communities
  const forumCommunityRepository = dataSource.getRepository(ForumCommunity);
  await forumCommunityRepository.save(
    forumData.forum_communities.map((community) => ({ ...community })),
  );
  console.log('Forum Communities seeded');

  // Forum Users
  const forumUserRepository = dataSource.getRepository(ForumUser);
  await forumUserRepository.save(
    forumData.forum_users.map((user) => ({ ...user })),
  );
  console.log('Forum Users seeded');

  // Forum Posts
  const forumPostRepository = dataSource.getRepository(ForumPost);
  const posts = forumData.forum_posts.map((fp) => ({
    ...fp,
    tags: Array.isArray(fp.tags) ? [...fp.tags] : [],
    created_at: new Date(fp.created_at),
  }));
  await forumPostRepository.save(posts);
  console.log('Forum Posts seeded');

  // Forum Comments
  const forumCommentRepository = dataSource.getRepository(ForumComment);
  const comments = forumData.forum_comments.map((fc) => ({
    ...fc,
    created_at: new Date(fc.created_at),
    parent_id: fc.parent_id ?? undefined,
    edited_at:
      'edited_at' in fc && fc.edited_at
        ? new Date(fc.edited_at as string)
        : undefined,
  }));
  await forumCommentRepository.save(comments);
  console.log('Forum Comments seeded');

  // Problem List Groups
  const problemListGroupRepository = dataSource.getRepository(ProblemListGroup);
  await problemListGroupRepository.save(
    problemListsData.problem_list_groups.map((group) => ({ ...group })),
  );
  console.log('Problem List Groups seeded');

  // Problem Lists
  const problemListRepository = dataSource.getRepository(ProblemList);
  const lists = problemListsData.problem_lists.map((pl) => ({
    ...pl,
    created_at: new Date(pl.created_at),
    updated_at: new Date(pl.updated_at),
  }));
  await problemListRepository.save(lists);
  console.log('Problem Lists seeded');

  console.log('Seeding complete!');
  await app.close();
}

void bootstrap();
