package com.yuzong.yuzongpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * @author : Yuzong
 * @date 2026/6/3 19:33
 *
 **/
@Component
@Slf4j
public class UrlPictureUpload extends PictureUploadTemplate {
    // 图片校验
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");

        try {
            // 1. 验证 URL 格式
            new URL(fileUrl); // 验证是否是合法的 URL
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }

        // 2. 校验 URL 协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");

        // 3. 发送 HEAD 请求以验证文件是否存在
        HttpResponse response = null;
        try {
//            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            response = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    .timeout(5000) // 【新增】设置 5 秒超时，防止卡死
                    .execute();
            // 未正常返回，无需执行其他判断
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 4. 校验文件类型
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 5. 校验文件大小
            String contentLengthStr = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long TWO_MB = 2 * 1024 * 1024L; // 限制文件大小为 2MB
                    ThrowUtils.throwIf(contentLength > TWO_MB, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }

    }

    // 获取原始文件名
//    @Override
//    protected String getOriginFilename(Object inputSource) {
//        String fileUrl = (String) inputSource;
//        return getFileExtension(fileUrl);
//    }
    // 获取原始文件名
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 从URL中提取文件名，例如 "https://example.com/photo.jpg?param=123" -> "photo.jpg"
        int lastSlashIndex = fileUrl.lastIndexOf("/");
        if (lastSlashIndex != -1 && lastSlashIndex < fileUrl.length() - 1) {
            String fileName = fileUrl.substring(lastSlashIndex + 1);
            // 移除URL参数（?后面的部分）
            int questionMarkIndex = fileName.indexOf("?");
            if (questionMarkIndex != -1) {
                fileName = fileName.substring(0, questionMarkIndex);
            }
            // 如果文件名中没有扩展名，添加默认扩展名
            if (!fileName.contains(".")) {
                fileName = fileName + ".jpg";
            }
            return fileName;
        }
        // 如果没有找到文件名，使用默认名称
        return "image.jpg";
    }


    // 处理文件
//    @Override
//    protected void processFile(Object inputSource, File tempFile) {
//        String fileUrl = (String) inputSource;
//        HttpUtil.downloadFile(fileUrl, tempFile);
//    }
    // 处理文件：下载图片到临时文件
    @Override
    protected void processFile(Object inputSource, File tempFile) {
        String fileUrl = (String) inputSource;

        // 设置连接超时为 10 秒，读取超时为 20 秒
        int connectTimeout = 10000;
        int readTimeout = 20000;

        try {
            // 使用 HttpRequest 构建请求并设置超时
            HttpResponse response = HttpUtil.createRequest(Method.GET, fileUrl)
                    // 关键：设置超时时间
                    .setConnectionTimeout(connectTimeout) // 设置连接超时 (10秒)
                    .setReadTimeout(readTimeout)          // 设置读取超时 (20秒)
                    .execute();

            // 检查响应状态
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载图片失败，HTTP 状态码: " + response.getStatus());
            }

            // 将下载的字节流写入临时文件
            byte[] bodyBytes = response.bodyBytes();
            FileUtil.writeBytes(bodyBytes, tempFile);

        } catch (Exception e) {
            log.error("下载图片失败，URL: {}", fileUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片下载超时或失败，请检查图片地址是否有效");
        }
    }

}
