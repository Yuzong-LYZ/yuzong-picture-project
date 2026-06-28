package com.yuzong.yuzongpicture.infrastructure.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : Yuzong
 * 请求删除包装类
 * @date 2026/5/12 00:04
 **/
@Data
public class DeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * id
     */
    private Long id;
}
