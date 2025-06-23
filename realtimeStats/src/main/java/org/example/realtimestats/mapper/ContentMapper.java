package org.example.realtimestats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.realtimestats.entity.Content;

/**
 * 内容Mapper接口
 */
@Mapper
public interface ContentMapper extends BaseMapper<Content> {

    /**
     * 增加浏览量
     *
     * @param contentId 内容ID
     * @param count     增加的数量
     * @return 影响的行数
     */
    @Update("UPDATE content SET view_count = view_count + #{count} WHERE id = #{contentId}")
    int incrementViewCount(@Param("contentId") Long contentId, @Param("count") Long count);

    /**
     * 增加点赞数
     *
     * @param contentId 内容ID
     * @param count     增加的数量
     * @return 影响的行数
     */
    @Update("UPDATE content SET like_count = like_count + #{count} WHERE id = #{contentId}")
    int incrementLikeCount(@Param("contentId") Long contentId, @Param("count") Long count);

    /**
     * 增加评论数
     *
     * @param contentId 内容ID
     * @param count     增加的数量
     * @return 影响的行数
     */
    @Update("UPDATE content SET comment_count = comment_count + #{count} WHERE id = #{contentId}")
    int incrementCommentCount(@Param("contentId") Long contentId, @Param("count") Long count);

    /**
     * 增加分享数
     *
     * @param contentId 内容ID
     * @param count     增加的数量
     * @return 影响的行数
     */
    @Update("UPDATE content SET share_count = share_count + #{count} WHERE id = #{contentId}")
    int incrementShareCount(@Param("contentId") Long contentId, @Param("count") Long count);

    /**
     * 更新内容计数器
     *
     * @param contentId    内容ID
     * @param viewCount    浏览量
     * @param likeCount    点赞数
     * @param commentCount 评论数
     * @param shareCount   分享数
     * @return 影响的行数
     */
    @Update("UPDATE content SET view_count = #{viewCount}, like_count = #{likeCount}, " +
            "comment_count = #{commentCount}, share_count = #{shareCount} WHERE id = #{contentId}")
    int updateCounters(@Param("contentId") Long contentId,
                       @Param("viewCount") Long viewCount,
                       @Param("likeCount") Long likeCount,
                       @Param("commentCount") Long commentCount,
                       @Param("shareCount") Long shareCount);
}