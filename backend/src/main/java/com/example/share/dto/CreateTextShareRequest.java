package com.example.share.dto;

import com.example.share.entity.ShareType;

public record CreateTextShareRequest(
        ShareType type,
        String content,
        Integer expirationMinutes,
        String password
) {
}
