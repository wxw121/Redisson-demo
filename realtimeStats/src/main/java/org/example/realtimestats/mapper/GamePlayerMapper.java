package org.example.realtimestats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.realtimestats.entity.GamePlayer;

import java.util.List;

/**
 * 游戏玩家Mapper接口
 */
@Mapper
public interface GamePlayerMapper extends BaseMapper<GamePlayer> {

    /**
     * 增加玩家积分
     *
     * @param playerId 玩家ID
     * @param score    增加的积分
     * @return 影响的行数
     */
    @Update("UPDATE game_player SET score = score + #{score} WHERE id = #{playerId}")
    int incrementScore(@Param("playerId") Long playerId, @Param("score") Long score);

    /**
     * 更新玩家等级
     *
     * @param playerId 玩家ID
     * @param level    新等级
     * @return 影响的行数
     */
    @Update("UPDATE game_player SET level = #{level} WHERE id = #{playerId}")
    int updateLevel(@Param("playerId") Long playerId, @Param("level") Integer level);

    /**
     * 获取积分排行榜
     *
     * @param limit 限制数量
     * @return 玩家列表
     */
    @Select("SELECT * FROM game_player WHERE status = 1 AND deleted = 0 " +
            "ORDER BY score DESC LIMIT #{limit}")
    List<GamePlayer> getTopPlayers(@Param("limit") Integer limit);

    /**
     * 获取玩家排名
     *
     * @param playerId 玩家ID
     * @return 排名（从1开始）
     */
    @Select("SELECT COUNT(*) + 1 FROM game_player " +
            "WHERE status = 1 AND deleted = 0 AND score > " +
            "(SELECT score FROM game_player WHERE id = #{playerId})")
    Integer getPlayerRank(@Param("playerId") Long playerId);

    /**
     * 批量更新玩家积分
     * 注意：MyBatis-Plus不直接支持批量更新，需要在XML中定义
     * 这里只是声明方法，实际实现在XML中
     *
     * @param players 玩家列表（包含ID和新的积分）
     * @return 影响的行数
     */
    int batchUpdateScore(@Param("players") List<GamePlayer> players);

    /**
     * 获取指定等级范围的玩家
     *
     * @param minLevel 最小等级
     * @param maxLevel 最大等级
     * @return 玩家列表
     */
    @Select("SELECT * FROM game_player WHERE status = 1 AND deleted = 0 " +
            "AND level BETWEEN #{minLevel} AND #{maxLevel} ORDER BY level DESC, score DESC")
    List<GamePlayer> getPlayersByLevelRange(@Param("minLevel") Integer minLevel,
                                          @Param("maxLevel") Integer maxLevel);
}