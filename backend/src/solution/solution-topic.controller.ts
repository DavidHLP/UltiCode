import { Controller, Get } from '@nestjs/common';

// I'll define the interface locally or just return object.

@Controller('solution-topics')
export class SolutionTopicController {
  @Get()
  findAll() {
    return {
      topics: [
        { id: 'algorithms', name: 'Algorithms', count: 100 },
        { id: 'database', name: 'Database', count: 20 },
        { id: 'shell', name: 'Shell', count: 10 },
        { id: 'concurrency', name: 'Concurrency', count: 5 },
        { id: 'system-design', name: 'System Design', count: 15 },
        { id: 'javascript', name: 'JavaScript', count: 50 },
        { id: 'python', name: 'Python', count: 60 },
      ],
    };
  }
}
