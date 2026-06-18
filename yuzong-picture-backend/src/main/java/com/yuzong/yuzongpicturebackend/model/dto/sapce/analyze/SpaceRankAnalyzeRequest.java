package com.yuzong.yuzongpicturebackend.model.dto.sapce.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间使用排行分析请求（管理员）
 */

@Data
public class SpaceRankAnalyzeRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 排名前 N 的空间
     */
    private Integer topN = 10;
}