package org.example.realtimestats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.realtimestats.entity.LikeRecord;

/**
 * 点赞记录Mapper接口
 */
@Mapper
public interface LikeRecordMapper extends BaseMapper<LikeRecord> {

    /**
     * 查询用户是否点赞过内容
     *
     * @param userId    用户ID
     * @param contentId 内容ID
     * @return 点赞记录
     */
    @Select("SELECT * FROM like_record WHERE user_id = #{userId} AND content_id = #{contentId} LIMIT 1")
    LikeRecord findByUserIdAndContentId(@Param("userId") Long userId, @Param("contentId") Long contentId);

    /**
     * 更新点赞状态
     *
     * @param id     记录ID
     * @param status 状态：0-取消点赞，1-已点赞
     * @return 影响的行数
     */
    @Update("UPDATE like_record SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 统计内容的点赞数
     *
     * @param contentId 内容ID
     * @return 点赞数
     */
    @Select("SELECT COUNT(*) FROM like_record WHERE content_id = #{contentId} AND status = 1")
    Long countByContentId(@Param("contentId") Long contentId);

    /**
     * 批量插入点赞记录
     * 注意：MyBatis-Plus不直接支持批量插入，需要在XML中定义
     * 这里只是声明方法，实际实现在XML中
     *
     * @param records 点赞记录列表
     * @return 影响的行数
     */
    int batchInsert(@Param("records") java.util.List<LikeRecord> records);
}