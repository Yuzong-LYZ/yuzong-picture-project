package com.yuzong.yuzongpicturebackend.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @projectName：yuzong-picture-backend
 * @date_time：2026/5/12 00:04
 * @author：Yuzong
 * @description：请求删除包装类
 **/
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
