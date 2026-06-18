package com.yuzong.yuzongpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicturebackend.model.dto.sapce.SpaceAddRequest;
import com.yuzong.yuzongpicturebackend.model.dto.sapce.SpaceQueryRequest;
import com.yuzong.yuzongpicturebackend.model.entity.Space;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author yuzong
 *  针对表【space(空间)】的数据库操作Service
 * @createDate 2026-06-07 00:54:49
 */
public interface SpaceService extends IService<Space> {

    /**
     * 6.添加空间
     *
     * @param spaceAddRequest 添加空间请求参数
     * @param loginUser       登录用户
     * @return 添加的空间ID
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 5.根据空间等级填充空间大小之类的
     *
     * @param space 空间实体类
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 4.获取查询条件对象
     * 获取查询条件的对象.这里参数是查询用户请求封装类。啥意思呢，就是查询用户功能的request（请求体那样（不严谨））
     *
     * @param spaceQueryRequest 获取查询条件的对象
     * @return 获取查询条件的对象
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 3.将空间转化为VO类【单个】
     *
     * @param space   空间实体类
     * @param request 请求对象
     * @return 空间视图对象
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 2.分页获取空间列表转换为VO类【多个】
     *
     * @param spacePage 分页参数
     * @param request   请求对象
     * @return 空间视图对象列表
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 1.校验空间参数
     *
     * @param space 空间实体类
     *              新增一个是否 创建时校验
     */
    void validSpace(Space space, boolean add);

    /**
     * 校验空间权限
     *
     * @param loginUser 登录用户
     * @param space     空间实体类
     */
    void checkSpaceAuth(User loginUser, Space space);
}
