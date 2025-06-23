package org.example.realtimestats.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.config.RealtimeStatsProperties;
import org.example.realtimestats.entity.PageView;
import org.example.realtimestats.mapper.PageViewMapper;
import org.example.realtimestats.service.ContentService;
import org.example.realtimestats.service.PageViewService;
import org.example.realtimestats.vo.DailyStatsVO;
import org.example.realtimestats.vo.HourlyStatsVO;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 页面访问服务实现类
 */
@Slf4j
@Service
public class PageViewServiceImpl extends ServiceImpl<PageViewMapper, PageView> implements PageViewService {

    @Autowired
    private PageViewMapper pageViewMapper;

    @Autowired
    private ContentService contentService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RealtimeStatsProperties realtimeStatsProperties;

    private String getPageViewQueueKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "queue";
    }

    private String getPageViewDailyKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "daily:" + contentId;
    }

    private String getPageViewHourlyKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "hourly:" + contentId;
    }

    private String getOnlineUsersKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "online:" + contentId;
    }

    private String getUvSetKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "uv:" + contentId;
    }


    @Override
    public boolean recordPageView(Long contentId, Long userId, String ipAddress, String userAgent, String referer) {
        try {
            // 创建页面访问记录
            PageView pageView = new PageView();
            pageView.setContentId(contentId);
            pageView.setUserId(userId);
            pageView.setIpAddress(ipAddress);
            pageView.setUserAgent(userAgent);
            pageView.setReferer(referer);
            pageView.setVisitTime(LocalDateTime.now());

            // 将记录添加到Redis队列，稍后批量保存到数据库
            RQueue<PageView> queue = redissonClient.getQueue(getPageViewQueueKey());
            queue.add(pageView);

            // 增加内容浏览量
            contentService.incrementViewCount(contentId);

            // 更新当日访问量
            String dailyKey = getPageViewDailyKey(contentId) + LocalDate.now();
            redissonClient.getAtomicLong(dailyKey).incrementAndGet();
            // 设置过期时间为2天
            redissonClient.getKeys().expire(dailyKey, 2, TimeUnit.DAYS);

            // 更新小时访问量
            String hourlyKey = getPageViewHourlyKey(contentId) +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            redissonClient.getAtomicLong(hourlyKey).incrementAndGet();
            // 设置过期时间为1天
            redissonClient.getKeys().expire(hourlyKey, 1, TimeUnit.DAYS);

            // 更新在线用户
            String onlineKey = getOnlineUsersKey(contentId);
            String visitorId = userId != null ? userId.toString() : ipAddress;
            RSet<String> onlineUsers = redissonClient.getSet(onlineKey);
            onlineUsers.add(visitorId);
            // 设置过期时间为30分钟
            redissonClient.getKeys().expire(onlineKey, 30, TimeUnit.MINUTES);

            // 更新UV统计
            String uvKey = getUvSetKey(contentId) + LocalDate.now();
            RSet<String> uvSet = redissonClient.getSet(uvKey);
            uvSet.add(visitorId);
            // 设置过期时间为2天
            redissonClient.getKeys().expire(uvKey, 2, TimeUnit.DAYS);

            return true;
        } catch (Exception e) {
            log.error("记录页面访问失败: contentId={}, userId={}, ipAddress={}", contentId, userId, ipAddress, e);
            return false;
        }
    }

    @Override
    public Long getPageViewCount(Long contentId, LocalDateTime startTime, LocalDateTime endTime) {
        // 如果是查询当天的数据，优先从Redis获取
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(today, LocalTime.MAX);

        if (startTime.isEqual(todayStart) && endTime.isEqual(todayEnd)) {
            String dailyKey = getPageViewDailyKey(contentId) + today;
            RAtomicLong atomicLong = redissonClient.getAtomicLong(dailyKey);
            long count = atomicLong.get();
            if (count > 0) {
                return count;
            }
        }

        // 从数据库查询
        return pageViewMapper.countByTimeRange(contentId, startTime, endTime);
    }

    @Override
    public Long getUniqueVisitorCount(Long contentId, LocalDateTime startTime, LocalDateTime endTime) {
        // 如果是查询当天的数据，优先从Redis获取
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(today, LocalTime.MAX);

        if (startTime.isEqual(todayStart) && endTime.isEqual(todayEnd)) {
            String uvKey = getUvSetKey(contentId) + today;
            RSet<String> uvSet = redissonClient.getSet(uvKey);
            int count = uvSet.size();
            if (count > 0) {
                return (long) count;
            }
        }

        // 从数据库查询
        return pageViewMapper.countUniqueVisitors(contentId, startTime, endTime);
    }

    @Override
    public List<PageView> getPageViews(Long contentId, LocalDateTime startTime, LocalDateTime endTime) {
        return pageViewMapper.findByTimeRange(contentId, startTime, endTime);
    }

    @Override
    public Map<String, Object> getRealTimeStats(Long contentId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取总访问量（从内容服务获取）
            stats.put("totalViews", contentService.getContentStats(contentId).getViewCount());

            // 获取当日访问量
            LocalDate today = LocalDate.now();
            String dailyKey = getPageViewDailyKey(contentId) + today;
            long dailyViews = redissonClient.getAtomicLong(dailyKey).get();
            stats.put("dailyViews", dailyViews);

            // 获取当前在线人数
            String onlineKey = getOnlineUsersKey(contentId);
            int onlineUsers = redissonClient.getSet(onlineKey).size();
            stats.put("onlineUsers", onlineUsers);

            // 获取当日独立访客数
            String uvKey = getUvSetKey(contentId) + today;
            int uniqueVisitors = redissonClient.getSet(uvKey).size();
            stats.put("uniqueVisitors", uniqueVisitors);

            // 获取最近一小时的访问量
            LocalDateTime now = LocalDateTime.now();
            String hourlyKey = getPageViewHourlyKey(contentId) +
                    now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            long hourlyViews = redissonClient.getAtomicLong(hourlyKey).get();
            stats.put("hourlyViews", hourlyViews);

        } catch (Exception e) {
            log.error("获取实时统计数据失败: contentId={}", contentId, e);
        }

        return stats;
    }

    @Override
    public Map<String, Long> getViewTrend(Long contentId, LocalDateTime startTime, LocalDateTime endTime, int interval) {
        Map<String, Long> trend = new TreeMap<>();

        try {
            // 计算时间点数量
            long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
            int points = (int) (minutes / interval) + 1;

            // 如果是查询当天的数据，且间隔为60分钟（小时级别），优先从Redis获取
            LocalDate today = LocalDate.now();
            LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
            boolean isToday = startTime.isEqual(todayStart) || startTime.isAfter(todayStart);

            if (isToday && interval == 60) {
                // 按小时获取数据
                for (int i = 0; i < points; i++) {
                    LocalDateTime pointTime = startTime.plusMinutes((long) i * interval);
                    if (pointTime.isAfter(endTime)) {
                        break;
                    }

                    String hourlyKey = getPageViewHourlyKey(contentId) +
                            pointTime.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
                    long views = redissonClient.getAtomicLong(hourlyKey).get();

                    String timeKey = pointTime.format(DateTimeFormatter.ofPattern("HH:mm"));
                    trend.put(timeKey, views);
                }

                // 如果Redis中有数据，直接返回
                if (!trend.isEmpty()) {
                    return trend;
                }
            }

            // 从数据库获取原始访问记录
            List<PageView> pageViews = getPageViews(contentId, startTime, endTime);

            // 初始化所有时间点的访问量为0
            for (int i = 0; i < points; i++) {
                LocalDateTime pointTime = startTime.plusMinutes((long) i * interval);
                if (pointTime.isAfter(endTime)) {
                    break;
                }

                String timeKey = pointTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                trend.put(timeKey, 0L);
            }

            // 统计每个时间点的访问量
            for (PageView pageView : pageViews) {
                LocalDateTime visitTime = pageView.getVisitTime();
                long minutesDiff = ChronoUnit.MINUTES.between(startTime, visitTime);
                int pointIndex = (int) (minutesDiff / interval);

                if (pointIndex >= 0 && pointIndex < points) {
                    LocalDateTime pointTime = startTime.plusMinutes((long) pointIndex * interval);
                    String timeKey = pointTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));

                    trend.put(timeKey, trend.getOrDefault(timeKey, 0L) + 1);
                }
            }

        } catch (Exception e) {
            log.error("获取访问趋势数据失败: contentId={}, startTime={}, endTime={}, interval={}",
                    contentId, startTime, endTime, interval, e);
        }

        return trend;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncPageViewsToDb() {
        // 获取分布式锁，防止并发同步
        RLock lock = redissonClient.getLock("lock:page_view:sync");
        try {
            // 尝试获取锁，最多等待5秒，锁过期时间30秒
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    // 从Redis队列获取所有页面访问记录
                    RQueue<PageView> queue = redissonClient.getQueue(getPageViewQueueKey());
                    List<PageView> pageViews = new ArrayList<>();

                    // 一次最多处理1000条记录
                    int batchSize = 1000;
                    int processedCount = 0;

                    PageView pageView;
                    while (processedCount < batchSize && (pageView = queue.poll()) != null) {
                        pageViews.add(pageView);
                        processedCount++;
                    }

                    if (!pageViews.isEmpty()) {
                        // 批量插入数据库
                        saveBatch(pageViews);
                    }

                    return processedCount;
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                log.warn("获取页面访问同步锁超时");
                return 0;
            }
        } catch (Exception e) {
            log.error("同步页面访问数据到数据库失败", e);
            return 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncAllPageViewDataToDb() {
        log.info("开始同步所有页面访问数据到数据库");
        try {
            // 同步队列中的页面访问记录
            int totalSynced = 0;
            int batchSynced;
            do {
                batchSynced = syncPageViewsToDb();
                totalSynced += batchSynced;
            } while (batchSynced > 0);
            log.info("同步页面访问记录完成，共同步{}条记录", totalSynced);

            // 同步每日统计数据
            String dailyPattern = realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "daily:" + "*";
            Iterable<String> dailyKeys = redissonClient.getKeys().getKeysByPattern(dailyPattern);
            List<String> dailyKeyList = new ArrayList<>();
            dailyKeys.forEach(dailyKeyList::add);
            int batchSize = realtimeStatsProperties.getSync().getBatchSize();
            
            for (int i = 0; i < dailyKeyList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, dailyKeyList.size());
                List<String> batchKeys = dailyKeyList.subList(i, end);
                
                for (String key : batchKeys) {
                    try {
                        // 从key中提取contentId和日期
                        String[] parts = key.substring((realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "daily:").length()).split(":");
                        if (parts.length == 2) {
                            Long contentId = Long.parseLong(parts[0]);
                            LocalDate date = LocalDate.parse(parts[1]);
                            
                            // 获取当日访问量
                            long views = redissonClient.getAtomicLong(key).get();
                            
                            // 更新数据库中的每日统计
                            pageViewMapper.updateDailyStats(contentId, date, views);
                        }
                    } catch (Exception e) {
                        log.error("同步每日统计数据失败，key=" + key, e);
                    }
                }
            }

            // 同步每小时统计数据
            String hourlyPattern = realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "hourly:"+ "*";
            Iterable<String> hourlyKeys = redissonClient.getKeys().getKeysByPattern(hourlyPattern);
            List<String> hourlyKeyList = new ArrayList<>();
            hourlyKeys.forEach(hourlyKeyList::add);
            
            for (int i = 0; i < hourlyKeyList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, hourlyKeyList.size());
                List<String> batchKeys = hourlyKeyList.subList(i, end);
                
                for (String key : batchKeys) {
                    try {
                        // 从key中提取contentId和时间
                        String[] parts = key.substring((realtimeStatsProperties.getRedisKeyPrefix().getPageView() + "hourly:").length()).split(":");
                        if (parts.length == 2) {
                            Long contentId = Long.parseLong(parts[0]);
                            LocalDateTime dateTime = LocalDateTime.parse(parts[1], 
                                DateTimeFormatter.ofPattern("yyyyMMddHH"));
                            
                            // 获取小时访问量
                            long views = redissonClient.getAtomicLong(key).get();
                            
                            // 更新数据库中的每小时统计
                            pageViewMapper.updateHourlyStats(contentId, dateTime, views);
                        }
                    } catch (Exception e) {
                        log.error("同步每小时统计数据失败，key=" + key, e);
                    }
                }
            }

            log.info("同步所有页面访问数据到数据库完成");
        } catch (Exception e) {
            log.error("同步所有页面访问数据到数据库失败", e);
            throw new RuntimeException("同步所有页面访问数据失败", e);
        }
    }
}