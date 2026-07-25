package com.ulticode.modules.edgeoperations.service;

import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector;
import com.ulticode.modules.edgeoperations.service.impl.EdgeOperationsServiceImpl;
import com.ulticode.modules.solution.mapper.SolutionMapper;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the write path of {@link EdgeOperationsService}.
 *
 * <p>Pure-read paths (interaction stats, favorites count) used to
 * live on this class as {@code getInteractions}; they have been
 * extracted into {@link EdgeOperationInspector} and are now
 * exercised by
 * {@code DefaultEdgeOperationInspectorTest}. The service tests here
 * stub the inspector so the post-mutation read returns a real
 * response VO without dragging the bookmark mapper back into the
 * service's bean graph.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EdgeOperationsService — write path")
class EdgeOperationsServiceTest {

    @Mock
    private VoteService voteService;

    @Mock
    private EdgeOperationMapper edgeOperationMapper;

    @Mock
    private SolutionMapper solutionMapper;

    @Spy
    private EdgeOperationInspector edgeOperationInspector =
            org.mockito.Mockito.mock(EdgeOperationInspector.class);

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

    /**
     * Build a stock "after-mutation" response that the inspector will
     * return. Centralised so the vote/toggle tests can focus on the
     * write path; the inspector's own tests cover the read shape.
     */
    private EdgeOperationResponseVO stubbedInteractionResponse(long likes, long dislikes,
                                                                long favorites, int vote) {
        return EdgeOperationResponseVO.builder()
                .likes(likes)
                .dislikes(dislikes)
                .favorites(favorites)
                .viewer(EdgeOperationResponseVO.ViewerState.builder().vote(vote).build())
                .build();
    }

    // ------------------------------------------------------------------
    // performOperation - Vote Up
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("performOperation - Vote Up")
    class VoteUpTests {

        @Test
        @DisplayName("delegates to VoteService for VOTE_UP and reuses the inspector's response")
        void voteUp_delegatesToVoteServiceAndReturnsInspectorResponse() {
            operationDTO.setOperationType(EdgeOperationType.VOTE_UP);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 10L, 2L, 1);
            when(voteService.vote(eq(USER_ID), any(VoteDTO.class))).thenReturn(voteResult);
            when(edgeOperationInspector.getFavoritesCount(TARGET_ID, TARGET_TYPE)).thenReturn(5L);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(stubbedInteractionResponse(10L, 2L, 5L, 1));

            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            assertThat(result).isNotNull();
            assertThat(result.getLikes()).isEqualTo(10L);
            assertThat(result.getDislikes()).isEqualTo(2L);
            assertThat(result.getFavorites()).isEqualTo(5L);
            assertThat(result.getViewer().getVote()).isEqualTo(1);

            ArgumentCaptor<VoteDTO> captor = ArgumentCaptor.forClass(VoteDTO.class);
            verify(voteService).vote(eq(USER_ID), captor.capture());
            VoteDTO captured = captor.getValue();
            assertThat(captured.getTargetId()).isEqualTo(TARGET_ID);
            assertThat(captured.getTargetType()).isEqualTo(TARGET_TYPE);
            assertThat(captured.getValue()).isEqualTo(1);

            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
            verify(edgeOperationMapper, never()).deleteByOperatorAndTarget(
                    anyString(), anyString(), anyString(), anyString());
        }
    }

    // ------------------------------------------------------------------
    // performOperation - Vote Down
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("performOperation - Vote Down")
    class VoteDownTests {

        @Test
        @DisplayName("delegates to VoteService for VOTE_DOWN with -1 value")
        void voteDown_delegatesToVoteService() {
            operationDTO.setOperationType(EdgeOperationType.VOTE_DOWN);

            VoteResultVO voteResult = new VoteResultVO(TARGET_ID, TARGET_TYPE.getValue(), 8L, 5L, -1);
            when(voteService.vote(eq(USER_ID), any(VoteDTO.class))).thenReturn(voteResult);
            when(edgeOperationInspector.getFavoritesCount(TARGET_ID, TARGET_TYPE)).thenReturn(3L);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(stubbedInteractionResponse(8L, 5L, 3L, -1));

            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            assertThat(result.getLikes()).isEqualTo(8L);
            assertThat(result.getDislikes()).isEqualTo(5L);
            assertThat(result.getFavorites()).isEqualTo(3L);
            assertThat(result.getViewer().getVote()).isEqualTo(-1);

            ArgumentCaptor<VoteDTO> captor = ArgumentCaptor.forClass(VoteDTO.class);
            verify(voteService).vote(eq(USER_ID), captor.capture());
            assertThat(captor.getValue().getValue()).isEqualTo(-1);
        }
    }

    // ------------------------------------------------------------------
    // performOperation - Analyze
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("performOperation - Analyze")
    class AnalyzeTests {

        @Test
        @DisplayName("adds ANALYZE operation when not exists")
        void analyze_addWhenNotExists() {
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);

            when(edgeOperationMapper.existsByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(stubbedInteractionResponse(10L, 2L, 0L, 0));

            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            assertThat(result).isNotNull();
            assertThat(result.getLikes()).isEqualTo(10L);
            assertThat(result.getFavorites()).isZero();
            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
            verify(edgeOperationMapper, never()).deleteByOperatorAndTarget(
                    anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("removes ANALYZE operation when already exists (toggle)")
        void analyze_removeWhenExists() {
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);

            when(edgeOperationMapper.existsByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.deleteByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(1);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(stubbedInteractionResponse(10L, 2L, 0L, 0));

            edgeOperationsService.performOperation(USER_ID, operationDTO);

            verify(edgeOperationMapper).deleteByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue());
            verify(edgeOperationMapper, never()).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("creates EdgeOperation with correct fields for ANALYZE")
        void analyze_createsCorrectEdgeOperation() {
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);
            ArgumentCaptor<EdgeOperation> captor = ArgumentCaptor.forClass(EdgeOperation.class);

            when(edgeOperationMapper.existsByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.ANALYZE.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.insert(captor.capture())).thenReturn(1);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(stubbedInteractionResponse(0L, 0L, 0L, 0));

            edgeOperationsService.performOperation(USER_ID, operationDTO);

            EdgeOperation saved = captor.getValue();
            assertThat(saved.getTargetId()).isEqualTo(TARGET_ID);
            assertThat(saved.getTargetType()).isEqualTo(TARGET_TYPE);
            assertThat(saved.getOperatorId()).isEqualTo(USER_ID);
            assertThat(saved.getOperationType()).isEqualTo(EdgeOperationType.ANALYZE);
        }
    }

    // ------------------------------------------------------------------
    // performOperation - View
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("performOperation - View")
    class ViewTests {

        @Test
        @DisplayName("adds VIEW operation when not exists")
        void view_addWhenNotExists() {
            operationDTO.setOperationType(EdgeOperationType.VIEW);

            when(edgeOperationMapper.existsByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(stubbedInteractionResponse(15L, 3L, 2L, 1));

            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            assertThat(result).isNotNull();
            assertThat(result.getFavorites()).isEqualTo(2L);
            verify(edgeOperationMapper).insert(any(EdgeOperation.class));
        }

        @Test
        @DisplayName("removes VIEW operation when already exists (toggle)")
        void view_removeWhenExists() {
            operationDTO.setOperationType(EdgeOperationType.VIEW);

            when(edgeOperationMapper.existsByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue()))
                    .thenReturn(1);
            when(edgeOperationMapper.deleteByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue()))
                    .thenReturn(1);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, TARGET_TYPE))
                    .thenReturn(stubbedInteractionResponse(15L, 3L, 0L, 0));

            edgeOperationsService.performOperation(USER_ID, operationDTO);

            verify(edgeOperationMapper).deleteByOperatorAndTarget(
                    USER_ID, TARGET_ID, TARGET_TYPE.getValue(), EdgeOperationType.VIEW.getValue());
        }
    }

    // ------------------------------------------------------------------
    // Different Target Types
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("performOperation - Different Target Types")
    class DifferentTargetTypesTests {

        @Test
        @DisplayName("handles SOLUTION target type for ANALYZE")
        void solutionTarget_analyze() {
            operationDTO.setTargetType(EdgeOperationTargetType.SOLUTION);
            operationDTO.setOperationType(EdgeOperationType.ANALYZE);

            when(edgeOperationMapper.existsByOperatorAndTarget(
                    anyString(), anyString(),
                    eq(EdgeOperationTargetType.SOLUTION.getValue()), anyString()))
                    .thenReturn(0);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, EdgeOperationTargetType.SOLUTION))
                    .thenReturn(stubbedInteractionResponse(5L, 1L, 0L, 0));

            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            assertThat(result).isNotNull();
            assertThat(result.getFavorites()).isZero();
        }

        @Test
        @DisplayName("handles POST target type for VIEW")
        void postTarget_view() {
            operationDTO.setTargetType(EdgeOperationTargetType.POST);
            operationDTO.setOperationType(EdgeOperationType.VIEW);

            when(edgeOperationMapper.existsByOperatorAndTarget(
                    USER_ID, TARGET_ID,
                    EdgeOperationTargetType.POST.getValue(), EdgeOperationType.VIEW.getValue()))
                    .thenReturn(0);
            when(edgeOperationMapper.insert(any(EdgeOperation.class))).thenReturn(1);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, EdgeOperationTargetType.POST))
                    .thenReturn(stubbedInteractionResponse(8L, 0L, 0L, 0));

            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            assertThat(result.getLikes()).isEqualTo(8L);
            assertThat(result.getFavorites()).isZero();
        }

        @Test
        @DisplayName("handles COMMENT target type for VOTE_DOWN")
        void commentTarget_voteDown() {
            operationDTO.setTargetType(EdgeOperationTargetType.COMMENT);
            operationDTO.setOperationType(EdgeOperationType.VOTE_DOWN);

            when(voteService.vote(eq(USER_ID), any(VoteDTO.class)))
                    .thenReturn(new VoteResultVO(
                            TARGET_ID, EdgeOperationTargetType.COMMENT.getValue(), 2L, 3L, -1));
            when(edgeOperationInspector.getFavoritesCount(TARGET_ID, EdgeOperationTargetType.COMMENT))
                    .thenReturn(0L);
            when(edgeOperationInspector.getInteractions(USER_ID, TARGET_ID, EdgeOperationTargetType.COMMENT))
                    .thenReturn(stubbedInteractionResponse(2L, 3L, 0L, -1));

            EdgeOperationResponseVO result = edgeOperationsService.performOperation(USER_ID, operationDTO);

            assertThat(result.getLikes()).isEqualTo(2L);
            assertThat(result.getDislikes()).isEqualTo(3L);
            assertThat(result.getViewer().getVote()).isEqualTo(-1);
        }
    }
}
