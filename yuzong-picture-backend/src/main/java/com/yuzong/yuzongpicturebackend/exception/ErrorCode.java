package com.yuzong.yuzongpicturebackend.exception;

import lombok.Getter;

/**
 * @author : Yuzong
 * 异常枚举类
 * @date 2026/5/11 23:18
 **/
@Getter   //这里导入的是lombok的
public enum ErrorCode {

    //    这个错误码和message是给前端控制台和后端日志查看的，前端不会看到
    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
