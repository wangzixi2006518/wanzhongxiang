package com.wanzhongxiang.service;

import com.wanzhongxiang.dto.UserLoginDTO;
import com.wanzhongxiang.entity.User;

public interface UserService {

    // 微信用户登录
    User wxlogin(UserLoginDTO userLoginDTO);

}
