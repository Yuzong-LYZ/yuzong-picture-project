package com.yuzong.yuzongpicture.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceAddRequest;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceQueryRequest;
import com.yuzong.yuzongpicture.interfaces.vo.space.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author yuzong
 *  针对表【space(空间)】的数据库操作Service
 * @createDate 2026-06-07 00:54:49
 */
public interface SpaceDomainService {


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
     * 校验空间权限
     *
     * @param loginUser 登录用户
     * @param space     空间实体类
     */
    void checkSpaceAuth(User loginUser, Space space);
}
