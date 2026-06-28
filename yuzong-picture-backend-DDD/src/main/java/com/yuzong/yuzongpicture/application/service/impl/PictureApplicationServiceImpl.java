package com.yuzong.yuzongpicture.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicture.application.service.PictureApplicationService;
import com.yuzong.yuzongpicture.application.service.SpaceApplicationService;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import com.yuzong.yuzongpicture.domain.picture.service.PictureDomainService;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.AliYunAiApi;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.api.OssManager;
import com.yuzong.yuzongpicture.interfaces.dto.picture.*;
import com.yuzong.yuzongpicture.infrastructure.manager.upload.FilePictureUpload;
import com.yuzong.yuzongpicture.infrastructure.manager.upload.UrlPictureUpload;
import com.yuzong.yuzongpicture.infrastructure.mapper.PictureMapper;
import com.yuzong.yuzongpicture.domain.picture.entity.Picture;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.interfaces.vo.picture.PictureVO;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yuzong
 *  针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-05-21 20:05:02
 */
@Slf4j
@Service
public class PictureApplicationServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureApplicationService {

    @Resource
    private PictureDomainService pictureDomainService;
    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;
    @Resource
    private SpaceApplicationService spaceApplicationService;
    @Resource
    private OssManager ossManager;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private UserApplicationService userApplicationService;

    /**
     * 创建图片扩图任务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        return pictureDomainService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求参数
     * @param loginUser                 登录用户
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        pictureDomainService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
    }



    /**
     * 【新增】编辑图片
     *
     * @param picture 图片编辑请求参数
     * @param loginUser          登录用户信息
     */
    @Override
    public void editPicture(Picture picture, User loginUser) {
        pictureDomainService.editPicture(picture, loginUser);
    }

    /**
     * 【新增】删除图片
     *
     * @param pictureId 图片id
     * @param loginUser 登录用户信息
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        pictureDomainService.deletePicture(pictureId, loginUser);
    }



    /**
     * 【新增】异步清理图片文件（用于更新图片时删除旧图）
     *
     * @param oldPicture 旧图片对象
     */
    @Async  // 异步执行，不阻塞主流程
    @Override
    public void clearPictureFile(Picture oldPicture) {
        pictureDomainService.clearPictureFile(oldPicture);
    }

    /**
     * --------1. 上传图片 功能实现【新增或更新】
     *
     * @param inputSource          输入源，
     * @param pictureUploadRequest 图片上传请求参数 就是id
     * @param loginUser            登录用户信息
     * @return 图片上传结果
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        return pictureDomainService.uploadPicture(inputSource, pictureUploadRequest, loginUser);
    }

    /**
     * 上传图片（批量）
     *
     * @param pictureUploadByBatchRequest 图片上传（批量）请求参数
     * @param loginUser                   登录用户
     * @return
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
      return pictureDomainService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
    }

    /**
     * 构建图片查询条件（QueryWrapper）
     * <p>
     * 【方法用途】
     * 将前端传入的查询参数转换为 MyBatis-Plus 的 QueryWrapper 对象
     * 用于动态构建 SQL 查询条件，支持多条件组合查询
     * <p>
     * 【执行流程】
     * 1. 空值检查：如果请求对象为 null，返回空条件（查询所有）
     * 2. 提取参数：从 PictureQueryRequest 中提取所有查询字段
     * 3. 构建条件：根据字段是否有值，动态拼接查询条件
     * 4. 返回结果：返回构建好的 QueryWrapper 对象
     * <p>
     * 【查询类型说明】
     * - 精确查询（eq）：id、userId、category、picWidth、picHeight、picSize、picScale
     * - 模糊查询（like）：name、introduction、picFormat、tags
     * - 全文搜索（and...or）：searchText 同时搜索 name 和 introduction
     * - 排序（orderBy）：根据 sortField 和 sortOrder 动态排序
     *
     * @param pictureQueryRequest 查询请求参数（包含所有可能的查询条件）
     * @return QueryWrapper<Picture> 构建好的查询条件对象
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        return pictureDomainService.getQueryWrapper(pictureQueryRequest);
    }

    /**
     * 获取图片封装类【单个】
     * <p>
     * 【方法用途】
     * 将数据库中的图片信息封装成 PictureVO 对象，并返回给前端
     * - picture：数据库中的图片信息
     * - request：HttpServletRequest 对象，用于获取用户信息
     *
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userApplicationService.getUserById(userId);
            UserVO userVO = userApplicationService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }


    /**
     * 获取图片封装类【分页：多个】
     * <p>
     * 【方法用途】
     * 将数据库中的图片信息封装成 PictureVO 列表，并返回给前端
     * - picturePage：数据库中的图片信息列表
     * - request：HttpServletRequest 对象，用于获取用户信息
     *
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 像这种调用了应用服务，不往下沉。上面的可以继续往下沉到实体类。但是我不这么做
        Map<Long, List<User>> userIdUserListMap = userApplicationService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userApplicationService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 校验图片
     *
     * @param picture 图片
     */
    @Override
    public void validPicture(Picture picture) {
        if(picture == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        picture.validPicture();
    }


    /**
     * 图片审核
     * 备注：是传递审核结果过来。然后直接就将结果赋给对应id图片的数据库。不需要我们将什么改成什么
     * <p>
     * 【方法用途】
     * 图片审核，包括 id、reviewStatus、reviewMessage、reviewerId 等字段
     * 1. 只有管理员可以执行审核操作（Controller 层通过 @AuthCheck 校验）
     * 2. 审核状态只能设置为"通过"（1）或"拒绝"（2），不能设置为"待审核"（0）
     * 3. 同一张图片不能重复审核（防止状态重复更新）
     * 4. 审核时需要记录审核人 ID 和审核时间
     * - pictureReviewRequest：图片审核参数对象
     * - loginUser：登录用户对象
     *
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        pictureDomainService.doPictureReview(pictureReviewRequest, loginUser);
    }

    /**
     * 设置审核状态（初始状态）--填充审核参数
     * <p>
     * 【方法用途】
     * 填充审核参数，包括 reviewStatus、reviewerId、reviewMessage、reviewTime 等字段
     * - picture：图片参数对象
     * - loginUser：登录用户对象
     *
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        pictureDomainService.fillReviewParams(picture, loginUser);
    }


}




