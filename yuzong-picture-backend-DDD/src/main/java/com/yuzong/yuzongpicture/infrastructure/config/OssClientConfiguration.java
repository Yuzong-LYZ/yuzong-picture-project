package com.yuzong.yuzongpicture.infrastructure.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

/**
 * OSS 客户端配置类（+初始化+关闭）
 * 采用单例模式，由 Spring 容器管理 OSSClient 生命周期
 */
@Slf4j
@Configuration
public class OssClientConfiguration {

    private OSSClient ossClient;

    /**
     * 创建 OSSClient Bean（单例）
     * 从配置文件读取 OSS 相关参数并初始化客户端
     *
     * @param ossConfig OSS 配置对象（由 Spring 自动注入）
     * @return OSSClient 实例
     */
    @Bean
    public OSSClient ossClient(OssConfig ossConfig) {
        log.info("开始初始化 OSS Client...");

        this.ossClient = OSSClient.newBuilder()
                // 配置访问凭证（AccessKey ID 和 Secret）
                .credentialsProvider(new StaticCredentialsProvider(
                        ossConfig.getAccessKeyId(),
                        ossConfig.getAccessKeySecret()
                ))
                // 配置 OSS 地域
                .region(ossConfig.getEndpoint().replace("https://", "")
                        .replace("http://", "")
                        .replace("oss-", "")
                        .replace(".aliyuncs.com", ""))
                // 配置 Endpoint（OSS 访问域名）
                .endpoint("https://" + ossConfig.getEndpoint())
                .build();

        log.info("OSS Client 初始化成功 - Endpoint: {}, Bucket: {}",
                ossConfig.getEndpoint(), ossConfig.getBucketName());

        return this.ossClient;
    }

    /**
     * 应用关闭时释放 OSSClient 资源
     * 防止资源泄漏
     */
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            try {
                ossClient.close();
                log.info("OSS Client 已关闭");
            } catch (Exception e) {
                log.error("关闭 OSS Client 失败", e);
            }
        }
    }
}
