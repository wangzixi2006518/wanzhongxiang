package com.wanzhongxiang.mapper;

import com.wanzhongxiang.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper {

    // 根据 openid 查询用户
    @Select("select * from user where openid = #{openid}")
    User getByOpenId(String openid);

    // 插入用户数据，并返回主键值（Controller 中 .id(user.getId()) 会使用）
    void insert(User user);

    // 根据 id 查询用户
    @Select("select * from user where id = #{id}")
    User getById(Long userId);

    // 根据动态条件统计用户数量
    Integer countByMap(Map map);

}
