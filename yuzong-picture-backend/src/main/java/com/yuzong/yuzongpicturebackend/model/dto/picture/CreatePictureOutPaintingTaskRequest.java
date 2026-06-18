package com.yuzong.yuzongpicturebackend.model.dto.picture;

import com.yuzong.yuzongpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 图片 id
     */
    private Long pictureId;
    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;
}