package com.wanzhongxiang.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wanzhongxiang.constant.MessageConstant;
import com.wanzhongxiang.dto.UserLoginDTO;
import com.wanzhongxiang.entity.User;
import com.wanzhongxiang.exception.LoginFailedException;
import com.wanzhongxiang.mapper.UserMapper;
import com.wanzhongxiang.properties.WeChatProperties;
import com.wanzhongxiang.service.UserService;
import com.wanzhongxiang.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    // 微信服务接口地址
    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    // 微信登录
    @Override
    public User wxlogin(UserLoginDTO userLoginDTO) {
        // 获取 openid
        String openid = getOpenid(userLoginDTO.getCode());

        // 判断 openid 是否为 null，如果为 null -> 登录失败，抛出业务异常
        if(openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED); // "登录失败"
        }

        // 判断当前用户是否为新用户
        User user = userMapper.getByOpenId(openid);

        // 如果是新用户，自动完成注册
        if(user == null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build(); // 其他信息我们拿不到，后期通过个人中心去完善，先存标识和注册时间的主要信息
            userMapper.insert(user);
        }

        // 返回用户对象
        return user;
    }

    private String getOpenid(String code){
        // 调用微信服务器接口，获取当前微信用户的 openid
        Map<String, String> map = new HashMap<>();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type","authorization_code");
        String json = HttpClientUtil.doGet(WX_LOGIN, map);

        // 根据"openid"查找openid内容
        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");
        return openid;
    }
}
