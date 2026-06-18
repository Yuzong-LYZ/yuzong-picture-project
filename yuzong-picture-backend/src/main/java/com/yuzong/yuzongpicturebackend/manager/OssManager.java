package com.yuzong.yuzongpicturebackend.manager;


import cn.hutool.core.util.StrUtil;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.yuzong.yuzongpicturebackend.config.OssConfig;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * OSS基本方法类【通用】
 */
@Slf4j
@Component
public class OssManager {

    @Resource
    private OSSClient ossClient;

    @Resource
    private OssConfig ossConfig;

    /**
     * ------上传文件方法【通用的上传文件的方法】
     * 上传文件到 OSS 指定文件夹（简化版）
     */
    public String uploadFile(InputStream inputStream, String fileName, String folderPath) {
        try {
            // 这里拼接一下文件路径：我们想要放到bucket的某文件夹（传递进来的记得是xxx/格式） + 文件名
            String objectKey = folderPath + fileName;
            System.out.println("测试一下这是啥：" + objectKey);
            System.out.println(fileName);
            System.out.println(folderPath);


            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossConfig.getBucketName())  //这里是我们的存储桶也就是bucket名，他就会存到这个bucket里
                    .key(objectKey)  // 这里就是我们上传的文件路径，包括文件夹和文件名
                    .body(BinaryData.fromStream(inputStream))  // 这里就是我们上传的文件
                    .build();  // 创建请求

            // 执行上传操作（这里调用的是oss官方的方法）
            PutObjectResult putObjectResult = ossClient.putObject(request);
            // 这里返回的是文件的URL，包括文件夹和文件名（将返回值复制到浏览器可以直接下载）
            return "https://" + ossConfig.getBucketName() + "."
                    + ossConfig.getEndpoint() + "/" + objectKey;
        } catch (Exception e) {
            throw new RuntimeException("上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传对象（附带图片信息）- 基础方法--【通用的上传文件的方法】
     *
     * @param key  唯一键（文件夹路径 + 文件名，如 "pictures/xxx.jpg"）
     * @param file 文件对象
     * @return PutObjectResult OSS上传结果
     */
    public PutObjectResult putPictureObject(String key, File file) {
//        这里就new一个输入流对象
        try (InputStream inputStream = new java.io.FileInputStream(String.valueOf(file))) {

            // 这里补充个将文件的拓展名改成webp
            String webpKey = key.substring(0, key.lastIndexOf(".")) + ".webp";


            PutObjectRequest request = PutObjectRequest.newBuilder()
//                    这里是桶名，不应该写死，但是懒。
                    .bucket(ossConfig.getBucketName())
                    .key(key)//上传到桶的 路径+图片名字
                    .body(BinaryData.fromStream(inputStream)) //上传的文件
                    .build();
//          执行上传操作（这里调用的是oss官方的方法）
            return ossClient.putObject(request);
        } catch (Exception e) {
            log.error("图片上传到OSS失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        }
    }

    /**
     * 【新增】删除 OSS 文件
     *
     * @param fileUrl 文件完整URL，如：https://bucket.endpoint.com/pictures/xxx.jpg
     */
    public void deleteFile(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return;
        }

        try {
            // 【关键】从 URL 中提取 key（相对路径）
            // URL: https://yuzong-picture-1318290657.oss-cn-nanjing.aliyuncs.com/pictures/xxx.jpg
            // Key: pictures/xxx.jpg
            String key = extractKeyFromUrl(fileUrl);

            log.info("开始删除 OSS 文件: {}, key: {}", fileUrl, key);

            // 【OSS SDK V2】构建删除请求
            DeleteObjectRequest request = DeleteObjectRequest.newBuilder()
                    .bucket(ossConfig.getBucketName())
                    .key(key)
                    .build();

            // 执行删除操作
            ossClient.deleteObject(request);

            log.info("OSS 文件删除成功: {}", key);
        } catch (Exception e) {
            log.error("OSS 文件删除失败: {}", fileUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 【辅助方法】从 URL 中提取 key（相对路径）
     *
     * @param fileUrl 完整URL
     * @return key（相对路径）
     */
    private String extractKeyFromUrl(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            String path = url.getPath(); // 获取 /pictures/xxx.jpg

            // 去掉开头的 "/"
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            return path;
        } catch (Exception e) {
            log.error("从 URL 提取 key 失败: {}", fileUrl, e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的文件URL");
        }
    }


    //

}
