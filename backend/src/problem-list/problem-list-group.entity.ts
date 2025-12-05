import { Entity, Column, PrimaryColumn, OneToMany } from 'typeorm';
import { ProblemList } from './problem-list.entity';

@Entity('problem_list_groups')
export class ProblemListGroup {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 120 })
  name: string;

  @Column({ default: 0 })
  sort_order: number;

  @OneToMany(() => ProblemList, (list) => list.group)
  lists: ProblemList[];
}
