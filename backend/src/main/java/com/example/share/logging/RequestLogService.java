package com.example.share.logging;

import com.example.share.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Writes request logs to MongoDB on a background thread so a slow or unavailable MongoDB
 * never delays share requests. When the queue is full, new entries are dropped.
 */
@Service
public class RequestLogService {

    public static final String COLLECTION = "request_logs";

    private static final Logger log = LoggerFactory.getLogger(RequestLogService.class);

    private final MongoTemplate mongo;
    private final boolean enabled;
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1000),
            runnable -> {
                Thread thread = new Thread(runnable, "request-log-writer");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    public RequestLogService(MongoTemplate mongo, AppProperties properties) {
        this.mongo = mongo;
        this.enabled = properties.requestLog().enabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void record(RequestLogEntry entry) {
        if (!enabled) {
            return;
        }
        writer.execute(() -> {
            try {
                mongo.insert(entry.toDocument(), COLLECTION);
            } catch (RuntimeException ex) {
                log.warn("event=request_log_write_failed type={}", ex.getClass().getSimpleName());
            }
        });
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        writer.shutdown();
        writer.awaitTermination(5, TimeUnit.SECONDS);
    }
}
