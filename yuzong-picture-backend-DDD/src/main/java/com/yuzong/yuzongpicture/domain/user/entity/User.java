package com.yuzong.yuzongpicture.domain.user.entity;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.*;
import com.yuzong.yuzongpicture.domain.user.valueobject.UserRoleEnum;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import lombok.Data;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户
 * 对应数据库表 user
 */
@TableName(value = "user")
@Data
public class User implements Serializable {
    /**
     * Java 序列化版本号 默认为1
     * 比如你现在序列化了一个 User 对象到 Redis,后来给 User 类添加了一个新字段:
     * 有固定 serialVersionUID: 可以正常读取旧数据,新字段为 null 或默认值 ✅
     * 没有固定 serialVersionUID: 可能因为版本号变化而报错 ❌
     *
     */
    @TableField
    private static final long serialVersionUID = 1L;
    /**
     * id     IdType.ASSIGN_ID会生成较长id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 账号
     */
    private String userAccount;
    /**
     * 密码
     */
    private String userPassword;
    /**
     * 用户昵称
     */
    private String userName;
    /**
     * 用户头像
     */
    private String userAvatar;
    /**
     * 用户简介
     */
    private String userProfile;
    /**
     * 用户角色：user/admin
     */
    private String userRole;
    /**
     * 编辑时间
     */
    private Date editTime;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 是否逻辑删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 静态方法，用于注册时校验账号密码
     */
    public static void validUserRegister(String userAccount, String userPassword, String checkPassword){
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短，需要>=4");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短，需要>=8");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
    }
    /**
     * 静态方法，用于登录时校验账号密码
     */
    public static void validUserLogin(String userAccount, String userPassword, HttpServletRequest request){
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短，需要>=4");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短，需要>=8");
        }
    }

    /**
     * 判断当前用户是否为管理员
     */
    public boolean isAdmin() {
        return UserRoleEnum.ADMIN.getValue().equals(this.getUserRole());
    }
}