package com.yuzong.yuzongpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicturebackend.api.aliyunai.AliYunAiApi;
import com.yuzong.yuzongpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yuzong.yuzongpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.exception.ThrowUtils;
import com.yuzong.yuzongpicturebackend.manager.OssManager;
import com.yuzong.yuzongpicturebackend.manager.upload.FilePictureUpload;
import com.yuzong.yuzongpicturebackend.manager.upload.PictureUploadTemplate;
import com.yuzong.yuzongpicturebackend.manager.upload.UrlPictureUpload;
import com.yuzong.yuzongpicturebackend.mapper.PictureMapper;
import com.yuzong.yuzongpicturebackend.model.dto.file.UploadPictureResult;
import com.yuzong.yuzongpicturebackend.model.dto.picture.*;
import com.yuzong.yuzongpicturebackend.model.entity.Picture;
import com.yuzong.yuzongpicturebackend.model.entity.Space;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.enums.PictureReviewStatusEnum;
import com.yuzong.yuzongpicturebackend.model.vo.PictureVO;
import com.yuzong.yuzongpicturebackend.model.vo.UserVO;
import com.yuzong.yuzongpicturebackend.service.PictureService;
import com.yuzong.yuzongpicturebackend.service.SpaceService;
import com.yuzong.yuzongpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yuzong
 *  针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-05-21 20:05:02
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    //    @Resource
//    private PictureUploadTemplate pictureUploadTemplate;
    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;
    @Resource
    private SpaceService spaceService;
    @Resource
    private OssManager ossManager;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Resource
    private UserService userService;

    /**
     * 创建图片扩图任务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));

        //todo：ddd待补充： 校验图片尺寸（阿里云扩图 API 要求：单边长度 [512, 4096] 像素）
        Integer picWidth = picture.getPicWidth();
        Integer picHeight = picture.getPicHeight();
        if (picWidth != null && picHeight != null) {
            int minSide = Math.min(picWidth, picHeight);
            int maxSide = Math.max(picWidth, picHeight);
            if (minSide < 512) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "图片尺寸过小，扩图要求每条边至少 512px，当前短边为 " + minSide + "px，请上传更大的图片");
            }
            if (maxSide > 4096) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "图片尺寸过大，扩图要求每条边不超过 4096px，当前长边为 " + maxSide + "px");
            }
        }

        // 权限校验
        // 已经改为注解鉴权，所以下面这个注释掉，这个方法和接口也会注释
//        checkPictureAuth(loginUser, picture);
        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest(); // 创建扩图任务请求
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input(); // 需要扩图的图片url
        input.setImageUrl(picture.getUrl());  // 把从数据库中查出来的url赋给input
        taskRequest.setInput(input);  //  输入图像信息
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);  // 把前端传进来的参数，赋给taskRequest
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求参数
     * @param loginUser                 登录用户
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 获取参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();

        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    /**
     * 【新增】编辑图片
     *
     * @param pictureEditRequest 图片编辑请求参数
     * @param loginUser          登录用户信息
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        // 已经改为注解鉴权，所以下面这个注释掉，这个方法和接口也会注释
//        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 【新增】删除图片
     *
     * @param pictureId 图片id
     * @param loginUser 登录用户信息
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        // 已经改为注解鉴权，所以下面这个注释掉，这个方法和接口也会注释
//        checkPictureAuth(loginUser, oldPicture);

        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    /**
     * 校验权限，
     */
    // 废弃
    @Deprecated
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();

        //如果spaceId为空，则表示公共图库
        if (spaceId == null) {
            // 公共图库，本人或管理员可操作
            if (!loginUserId.equals(picture.getUserId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不是本人或者管理员");
            }
        } else {
            // 私有空间：仅管理员，不对啊，这里应该还是仅本人和管理员可以操作才对啊。那这个ifelse有啥区别？不管了
            if (!userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不是本人");
            }
        }
    }

    /**
     * 【新增】异步清理图片文件（用于更新图片时删除旧图）
     *
     * @param oldPicture 旧图片对象
     */
    @Async  // 异步执行，不阻塞主流程
    @Override
    public void clearPictureFile(Picture oldPicture) {
        if (oldPicture == null || StrUtil.isBlank(oldPicture.getUrl())) {
            return;
        }

        String pictureUrl = oldPicture.getUrl();

        try {
            // 【关键】检查该图片是否被其他记录引用
            long count = this.lambdaQuery()
                    .eq(Picture::getUrl, pictureUrl)
                    .count();

            // 如果有不止一条记录用到了该图片，不清理
            if (count > 1) {
                log.info("图片被 {} 条记录引用，跳过清理: {}", count, pictureUrl);
                return;
            }

            // 只有一条记录使用，可以安全删除
            ossManager.deleteFile(pictureUrl);
            log.info("异步清理旧图片成功: {}", pictureUrl);

        } catch (Exception e) {
            log.error("异步清理旧图片失败: {}", pictureUrl, e);
            // 异步清理失败不影响主流程，只记录日志
        }
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

        // 1. 校验文件合法性: 如果没登录就不能上传图片
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 校验空间是否存在【新增1：写了space新增的】
        // 如果传了spaceId
        // 空间权限校验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 必须空间创建人（管理员）才能上传
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        // 3. 判断图片是更新还是新增还是别的什么操作
        //    如果请求参数不为空，就证明是更新图片信息，如果为空就证明是新增图片
        Long pictureId = null;//先默认为空，下面判断：1. 如果请求参数不为空，就证明是更新图片；2. 把请求参数的id赋值给他。
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }

        // 4. 检查数据库：如果pictureId不为空，继续进行更新的判断：如果数据库当中找不到这个图片id，就证明数据库中不存在这个id，就抛出异常
        //备注：如果pictureId为空，就证明是新增的上传图片，不是更新。就正常上传图片。
        if (pictureId != null) {
            // 4.1 检查图片是否存在
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 4.2 权限校验：仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此图片");
            }

            // 4.3 校验空间是否一致【新增1：写了space新增的】
            // 进入到这里不就意味着是更新吗？如果前面没传入spaceId，那就将原来的老图片的spaceId赋给新图片
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                //spaceId不为空，则需要判断，spaceId和原图片的spaceId是否一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间不一致");
                }
            }
        }

        // 5. 上传图片：无论新增还是修改，都需要上传图片
        //  备注：1. 如果上面都过了，证明是修改图片。 2. 上面的更新判断不符合要求，会带着pictureId=null下来，就是新增图片。
        // 按照空间划分目录【新增1】
        String uploadPathPrefix;
        if (spaceId == null) {
            // 如果上传上来没spaceId，就使用默认的目录public为子目录，按照id作为孙子目录。
            uploadPathPrefix = String.format("picturesProject/public/%s", loginUser.getId());
        } else {
            // 如果上传上来有spaceId，就使用空间space为子目录，按照spaceId作为孙子目录。
            uploadPathPrefix = String.format("picturesProject/space/%s", spaceId);

        }
        // 调用上传图片的方法
        // 父类引用指向filePictureUpload子类对象
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            // 父类引用指向urlPictureUpload子类对象
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        /**
         *  --接下来这里我有必要解释一下：项目中从manager到前端发生了什么？
         *   答： FileManager返回的是dto类。后续被service层调用返回的结果是dto类，但是数据库只认实体类Picture，所以需要将dto类转为实体类。
         *    转为实体类后，将数据插入数据库。后续controller调用service，读取数据库的数据，将其返回给前端，但是前端只认VO类。所以：
         *    这个时候又要将实体类Picture转为VO类。展示给前端。
         *   FileManager (技术层)
         *     ↓ 返回 UploadPictureResult (DTO)
         *   PictureServiceImpl (业务层)
         *     ↓ 转换为 Picture (Entity)
         *   数据库 (持久层)
         *     ↓ 读取 Picture (Entity)
         *   PictureServiceImpl (业务层)
         *     ↓ 转换为 PictureVO (VO)
         *   Controller (展示层)
         *     ↓ 返回给前端
         */
//        将dto类转为实体类（构造要入库的图片信息  ）
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());

        //这是从上传结果中获取的文件名，如传到oss，名称叫啥，就叫啥
        String picName = uploadPictureResult.getPicName();
        //这是从请求中获取的文件名，如用户输入的图片名称
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        // 设置图片名称(构造入库的图片信息)
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
//        注意：这里是picture的userid被赋值，而不是id被赋值。
//        只是将picture实体类的用户id赋值为登录用户的id。而不是将图片的id被赋值为登录用户的id。
        picture.setUserId(loginUser.getId());
        picture.setSpaceId(spaceId); //设置图片空间id【新增】


        // 填充审核参数：管理员自动过审，普通用户需待审核（方法里设置过了）
        this.fillReviewParams(picture, loginUser);
        // 6. 插入数据库
        //  6.1 区分新增与更新逻辑
        if (pictureId != null) {
            // 就是更新图片
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });

        return PictureVO.objToVo(picture);
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
        // ============ 第一步：获取请求参数 ============
        // 获取搜索关键词，比如 "风景"、"猫咪"
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 获取要抓取的图片数量，比如 10
        Integer count = pictureUploadByBatchRequest.getCount();
        // 校验需要获取数量不能超过30张（防止请求过多）
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        //  获取图片名称前缀；作用：如搜索蔡徐坤，可以设置名称前缀为坤，后续拼接后则图片名称为：坤1.jpg
        //                      也可以设置前缀为搜索词，直接就是蔡徐坤1.jpg
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        // 如果前缀为空，则使用搜索词作为前缀
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        // ============ 第二步：构造Bing图片搜索URL ============
        // 拼接Bing图片搜索的API地址
        // 例如：https://cn.bing.com/images/async?q=风景&mmasync=1
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);

        // ============ 第三步：请求Bing并获取HTML页面 ============
        Document document;
        // 优化后：
        try {
            // 设置连接超时时间为 10 秒，读取超时时间为 10 秒
            document = Jsoup.connect(fetchUrl)
                    .timeout(10000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36") // 伪装浏览器
                    .get();
        } catch (IOException e) {
            log.error("获取页面失败，网络超时或被拒绝", e);
            // 根据业务需求决定是抛出异常中断，还是返回空
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片搜索服务暂时不可用，请稍后重试");
        }
//        try {
//            // 使用jsoup发起HTTP请求，获取Bing搜索结果的HTML页面
//            document = Jsoup.connect(fetchUrl).get();
//        } catch (IOException e) {
//            // 如果网络请求失败，记录错误日志
//            log.error("获取页面失败", e);
//            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
//        }

        // ============ 第四步：从HTML中解析图片元素 ============
        // 查找HTML中class为"dgControl"的div元素（这个div包含了所有搜索结果图片）
        Element div = document.getElementsByClass("dgControl").first();
        // 如果没找到这个div，说明页面结构不对
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        // 在div中查找所有class为"mimg"的img标签（这些就是图片元素）
        Elements imgElementList = div.select("img.mimg");

        // ============ 第五步：遍历图片并逐个上传 ============
        int uploadCount = 0;  // 记录成功上传的图片数量
        for (Element imgElement : imgElementList) {
            // 从img标签中提取src属性（图片的URL地址）
            String fileUrl = imgElement.attr("src");

            // 如果URL为空，跳过这张图片
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }

            // 清理URL：去掉?后面的参数部分
            // 例如：https://example.com/photo.jpg?param=123 -> https://example.com/photo.jpg
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // ============ 第六步：调用单张上传方法 ============
            // 创建一个上传请求对象（id为空表示新增）
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            // 设置图片URL
            pictureUploadRequest.setFileUrl(fileUrl);
            // 设置图片名称，序号连续递增
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                // 调用uploadPicture方法上传这张图片到OSS
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                // 上传成功，记录日志
                log.info("图片上传成功, id = {}", pictureVO.getId());
                // 成功计数+1
                uploadCount++;
            } catch (Exception e) {
                // 如果某张图片上传失败，记录错误但继续处理下一张
                log.error("图片上传失败", e);
                continue;
            }

            // ============ 第七步：检查是否达到指定数量 ============
            // 如果已成功上传的图片数量达到了用户要求的数量，就停止
            if (uploadCount >= count) {
                break;
            }
        }

        // ============ 第八步：返回结果 ============
        // 返回实际成功上传的图片数量
        return uploadCount;
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
        //创建查询条件对象
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 将前端传进来的图片查询参数 提取出来
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();


        // 构建查询条件
        //如果搜索词searchText不为空，则同时从name和introduction字段中模糊匹配
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

        // JSON 数组查询：匹配 tags 字段中包含指定标签的图片
        if (CollUtil.isNotEmpty(tags)) {
            // 遍历所有标签，每个标签都需要匹配
            // SQL: WHERE tags LIKE '%"tag1"%' AND tags LIKE '%"tag2"%'
            for (String tag : tags) {
                // 注意：需要加上引号 "\""+tag+"\""，因为 tags 字段存储的是 JSON 数组字符串
                // 例如：tags = '["风景", "旅行"]'，搜索 "风景" 需要匹配 '"风景"'
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序：根据字段和排序方式动态排序
        // 参数说明：
        // - StrUtil.isNotEmpty(sortField)：如果排序字段不为空，才添加 ORDER BY
        // - sortOrder.equals("ascend")：如果 sortOrder 是 "ascend"，则升序；否则降序
        // - sortField：排序字段名（如 "createTime"、"picSize"）
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
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
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
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
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 图片参数校验，在进行一些操作之前，需要对数据进行检验，如果有问题的话。抛出异常。保证我们的数据进入一些操作的时候是没问题的。
     * <p>
     * 【方法用途】
     * 校验图片参数，包括 id、url、name、introduction、category、tags、picSize、picWidth、picHeight、picScale、picFormat、userId 等字段
     * - picture：图片参数对象
     *
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
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
//        1. 获取需要审核的图片id，获取审核状态的数值，通过审核状态的数值获取枚举对象：比如PASS，REJECT，REVIEWING
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
//        2. 判断我们获取的数据参数有无问题
        // 触发条件：
        // - id == null：图片ID为空
        // - reviewStatusEnum == null：审核状态不是有效值（不是 0/1/2）
        // - reviewStatusEnum == REVIEWING：尝试设置为"待审核"状态（审核操作只能是"通过"或"拒绝"
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 3. 根据id 查询图片，判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 4. 如果图片当前状态已经是目标状态，说明已经审核过了，不允许重复审核
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 5. 更新审核状态：将审核结果赋给数据库
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        // 审核人信息
        updatePicture.setReviewerId(loginUser.getId());
        // 审核时间
        updatePicture.setReviewTime(new Date());
        //更新
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
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
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }


}




