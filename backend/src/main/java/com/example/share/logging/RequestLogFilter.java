package com.example.share.logging;

import com.example.share.exception.ErrorCode;
import com.example.share.service.ShareNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = RequestLogFilter.class.getName() + ".errorCode";
    public static final String SHARE_NAME_ATTRIBUTE = RequestLogFilter.class.getName() + ".shareName";

    private static final Pattern SHARE_PATH = Pattern.compile("^/api/shares/([^/]+)(/verify|/download)?$");

    private final RequestLogService logs;

    public RequestLogFilter(RequestLogService logs) {
        this.logs = logs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !logs.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant startedAt = Instant.now();
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            throw ex;
        } finally {
            int status = failed ? 500 : response.getStatus();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            logs.record(entry(request, status, startedAt, elapsedMs));
        }
    }

    private static RequestLogEntry entry(HttpServletRequest request, int status, Instant startedAt, long elapsedMs) {
        String method = request.getMethod();
        String path = truncate(request.getRequestURI(), 300);
        ErrorCode errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE) instanceof ErrorCode code ? code : null;
        return new RequestLogEntry(
                startedAt,
                method,
                path,
                status,
                request.getRemoteAddr(),
                truncate(request.getHeader(HttpHeaders.USER_AGENT), 300),
                shareName(request, path),
                action(method, path, status, errorCode),
                errorCode == null ? null : errorCode.name(),
                elapsedMs);
    }

    static String action(String method, String path, int status, ErrorCode errorCode) {
        if (errorCode != null) {
            return switch (errorCode) {
                case INVALID_PASSWORD -> "PASSWORD_FAILED";
                case SHARE_REVOKED -> "REVOKED_ACCESS";
                case SHARE_EXPIRED -> "EXPIRED_ACCESS";
                case SHARE_NOT_FOUND, NOT_FOUND -> "NOT_FOUND";
                case SHARE_NAME_TAKEN -> "NAME_TAKEN";
                case RATE_LIMITED -> "RATE_LIMITED";
                case PASSWORD_REQUIRED -> "PASSWORD_REQUIRED";
                case UNAUTHORIZED -> "UNAUTHORIZED";
                case INTERNAL_ERROR, SERVICE_UNAVAILABLE -> "ERROR";
                default -> baseAction(method, path) + "_REJECTED";
            };
        }
        if (status >= 500) {
            return "ERROR";
        }
        return baseAction(method, path);
    }

    private static String baseAction(String method, String path) {
        if ("POST".equals(method) && "/api/shares".equals(path)) {
            return "CREATE_TEXT";
        }
        if ("POST".equals(method) && "/api/shares/file".equals(path)) {
            return "CREATE_FILE";
        }
        Matcher matcher = SHARE_PATH.matcher(path);
        if (matcher.matches()) {
            String suffix = matcher.group(2);
            if (suffix == null && "GET".equals(method)) {
                return "VIEW";
            }
            if (suffix == null && "DELETE".equals(method)) {
                return "REVOKE";
            }
            if ("/verify".equals(suffix) && "POST".equals(method)) {
                return "PASSWORD_OK";
            }
            if ("/download".equals(suffix) && "GET".equals(method)) {
                return "DOWNLOAD";
            }
        }
        return "REQUEST";
    }

    static String shareName(HttpServletRequest request, String path) {
        Matcher matcher = SHARE_PATH.matcher(path);
        if (matcher.matches() && !"file".equals(matcher.group(1))) {
            ShareNames.Slug slug = ShareNames.parse(matcher.group(1));
            return slug == null ? null : slug.name();
        }
        Object captured = request.getAttribute(SHARE_NAME_ATTRIBUTE);
        String raw = captured instanceof String value ? value : null;
        if (raw == null && "/api/shares/file".equals(path)) {
            try {
                raw = request.getParameter("name");
            } catch (RuntimeException ex) {
                raw = null;
            }
        }
        String name = ShareNames.normalize(raw);
        return ShareNames.isValid(name) ? name : null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
