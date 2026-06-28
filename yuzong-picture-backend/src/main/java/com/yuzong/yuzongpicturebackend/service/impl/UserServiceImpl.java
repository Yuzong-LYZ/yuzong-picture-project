package com.yuzong.yuzongpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicturebackend.constant.UserConstant;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.manager.auth.StpKit;
import com.yuzong.yuzongpicturebackend.mapper.UserMapper;
import com.yuzong.yuzongpicturebackend.model.dto.user.UserQueryRequest;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.enums.UserRoleEnum;
import com.yuzong.yuzongpicturebackend.model.vo.UserLoginVO;
import com.yuzong.yuzongpicturebackend.model.vo.UserVO;
import com.yuzong.yuzongpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.yuzong.yuzongpicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author yuzong
 *  针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-05-16 18:29:50
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {


    /**
     * 1. 用户注册 方法
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        /* 这里有必要解释一下
         *  1. StrUtil.hasBlank() 方法会判断字符串是否为空，如果为空则返回 true，否则返回 false。用的是hutool的工具类
         *  2. 我这个异常发生了什么？凭什么自己定义的异常类可以抛出？
         *     2.1 BusinessException 是一个自定义的异常类，定义了错误码和错误信息。
         *         而我定义的ErrorCode类，的PARAMS_ERROR的40000错误码是表示请求参数异常。所以我可以直接将
         *         ErrorCode.PARAMS_ERROR传递进来，表示我40000错误，请求参数异常，后面我可以定义message错误信息
         *     2.3 可是我ErrorCode里面已经定义了message，为什么我调用BusinessException还要传入message？
         *       答：可以完全不填写message，因为BusinessException我们里面不是定义了各种情况吗？
         *           调用BusinessException不填写message也会调用errorcode里面的message，
         *           但是我们在调用BusinessException的时候，
         *           我感觉我可以详细一点描述这个异常的时候，我就可以填写message。
         *     2.2 为什么我可以用自定义异常类抛出异常？只要继承了Throwable
         *        （无论直接继承还是a继承了Throwable，b继承了a，c继承了b，最终你定义的异常类继承了c）那么你定义的异常类，就可以直接抛出。
         *
         * */
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短，需要>=4");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短，需要>=8");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查用户密码是否在数据库中重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
//        去数据库userAccount字段，查询userAccount字段的值是否等于我传递进来的userAccount，如果有，则返回查询到的n条数据，没有则返回n=0
        queryWrapper.eq("userAccount", userAccount);
//        这里才是执行的
        Long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号重复");
        }

        // 3. 密码加密
//        这里使用BCrypt加密
        String BcPassword = getBCryptPassword(userPassword);
        // 4. 插入数据到数据库
        User user = new User();
        user.setUserAccount(userAccount);
//        这里将加密后的密码保存到数据库中
        user.setUserPassword(BcPassword);
        user.setUserName("Momo");// 用户昵称
        user.setUserAvatar("https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/picturesProject/public/0-default-profile-picture/头像momo.png");
        user.setUserRole(UserRoleEnum.USER.getValue());//这里不是用0，1表示，而是用user， admin，表示用户还是管理员
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败,数据库错误");
        }
        return user.getId();
    }

    /**
     * 2. 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @return 脱敏后的用户信息
     */
    @Override
    public UserLoginVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短，需要>=4");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短，需要>=8");
        }

        // 2. 检查用户账号是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("用户登录失败，用户不存在");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号不存在或者密码错误");
        }


        // 3. 验证密码（使用BCrypt的matches方法）（这里应该和密码加密封装在一起的，但是当时忘了，不想改了）
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
// 这里是使用BCrypt的matches方法进行密码验证：将输入进来的密码转化为A部分，然后用A+B部分进行未知算法运算，得出来的C和数据库存放C一致就可以登录了。
        if (!encoder.matches(userPassword, user.getUserPassword())) {
            log.info("用户登录失败，密码错误");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号不存在或者密码错误");
        }

        // 4. 保存用户登录态
//  翻译：将 user 这个完整的 User 对象，以 "user_login"（即 UserConstant.USER_LOGIN_STATE 的值）作为名字，存储到当前用户的 session 中
//       获取这个已登录的信息：User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
//        这是保证能持续登录，以及跳转页面也能登录的功能
        request.getSession().setAttribute(USER_LOGIN_STATE, user);

        // 补充： 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        // 备注：这里使用 Sa-token 记录用户登录态，是为了方便后续的空间鉴权。而不是说一定要2种记录登录态的方式都要写。
        //      严格来说，这里双写一来是为了方便后续的空间鉴权。二来是兼容之前的登录态记录方式。
        //      因为：传统 Session 做细粒度权限（比如判断是不是空间管理员）太麻烦了，于是引入了 Sa-Token。
        // 步骤 A：在 Sa-Token 的 "SPACE"（空间）逻辑分区中，标记该用户 ID 为已登录状态，并下发 Token
        StpKit.SPACE.login(user.getId());

        // 步骤 B：将完整的 User 对象缓存到 Sa-Token 的 Session 中
        // 目的：后续空间鉴权时，可直接从内存中获取 User 对象，避免频繁查询数据库
        StpKit.SPACE.getSession().set(USER_LOGIN_STATE, user);


        log.info("用户登录成功");
        // 5. 返回脱敏后的用户信息
        return getLoginVO(user);
    }

    /**
     * 获取脱敏后的用户信息：把用户信息封装成VO类
     *
     * @param user
     * @return
     */
    @Override
    public UserLoginVO getLoginVO(User user) {
        if (user == null) {
            return null;
        }

        UserLoginVO userLoginVO = new UserLoginVO();
//        将user赋给userLoginVO
        // 添加这行打印，查看 user 对象的 userAvatar 值
        System.out.println("User 的 userAvatar: " + user.getUserAvatar());
        BeanUtil.copyProperties(user, userLoginVO);
        userLoginVO.setUserAvatar(user.getUserAvatar());
        return userLoginVO;
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
//        判断是否已经登录
        User currentUser = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

//        上面如果没抛出异常，证明已经登录，直接返回当前用户信息
        return currentUser;
    }

    /**
     * 3. 用户注销
     *
     * @param request
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        //        判断是否已经登录，如果没有登录，则返回错误
        User currentUser = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
//        移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取脱敏后的用户信息
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
//        将user赋给userVO
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏后的用户列表
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        //  列表不能为空
        if (CollUtil.isEmpty(userList)) {
            // 如果为空的，则返回一个空的列表
            return new ArrayList<>();
        }
//        一句话：将列表转换成VO列表
        return userList.stream()  //1. 创建流：将userList转换成流
                .map(this::getUserVO)  //2. 映射：将userList中的每个元素映射成UserVO对象
                .collect(Collectors.toList());  //3. 收集：将映射后的结果收集到一个新的列表中
    }


    // 获取查询条件的对象：到时候调用这个查询语句，根据这个语句去执行查询
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
//        获取请求参数
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
//        创建查询构造器
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
//        添加查询条件
//        【重要】queryWrapper是个集合，你不需要用add将数据存进去。只需要调用一次queryWrapper的方法，就自动可以添加一个查询条件。
//         所以：换句话说就是，将查询条件封装到了queryWrapper里面。返回的对象，实际上是返回了查询条件的对象
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
//        升序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
//        返回查询条件对象
        return queryWrapper;
    }

    /**
     * 判断是否为管理员
     *
     * @param user
     * @return
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
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
//      1.new一个BCryptPasswordEncoder对象，就可以使用这个对象进行密码加密了，里面有个encode方法，这个方法就是进行密码加密的。
//        12是迭代次数，迭代次数越多，加密越安全，但是加密速度越慢。
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
//      2.进行加密，返回结果用Password接收
        String Password = encoder.encode(userPassword);
//      3.返回加密后的密码
        return Password;
    }


}




