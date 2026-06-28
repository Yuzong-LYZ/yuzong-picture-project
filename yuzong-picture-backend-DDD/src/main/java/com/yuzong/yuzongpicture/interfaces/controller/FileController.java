package com.yuzong.yuzongpicture.interfaces.controller;

import com.yuzong.yuzongpicture.infrastructure.common.BaseResponse;
import com.yuzong.yuzongpicture.infrastructure.common.ResultUtils;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.api.OssManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

/**
 * @author : Yuzong
 * @date 2026/5/20 23:49
 *
 **/
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {
    @Resource
    private OssManager ossManager;

    /**
     * 测试上传文件
     */
    @PostMapping("/test/upload")
    public BaseResponse<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 调用上传文件方法上传到 picturesProject/ 文件夹
            String fileUrl = ossManager.uploadFile(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    "picturesProject/"
            );

            return ResultUtils.success(fileUrl);
        } catch (Exception e) {
            log.error("上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

}
