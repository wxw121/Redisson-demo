package org.example.realtimestats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.realtimestats.entity.PageView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 页面访问记录Mapper接口
 */
@Mapper
public interface PageViewMapper extends BaseMapper<PageView> {

    /**
     * 统计指定时间范围内的页面访问量
     *
     * @param contentId  内容ID
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @return 访问量
     */
    @Select("SELECT COUNT(*) FROM page_view WHERE content_id = #{contentId} " +
            "AND visit_time BETWEEN #{startTime} AND #{endTime}")
    Long countByTimeRange(@Param("contentId") Long contentId,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内的独立访客数
     *
     * @param contentId  内容ID
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @return 独立访客数
     */
    @Select("SELECT COUNT(DISTINCT COALESCE(user_id, ip_address)) FROM page_view " +
            "WHERE content_id = #{contentId} AND visit_time BETWEEN #{startTime} AND #{endTime}")
    Long countUniqueVisitors(@Param("contentId") Long contentId,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

    /**
     * 获取指定时间范围内的访问记录
     *
     * @param contentId  内容ID
     * @param startTime  开始时间
     * @param endTime    结束时间
     * @return 访问记录列表
     */
    @Select("SELECT * FROM page_view WHERE content_id = #{contentId} " +
            "AND visit_time BETWEEN #{startTime} AND #{endTime} ORDER BY visit_time DESC")
    List<PageView> findByTimeRange(@Param("contentId") Long contentId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 批量插入访问记录
     * 注意：MyBatis-Plus不直接支持批量插入，需要在XML中定义
     * 这里只是声明方法，实际实现在XML中
     *
     * @param records 访问记录列表
     * @return 影响的行数
     */
    int batchInsert(@Param("records") List<PageView> records);
    
    /**
     * 更新每日统计数据
     *
     * @param contentId 内容ID
     * @param date 日期
     * @param views 访问量
     */
    void updateDailyStats(@Param("contentId") Long contentId, 
                          @Param("date") java.time.LocalDate date, 
                          @Param("views") Long views);
    
    /**
     * 更新每小时统计数据
     *
     * @param contentId 内容ID
     * @param dateTime 日期时间
     * @param views 访问量
     */
    void updateHourlyStats(@Param("contentId") Long contentId, 
                           @Param("dateTime") LocalDateTime dateTime, 
                           @Param("views") Long views);
}