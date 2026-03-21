package com.ulticode.modules.websocket.dto;

/** Response for join/leave contest operations. */
public record ContestRoomResponse(
    boolean success, String contestId, String message, String error) {

  /**
   * Create a success response.
   *
   * @param contestId the contest ID
   * @param message the success message
   * @return success response
   */
  public static ContestRoomResponse success(String contestId, String message) {
    return new ContestRoomResponse(true, contestId, message, null);
  }

  /**
   * Create an error response.
   *
   * @param contestId the contest ID
   * @param error the error code
   * @param message the error message
   * @return error response
   */
  public static ContestRoomResponse error(String contestId, String error, String message) {
    return new ContestRoomResponse(false, contestId, message, error);
  }
}
