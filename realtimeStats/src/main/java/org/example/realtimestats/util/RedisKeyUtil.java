package org.example.realtimestats.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis键工具类
 */
@Component
public class RedisKeyUtil {

    @Value("${realtime-stats.redis-key-prefix.content:content:}")
    private String contentKeyPrefix;

    @Value("${realtime-stats.redis-key-prefix.like:like:}")
    private String likeKeyPrefix;

    @Value("${realtime-stats.redis-key-prefix.page-view:pv:}")
    private String pageViewKeyPrefix;

    @Value("${realtime-stats.redis-key-prefix.unique-visitor:uv:}")
    private String uniqueVisitorKeyPrefix;

    @Value("${realtime-stats.redis-key-prefix.game-player:game:player:}")
    private String gamePlayerKeyPrefix;

    @Value("${realtime-stats.redis-key-prefix.hot-search:hot:search:}")
    private String hotSearchKeyPrefix;

    /**
     * 获取内容计数器的Redis键
     *
     * @param contentId 内容ID
     * @param type      计数器类型（view, like, comment, share）
     * @return Redis键
     */
    public String getContentCounterKey(Long contentId, String type) {
        return contentKeyPrefix + contentId + ":" + type;
    }

    /**
     * 获取内容所有计数器的Redis键前缀
     *
     * @param contentId 内容ID
     * @return Redis键前缀
     */
    public String getContentCounterKeyPrefix(Long contentId) {
        return contentKeyPrefix + contentId + ":";
    }

    /**
     * 获取用户点赞内容的Redis键
     *
     * @param userId    用户ID
     * @param contentId 内容ID
     * @return Redis键
     */
    public String getUserLikeKey(Long userId, Long contentId) {
        return likeKeyPrefix + "user:" + userId + ":" + contentId;
    }

    /**
     * 获取内容点赞计数的Redis键
     *
     * @param contentId 内容ID
     * @return Redis键
     */
    public String getContentLikeCountKey(Long contentId) {
        return likeKeyPrefix + "count:" + contentId;
    }

    /**
     * 获取内容点赞用户集合的Redis键
     *
     * @param contentId 内容ID
     * @return Redis键
     */
    public String getContentLikeSetKey(Long contentId) {
        return likeKeyPrefix + "set:" + contentId;
    }

    /**
     * 获取页面访问计数的Redis键
     *
     * @param contentId 内容ID
     * @return Redis键
     */
    public String getPageViewCountKey(Long contentId) {
        return pageViewKeyPrefix + "count:" + contentId;
    }

    /**
     * 获取页面唯一访客集合的Redis键
     *
     * @param contentId 内容ID
     * @return Redis键
     */
    public String getUniqueVisitorSetKey(Long contentId) {
        return uniqueVisitorKeyPrefix + "set:" + contentId;
    }

    /**
     * 获取游戏玩家积分的Redis键
     *
     * @param playerId 玩家ID
     * @return Redis键
     */
    public String getPlayerScoreKey(Long playerId) {
        return gamePlayerKeyPrefix + "score:" + playerId;
    }

    /**
     * 获取游戏积分排行榜的Redis键
     *
     * @return Redis键
     */
    public String getScoreLeaderboardKey() {
        return gamePlayerKeyPrefix + "leaderboard";
    }

    /**
     * 获取热搜词热度值的Redis键
     *
     * @param keyword 关键词
     * @return Redis键
     */
    public String getHotSearchValueKey(String keyword) {
        return hotSearchKeyPrefix + "value:" + keyword;
    }

    /**
     * 获取热搜排行榜的Redis键
     *
     * @return Redis键
     */
    public String getHotSearchLeaderboardKey() {
        return hotSearchKeyPrefix + "leaderboard";
    }
}