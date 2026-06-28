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
import com.yuzong.yuzongpicturebackend.model.dto.user.*;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.vo.UserLoginVO;
import com.yuzong.yuzongpicturebackend.model.vo.UserVO;
import com.yuzong.yuzongpicturebackend.service.UserService;
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
    private UserService userService;

    /**
     * 1. 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
//        如果参数为空，则返回错误信息
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
//        获取请求体的参数
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

//        如果请求体参数不为空，那就可以直接调用注册方法了（Service的）
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
//        这里返回怎么用的ResultUtils？这里其实是我们之前初始化定义的。
//        说白了就是本来的返回值不是他，但是我们将BaseResponse返回值封装成ResultUtils，然后返回给前端
//        我感觉还是复杂了，按照不准确的理解就是：BaseResponse就像是定义了格式，
//        ResultUtils他其实实际上是将按照BaseResponse的格式，将数据返回给前端的
        return ResultUtils.success(result);
    }

    /**
     * 2. 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //        如果参数为空，则返回错误信息
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        //        获取请求体的参数
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
//        如果请求体参数不为空，那就可以直接调用登录方法了（Service的）
        UserLoginVO result = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<UserLoginVO> getLoginUser(HttpServletRequest request) {
        User result = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginVO(result));
    }

    /**
     * 3. 用户注销登出
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
//        如果request为空，则返回错误信息
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
//        既然走到这里，肯定不为空，则直接调用登出方法
        boolean result = userService.userLogout(request);
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
        User user = new User();
//        将请求体参数复制给实体类
        BeanUtils.copyProperties(userAddRequest, user);


//        设置默认密码
        final String DEFAULT_USER_PASSWORD = "12345678";
//        加密
        String bCryptPassword = userService.getBCryptPassword(DEFAULT_USER_PASSWORD);
        user.setUserPassword(bCryptPassword);
//        插入数据库
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
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
        User user = userService.getById(id);
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

        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
//        将查询到的用户转换成包装类（脱敏的user类）
        return ResultUtils.success(userService.getUserVO(user));
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
        boolean b = userService.removeById(deleteRequest.getId());
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
        User user = new User();
//        将请求体参数复制给实体类
        BeanUtils.copyProperties(userUpdateRequest, user);
//        这里是根据传递进来的请求体参数，里面包含id，和其他各种乱七八糟的玩意。
//        我调用userService.updateById(user);他会根据id定位到我的位置，然后将请求体的参数更新到数据库中。
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

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

        // 2. 提取分页参数：告诉数据库从哪一页开始拿，拿多少条
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();

        // 3. 执行分页查询：底层自动生成带 LIMIT 的 SQL，返回包含 User 实体的分页对象
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));

        // 4. 创建一个新的分页对象，保留了 total（总记录数），这样前端才知道一共有多少页
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());

        // 5. userPage.getRecords()拿到数据库查出来的原始用户列表，调用 getUserVOList把它们一个个转换成 UserVO
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());

        // 6. 填充数据：将脱敏后的列表放入新的分页对象当中，准备返回给前端
        userVOPage.setRecords(userVOList);

        // 7. 统一格式返回
        return ResultUtils.success(userVOPage);
    }

}


