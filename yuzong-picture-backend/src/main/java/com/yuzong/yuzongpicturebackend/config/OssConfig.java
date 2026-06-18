package com.yuzong.yuzongpicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : Yuzong
 * oss存储对象配置:读取配置文件中的阿里云OSS相关属性。把配置文件（yml文件）中的文字信息转换成Java对象
 * @date 2026/5/20 21:37
 **/
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
@Data
public class OssConfig {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

}


