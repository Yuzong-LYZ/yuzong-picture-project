package com.yuzong.yuzongpicture.infrastructure.common;

import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;

/**
 * @author : Yuzong
 * 统一结果工具类：简化 BaseResponse 的创建过程；
 * 很复杂？讲人话：ResultUtils他其实实际上是将按照BaseResponse的格式，将数据返回给前端的
 * @date 2026/5/11 23:48
 **/
public class ResultUtils {

    /**
     * 成功
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 响应
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 失败
     *
     * @param errorCode 错误码
     * @return 响应
     */
    public static BaseResponse<?> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    /**
     * 失败
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 响应
     */
    public static BaseResponse<?> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }

    /**
     * 失败
     *
     * @param errorCode 错误码
     * @return 响应
     */
    public static BaseResponse<?> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}


