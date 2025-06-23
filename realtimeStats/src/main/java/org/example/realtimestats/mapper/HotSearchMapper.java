package org.example.realtimestats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.realtimestats.entity.HotSearch;

import java.util.List;

/**
 * 热搜词Mapper接口
 */
@Mapper
public interface HotSearchMapper extends BaseMapper<HotSearch> {

    /**
     * 获取热搜排行榜
     *
     * @param limit 限制数量
     * @return 热搜词列表
     */
    @Select("SELECT * FROM hot_search ORDER BY hot_value DESC, id ASC LIMIT #{limit}")
    List<HotSearch> getTopHotSearches(@Param("limit") Integer limit);

    /**
     * 增加热搜词热度
     *
     * @param id       热搜词ID
     * @param hotValue 增加的热度值
     * @return 影响的行数
     */
    @Update("UPDATE hot_search SET hot_value = hot_value + #{hotValue} WHERE id = #{id}")
    int incrementHotValue(@Param("id") Long id, @Param("hotValue") Long hotValue);

    /**
     * 根据关键词查询热搜记录
     *
     * @param keyword 关键词
     * @return 热搜记录
     */
    @Select("SELECT * FROM hot_search WHERE keyword = #{keyword} LIMIT 1")
    HotSearch findByKeyword(@Param("keyword") String keyword);

    /**
     * 更新热搜词排名
     *
     * @param id   热搜词ID
     * @param rank 新排名
     * @return 影响的行数
     */
    @Update("UPDATE hot_search SET rank = #{rank} WHERE id = #{id}")
    int updateRank(@Param("id") Long id, @Param("rank") Integer rank);

    /**
     * 批量更新热搜词热度和排名
     * 注意：MyBatis-Plus不直接支持批量更新，需要在XML中定义
     * 这里只是声明方法，实际实现在XML中
     *
     * @param hotSearches 热搜词列表（包含ID、热度值和排名）
     * @return 影响的行数
     */
    int batchUpdate(@Param("hotSearches") List<HotSearch> hotSearches);
}