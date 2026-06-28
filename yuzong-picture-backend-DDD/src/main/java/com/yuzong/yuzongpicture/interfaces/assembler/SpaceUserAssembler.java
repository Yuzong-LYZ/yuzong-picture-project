package com.yuzong.yuzongpicture.interfaces.assembler;

import com.yuzong.yuzongpicture.domain.space.entity.SpaceUser;
import com.yuzong.yuzongpicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.yuzong.yuzongpicture.interfaces.dto.spaceuser.SpaceUserEditRequest;
import org.springframework.beans.BeanUtils;

/**
 * 空间成员对象转换
 */
public class SpaceUserAssembler {

    public static SpaceUser toSpaceUserEntity(SpaceUserAddRequest request) {
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(request, spaceUser);
        return spaceUser;
    }

    public static SpaceUser toSpaceUserEntity(SpaceUserEditRequest request) {
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(request, spaceUser);
        return spaceUser;
    }
}