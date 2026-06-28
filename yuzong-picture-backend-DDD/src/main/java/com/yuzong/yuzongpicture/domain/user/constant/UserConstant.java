package com.yuzong.yuzongpicture.domain.user.constant;

//用户常量
//个人认为可以在前面的枚举类当中，定义，而不是重新建个类。
public interface UserConstant {
    //用户登录态键
    String USER_LOGIN_STATE = "user_login";

    //region 权限

    /**
     * 默认权限
     */
    String DEFAULT_ROLE = "user";
    /**
     * 管理员权限
     */
    String ADMIN_ROLE = "admin";

    //endregion
}
