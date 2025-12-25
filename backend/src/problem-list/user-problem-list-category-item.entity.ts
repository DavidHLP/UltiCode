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

@Entity('user_problem_list_category_items')
@Index(['user_id'])
@Index(['user_id', 'category_id'])
export class UserProblemListCategoryItem {
  @PrimaryGeneratedColumn('uuid', { name: 'id' })
  id: string;

  @Column({ length: 40 })
  user_id: string;

  @Column({ length: 50 })
  list_id: string;

  @Column({ length: 40 })
  category_id: string;

  @Column({ type: 'datetime', default: () => 'CURRENT_TIMESTAMP' })
  created_at: Date;

  @ManyToOne(() => ProblemList, (list) => list.categoryItems, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'list_id' })
  list: ProblemList;

  @ManyToOne(() => UserProblemListCategory, (category) => category.items, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'category_id' })
  category: UserProblemListCategory;
}
