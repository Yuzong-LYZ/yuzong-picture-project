package com.yuzong.yuzongpicturebackend.model.enums;


import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

/**
 * @author : Yuzong
 * 用户角色枚举类，用户定义和管理，系统中的用户角色
 * @date 2026/5/17 14:25
 */
@Getter
public enum UserRoleEnum {

    /**
     * 普通用户角色
     * text: 显示名称 "用户"
     * value: 数据库存储值 "user"
     */
    USER("用户", "user"),

    /**
     * 管理员角色
     * text: 显示名称 "管理员"
     * value: 数据库存储值 "admin"
     */
    ADMIN("管理员", "admin");

    /**
     * 角色的中文显示名称
     * 角色的代码值(用于数据库存储和接口传输)
     */
    private final String text;
    private final String value;

    /**
     * 枚举构造函数
     *
     * @param text  角色显示名称
     * @param value 角色代码值
     */
    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据角色代码值获取对应的枚举对象
     *
     * @param value 角色代码值("user" 或 "admin")
     * @return 对应的枚举对象,找不到则返回 null
     */
    public static UserRoleEnum getEnumByValue(String value) {
        // 参数为空直接返回 null
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }

        // 遍历所有枚举值,查找匹配的枚举对象
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if (userRoleEnum.value.equals(value)) {
                return userRoleEnum;
            }
        }

        return null;
    }
}

