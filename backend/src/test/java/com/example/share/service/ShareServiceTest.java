package com.example.share.service;

import com.example.share.config.AppProperties;
import com.example.share.dto.CreateTextShareRequest;
import com.example.share.dto.ShareCreatedResponse;
import com.example.share.dto.ShareViewResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
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
    private static final String KEY = "0123456789abcdef0123456789abcdef";

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
        service = service(properties(50));
        lenient().when(repository.saveAndFlush(any(Share.class))).thenAnswer(invocation -> store(invocation.getArgument(0)));
        lenient().when(repository.save(any(Share.class))).thenAnswer(invocation -> store(invocation.getArgument(0)));
        lenient().when(repository.findByName(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get(invocation.getArgument(0))));
        lenient().when(repository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return saved.values().stream().filter(share -> id.equals(share.getId())).findFirst();
        });
    }

    @Test
    void createsATextShareUnderTheChosenName() {
        ShareCreatedResponse created = service.createText(text("Vivi", "Hello World", 60, null), "127.0.0.1");

        assertThat(created.name()).isEqualTo("vivi");
        assertThat(created.shareUrl()).isEqualTo("/vivi");
        assertThat(created.type()).isEqualTo("TEXT");
        assertThat(created.managementToken()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(created.passwordProtected()).isFalse();
        Share stored = saved.get("vivi");
        assertThat(stored.getTextContent()).isEqualTo("Hello World");
        assertThat(stored.getPasswordHash()).isNull();
        assertThat(stored.getManagementTokenHash()).isNotEqualTo(created.managementToken());
        assertThat(tokens.matchesHash(created.managementToken(), stored.getManagementTokenHash())).isTrue();

        ShareViewResponse view = service.view("vivi", null);
        assertThat(view.content()).isEqualTo("Hello World");
        assertThat(view.passwordRequired()).isFalse();
    }

    @Test
    void rejectsInvalidReservedAndMissingNames() {
        assertThat(code(() -> service.createText(text("", "x", 60, null), "ip"))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(code(() -> service.createText(text("my.share", "x", 60, null), "ip"))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(code(() -> service.createText(text("../etc", "x", 60, null), "ip"))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(code(() -> service.createText(text("api", "x", 60, null), "ip"))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(saved).isEmpty();
    }

    @Test
    void rejectsEmptyTextAndInvalidExpiration() {
        assertThat(code(() -> service.createText(text("vivi", "   ", 60, null), "ip"))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(code(() -> service.createText(text("vivi", "hello", 45, null), "ip"))).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(saved).isEmpty();
    }

    @Test
    void doesNotOverwriteALiveShareWithTheSameName() {
        service.createText(text("vivi", "original", 60, null), "ip");

        assertThatThrownBy(() -> service.createText(text("vivi", "replacement", 60, null), "ip"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.SHARE_NAME_TAKEN);
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getShareUrl()).isEqualTo("/vivi");
                });
        assertThat(saved.get("vivi").getTextContent()).isEqualTo("original");
    }

    @Test
    void reportsTheExistingFileUrlWhenTheNameIsTaken() {
        MultipartFile file = file("report.pdf", 24);
        when(storage.store(file)).thenReturn(KEY);
        when(storage.probeContentType(KEY)).thenReturn("application/pdf");
        service.createFile("vivi", file, 60, null, "ip");

        assertThatThrownBy(() -> service.createText(text("vivi", "text", 60, null), "ip"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getShareUrl()).isEqualTo("/vivi.pdf"));
    }

    @Test
    void mapsAConcurrentUniqueViolationToNameTaken() {
        when(repository.saveAndFlush(any(Share.class))).thenThrow(new DataIntegrityViolationException(
                "insert failed",
                new SQLException("duplicate key value violates unique constraint \"uk_shares_name\"")));

        assertThat(code(() -> service.createText(text("vivi", "hello", 60, null), "ip")))
                .isEqualTo(ErrorCode.SHARE_NAME_TAKEN);
    }

    @Test
    void reusesTheNameOfAnExpiredShare() {
        service.createText(text("vivi", "old", 60, null), "ip");
        Share old = saved.get("vivi");
        old.setExpiresAt(NOW.minusSeconds(1));

        service.createText(text("vivi", "new", 60, null), "ip");

        verify(repository).delete(old);
        verify(repository).flush();
        assertThat(saved.get("vivi").getTextContent()).isEqualTo("new");
    }

    @Test
    void createsAFileShareWhoseUrlCarriesTheOriginalExtension() {
        MultipartFile file = file("../../Report.PDF", 24);
        when(storage.store(file)).thenReturn(KEY);
        when(storage.probeContentType(KEY)).thenReturn("application/pdf");

        ShareCreatedResponse created = service.createFile("vivi", file, 60, null, "ip");

        assertThat(created.shareUrl()).isEqualTo("/vivi.pdf");
        Share stored = saved.get("vivi");
        assertThat(stored.getOriginalFilename()).isEqualTo("Report.PDF");
        assertThat(stored.getExtension()).isEqualTo("pdf");
        assertThat(stored.getStorageKey()).isEqualTo(KEY);
        assertThat(stored.getContentType()).isEqualTo("application/pdf");

        assertThat(service.view("vivi.pdf", null).filename()).isEqualTo("Report.PDF");
        assertThat(service.view("vivi", null).shareUrl()).isEqualTo("/vivi.pdf");
        assertThat(code(() -> service.view("vivi.png", null))).isEqualTo(ErrorCode.SHARE_NOT_FOUND);
    }

    @Test
    void fileWithoutAnExtensionUsesThePlainName() {
        MultipartFile file = file("README", 4);
        when(storage.store(file)).thenReturn(KEY);
        when(storage.probeContentType(KEY)).thenReturn("text/plain");

        assertThat(service.createFile("notes", file, 60, null, "ip").shareUrl()).isEqualTo("/notes");
    }

    @Test
    void deletesTheStoredFileWhenTheNameIsTaken() {
        service.createText(text("vivi", "original", 60, null), "ip");
        MultipartFile file = file("report.pdf", 24);
        when(storage.store(file)).thenReturn(KEY);
        when(storage.probeContentType(KEY)).thenReturn("application/pdf");

        assertThat(code(() -> service.createFile("vivi", file, 60, null, "ip"))).isEqualTo(ErrorCode.SHARE_NAME_TAKEN);
        verify(storage).delete(KEY);
    }

    @Test
    void protectedShareNeedsTheCorrectPasswordBeforeShowingContent() {
        ShareCreatedResponse created = service.createText(text("secret", "note", 15, "correct-horse"), "ip");
        Share stored = saved.get("secret");

        assertThat(created.passwordProtected()).isTrue();
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse");
        ShareViewResponse locked = service.view("secret", null);
        assertThat(locked.passwordRequired()).isTrue();
        assertThat(locked.content()).isNull();

        assertThatThrownBy(() -> service.verify("secret", "wrong"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_PASSWORD);
                    assertThat(ex.getMessage()).isEqualTo("Incorrect password.");
                });

        var access = service.verify("secret", "correct-horse");
        assertThat(access.share().content()).isEqualTo("note");
        assertThat(service.view("secret", access.accessToken()).content()).isEqualTo("note");
    }

    @Test
    void protectedFileCannotBeDownloadedWithoutAccess() {
        MultipartFile file = file("report.pdf", 24);
        when(storage.store(file)).thenReturn(KEY);
        when(storage.probeContentType(KEY)).thenReturn("application/pdf");
        service.createFile("locked", file, 60, "pw", "ip");

        assertThat(code(() -> service.download("locked.pdf", null))).isEqualTo(ErrorCode.PASSWORD_REQUIRED);
        verify(storage, never()).load(anyString());
    }

    @Test
    void hidesExpiredAndRevokedShares() {
        service.createText(text("temp", "temporary", 60, null), "ip");
        saved.get("temp").setExpiresAt(NOW.minusSeconds(1));
        assertThat(code(() -> service.view("temp", null))).isEqualTo(ErrorCode.SHARE_EXPIRED);

        saved.get("temp").setExpiresAt(NOW.plusSeconds(60));
        saved.get("temp").setRevokedAt(NOW);
        assertThat(code(() -> service.view("temp", null))).isEqualTo(ErrorCode.SHARE_REVOKED);
    }

    @Test
    void unknownNamesAreNotFound() {
        assertThat(code(() -> service.view("nope", null))).isEqualTo(ErrorCode.SHARE_NOT_FOUND);
        assertThat(code(() -> service.view("../secret", null))).isEqualTo(ErrorCode.SHARE_NOT_FOUND);
    }

    @Test
    void revokeRequiresTheManagementTokenNotTheName() {
        ShareCreatedResponse created = service.createText(text("keep", "keep me", 60, null), "ip");

        assertThat(code(() -> service.revoke("keep", null))).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(code(() -> service.revoke("keep", "Bearer keep"))).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(saved.get("keep").getRevokedAt()).isNull();

        service.revoke("keep", "Bearer " + created.managementToken());
        assertThat(saved.get("keep").getRevokedAt()).isEqualTo(NOW);
        assertThat(saved.get("keep").getTextContent()).isNull();
        assertThat(code(() -> service.view("keep", null))).isEqualTo(ErrorCode.SHARE_REVOKED);
    }

    @Test
    void revokeDeletesTheStoredFile() {
        MultipartFile file = file("report.pdf", 24);
        when(storage.store(file)).thenReturn(KEY);
        when(storage.probeContentType(KEY)).thenReturn("application/pdf");
        ShareCreatedResponse created = service.createFile("doc", file, 60, null, "ip");

        service.revoke("doc.pdf", "Bearer " + created.managementToken());

        verify(storage).delete(KEY);
        assertThat(saved.get("doc").getStorageKey()).isNull();
    }

    @Test
    void rejectsOversizedFilesBeforeStoringThem() {
        ShareService limited = service(properties(16));

        assertThat(code(() -> limited.createFile("big", file("big.bin", 17), 60, null, "ip")))
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
        verify(storage, never()).store(any());
    }

    @Test
    void stopsCreatingWhenTheRateLimitIsExceeded() {
        doThrow(new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, "Too many requests."))
                .when(rateLimit).checkCreate(anyString());

        assertThat(code(() -> service.createText(text("vivi", "hello", 60, null), "ip"))).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(saved).isEmpty();
    }

    @Test
    void cleanupRemovesExpiredFilesAndRows() {
        service.createText(text("gone", "gone", 60, null), "ip");
        Share stored = saved.get("gone");
        stored.setExpiresAt(NOW.minusSeconds(5));
        stored.setStorageKey(KEY);
        when(repository.findExpiredIds(any(), any())).thenReturn(List.of(stored.getId()), List.of());

        service.cleanupExpired();

        verify(storage).delete(KEY);
        verify(repository).delete(stored);
    }

    private Share store(Share share) {
        saved.put(share.getName(), share);
        return share;
    }

    private ShareService service(AppProperties properties) {
        return new ShareService(
                repository,
                storage,
                passwordEncoder,
                tokens,
                new AccessTokenService(properties),
                rateLimit,
                properties,
                clock);
    }

    private static ErrorCode code(Runnable action) {
        try {
            action.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        throw new AssertionError("Expected an ApiException");
    }

    private static CreateTextShareRequest text(String name, String content, int minutes, String password) {
        return new CreateTextShareRequest(name, ShareType.TEXT, content, minutes, password);
    }

    private static MultipartFile file(String filename, long size) {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getSize()).thenReturn(size);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        return file;
    }

    static AppProperties properties(long maxFileSize) {
        return new AppProperties(
                new AppProperties.Storage("./data/uploads"),
                new AppProperties.Share(maxFileSize, 1_000, Duration.ofMinutes(15), 60_000, "15,60,360,1440,4320,10080"),
                new AppProperties.Security("dev-only-access-token-secret-change-me"),
                new AppProperties.RateLimit(20, 10),
                new AppProperties.Cors("http://localhost:5173"),
                new AppProperties.RequestLog(true));
    }
}
