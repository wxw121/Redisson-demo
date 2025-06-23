package org.example.realtimestats.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 热搜词实体类
 */
@Data
@TableName("hot_search")
public class HotSearch implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关键词
     */
    private String keyword;

    /**
     * 热度值
     */
    private Long hotValue;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}