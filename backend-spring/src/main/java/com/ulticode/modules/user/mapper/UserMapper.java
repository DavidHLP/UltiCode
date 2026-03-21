package com.ulticode.modules.user.mapper;

import com.ulticode.modules.user.entity.User;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis mapper for User entity. */
@Mapper
public interface UserMapper {

  /**
   * Find a user by ID.
   *
   * @param id the user ID
   * @return Optional containing the user if found
   */
  @Select(
      "SELECT id, username, email, password, avatar, bio, role, rating, "
          + "joined_at AS joinedAt, updated_at AS updatedAt "
          + "FROM users WHERE id = #{id}")
  Optional<User> findById(@Param("id") String id);

  /**
   * Find a user by username.
   *
   * @param username the username
   * @return Optional containing the user if found
   */
  @Select(
      "SELECT id, username, email, password, avatar, bio, role, rating, "
          + "joined_at AS joinedAt, updated_at AS updatedAt "
          + "FROM users WHERE username = #{username}")
  Optional<User> findByUsername(@Param("username") String username);

  /**
   * Find a user by email.
   *
   * @param email the email
   * @return Optional containing the user if found
   */
  @Select(
      "SELECT id, username, email, password, avatar, bio, role, rating, "
          + "joined_at AS joinedAt, updated_at AS updatedAt "
          + "FROM users WHERE email = #{email}")
  Optional<User> findByEmail(@Param("email") String email);

  /**
   * Check if a user exists by ID.
   *
   * @param id the user ID
   * @return true if user exists
   */
  @Select("SELECT COUNT(*) > 0 FROM users WHERE id = #{id}")
  boolean existsById(@Param("id") String id);
}
