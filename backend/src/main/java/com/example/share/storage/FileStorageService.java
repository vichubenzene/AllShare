package com.example.share.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String store(MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);

    default String probeContentType(String storageKey) {
        return "application/octet-stream";
    }
}
