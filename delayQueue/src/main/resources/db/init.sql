-- 创建数据库
CREATE DATABASE IF NOT EXISTS delay_queue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE delay_queue;

-- 创建订单表
CREATE TABLE IF NOT EXISTS `t_order` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` varchar(32) NOT NULL COMMENT '订单号',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `product_id` bigint(20) NOT NULL COMMENT '商品ID',
    `product_name` varchar(100) NOT NULL COMMENT '商品名称',
    `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
    `status` varchar(20) NOT NULL COMMENT '订单状态：PENDING-待支付，PAID-已支付，CANCELLED-已取消',
    `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
    `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
    `cancel_reason` varchar(200) DEFAULT NULL COMMENT '取消原因',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
