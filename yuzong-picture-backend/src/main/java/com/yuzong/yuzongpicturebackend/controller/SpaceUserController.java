package com.yuzong.yuzongpicturebackend.controller;

import cn.hutool.core.util.ObjectUtil;
import com.yuzong.yuzongpicturebackend.common.BaseResponse;
import com.yuzong.yuzongpicturebackend.common.DeleteRequest;
import com.yuzong.yuzongpicturebackend.common.ResultUtils;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.exception.ThrowUtils;
import com.yuzong.yuzongpicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.yuzong.yuzongpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yuzong.yuzongpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yuzong.yuzongpicturebackend.model.dto.spaceuser.SpaceUserEditRequest;
import com.yuzong.yuzongpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yuzong.yuzongpicturebackend.model.entity.SpaceUser;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.vo.SpaceUserVO;
import com.yuzong.yuzongpicturebackend.service.SpaceUserService;
import com.yuzong.yuzongpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间成员管理
 */
@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    /**
     * 添加成员到空间
     *
     * @param spaceUserAddRequest 添加成员请求参数（包含 spaceId, userId, role 等）
     * @param request             HTTP 请求对象（可用于获取登录态等信息）
     * @return 成功返回新创建的空间用户记录 ID
     */
    @PostMapping("/add")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
        // 1. 基础参数非空校验
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 调用 Service 层执行添加逻辑（内部可能包含重复添加校验、权限校验等）
        long id = spaceUserService.addSpaceUser(spaceUserAddRequest);

        // 3. 封装统一响应结果
        return ResultUtils.success(id);
    }

    /**
     * 从空间移除成员（逻辑删除或物理删除）
     *
     * @param deleteRequest 删除请求参数（包含目标记录 ID）
     * @param request       HTTP 请求对象
     * @return 成功返回 true
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 1. 基础参数校验：对象非空且 ID 必须大于 0
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();

        // 2. 存在性校验：防止删除不存在的数据，避免无效 DB 操作
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 执行删除操作，并对操作结果进行校验
        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 查询某个成员在某个空间的信息（获取单条详情）
     *
     * @param spaceUserQueryRequest 查询请求参数（必须包含 spaceId 和 userId）
     * @return 成功返回 SpaceUser 实体信息
     */
    @PostMapping("/get")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        // 1. 基础参数校验
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();

        // 2. 核心业务字段校验：查询单条关联记录时，空间 ID 和用户 ID 缺一不可
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);

        // 3. 构建查询条件并执行单条查询
        SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);

        return ResultUtils.success(spaceUser);
    }

    /**
     * 查询空间成员信息列表（获取多条记录）
     *
     * @param spaceUserQueryRequest 查询请求参数（支持动态条件过滤）
     * @param request               HTTP 请求对象
     * @return 成功返回 SpaceUserVO 列表（经过脱敏和格式化）
     */
    @PostMapping("/list")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest, HttpServletRequest request) {
        // 1. 基础参数校验
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 根据动态条件查询数据库实体列表
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );

        // 3. 实体类 (Entity) 转换为 视图对象 (VO)，隐藏敏感字段并补充关联信息
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

    /**
     * 编辑成员信息（如：修改空间角色/设置权限）
     *
     * @param spaceUserEditRequest 编辑请求参数（包含 ID 及需要更新的字段）
     * @param request              HTTP 请求对象
     * @return 成功返回 true
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.SPACE_USER_MANAGE)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest, HttpServletRequest request) {
        // 1. 基础参数校验
        if (spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. DTO 转 Entity：将请求对象属性拷贝至数据库实体对象
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);

        // 3. 业务规则校验：调用 Service 层的校验方法（如：角色枚举值是否合法等）
        spaceUserService.validSpaceUser(spaceUser, false);

        // 4. 存在性校验：确保要更新的记录在数据库中存在
        long id = spaceUserEditRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);

        // 5. 执行更新操作并校验结果
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 查询当前登录用户加入的团队空间列表
     *
     * @param request HTTP 请求对象（用于解析当前登录态）
     * @return 成功返回当前用户关联的 SpaceUserVO 列表
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        // 1. 鉴权与身份识别：从请求中获取当前登录用户信息（未登录会抛异常）
        User loginUser = userService.getLoginUser(request);

        // 2. 强制构建查询条件：仅查询当前登录用户 ID 关联的数据
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());

        // 3. 查询数据并转换为 VO 返回
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }
}
