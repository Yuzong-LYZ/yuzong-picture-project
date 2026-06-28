package com.yuzong.yuzongpicture.infrastructure.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yuzong.yuzongpicture.infrastructure.api.OssManager;
import com.yuzong.yuzongpicture.infrastructure.config.OssConfig;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import com.yuzong.yuzongpicture.infrastructure.manager.upload.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 文件管理器 - 业务服务层（Service）
 * <p>
 * 【定位说明】
 * - 这用了（@Service注解），但是他是属于manager层，是属于可复用的业务层。----图片上传服务
 * - 职责：处理图片上传的完整业务流程（校验 → 处理 → 解析 → 返回结果）
 */
@Service
@Slf4j
@Deprecated   // 这个类被弃用，请不用使用FileManager
public class FileManager {

    @Resource
    private OssConfig ossConfig;

    @Resource
    private OssManager ossManager;

//        --------以下：《通用文件上传服务》

    /**
     * -
     * 上传图片 - 拓展方法（核心业务流程）
     * 【业务价值】
     * 这是图片上传的完整业务实现，封装了从接收文件到返回元数据的全流程
     * 【执行流程】
     * ① 校验文件合法性 → ② 生成唯一文件名 → ③ 创建临时文件
     * → ④ 解析图片元数据 → ⑤ 上传到OSS → ⑥ 封装返回结果 → ⑦ 清理临时文件
     *
     * @param multipartFile    前端上传的文件（来自 Controller）
     * @param uploadPathPrefix 上传路径前缀（如 "pictures"、"avatars"），用于分类存储
     * @return UploadPictureResult 包含图片URL和完整元数据的业务对象
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
//        一、前置校验
        // 1. 校验文件大小、格式是否符合业务规则
        validPicture(multipartFile);

//        二、上传参数
        // 1.生成唯一标识符（避免文件名冲突，UUID去除横杠）【就是生成一个随机的字符串，给文件名】
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // 2. 获取文件名【就是用户上传的文件名】
        String originFilename = multipartFile.getOriginalFilename();
        // 3. 获取文件后缀，如 ".jpg"
        String fileSuffix = getFileExtension(originFilename);
        // 4. 生成唯一文件名：日期_时间戳_UUID（保证全局唯一性
        String uploadFilename = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originFilename));
        // 拼接完整上传路径：上传路径前缀/文件名（如 "pictures/2026-5-26_1234567890_abc.jpg"）
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);


        File tempFile = null;
        try {
//        三、创建临时文件
            // 1， 创建临时文件，
            tempFile = File.createTempFile("upload_", "." + fileSuffix);
//            将传递进来的文件转为临时文件
            multipartFile.transferTo(tempFile);


//        四、解析图片元数据
            // 阿里云 OSS 不会自动返回图片信息，需要手动解析
            BufferedImage bufferedImage = ImageIO.read(tempFile);
            if (bufferedImage == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别的图片文件");
            }
//        五、上传图片到OSS
            // 调用OssManager的putPictureObject方法上传图片到OSS
            ossManager.putPictureObject(uploadPath, tempFile);

//        六、获取图片属性（也就是图片尺寸、比例、大小、格式）
            // 获取图片宽度（像素）
            int picWidth = bufferedImage.getWidth();
            // 获取图片高度（像素）
            int picHeight = bufferedImage.getHeight();
            // 计算宽高比（保留2位小数，四舍五入，防止除零错误）
            double picScale = picWidth > 0 ?
                    BigDecimal.valueOf((double) picWidth / picHeight).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue() : 0.0;
            // 获取文件大小（字节）
            long picSize = tempFile.length();

//        七、封装返回结果
//            要上传上来的图片，参数什么的封装成dto对象，为啥不封装成vo对象或者Picture对象呢？
//            因为vo对象是用于展示的，而dto对象是用于业务逻辑的，所以要封装成dto对象。所以在写之前需要创建个dto对象。
            // 将技术层面的数据转换为业务层面的 DTO 对象
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            // 构造可访问的 URL（HTTPS协议 + Bucket + Endpoint + 对象键）
            uploadPictureResult.setUrl("https://" + ossConfig.getBucketName() + "."
                    + ossConfig.getEndpoint() + "/" + uploadPath);
            // 设置展示名称（去掉扩展名，如 "photo_2026"）
            uploadPictureResult.setPicName(getFileNameWithoutExtension(originFilename));
            // 设置图片宽度
            uploadPictureResult.setPicWidth(picWidth);
            // 设置图片高度
            uploadPictureResult.setPicHeight(picHeight);
            // 设置宽高比
            uploadPictureResult.setPicScale(picScale);
            // 设置格式（大写，如 "JPG"、"PNG"）
            uploadPictureResult.setPicFormat(fileSuffix.toUpperCase());
            // 设置文件大小
            uploadPictureResult.setPicSize(picSize);

            // 记录成功日志（便于追踪和问题排查）
            log.info("图片上传成功: {}, 尺寸: {}x{}, 大小: {} bytes", uploadFilename, picWidth, picHeight, picSize);
            return uploadPictureResult;

        } catch (BusinessException e) {
            // 业务异常直接抛出（已经包含明确的错误信息）
            throw e;
        } catch (Exception e) {
            // 未知异常记录日志并转换为友好的业务异常
            log.error("图片上传处理失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传处理失败");
        } finally {
            // 无论成功或失败，都必须删除临时文件（防止磁盘空间泄漏）
            deleteTempFile(tempFile);
        }
    }

    /**
     * 上传图片（通过 URL）方法
     */
    public UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
//        一、前置校验
        // 1. 校验文件大小、格式是否符合业务规则
        validPicture(fileUrl);

//        二、上传参数
        // 1.生成唯一标识符（避免文件名冲突，UUID去除横杠）【就是生成一个随机的字符串，给文件名】
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // 2. 获取文件名【就是用户上传的文件名】
        String originFilename = getFileExtension(fileUrl);
        // 3. 获取文件后缀，如 ".jpg"
        String fileSuffix = getFileExtension(originFilename);
        // 4. 生成唯一文件名：日期_时间戳_UUID（保证全局唯一性
        String uploadFilename = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originFilename));
        // 拼接完整上传路径：上传路径前缀/文件名（如 "pictures/2026-5-26_1234567890_abc.jpg"）
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);


        File tempFile = null;
        try {
//        三、创建临时文件
            // 1， 创建临时文件，将传递进来的文件转为临时文件
            tempFile = File.createTempFile("upload_", "." + fileSuffix);
            HttpUtil.downloadFile(fileUrl, tempFile);

//            下面开始和本来的上传图片功能没区别。
//        四、解析图片元数据
            // 阿里云 OSS 不会自动返回图片信息，需要手动解析
            BufferedImage bufferedImage = ImageIO.read(tempFile);
            if (bufferedImage == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别的图片文件");
            }
//        五、上传图片到OSS
            // 调用OssManager的putPictureObject方法上传图片到OSS
            ossManager.putPictureObject(uploadPath, tempFile);

//        六、获取图片属性（也就是图片尺寸、比例、大小、格式）
            // 获取图片宽度（像素）
            int picWidth = bufferedImage.getWidth();
            // 获取图片高度（像素）
            int picHeight = bufferedImage.getHeight();
            // 计算宽高比（保留2位小数，四舍五入，防止除零错误）
            double picScale = picWidth > 0 ?
                    BigDecimal.valueOf((double) picWidth / picHeight).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue() : 0.0;
            // 获取文件大小（字节）
            long picSize = tempFile.length();

//        七、封装返回结果
//            要上传上来的图片，参数什么的封装成dto对象，为啥不封装成vo对象或者Picture对象呢？
//            因为vo对象是用于展示的，而dto对象是用于业务逻辑的，所以要封装成dto对象。所以在写之前需要创建个dto对象。
            // 将技术层面的数据转换为业务层面的 DTO 对象
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            // 构造可访问的 URL（HTTPS协议 + Bucket + Endpoint + 对象键）
            uploadPictureResult.setUrl("https://" + ossConfig.getBucketName() + "."
                    + ossConfig.getEndpoint() + "/" + uploadPath);
            // 设置展示名称（去掉扩展名，如 "photo_2026"）
            uploadPictureResult.setPicName(getFileNameWithoutExtension(originFilename));
            // 设置图片宽度
            uploadPictureResult.setPicWidth(picWidth);
            // 设置图片高度
            uploadPictureResult.setPicHeight(picHeight);
            // 设置宽高比
            uploadPictureResult.setPicScale(picScale);
            // 设置格式（大写，如 "JPG"、"PNG"）
            uploadPictureResult.setPicFormat(fileSuffix.toUpperCase());
            // 设置文件大小
            uploadPictureResult.setPicSize(picSize);

            // 记录成功日志（便于追踪和问题排查）
            log.info("图片上传成功: {}, 尺寸: {}x{}, 大小: {} bytes", uploadFilename, picWidth, picHeight, picSize);
            return uploadPictureResult;

        } catch (BusinessException e) {
            // 业务异常直接抛出（已经包含明确的错误信息）
            throw e;
        } catch (Exception e) {
            // 未知异常记录日志并转换为友好的业务异常
            log.error("图片上传处理失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传处理失败");
        } finally {
            // 无论成功或失败，都必须删除临时文件（防止磁盘空间泄漏）
            deleteTempFile(tempFile);
        }
    }

    /**
     * 校验文件 - 业务规则验证【这个不需要啊知道原理，会看注释就够了】
     * <p>
     * 【校验目的】
     * 在上传前拦截非法文件，避免浪费 OSS 存储空间和网络带宽
     * <p>
     * 【校验项】
     * 1. 非空校验：确保文件存在
     * 2. 大小校验：限制为 2MB（根据业务需求调整）
     * 3. 格式校验：仅允许常见图片格式（安全防护）
     *
     * @param multipartFile 待校验的文件
     */
    public void validPicture(MultipartFile multipartFile) {
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
     * 删除临时文件【这个不需要啊知道原理，会看注释就够了】
     *
     * @param file 临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("临时文件删除失败, filepath = {}", file.getAbsolutePath());
        }
    }

    /**
     * 获取文件扩展名【这个不需要啊知道原理，会看注释就够了】
     *
     * @param fileName 文件名
     * @return 扩展名（小写）
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名无效");
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 获取不带扩展名的文件名【这个不需要啊知道原理，会看注释就够了】
     *
     * @param fileName 文件名
     * @return 纯文件名
     */
    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf("."));
    }


    /**
     * 验证url图片
     * 方法名和之前的方法名一样，只是参数不同
     *
     * @param fileUrl 图片地址
     */
    private void validPicture(String fileUrl) {
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
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
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


}
