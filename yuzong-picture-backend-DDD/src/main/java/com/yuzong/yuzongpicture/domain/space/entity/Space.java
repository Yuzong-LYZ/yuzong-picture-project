package com.yuzong.yuzongpicture.domain.space.entity;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.*;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceLevelEnum;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceTypeEnum;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 空间
 * 对应数据库表 space
 */
@TableName(value = "space")
@Data
public class Space implements Serializable {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
    /**
     * id:随机生成
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 空间名称
     */
    private String spaceName;
    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;
    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;
    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;
    /**
     * 空间图片的最大数量
     */
    private Long maxCount;
    /**
     * 当前空间下图片的总大小
     */
    private Long totalSize;
    /**
     * 当前空间下的图片数量
     */
    private Long totalCount;
    /**
     * 创建用户 id
     */
    private Long userId;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 编辑时间
     */
    private Date editTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 创建空间时的校验
     */
    public void validSpace(boolean add){
        ThrowUtils.throwIf(this == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = this.getSpaceName();  // 空间名称
        Integer spaceLevel = this.getSpaceLevel();  // 空间等级，如普通专业旗舰
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);  // 空间等级对应的枚举对象
        Integer spaceType = this.getSpaceType(); //补充： 空间类型：私有空间、团队空间
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType); // 补充： 空间类型对应的枚举对象
        // 如果是要创建空间时的校验
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            // 补充：
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不能为空");
            }
        }
        // 创建空间 或 修改空间时，空间名称长度不能超过30个字符
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        // 修改数据时，空间级别进行校验，如果空间级别传递进来了，但是枚举类当中没这级别，则报错
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        // 补充：修改数据时，空间类型进行校验，如果空间类型传递进来了，但是枚举类当中没这类型，则报错
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
    }
}