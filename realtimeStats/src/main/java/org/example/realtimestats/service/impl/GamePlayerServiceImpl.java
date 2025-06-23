package org.example.realtimestats.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.realtimestats.config.RealtimeStatsProperties;
import org.example.realtimestats.entity.GamePlayer;
import org.example.realtimestats.exception.BusinessException;
import org.example.realtimestats.mapper.GamePlayerMapper;
import org.example.realtimestats.service.GamePlayerService;
import org.redisson.api.*;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 游戏玩家服务实现类
 */
@Slf4j
@Service
public class GamePlayerServiceImpl extends ServiceImpl<GamePlayerMapper, GamePlayer> implements GamePlayerService {

    @Autowired
    private GamePlayerMapper gamePlayerMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RealtimeStatsProperties realtimeStatsProperties;

    private String getPlayerScoreKey(Long playerId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getGamePlayer() + "score:" + playerId;
    }

    private String getPlayerLevelKey(Long playerId) {
        return realtimeStatsProperties.getRedisKeyPrefix().getGamePlayer() + "level:" + playerId;
    }

    private String getPlayerRankKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getGamePlayer() + "rank:";
    }

    private String getPlayerScoreUpdateKey() {
        return realtimeStatsProperties.getRedisKeyPrefix().getGamePlayer() + "score:update:";
    }


    @Override
    public Long incrementScore(Long playerId, Long score) {
        try {
            // 使用Redis增加积分
            String scoreKey = getPlayerScoreKey(playerId);
            Long scoreRes = redissonClient.getAtomicLong(scoreKey).addAndGet(score);

            // 更新排行榜
            RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getPlayerRankKey());
            rankSet.addScore(playerId, score);

            // 记录需要更新到数据库的玩家ID
            RSet<Long> updateSet = redissonClient.getSet(getPlayerScoreUpdateKey());
            updateSet.add(playerId);

            return scoreRes;
        } catch (Exception e) {
            log.error("增加玩家积分失败: playerId={}, score={}", playerId, score, e);
            throw new BusinessException("增加玩家积分失败");
        }
    }

    @Override
    public boolean updateLevel(Long playerId, Integer level) {
        try {
            // 使用Redis更新等级
            String levelKey = getPlayerLevelKey(playerId);
            redissonClient.getBucket(levelKey).set(level);

            // 同时更新数据库
            return gamePlayerMapper.updateLevel(playerId, level) > 0;
        } catch (Exception e) {
            log.error("更新玩家等级失败: playerId={}, level={}", playerId, level, e);
            return false;
        }
    }

    @Override
    public List<GamePlayer> getTopPlayers(Integer limit) {
        try {
            // 从Redis获取排行榜
            RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getPlayerRankKey());
            Collection<ScoredEntry<Long>> scoredEntries = rankSet.entryRangeReversed(0, limit - 1);

            if (scoredEntries.isEmpty()) {
                // Redis中没有数据，从数据库获取并初始化Redis
                List<GamePlayer> topPlayers = gamePlayerMapper.getTopPlayers(limit);
                initRedisRankSet(topPlayers);
                return topPlayers;
            }

            // 获取玩家ID列表
            List<Long> playerIds = scoredEntries.stream()
                    .map(ScoredEntry::getValue)
                    .collect(Collectors.toList());

            // 批量获取玩家信息
            List<GamePlayer> players = listByIds(playerIds);

            // 按照排行榜顺序排序
            Map<Long, GamePlayer> playerMap = players.stream()
                    .collect(Collectors.toMap(GamePlayer::getId, player -> player));

            List<GamePlayer> result = new ArrayList<>();
            for (ScoredEntry<Long> entry : scoredEntries) {
                Long playerId = entry.getValue();
                GamePlayer player = playerMap.get(playerId);
                if (player != null) {
                    // 更新积分为Redis中的最新值
                    player.setScore(Math.round(entry.getScore()));
                    result.add(player);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("获取积分排行榜失败: limit={}", limit, e);
            // 发生异常时从数据库获取
            return gamePlayerMapper.getTopPlayers(limit);
        }
    }

    @Override
    public Long getScore(Long playerId) {
        try {
            // 从Redis获取积分
            String scoreKey = getPlayerScoreKey(playerId);
            long score = redissonClient.getAtomicLong(scoreKey).get();

            // 如果Redis中没有积分数据，从数据库获取
            if (score == 0) {
                GamePlayer player = getById(playerId);
                if (player != null) {
                    score = player.getScore();
                    // 初始化Redis数据
                    redissonClient.getAtomicLong(scoreKey).set(score);
                    RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getPlayerRankKey());
                    rankSet.add(score, playerId);
                }
            }

            return score;
        } catch (Exception e) {
            log.error("获取玩家积分失败: playerId={}", playerId, e);
            // 发生异常时从数据库获取
            GamePlayer player = getById(playerId);
            return player != null ? player.getScore() : 0L;
        }
    }

    @Override
    public Integer getPlayerRank(Long playerId) {
        try {
            // 从Redis获取排名
            RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getPlayerRankKey());
            int rank = rankSet.revRank(playerId).intValue();

            // Redis排名从0开始，需要加1
            return rank + 1;
        } catch (Exception e) {
            log.error("获取玩家排名失败: playerId={}", playerId, e);
            // 发生异常时从数据库获取
            return gamePlayerMapper.getPlayerRank(playerId);
        }
    }

    @Override
    public Map<String, Object> getPlayerScoreAndRank(Long playerId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 从Redis获取积分
            String scoreKey = getPlayerScoreKey(playerId);
            long score = redissonClient.getAtomicLong(scoreKey).get();

            // 如果Redis中没有积分数据，从数据库获取
            if (score == 0) {
                GamePlayer player = getById(playerId);
                if (player != null) {
                    score = player.getScore();
                    // 初始化Redis数据
                    redissonClient.getAtomicLong(scoreKey).set(score);
                    RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getPlayerRankKey());
                    rankSet.add(score, playerId);
                }
            }

            // 获取排名
            Integer rank = getPlayerRank(playerId);

            result.put("playerId", playerId);
            result.put("score", score);
            result.put("rank", rank);

            // 获取等级
            String levelKey = getPlayerLevelKey(playerId);
            RBucket<Integer> levelBucket = redissonClient.getBucket(levelKey);
            Integer level = levelBucket.get();
            if (level != null) {
                result.put("level", level);
            } else {
                // 从数据库获取等级
                GamePlayer player = getById(playerId);
                if (player != null) {
                    result.put("level", player.getLevel());
                }
            }
        } catch (Exception e) {
            log.error("获取玩家积分和排名失败: playerId={}", playerId, e);
            // 发生异常时从数据库获取
            GamePlayer player = getById(playerId);
            if (player != null) {
                result.put("playerId", playerId);
                result.put("score", player.getScore());
                result.put("level", player.getLevel());
                result.put("rank", gamePlayerMapper.getPlayerRank(playerId));
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateScore(Map<Long, Long> playerScores) {
        if (playerScores == null || playerScores.isEmpty()) {
            return 0;
        }

        try {
            List<GamePlayer> players = new ArrayList<>();

            for (Map.Entry<Long, Long> entry : playerScores.entrySet()) {
                Long playerId = entry.getKey();
                Long score = entry.getValue();

                GamePlayer player = new GamePlayer();
                player.setId(playerId);
                player.setScore(score);
                player.setUpdateTime(LocalDateTime.now());

                players.add(player);

                // 更新Redis中的积分
                String scoreKey = getPlayerScoreKey(playerId);
                redissonClient.getAtomicLong(scoreKey).set(score);

                // 更新排行榜
                RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getPlayerRankKey());
                rankSet.add(score, playerId);
            }

            // 批量更新数据库
            updateBatchById(players);

            return players.size();
        } catch (Exception e) {
            log.error("批量更新玩家积分失败", e);
            return 0;
        }
    }

    @Override
    public List<GamePlayer> getPlayersByLevelRange(Integer minLevel, Integer maxLevel) {
        return gamePlayerMapper.getPlayersByLevelRange(minLevel, maxLevel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncScoresToDb() {
        // 获取分布式锁，防止并发同步
        RLock lock = redissonClient.getLock("lock:player:sync");
        try {
            // 尝试获取锁，最多等待5秒，锁过期时间30秒
            if (lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                try {
                    // 获取需要更新的玩家ID集合
                    RSet<Long> updateSet = redissonClient.getSet(getPlayerScoreUpdateKey());
                    Set<Long> playerIds = new HashSet<>(updateSet.readAll());

                    if (playerIds.isEmpty()) {
                        return 0;
                    }

                    List<GamePlayer> players = new ArrayList<>();

                    int batchSize = realtimeStatsProperties.getSync().getBatchSize();
                    List<Long> playerIdList = new ArrayList<>(playerIds);
                    int totalSynced = 0;

                    // 分批处理
                    for (int i = 0; i < playerIdList.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, playerIdList.size());
                        List<Long> batchPlayerIds = playerIdList.subList(i, end);
                        List<GamePlayer> batchPlayers = new ArrayList<>();

                        for (Long playerId : batchPlayerIds) {
                            // 获取Redis中的积分
                            String scoreKey = getPlayerScoreKey(playerId);
                            long score = redissonClient.getAtomicLong(scoreKey).get();

                            GamePlayer player = new GamePlayer();
                            player.setId(playerId);
                            player.setScore(score);
                            player.setUpdateTime(LocalDateTime.now());

                            batchPlayers.add(player);
                        }

                        // 批量更新当前批次
                        updateBatchById(batchPlayers);
                        totalSynced += batchPlayers.size();

                        // 从更新集合中移除已处理的ID
                        updateSet.removeAll(batchPlayerIds);
                    }

                    // 如果还有剩余未处理的ID（可能由于并发修改）
                    if (!updateSet.isEmpty()) {
                        log.info("还有{}个玩家积分需要同步", updateSet.size());
                    }

                    return players.size();
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                log.warn("获取玩家积分同步锁超时");
                return 0;
            }
        } catch (Exception e) {
            log.error("同步玩家积分到数据库失败", e);
            return 0;
        }
    }

    /**
     * 初始化Redis排行榜
     *
     * @param players 玩家列表
     */
    private void initRedisRankSet(List<GamePlayer> players) {
        if (players == null || players.isEmpty()) {
            return;
        }

        try {
            RScoredSortedSet<Long> rankSet = redissonClient.getScoredSortedSet(getPlayerRankKey());

            for (GamePlayer player : players) {
                Long playerId = player.getId();
                Long score = player.getScore();

                // 更新排行榜
                rankSet.add(score, playerId);

                // 更新积分缓存
                String scoreKey = getPlayerScoreKey(playerId);
                redissonClient.getAtomicLong(scoreKey).set(score);

                // 更新等级缓存
                String levelKey = getPlayerLevelKey(playerId);
                redissonClient.getBucket(levelKey).set(player.getLevel());
            }
        } catch (Exception e) {
            log.error("初始化Redis排行榜失败", e);
        }
    }
}