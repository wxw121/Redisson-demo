package org.example.realtimestats.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.realtimestats.entity.GamePlayer;

import java.util.List;
import java.util.Map;

/**
 * 游戏玩家服务接口
 */
public interface GamePlayerService extends IService<GamePlayer> {

    /**
     * 增加玩家积分
     *
     * @param playerId 玩家ID
     * @param score    增加的积分
     * @return 是否成功
     */
    Long incrementScore(Long playerId, Long score);

    /**
     * 更新玩家等级
     *
     * @param playerId 玩家ID
     * @param level    新等级
     * @return 是否成功
     */
    boolean updateLevel(Long playerId, Integer level);

    /**
     * 获取积分排行榜
     *
     * @param limit 限制数量
     * @return 玩家列表
     */
    List<GamePlayer> getTopPlayers(Integer limit);

    /**
     * 获取玩家排名
     *
     * @param playerId 玩家ID
     * @return 排名（从1开始）
     */
    Integer getPlayerRank(Long playerId);

    /**
     * 获取玩家积分
     *
     * @param playerId 玩家ID
     * @return 玩家积分
     */
    Long getScore(Long playerId);

    /**
     * 获取玩家积分和排名
     *
     * @param playerId 玩家ID
     * @return 包含积分和排名的Map
     */
    Map<String, Object> getPlayerScoreAndRank(Long playerId);

    /**
     * 批量更新玩家积分
     *
     * @param playerScores 玩家ID和积分的Map
     * @return 更新成功的数量
     */
    int batchUpdateScore(Map<Long, Long> playerScores);

    /**
     * 获取指定等级范围的玩家
     *
     * @param minLevel 最小等级
     * @param maxLevel 最大等级
     * @return 玩家列表
     */
    List<GamePlayer> getPlayersByLevelRange(Integer minLevel, Integer maxLevel);

    /**
     * 同步Redis中的玩家积分数据到数据库
     *
     * @return 同步的记录数
     */
    int syncScoresToDb();
}