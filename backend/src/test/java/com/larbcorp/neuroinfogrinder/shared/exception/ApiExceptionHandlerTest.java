package com.larbcorp.neuroinfogrinder.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    @Test
    void sseDisconnectReturnsNoContentWithoutErrorBody() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/pipeline/events/stream");

        ResponseEntity<?> response = handler.handleIoException(new IOException("client disconnected"), request);

        assertEquals(204, response.getStatusCode().value());
    }
}
