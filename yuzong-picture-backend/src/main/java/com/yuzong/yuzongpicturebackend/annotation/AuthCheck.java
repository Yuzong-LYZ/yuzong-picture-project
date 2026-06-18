package com.yuzong.yuzongpicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)// 告诉Java：这个注解可以用在方法上
@Retention(RetentionPolicy.RUNTIME)// 告诉Java：运行时保留，否则只有在编译时保留
public @interface AuthCheck {
    /**
     * 必须有该角色
     *
     */
    String mustRole() default "";
}
