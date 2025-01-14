package com.github.swce.tests.config;

import java.io.IOException;
import java.net.Socket;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import redis.embedded.RedisServer;

/**
 * Embedded redis configuration.
 *
 * @author Marcos Barbero
 * @since 2017-06-27
 */
@Configuration
public class RedisConfig {

    private static final int DEFAULT_PORT = 6379;

    private RedisServer redisServer;

    private static boolean available(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }

    @PostConstruct
    public void setUp() throws IOException {
        this.redisServer = new RedisServer(DEFAULT_PORT);
        if (available(DEFAULT_PORT)) {
            this.redisServer.start();
        }
    }

    @PreDestroy
    public void destroy() {
        this.redisServer.stop();
    }
}
