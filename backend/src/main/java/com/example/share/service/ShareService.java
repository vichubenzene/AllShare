package com.example.share.service;

import com.example.share.config.AppProperties;
import com.example.share.dto.CreateTextShareRequest;
import com.example.share.dto.ShareAccessResponse;
import com.example.share.dto.ShareCreatedResponse;
import com.example.share.dto.ShareDownload;
import com.example.share.dto.ShareViewResponse;
import com.example.share.entity.Share;
import com.example.share.entity.ShareType;
import com.example.share.exception.ApiException;
import com.example.share.exception.ErrorCode;
import com.example.share.repository.ShareRepository;
import com.example.share.storage.FileStorageService;
import com.example.share.storage.FilenameSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);
    private static final String NAME_CONSTRAINT = "uk_shares_name";
    private static final Set<String> ACTIVE_CONTENT = Set.of(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "text/javascript",
            "application/javascript");
    private static final int CLEANUP_BATCH = 100;

    private final ShareRepository shares;
    private final FileStorageService storage;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AccessTokenService accessTokens;
    private final RateLimitService rateLimit;
    private final AppProperties properties;
    private final Clock clock;

    public ShareService(
            ShareRepository shares,
            FileStorageService storage,
            PasswordEncoder passwordEncoder,
            TokenService tokens,
            AccessTokenService accessTokens,
            RateLimitService rateLimit,
            AppProperties properties,
            Clock clock
    ) {
        this.shares = shares;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.accessTokens = accessTokens;
        this.rateLimit = rateLimit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ShareCreatedResponse createText(CreateTextShareRequest request, String clientKey) {
        rateLimit.checkCreate(clientKey);
        if (request == null) {
            throw validation("Invalid request.");
        }
        if (request.type() != null && request.type() != ShareType.TEXT) {
            throw validation("Use POST /api/shares/file for file shares.");
        }
        String name = requireName(request.name());
        String content = request.content() == null ? "" : request.content();
        if (content.isBlank()) {
            throw validation("Text content must not be blank.");
        }
        if (content.length() > properties.share().maxTextLength()) {
            throw validation("Text content is too long.");
        }
        int minutes = requireExpiration(request.expirationMinutes());
        String password = normalizePassword(request.password());

        Created created = newShare(name, ShareType.TEXT, minutes, password);
        created.share().setTextContent(content);
        insert(created.share());
        log.info("event=share_created shareId={} type=TEXT", created.share().getId());
        return toCreated(created);
    }

    @Transactional
    public ShareCreatedResponse createFile(
            String rawName,
            MultipartFile file,
            Integer expirationMinutes,
            String password,
            String clientKey
    ) {
        rateLimit.checkCreate(clientKey);
        String name = requireName(rawName);
        int minutes = requireExpiration(expirationMinutes);
        String normalizedPassword = normalizePassword(password);
        validateFile(file);

        String storageKey = storage.store(file);
        try {
            String originalFilename = FilenameSanitizer.sanitize(file.getOriginalFilename());
            Created created = newShare(name, ShareType.FILE, minutes, normalizedPassword);
            Share share = created.share();
            share.setOriginalFilename(originalFilename);
            share.setExtension(ShareNames.extensionOf(originalFilename));
            share.setContentType(safeContentType(storage.probeContentType(storageKey)));
            share.setFileSize(file.getSize());
            share.setStorageKey(storageKey);
            insert(share);
            log.info("event=share_created shareId={} type=FILE", share.getId());
            return toCreated(created);
        } catch (RuntimeException ex) {
            try {
                storage.delete(storageKey);
            } catch (RuntimeException deleteFailure) {
                ex.addSuppressed(deleteFailure);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ShareViewResponse view(String slug, String accessToken) {
        Share share = requireAvailable(slug);
        if (!hasAccess(share, accessToken)) {
            return ShareViewResponse.locked(
                    share.getName(), publicPath(share), share.getType().name(), share.getExpiresAt());
        }
        return toView(share);
    }

    @Transactional(readOnly = true)
    public ShareAccessResponse verify(String slug, String password) {
        Share share = requireAvailable(slug);
        rateLimit.checkVerify(share.getName());
        if (share.getPasswordHash() == null) {
            throw validation("This share is not password protected.");
        }
        if (!passwordMatches(password, share.getPasswordHash())) {
            log.info("event=password_verification_failed shareId={}", share.getId());
            throw new ApiException(ErrorCode.INVALID_PASSWORD, HttpStatus.UNAUTHORIZED, "Incorrect password.");
        }
        Instant accessExpiry = Instant.now(clock).plus(properties.share().accessTokenTtl());
        if (accessExpiry.isAfter(share.getExpiresAt())) {
            accessExpiry = share.getExpiresAt();
        }
        String accessToken = accessTokens.issue(share.getName(), accessExpiry);
        return new ShareAccessResponse(accessToken, accessExpiry, toView(share));
    }

    @Transactional(readOnly = true)
    public ShareDownload download(String slug, String accessToken) {
        Share share = requireAvailable(slug);
        if (!hasAccess(share, accessToken)) {
            throw new ApiException(ErrorCode.PASSWORD_REQUIRED, HttpStatus.UNAUTHORIZED, "Password required.");
        }
        if (share.getType() != ShareType.FILE || share.getStorageKey() == null) {
            throw new ApiException(ErrorCode.INVALID_FILE, HttpStatus.BAD_REQUEST, "This share has no file.");
        }
        var resource = storage.load(share.getStorageKey());
        log.info("event=file_downloaded shareId={} size={}", share.getId(), share.getFileSize());
        String filename = share.getOriginalFilename() == null ? "download" : share.getOriginalFilename();
        long size = share.getFileSize() == null ? -1 : share.getFileSize();
        return new ShareDownload(resource, filename, servingType(share.getContentType()), size);
    }

    @Transactional
    public void revoke(String slug, String authorizationHeader) {
        Share share = requireKnown(slug);
        if (!tokens.matchesHash(bearer(authorizationHeader), share.getManagementTokenHash())) {
            log.info("event=revoke_unauthorized shareId={}", share.getId());
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid management token.");
        }
        if (share.getRevokedAt() != null) {
            return;
        }
        if (share.getStorageKey() != null) {
            storage.delete(share.getStorageKey());
            share.setStorageKey(null);
        }
        share.setTextContent(null);
        share.setRevokedAt(Instant.now(clock));
        shares.save(share);
        log.info("event=share_revoked shareId={}", share.getId());
    }

    public void cleanupExpired() {
        Instant now = Instant.now(clock);
        while (true) {
            List<UUID> ids = shares.findExpiredIds(now, PageRequest.of(0, CLEANUP_BATCH));
            if (ids.isEmpty()) {
                return;
            }
            int failed = 0;
            for (UUID id : ids) {
                try {
                    purge(id);
                } catch (RuntimeException ex) {
                    failed++;
                    log.error("event=share_cleanup_failed shareId={}", id);
                }
            }
            if (failed > 0 || ids.size() < CLEANUP_BATCH) {
                return;
            }
        }
    }

    @Transactional
    public void purge(UUID id) {
        Share share = shares.findById(id).orElse(null);
        if (share == null || share.getExpiresAt().isAfter(Instant.now(clock))) {
            return;
        }
        deleteWithFile(share);
        log.info("event=share_expired shareId={}", share.getId());
    }

    /**
     * An expired share frees its name. A live or revoked-but-unexpired share keeps it.
     * The unique constraint decides races between concurrent creates.
     */
    private void insert(Share share) {
        Share existing = shares.findByName(share.getName()).orElse(null);
        if (existing != null) {
            if (existing.getExpiresAt().isAfter(Instant.now(clock))) {
                throw nameTaken(share.getName(), publicPath(existing));
            }
            deleteWithFile(existing);
            shares.flush();
        }
        try {
            shares.saveAndFlush(share);
        } catch (DataIntegrityViolationException ex) {
            if (isNameConflict(ex)) {
                throw nameTaken(share.getName(), "/" + share.getName());
            }
            throw ex;
        }
    }

    private void deleteWithFile(Share share) {
        if (share.getStorageKey() != null) {
            storage.delete(share.getStorageKey());
        }
        shares.delete(share);
    }

    private static boolean isNameConflict(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null ? ex.getMessage() : cause.getMessage();
        return message != null && message.contains(NAME_CONSTRAINT);
    }

    private static ApiException nameTaken(String name, String shareUrl) {
        return new ApiException(
                ErrorCode.SHARE_NAME_TAKEN,
                HttpStatus.CONFLICT,
                "The share name '" + name + "' already exists. Choose another name.",
                shareUrl);
    }

    private Created newShare(String name, ShareType type, int expirationMinutes, String password) {
        String managementToken = tokens.newManagementToken();
        Instant now = Instant.now(clock);
        Share share = new Share();
        share.setId(UUID.randomUUID());
        share.setName(name);
        share.setType(type);
        share.setCreatedAt(now);
        share.setExpiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES));
        share.setManagementTokenHash(tokens.sha256Hex(managementToken));
        if (password != null) {
            share.setPasswordHash(passwordEncoder.encode(password));
        }
        return new Created(share, managementToken);
    }

    private Share requireAvailable(String slug) {
        Share share = requireKnown(slug);
        if (share.getRevokedAt() != null) {
            throw new ApiException(ErrorCode.SHARE_REVOKED, HttpStatus.GONE, "This share has been revoked.");
        }
        if (!share.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ApiException(ErrorCode.SHARE_EXPIRED, HttpStatus.GONE, "This share has expired.");
        }
        return share;
    }

    private Share requireKnown(String slug) {
        ShareNames.Slug parsed = ShareNames.parse(slug);
        if (parsed == null) {
            throw notFound();
        }
        Share share = shares.findByName(parsed.name()).orElseThrow(ShareService::notFound);
        if (parsed.extension() != null && !parsed.extension().equals(share.getExtension())) {
            throw notFound();
        }
        return share;
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.SHARE_NOT_FOUND, HttpStatus.NOT_FOUND, "Share not found.");
    }

    private static String requireName(String raw) {
        String name = ShareNames.normalize(raw);
        if (name.isEmpty()) {
            throw validation("Choose a share name.");
        }
        if (!ShareNames.isValid(name)) {
            throw validation(ShareNames.RULES);
        }
        if (ShareNames.isReserved(name)) {
            throw validation("The share name '" + name + "' is reserved. Choose another name.");
        }
        return name;
    }

    private boolean hasAccess(Share share, String accessToken) {
        if (share.getPasswordHash() == null) {
            return true;
        }
        return accessTokens.isValid(accessToken, share.getName(), Instant.now(clock));
    }

    private boolean passwordMatches(String password, String hash) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        try {
            return passwordEncoder.matches(password, hash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_FILE, HttpStatus.BAD_REQUEST, "Choose a file to share.");
        }
        if (file.getSize() > properties.share().maxFileSize()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 50 MB limit.");
        }
    }

    private int requireExpiration(Integer minutes) {
        if (minutes == null || !properties.share().allowedExpirationMinutes().contains(minutes)) {
            throw validation("Expiration must be one of: 15, 60, 360, 1440, 4320, 10080 minutes.");
        }
        return minutes;
    }

    private static String normalizePassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        if (password.length() > 200) {
            throw validation("Password is too long.");
        }
        return password;
    }

    private static String safeContentType(String detected) {
        if (detected == null || detected.isBlank()) {
            return "application/octet-stream";
        }
        String base = detected.split(";")[0].trim().toLowerCase();
        if (base.isEmpty() || base.length() > 120) {
            return "application/octet-stream";
        }
        return base;
    }

    private static String servingType(String stored) {
        if (stored == null || ACTIVE_CONTENT.contains(stored)) {
            return "application/octet-stream";
        }
        return stored;
    }

    private static String bearer(String authorizationHeader) {
        String prefix = "Bearer ";
        if (authorizationHeader != null && authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorizationHeader.substring(prefix.length()).trim();
        }
        return "";
    }

    private static String publicPath(Share share) {
        return ShareNames.publicPath(share.getName(), share.getExtension());
    }

    private static ShareViewResponse toView(Share share) {
        boolean passwordProtected = share.getPasswordHash() != null;
        if (share.getType() == ShareType.TEXT) {
            return ShareViewResponse.text(
                    share.getName(), publicPath(share), share.getTextContent(), share.getExpiresAt(), passwordProtected);
        }
        return ShareViewResponse.file(
                share.getName(),
                publicPath(share),
                share.getOriginalFilename(),
                share.getExtension(),
                share.getContentType(),
                share.getFileSize() == null ? 0 : share.getFileSize(),
                share.getExpiresAt(),
                passwordProtected);
    }

    private static ShareCreatedResponse toCreated(Created created) {
        Share share = created.share();
        return new ShareCreatedResponse(
                share.getName(),
                share.getType().name(),
                publicPath(share),
                created.managementToken(),
                share.getExpiresAt(),
                share.getPasswordHash() != null);
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    private record Created(Share share, String managementToken) {
    }
}
