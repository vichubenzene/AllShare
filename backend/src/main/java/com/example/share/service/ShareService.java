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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9]{8,32}$");
    private static final Set<String> ACTIVE_CONTENT = Set.of(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "text/javascript",
            "application/javascript");
    private static final int CLEANUP_BATCH = 100;

    private final ShareRepository shares;
    private final FileStorageService storage;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AccessTokenService accessTokens;
    private final RateLimitService rateLimit;
    private final AppProperties properties;
    private final Clock clock;

    public ShareService(
            ShareRepository shares,
            FileStorageService storage,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
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
        if (request.type() != ShareType.TEXT) {
            throw validation("Text shares must use type TEXT.");
        }
        String content = request.content() == null ? "" : request.content();
        if (content.isBlank()) {
            throw validation("Text content must not be blank.");
        }
        if (content.length() > properties.share().maxTextLength()) {
            throw validation("Text content is too long.");
        }
        int minutes = requireExpiration(request.expirationMinutes());
        String password = normalizePassword(request.password());

        Created created = newShare(ShareType.TEXT, minutes, password);
        created.share().setTextContent(content);
        shares.save(created.share());
        log.info("event=share_created shareId={} type=TEXT", created.share().getId());
        return toCreated(created);
    }

    @Transactional
    public ShareCreatedResponse createFile(MultipartFile file, Integer expirationMinutes, String password, String clientKey) {
        rateLimit.checkCreate(clientKey);
        int minutes = requireExpiration(expirationMinutes);
        String normalizedPassword = normalizePassword(password);
        validateFile(file);

        String storageKey = storage.store(file);
        try {
            String contentType = safeContentType(storage.probeContentType(storageKey));
            Created created = newShare(ShareType.FILE, minutes, normalizedPassword);
            created.share().setOriginalFilename(FilenameSanitizer.sanitize(file.getOriginalFilename()));
            created.share().setContentType(contentType);
            created.share().setFileSize(file.getSize());
            created.share().setStorageKey(storageKey);
            shares.save(created.share());
            log.info("event=share_created shareId={} type=FILE", created.share().getId());
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
    public ShareViewResponse view(String token, String accessToken) {
        Share share = requireAvailable(token);
        if (!hasAccess(share, accessToken)) {
            return ShareViewResponse.locked(share.getExpiresAt());
        }
        log.info("event=share_accessed shareId={} type={}", share.getId(), share.getType());
        return toView(share);
    }

    @Transactional(readOnly = true)
    public ShareAccessResponse verify(String token, String password) {
        Share share = requireAvailable(token);
        rateLimit.checkVerify(share.getShareToken());
        if (share.getPasswordHash() == null) {
            throw validation("This share is not password protected.");
        }
        if (!passwordMatches(password, share.getPasswordHash())) {
            log.info("event=password_verification_failed shareId={}", share.getId());
            throw new ApiException(ErrorCode.INVALID_PASSWORD, HttpStatus.UNAUTHORIZED, "Invalid password");
        }
        Instant now = Instant.now(clock);
        Instant accessExpiry = now.plus(properties.share().accessTokenTtl());
        if (accessExpiry.isAfter(share.getExpiresAt())) {
            accessExpiry = share.getExpiresAt();
        }
        String accessToken = accessTokens.issue(share.getShareToken(), accessExpiry);
        log.info("event=share_accessed shareId={} type={}", share.getId(), share.getType());
        return new ShareAccessResponse(accessToken, accessExpiry, toView(share));
    }

    @Transactional(readOnly = true)
    public ShareDownload download(String token, String accessToken) {
        Share share = requireAvailable(token);
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
    public void revoke(String token, String authorizationHeader) {
        Share share = requireKnown(token);
        if (share.getRevokedAt() != null) {
            return;
        }
        String presented = bearer(authorizationHeader);
        if (!tokens.matchesHash(presented, share.getManagementTokenHash())) {
            log.info("event=revoke_unauthorized shareId={}", share.getId());
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Unauthorized");
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
        if (share == null) {
            return;
        }
        if (share.getExpiresAt().isAfter(Instant.now(clock))) {
            return;
        }
        if (share.getStorageKey() != null) {
            storage.delete(share.getStorageKey());
        }
        shares.delete(share);
        log.info("event=share_expired shareId={}", share.getId());
    }

    private Created newShare(ShareType type, int expirationMinutes, String password) {
        String shareToken = allocateToken();
        String managementToken = tokens.newManagementToken();
        Instant now = Instant.now(clock);
        Share share = new Share();
        share.setId(UUID.randomUUID());
        share.setShareToken(shareToken);
        share.setType(type);
        share.setCreatedAt(now);
        share.setExpiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES));
        share.setManagementTokenHash(tokens.sha256Hex(managementToken));
        if (password != null) {
            share.setPasswordHash(passwordEncoder.encode(password));
        }
        return new Created(share, managementToken);
    }

    private String allocateToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = tokens.newShareToken();
            if (!shares.existsByShareToken(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a share token");
    }

    private Share requireAvailable(String token) {
        Share share = requireKnown(token);
        if (share.getRevokedAt() != null) {
            throw new ApiException(ErrorCode.SHARE_REVOKED, HttpStatus.GONE, "This share has been revoked.");
        }
        if (!share.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ApiException(ErrorCode.SHARE_EXPIRED, HttpStatus.GONE, "This share has expired.");
        }
        return share;
    }

    private Share requireKnown(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new ApiException(ErrorCode.SHARE_NOT_FOUND, HttpStatus.NOT_FOUND, "Share not found.");
        }
        return shares.findByShareToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.SHARE_NOT_FOUND, HttpStatus.NOT_FOUND, "Share not found."));
    }

    private boolean hasAccess(Share share, String accessToken) {
        if (share.getPasswordHash() == null) {
            return true;
        }
        return accessTokens.isValid(accessToken, share.getShareToken(), Instant.now(clock));
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
        long size = file.getSize();
        if (size < 0) {
            throw new ApiException(ErrorCode.INVALID_FILE, HttpStatus.BAD_REQUEST, "The upload could not be read.");
        }
        if (size > properties.share().maxFileSize()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 50 MB limit.");
        }
    }

    private int requireExpiration(Integer minutes) {
        if (minutes == null || !properties.share().allowedExpirationMinutes().contains(minutes)) {
            throw validation("Expiration must be one of: 15, 60, 360, 1440, 4320, 10080 minutes.");
        }
        return minutes;
    }

    private String normalizePassword(String password) {
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
        if (authorizationHeader == null) {
            return "";
        }
        String prefix = "Bearer ";
        if (authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorizationHeader.substring(prefix.length()).trim();
        }
        return "";
    }

    private ShareViewResponse toView(Share share) {
        boolean passwordProtected = share.getPasswordHash() != null;
        if (share.getType() == ShareType.TEXT) {
            return ShareViewResponse.text(share.getTextContent(), share.getExpiresAt(), passwordProtected);
        }
        return ShareViewResponse.file(
                share.getOriginalFilename(),
                share.getContentType(),
                share.getFileSize() == null ? 0 : share.getFileSize(),
                share.getExpiresAt(),
                passwordProtected);
    }

    private static ShareCreatedResponse toCreated(Created created) {
        Share share = created.share();
        return new ShareCreatedResponse(
                share.getShareToken(),
                "/s/" + share.getShareToken(),
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
