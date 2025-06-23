package org.example.realtimestats.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 页面访问记录实体类
 */
@Data
@TableName("page_view")
public class PageView implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 内容ID
     */
    private Long contentId;

    /**
     * 用户ID，未登录用户为NULL
     */
    private Long userId;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 来源页面
     */
    private String referer;

    /**
     * 访问时间
     */
    private LocalDateTime visitTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}