package com.yuzong.yuzongpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.yuzong.yuzongpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import com.yuzong.yuzongpicture.domain.space.entity.SpaceUser;
import com.yuzong.yuzongpicture.interfaces.vo.space.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author yuzong
 *  针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2026-06-17 21:21:44
 */
public interface SpaceUserApplicationService extends IService<SpaceUser> {

    /**
     * 5. 构建查询条件。构建 SpaceUser（空间用户）实体的查询条件构造器
     * 根据传入的查询请求对象，动态拼接 SQL 的 WHERE 子句
     *
     * @param spaceUserQueryRequest 前端传入的查询请求参数对象
     * @return 构建完成的 MyBatis-Plus QueryWrapper 条件构造器
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 4.获取空间用户视图对象（批量）
     *
     * @param spaceUserList 空间用户对象列表
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 3.获取空间用户视图对象（单个）
     *
     * @param spaceUser 空间用户对象
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 2.添加空间用户（将用户关联到某个空间）
     *
     * @param spaceUserAddRequest 前端/调用方传入的添加请求对象
     * @return 新创建的空间用户记录的 ID
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 1. 校验空间用户信息是否合法
     *
     * @param spaceUser 空间用户对象
     * @param add       是否为创建（新增）操作。true=新增，false=更新
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);
}
