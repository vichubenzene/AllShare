package com.example.share.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizerTest {

    @Test
    void stripsPathTraversalFromUnixAndWindowsNames() {
        assertThat(FilenameSanitizer.sanitize("../../secret.txt")).isEqualTo("secret.txt");
        assertThat(FilenameSanitizer.sanitize("..\\..\\secret.txt")).isEqualTo("secret.txt");
        assertThat(FilenameSanitizer.sanitize("foo/../../etc/passwd")).isEqualTo("passwd");
    }

    @Test
    void replacesMissingAndHostileNames() {
        assertThat(FilenameSanitizer.sanitize(null)).isEqualTo("file");
        assertThat(FilenameSanitizer.sanitize("   ")).isEqualTo("file");
        assertThat(FilenameSanitizer.sanitize("..")).isEqualTo("file");
        assertThat(FilenameSanitizer.sanitize(".\r\n.\r\n")).isEqualTo("file");
    }

    @Test
    void removesControlCharactersAndKeepsAReadableName() {
        assertThat(FilenameSanitizer.sanitize("quarterly\r\nreport.pdf")).isEqualTo("quarterlyreport.pdf");
        assertThat(FilenameSanitizer.sanitize("notes (final).txt")).isEqualTo("notes (final).txt");
    }

    @Test
    void truncatesVeryLongNames() {
        String longName = "a".repeat(250) + ".txt";
        assertThat(FilenameSanitizer.sanitize(longName)).hasSize(200);
    }
}
