-- 创建数据库
CREATE DATABASE IF NOT EXISTS realtime_stats DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE realtime_stats;

-- 内容表
CREATE TABLE IF NOT EXISTS `content` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '内容ID',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `author_id` bigint(20) NOT NULL COMMENT '作者ID',
  `view_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '浏览量',
  `like_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '点赞数',
  `comment_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '评论数',
  `share_count` bigint(20) NOT NULL DEFAULT '0' COMMENT '分享数',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容表';

-- 游戏玩家表
CREATE TABLE IF NOT EXISTS `game_player` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '玩家ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `score` bigint(20) NOT NULL DEFAULT '0' COMMENT '积分',
  `level` int(11) NOT NULL DEFAULT '1' COMMENT '等级',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏玩家表';

-- 热搜词表
CREATE TABLE IF NOT EXISTS `hot_search` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `keyword` varchar(100) NOT NULL COMMENT '关键词',
  `hot_value` bigint(20) NOT NULL DEFAULT '0' COMMENT '热度值',
  `rank` int(11) DEFAULT NULL COMMENT '排名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_keyword` (`keyword`),
  KEY `idx_hot_value` (`hot_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热搜词表';

-- 点赞记录表
CREATE TABLE IF NOT EXISTS `like_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `content_id` bigint(20) NOT NULL COMMENT '内容ID',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：0-取消点赞，1-已点赞',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_content` (`user_id`,`content_id`),
  KEY `idx_content_id` (`content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';

-- 创建页面访问记录表
CREATE TABLE IF NOT EXISTS `page_view` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `content_id` BIGINT NOT NULL COMMENT '内容ID',
    `user_id` BIGINT COMMENT '用户ID，未登录用户为NULL',
    `ip_address` VARCHAR(50) NOT NULL COMMENT 'IP地址',
    `user_agent` VARCHAR(500) COMMENT '用户代理',
    `referer` VARCHAR(500) COMMENT '来源页面',
    `visit_time` DATETIME NOT NULL COMMENT '访问时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_content_id` (`content_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_visit_time` (`visit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面访问记录表';

-- 创建每日统计数据表
CREATE TABLE IF NOT EXISTS `daily_stats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `content_id` BIGINT NOT NULL COMMENT '内容ID',
    `stats_date` DATE NOT NULL COMMENT '统计日期',
    `pv_count` INT NOT NULL DEFAULT 0 COMMENT '页面浏览量',
    `uv_count` INT NOT NULL DEFAULT 0 COMMENT '独立访客数',
    `ip_count` INT NOT NULL DEFAULT 0 COMMENT '独立IP数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_date` (`content_id`, `stats_date`),
    INDEX `idx_stats_date` (`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计数据表';

-- 创建每小时统计数据表
CREATE TABLE IF NOT EXISTS `hourly_stats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `content_id` BIGINT NOT NULL COMMENT '内容ID',
    `stats_date` DATE NOT NULL COMMENT '统计日期',
    `stats_hour` INT NOT NULL COMMENT '统计小时(0-23)',
    `pv_count` INT NOT NULL DEFAULT 0 COMMENT '页面浏览量',
    `uv_count` INT NOT NULL DEFAULT 0 COMMENT '独立访客数',
    `ip_count` INT NOT NULL DEFAULT 0 COMMENT '独立IP数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_date_hour` (`content_id`, `stats_date`, `stats_hour`),
    INDEX `idx_stats_date_hour` (`stats_date`, `stats_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每小时统计数据表';
