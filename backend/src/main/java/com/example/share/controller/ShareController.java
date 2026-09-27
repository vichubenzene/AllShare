package com.example.share.controller;

import com.example.share.dto.CreateTextShareRequest;
import com.example.share.dto.ShareAccessResponse;
import com.example.share.dto.ShareCreatedResponse;
import com.example.share.dto.ShareDownload;
import com.example.share.dto.ShareViewResponse;
import com.example.share.dto.VerifyPasswordRequest;
import com.example.share.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
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

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService shares;

    public ShareController(ShareService shares) {
        this.shares = shares;
    }

    @PostMapping
    public ResponseEntity<ShareCreatedResponse> createText(
            @RequestBody CreateTextShareRequest request,
            HttpServletRequest http
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(shares.createText(request, http.getRemoteAddr()));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ShareCreatedResponse> createFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "expirationMinutes", required = false) Integer expirationMinutes,
            @RequestParam(value = "password", required = false) String password,
            HttpServletRequest http
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(shares.createFile(file, expirationMinutes, password, http.getRemoteAddr()));
    }

    @GetMapping("/{token}")
    public ResponseEntity<ShareViewResponse> view(
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Access", required = false) String accessToken
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shares.view(token, accessToken));
    }

    @PostMapping("/{token}/verify")
    public ResponseEntity<ShareAccessResponse> verify(
            @PathVariable String token,
            @RequestBody VerifyPasswordRequest request
    ) {
        String password = request == null ? null : request.password();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shares.verify(token, password));
    }

    @GetMapping("/{token}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Access", required = false) String accessToken
    ) {
        ShareDownload download = shares.download(token, accessToken);
        MediaType mediaType = mediaType(download.contentType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        if (download.size() >= 0) {
            builder.contentLength(download.size());
        }
        return builder.body(download.resource());
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<Void> revoke(
            @PathVariable String token,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        shares.revoke(token, authorization);
        return ResponseEntity.noContent().build();
    }

    private static MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
