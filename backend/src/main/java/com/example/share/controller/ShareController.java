package com.example.share.controller;

import com.example.share.dto.CreateTextShareRequest;
import com.example.share.dto.ShareAccessResponse;
import com.example.share.dto.ShareCreatedResponse;
import com.example.share.dto.ShareDownload;
import com.example.share.dto.ShareViewResponse;
import com.example.share.dto.VerifyPasswordRequest;
import com.example.share.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * {@code {slug}} is the public path without the leading slash: {@code vivi} or {@code vivi.pdf}.
 */
@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private static final String ACCESS_HEADER = "X-Share-Access";

    private final ShareService shares;

    public ShareController(ShareService shares) {
        this.shares = shares;
    }

    @PostMapping
    public ResponseEntity<ShareCreatedResponse> createText(
            @RequestBody CreateTextShareRequest request,
            HttpServletRequest http
    ) {
        return created(shares.createText(request, http.getRemoteAddr()));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ShareCreatedResponse> createFile(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "expirationMinutes", required = false) Integer expirationMinutes,
            @RequestParam(value = "password", required = false) String password,
            HttpServletRequest http
    ) {
        return created(shares.createFile(name, file, expirationMinutes, password, http.getRemoteAddr()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ShareViewResponse> view(
            @PathVariable String slug,
            @RequestHeader(value = ACCESS_HEADER, required = false) String accessToken
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shares.view(slug, accessToken));
    }

    @PostMapping("/{slug}/verify")
    public ResponseEntity<ShareAccessResponse> verify(
            @PathVariable String slug,
            @RequestBody(required = false) VerifyPasswordRequest request
    ) {
        String password = request == null ? null : request.password();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shares.verify(slug, password));
    }

    @GetMapping("/{slug}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String slug,
            @RequestHeader(value = ACCESS_HEADER, required = false) String accessToken
    ) {
        ShareDownload download = shares.download(slug, accessToken);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        if (download.size() >= 0) {
            builder.contentLength(download.size());
        }
        return builder.body(download.resource());
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> revoke(
            @PathVariable String slug,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        shares.revoke(slug, authorization);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<ShareCreatedResponse> created(ShareCreatedResponse body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private static MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
