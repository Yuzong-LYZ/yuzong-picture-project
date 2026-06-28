package com.yuzong.yuzongpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicture.infrastructure.common.DeleteRequest;
import com.yuzong.yuzongpicture.interfaces.dto.user.UserLoginRequest;
import com.yuzong.yuzongpicture.interfaces.dto.user.UserQueryRequest;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.interfaces.dto.user.UserRegisterRequest;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserLoginVO;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * @author yuzong
 *  针对表【user(用户)】的数据库操作Service
 * @createDate 2026-05-16 18:29:50
 */
public interface UserApplicationService     {
//    密码加密接口  ：密码用 BCrypt加密

    String getBCryptPassword(String userPassword);

    //    1.用户注册接口
    long userRegister(UserRegisterRequest userRegisterRequest);

    //    2.用户登录接口
    UserLoginVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    //    获得脱敏后的用户登录信息
    UserLoginVO getLoginVO(User user);

    /**
     * 获取当前登录用户
     */
    User getLoginUser(HttpServletRequest request);

    //3.用户注销接口：这是用户退出登录的注销，不是删除用户的注销
    boolean userLogout(HttpServletRequest request);

    //    获取脱敏后的用户信息
    UserVO getUserVO(User user);

    //    获取脱敏后的用户列表
    List<UserVO> getUserVOList(List<User> userList);

    //    获取查询条件的对象.这里参数是查询用户请求封装类。啥意思呢，就是查询用户功能的request（请求体那样（不严谨））
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    long addUser(User user);

    User getUserById(long id);

    UserVO getUserVOById(long id);

    boolean deleteUser(DeleteRequest deleteRequest);

    void updateUser(User user);

    Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest);

    List<User> listByIds(Set<Long> userIdSet);

    long saveUser(User userEntity);
}
