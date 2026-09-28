package com.example.share.dto;

import com.example.share.exception.ErrorCode;

public record ErrorResponse(ErrorCode code, String message, String shareUrl) {

    public ErrorResponse(ErrorCode code, String message) {
        this(code, message, null);
    }
}
