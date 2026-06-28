package com.yuzong.yuzongpicture.infrastructure.aop;

import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import com.yuzong.yuzongpicture.infrastructure.annotation.AuthCheck;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.domain.user.valueobject.UserRoleEnum;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author : Yuzong
 * @date 2026/5/19 13:21
 *
 **/
@Aspect  // 切面
@Component  // 组件
public class AuthInterceptor {
    //    既然是权限校验，那么肯定要注入UserService
    @Resource
    private UserApplicationService userApplicationService;


    /**
     * @param: joinPoint  切入点：被拦截的方法信息（可以获取方法名、参数等）
     * @param: authCheck  权限校验注解：你注解的内容（可以获取 mustRole 的值）
     **/
    /*
        @Around("@annotation(authCheck)")
        - 拦截所有带有 @AuthCheck 注解的方法
        - 把注解对象注入到 authCheck 参数中

     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        //1. 获取mustRole的mustRole的值 如：@AuthCheck(mustRole = "admin")，这里获取到 "admin"【注解的】
        String mustRole = authCheck.mustRole();
        //2. 获取当前这个请求的所有属性（获取当前http请求）【固定搭配】
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
//        3. 获取当前登录用户【当前的】
        User loginUser = userApplicationService.getLoginUser(request);
//        4. 将mustRole的值转为枚举【UserRoleEnum】【注解的】；翻译：从注解中比如拿到的是mustRole，就去枚举类的value找
//           如果找不到，就返回null，证明注解并没有配置mustRole的值，证明他是不需要权限校验的。
//           如果找得到，比如从过年注解拿到的是admin，枚举类当中admin是对应："ADMIN"。所以就会返回"ADMIN"或者UserRoleEnum.ADMIN
        UserRoleEnum mustUserRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
//        5. 现在拿到的是mustRole对应的枚举类的值，如果是null，证明我们没有配置mustRole的值，那么就直接通过。【注解的】
        if (mustUserRoleEnum == null) {
            return joinPoint.proceed();
        }
//        6. 这里拿到的是当前用户的角色的权限的枚举类型【当前的】
        UserRoleEnum loginUserRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
//        7. 每个用户我们都给了权限，如果他连user权限都没有，那么肯定有问题（可能是没登录）。【当前的】
        if (loginUserRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
//        8.如果注解的这个方法是需要管理员的权限，但是当前登录用户不是管理员，那么就抛出异常【注解的】
        if (UserRoleEnum.ADMIN.equals(mustUserRoleEnum) && !UserRoleEnum.ADMIN.equals(loginUserRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
//        通过验证，放行
        return joinPoint.proceed();

    }

}
