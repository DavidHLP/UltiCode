import { Entity, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { Problem } from './problem.entity';
import { ProblemTag } from './problem-tag.entity';

@Entity('problem_tag_relations')
export class ProblemTagRelation {
  @PrimaryColumn({ type: 'bigint' })
  problem_id: number;

  @PrimaryColumn({ length: 40 })
  tag_id: string;

  @ManyToOne(() => Problem, (problem) => problem.tagRelations)
  @JoinColumn({ name: 'problem_id' })
  problem: Problem;

  @ManyToOne(() => ProblemTag, (tag) => tag.problemRelations)
  @JoinColumn({ name: 'tag_id' })
  tag: ProblemTag;
}
