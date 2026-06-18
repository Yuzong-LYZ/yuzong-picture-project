package com.yuzong.yuzongpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

//批量导入图片请求
@Data
public class PictureUploadByBatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 搜索词
     */
    private String searchText;

    //新增：
    /**
     * 抓取数量
     */
    private Integer count = 10;
    /**
     * 名称前缀
     */
    private String namePrefix;
}
