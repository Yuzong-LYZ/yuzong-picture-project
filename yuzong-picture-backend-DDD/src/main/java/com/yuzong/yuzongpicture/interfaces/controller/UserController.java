package com.yuzong.yuzongpicture.interfaces.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuzong.yuzongpicture.infrastructure.annotation.AuthCheck;
import com.yuzong.yuzongpicture.infrastructure.common.BaseResponse;
import com.yuzong.yuzongpicture.infrastructure.common.DeleteRequest;
import com.yuzong.yuzongpicture.infrastructure.common.ResultUtils;
import com.yuzong.yuzongpicture.interfaces.assembler.UserAssembler;
import com.yuzong.yuzongpicture.interfaces.dto.user.*;
import com.yuzong.yuzongpicture.domain.user.constant.UserConstant;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserLoginVO;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserVO;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;


/**
 * 用户接口
 *
 * @author yuzong
 */
@RestController
@RequestMapping("/user")
public class UserController {
    //    如果不用@Resource注解，则需要用@Autowired注解，作用：自动注入
    @Resource
    private UserApplicationService userApplicationService;

    /**
     * 1. 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        //        如果参数为空，则返回错误信息
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);

        long result = userApplicationService.userRegister(userRegisterRequest);
        return ResultUtils.success(result);
    }

    /**
     * 2. 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //        如果参数为空，则返回错误信息
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);

//        如果请求体参数不为空，那就可以直接调用登录方法了（Service的）
        UserLoginVO result = userApplicationService.userLogin(userLoginRequest,request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<UserLoginVO> getLoginUser(HttpServletRequest request) {
        User result = userApplicationService.getLoginUser(request);
        return ResultUtils.success(userApplicationService.getLoginVO(result));
    }

    /**
     * 3. 用户注销登出
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
//        如果request为空，则返回错误信息
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
//        既然走到这里，肯定不为空，则直接调用登出方法
        boolean result = userApplicationService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 4. 管理员创建用户
     * 这里就不写后端接口和后端逻辑了。直接在controller这里写就行了。毕竟这个功能也就管理员用。没必要写那么多
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)  //别忘了只有管理员能干这事
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
//        如果参数为空，则返回错误信息
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        //        将请求体参数转换成实体类
        User userEntity = UserAssembler.toUserEntity(userAddRequest);


        return ResultUtils.success(userApplicationService.saveUser(userEntity));
    }

    /**
     * 5. 管理员版本：根据id查看用户（仅管理员，所以不需要脱敏）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
//        id<=0肯定就不可能啊，直接抛出异常
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
//        这里为啥不用qureyWrapper？因为这里是getById是按照主键id查询的。之前的不是，只能用qureyWrapper添加条件再执行
        User user = userApplicationService.getUserById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 5.1 普通用户版本：根据id查看用户，看的用户是脱敏后的
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        //        获取【根据id获取用户】的那个用户【这个好像有bug，老师写的，我调用5的代码，但是5需要管理员权限】
//        BaseResponse<User> response = getUserById(id);
        //        id<=0肯定就不可能啊，直接抛出异常
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        User user = userApplicationService.getUserById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
//        将查询到的用户转换成包装类（脱敏的user类）
        return ResultUtils.success(userApplicationService.getUserVO(user));
    }

    /**
     * 6. 根据id删除用户（仅管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数错误");
        }
//        这里不是真的删除，因为我们在User类当中已经加了逻辑删除的注解，又在全局配置文件配置了。所以删除的时候只不过将0改成1（逻辑删除）.
//        如果不配置逻辑删除，这样执行就是真的从数据库删除了。
        boolean b = userApplicationService.deleteUser(deleteRequest);
        return ResultUtils.success(b);
    }

    /**
     * 7. 更新用户（仅管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数错误");
        }
        User userEntity = UserAssembler.toUserEntity(userUpdateRequest);
//        这里是根据传递进来的请求体参数，里面包含id，和其他各种乱七八糟的玩意。
//        我调用userService.updateById(user);他会根据id定位到我的位置，然后将请求体的参数更新到数据库中。
         userApplicationService.updateUser(userEntity);

        return ResultUtils.success(true);
    }

    /**
     * 8. 获取所有用户（仅管理员）-分页查询
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        // 1. 参数校验：防止空指针
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 7. 统一格式返回
        return ResultUtils.success(userApplicationService.listUserVOByPage(userQueryRequest));
    }

}


