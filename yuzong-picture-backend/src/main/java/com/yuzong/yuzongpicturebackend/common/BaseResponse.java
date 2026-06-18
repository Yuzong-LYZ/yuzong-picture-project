package com.yuzong.yuzongpicturebackend.common;

import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : Yuzong
 * 统一响应类：把所有接口返回给前端的数据都包装成统一的格式，让前端能够一致地处理响应数据。
 * @date 2026/5/11 23:45
 **/
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}



