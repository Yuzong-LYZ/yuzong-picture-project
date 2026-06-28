package com.yuzong.yuzongpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuzong.yuzongpicture.interfaces.dto.picture.*;
import com.yuzong.yuzongpicture.domain.picture.entity.Picture;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.interfaces.vo.picture.PictureVO;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;

/**
 * @author yuzong
 *  针对表【picture(图片)】的数据库操作Service
 * @createDate 2026-05-21 20:05:02
 */
public interface PictureApplicationService extends IService<Picture> {

    /**
     * 创建图片扩图任务
     *
     * @param createPictureOutPaintingTaskRequest 创建图片外画任务请求参数
     * @param loginUser                           登录用户
     * @return 创建图片外画任务结果
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求参数
     * @param loginUser                 登录用户
     */
    @Transactional(rollbackFor = Exception.class)
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 编辑图片
     *
     * @param picture 图片编辑请求参数
     * @param loginUser          登录用户
     */
    void editPicture(Picture picture, User loginUser);

    /**
     * 删除图片
     *
     * @param pictureId 图片id
     * @param loginUser 登录用户
     */
    void deletePicture(long pictureId, User loginUser);


    /**
     * 上传图片 功能实现
     *
     * @param inputSource          前端传入的图片文件
     * @param pictureUploadRequest 图片上传请求参数 就是id
     * @param loginUser            登录用户信息
     * @return 图片上传结果
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 获取查询条件的对象.这里参数是查询用户请求封装类。啥意思呢，就是查询用户功能的request（请求体那样（不严谨））
     *
     * @param pictureQueryRequest 查询用户请求封装类
     * @return 查询条件对象
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 将图片转化为VO类【单个】
     *
     * @param picture 图片实体类
     * @param request 请求对象
     * @return 图片视图对象
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 分页获取图片列表转换为VO类【多个】
     *
     * @param picturePage 分页参数
     * @param request     请求对象
     * @return 图片视图对象列表
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 校验图片参数
     *
     * @param picture 图片实体类
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 图片审核状态设置---填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

    /**
     * 清理图片文件
     *
     * @param oldPicture
     */
    @Async
    // 异步执行，不阻塞主流程
    void clearPictureFile(Picture oldPicture);
}
