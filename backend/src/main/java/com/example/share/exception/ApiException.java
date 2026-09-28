package com.example.share.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final String shareUrl;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, null);
    }

    public ApiException(ErrorCode code, HttpStatus status, String message, String shareUrl) {
        super(message);
        this.code = code;
        this.status = status;
        this.shareUrl = shareUrl;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getShareUrl() {
        return shareUrl;
    }
}
