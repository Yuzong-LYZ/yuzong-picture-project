package com.yuzong.yuzongpicture.interfaces.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yuzong.yuzongpicture.infrastructure.annotation.AuthCheck;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.AliYunAiApi;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yuzong.yuzongpicture.infrastructure.api.imagesearch.ImageSearchApiFacade;
import com.yuzong.yuzongpicture.infrastructure.api.imagesearch.model.ImageSearchResult;
import com.yuzong.yuzongpicture.infrastructure.common.BaseResponse;
import com.yuzong.yuzongpicture.infrastructure.common.DeleteRequest;
import com.yuzong.yuzongpicture.infrastructure.common.ResultUtils;
import com.yuzong.yuzongpicture.domain.user.constant.UserConstant;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import com.yuzong.yuzongpicture.interfaces.assembler.PictureAssembler;
import com.yuzong.yuzongpicture.interfaces.dto.picture.*;
import com.yuzong.yuzongpicture.shared.auth.SpaceUserAuthManager;
import com.yuzong.yuzongpicture.shared.auth.StpKit;
import com.yuzong.yuzongpicture.shared.auth.annotation.SaSpaceCheckPermission;
import com.yuzong.yuzongpicture.shared.auth.model.SpaceUserPermissionConstant;
import com.yuzong.yuzongpicture.domain.picture.entity.Picture;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.domain.picture.valueobject.PictureReviewStatusEnum;
import com.yuzong.yuzongpicture.interfaces.vo.picture.PictureTagCategory;
import com.yuzong.yuzongpicture.interfaces.vo.picture.PictureVO;
import com.yuzong.yuzongpicture.application.service.PictureApplicationService;
import com.yuzong.yuzongpicture.application.service.SpaceApplicationService;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 图片接口
 * @author yuzong
 */
@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024) //初始化缓存空间
                    .maximumSize(10000L) //最大10000条缓存
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();
    @Resource
    private UserApplicationService userApplicationService;
    @Resource
    private PictureApplicationService pictureApplicationService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SpaceApplicationService spaceApplicationService;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 批量编辑图片
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        // 2. 获取当前登录用户
        User loginUser = userApplicationService.getLoginUser(request);
        // 3. 调用编辑方法
        pictureApplicationService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建 AI 扩图任务
     * <p>
     * 【功能说明】
     * 用户选择一张已上传的图片，指定扩展参数（如扩展比例、方向），系统调用阿里云 AI 服务创建扩图任务
     * 这是一个异步操作，会立即返回任务 ID，但扩图结果需要稍后通过查询接口获取
     * 【工作流程】
     * 1. 前端传递图片 ID 和扩图参数
     * 2. 后端验证用户权限（确保用户有权操作该图片）
     * 3. 根据图片 ID 从数据库查询图片 URL
     * 4. 调用阿里云 AI API 创建扩图任务
     * 5. 返回任务 ID 给前端
     * 6. 前端轮询查询任务状态，直到完成
     */
    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        // 1. 参数校验，请求对象或图片id为空
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 获取当前登录用户
        User loginUser = userApplicationService.getLoginUser(request);
        // 3. 调用创建方法
        CreateOutPaintingTaskResponse response = pictureApplicationService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        // 4. 返回结果
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     * 【功能说明】
     * 根据任务 ID 查询扩图任务的执行状态和结果
     * 前端需要轮询调用此接口，直到任务状态变为 "SUCCEEDED"（成功）或 "FAILED"（失败）
     *
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        // 1. 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        // 2. 调用查询方法
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        // 3. 返回结果
        return ResultUtils.success(task);
    }

    /**
     * 以图搜图功能
     * 【工作流程】
     * 1. 前端传递一个已上传图片的 ID（pictureId）
     * 2. 后端根据 ID 从数据库查询该图片的 URL
     * 3. 调用第三方图片搜索 API（如百度识图、Google Lens 等）
     * 4. 将搜索结果返回给前端展示
     * 【参数说明】
     *
     * @param searchPictureByPictureRequest 请求参数对象，包含 pictureId 字段
     *                                      - pictureId: 已上传图片的 ID（数据库中存在的图片）
     *                                      【返回值】
     * @return BaseResponse<List<ImageSearchResult>> 返回相似图片列表
     * - ImageSearchResult: 包含相似图片的信息（URL、标题、来源网站等）
     */
    @PostMapping("/search/picture")  // POST 请求，路径：/picture/search/picture
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(
            @RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        // ============ 第一步：参数校验 ============
        // 检查请求对象是否为空
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        // 从请求中提取图片 ID
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        // 检查图片 ID 是否有效（不能为空，必须大于 0）
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        // ============ 第二步：查询原图信息 ============
        // 根据图片 ID 从数据库查询图片记录
        Picture oldPicture = pictureApplicationService.getById(pictureId);
        // 如果数据库中找不到该图片，抛出异常
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // ============ 第三步：调用第三方图片搜索 API ============
        // 获取原图的 URL（OSS 上的完整地址）
        String imageUrl = oldPicture.getUrl();
        // 调用图片搜索工具类，传入图片 URL
        // 这个工具类内部会：
        // 1. 将图片上传到百度识图api当中。最后返回图片列表
        // 2. 获取搜索结果
        // 3. 解析结果并封装成 ImageSearchResult 对象列表
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(imageUrl);

        // ============ 第四步：返回结果 ============
        // 将相似图片列表包装成统一响应格式返回给前端
        return ResultUtils.success(resultList);
    }

    /**
     * 1. 上传图片【新增或更新】（管理员用）
     *
     * @param multipartFile        图片文件
     * @param pictureUploadRequest 图片上传请求参数
     * @param
     * @return 图片上传结果
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, HttpServletRequest request) {
        // 获取当前登录用户
        User loginUser = userApplicationService.getLoginUser(request);
        // 调用pictureService上传图片
        PictureVO pictureVO = pictureApplicationService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);

        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userApplicationService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureApplicationService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 2. 删除图片(用户和管理员都能用)
     *
     * @param deleteRequest 删除请求参数:id
     * @param
     * @return 删除结果
     */
    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
//        判断参数是否正常
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "删除请求参数错误");
        }
//        获取当前登录用户
        User loginUser = userApplicationService.getLoginUser(request);
//        如果没抛出异常，就存在。要验证是否本人或者管理员。
        pictureApplicationService.deletePicture(deleteRequest.getId(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 3. 更新图片信息(管理员用)
     *
     * @param pictureUpdateRequest 更新请求参数:图片的：id，名称，简介，分类，标签
     * @param
     * @return 更新结果
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
//        判断参数
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = PictureAssembler.toPictureEntity(pictureUpdateRequest);
        // 数据校验
        pictureApplicationService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureApplicationService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 填充审核参数
        User loginUser = userApplicationService.getLoginUser(request);
        pictureApplicationService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureApplicationService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 4. 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureApplicationService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 5. 根据 id 获取图片（封装类）（用户版）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 查询数据库
        Picture picture = pictureApplicationService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        Space space = null; // 补充2；
        if (spaceId != null) {
//            补充：改为这样：
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            // 获取当前登录用户
//            User loginUser = userApplicationService.getLoginUser(request);
            // 已经改为注解鉴权，所以下面这个注释掉，这个方法和接口也会注释
//            pictureApplicationService.checkPictureAuth(loginUser, picture);
            // 补充2：获取空间信息
            space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 补充2：获取当前登录用户,获取权限列表,将picture转VO类，将权限列表给VO类
        User loginUser = userApplicationService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        PictureVO pictureVO = pictureApplicationService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);

        // 获取封装类
        return ResultUtils.success(pictureVO);
    }

    /**
     * 6. 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
//        页码，页面大小
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureApplicationService.page(new Page<>(current, size),
                pictureApplicationService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 7. 分页获取图片列表（封装类）（用户用）
     * 从数据库中获取图片链接，展示给前端
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        //普通用户默认只能查看已过审的数据（图片）
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        // 公开图库
        if (spaceId == null) {
            // 普通用户默认只能查看已过审的公开数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            // 补充：改为注解鉴权
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
//            User loginUser = userApplicationService.getLoginUser(request);
//            Space space = spaceApplicationService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
        }

        // 查询数据库
        Page<Picture> picturePage = pictureApplicationService.page(new Page<>(current, size),
                pictureApplicationService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureApplicationService.getPictureVOPage(picturePage, request));
    }

    /**
     * 7.1 分页获取图片列表------【redis版、本地缓存版、多级缓存版】
     * 从数据库中获取图片链接，展示给前端
     */
    @Deprecated  // 废弃该接口
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 1. 构建缓存 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
//        String redisKey = "yuzongpicture:listPictureVOByPage:" + hashKey; // redis缓存
//        String cacheKey = "istPictureVOByPage:" + hashKey; // 本地缓存
        String cacheKey = "istPictureVOByPage:" + hashKey; // 多级缓存之 本地缓存

        // 2. 从 【某某】缓存中查询
//        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue(); // redis缓存
//        String cachedValue = valueOps.get(redisKey); // redis缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey); // 本地缓存
//        if (cachedValue != null) {  //本地缓存
//            // 如果缓存命中，返回结果
//            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
//            return ResultUtils.success(cachedPage);
//        }
        if (cachedValue != null) {  //多级缓存之 先从本地缓存查询
            // 如果缓存命中，返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue(); // 多级缓存之 redis缓存
        cachedValue = valueOps.get(cacheKey); // 多级缓存之 从redis缓存查询
        if (cachedValue != null) {  //先从多级缓存中查询
            // 如果缓存命中，更新本地缓存，返回结果
            LOCAL_CACHE.put(cacheKey, cachedValue);
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }


        // 3. 查询数据库
        Page<Picture> picturePage = pictureApplicationService.page(new Page<>(current, size),
                pictureApplicationService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureApplicationService.getPictureVOPage(picturePage, request);

        //4. 更新缓存
        // 存入 Redis 缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 5 - 10 分钟随机过期，防止雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
//        valueOps.set(redisKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);// redis缓存
//        LOCAL_CACHE.put(cacheKey, cacheValue); // 本地缓存
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS); //多级缓存之 更新redis缓存
        LOCAL_CACHE.put(cacheKey, cacheValue); // 多级缓存之 更新本地缓存

        // 返回结果
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 8. 编辑图片（给用户使用。和更新图片基本一致）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userApplicationService.getLoginUser(request);
        Picture pictureEntity = PictureAssembler.toPictureEntity(pictureEditRequest);
        pictureApplicationService.editPicture(pictureEntity, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 9. 获取标签和分类
     * 支持用户根据标签和分类搜索图片。列举一些标签和分类，供用户选择。
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
//        将我们预设的，存入VO类当中
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
//        返回给前端
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 10. 图片审核（仅管理员可用）
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        //参数判断
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前用户
        User loginUser = userApplicationService.getLoginUser(request);
        //
        pictureApplicationService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 11. 批量上传图片（仅管理员可用）
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        //参数校验
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        //获取当前用户
        User loginUser = userApplicationService.getLoginUser(request);
        //执行批量上传
        int uploadCount = pictureApplicationService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        //返回上传数量
        return ResultUtils.success(uploadCount);
    }


}
