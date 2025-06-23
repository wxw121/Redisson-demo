package org.example.realtimestats.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.config.RealtimeStatsProperties;
import org.example.realtimestats.entity.Content;
import org.example.realtimestats.entity.LikeRecord;
import org.example.realtimestats.mapper.ContentMapper;
import org.example.realtimestats.mapper.LikeRecordMapper;
import org.example.realtimestats.service.ContentService;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 内容服务实现类
 */
@Slf4j
@Service
public class ContentServiceImpl extends ServiceImpl<ContentMapper, Content> implements ContentService {

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private LikeRecordMapper likeRecordMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RealtimeStatsProperties realtimeStatsProperties;

    private String getContentViewCountKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getContent() + "view:" + contentId;
    }

    private String getContentLikeCountKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getContent() + "like:" + contentId;
    }

    private String getContentCommentCountKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getContent() + "comment:" + contentId;
    }

    private String getPlayerScoreUpdateKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getContent() + "share:" + contentId;
    }

    private String getContentStatsMapKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getContent() + "share:";
    }


    @Override
    public boolean incrementViewCount(Long contentId) {
        try {
            // 使用Redis增加浏览量
            String key = getContentViewCountKey(contentId);
            redissonClient.getAtomicLong(key).incrementAndGet();

            // 同时更新统计Map
            RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
            statsMap.addAndGet("viewCount", 1L);

            return true;
        } catch (Exception e) {
            log.error("增加内容浏览量失败: contentId={}", contentId, e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean like(Long contentId, Long userId) {
        // 获取分布式锁，防止并发操作
        RLock lock = redissonClient.getLock("lock:content:like:" + contentId + ":" + userId);
        try {
            // 尝试获取锁，最多等待5秒，锁过期时间10秒
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // 查询是否已点赞
                    LikeRecord record = likeRecordMapper.findByUserIdAndContentId(userId, contentId);

                    if (record == null) {
                        // 创建新的点赞记录
                        record = new LikeRecord();
                        record.setUserId(userId);
                        record.setContentId(contentId);
                        record.setStatus(1);
                        record.setCreateTime(LocalDateTime.now());
                        record.setUpdateTime(LocalDateTime.now());
                        likeRecordMapper.insert(record);

                        // 使用Redis增加点赞数
                        String key = getContentLikeCountKey(contentId);
                        redissonClient.getAtomicLong(key).incrementAndGet();

                        // 同时更新统计Map
                        RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
                        statsMap.addAndGet("likeCount", 1L);

                        return true;
                    } else if (record.getStatus() == 0) {
                        // 已存在但是取消点赞状态，更新为已点赞
                        record.setStatus(1);
                        record.setUpdateTime(LocalDateTime.now());
                        likeRecordMapper.updateById(record);

                        // 使用Redis增加点赞数
                        String key = getContentLikeCountKey(contentId);
                        redissonClient.getAtomicLong(key).incrementAndGet();

                        // 同时更新统计Map
                        RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
                        statsMap.addAndGet("likeCount", 1L);

                        return true;
                    } else {
                        // 已点赞，不做处理
                        return false;
                    }
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                log.warn("获取点赞锁超时: contentId={}, userId={}", contentId, userId);
                return false;
            }
        } catch (Exception e) {
            log.error("点赞操作失败: contentId={}, userId={}", contentId, userId, e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlike(Long contentId, Long userId) {
        // 获取分布式锁，防止并发操作
        RLock lock = redissonClient.getLock("lock:content:unlike:" + contentId + ":" + userId);
        try {
            // 尝试获取锁，最多等待5秒，锁过期时间10秒
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                try {
                    // 查询是否已点赞
                    LikeRecord record = likeRecordMapper.findByUserIdAndContentId(userId, contentId);

                    if (record != null && record.getStatus() == 1) {
                        // 更新为取消点赞状态
                        record.setStatus(0);
                        record.setUpdateTime(LocalDateTime.now());
                        likeRecordMapper.updateById(record);

                        // 使用Redis减少点赞数
                        String key = getContentLikeCountKey(contentId);
                        redissonClient.getAtomicLong(key).decrementAndGet();

                        // 同时更新统计Map
                        RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
                        statsMap.addAndGet("likeCount", -1L);

                        return true;
                    } else {
                        // 未点赞，不做处理
                        return false;
                    }
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                log.warn("获取取消点赞锁超时: contentId={}, userId={}", contentId, userId);
                return false;
            }
        } catch (Exception e) {
            log.error("取消点赞操作失败: contentId={}, userId={}", contentId, userId, e);
            return false;
        }
    }

    @Override
    public boolean incrementCommentCount(Long contentId) {
        try {
            // 使用Redis增加评论数
            String key = getContentCommentCountKey(contentId);
            redissonClient.getAtomicLong(key).incrementAndGet();

            // 同时更新统计Map
            RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
            statsMap.addAndGet("commentCount", 1L);

            return true;
        } catch (Exception e) {
            log.error("增加内容评论数失败: contentId={}", contentId, e);
            return false;
        }
    }

    @Override
    public boolean incrementShareCount(Long contentId) {
        try {
            // 使用Redis增加分享数
            String key = getPlayerScoreUpdateKey(contentId);
            redissonClient.getAtomicLong(key).incrementAndGet();

            // 同时更新统计Map
            RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
            statsMap.addAndGet("shareCount", 1L);

            return true;
        } catch (Exception e) {
            log.error("增加内容分享数失败: contentId={}", contentId, e);
            return false;
        }
    }

    @Override
    public Content getContentStats(Long contentId) {
        // 先从数据库获取内容基本信息
        Content content = contentMapper.selectById(contentId);
        if (content == null) {
            return null;
        }

        try {
            // 从Redis获取最新计数
            RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
            Map<String, Long> allStats = statsMap.readAllMap();

            // 如果Redis中有数据，更新内容对象的计数
            if (!allStats.isEmpty()) {
                Long viewCount = allStats.get("viewCount");
                if (viewCount != null) {
                    content.setViewCount(viewCount);
                }

                Long likeCount = allStats.get("likeCount");
                if (likeCount != null) {
                    content.setLikeCount(likeCount);
                }

                Long commentCount = allStats.get("commentCount");
                if (commentCount != null) {
                    content.setCommentCount(commentCount);
                }

                Long shareCount = allStats.get("shareCount");
                if (shareCount != null) {
                    content.setShareCount(shareCount);
                }
            } else {
                // Redis中没有数据，初始化Redis计数
                initRedisCounters(content);
            }
        } catch (Exception e) {
            log.error("获取内容统计数据失败: contentId={}", contentId, e);
        }

        return content;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean syncCountersToDb(Long contentId) {
        // 获取分布式锁，防止并发同步
        RLock lock = redissonClient.getLock("lock:content:sync:" + contentId);
        try {
            // 尝试获取锁，最多等待5秒，锁过期时间30秒
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    // 从Redis获取最新计数
                    RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);
                    Map<String, Long> allStats = statsMap.readAllMap();

                    if (allStats.isEmpty()) {
                        return false;
                    }

                    // 获取各个计数
                    Long viewCount = allStats.getOrDefault("viewCount", 0L);
                    Long likeCount = allStats.getOrDefault("likeCount", 0L);
                    Long commentCount = allStats.getOrDefault("commentCount", 0L);
                    Long shareCount = allStats.getOrDefault("shareCount", 0L);

                    // 更新数据库
                    int rows = contentMapper.updateCounters(contentId, viewCount, likeCount, commentCount, shareCount);

                    return rows > 0;
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                log.warn("获取同步锁超时: contentId={}", contentId);
                return false;
            }
        } catch (Exception e) {
            log.error("同步内容计数器到数据库失败: contentId={}", contentId, e);
            return false;
        }
    }

    @Override
    public int batchSyncCountersToDb() {
        int syncCount = 0;
        List<String> keys = new ArrayList<>();
        int batchSize = realtimeStatsProperties.getSync().getBatchSize();

        try {
            // 获取所有内容统计Map的键
            Iterable<String> keyPattern = redissonClient.getKeys().getKeysByPattern(getContentStatsMapKey() + "*");
            keyPattern.forEach(keys::add);

            // 分批处理
            for (int i = 0; i < keys.size(); i += batchSize) {
                int end = Math.min(i + batchSize, keys.size());
                List<String> batchKeys = keys.subList(i, end);

                for (String key : batchKeys) {
                    try {
                        // 从键中提取内容ID
                        String contentIdStr = key.substring(getContentStatsMapKey().length());
                        Long contentId = Long.parseLong(contentIdStr);

                        // 同步单个内容的计数器
                        boolean success = syncCountersToDb(contentId);
                        if (success) {
                            syncCount++;
                        }
                    } catch (NumberFormatException e) {
                        log.error("无法解析内容ID: 键={}, 错误={}", key, e.getMessage());
                    } catch (StringIndexOutOfBoundsException e) {
                        log.error("无效的Redis键格式: {}", key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量同步内容计数器到数据库失败", e);
        }

        return syncCount;
    }

    /**
     * 初始化Redis计数器
     *
     * @param content 内容对象
     */
    private void initRedisCounters(Content content) {
        if (content == null) {
            return;
        }

        try {
            Long contentId = content.getId();
            RMap<String, Long> statsMap = redissonClient.getMap(getContentStatsMapKey() + contentId);

            // 设置初始值
            statsMap.put("viewCount", content.getViewCount());
            statsMap.put("likeCount", content.getLikeCount());
            statsMap.put("commentCount", content.getCommentCount());
            statsMap.put("shareCount", content.getShareCount());
        } catch (Exception e) {
            log.error("初始化Redis计数器失败: content={}", content, e);
        }
    }
}