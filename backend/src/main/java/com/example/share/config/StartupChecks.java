package com.example.share.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL is checked by Flyway at startup. Redis and MongoDB connect lazily, so ping them here. */
@Component
public class StartupChecks implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupChecks.class);

    private final StringRedisTemplate redis;
    private final MongoTemplate mongo;
    private final AppProperties properties;

    public StartupChecks(StringRedisTemplate redis, MongoTemplate mongo, AppProperties properties) {
        this.redis = redis;
        this.mongo = mongo;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("event=startup_check service=postgresql status=ok");
        try {
            redis.execute((RedisCallback<String>) RedisConnection::ping);
            log.info("event=startup_check service=redis status=ok");
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Redis is not reachable. Start Redis or set REDIS_HOST and REDIS_PORT.", ex);
        }
        if (!properties.requestLog().enabled()) {
            log.warn("event=startup_check service=mongodb status=disabled msg=REQUEST_LOG_ENABLED=false, request logs are not stored");
            return;
        }
        try {
            mongo.getDb().runCommand(new Document("ping", 1));
            log.info("event=startup_check service=mongodb status=ok database={}", mongo.getDb().getName());
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "MongoDB is not reachable for request logging. Start MongoDB, set MONGODB_URI, "
                            + "or set REQUEST_LOG_ENABLED=false to run without request logs.", ex);
        }
    }
}
