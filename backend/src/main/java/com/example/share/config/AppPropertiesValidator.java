package com.example.share.config;

import org.springframework.stereotype.Component;

@Component
public class AppPropertiesValidator {

    public AppPropertiesValidator(AppProperties properties) {
        if (properties.security() == null
                || properties.security().accessTokenSecret() == null
                || properties.security().accessTokenSecret().length() < 32) {
            throw new IllegalStateException("ACCESS_TOKEN_SECRET must be at least 32 characters");
        }
        if (properties.share() == null || properties.share().allowedExpirationMinutes().isEmpty()) {
            throw new IllegalStateException("At least one share expiration must be configured");
        }
        if (properties.share().maxFileSize() <= 0) {
            throw new IllegalStateException("MAX_FILE_SIZE must be positive");
        }
        if (properties.storage() == null
                || properties.storage().location() == null
                || properties.storage().location().isBlank()) {
            throw new IllegalStateException("STORAGE_LOCATION must be set");
        }
    }
}
