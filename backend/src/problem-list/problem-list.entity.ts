import { Entity, Column, PrimaryColumn, ManyToOne, JoinColumn } from 'typeorm';
import { ProblemListGroup } from './problem-list-group.entity';

@Entity('problem_lists')
export class ProblemList {
  @PrimaryColumn({ length: 40 })
  id: string;

  @Column({ length: 40 })
  group_id: string;

  @ManyToOne(() => ProblemListGroup, (group) => group.lists)
  @JoinColumn({ name: 'group_id' })
  group: ProblemListGroup;

  @Column({ length: 120 })
  name: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ length: 40 })
  author_id: string;

  @Column({ default: true })
  is_public: boolean;

  @Column()
  created_at: Date;

  @Column()
  updated_at: Date;
}
