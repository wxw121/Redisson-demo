package org.example.realtimestats.config;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置类
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduleConfig {

    @Autowired
    private ContentService contentService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private PageViewService pageViewService;

    @Autowired
    private GamePlayerService gamePlayerService;

    @Autowired
    private HotSearchService hotSearchService;

    @Value("${realtime-stats.sync.interval:300000}")
    private long syncInterval;

    /**
     * 定时同步内容计数器数据到数据库
     * 默认每5分钟执行一次
     */
    @Scheduled(fixedRateString = "${realtime-stats.sync.interval:300000}")
    public void syncContentCounters() {
        try {
            log.info("开始同步内容计数器数据到数据库...");
            contentService.batchSyncCountersToDb();
            log.info("同步内容计数器数据到数据库完成");
        } catch (Exception e) {
            log.error("同步内容计数器数据到数据库失败", e);
        }
    }

    /**
     * 定时同步点赞数据到数据库
     * 默认每5分钟执行一次
     */
    @Scheduled(fixedRateString = "${realtime-stats.sync.interval:300000}")
    public void syncLikeData() {
        try {
            log.info("开始同步点赞数据到数据库...");
            likeService.syncAllLikeDataToDb();
            log.info("同步点赞数据到数据库完成");
        } catch (Exception e) {
            log.error("同步点赞数据到数据库失败", e);
        }
    }

    /**
     * 定时同步页面访问数据到数据库
     * 默认每5分钟执行一次
     */
    @Scheduled(fixedRateString = "${realtime-stats.sync.interval:300000}")
    public void syncPageViewData() {
        try {
            log.info("开始同步页面访问数据到数据库...");
            pageViewService.syncAllPageViewDataToDb();
            log.info("同步页面访问数据到数据库完成");
        } catch (Exception e) {
            log.error("同步页面访问数据到数据库失败", e);
        }
    }

    /**
     * 定时同步游戏积分数据到数据库
     * 默认每5分钟执行一次
     */
    @Scheduled(fixedRateString = "${realtime-stats.sync.interval:300000}")
    public void syncGamePlayerScoreData() {
        try {
            log.info("开始同步游戏积分数据到数据库...");
            gamePlayerService.syncScoresToDb();
            log.info("同步游戏积分数据到数据库完成");
        } catch (Exception e) {
            log.error("同步游戏积分数据到数据库失败", e);
        }
    }

    /**
     * 定时同步热搜数据到数据库
     * 默认每5分钟执行一次
     */
    @Scheduled(fixedRateString = "${realtime-stats.sync.interval:300000}")
    public void syncHotSearchData() {
        try {
            log.info("开始同步热搜数据到数据库...");
            hotSearchService.syncHotSearchesToDb();
            log.info("同步热搜数据到数据库完成");
        } catch (Exception e) {
            log.error("同步热搜数据到数据库失败", e);
        }
    }
}