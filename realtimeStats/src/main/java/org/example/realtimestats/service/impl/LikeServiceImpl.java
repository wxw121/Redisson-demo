package org.example.realtimestats.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.config.RealtimeStatsProperties;
import org.example.realtimestats.entity.LikeRecord;
import org.example.realtimestats.exception.BusinessException;
import org.example.realtimestats.mapper.ContentMapper;
import org.example.realtimestats.mapper.LikeRecordMapper;
import org.example.realtimestats.service.LikeService;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 点赞服务实现类
 */
@Slf4j
@Service
public class LikeServiceImpl implements LikeService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private LikeRecordMapper likeRecordMapper;

    @Autowired
    private ContentMapper contentMapper;

    @Autowired
    private RealtimeStatsProperties realtimeStatsProperties;

    private String getLikeRecordKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getLike() + "record:" + contentId;
    }

    private String getLikeCountKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getLike() + "count:" + contentId;
    }

    private String getLikeLockKey(Long contentId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getLike() + "lock:" + contentId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likeContent(Long userId, Long contentId) {
        String lockKey = getLikeLockKey(contentId) + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 获取分布式锁，防止重复点赞
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.warn("获取点赞锁失败，userId={}，contentId={}", userId, contentId);
                return false;
            }

            // 获取用户点赞记录
            RMap<String, Integer> likeRecordMap = redissonClient.getMap(getLikeRecordKey(contentId));
            String userKey = userId.toString();
            Integer likeStatus = likeRecordMap.get(userKey);

            // 如果Redis中没有记录，查询数据库
            if (likeStatus == null) {
                LikeRecord record = likeRecordMapper.findByUserIdAndContentId(userId, contentId);
                likeStatus = record == null ? 0 : record.getStatus();
                likeRecordMap.put(userKey, likeStatus);
            }

            // 切换点赞状态
            boolean newStatus = likeStatus != 1;
            int newStatusValue = newStatus ? 1 : 0;
            likeRecordMap.put(userKey, newStatusValue);

            // 更新点赞计数
            long increment = newStatus ? 1 : -1;
            redissonClient.getAtomicLong(getLikeCountKey(contentId)).addAndGet(increment);

            // 保存到数据库
            LikeRecord record = new LikeRecord();
            record.setUserId(userId);
            record.setContentId(contentId);
            record.setStatus(newStatusValue);
            likeRecordMapper.insert(record);

            // 更新内容表的点赞数
            contentMapper.incrementLikeCount(contentId, increment);

            return newStatus;
        } catch (Exception e) {
            log.error("处理点赞操作失败，userId=" + userId + "，contentId=" + contentId, e);
            throw new BusinessException("处理点赞操作失败");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Long getLikeCount(Long contentId) {
        // 优先从Redis获取
        Long count = redissonClient.getAtomicLong(getLikeCountKey(contentId)).get();
        if (count == null || count == 0) {
            // Redis中没有数据，从数据库获取
            count = likeRecordMapper.countByContentId(contentId);
            // 将数据库的计数设置到Redis
            redissonClient.getAtomicLong(getLikeCountKey(contentId)).set(count);
        }
        return count;
    }

    @Override
    public boolean hasUserLiked(Long userId, Long contentId) {
        // 优先从Redis获取
        RMap<String, Integer> likeRecordMap = redissonClient.getMap(getLikeRecordKey(contentId));
        Integer status = likeRecordMap.get(userId.toString());

        if (status == null) {
            // Redis中没有记录，查询数据库
            LikeRecord likeRecord = likeRecordMapper.findByUserIdAndContentId(userId, contentId);
            status = likeRecord == null ? null : likeRecord.getStatus();
            if (status != null) {
                // 将数据库的状态缓存到Redis
                likeRecordMap.put(userId.toString(), status);
            }
        }

        return status != null && status == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncLikeDataToDb(Long contentId) {
        String lockKey = getLikeLockKey(contentId) + ":sync";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 获取同步锁
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.warn("获取同步锁失败，contentId={}", contentId);
                return;
            }

            // 同步点赞记录
            RMap<String, Integer> likeRecordMap = redissonClient.getMap(getLikeRecordKey(contentId));
            likeRecordMap.forEach((userId, status) -> {
                LikeRecord record = new LikeRecord();
                record.setUserId(Long.parseLong(userId));
                record.setContentId(contentId);
                record.setStatus(status);
                likeRecordMapper.insert(record);
            });

            // 同步点赞计数
            Long count = redissonClient.getAtomicLong(getLikeCountKey(contentId)).get();
            contentMapper.incrementLikeCount(contentId, count);

            log.info("同步点赞数据到数据库成功，contentId={}", contentId);
        } catch (Exception e) {
            log.error("同步点赞数据到数据库失败，contentId=" + contentId, e);
            throw new BusinessException("同步点赞数据失败");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncAllLikeDataToDb() {
        log.info("开始同步所有内容的点赞数据到数据库");
        try {
            // 获取所有点赞记录的key
            String likeRecordPattern = getLikeRecordKey(0L).replace("0", "*");
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(likeRecordPattern);
            
            // 分批处理
            int batchSize = realtimeStatsProperties.getSync().getBatchSize();
            List<String> keyList = new ArrayList<>();
            keys.forEach(keyList::add);
            
            for (int i = 0; i < keyList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, keyList.size());
                List<String> batchKeys = keyList.subList(i, end);
                
                for (String key : batchKeys) {
                    try {
                        // 从key中提取contentId
                        String prefix = realtimeStatsProperties.getRedisKeyPrefix().getLike() + "record:";
                        String contentIdStr = key.substring(prefix.length());
                        Long contentId = Long.parseLong(contentIdStr);
                        
                        // 调用单个内容的同步方法
                        syncLikeDataToDb(contentId);
                    } catch (Exception e) {
                        log.error("同步内容点赞数据失败，key=" + key, e);
                        // 继续处理下一个，不中断整体同步
                    }
                }
            }
            log.info("同步所有内容的点赞数据到数据库完成");
        } catch (Exception e) {
            log.error("同步所有点赞数据到数据库失败", e);
            throw new BusinessException("同步所有点赞数据失败");
        }
    }
}