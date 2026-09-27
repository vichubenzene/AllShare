package com.example.share.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsAConsistentBodyForApplicationErrors() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        var entity = handler.handleApi(
                new ApiException(ErrorCode.SHARE_EXPIRED, HttpStatus.GONE, "This share has expired."),
                response);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(entity.getBody().code()).isEqualTo(ErrorCode.SHARE_EXPIRED);
        assertThat(entity.getBody().message()).isEqualTo("This share has expired.");
    }

    @Test
    void mapsOversizedUploadsToACleanError() {
        var entity = handler.handleTooLarge(new MaxUploadSizeExceededException(50 * 1024 * 1024));

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(entity.getBody().code()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
        assertThat(entity.getBody().message()).contains("50 MB");
    }

    @Test
    void hidesUnexpectedFailures() {
        var entity = handler.handleUnexpected(new IllegalStateException("password=hunter2 token=abc"));

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(entity.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(entity.getBody().message()).doesNotContain("hunter2");
    }
}
