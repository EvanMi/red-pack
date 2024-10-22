package com.yumi.read_pack.redis.server;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.embedded.RedisServer;

@Component
public class RedisServerManager {
    private static Logger logger = LoggerFactory.getLogger(RedisServerManager.class);

    private RedisServer redisServer;

    @PostConstruct
    public void init() {
        try {
            this.redisServer = new RedisServer(6379);
            this.redisServer.start();
            logger.info("redisServer start");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (null != this.redisServer) {
            redisServer.stop();
            logger.info("redisServer stop");
        }
    }

    public RedisServer getRedisServer() {
        return redisServer;
    }
}
