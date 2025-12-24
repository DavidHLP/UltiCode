import {
  Entity,
  Column,
  PrimaryGeneratedColumn,
  ManyToOne,
  JoinColumn,
  Index,
} from 'typeorm';
import { ProblemList } from './problem-list.entity';
import { UserProblemListCategory } from './user-problem-list-category.entity';

@Entity('user_problem_list_saves')
@Index(['user_id'])
export class UserProblemListSave {
  @PrimaryGeneratedColumn('uuid', { name: 'id' })
  id: string;

  @Column({ length: 40 })
  user_id: string;

  @Column({ length: 50 })
  list_id: string;

  @Column({ length: 40, nullable: true })
  category_id: string | null;

  @Column({ type: 'datetime', default: () => 'CURRENT_TIMESTAMP' })
  saved_at: Date;

  @ManyToOne(() => ProblemList, (list) => list.savedByUsers, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'list_id' })
  list: ProblemList;

  @ManyToOne(() => UserProblemListCategory, (category) => category.savedLists, {
    onDelete: 'SET NULL',
    nullable: true,
  })
  @JoinColumn({ name: 'category_id' })
  category: UserProblemListCategory | null;
}
