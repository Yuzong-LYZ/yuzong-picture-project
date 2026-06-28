package com.yuzong.yuzongpicture.interfaces.vo.picture;

import lombok.Data;

import java.util.List;

/**
 * @author : Yuzong
 * 返回给前端的一些图片标签分类
 * @date 2026/5/25 10:29
 **/
@Data
public class PictureTagCategory {
    //标签列表
    private List<String> tagList;
    //图片列表
    private List<String> categoryList;
}
