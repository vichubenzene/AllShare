package com.example.share.scheduler;

import com.example.share.service.ShareService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShareCleanupScheduler {

    private final ShareService shares;

    public ShareCleanupScheduler(ShareService shares) {
        this.shares = shares;
    }

    @Scheduled(fixedDelayString = "${app.share.cleanup-delay-ms:60000}")
    public void cleanup() {
        shares.cleanupExpired();
    }
}
