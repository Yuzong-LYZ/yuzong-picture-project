package com.yuzong.yuzongpicturebackend.common;

import lombok.Data;

/**
 * @projectName：yuzong-picture-backend
 * @date_time：2026/5/12 00:03
 * @author：Yuzong
 * @description：分页请求包装类
 **/
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";
}
