package com.yuzong.yuzongpicturebackend.controller;

import com.yuzong.yuzongpicturebackend.common.BaseResponse;
import com.yuzong.yuzongpicturebackend.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : Yuzong
 * @date 2026/5/12 00:10
 *
 **/
@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }
}


