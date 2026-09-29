package com.example.share.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShareNamesTest {

    @Test
    void acceptsSimpleNamesAndRejectsPathsDotsAndSpaces() {
        assertThat(ShareNames.isValid("vivi")).isTrue();
        assertThat(ShareNames.isValid("my-notes_2")).isTrue();
        assertThat(ShareNames.isValid("a")).isTrue();
        assertThat(ShareNames.isValid("-vivi")).isFalse();
        assertThat(ShareNames.isValid("vi.vi")).isFalse();
        assertThat(ShareNames.isValid("vi vi")).isFalse();
        assertThat(ShareNames.isValid("../vivi")).isFalse();
        assertThat(ShareNames.isValid("Vivi")).isFalse();
        assertThat(ShareNames.isValid("x".repeat(64))).isFalse();
        assertThat(ShareNames.normalize("  Vivi ")).isEqualTo("vivi");
    }

    @Test
    void reservesRoutesUsedByTheApp() {
        assertThat(ShareNames.isReserved("api")).isTrue();
        assertThat(ShareNames.isReserved("created")).isTrue();
        assertThat(ShareNames.isReserved("vivi")).isFalse();
    }

    @Test
    void takesTheExtensionFromTheUploadedFilename() {
        assertThat(ShareNames.extensionOf("report.pdf")).isEqualTo("pdf");
        assertThat(ShareNames.extensionOf("Photo.JPG")).isEqualTo("jpg");
        assertThat(ShareNames.extensionOf("backup.tar.gz")).isEqualTo("gz");
        assertThat(ShareNames.extensionOf("README")).isNull();
        assertThat(ShareNames.extensionOf(".env")).isNull();
        assertThat(ShareNames.extensionOf("weird.p d f")).isNull();
    }

    @Test
    void parsesPublicPaths() {
        assertThat(ShareNames.parse("vivi")).isEqualTo(new ShareNames.Slug("vivi", null));
        assertThat(ShareNames.parse("VIVI.PDF")).isEqualTo(new ShareNames.Slug("vivi", "pdf"));
        assertThat(ShareNames.parse("vivi.tar.gz")).isNull();
        assertThat(ShareNames.parse("..")).isNull();
        assertThat(ShareNames.publicPath("vivi", "pdf")).isEqualTo("/vivi.pdf");
        assertThat(ShareNames.publicPath("vivi", null)).isEqualTo("/vivi");
    }
}
