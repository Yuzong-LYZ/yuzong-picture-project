package com.yuzong.yuzongpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

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
}