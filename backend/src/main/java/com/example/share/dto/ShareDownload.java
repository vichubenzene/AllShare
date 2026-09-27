package com.example.share.dto;

import org.springframework.core.io.Resource;

public record ShareDownload(
        Resource resource,
        String filename,
        String contentType,
        long size
) {
}
