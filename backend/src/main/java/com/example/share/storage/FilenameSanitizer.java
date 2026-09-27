package com.example.share.storage;

public final class FilenameSanitizer {

    private FilenameSanitizer() {
    }

    public static String sanitize(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "");
        name = name.replaceAll("\\p{Cntrl}", "");
        name = name.replaceAll("[^A-Za-z0-9._ ()-]", "_");
        name = name.trim();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return "file";
        }
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name;
    }
}
