package com.example.share.service;

import com.example.share.config.AppProperties;
import com.example.share.dto.CreateTextShareRequest;
import com.example.share.dto.ShareCreatedResponse;
import com.example.share.entity.Share;
import com.example.share.entity.ShareType;
import com.example.share.exception.ApiException;
import com.example.share.exception.ErrorCode;
import com.example.share.repository.ShareRepository;
import com.example.share.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @Mock
    private ShareRepository repository;
    @Mock
    private FileStorageService storage;
    @Mock
    private RateLimitService rateLimit;

    private final Map<String, Share> saved = new HashMap<>();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final TokenService tokens = new TokenService();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private ShareService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = properties(50);
        service = new ShareService(
                repository,
                storage,
                passwordEncoder,
                tokens,
                new AccessTokenService(properties),
                rateLimit,
                properties,
                clock);
        lenient().when(repository.existsByShareToken(anyString()))
                .thenAnswer(invocation -> saved.containsKey(invocation.getArgument(0)));
        lenient().when(repository.save(any(Share.class))).thenAnswer(invocation -> {
            Share share = invocation.getArgument(0);
            saved.put(share.getShareToken(), share);
            return share;
        });
        lenient().when(repository.findByShareToken(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get(invocation.getArgument(0))));
        lenient().when(repository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return saved.values().stream().filter(share -> id.equals(share.getId())).findFirst();
        });
    }

    @Test
    void createsATextShareWithARandomToken() {
        ShareCreatedResponse created = service.createText(text("Hello World", 60, null), "127.0.0.1");

        assertThat(created.token()).hasSize(12).matches("[A-Za-z0-9]+");
        assertThat(created.shareUrl()).isEqualTo("/s/" + created.token());
        assertThat(created.managementToken()).isNotEqualTo(created.token());
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(created.passwordProtected()).isFalse();
        Share stored = saved.get(created.token());
        assertThat(stored.getType()).isEqualTo(ShareType.TEXT);
        assertThat(stored.getTextContent()).isEqualTo("Hello World");
        assertThat(stored.getPasswordHash()).isNull();
        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getManagementTokenHash()).isNotEqualTo(created.managementToken());
        assertThat(tokens.matchesHash(created.managementToken(), stored.getManagementTokenHash())).isTrue();
        verify(storage, never()).store(any());
    }

    @Test
    void rejectsEmptyTextAndInvalidExpiration() {
        assertThatThrownBy(() -> service.createText(text("   ", 60, null), "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.createText(text("hello", 45, null), "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(saved).isEmpty();
    }

    @Test
    void storesAPasswordHashAndUnlocksWithTheCorrectPassword() {
        ShareCreatedResponse created = service.createText(text("secret note", 15, "correct-horse"), "127.0.0.1");
        Share stored = saved.get(created.token());

        assertThat(created.passwordProtected()).isTrue();
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse");
        assertThat(passwordEncoder.matches("correct-horse", stored.getPasswordHash())).isTrue();
        assertThat(service.view(created.token(), null).passwordRequired()).isTrue();
        assertThat(service.view(created.token(), null).content()).isNull();

        var access = service.verify(created.token(), "correct-horse");
        assertThat(access.share().content()).isEqualTo("secret note");
        assertThat(service.view(created.token(), access.accessToken()).content()).isEqualTo("secret note");
    }

    @Test
    void rejectsAnIncorrectPasswordWithoutRevealingTheContent() {
        ShareCreatedResponse created = service.createText(text("secret note", 15, "correct-horse"), "127.0.0.1");

        assertThatThrownBy(() -> service.verify(created.token(), "wrong"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid password")
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
    }

    @Test
    void hidesExpiredAndRevokedShares() {
        ShareCreatedResponse created = service.createText(text("temporary", 60, null), "127.0.0.1");
        saved.get(created.token()).setExpiresAt(NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.view(created.token(), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_EXPIRED);

        saved.get(created.token()).setExpiresAt(NOW.plusSeconds(60));
        saved.get(created.token()).setRevokedAt(NOW);
        assertThatThrownBy(() -> service.view(created.token(), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_REVOKED);
    }

    @Test
    void rejectsUnknownTokensAndUnauthorizedRevoke() {
        assertThatThrownBy(() -> service.view("nope", null))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_NOT_FOUND);
        assertThatThrownBy(() -> service.view("../secret", null))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_NOT_FOUND);

        ShareCreatedResponse created = service.createText(text("keep", 60, null), "127.0.0.1");
        assertThatThrownBy(() -> service.revoke(created.token(), "Bearer " + created.token()))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(saved.get(created.token()).getRevokedAt()).isNull();
        assertThat(saved.get(created.token()).getTextContent()).isEqualTo("keep");
    }

    @Test
    void revokeRequiresTheManagementTokenAndDeletesFileContent() {
        MultipartFile file = file("../../secret.txt", "image/png", 24);
        when(storage.store(file)).thenReturn("0123456789abcdef0123456789abcdef");
        when(storage.probeContentType("0123456789abcdef0123456789abcdef")).thenReturn("application/pdf");

        ShareCreatedResponse created = service.createFile(file, 60, null, "127.0.0.1");
        Share stored = saved.get(created.token());
        assertThat(stored.getOriginalFilename()).isEqualTo("secret.txt");
        assertThat(stored.getContentType()).isEqualTo("application/pdf");
        assertThat(stored.getStorageKey()).isEqualTo("0123456789abcdef0123456789abcdef");

        service.revoke(created.token(), "Bearer " + created.managementToken());

        verify(storage).delete("0123456789abcdef0123456789abcdef");
        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        assertThat(stored.getStorageKey()).isNull();
        assertThatThrownBy(() -> service.view(created.token(), null))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_REVOKED);
    }

    @Test
    void rejectsOversizedFilesBeforeStoringThem() {
        ShareService limited = new ShareService(
                repository,
                storage,
                passwordEncoder,
                tokens,
                new AccessTokenService(properties(16)),
                rateLimit,
                properties(16),
                clock);
        MultipartFile file = file("big.bin", "application/octet-stream", 17);

        assertThatThrownBy(() -> limited.createFile(file, 60, null, "127.0.0.1"))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
        verify(storage, never()).store(any());
    }

    @Test
    void deletesTheStoredFileWhenTheDatabaseSaveFails() {
        MultipartFile file = file("notes.txt", "text/plain", 4);
        when(storage.store(file)).thenReturn("abcdef0123456789abcdef0123456789");
        when(storage.probeContentType(anyString())).thenReturn("text/plain");
        when(repository.save(any(Share.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.createFile(file, 60, null, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete("abcdef0123456789abcdef0123456789");
    }

    @Test
    void stopsCreatingWhenTheRateLimitIsExceeded() {
        doThrow(new ApiException(ErrorCode.RATE_LIMITED, org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later."))
                .when(rateLimit).checkCreate(anyString());

        assertThatThrownBy(() -> service.createText(text("hello", 60, null), "127.0.0.1"))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(saved).isEmpty();
    }

    @Test
    void purgeRemovesExpiredFilesAndRows() {
        ShareCreatedResponse created = service.createText(text("gone", 60, null), "127.0.0.1");
        Share stored = saved.get(created.token());
        stored.setExpiresAt(NOW.minusSeconds(5));
        stored.setStorageKey("0123456789abcdef0123456789abcdef");
        when(repository.findExpiredIds(any(), any())).thenReturn(java.util.List.of(stored.getId()), java.util.List.of());

        service.cleanupExpired();

        verify(storage).delete("0123456789abcdef0123456789abcdef");
        verify(repository).delete(stored);
    }

    private static CreateTextShareRequest text(String content, int minutes, String password) {
        return new CreateTextShareRequest(ShareType.TEXT, content, minutes, password);
    }

    private static MultipartFile file(String filename, String contentType, long size) {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getSize()).thenReturn(size);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        lenient().when(file.getContentType()).thenReturn(contentType);
        return file;
    }

    private static AppProperties properties(long maxFileSize) {
        return new AppProperties(
                new AppProperties.Storage("./data/uploads"),
                new AppProperties.Share(maxFileSize, 1_000, Duration.ofMinutes(15), 60_000, "15,60,360,1440,4320,10080"),
                new AppProperties.Security("dev-only-access-token-secret-change-me"),
                new AppProperties.RateLimit(20, 10),
                new AppProperties.Cors("http://localhost:5173"));
    }
}
