package org.example.realtimestats.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.config.RealtimeStatsProperties;
import org.example.realtimestats.entity.HotSearch;
import org.example.realtimestats.mapper.HotSearchMapper;
import org.example.realtimestats.service.HotSearchService;
import org.redisson.api.*;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 热搜词服务实现类
 */
@Slf4j
@Service
public class HotSearchServiceImpl extends ServiceImpl<HotSearchMapper, HotSearch> implements HotSearchService {

    @Autowired
    private HotSearchMapper hotSearchMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RealtimeStatsProperties realtimeStatsProperties;

    private String getHotSearchKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getHotSearch() + "value:";
    }

    private String getHotSearchRankKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getHotSearch() + "rank:";
    }

    private String getHotSearchHourlyKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getHotSearch() + "hourly:";
    }

    private String getHotSearchUpdateKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getHotSearch() + "update:";
    }

    @Override
    public boolean incrementHotValue(String keyword, Long hotValue) {
        try {
            // 获取分布式锁，防止并发操作
            RLock lock = redissonClient.getLock("lock:hot_search:" + keyword);
            try {
                // 尝试获取锁，最多等待2秒，锁过期时间5秒
                if (lock.tryLock(2, 5, TimeUnit.SECONDS)) {
                    try {
                        // 查询热搜词是否存在
                        HotSearch hotSearch = hotSearchMapper.findByKeyword(keyword);
                        Long hotSearchId;

                        if (hotSearch == null) {
                            // 创建新的热搜词
                            hotSearch = new HotSearch();
                            hotSearch.setKeyword(keyword);
                            hotSearch.setHotValue(hotValue);
                            hotSearch.setRank(0);
                            hotSearch.setCreateTime(LocalDateTime.now());
                            hotSearch.setUpdateTime(LocalDateTime.now());
                            save(hotSearch);

                            hotSearchId = hotSearch.getId();
                        } else {
                            hotSearchId = hotSearch.getId();

                            // 使用Redis增加热度值
                            String key = getHotSearchKey() + hotSearchId;
                            redissonClient.getAtomicLong(key).addAndGet(hotValue);
                        }

                        // 更新排行榜
                        RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getHotSearchRankKey());
                        rankSet.addScore(hotSearchId, hotValue);

                        // 记录需要更新到数据库的热搜词ID
                        RSet<Long> updateSet = redissonClient.getSet(getHotSearchUpdateKey());
                        updateSet.add(hotSearchId);

                        // 更新小时热度趋势
                        String hourlyKey = getHotSearchHourlyKey() + keyword + ":" +
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
                        redissonClient.getAtomicLong(hourlyKey).addAndGet(hotValue);
                        // 设置过期时间为3天
                        redissonClient.getKeys().expire(hourlyKey, 3, TimeUnit.DAYS);

                        return true;
                    } finally {
                        // 释放锁
                        lock.unlock();
                    }
                } else {
                    log.warn("获取热搜词锁超时: keyword={}", keyword);
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("获取热搜词锁被中断: keyword={}", keyword, e);
                return false;
            }
        } catch (Exception e) {
            log.error("增加热搜词热度失败: keyword={}, hotValue={}", keyword, hotValue, e);
            return false;
        }
    }

    @Override
    public List<HotSearch> getTopHotSearches(Integer limit) {
        try {
            // 从Redis获取排行榜
            RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getHotSearchRankKey());
            Collection<ScoredEntry<Long>> scoredEntries = rankSet.entryRangeReversed(0, limit - 1);

            if (scoredEntries.isEmpty()) {
                // Redis中没有数据，从数据库获取并初始化Redis
                List<HotSearch> topHotSearches = hotSearchMapper.getTopHotSearches(limit);
                initRedisRankSet(topHotSearches);
                return topHotSearches;
            }

            // 获取热搜词ID列表
            List<Long> hotSearchIds = scoredEntries.stream()
                    .map(ScoredEntry::getValue)
                    .collect(Collectors.toList());

            // 批量获取热搜词信息
            List<HotSearch> hotSearches = listByIds(hotSearchIds);

            // 按照排行榜顺序排序
            Map<Long, HotSearch> hotSearchMap = hotSearches.stream()
                    .collect(Collectors.toMap(HotSearch::getId, hotSearch -> hotSearch));

            List<HotSearch> result = new ArrayList<>();
            int rank = 1;
            for (ScoredEntry<Long> entry : scoredEntries) {
                Long hotSearchId = entry.getValue();
                HotSearch hotSearch = hotSearchMap.get(hotSearchId);
                if (hotSearch != null) {
                    // 更新热度值为Redis中的最新值
                    hotSearch.setHotValue(Math.round(entry.getScore()));
                    // 更新排名
                    hotSearch.setRank(rank++);
                    result.add(hotSearch);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("获取热搜排行榜失败: limit={}", limit, e);
            // 发生异常时从数据库获取
            return hotSearchMapper.getTopHotSearches(limit);
        }
    }

    @Override
    public Long getHotValue(String keyword) {
        try {
            // 查询热搜词
            HotSearch hotSearch = hotSearchMapper.findByKeyword(keyword);
            if (hotSearch == null) {
                return 0L;
            }

            // 从Redis获取热度值
            String key = getHotSearchKey() + hotSearch.getId();
            long hotValue = redissonClient.getAtomicLong(key).get();

            // 如果Redis中没有数据，返回数据库中的值
            if (hotValue == 0) {
                return hotSearch.getHotValue();
            }

            return hotValue;
        } catch (Exception e) {
            log.error("获取热搜词热度失败: keyword={}", keyword, e);
            // 发生异常时从数据库获取
            HotSearch hotSearch = hotSearchMapper.findByKeyword(keyword);
            return hotSearch != null ? hotSearch.getHotValue() : 0L;
        }
    }

    @Override
    public boolean updateRank(Long id, Integer rank) {
        try {
            return hotSearchMapper.updateRank(id, rank) > 0;
        } catch (Exception e) {
            log.error("更新热搜词排名失败: id={}, rank={}", id, rank, e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateHotValue(Map<String, Long> hotSearchMap) {
        if (hotSearchMap == null || hotSearchMap.isEmpty()) {
            return 0;
        }

        try {
            int updateCount = 0;

            for (Map.Entry<String, Long> entry : hotSearchMap.entrySet()) {
                String keyword = entry.getKey();
                Long hotValue = entry.getValue();

                if (incrementHotValue(keyword, hotValue)) {
                    updateCount++;
                }
            }

            return updateCount;
        } catch (Exception e) {
            log.error("批量更新热搜词热度失败", e);
            return 0;
        }
    }

    @Override
    public Map<String, Long> getHotSearchTrend(String keyword, int hours) {
        Map<String, Long> trend = new TreeMap<>();

        try {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("MM-dd HH:00");

            // 初始化所有小时的热度为0
            for (int i = hours - 1; i >= 0; i--) {
                LocalDateTime pointTime = now.minusHours(i);
                String timeKey = pointTime.format(hourFormatter);
                trend.put(timeKey, 0L);
            }

            // 从Redis获取小时热度数据
            for (int i = hours - 1; i >= 0; i--) {
                LocalDateTime pointTime = now.minusHours(i);
                String hourlyKey = getHotSearchHourlyKey() + keyword + ":" +
                        pointTime.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

                long hotValue = redissonClient.getAtomicLong(hourlyKey).get();
                String timeKey = pointTime.format(hourFormatter);

                trend.put(timeKey, hotValue);
            }
        } catch (Exception e) {
            log.error("获取热搜趋势数据失败: keyword={}, hours={}", keyword, hours, e);
        }

        return trend;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncHotSearchesToDb() {
        // 获取分布式锁，防止并发同步
        RLock lock = redissonClient.getLock("lock:hot_search:sync");
        try {
            // 尝试获取锁，最多等待5秒，锁过期时间30秒
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    // 获取需要更新的热搜词ID集合
                    RSet<Long> updateSet = redissonClient.getSet(getHotSearchUpdateKey());
                    Set<Long> hotSearchIds = new HashSet<>(updateSet.readAll());

                    if (hotSearchIds.isEmpty()) {
                        return 0;
                    }

                    List<HotSearch> hotSearches = new ArrayList<>();

                    // 获取排行榜
                    RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getHotSearchRankKey());

                    int batchSize = realtimeStatsProperties.getSync().getBatchSize();
                    List<Long> hotSearchIdList = new ArrayList<>(hotSearchIds);
                    int totalSynced = 0;

                    // 分批处理
                    for (int i = 0; i < hotSearchIdList.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, hotSearchIdList.size());
                        List<Long> batchIds = hotSearchIdList.subList(i, end);
                        List<HotSearch> batchHotSearches = new ArrayList<>();

                        for (Long hotSearchId : batchIds) {
                            // 获取Redis中的热度值
                            String key = getHotSearchKey() + hotSearchId;
                            long hotValue = redissonClient.getAtomicLong(key).get();
                            
                            // 获取排名
                            int rank = rankSet.revRank(hotSearchId).intValue() + 1;
                            
                            HotSearch hotSearch = new HotSearch();
                            hotSearch.setId(hotSearchId);
                            hotSearch.setHotValue(hotValue);
                            hotSearch.setRank(rank);
                            hotSearch.setUpdateTime(LocalDateTime.now());
                            
                            batchHotSearches.add(hotSearch);
                        }
                        
                        // 批量更新当前批次
                        updateBatchById(batchHotSearches);
                        totalSynced += batchHotSearches.size();
                        
                        // 从更新集合中移除已处理的ID
                        updateSet.removeAll(batchIds);
                    }

                    // 如果还有剩余未处理的ID（可能由于并发修改）
                    if (!updateSet.isEmpty()) {
                        log.info("还有{}个热搜词需要同步", updateSet.size());
                    }
                    
                    return hotSearches.size();
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                log.warn("获取热搜词同步锁超时");
                return 0;
            }
        } catch (Exception e) {
            log.error("同步热搜词到数据库失败", e);
            return 0;
        }
    }

    @Override
    public boolean recordSearch(String keyword) {
        try {
            // 记录搜索并增加热度值（默认增加1）
            return incrementHotValue(keyword, 1L);
        } catch (Exception e) {
            log.error("记录搜索关键词失败: keyword={}", keyword, e);
            return false;
        }
    }

    @Override
    public List<HotSearch> getHotSearchList(Integer limit) {
        // 调用已有的获取热搜排行榜方法
        return getTopHotSearches(limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanExpiredHotSearches(int days) {
        try {
            LocalDateTime expireTime = LocalDateTime.now().minusDays(days);
            
            // 查询过期的热搜词
            List<HotSearch> expiredHotSearches = lambdaQuery()
                    .lt(HotSearch::getUpdateTime, expireTime)
                    .list();
            
            if (expiredHotSearches.isEmpty()) {
                return 0;
            }
            
            // 获取过期热搜词ID列表
            List<Long> expiredIds = expiredHotSearches.stream()
                    .map(HotSearch::getId)
                    .collect(Collectors.toList());
            
            // 从Redis中删除过期数据
            for (Long id : expiredIds) {
                String key = getHotSearchKey() + id;
                redissonClient.getKeys().delete(key);
            }
            
            // 从排行榜中删除过期数据
            RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getHotSearchRankKey());
            rankSet.removeAll(new HashSet<>(expiredIds));
            
            // 从数据库中删除过期数据
            removeByIds(expiredIds);
            
            return expiredIds.size();
        } catch (Exception e) {
            log.error("清理过期热搜词失败: days={}", days, e);
            return 0;
        }
    }

    /**
     * 初始化Redis排行榜
     *
     * @param hotSearches 热搜词列表
     */
    private void initRedisRankSet(List<HotSearch> hotSearches) {
        if (hotSearches == null || hotSearches.isEmpty()) {
            return;
        }
        
        try {
            RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getHotSearchRankKey());
            
            for (HotSearch hotSearch : hotSearches) {
                Long id = hotSearch
                        .getId();
                Long hotValue = hotSearch.getHotValue();
                
                // 更新排行榜
                rankSet.add(hotValue, id);
                
                // 更新热度值缓存
                String key = getHotSearchKey() + id;
                redissonClient.getAtomicLong(key).set(hotValue);
            }
        } catch (Exception e) {
            log.error("初始化Redis排行榜失败", e);
        }
    }
}