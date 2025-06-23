package org.example.realtimestats.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.realtimestats.entity.Content;

/**
 * 内容服务接口
 */
public interface ContentService extends IService<Content> {

    /**
     * 增加内容浏览量
     *
     * @param contentId 内容ID
     * @return 是否成功
     */
    boolean incrementViewCount(Long contentId);

    /**
     * 增加内容点赞数
     *
     * @param contentId 内容ID
     * @param userId    用户ID
     * @return 是否成功
     */
    boolean like(Long contentId, Long userId);

    /**
     * 取消内容点赞
     *
     * @param contentId 内容ID
     * @param userId    用户ID
     * @return 是否成功
     */
    boolean unlike(Long contentId, Long userId);

    /**
     * 增加内容评论数
     *
     * @param contentId 内容ID
     * @return 是否成功
     */
    boolean incrementCommentCount(Long contentId);

    /**
     * 增加内容分享数
     *
     * @param contentId 内容ID
     * @return 是否成功
     */
    boolean incrementShareCount(Long contentId);

    /**
     * 获取内容统计数据
     *
     * @param contentId 内容ID
     * @return 内容对象，包含各种计数
     */
    Content getContentStats(Long contentId);

    /**
     * 同步Redis中的计数器数据到数据库
     *
     * @param contentId 内容ID
     * @return 是否成功
     */
    boolean syncCountersToDb(Long contentId);

    /**
     * 批量同步Redis中的计数器数据到数据库
     *
     * @return 同步的记录数
     */
    int batchSyncCountersToDb();
}