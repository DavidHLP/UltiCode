import {
  Entity,
  PrimaryColumn,
  Column,
  ManyToOne,
  JoinColumn,
  CreateDateColumn,
} from 'typeorm';
import { ProblemList } from './problem-list.entity';
import { Problem } from '../problem/problem.entity';

@Entity('problem_list_problem_relations')
export class ProblemListProblemRelation {
  @PrimaryColumn({ length: 40 })
  list_id: string;

  @PrimaryColumn({ type: 'bigint' })
  problem_id: number;

  @Column({ type: 'int', default: 0 })
  sort_order: number;

  @CreateDateColumn()
  added_at: Date;

  @ManyToOne(() => ProblemList, (list) => list.problemRelations, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'list_id' })
  list: ProblemList;

  @ManyToOne(() => Problem, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'problem_id' })
  problem: Problem;
}
