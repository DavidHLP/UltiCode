package com.ulticode.modules.edgeoperations.controller;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector;
import com.ulticode.modules.edgeoperations.service.EdgeOperationsService;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdgeOperationsControllerTest {

    @Mock
    private EdgeOperationsService edgeOperationsService;

    @Mock
    private EdgeOperationInspector edgeOperationInspector;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private EdgeOperationsController controller;

    @Test
    void performOperationRejectsMissingCurrentUserBeforeWrite() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        EdgeOperationDTO request = new EdgeOperationDTO();
        request.setTargetId("1");
        request.setTargetType(EdgeOperationTargetType.PROBLEM);
        request.setOperationType(EdgeOperationType.VOTE_UP);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.performOperation(request));

        assertEquals(BaseErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verifyNoInteractions(edgeOperationsService);
    }
}
