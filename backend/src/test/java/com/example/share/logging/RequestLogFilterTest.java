package com.example.share.logging;

import com.example.share.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLogFilterTest {

    @Test
    void classifiesShareRequests() {
        assertThat(RequestLogFilter.action("POST", "/api/shares", 201, null)).isEqualTo("CREATE_TEXT");
        assertThat(RequestLogFilter.action("POST", "/api/shares/file", 201, null)).isEqualTo("CREATE_FILE");
        assertThat(RequestLogFilter.action("GET", "/api/shares/vivi", 200, null)).isEqualTo("VIEW");
        assertThat(RequestLogFilter.action("GET", "/api/shares/vivi.pdf/download", 200, null)).isEqualTo("DOWNLOAD");
        assertThat(RequestLogFilter.action("POST", "/api/shares/vivi/verify", 200, null)).isEqualTo("PASSWORD_OK");
        assertThat(RequestLogFilter.action("DELETE", "/api/shares/vivi", 204, null)).isEqualTo("REVOKE");
    }

    @Test
    void classifiesFailuresByErrorCode() {
        assertThat(RequestLogFilter.action("POST", "/api/shares/vivi/verify", 401, ErrorCode.INVALID_PASSWORD))
                .isEqualTo("PASSWORD_FAILED");
        assertThat(RequestLogFilter.action("GET", "/api/shares/vivi", 410, ErrorCode.SHARE_REVOKED))
                .isEqualTo("REVOKED_ACCESS");
        assertThat(RequestLogFilter.action("GET", "/api/shares/vivi", 410, ErrorCode.SHARE_EXPIRED))
                .isEqualTo("EXPIRED_ACCESS");
        assertThat(RequestLogFilter.action("POST", "/api/shares", 409, ErrorCode.SHARE_NAME_TAKEN))
                .isEqualTo("NAME_TAKEN");
        assertThat(RequestLogFilter.action("POST", "/api/shares", 429, ErrorCode.RATE_LIMITED))
                .isEqualTo("RATE_LIMITED");
        assertThat(RequestLogFilter.action("POST", "/api/shares/file", 413, ErrorCode.FILE_TOO_LARGE))
                .isEqualTo("CREATE_FILE_REJECTED");
        assertThat(RequestLogFilter.action("GET", "/api/shares/vivi", 500, null)).isEqualTo("ERROR");
    }

    @Test
    void recordsARequestWithoutSensitiveHeaders() throws Exception {
        RequestLogService logs = mock(RequestLogService.class);
        when(logs.isEnabled()).thenReturn(true);
        RequestLogFilter filter = new RequestLogFilter(logs);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/shares/Vivi.pdf/download");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "curl/8");
        request.addHeader("Authorization", "Bearer secret-management-token");
        request.addHeader("X-Share-Access", "v1.secret.access");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEntry> captor = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(logs).record(captor.capture());
        RequestLogEntry entry = captor.getValue();
        assertThat(entry.method()).isEqualTo("GET");
        assertThat(entry.path()).isEqualTo("/api/shares/Vivi.pdf/download");
        assertThat(entry.status()).isEqualTo(200);
        assertThat(entry.ip()).isEqualTo("127.0.0.1");
        assertThat(entry.userAgent()).isEqualTo("curl/8");
        assertThat(entry.shareName()).isEqualTo("vivi");
        assertThat(entry.action()).isEqualTo("DOWNLOAD");
        assertThat(entry.toDocument().toJson())
                .doesNotContain("secret-management-token")
                .doesNotContain("v1.secret.access")
                .doesNotContain("Authorization");
    }

    @Test
    void takesTheNameOfATextShareFromTheCapturedBody() throws Exception {
        RequestLogService logs = mock(RequestLogService.class);
        when(logs.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/shares");
        request.setAttribute(RequestLogFilter.SHARE_NAME_ATTRIBUTE, "Vivi");

        new RequestLogFilter(logs).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<RequestLogEntry> captor = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(logs).record(captor.capture());
        assertThat(captor.getValue().shareName()).isEqualTo("vivi");
    }

    @Test
    void skipsLoggingWhenDisabled() throws Exception {
        RequestLogService logs = mock(RequestLogService.class);
        when(logs.isEnabled()).thenReturn(false);

        new RequestLogFilter(logs).doFilter(
                new MockHttpServletRequest("GET", "/api/shares/vivi"), new MockHttpServletResponse(), new MockFilterChain());

        verify(logs, never()).record(org.mockito.ArgumentMatchers.any());
    }
}
