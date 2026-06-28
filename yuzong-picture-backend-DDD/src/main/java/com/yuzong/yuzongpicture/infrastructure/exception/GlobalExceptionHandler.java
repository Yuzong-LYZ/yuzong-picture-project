package com.yuzong.yuzongpicture.infrastructure.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.yuzong.yuzongpicture.infrastructure.common.BaseResponse;
import com.yuzong.yuzongpicture.infrastructure.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * 作用：统一拦截和处理整个项目中抛出的异常，避免将丑陋的异常堆栈直接暴露给前端，防止服务器的报错信息返回前端。
 * 并返回统一格式的错误响应（BaseResponse）。
 *
 * 为啥非要写这个？前面已经写了那么多异常处理方法，我们不是能直接用吗？上面的作用说的还是太抽象了，压根就看不懂啊。
 * 答：1. 首先，后端抛出异常后只是把错误抛出，并没有将错误封装起来。没有封装起来，前端就无法获取错误信息。
 *    2. 这么写看上去虽然很像多此一举，实际上很重要。可以直接理解为：异常版本的VO转换器。
 *    3. 最重要的是：后端抛出的异常，终究是要返回给前端的，也不是仅仅只是后端看，无论如何都是需要写这个全局异常处理器的。
 *    4. 而且，我们这里都直接将异常结果序列化为json数据来。也更方便前端调用。
 *    5. 总结：理解为：异常版本的 VO类，VO转换器。
 */
@RestControllerAdvice // 核心注解1：标识这是一个全局异常处理类。它相当于 @ControllerAdvice + @ResponseBody，意味着拦截异常后，返回的对象会自动序列化为 JSON 格式。
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 1. 处理“未登录”异常
     * 触发场景：用户未登录、Token 过期或无效时，访问了需要登录的接口（常见于 Sa-Token 或自定义拦截器抛出的异常）。
     */
    @ExceptionHandler(NotLoginException.class) // 指定该方法只捕获 NotLoginException 及其子类
    public BaseResponse<?> notLoginException(NotLoginException e) {
        // 记录 ERROR 级别日志，"NotLoginException" 是提示语，e 会打印完整的异常堆栈
        log.error("NotLoginException", e);
        // 返回统一格式的错误结果，使用预定义的“未登录”错误码和异常自带的提示信息
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }

    /**
     * 2. 处理“无权限”异常
     * 触发场景：用户已登录，但角色/权限不足，试图访问越权接口时抛出。
     */
    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
        log.error("NotPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }

    /**
     * 3. 处理“自定义业务”异常
     * 触发场景：代码中主动抛出的业务逻辑错误。
     * 这是开发中最常触发的异常。
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        // 记录业务异常日志。注意：这里用 error 也可以，但有些团队习惯用 warn，因为这是预期内的业务错误，不是系统崩溃。
        log.error("BusinessException", e);
        // 业务异常通常自带特定的错误码（e.getCode()），直接透传给前端
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 4. 处理“运行时”异常（兜底异常）
     * 触发场景：代码中未预料到的系统级错误（如：NullPointerException 空指针、数据库连接失败、数组越界等）。
     * 它是所有非受检异常的父类，放在最后作为“兜底”。
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        // 系统级错误必须记录 ERROR 日志，方便后续排查 Bug
        log.error("RuntimeException", e);
        // 为了安全，**绝对不能**把系统异常的 e.getMessage() 直接返回给前端（可能会暴露数据库表名、SQL语句或服务器路径），
        // 所以这里统一返回固定的“系统错误”提示。
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}



