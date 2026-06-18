package com.yuzong.yuzongpicturebackend.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.yuzong.yuzongpicturebackend.config.OssConfig;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.manager.OssManager;
import com.yuzong.yuzongpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

/**
 * 图片上传服务模版【完整版】--模版方法模式
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private OssConfig ossConfig;

    @Resource
    private OssManager ossManager;


    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
//        一、前置校验
        // 1. 校验文件大小、格式是否符合业务规则
        validPicture(inputSource);

//        二、上传参数
        // 1.生成唯一标识符（避免文件名冲突，UUID去除横杠）【就是生成一个随机的字符串，给文件名】
        String uuid = UUID.randomUUID().toString().replace("-", "");
        // 2. 获取文件名【就是用户上传的文件名】
        String originFilename = getOriginFilename(inputSource);
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
            processFile(inputSource, tempFile);


//        四、解析图片元数据
            // 阿里云 OSS 不会自动返回图片信息，需要手动解析
            BufferedImage bufferedImage = ImageIO.read(tempFile);
            if (bufferedImage == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别的图片文件");
            }
//        五、上传图片到OSS
            // 调用OssManager的putPictureObject方法上传图片到OSS
            ossManager.putPictureObject(uploadPath, tempFile);

//        六、封装返回结果（新）
//        备注：这里将获取图片信息和封装返回结果封装成buildResult方法
            //调用封装返回结果的方法
            return buildResult(bufferedImage, tempFile, uploadPath, originFilename, fileSuffix, uploadFilename);

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
     * 封装返回结果
     *
     * @param bufferedImage  图片缓冲区
     * @param tempFile       临时文件
     * @param uploadPath     上传路径
     * @param originFilename 原始文件名
     * @param fileSuffix     文件后缀
     * @param uploadFilename 上传文件名
     * @return 上传结果
     */
    @NonNull
    private UploadPictureResult buildResult(BufferedImage bufferedImage, File tempFile, String uploadPath, String originFilename, String fileSuffix, String uploadFilename) {
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
    }


    /**
     * 3个抽象方法，用于子类实现
     */
    // 1. 验证图片
    protected abstract void validPicture(Object inputSource);

    // 2. 获取原始文件名
    protected abstract String getOriginFilename(Object inputSource);

    // 3. 处理文件
    protected abstract void processFile(Object inputSource, File tempFile);


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
    String getFileExtension(String fileName) {
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


//
}
