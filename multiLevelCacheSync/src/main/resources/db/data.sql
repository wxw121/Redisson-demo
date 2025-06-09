-- 清空现有数据
DELETE FROM t_user;

-- 插入测试用户数据
INSERT INTO t_user (username, password, email, create_time, update_time, status)
VALUES
    ('admin', '$2a$10$X/hR.X6Zj4qn.QK9V1Zgd.lZ.8ZeGE9SNqJWJ9DJOKjR5VHVbhGGi', 'admin@example.com', NOW(), NOW(), 0),
    ('user1', '$2a$10$8KxT4JaYSp8uqUzF/ZxQeOMeZD1r3VZCHYk.O6pVBE0P5HEQBW8.q', 'user1@example.com', NOW(), NOW(), 0),
    ('user2', '$2a$10$8KxT4JaYSp8uqUzF/ZxQeOMeZD1r3VZCHYk.O6pVBE0P5HEQBW8.q', 'user2@example.com', NOW(), NOW(), 0),
    ('test1', '$2a$10$8KxT4JaYSp8uqUzF/ZxQeOMeZD1r3VZCHYk.O6pVBE0P5HEQBW8.q', 'test1@example.com', NOW(), NOW(), 0),
    ('test2', '$2a$10$8KxT4JaYSp8uqUzF/ZxQeOMeZD1r3VZCHYk.O6pVBE0P5HEQBW8.q', 'test2@example.com', NOW(), NOW(), 0);

-- 注意：密码都是使用BCrypt加密的"123456"
-- 可以使用以下SQL查询验证数据：
-- SELECT * FROM t_user WHERE deleted = 0;
