package com.yuzong.yuzongpicturebackend.exception;

import lombok.Getter;

/**
 * @author : Yuzong
 * 自定义业务异常
 * @date 2026/5/11 23:21
 **/
@Getter
public class BusinessException extends RuntimeException {
    /**
     * 错误码
     */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}


