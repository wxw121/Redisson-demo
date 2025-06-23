-- 初始化3篇示例文章
INSERT INTO content (id, title, author, create_time, update_time, like_count, view_count) VALUES
(1, 'Spring Boot最佳实践', '王小明', NOW(), NOW(), 0, 0),
(2, 'Redis深度解析', '李华', NOW(), NOW(), 0, 0),
(3, '微服务架构设计', '张伟', NOW(), NOW(), 0, 0);

-- 可选：初始化一些点赞记录
-- INSERT INTO like_record (user_id, content_id, status, create_time, update_time) VALUES
-- (1001, 1, 1, NOW(), NOW()),
-- (1002, 1, 1, NOW(), NOW()),
-- (1003, 2, 0, NOW(), NOW());
