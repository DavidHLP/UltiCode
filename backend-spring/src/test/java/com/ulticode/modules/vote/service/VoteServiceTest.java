package com.ulticode.modules.vote.service;

import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.vote.service.impl.VoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VoteService.
 */
@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock
    private EdgeOperationMapper edgeOperationMapper;

    @InjectMocks
    private VoteServiceImpl voteService;

    private static final String USER_ID = "test-user-id";
    private static final String TARGET_ID = "test-target-id";
    private static final EdgeOperationTargetType TARGET_TYPE = EdgeOperationTargetType.PROBLEM;

    private VoteDTO voteDTO;

    @BeforeEach
    void setUp() {
        voteDTO = new VoteDTO();
        voteDTO.setTargetId(TARGET_ID);
        voteDTO.setTargetType(TARGET_TYPE);
    }

    @Nested
    @DisplayName("vote - Upvote Tests")
    class UpvoteTests {

        @Test
        @DisplayName("should add upvote when no existing vote")
        void shouldAddUpvoteWhenNoExistingVote() {
            // Arrange
            voteDTO.setValue(1);
            // Initial check: no existing votes (vote() method checks both)
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0); // First call in vote()
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0); // First call in vote()

            // getVoteStatus() checks again after vote
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            // Reset exists for getVoteStatus calls
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0) // First call in vote()
                    .thenReturn(1); // Call in getVoteStatus (user now has upvote)
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0) // First call in vote()
                    .thenReturn(0); // Call in getVoteStatus (no downvote)
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(TARGET_ID, result.getTargetId());
            assertEquals(TARGET_TYPE.getValue(), result.getTargetType());
            assertEquals(1L, result.getLikes());
            assertEquals(0L, result.getDislikes());
            assertEquals(1, result.getUserVote());
            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
            verify(edgeOperationMapper, never()).deleteByOperatorAndTarget(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should remove upvote when already upvoted (toggle off)")
        void shouldRemoveUpvoteWhenAlreadyUpvoted() {
            // Arrange
            voteDTO.setValue(1);
            // Initial check: already has upvote
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1) // First call in vote() - user has upvote
                    .thenReturn(0); // Call in getVoteStatus (after removal)
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0); // Both calls in vote() and getVoteStatus
            // Status check after removal: no votes
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(0L, result.getLikes());
            assertEquals(0, result.getUserVote());
            verify(edgeOperationMapper).deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue());
            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("should change downvote to upvote")
        void shouldChangeDownvoteToUpvote() {
            // Arrange
            voteDTO.setValue(1);
            // Initial check: no upvote, but has downvote
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0) // First call in vote()
                    .thenReturn(1); // Call in getVoteStatus (now has upvote)
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(1) // First call in vote()
                    .thenReturn(0); // Call in getVoteStatus (no downvote now)
            // Status check after changing
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getUserVote());
            verify(edgeOperationMapper).deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue());
            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
        }
    }

    @Nested
    @DisplayName("vote - Downvote Tests")
    class DownvoteTests {

        @Test
        @DisplayName("should add downvote when no existing vote")
        void shouldAddDownvoteWhenNoExistingVote() {
            // Arrange
            voteDTO.setValue(-1);
            // Initial check: no existing votes
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0) // First call in vote()
                    .thenReturn(0); // Call in getVoteStatus
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0) // First call in vote()
                    .thenReturn(1); // Call in getVoteStatus (now has downvote)
            // Status check after adding downvote
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(0L, result.getLikes());
            assertEquals(1L, result.getDislikes());
            assertEquals(-1, result.getUserVote());
            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("should remove downvote when already downvoted (toggle off)")
        void shouldRemoveDownvoteWhenAlreadyDownvoted() {
            // Arrange
            voteDTO.setValue(-1);
            // Initial check: already has downvote, no upvote
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0); // Both calls in vote() and getVoteStatus
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(1) // First call in vote() - user has downvote
                    .thenReturn(0); // Call in getVoteStatus (after removal)
            // Status check after removal
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getUserVote());
            verify(edgeOperationMapper).deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue());
            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("should change upvote to downvote")
        void shouldChangeUpvoteToDownvote() {
            // Arrange
            voteDTO.setValue(-1);
            // Initial check: has upvote, no downvote
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1) // First call in vote() - user has upvote
                    .thenReturn(0); // Call in getVoteStatus (after removal)
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0) // First call in vote()
                    .thenReturn(1); // Call in getVoteStatus (now has downvote)
            // Status check after changing
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(-1, result.getUserVote());
            verify(edgeOperationMapper).deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue());
            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
        }
    }

    @Nested
    @DisplayName("vote - Neutral Tests")
    class NeutralTests {

        @Test
        @DisplayName("should remove all votes when value is 0")
        void shouldRemoveAllVotesWhenValueIsZero() {
            // Arrange
            voteDTO.setValue(0);
            // Initial check: has upvote, no downvote
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1) // First call in vote() - user has upvote
                    .thenReturn(0); // Call in getVoteStatus (after removal)
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0); // Both calls in vote() and getVoteStatus
            // Status check after removal
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getUserVote());
            verify(edgeOperationMapper).deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue());
            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("should do nothing when no existing vote and value is 0")
        void shouldDoNothingWhenNoExistingVoteAndValueIsZero() {
            // Arrange
            voteDTO.setValue(0);
            // No existing votes
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertNotNull(result);
            assertEquals(0, result.getUserVote());
            verify(edgeOperationMapper, never()).deleteByOperatorAndTarget(anyString(), anyString(), anyString(), anyString());
            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
        }
    }

    @Nested
    @DisplayName("vote - Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("should throw exception for invalid vote value")
        void shouldThrowExceptionForInvalidVoteValue() {
            // Arrange
            voteDTO.setValue(2);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> voteService.vote(USER_ID, voteDTO));
        }

        @Test
        @DisplayName("should throw exception for null vote value")
        void shouldThrowExceptionForNullVoteValue() {
            // Arrange
            voteDTO.setValue(null);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> voteService.vote(USER_ID, voteDTO));
        }
    }

    @Nested
    @DisplayName("getVoteStatus Tests")
    class GetVoteStatusTests {

        @Test
        @DisplayName("should return correct vote status with user upvote")
        void shouldReturnCorrectVoteStatusWithUserUpvote() {
            // Arrange
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(5);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(2);
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);

            // Act
            VoteResultVO result = voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE);

            // Assert
            assertNotNull(result);
            assertEquals(TARGET_ID, result.getTargetId());
            assertEquals(TARGET_TYPE.getValue(), result.getTargetType());
            assertEquals(5L, result.getLikes());
            assertEquals(2L, result.getDislikes());
            assertEquals(1, result.getUserVote());
        }

        @Test
        @DisplayName("should return correct vote status with user downvote")
        void shouldReturnCorrectVoteStatusWithUserDownvote() {
            // Arrange
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(5);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(2);
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(1);

            // Act
            VoteResultVO result = voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE);

            // Assert
            assertEquals(-1, result.getUserVote());
        }

        @Test
        @DisplayName("should return zero user vote when user has not voted")
        void shouldReturnZeroUserVoteWhenUserHasNotVoted() {
            // Arrange
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(5);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(2);
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);

            // Act
            VoteResultVO result = voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE);

            // Assert
            assertEquals(0, result.getUserVote());
        }

        @Test
        @DisplayName("should return zero user vote when userId is null (anonymous)")
        void shouldReturnZeroUserVoteWhenUserIdIsNull() {
            // Arrange
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(5);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(2);

            // Act
            VoteResultVO result = voteService.getVoteStatus(null, TARGET_ID, TARGET_TYPE);

            // Assert
            assertEquals(0, result.getUserVote());
            verify(edgeOperationMapper, never()).existsByOperatorAndTarget(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("EdgeOperation Entity Creation Tests")
    class EntityCreationTests {

        @Test
        @DisplayName("should create EdgeOperation with correct fields on upvote")
        void shouldCreateEdgeOperationWithCorrectFieldsOnUpvote() {
            // Arrange
            voteDTO.setValue(1);
            ArgumentCaptor<EdgeOperation> captor = ArgumentCaptor.forClass(EdgeOperation.class);

            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(0)
                    .thenReturn(1); // Second call in getVoteStatus
            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.countByTargetAndOperation(TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VOTE_DOWN.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.insert(captor.capture())).thenReturn(1);

            // Act
            voteService.vote(USER_ID, voteDTO);

            // Assert
            EdgeOperation savedOperation = captor.getValue();
            assertEquals(TARGET_ID, savedOperation.getTargetId());
            assertEquals(TARGET_TYPE, savedOperation.getTargetType());
            assertEquals(USER_ID, savedOperation.getOperatorId());
            assertEquals(EdgeOperationType.VOTE_UP, savedOperation.getOperationType());
        }
    }

    @Nested
    @DisplayName("Different Target Types Tests")
    class DifferentTargetTypesTests {

        @Test
        @DisplayName("should handle SOLUTION target type")
        void shouldHandleSolutionTargetType() {
            // Arrange
            voteDTO.setTargetType(EdgeOperationTargetType.SOLUTION);
            voteDTO.setValue(1);

            when(edgeOperationMapper.existsByOperatorAndTarget(anyString(), anyString(), eq(EdgeOperationTargetType.SOLUTION.getValue()), anyString()))
                    .thenReturn(0)
                    .thenReturn(1); // Second call in getVoteStatus
            when(edgeOperationMapper.countByTargetAndOperation(anyString(), eq(EdgeOperationTargetType.SOLUTION.getValue()), anyString()))
                    .thenReturn(1);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertEquals(EdgeOperationTargetType.SOLUTION.getValue(), result.getTargetType());
        }

        @Test
        @DisplayName("should handle POST target type")
        void shouldHandlePostTargetType() {
            // Arrange
            voteDTO.setTargetType(EdgeOperationTargetType.POST);
            voteDTO.setValue(1);

            when(edgeOperationMapper.existsByOperatorAndTarget(anyString(), anyString(), eq(EdgeOperationTargetType.POST.getValue()), anyString()))
                    .thenReturn(0)
                    .thenReturn(1); // Second call in getVoteStatus
            when(edgeOperationMapper.countByTargetAndOperation(anyString(), eq(EdgeOperationTargetType.POST.getValue()), anyString()))
                    .thenReturn(1);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertEquals(EdgeOperationTargetType.POST.getValue(), result.getTargetType());
        }

        @Test
        @DisplayName("should handle COMMENT target type")
        void shouldHandleCommentTargetType() {
            // Arrange
            voteDTO.setTargetType(EdgeOperationTargetType.COMMENT);
            voteDTO.setValue(1);

            when(edgeOperationMapper.existsByOperatorAndTarget(anyString(), anyString(), eq(EdgeOperationTargetType.COMMENT.getValue()), anyString()))
                    .thenReturn(0)
                    .thenReturn(1); // Second call in getVoteStatus
            when(edgeOperationMapper.countByTargetAndOperation(anyString(), eq(EdgeOperationTargetType.COMMENT.getValue()), anyString()))
                    .thenReturn(1);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            VoteResultVO result = voteService.vote(USER_ID, voteDTO);

            // Assert
            assertEquals(EdgeOperationTargetType.COMMENT.getValue(), result.getTargetType());
        }
    }
}
