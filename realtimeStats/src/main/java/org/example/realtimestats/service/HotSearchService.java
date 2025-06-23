package org.example.realtimestats.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.realtimestats.entity.HotSearch;

import java.util.List;
import java.util.Map;

/**
 * 热搜词服务接口
 */
public interface HotSearchService extends IService<HotSearch> {

    /**
     * 增加热搜词热度
     *
     * @param keyword  关键词
     * @param hotValue 增加的热度值
     * @return 是否成功
     */
    boolean incrementHotValue(String keyword, Long hotValue);

    /**
     * 记录搜索关键词
     *
     * @param keyword 关键词
     * @return 是否成功
     */
    boolean recordSearch(String keyword);

    /**
     * 获取热搜排行榜
     *
     * @param limit 限制数量
     * @return 热搜词列表
     */
    List<HotSearch> getHotSearchList(Integer limit);

    List<HotSearch> getTopHotSearches(Integer limit);

    /**
     * 获取热搜词热度
     *
     * @param keyword 关键词
     * @return 热度值
     */
    Long getHotValue(String keyword);

    /**
     * 更新热搜词排名
     *
     * @param id   热搜词ID
     * @param rank 新排名
     * @return 是否成功
     */
    boolean updateRank(Long id, Integer rank);

    /**
     * 批量更新热搜词热度和排名
     *
     * @param hotSearchMap 热搜词和热度值的Map
     * @return 更新成功的数量
     */
    int batchUpdateHotValue(Map<String, Long> hotSearchMap);

    /**
     * 获取热搜趋势
     *
     * @param keyword 关键词
     * @param hours   小时数
     * @return 趋势数据，键为时间点，值为热度
     */
    Map<String, Long> getHotSearchTrend(String keyword, int hours);

    /**
     * 同步Redis中的热搜数据到数据库
     *
     * @return 同步的记录数
     */
    int syncHotSearchesToDb();

    /**
     * 清理过期的热搜词
     *
     * @param days 保留天数
     * @return 清理的记录数
     */
    int cleanExpiredHotSearches(int days);
}