package com.yuzong.yuzongpicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuzong.yuzongpicturebackend.annotation.AuthCheck;
import com.yuzong.yuzongpicturebackend.common.BaseResponse;
import com.yuzong.yuzongpicturebackend.common.DeleteRequest;
import com.yuzong.yuzongpicturebackend.common.ResultUtils;
import com.yuzong.yuzongpicturebackend.constant.UserConstant;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.exception.ThrowUtils;
import com.yuzong.yuzongpicturebackend.manager.auth.SpaceUserAuthManager;
import com.yuzong.yuzongpicturebackend.model.dto.sapce.*;
import com.yuzong.yuzongpicturebackend.model.entity.Space;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.enums.SpaceLevelEnum;
import com.yuzong.yuzongpicturebackend.model.vo.SpaceVO;
import com.yuzong.yuzongpicturebackend.service.SpaceService;
import com.yuzong.yuzongpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间管理
 */
@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Autowired
    private SpaceUserAuthManager spaceUserAuthManager;


    /**
     * 0. 获取空间等级列表【展示给前端】
     * 将空间等级的枚举转化为列表
     *
     * @return 空间等级列表
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }


    /**
     * 1. 删除空间(用户和管理员都能用)
     *
     * @param deleteRequest 删除请求参数:id
     * @param
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
//        判断参数是否正常
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "删除请求参数错误");
        }
//        获取当前登录用户
        User loginUser = userService.getLoginUser(request);
//        拿到传递进来的空间id
        Long id = deleteRequest.getId();
//        判断是否存在
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//        如果没抛出异常，就存在。要验证是否本人或者管理员。
        spaceService.checkSpaceAuth(loginUser, oldSpace);
//        操作数据库
        boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    /**
     * 2. 更新空间信息(管理员用)
     * 比如更新空间的等级之类的，比普通用户可更新的更多
     *
     * @param spaceUpdateRequest 更新请求参数:空间的：id，名称，简介，分类，标签
     * @param
     * @return 更新结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        // 1.参数校验
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2.将dto类转化为实体类
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);

        // 3.根据等级填充空间容量大小
        spaceService.fillSpaceBySpaceLevel(space);

        // 4. 数据校验
        spaceService.validSpace(space, false);
        // 5. 判断是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 6. 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 3. 根据 id 获取空间（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(space);
    }

    /**
     * 4. 根据 id 获取空间（封装类）（用户版）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 补充：
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);// 将space转化为spaceVO
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);// 获取权限列表
        spaceVO.setPermissionList(permissionList); //设置权限列表

        // 获取封装类
        return ResultUtils.success(spaceVO);
    }

    /**
     * 5. 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
//        页码，页面大小
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 6. 分页获取空间列表（封装类）（用户用）
     * 从数据库中获取空间链接，展示给前端
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                         HttpServletRequest request) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    /**
     * 7. 编辑空间（给用户使用的更新。和更新空间基本一致）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 根据等级填充空间容量大小
        spaceService.fillSpaceBySpaceLevel(space);
        // 设置编辑时间
        space.setEditTime(new Date());
        // 数据校验
        spaceService.validSpace(space, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        spaceService.checkSpaceAuth(loginUser, oldSpace);
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 8. 添加空间（用户版）
     */
    @PostMapping("/add")
    public BaseResponse<Long> adespace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        //  获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用添加空间方法
        long newId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(newId);


    }


}
