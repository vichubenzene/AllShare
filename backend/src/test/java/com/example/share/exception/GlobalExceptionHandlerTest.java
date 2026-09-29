package com.example.share.exception;

import com.example.share.logging.RequestLogFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsAConsistentBodyAndTagsTheRequestForLogging() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        var entity = handler.handleApi(
                new ApiException(ErrorCode.SHARE_EXPIRED, HttpStatus.GONE, "This share has expired."),
                request,
                new MockHttpServletResponse());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(entity.getBody().code()).isEqualTo(ErrorCode.SHARE_EXPIRED);
        assertThat(entity.getBody().message()).isEqualTo("This share has expired.");
        assertThat(request.getAttribute(RequestLogFilter.ERROR_CODE_ATTRIBUTE)).isEqualTo(ErrorCode.SHARE_EXPIRED);
    }

    @Test
    void includesTheExistingShareUrlWhenANameIsTaken() {
        var entity = handler.handleApi(
                new ApiException(ErrorCode.SHARE_NAME_TAKEN, HttpStatus.CONFLICT, "taken", "/vivi.pdf"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(entity.getBody().shareUrl()).isEqualTo("/vivi.pdf");
    }

    @Test
    void mapsOversizedUploadsToACleanError() {
        var entity = handler.handleTooLarge(new MaxUploadSizeExceededException(50 * 1024 * 1024), new MockHttpServletRequest());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(entity.getBody().code()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void hidesUnexpectedFailures() {
        var entity = handler.handleUnexpected(new IllegalStateException("password=hunter2"), new MockHttpServletRequest());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(entity.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(entity.getBody().message()).doesNotContain("hunter2");
    }
}
