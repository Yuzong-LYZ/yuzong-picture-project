package com.yuzong.yuzongpicture.shared.auth.annotation;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.annotation.AliasFor;
import com.yuzong.yuzongpicture.shared.auth.StpKit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 空间权限认证：必须具有指定权限才能进入该方法
 * <p> 可标注在函数、类上（效果等同于标注在此类的所有方法上）
 */
//  这些注解是在定义：我们这个注解的底层行为、生命周期、注解位置
@SaCheckPermission(type = StpKit.SPACE_TYPE) // 指定权限认证的验证类型，当我们@SaCheckPermission标注在方法上时，会默认使用该值
@Retention(RetentionPolicy.RUNTIME)  // 告诉Java 编译器：“这个注解信息要保留到运行时。”
@Target({ElementType.METHOD, ElementType.TYPE})  // 贴在 Controller 的某个方法上（只校验这一个接口）
public @interface SaSpaceCheckPermission {
//  下面这些注解是在定义：我们这个注解的参数
    /**
     * 权限码：需要校验的权限码
     *
     * @return 需要校验的权限码
     */
    @AliasFor(annotation = SaCheckPermission.class)
    String[] value() default {};

    /**
     * 验证模式：AND | OR，默认AND
     *
     * @return 验证模式
     */
    @AliasFor(annotation = SaCheckPermission.class)
    SaMode mode() default SaMode.AND;

    /**
     * 备选角色：在权限校验不通过时的次要选择，两者只要其一校验成功即可通过校验
     *
     * <p>
     * 例1：@SaCheckPermission(value="user-add", orRole="admin")，
     * 代表本次请求只要具有 user-add权限 或 admin角色 其一即可通过校验。
     * </p>
     *
     * <p>
     * 例2： orRole = {"admin", "manager", "staff"}，具有三个角色其一即可。 <br>
     * 例3： orRole = {"admin, manager, staff"}，必须三个角色同时具备。
     * </p>
     *
     * @return /
     */
    @AliasFor(annotation = SaCheckPermission.class)
    String[] orRole() default {};

}