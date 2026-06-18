package com.yuzong.yuzongpicturebackend.model.dto.sapce;


import lombok.AllArgsConstructor;
import lombok.Data;


/**
 * 这个类是给前端的，类似于实体类转vo类，这个就是space级别的枚举转为这个封装类。
 */
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}
