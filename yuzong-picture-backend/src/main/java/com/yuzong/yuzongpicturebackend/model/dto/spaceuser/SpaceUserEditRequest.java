package com.yuzong.yuzongpicturebackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑空间成员请求，给管理员（不是系统的管理员）用的
 */
@Data
public class SpaceUserEditRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * id
     */
    private Long id;
    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;
}