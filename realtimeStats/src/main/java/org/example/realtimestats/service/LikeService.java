package org.example.realtimestats.service;

/**
 * 点赞服务接口
 */
public interface LikeService {

    /**
     * 用户点赞内容
     *
     * @param userId 用户ID
     * @param contentId 内容ID
     * @return 点赞后的状态（true: 已点赞, false: 已取消点赞）
     */
    boolean likeContent(Long userId, Long contentId);

    /**
     * 获取内容的点赞数
     *
     * @param contentId 内容ID
     * @return 点赞数
     */
    Long getLikeCount(Long contentId);

    /**
     * 检查用户是否已点赞内容
     *
     * @param userId 用户ID
     * @param contentId 内容ID
     * @return 是否已点赞
     */
    boolean hasUserLiked(Long userId, Long contentId);

    /**
     * 同步Redis中的点赞数据到数据库
     *
     * @param contentId 内容ID
     */
    void syncLikeDataToDb(Long contentId);
    
    /**
     * 同步所有内容的点赞数据到数据库
     */
    void syncAllLikeDataToDb();
}