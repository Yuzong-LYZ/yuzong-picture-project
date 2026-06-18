package com.yuzong.yuzongpicturebackend.api.imagesearch;

import com.yuzong.yuzongpicturebackend.api.imagesearch.model.ImageSearchResult;
import com.yuzong.yuzongpicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.yuzong.yuzongpicturebackend.api.imagesearch.sub.GetImageListApi;
import com.yuzong.yuzongpicturebackend.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);  // 获取以图搜图页面地址（step1）
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);  // 根据页面地址获取图片列表页面地址（step2）
        // 根据图片列表地址获取图片列表（step3）
        return GetImageListApi.getImageList(imageFirstUrl); // 返回图片列表
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        List<ImageSearchResult> resultList = searchImage(imageUrl);
        System.out.println("结果列表" + resultList);
    }
}
