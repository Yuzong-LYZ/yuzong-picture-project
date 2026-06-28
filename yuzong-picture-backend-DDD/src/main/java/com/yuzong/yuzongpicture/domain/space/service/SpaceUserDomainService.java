package com.yuzong.yuzongpicture.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicture.domain.space.entity.SpaceUser;
import com.yuzong.yuzongpicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.yuzong.yuzongpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import com.yuzong.yuzongpicture.interfaces.vo.space.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author yuzong
 *  针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2026-06-17 21:21:44
 */
public interface SpaceUserDomainService  {

    /**
     * 5. 构建查询条件。构建 SpaceUser（空间用户）实体的查询条件构造器
     * 根据传入的查询请求对象，动态拼接 SQL 的 WHERE 子句
     *
     * @param spaceUserQueryRequest 前端传入的查询请求参数对象
     * @return 构建完成的 MyBatis-Plus QueryWrapper 条件构造器
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

}
