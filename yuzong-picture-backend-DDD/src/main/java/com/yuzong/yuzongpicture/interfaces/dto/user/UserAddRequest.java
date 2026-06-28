package com.yuzong.yuzongpicture.interfaces.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : Yuzong
 * 管理员-添加用户请求封装类
 * @date 2026/5/19 15:27
 **/
@Data
public class UserAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 用户昵称
     */
    private String userName;
    /**
     * 账号
     */
    private String userAccount;
    /**
     * 用户头像
     */
    private String userAvatar;
    /**
     * 用户简介
     */
    private String userProfile;
    /**
     * 用户角色: user, admin
     */
    private String userRole;
}

