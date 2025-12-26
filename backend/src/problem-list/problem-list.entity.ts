import { Entity, Column, PrimaryColumn, OneToMany } from 'typeorm';
import { ProblemListProblemRelation } from './problem-list-problem-relation.entity';

@Entity('problem_lists')
export class ProblemList {
  @PrimaryColumn({ length: 50 })
  id: string;

  @Column({ length: 120 })
  name: string;

  @Column({ type: 'text', nullable: true })
  description: string;

  @Column({ length: 40 })
  author_id: string;

  @Column({ default: true })
  is_public: boolean;

  @Column({ default: false })
  is_featured: boolean;

  @Column({ length: 30, nullable: true })
  banner_tag: string;

  @Column({ length: 50, nullable: true })
  banner_icon: string;

  @Column({ length: 30, nullable: true })
  banner_theme: string;

  @Column({ default: 0 })
  banner_order: number;

  @Column()
  created_at: Date;

  @Column()
  updated_at: Date;

  @OneToMany(() => ProblemListProblemRelation, (relation) => relation.list)
  problemRelations: ProblemListProblemRelation[];
}
