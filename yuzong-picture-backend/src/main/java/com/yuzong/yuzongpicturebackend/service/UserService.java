package com.yuzong.yuzongpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicturebackend.model.dto.user.UserQueryRequest;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.vo.UserLoginVO;
import com.yuzong.yuzongpicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author yuzong
 *  针对表【user(用户)】的数据库操作Service
 * @createDate 2026-05-16 18:29:50
 */
public interface UserService extends IService<User> {
//    密码加密接口  ：密码用 BCrypt加密

    String getBCryptPassword(String userPassword);

    //    1.用户注册接口
    long userRegister(String userAccount, String userPassword, String checkPassword);

    //    2.用户登录接口
    UserLoginVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

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

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

}
