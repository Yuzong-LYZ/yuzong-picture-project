package com.yuzong.yuzongpicture.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import com.yuzong.yuzongpicture.domain.user.service.UserDomainService;
import com.yuzong.yuzongpicture.infrastructure.common.DeleteRequest;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import com.yuzong.yuzongpicture.interfaces.dto.user.UserLoginRequest;
import com.yuzong.yuzongpicture.interfaces.dto.user.UserQueryRequest;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.interfaces.dto.user.UserRegisterRequest;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserLoginVO;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * @author yuzong
 *  针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-05-16 18:29:50
 */
@Slf4j
@Service
public class UserApplicationServiceImpl implements UserApplicationService {

    @Resource
    private UserDomainService userDomainService;

    /**
     * 1. 用户注册 方法
     *
     * @return 新用户id
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        //获取请求体的参数
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        // 1. 校验
        User.validUserRegister(userAccount, userPassword, checkPassword);
        // 2. 执行
        return userDomainService.userRegister(userAccount, userPassword, checkPassword);
    }

    /**
     * 2. 用户登录
     *
     * @return 脱敏后的用户信息
     */
    @Override
    public UserLoginVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //        获取请求体的参数
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        // 1. 校验
        User.validUserLogin(userAccount, userPassword, request);
        // 2. 执行
        return userDomainService.userLogin(userAccount, userPassword, request);
    }

    /**
     * 获取脱敏后的用户信息：把用户信息封装成VO类
     *
     * @param user
     * @return
     */
    @Override
    public UserLoginVO getLoginVO(User user) {
        return userDomainService.getLoginVO(user);
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        return userDomainService.getLoginUser(request);
    }

    /**
     * 3. 用户注销
     *
     * @param request
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        return userDomainService.userLogout(request);
    }

    /**
     * 获取脱敏后的用户信息
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        return userDomainService.getUserVO(user);
    }

    /**
     * 获取脱敏后的用户列表
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        return userDomainService.getUserVOList(userList);
    }


    // 获取查询条件的对象：到时候调用这个查询语句，根据这个语句去执行查询
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        return userDomainService.getQueryWrapper(userQueryRequest);
    }



    /**
     * BCrypt密码加密方法
     *
     * @param userPassword
     * @return 加密后的密码
     * 加密原理：加密后的密码=A部分（明文密码转化的固定的玩意）+B部分（随机盐）+C部分组成。
     * A部分密码相同的情况下是固定不变的（大致如此），B部分是随机的盐，C部分是由A部分+B部分进行一个未知算法得出的C。
     * 密码验证原理：检验密码的时候只需要将输入进来的密码转化为A部分，然后用A+B部分进行未知算法运算，得出来的C和数据库存放C一致就可以登录了。
     */
    @Override
    public String getBCryptPassword(String userPassword) {
        return userDomainService.getBCryptPassword(userPassword);
    }

    // 下面为controller调用到的，但是偷懒没写在service的

    @Override
    public long addUser(User user) {
        return userDomainService.addUser(user);
    }

    @Override
    public User getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userDomainService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return user;
    }

    @Override
    public UserVO getUserVOById(long id) {
        return userDomainService.getUserVO(getUserById(id));
    }

    @Override
    public boolean deleteUser(DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return userDomainService.removeById(deleteRequest.getId());
    }

    @Override
    public void updateUser(User user) {
        boolean result = userDomainService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userDomainService.page(new Page<>(current, size),
                userDomainService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userDomainService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return userVOPage;
    }

    @Override
    public List<User> listByIds(Set<Long> userIdSet) {
        return userDomainService.listByIds(userIdSet);
    }

    @Override
    public long saveUser(User userEntity) {
        //        设置默认密码
        final String DEFAULT_USER_PASSWORD = "12345678";
//        加密
        String bCryptPassword = userDomainService.getBCryptPassword(DEFAULT_USER_PASSWORD);
        userEntity.setUserPassword(bCryptPassword);
//        插入数据库
        boolean result = userDomainService.saveUser(userEntity);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return userEntity.getId();
    }


}




