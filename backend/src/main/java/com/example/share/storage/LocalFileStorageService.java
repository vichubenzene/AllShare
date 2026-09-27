package com.example.share.storage;

import com.example.share.config.AppProperties;
import com.example.share.exception.ApiException;
import com.example.share.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final HexFormat HEX = HexFormat.of();

    private final Path root;
    private final SecureRandom random = new SecureRandom();
    private final Tika tika = new Tika();

    @Autowired
    public LocalFileStorageService(AppProperties properties) {
        this(properties.storage().path());
    }

    public LocalFileStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public String store(MultipartFile file) {
        String key = newKey();
        Path target = resolve(key);
        try {
            Files.createDirectories(root);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store file", ex);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new ApiException(ErrorCode.SHARE_NOT_FOUND, HttpStatus.NOT_FOUND, "This share is no longer available.");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || !isSafeKey(storageKey)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not delete file", ex);
        }
    }

    @Override
    public String probeContentType(String storageKey) {
        Path target = resolve(storageKey);
        try {
            String detected = tika.detect(target);
            if (detected == null || detected.isBlank()) {
                return "application/octet-stream";
            }
            return detected;
        } catch (IOException ex) {
            log.warn("event=content_type_probe_failed");
            return "application/octet-stream";
        }
    }

    public Path root() {
        return root;
    }

    private String newKey() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private Path resolve(String storageKey) {
        if (!isSafeKey(storageKey)) {
            throw new ApiException(ErrorCode.INVALID_FILE, HttpStatus.BAD_REQUEST, "Invalid storage key.");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(ErrorCode.INVALID_FILE, HttpStatus.BAD_REQUEST, "Invalid storage key.");
        }
        return resolved;
    }

    private static boolean isSafeKey(String storageKey) {
        return storageKey != null && storageKey.matches("[a-f0-9]{32}");
    }
}
