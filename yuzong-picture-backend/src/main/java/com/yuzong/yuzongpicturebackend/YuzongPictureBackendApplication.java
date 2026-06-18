package com.yuzong.yuzongpicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.yuzong.yuzongpicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
public class YuzongPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuzongPictureBackendApplication.class, args);
    }

}
