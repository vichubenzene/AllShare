package com.example.share.dto;

import com.example.share.entity.ShareType;

public record CreateTextShareRequest(
        String name,
        ShareType type,
        String content,
        Integer expirationMinutes,
        String password
) {
}
