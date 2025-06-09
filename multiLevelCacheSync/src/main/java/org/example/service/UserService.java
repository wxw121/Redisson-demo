package org.example.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.entity.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 查找所有用户
     *
     * @return 用户列表
     */
    List<User> getAllUsers();

    /**
     * 根据ID查找用户
     *
     * @param id 用户ID
     * @return 用户对象
     */
    User getUser(Long id);

    /**
     * 保存用户
     *
     * @param user 用户对象
     * @return 保存后的用户对象
     */
    User createUser(User user);

    /**
     * 更新用户
     *
     * @param user 用户对象
     * @return 更新后的用户对象
     */
    User updateUser(User user);

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    boolean deleteUser(Long id);

    /**
     * 根据名称查找用户
     *
     * @param name 用户名称
     * @return 用户列表
     */
    List<User> getUsersByUsernameLike(String name);


    /**
     * 清除指定用户的缓存
     *
     * @param id 用户ID
     */
    void clearCache(Long id);

    /**
     * 清除所有用户缓存
     */
    void clearAllCache();

    /**
     * 获取用户统计信息
     *
     * @return 统计信息
     */
    Map<String, Object> getStats();

    /**
     * 批量保存用户
     *
     * @param users 用户列表
     * @return 保存后的用户列表
     */
    List<User> saveAll(List<User> users);


    /**
     * 检查用户是否存在
     *
     * @param id 用户ID
     * @return 是否存在
     */
    boolean exists(Long id);

    /**
     * 获取用户数量
     *
     * @return 用户数量
     */
    long count();

    /**
     * 分页查询用户
     *
     * @param page 页码
     * @param size 每页大小
     * @return 用户列表
     */
    List<User> findByPage(int page, int size);


    /**
     * 更新用户状态
     *
     * @param id      用户ID
     * @param enabled 是否启用
     * @return 更新后的用户对象
     */
    User updateStatus(Long id, boolean enabled);

    /**
     * 预热用户缓存
     *
     * @param limit 预热数量限制
     */
    void preloadCache(int limit);



    IPage<org.example.entity.User> getUsersByPage(int page, int pageSize, String username, String email);


    org.example.entity.User getUserByEmail(String email);

    List<User> batchCreateUsers(List<User> users);


    boolean batchDeleteUsers(List<Long> ids);


    boolean batchUpdateUsers(List<org.example.entity.User> users);

    int countUsers();

    List<org.example.entity.User> getUsersByStatus(Integer status);

    List<org.example.entity.User> getUsersByCreateTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    List<org.example.entity.User> batchGetUsers(List<Long> ids);
}
