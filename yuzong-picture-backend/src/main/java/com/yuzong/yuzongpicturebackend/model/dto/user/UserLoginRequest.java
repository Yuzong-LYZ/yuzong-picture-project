package com.yuzong.yuzongpicturebackend.model.dto.user;

import lombok.Data;

/**
 * @author : Yuzong
 * 用户登录请求体
 * @date 2026/5/18 16:33
 **/
@Data
public class UserLoginRequest {
    /**
     * 用户账号,密码，校验密码
     */
    private String userAccount;
    private String userPassword;
}
