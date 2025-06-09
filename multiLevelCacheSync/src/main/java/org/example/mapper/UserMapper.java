package org.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.entity.User;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名模糊查询用户列表
     *
     * @param username 用户名
     * @return 用户列表
     */
    @Select("SELECT * FROM t_user WHERE username LIKE CONCAT('%', #{username}, '%') AND deleted = 0")
    List<User> selectByUsernameLike(@Param("username") String username);

    /**
     * 统计用户数量
     *
     * @return 用户数量
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted = 0")
    int countUsers();
}
