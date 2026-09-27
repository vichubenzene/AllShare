package com.example.share.storage;

import com.example.share.exception.ApiException;
import com.example.share.support.BytesMultipartFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path temp;

    @Test
    void storesRetrievesAndDeletesWithoutUsingTheOriginalFilename() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(temp);
        byte[] pdf = "%PDF-1.4\ntrailer\n".getBytes();
        BytesMultipartFile file = new BytesMultipartFile("../../secret.txt", "image/png", pdf);

        String key = storage.store(file);

        assertThat(key).matches("[a-f0-9]{32}");
        assertThat(key).doesNotContain("secret");
        Path stored = temp.resolve(key);
        assertThat(Files.isRegularFile(stored)).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(pdf);
        assertThat(Files.exists(temp.resolve("secret.txt"))).isFalse();
        assertThat(Files.exists(temp.getParent().resolve("secret.txt"))).isFalse();

        assertThat(storage.probeContentType(key)).isEqualTo("application/pdf");
        assertThat(storage.load(key).getInputStream().readAllBytes()).isEqualTo(pdf);

        storage.delete(key);
        assertThat(Files.exists(stored)).isFalse();
        storage.delete(key);
    }

    @Test
    void rejectsPathTraversalStorageKeys() {
        LocalFileStorageService storage = new LocalFileStorageService(temp);

        assertThatThrownBy(() -> storage.load("../abcdefghijklmnopabcdefghijklmnop"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> storage.load("../../secret.txt"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> storage.load("not-a-key"))
                .isInstanceOf(ApiException.class);
    }
}
