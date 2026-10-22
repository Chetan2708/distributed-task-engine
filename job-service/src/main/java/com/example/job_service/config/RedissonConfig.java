package com.example.job_service.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        try {
            Config config = new Config();
            config.useSingleServer()
                    .setAddress("redis://" + redisHost + ":" + redisPort)
                    .setConnectTimeout(3000)
                    .setRetryAttempts(2);
            return Redisson.create(config);
        } catch (Exception e) {
            log.warn("Redis is not available at {}:{} - SSE fallback to in-memory events only: {}", redisHost, redisPort, e.getMessage());
            return null;
        }
    }
}
