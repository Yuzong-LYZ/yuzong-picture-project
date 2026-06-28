package com.yuzong.yuzongpicture.interfaces.assembler;

import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceAddRequest;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceEditRequest;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceUpdateRequest;
import org.springframework.beans.BeanUtils;

/**
 * 空间对象转换
 */
public class SpaceAssembler {

    public static Space toSpaceEntity(SpaceAddRequest request) {
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }

    public static Space toSpaceEntity(SpaceUpdateRequest request) {
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }

    public static Space toSpaceEntity(SpaceEditRequest request) {
        Space space = new Space();
        BeanUtils.copyProperties(request, space);
        return space;
    }
}