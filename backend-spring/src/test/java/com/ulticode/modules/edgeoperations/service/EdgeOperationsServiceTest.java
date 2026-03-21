package com.ulticode.modules.edgeoperations.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.bookmark.entity.Bookmark;
import com.ulticode.modules.bookmark.mapper.BookmarkMapper;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.edgeoperations.service.impl.EdgeOperationsServiceImpl;
import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.vote.service.VoteService;
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
 * Unit tests for EdgeOperationsService.
 */
@ExtendWith(MockitoExtension.class)
class EdgeOperationsServiceTest {

    @Mock
    private VoteService voteService;

    @Mock
    private EdgeOperationMapper edgeOperationMapper;

    @Mock
    private BookmarkMapper bookmarkMapper;

    @InjectMocks
    private EdgeOperationsServiceImpl edgeOperationsService;

    private static final String USER_ID = "test-user-id";
    private static final String TARGET_ID = "test-target-id";
    private static final EdgeOperationTargetType TARGET_TYPE = EdgeOperationTargetType.PROBLEM;

    private EdgeOperationDTO operationDTO;

    @BeforeEach
    void setUp() {
        operationDTO = new EdgeOperationDTO();
        operationDTO.setTargetId(TARGET_ID);
        operationDTO.setTargetType(TARGET_TYPE);
    }

    @Nested
    @DisplayName("performOperation - Vote Up Tests")
    class VoteUpTests {

        @Test
        @DisplayName("should delegate to VoteService for VOTE_UP operation")
        void shouldDelegateToVoteServiceForVoteUp() {
            // Arrange
            operationDTO.setOperationType(EdgeOperationType.VOTE_UP);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 10L, 2L, 1);
            when(voteService.vote(eq(USER_ID), any(VoteDTO.class))).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);
            assertEquals(10L, result.getLikes());
            assertEquals(2L, result.getDislikes());
            assertEquals(5L, result.getFavorites());
            assertEquals(1, result.getViewer().getVote());

            // Verify VoteService was called with correct parameters
            ArgumentCaptor<VoteDTO> voteDTOCaptor = ArgumentCaptor.forClass(VoteDTO.class);
            verify(voteService).vote(eq(USER_ID), voteDTOCaptor.capture());
            VoteDTO capturedVoteDTO = voteDTOCaptor.getValue();
            assertEquals(TARGET_ID, capturedVoteDTO.getTargetId());
            assertEquals(TARGET_TYPE, capturedVoteDTO.getTargetType());
            assertEquals(1, capturedVoteDTO.getValue());

            // Verify EdgeOperationMapper was NOT called directly (vote is handled by VoteService)
            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
            verify(edgeOperationMapper, never()).deleteByOperatorAndTarget(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("performOperation - Vote Down Tests")
    class VoteDownTests {

        @Test
        @DisplayName("should delegate to VoteService for VOTE_DOWN operation")
        void shouldDelegateToVoteServiceForVoteDown() {
            // Arrange
            operationDTO.setOperationType(EdgeOperationType.VOTE_DOWN);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 8L, 5L, -1);
            when(voteService.vote(eq(USER_ID), any(VoteDTO.class))).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);
            assertEquals(8L, result.getLikes());
            assertEquals(5L, result.getDislikes());
            assertEquals(3L, result.getFavorites());
            assertEquals(-1, result.getViewer().getVote());

            // Verify VoteService was called with correct parameters
            ArgumentCaptor<VoteDTO> voteDTOCaptor = ArgumentCaptor.forClass(VoteDTO.class);
            verify(voteService).vote(eq(USER_ID), voteDTOCaptor.capture());
            VoteDTO capturedVoteDTO = voteDTOCaptor.getValue();
            assertEquals(-1, capturedVoteDTO.getValue());
        }
    }

    @Nested
    @DisplayName("performOperation - Analyze Tests")
    class AnalyzeTests {

        @Test
        @DisplayName("should add ANALYZE operation when not exists")
        void shouldAddAnalyzeOperationWhenNotExists() {
            // Arrange
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);

            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(0);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 10L, 2L, 0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);
            assertEquals(10L, result.getLikes());
            assertEquals(2L, result.getDislikes());
            assertEquals(0L, result.getFavorites());
            assertEquals(0, result.getViewer().getVote());

            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
            verify(edgeOperationMapper, never()).deleteByOperatorAndTarget(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should remove ANALYZE operation when already exists (toggle)")
        void shouldRemoveAnalyzeOperationWhenAlreadyExists() {
            // Arrange
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);

            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(1);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 10L, 2L, 0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(edgeOperationMapper.deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(1);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);

            verify(edgeOperationMapper).deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue());
            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("should create EdgeOperation with correct fields for ANALYZE")
        void shouldCreateEdgeOperationWithCorrectFieldsForAnalyze() {
            // Arrange
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);
            ArgumentCaptor<EdgeOperation> captor = ArgumentCaptor.forClass(EdgeOperation.class);

            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 0L, 0L, 0));
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(edgeOperationMapper.insert(captor.capture())).thenReturn(1);

            // Act
            edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            EdgeOperation savedOperation = captor.getValue();
            assertEquals(TARGET_ID, savedOperation.getTargetId());
            assertEquals(TARGET_TYPE, savedOperation.getTargetType());
            assertEquals(USER_ID, savedOperation.getOperatorId());
            assertEquals(EdgeOperationType.ANALYZE, savedOperation.getOperationType());
        }
    }

    @Nested
    @DisplayName("performOperation - View Tests")
    class ViewTests {

        @Test
        @DisplayName("should add VIEW operation when not exists")
        void shouldAddViewOperationWhenNotExists() {
            // Arrange
            operationDTO.setOperationType(EdgeOperationType.VIEW);

            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue()))
                    .thenReturn(0);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 15L, 3L, 1);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);
            assertEquals(15L, result.getLikes());
            assertEquals(3L, result.getDislikes());
            assertEquals(2L, result.getFavorites());
            assertEquals(1, result.getViewer().getVote());

            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("should remove VIEW operation when already exists (toggle)")
        void shouldRemoveViewOperationWhenAlreadyExists() {
            // Arrange
            operationDTO.setOperationType(EdgeOperationType.VIEW);

            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue()))
                    .thenReturn(1);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 15L, 3L, 0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            when(edgeOperationMapper.deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue()))
                    .thenReturn(1);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);

            verify(edgeOperationMapper).deleteByOperatorAndTarget(USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue());
        }
    }

    @Nested
    @DisplayName("getInteractions Tests")
    class GetInteractionsTests {

        @Test
        @DisplayName("should return correct interaction stats for PROBLEM")
        void shouldReturnCorrectInteractionStatsForProblem() {
            // Arrange
            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 20L, 5L, 1);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(8L);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE);

            // Assert
            assertNotNull(result);
            assertEquals(20L, result.getLikes());
            assertEquals(5L, result.getDislikes());
            assertEquals(8L, result.getFavorites());
            assertEquals(1, result.getViewer().getVote());

            // Verify bookmark query was made for PROBLEM type
            verify(bookmarkMapper).selectCount(any(QueryWrapper.class));
        }

        @Test
        @DisplayName("should return zero favorites for non-PROBLEM target types")
        void shouldReturnZeroFavoritesForNonProblemTargetTypes() {
            // Arrange
            EdgeOperationTargetType solutionType = EdgeOperationTargetType.SOLUTION;
            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, solutionType.getValue(), 10L, 2L, -1);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, solutionType)).thenReturn(voteResult);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.getInteractions(USER_ID, TARGET_ID, solutionType);

            // Assert
            assertNotNull(result);
            assertEquals(10L, result.getLikes());
            assertEquals(2L, result.getDislikes());
            assertEquals(0L, result.getFavorites()); // Should be 0 for non-PROBLEM types
            assertEquals(-1, result.getViewer().getVote());

            // Verify bookmark query was NOT made for non-PROBLEM type
            verify(bookmarkMapper, never()).selectCount(any(QueryWrapper.class));
        }

        @Test
        @DisplayName("should work for anonymous users (null userId)")
        void shouldWorkForAnonymousUsers() {
            // Arrange
            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 20L, 5L, 0);
            when(voteService.getVoteStatus(null, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.getInteractions(null, TARGET_ID, TARGET_TYPE);

            // Assert
            assertNotNull(result);
            assertEquals(20L, result.getLikes());
            assertEquals(5L, result.getDislikes());
            assertEquals(3L, result.getFavorites());
            assertEquals(0, result.getViewer().getVote()); // Anonymous users have no vote
        }

        @Test
        @DisplayName("should return zero user vote when user has not voted")
        void shouldReturnZeroUserVoteWhenUserHasNotVoted() {
            // Arrange
            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 20L, 5L, 0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, TARGET_TYPE)).thenReturn(voteResult);
            when(bookmarkMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE);

            // Assert
            assertEquals(0, result.getViewer().getVote());
        }
    }

    @Nested
    @DisplayName("Different Target Types Tests")
    class DifferentTargetTypesTests {

        @Test
        @DisplayName("should handle SOLUTION target type")
        void shouldHandleSolutionTargetType() {
            // Arrange
            operationDTO.setTargetType(EdgeOperationTargetType.SOLUTION);
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);

            when(edgeOperationMapper.existsByOperatorAndTarget(anyString(), anyString(),
                    eq(EdgeOperationTargetType.SOLUTION.getValue()), anyString())).thenReturn(0);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, EdgeOperationTargetType.SOLUTION))
                    .thenReturn(new VoteResultVO(TARGET_ID, EdgeOperationTargetType.SOLUTION.getValue(), 5L, 1L, 0));
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);
            assertEquals(0L, result.getFavorites()); // Non-PROBLEM types return 0 favorites
        }

        @Test
        @DisplayName("should handle POST target type")
        void shouldHandlePostTargetType() {
            // Arrange
            operationDTO.setTargetType(EdgeOperationTargetType.POST);
            operationDTO.setOperationType(EdgeOperationType.VIEW);

            when(edgeOperationMapper.existsByOperatorAndTarget(USER_ID, TARGET_ID,
                    EdgeOperationTargetType.POST.getValue(), EdgeOperationType.VIEW.getValue())).thenReturn(0);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);
            when(voteService.getVoteStatus(USER_ID, TARGET_ID, EdgeOperationTargetType.POST))
                    .thenReturn(new VoteResultVO(TARGET_ID, EdgeOperationTargetType.POST.getValue(), 8L, 0L, 0));

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);
            assertEquals(8L, result.getLikes());
            assertEquals(0L, result.getFavorites()); // Non-PROBLEM types return 0 favorites
        }

        @Test
        @DisplayName("should handle COMMENT target type")
        void shouldHandleCommentTargetType() {
            // Arrange
            operationDTO.setTargetType(EdgeOperationTargetType.COMMENT);
            operationDTO.setOperationType(EdgeOperationType.VOTE_DOWN);

            when(voteService.vote(eq(USER_ID), any(VoteDTO.class)))
                    .thenReturn(new VoteResultVO(TARGET_ID, EdgeOperationTargetType.COMMENT.getValue(), 2L, 3L, -1));

            // Act
            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            // Assert
            assertNotNull(result);
            assertEquals(2L, result.getLikes());
            assertEquals(3L, result.getDislikes());
            assertEquals(-1, result.getViewer().getVote());
        }
    }
}
