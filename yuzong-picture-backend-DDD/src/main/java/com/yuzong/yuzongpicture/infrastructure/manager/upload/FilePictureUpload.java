package com.yuzong.yuzongpicture.infrastructure.manager.upload;

import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class FilePictureUpload extends PictureUploadTemplate {
    /**
     * 文件校验
     */
    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 1. 校验文件大小（2MB）
        long fileSize = multipartFile.getSize();
        final long TWO_MB = 2 * 1024 * 1024L;
        if (fileSize > TWO_MB) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
        }

        // 2. 校验文件后缀
        String fileSuffix = getFileExtension(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp", "gif", "bmp");
        if (!ALLOW_FORMAT_LIST.contains(fileSuffix.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误，仅支持: " + ALLOW_FORMAT_LIST);
        }

    }

    /**
     * 获取原始文件名
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    /**
     * 处理文件转临时文件
     */
    @Override
    protected void processFile(Object inputSource, File tempFile) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        // 将传递进来的文件转为临时文件
        try {
            multipartFile.transferTo(tempFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
