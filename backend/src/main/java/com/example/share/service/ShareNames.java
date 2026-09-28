package com.example.share.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Share names are the public identifier: {@code /vivi} for text and {@code /vivi.pdf} for files.
 * Names cannot contain dots, so everything after the first dot in a public path is the extension.
 */
public final class ShareNames {

    public static final String RULES =
            "Share names use lowercase letters, numbers, '-' and '_' (1-63 characters, starting with a letter or number).";

    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,62}$");
    private static final Pattern EXTENSION = Pattern.compile("^[a-z0-9]{1,10}$");
    private static final Set<String> RESERVED = Set.of(
            "api", "created", "assets", "src", "node_modules", "favicon", "robots", "index", "file", "static", "public");

    private ShareNames() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static boolean isReserved(String name) {
        return RESERVED.contains(name);
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSION.matcher(extension).matches() ? extension : null;
    }

    public static String publicPath(String name, String extension) {
        return extension == null ? "/" + name : "/" + name + "." + extension;
    }

    /** Returns null when the path cannot be a share. */
    public static Slug parse(String slug) {
        if (slug == null) {
            return null;
        }
        String value = slug.toLowerCase(Locale.ROOT);
        int dot = value.indexOf('.');
        String name = dot < 0 ? value : value.substring(0, dot);
        String extension = dot < 0 ? null : value.substring(dot + 1);
        if (!isValid(name) || (extension != null && !EXTENSION.matcher(extension).matches())) {
            return null;
        }
        return new Slug(name, extension);
    }

    public record Slug(String name, String extension) {
    }
}
