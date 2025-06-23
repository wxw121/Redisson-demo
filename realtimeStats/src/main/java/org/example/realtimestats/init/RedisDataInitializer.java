package org.example.realtimestats.init;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.config.RealtimeStatsProperties;
import org.redisson.api.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisDataInitializer implements CommandLineRunner {

    private final RedissonClient redissonClient;
    private final RealtimeStatsProperties properties;

    public RedisDataInitializer(RedissonClient redissonClient,
                              RealtimeStatsProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Starting Redis data initialization...");
            RBatch batch = redissonClient.createBatch();

            // 初始化点赞数据
            initLikeData(batch);

            // 初始化浏览数据
            initViewData(batch);

            // 执行批量操作
            batch.execute();
            log.info("Redis data initialization completed successfully");
        } catch (Exception e) {
            log.error("Redis data initialization failed", e);
            throw new RuntimeException("Failed to initialize Redis data", e);
        }
    }

    private void initLikeData(RBatch batch) {
        // 文章1点赞数据
        initLikeRecord(batch, 1, Map.of(
            "1001", 1,  // 用户1001点赞
            "1002", 1   // 用户1002点赞
        ), 2);  // 总点赞数2

        // 文章2点赞数据
        initLikeRecord(batch, 2, Map.of(
            "1003", 0   // 用户1003取消点赞
        ), 0);  // 总点赞数0

        // 文章3点赞数据(初始无点赞)
        initLikeRecord(batch, 3, Map.of(), 0);
    }

    private void initLikeRecord(RBatch batch, int articleId,
                              Map<String, Integer> userLikes, long totalLikes) {
        String recordKey = properties.getRedisKeyPrefix().getLike() + "record:" + articleId;
        String countKey = properties.getRedisKeyPrefix().getLike() + "count:" + articleId;
        long expireDays = 1;

        // 初始化点赞记录
        RMapAsync<Object, Object> record = batch.getMap(recordKey);
        record.putAllAsync(userLikes);
        record.expireAsync(expireDays, TimeUnit.DAYS);

        // 初始化点赞总数
        RAtomicLong count = (RAtomicLong) batch.getAtomicLong(countKey);
        count.setAsync(totalLikes);
        count.expireAsync(expireDays, TimeUnit.DAYS);
    }

    private void initViewData(RBatch batch) {
        // 文章1浏览数据
        initViewCount(batch, 1, 5);  // 浏览数5

        // 文章2浏览数据
        initViewCount(batch, 2, 3);  // 浏览数3

        // 文章3浏览数据
        initViewCount(batch, 3, 1);  // 浏览数1
    }

    private void initViewCount(RBatch batch, int articleId, long viewCount) {
        String key = properties.getRedisKeyPrefix().getPageView() + "count:" + articleId;
        long expireDays = 1;

        RAtomicLong count = (RAtomicLong) batch.getAtomicLong(key);
        count.setAsync(viewCount);
        count.expireAsync(expireDays, TimeUnit.DAYS);
    }
}
