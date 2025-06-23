package org.example.realtimestats.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.realtimestats.entity.PageView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 页面访问服务接口
 */
public interface PageViewService extends IService<PageView> {

    /**
     * 记录页面访问
     *
     * @param contentId 内容ID
     * @param userId    用户ID，可为null
     * @param ipAddress IP地址
     * @param userAgent 用户代理
     * @param referer   来源页面
     * @return 是否成功
     */
    boolean recordPageView(Long contentId, Long userId, String ipAddress, String userAgent, String referer);

    /**
     * 获取指定时间范围内的页面访问量
     *
     * @param contentId 内容ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 访问量
     */
    Long getPageViewCount(Long contentId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取指定时间范围内的独立访客数
     *
     * @param contentId 内容ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 独立访客数
     */
    Long getUniqueVisitorCount(Long contentId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取指定时间范围内的访问记录
     *
     * @param contentId 内容ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 访问记录列表
     */
    List<PageView> getPageViews(Long contentId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取实时访问统计数据
     *
     * @param contentId 内容ID
     * @return 统计数据，包含总访问量、当日访问量、当前在线人数等
     */
    Map<String, Object> getRealTimeStats(Long contentId);

    /**
     * 获取指定时间范围内的访问趋势数据
     *
     * @param contentId 内容ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param interval  时间间隔（分钟）
     * @return 趋势数据，键为时间点，值为访问量
     */
    Map<String, Long> getViewTrend(Long contentId, LocalDateTime startTime, LocalDateTime endTime, int interval);

    /**
     * 同步Redis中的页面访问数据到数据库
     *
     * @return 同步的记录数
     */
    int syncPageViewsToDb();

    /**
     * 同步所有内容的页面访问数据到数据库
     */
    void syncAllPageViewDataToDb();
}