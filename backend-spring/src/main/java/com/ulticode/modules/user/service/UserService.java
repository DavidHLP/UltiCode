package com.ulticode.modules.user.service;

import com.ulticode.common.constants.ErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Service for user-related operations. */
@Service
public class UserService {

  private final UserMapper userMapper;

  public UserService(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  /**
   * Find a user by ID.
   *
   * @param id the user ID
   * @return Optional containing the user if found
   */
  public Optional<User> findById(String id) {
    return userMapper.findById(id);
  }

  /**
   * Find a user by ID or throw exception.
   *
   * @param id the user ID
   * @return the user
   * @throws BusinessException if user not found
   */
  public User findByIdOrThrow(String id) {
    return findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  /**
   * Find a user by username.
   *
   * @param username the username
   * @return Optional containing the user if found
   */
  public Optional<User> findByUsername(String username) {
    return userMapper.findByUsername(username);
  }

  /**
   * Find a user by email.
   *
   * @param email the email
   * @return Optional containing the user if found
   */
  public Optional<User> findByEmail(String email) {
    return userMapper.findByEmail(email);
  }

  /**
   * Check if a user exists by ID.
   *
   * @param id the user ID
   * @return true if user exists
   */
  public boolean existsById(String id) {
    return userMapper.existsById(id);
  }
}
